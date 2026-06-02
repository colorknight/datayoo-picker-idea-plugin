package org.datayoo.picker

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.MemberValuePair
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.google.gson.GsonBuilder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object I18nGenerator {
  data class Result(
    val wroteFile: Boolean,
    val file: VirtualFile,
    val entryCount: Int,
    val message: String,
  )

  fun generateForDescriptorFile(
    descriptorJava: VirtualFile,
    resourcesRoot: VirtualFile,
    overwriteExisting: Boolean,
  ): Result {
    val cu = JavaParser().parse(descriptorJava.inputStream).result.orElse(null)
      ?: return Result(false, resourcesRoot, 0, "解析 Java 文件失败：${descriptorJava.name}")

    val target = cu.findAll(ClassOrInterfaceDeclaration::class.java)
      .firstOrNull { decl -> decl.getAnnotationByName("OpDefiner").isPresent }
      ?: return Result(false, resourcesRoot, 0, "未找到 @OpDefiner：${descriptorJava.name}")

    val anno = target.getAnnotationByName("OpDefiner").get()
    if (anno !is NormalAnnotationExpr) {
      return Result(false, resourcesRoot, 0, "@OpDefiner 不是 NormalAnnotationExpr：${descriptorJava.name}")
    }

    val nameValue = findAnnoValue(anno, "name")?.let(::stripQuotes)?.trim().orEmpty()
    if (nameValue.isBlank()) {
      return Result(false, resourcesRoot, 0, "@OpDefiner.name 为空：${descriptorJava.name}")
    }

    val parametersRaw = findAnnoValue(anno, "parameters")
    val xml = parametersRaw?.let { normalizeXmlLiteral(it) }

    val entries = linkedSetOf<Map<String, Any?>>()
    entries += operatorEntry(nameValue)
    if (!xml.isNullOrBlank()) {
      entries += parseParametersXml(operatorName = nameValue, xml = xml)
    }

    val i18nsDir = resourcesRoot.findChild("i18ns") ?: VfsUtil.createDirectoryIfMissing(resourcesRoot, "i18ns")
      ?: return Result(false, resourcesRoot, entries.size, "创建 i18ns 目录失败：${resourcesRoot.path}")
    val outFile = i18nsDir.findChild("$nameValue.json") ?: i18nsDir.createChildData(this, "$nameValue.json")

    if (outFile.exists() && outFile.length > 0L && !overwriteExisting) {
      return Result(false, outFile, entries.size, "已存在，未覆盖：${outFile.name}")
    }

    val gson = GsonBuilder().setPrettyPrinting().create()
    val json = gson.toJson(entries.toList())
    outFile.setBinaryContent(json.toByteArray(Charsets.UTF_8))
    return Result(true, outFile, entries.size, "生成成功：${outFile.name}")
  }

  private fun findAnnoValue(anno: NormalAnnotationExpr, key: String): String? {
    val pair: MemberValuePair = anno.pairs.firstOrNull { it.nameAsString == key } ?: return null
    return pair.value.toString()
  }

  private fun stripQuotes(s: String): String = s.removeSurrounding("\"").removeSurrounding("'")

  /**
   * Handles Java annotation string literal that may include quotes, + concatenation artifacts, and escapes.
   */
  private fun normalizeXmlLiteral(raw: String): String {
    // `raw` is something like "\"<xml>...\" + \"...\""`
    val noPlus = raw.replace("+", "")
    val trimmed = noPlus.trim()
    val withoutOuterQuotes = stripQuotes(trimmed)
    val unescaped = unescapeJavaLike(withoutOuterQuotes)
    // In maven plugin they also removed: "\"<" -> "<" and ">\"" -> ">"
    return unescaped
      .replace(Regex("\"\\s*<"), "<")
      .replace(Regex(">\\s*\""), ">")
      .trim()
  }

  private fun unescapeJavaLike(input: String): String {
    val sb = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
      val ch = input[i]
      if (ch != '\\') {
        sb.append(ch)
        i++
        continue
      }
      if (i + 1 >= input.length) {
        sb.append('\\')
        break
      }
      val next = input[i + 1]
      when (next) {
        'r' -> sb.append('\r')
        'n' -> sb.append('\n')
        't' -> sb.append('\t')
        '\\' -> sb.append('\\')
        '"' -> sb.append('"')
        '\'' -> sb.append('\'')
        else -> sb.append(next)
      }
      i += 2
    }
    return sb.toString()
  }

  private fun operatorEntry(name: String): Map<String, Any?> {
    return mapOf(
      "id" to name,
      "text" to name,
      "i18ns" to mapOf(
        "en_US" to name,
        "zh_CN" to "",
      )
    )
  }

  /**
   * Best-effort XML walker to extract elements with name + (optional) c_Alias.
   * Produces entries using dot-joined path: operatorName.<group>.<param>...
   */
  private fun parseParametersXml(operatorName: String, xml: String): Set<Map<String, Any?>> {
    val doc = runCatching {
      val dbf = DocumentBuilderFactory.newInstance()
      dbf.isNamespaceAware = false
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      dbf.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }.getOrNull() ?: return emptySet()

    val out = linkedSetOf<Map<String, Any?>>()

    fun walk(node: org.w3c.dom.Node, path: List<String>) {
      if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) return
      val el = node as org.w3c.dom.Element
      val nameAttr = el.getAttribute("name")?.trim().orEmpty()
      val alias = el.getAttribute("c_Alias")?.trim().orEmpty()
      val nextPath = if (nameAttr.isNotBlank()) path + nameAttr else path

      if (nameAttr.isNotBlank()) {
        val id = (listOf(operatorName) + nextPath).joinToString(".")
        out += mapOf(
          "id" to id,
          "text" to nameAttr,
          "i18ns" to mapOf(
            "en_US" to nameAttr,
            "zh_CN" to alias,
          )
        )
      }

      val children = el.childNodes
      for (i in 0 until children.length) {
        walk(children.item(i), nextPath)
      }
    }

    walk(doc.documentElement, emptyList())
    return out
  }
}

