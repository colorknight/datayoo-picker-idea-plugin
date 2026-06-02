package org.datayoo.picker

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

object OperatorScanner {
  private val logger = Logger.getInstance(OperatorScanner::class.java)

  private val opDefinerStartPattern = Pattern.compile("@(?:[A-Za-z0-9_$.]+\\.)?OpDefiner\\b")
  private val classPattern = Pattern.compile(
    "\\s*(?:(?:public|protected|private|internal|open|final|abstract|sealed|data|static)\\s+)*class\\s+([A-Za-z_][A-Za-z0-9_]*)",
    Pattern.DOTALL
  )
  private val packagePattern = Pattern.compile("package\\s+([A-Za-z0-9_.]+)")
  private val ignoredDirectoryNames = setOf(".idea", ".git", ".gradle", "build", "out", "target")
  private val basicAttributes = listOf("name", "type", "version", "computionFramework", "provider", "summary")

  private data class OpDefinerAnnotation(
    val arguments: String,
    val endOffset: Int
  )

  data class ScanResult(
    val operators: List<String>,
    /**
     * 定义态 descriptor 类 FQCN → 命中该源码时解析出的含 `pom.xml` 的 Maven module 根目录（canonical），
     * 与扫描到的 `.java/.kt` 文件一致，供打包时直接使用，避免事后按类名反查误命中同名类。
     */
    val descriptorModulePomByFqcn: Map<String, String>,
    val visitedFileCount: Int,
    val sourceFileCount: Int,
    val candidateFileCount: Int,
    val annotationCount: Int,
    val elapsedMs: Long
  )

  private fun findNearestPomDir(start: VirtualFile): VirtualFile? {
    var current: VirtualFile? = start
    while (current != null) {
      val pom = current.findChild("pom.xml")
      if (pom != null && !pom.isDirectory) return current
      current = current.parent
    }
    return null
  }

  fun scan(baseDir: VirtualFile, shouldContinue: () -> Boolean = { true }): ScanResult {
    val operators = linkedSetOf<String>()
    val descriptorPomByFqcn = linkedMapOf<String, String>()
    var visitedFileCount = 0
    var sourceFileCount = 0
    var candidateFileCount = 0
    var annotationCount = 0
    val startNs = System.nanoTime()
    VfsUtilCore.iterateChildrenRecursively(baseDir, null) { file ->
      checkCanceled(shouldContinue)
      if (file.isDirectory && file.name in ignoredDirectoryNames) {
        return@iterateChildrenRecursively false
      }
      if (!file.isDirectory) {
        visitedFileCount++
      }
      val delta = collectOperator(file, operators, descriptorPomByFqcn, shouldContinue)
      if (delta.sourceFile) sourceFileCount++
      if (delta.candidateFile) candidateFileCount++
      annotationCount += delta.annotationCount
      true
    }
    checkCanceled(shouldContinue)
    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
    val sortedOperators = operators.toList().sorted()
    logger.info(
      "Operator scan finished: root=${baseDir.path}, visitedFiles=$visitedFileCount, " +
        "sourceFiles=$sourceFileCount, candidateFiles=$candidateFileCount, annotations=$annotationCount, " +
        "operators=${sortedOperators.size}, elapsedMs=$elapsedMs"
    )
    return ScanResult(
      operators = sortedOperators,
      descriptorModulePomByFqcn = descriptorPomByFqcn.toMap(),
      visitedFileCount = visitedFileCount,
      sourceFileCount = sourceFileCount,
      candidateFileCount = candidateFileCount,
      annotationCount = annotationCount,
      elapsedMs = elapsedMs
    )
  }

  private data class CollectDelta(
    val sourceFile: Boolean,
    val candidateFile: Boolean,
    val annotationCount: Int
  )

  private fun collectOperator(
    file: VirtualFile,
    operators: MutableSet<String>,
    descriptorPomByFqcn: MutableMap<String, String>,
    shouldContinue: () -> Boolean
  ): CollectDelta {
    checkCanceled(shouldContinue)
    if (file.isDirectory) return CollectDelta(sourceFile = false, candidateFile = false, annotationCount = 0)
    if (file.extension != "java" && file.extension != "kt") {
      return CollectDelta(sourceFile = false, candidateFile = false, annotationCount = 0)
    }

    val text = try {
      VfsUtilCore.loadText(file)
    } catch (_: Exception) {
      return CollectDelta(sourceFile = true, candidateFile = false, annotationCount = 0)
    }
    if (!text.contains("OpDefiner")) {
      return CollectDelta(sourceFile = true, candidateFile = false, annotationCount = 0)
    }

    val pkg = packagePattern.matcher(text).run {
      if (find()) group(1) else ""
    }

    val opDefinerAnnotations = findOpDefinerAnnotations(text, shouldContinue)
    for (annotation in opDefinerAnnotations) {
      checkCanceled(shouldContinue)
      val className = findClassNameAfter(text, annotation.endOffset) ?: continue
      val fqcn = if (pkg.isEmpty()) className else "$pkg.$className"
      val attributeValues = extractBasicAttributes(annotation.arguments)
      operators.add(formatOperator(fqcn, attributeValues))
      if (!descriptorPomByFqcn.containsKey(fqcn)) {
        findNearestPomDir(file)?.path?.let { p ->
          val canon = runCatching { java.io.File(p).canonicalFile.absolutePath }.getOrNull() ?: p
          descriptorPomByFqcn[fqcn] = canon
        }
      }
    }
    return CollectDelta(
      sourceFile = true,
      candidateFile = true,
      annotationCount = opDefinerAnnotations.size
    )
  }

