package org.datayoo.picker

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.regex.Pattern

/**
 * 扫描工程内 **源码** [PLUGIN_XML_REL]（含 [DESCRIPTOR_ROOT]），与 VM / connectorPack 使用的描述一致。
 * 不依赖 pom 里是否声明 connector-plugin（打包时再检查 Maven 配置即可）。
 */
object ConnectorScanner {
  const val CONNECTOR_PLUGIN_GROUP = "org.datayoo.pluginx"
  const val CONNECTOR_PLUGIN_ARTIFACT = "connector-plugin"

  const val PLUGIN_XML_REL = "src/main/resources/plugin.xml"
  private const val DESCRIPTOR_ROOT = "bracketsourcedescriptor"

  private val artifactIdPattern = Pattern.compile("<artifactId>\\s*([^<\\s]+)\\s*</artifactId>")
  private val pluginNamePattern = Pattern.compile("<name>\\s*([^<]+?)\\s*</name>")
  private val pluginAliasPattern = Pattern.compile("<alias>\\s*([^<]+?)\\s*</alias>")
  private val pluginTypesPattern = Pattern.compile("<types>\\s*([^<]+?)\\s*</types>")
  private val pluginFactoryPattern = Pattern.compile(
    "<factoryClass>\\s*([^<]+?)\\s*</factoryClass>",
    Pattern.DOTALL
  )
  private val mainArchivePattern = Pattern.compile("<mainArchive>\\s*([^<]+?)\\s*</mainArchive>")
  private val ignoredDirectoryNames = setOf(".idea", ".git", ".gradle", "build", "out", "target", "node_modules")

  data class ConnectorRow(
    val artifactId: String,
    /** 含 pom.xml 的 module 根目录（canonical 路径） */
    val pomDirPath: String,
    /** plugin.xml 的 &lt;name&gt;（逻辑 id，VM 用） */
    val pluginName: String?,
    /** plugin.xml 的 &lt;alias&gt;（展示名） */
    val pluginAlias: String?,
    /** plugin.xml 的 &lt;types&gt;（连接器类型，如 RDB） */
    val connectorTypes: String?,
    val factoryClassName: String?,
    /** plugin.xml 的 &lt;mainArchive&gt;（VM 加载主 jar，勿改） */
    val mainArchive: String?,
    val displayName: String,
    /** 源码 plugin.xml 路径 */
    val pluginXmlPath: String
  )

  data class ScanResult(
    val connectors: List<ConnectorRow>,
    /** 检查过的 src/main/resources/plugin.xml 数量 */
    val pluginXmlFilesScanned: Int,
    val elapsedMs: Long
  )

  fun describeScanScope(project: Project): String {
    val base = project.basePath?.replace('\\', '/') ?: "?"
    val mavenModules = collectMavenModuleRoots(project).size
    return if (mavenModules > 0) {
      "在 $mavenModules 个 Maven module 下查找 $PLUGIN_XML_REL（工程根：$base）"
    } else {
      "从工程根递归查找 $PLUGIN_XML_REL（$base）"
    }
  }

  fun collectMavenModuleRoots(project: Project): List<VirtualFile> {
    return collectMavenPomFiles(project).mapNotNull { it.parent?.takeIf { p -> p.isDirectory } }
      .distinctBy { canonicalFsPath(it) }
  }

  fun collectMavenPomFiles(project: Project): List<VirtualFile> {
    val mpm = MavenProjectsManager.getInstance(project)
    val fromMaven = runCatching { mpm.nonIgnoredProjects }.getOrNull().orEmpty()
      .mapNotNull { mp ->
        runCatching { mp.file }.getOrNull()?.takeIf { vf ->
          vf.isValid && !vf.isDirectory && vf.name.equals("pom.xml", ignoreCase = true)
        }
      }
    if (fromMaven.isNotEmpty()) {
      return fromMaven.distinctBy { it.path }
    }

    val byModule = linkedMapOf<String, VirtualFile>()
    ModuleManager.getInstance(project).modules.forEach { module ->
      ModuleRootManager.getInstance(module).contentRoots.forEach { root ->
        findNearestPomFile(root)?.let { pom -> byModule.putIfAbsent(pom.path, pom) }
      }
    }
    return byModule.values.toList()
  }

  fun scanProject(
    project: Project,
    shouldContinue: () -> Boolean = { true }
  ): ScanResult {
    val pluginXmlFiles = collectConnectorPluginXmlFiles(project)
    return scanConnectorPluginXmlFiles(pluginXmlFiles, shouldContinue)
  }