  /**
   * 扫描到 [offset]（不含）为止，判断当前是否处于 `//`、`/* */` 注释或 `"`/`'` 字符串内（与 Java/Kotlin 常见写法一致，非完整词法分析）。
   * 用于忽略 `//@OpDefiner`、块注释内示例等误匹配。
   */
  fun isOffsetInsideCommentOrString(text: String, offset: Int): Boolean {
    if (offset <= 0) return false
    var i = 0
    var inLineComment = false
    var inBlockComment = false
    var inString = false
    var stringDelim = '"'
    var escaping = false
    while (i < offset) {
      val ch = text[i]
      if (inLineComment) {
        if (ch == '\n' || ch == '\r') inLineComment = false
        i++
        continue
      }
      if (inBlockComment) {
        if (ch == '*' && i + 1 < text.length && text[i + 1] == '/') {
          inBlockComment = false
          i += 2
          continue
        }
        i++
        continue
      }
      if (inString) {
        if (escaping) {
          escaping = false
          i++
          continue
        }
        if (ch == '\\') {
          escaping = true
          i++
          continue
        }
        if (ch == stringDelim) {
          inString = false
        }
        i++
        continue
      }
      if (ch == '/' && i + 1 < text.length) {
        when (text[i + 1]) {
          '/' -> {
            inLineComment = true
            i += 2
            continue
          }
          '*' -> {
            inBlockComment = true
            i += 2
            continue
          }
        }
      }
      if (ch == '"' || ch == '\'') {
        inString = true
        stringDelim = ch
        i++
        continue
      }
      i++
    }
    return inLineComment || inBlockComment || inString
  }

  private fun findOpDefinerAnnotations(text: String, shouldContinue: () -> Boolean): List<OpDefinerAnnotation> {
    val results = mutableListOf<OpDefinerAnnotation>()
    val matcher = opDefinerStartPattern.matcher(text)
    while (matcher.find()) {
      checkCanceled(shouldContinue)
      if (isOffsetInsideCommentOrString(text, matcher.start())) continue
      var cursor = matcher.end()
      while (cursor < text.length && text[cursor].isWhitespace()) {
        cursor++
      }
      if (cursor >= text.length || text[cursor] != '(') {
        results.add(OpDefinerAnnotation("", cursor))
        continue
      }

      val parsed = parseParenthesizedContent(text, cursor) ?: continue
      results.add(OpDefinerAnnotation(parsed.first, parsed.second))
    }
    return results
  }

  private fun checkCanceled(shouldContinue: () -> Boolean) {
    if (!shouldContinue()) throw ProcessCanceledException()
  }

  private fun parseParenthesizedContent(text: String, openParenOffset: Int): Pair<String, Int>? {
    if (openParenOffset >= text.length || text[openParenOffset] != '(') return null

    val sb = StringBuilder()
    var depth = 0
    var inString = false
    var escaping = false
    var cursor = openParenOffset

    while (cursor < text.length) {
      val ch = text[cursor]
      if (inString) {
        if (escaping) {
          escaping = false
        } else if (ch == '\\') {
          escaping = true
        } else if (ch == '"') {
          inString = false
        }
      } else {
        when (ch) {
          '"' -> inString = true
          '(' -> {
            depth++
            if (depth > 1) sb.append(ch)
            cursor++
            continue
          }
          ')' -> {
            depth--
            if (depth == 0) {
              return Pair(sb.toString(), cursor + 1)
            }
          }
        }
      }

      if (depth > 0 && !(depth == 1 && ch == '(')) {
        sb.append(ch)
      }
      cursor++
    }
    return null
  }

  private fun findClassNameAfter(text: String, offset: Int): String? {
    var start = skipSpacesAndComments(text, offset) ?: return null
    while (start < text.length && text[start] == '@') {
      start = skipAnnotation(text, start) ?: return null
      start = skipSpacesAndComments(text, start) ?: return null
    }
    val matcher = classPattern.matcher(text)
    matcher.region(start, text.length)
    if (!matcher.lookingAt()) return null
    return matcher.group(1)
  }

  private fun skipAnnotation(text: String, offset: Int): Int? {
    var cursor = offset + 1
    while (cursor < text.length) {
      val ch = text[cursor]
      if (ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '$') {
        cursor++
      } else {
        break
      }
    }
    cursor = skipSpacesAndComments(text, cursor) ?: return text.length
    if (cursor < text.length && text[cursor] == '(') {
      val parsed = parseParenthesizedContent(text, cursor) ?: return null
      return parsed.second
    }
    return cursor
  }

  private fun skipSpacesAndComments(text: String, offset: Int): Int? {
    var cursor = offset
    while (cursor < text.length) {
      if (text[cursor].isWhitespace()) {
        cursor++
        continue
      }
      if (cursor + 1 < text.length && text[cursor] == '/' && text[cursor + 1] == '/') {
        cursor += 2
        while (cursor < text.length && text[cursor] != '\n') cursor++
        continue
      }
      if (cursor + 1 < text.length && text[cursor] == '/' && text[cursor + 1] == '*') {
        val commentEnd = text.indexOf("*/", cursor + 2)
        cursor = if (commentEnd >= 0) commentEnd + 2 else text.length
        continue
      }
      return cursor
    }
    return null
  }

  private fun extractBasicAttributes(annotationArguments: String): Map<String, String> {
    if (annotationArguments.isBlank()) return emptyMap()

    val assignments = parseTopLevelAssignments(annotationArguments)
    val result = linkedMapOf<String, String>()
    for (key in basicAttributes) {
      val raw = assignments[key] ?: continue
      result[key] = normalizeValue(raw)
    }
    return result
  }

  private fun parseTopLevelAssignments(text: String): Map<String, String> {
    val parts = mutableListOf<String>()
    var start = 0
    var depthParen = 0
    var depthBrace = 0
    var depthBracket = 0
    var inString = false
    var escaping = false

    for (index in text.indices) {
      val ch = text[index]
      if (inString) {
        if (escaping) {
          escaping = false
        } else if (ch == '\\') {
          escaping = true
        } else if (ch == '"') {
          inString = false
        }
        continue
      }

      when (ch) {
        '"' -> inString = true
        '(' -> depthParen++
        ')' -> if (depthParen > 0) depthParen--
        '{' -> depthBrace++
        '}' -> if (depthBrace > 0) depthBrace--
        '[' -> depthBracket++
        ']' -> if (depthBracket > 0) depthBracket--
        ',' -> {
          if (depthParen == 0 && depthBrace == 0 && depthBracket == 0) {
            parts.add(text.substring(start, index))
            start = index + 1
          }
        }
      }
    }
    if (start < text.length) {
      parts.add(text.substring(start))
    }

    val result = linkedMapOf<String, String>()
    for (part in parts) {
      val pair = splitTopLevelAssignment(part) ?: continue
      result[pair.first] = pair.second
    }
    return result
  }

  private fun splitTopLevelAssignment(part: String): Pair<String, String>? {
    var depthParen = 0
    var depthBrace = 0
    var depthBracket = 0
    var inString = false
    var escaping = false

    for (index in part.indices) {
      val ch = part[index]
      if (inString) {
        if (escaping) {
          escaping = false
        } else if (ch == '\\') {
          escaping = true
        } else if (ch == '"') {
          inString = false
        }
        continue
      }

      when (ch) {
        '"' -> inString = true
        '(' -> depthParen++
        ')' -> if (depthParen > 0) depthParen--
        '{' -> depthBrace++
        '}' -> if (depthBrace > 0) depthBrace--
        '[' -> depthBracket++
        ']' -> if (depthBracket > 0) depthBracket--
        '=' -> {
          if (depthParen == 0 && depthBrace == 0 && depthBracket == 0) {
            val key = part.substring(0, index).trim()
            val value = part.substring(index + 1).trim()
            if (key.isEmpty() || value.isEmpty()) return null
            return Pair(key, value)
          }
        }
      }
    }
    return null
  }

  private fun normalizeValue(raw: String): String {
    val compact = raw.replace(Regex("\\s+"), " ").trim()
    val unquoted = if (compact.length >= 2 && compact.first() == '"' && compact.last() == '"') {
      compact.substring(1, compact.length - 1)
    } else {
      compact
    }
    return if (unquoted.length > 80) "${unquoted.take(77)}..." else unquoted
  }

  private fun formatOperator(fqcn: String, attributes: Map<String, String>): String {
    if (attributes.isEmpty()) return fqcn
    val details = attributes.entries.joinToString(" | ") { "${it.key}=${it.value}" }
    return "$fqcn | $details"
  }
}