  /** 收集源码目录下的 connector plugin.xml（排除 target/ 等构建产物里的副本）。 */
  fun collectConnectorPluginXmlFiles(project: Project): List<VirtualFile> {
    val found = linkedMapOf<String, VirtualFile>()

    for (moduleRoot in collectMavenModuleRoots(project)) {
      moduleRoot.findFileByRelativePath(PLUGIN_XML_REL)?.let { xml ->
        if (isConnectorPluginXml(xml)) {
          found.putIfAbsent(canonicalFsPath(moduleRoot), xml)
        }
      }
    }

    if (found.isEmpty()) {
      val basePath = project.basePath ?: return emptyList()
      val root = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath.replace('\\', '/'))
        ?: return emptyList()
      if (root.isDirectory) {
        VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
          if (file.isDirectory) {
            return@iterateChildrenRecursively file.name !in ignoredDirectoryNames
          }
          if (isConnectorPluginXml(file)) {
            moduleRootOfPluginXml(file)?.let { moduleRoot ->
              found.putIfAbsent(canonicalFsPath(moduleRoot), file)
            }
          }
          true
        }
      }
    }

    return found.values.toList()
  }

  private fun scanConnectorPluginXmlFiles(
    pluginXmlFiles: List<VirtualFile>,
    shouldContinue: () -> Boolean
  ): ScanResult {
    val found = linkedMapOf<String, ConnectorRow>()
    val startNs = System.nanoTime()
    for (pluginXml in pluginXmlFiles) {
      if (!shouldContinue()) break
      parseConnectorFromPluginXml(pluginXml)?.let { row ->
        found.putIfAbsent(row.pomDirPath, row)
      }
    }
    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
    val sorted = found.values.sortedBy { (it.pluginAlias ?: it.pluginName ?: it.artifactId).lowercase() }
    return ScanResult(sorted, pluginXmlFiles.size, elapsedMs)
  }

  fun isConnectorPluginXml(file: VirtualFile): Boolean {
    if (!file.name.equals("plugin.xml", ignoreCase = true) || file.isDirectory) return false
    val normalized = file.path.replace('\\', '/')
    if (!normalized.endsWith("/$PLUGIN_XML_REL")) return false
    val text = runCatching { VfsUtilCore.loadText(file) }.getOrNull() ?: return false
    return text.contains(DESCRIPTOR_ROOT)
  }

  fun moduleRootOfPluginXml(pluginXml: VirtualFile): VirtualFile? {
    var current = pluginXml.parent ?: return null // resources
    current = current.parent ?: return null // main
    current = current.parent ?: return null // src
    current = current.parent ?: return null // module root
    return current.takeIf { it.isDirectory }
  }

  private fun findNearestPomFile(start: VirtualFile): VirtualFile? {
    var current: VirtualFile? = start
    while (current != null) {
      val pom = current.findChild("pom.xml")
      if (pom != null && !pom.isDirectory) return pom
      current = current.parent
    }
    return null
  }

  fun pomDirVirtualFile(pomDirPath: String): VirtualFile? {
    val path = pomDirPath.replace('\\', '/')
    val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return null
    return vf.takeIf { it.isValid && it.isDirectory }
  }

  private fun parseConnectorFromPluginXml(pluginXml: VirtualFile): ConnectorRow? {
    val moduleRoot = moduleRootOfPluginXml(pluginXml) ?: return null
    val text = runCatching { VfsUtilCore.loadText(pluginXml) }.getOrNull() ?: return null
    if (!text.contains(DESCRIPTOR_ROOT)) return null

    fun firstGroup(pattern: Pattern): String? =
      pattern.matcher(text).let { if (it.find()) it.group(1)?.trim()?.takeIf { s -> s.isNotEmpty() } else null }

    val pluginName = firstGroup(pluginNamePattern)
    val pluginAlias = firstGroup(pluginAliasPattern)
    val connectorTypes = firstGroup(pluginTypesPattern)
    val factoryClass = firstGroup(pluginFactoryPattern)
    val mainArchive = firstGroup(mainArchivePattern)

    val pomFile = moduleRoot.findChild("pom.xml")
    val artifactId = pomFile?.let { parseArtifactIdFromPom(it) }
      ?: moduleRoot.name

    val pomDirPath = canonicalFsPath(moduleRoot)
    val displayName = pluginAlias ?: pluginName ?: artifactId

    return ConnectorRow(
      artifactId = artifactId,
      pomDirPath = pomDirPath,
      pluginName = pluginName,
      pluginAlias = pluginAlias,
      connectorTypes = connectorTypes,
      factoryClassName = factoryClass,
      mainArchive = mainArchive,
      displayName = displayName,
      pluginXmlPath = canonicalFsPath(pluginXml)
    )
  }

  /** 取本 module 的 artifactId（跳过 parent / plugin 里的 artifactId）。 */
  private fun parseArtifactIdFromPom(pomFile: VirtualFile): String? {
    val pomText = runCatching { VfsUtilCore.loadText(pomFile) }.getOrNull() ?: return null
    val parentEnd = pomText.indexOf("</parent>")
    val searchFrom = if (parentEnd >= 0) parentEnd else 0
    val buildStart = pomText.indexOf("<build", searchFrom).let { if (it < 0) pomText.length else it }
    val slice = pomText.substring(searchFrom, buildStart)
    return artifactIdPattern.matcher(slice).let { m ->
      if (m.find()) m.group(1)?.trim() else null
    }?.takeIf { it.isNotBlank() }
  }

  private fun canonicalFsPath(file: VirtualFile): String =
    runCatching { java.io.File(file.path).canonicalFile.absolutePath }.getOrDefault(file.path)
}
