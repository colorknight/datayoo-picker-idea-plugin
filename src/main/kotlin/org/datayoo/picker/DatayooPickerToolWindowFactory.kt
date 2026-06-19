package org.datayoo.picker

import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.ui.JBColor
import com.intellij.ui.EditorTextField
import com.intellij.ui.SearchTextField
import com.intellij.ui.DocumentAdapter
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import javax.swing.JTextArea
import com.intellij.ui.components.JBList


import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.SVGLoader
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.awt.Frame
import java.awt.Component
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.awt.Dimension
import java.awt.Font
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.Point
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.nio.charset.Charset
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.BoxLayout
import javax.swing.Box
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JEditorPane
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JSplitPane
import javax.swing.SwingConstants
import javax.swing.JTable
import javax.swing.JTabbedPane
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.UIManager
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import java.awt.Cursor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.executors.DefaultRunExecutor

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.project.MavenProjectsManager
import com.intellij.openapi.projectRoots.Sdk
import org.datayoo.picker.tozoo.TozooUploadClient
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

private enum class PackLogTone {
  DEFAULT,
  PROGRESS,
  OK,
  WARN,
  ERROR
}

private fun escapeHtmlForPackLog(s: String): String = buildString(s.length + 8) {
  for (ch in s) {
    when (ch) {
      '&' -> append("&amp;")
      '<' -> append("&lt;")
      '>' -> append("&gt;")
      '"' -> append("&quot;")
      else -> append(ch)
    }
  }
}

private fun packLogToneToCssColor(tone: PackLogTone): String {
  val c = when (tone) {
    PackLogTone.DEFAULT -> JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
    PackLogTone.PROGRESS -> JBColor.namedColor("Link.activeForeground", JBColor(0x1866cc, 0x589df5))
    PackLogTone.OK -> JBColor(0x0d7d0d, 0x6aab6a)
    PackLogTone.WARN -> JBColor(0x8a6d00, 0xc8b000)
    PackLogTone.ERROR -> JBColor.namedColor("Label.errorForeground", JBColor.RED)
  }
  return String.format("#%06X", c.rgb and 0xffffff)
}

private fun shortenPathTail(file: java.io.File): String {
  val parts = file.toPath().toAbsolutePath().normalize().toString().replace('\\', '/')
    .split('/').filter { it.isNotBlank() }
  return if (parts.size <= 3) parts.joinToString("/") else parts.takeLast(3).joinToString("/")
}

/** 相对项目根路径；不在工程内时只保留路径末尾几段，避免铺满弹窗。 */
private fun pathRelativeToProjectHome(project: Project, file: java.io.File): String {
  val basePath = project.basePath ?: return shortenPathTail(file)
  return runCatching {
    val bp = Paths.get(basePath).toAbsolutePath().normalize()
    val fp = file.toPath().toAbsolutePath().normalize()
    if (fp.startsWith(bp)) {
      bp.relativize(fp).toString().replace('\\', '/')
    } else {
      shortenPathTail(file)
    }
  }.getOrDefault(shortenPathTail(file))
}

private fun revealIoFileInProjectView(project: Project, file: java.io.File) {
  val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: return
  ApplicationManager.getApplication().invokeLater {
    ProjectView.getInstance(project).select(project, vFile, true)
    // Don't activate Project toolwindow; it can steal focus and make this toolwindow look "closed"
    // (especially when user runs in WINDOWED mode).
  }
}

private fun zipPathLinkRow(project: Project, file: java.io.File): JComponent {
  val shortRaw = pathRelativeToProjectHome(project, file)
  val shortEsc = escapeHtmlForPackLog(shortRaw)
  val linkColor = packLogToneToCssColor(PackLogTone.PROGRESS)
  val lbl = JBLabel("<html><u><span style=\"color:$linkColor;\">$shortEsc</span></u></html>").apply {
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    toolTipText = file.absolutePath
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        revealIoFileInProjectView(project, file)
      }
    })
  }
  return NonOpaquePanel(BorderLayout()).apply {
    border = JBUI.Borders.emptyLeft(JBUI.scale(22))
    add(lbl, BorderLayout.WEST)
  }
}

class DatayooPickerToolWindowFactory : ToolWindowFactory, DumbAware {
  companion object {
    /** Must match `toolWindow id` in plugin.xml */
    private const val DATAYOO_TOOL_WINDOW_ID = "数由 GUI"
    // Maven plugin coordinates (must be resolvable in Maven local/remote repos)
    private const val DATAYOO_PLUGINX_VERSION = "1.0-SNAPSHOT"
    private const val DESCRIPTOR_PACK_GOAL = "org.datayoo.pluginx:descriptor-plugin:$DATAYOO_PLUGINX_VERSION:descriptorPack"
    private const val OYEZ_PACK_GOAL = "org.datayoo.pluginx:impl-maven-plugin:$DATAYOO_PLUGINX_VERSION:oyezPack"
    private const val MAVEN_CLEAN_GOAL = "clean"
    private const val MAVEN_PACKAGE_GOAL = "package"
    private const val MAVEN_INSTALL_GOAL = "install"
    private const val MAVEN_DEPLOY_GOAL = "deploy"
    private const val MAVEN_SKIP_TESTS_OPTION = "-DskipTests"
    /**
     * 仅构建当前目录这一枚工程，不递归执行 `<modules>`。
     * 用于「祖先 module 预置」：在聚合/父 POM 目录执行 `install` 时若不加此项，Maven 会顺带编译**全部**子 module，
     * 与用户仅勾选部分算子、未勾选的同仓库其它算子仍会被编译甚至报错（与列表勾选无关）。
     */
    private const val MAVEN_NON_RECURSIVE_OPTION = "-N"
  }

  private val logger = Logger.getInstance(DatayooPickerToolWindowFactory::class.java)
  private val ignoredDirectoryNames = setOf(".idea", ".git", ".gradle", "build", "out", "target")
  @Volatile
  private var lastHelpPreviewDebug: String = ""
  @Volatile
  private var lastScanDebug: String = ""
  @Volatile
  private var lastPackDebug: String = ""

  private fun invokeLaterOnWriteThreadCompat(action: () -> Unit) {
    val app = ApplicationManager.getApplication()
    val runnable = Runnable(action)
    try {
      val method = app.javaClass.methods.firstOrNull { m ->
        m.name == "invokeLaterOnWriteThread" &&
          m.parameterCount == 1 &&
          m.parameterTypes[0] == Runnable::class.java
      }
      if (method != null) {
        method.invoke(app, runnable)
        return
      }
    } catch (_: Throwable) {
      // ignore and fallback
    }
    app.invokeLater(runnable)
  }
  private data class ScanTarget(
    val key: String,
    val label: String
  ) {
    override fun toString(): String = label
  }

  override fun shouldBeAvailable(project: Project): Boolean = true

  private data class ResourceRef(
    val displayPath: String,
    val file: VirtualFile
  )

  private data class OperatorRow(
    val className: String,
    val name: String,
    val localizedName: String?,
    val displayName: String,
    /** 实现态类：同名 `@OpDefiner(name=...)` 且 `computionFramework != "sengee"` 的类（可能多个）。 */
    val implClasses: List<String>,
    /**
     * 扫描实现态命中该算子时写入的 **含 `pom.xml` 的 Maven module 根目录**（canonical 路径）；
     * 由源码文件向上 [findNearestPomDir] 解析，避免误用 `src/main/java` 等源码根作工作目录导致 `mvn -f pom.xml` 失败。
     */
    val implModulePomPath: String?,
    /**
     * 扫描定义态时在命中源码文件上用 [findNearestPomDir] 写入的 Maven module 根（canonical），
     * 与 [className] 对应 descriptor 源文件一致；打包 / 找 zip 时优先于事后反查。
     */
    val descriptorModulePomPath: String?,
    val type: String,
    val provider: String,
    val summary: String,
    val missingResources: List<String>,
    val resourceStatus: String,
    val i18nRefs: List<ResourceRef>,
    val portraitRefs: List<ResourceRef>,
    val helpRefs: List<ResourceRef>
  )

  private data class ResourceEntry(
    val normalizedStem: String,
    val ref: ResourceRef
  )

  private data class ResourceIndex(
    val i18nEntries: List<ResourceEntry>,
    val portraitEntries: List<ResourceEntry>,
    val helpEntries: List<ResourceEntry>
  )

  private fun collectScanRoots(rootCandidates: List<VirtualFile>): List<VirtualFile> {
    val rootsByPath = linkedMapOf<String, VirtualFile>()

    rootCandidates.forEach { candidateRoot ->
      VfsUtilCore.iterateChildrenRecursively(
        candidateRoot,
        { file -> file.isDirectory && file.name !in ignoredDirectoryNames }
      ) { dir ->
        if (dir.name.endsWith("descriptor", ignoreCase = true)) {
          rootsByPath.putIfAbsent(dir.path, dir)
        }
        true
      }
    }

    logger.info(
      "Descriptor search roots (${rootCandidates.size}): ${rootCandidates.joinToString(", ") { it.path }}"
    )
    return rootsByPath.values.toList()
  }

  private fun collectDefaultRootCandidates(project: Project): List<VirtualFile> {
    val rootCandidatesByPath = linkedMapOf<String, VirtualFile>()

    ProjectRootManager.getInstance(project).contentRoots.forEach { root ->
      rootCandidatesByPath.putIfAbsent(root.path, root)
    }

    val basePath = project.basePath
    if (!basePath.isNullOrBlank()) {
      val baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath.replace('\\', '/'))
      if (baseDir != null) {
        rootCandidatesByPath.putIfAbsent(baseDir.path, baseDir)
      }
    }

    return rootCandidatesByPath.values.toList()
  }

  private fun resolveScanCandidates(
    project: Project,
    scanTarget: ScanTarget?
  ): Pair<List<VirtualFile>, String> {
    val basePath = scanTarget?.key ?: project.basePath.orEmpty()
    if (basePath.isBlank()) {
      return emptyList<VirtualFile>() to "当前项目没有可用根目录"
    }
    val root = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath.replace('\\', '/'))
    return when {
      root == null -> emptyList<VirtualFile>() to "项目根目录不存在：$basePath"
      !root.isDirectory -> emptyList<VirtualFile>() to "项目根路径不是目录：$basePath"
      else -> listOf(root) to "项目根目录:${root.path}"
    }
  }

  private fun parseOperatorRow(
    raw: String,
    resources: ResourceIndex,
    implIndex: Map<String, List<String>>,
    implModulePomPathByOperator: Map<String, String>,
    descriptorModulePomPathByFqcn: Map<String, String>
  ): OperatorRow {
    val parts = raw.split(" | ")
    val className = parts.firstOrNull().orEmpty()
    val attributes = linkedMapOf<String, String>()
    for (part in parts.drop(1)) {
      val separatorIndex = part.indexOf('=')
      if (separatorIndex <= 0) continue
      val key = part.substring(0, separatorIndex).trim()
      val value = part.substring(separatorIndex + 1).trim()
      if (key.isNotEmpty()) attributes[key] = value
    }

    val name = attributes["name"] ?: "-"
    val normalizedName = normalizeOperatorName(name)
    val implClasses = implIndex[normalizedName].orEmpty()
    val implModulePomPath = implModulePomPathByOperator[normalizedName]
    val descriptorModulePomPath = descriptorModulePomPathByFqcn[className.trim()]
    val i18nRefs = pickResourcesByName(resources.i18nEntries, normalizedName, allowDotPrefix = true)
    val portraitRefs = pickPortraitResourcesByName(resources.portraitEntries, normalizedName)
    val helpRefs = pickResourcesByName(resources.helpEntries, normalizedName, allowDotPrefix = true)
    val localizedName = resolveLocalizedName(normalizedName, i18nRefs)
    val missingResources = mutableListOf<String>()
    if (helpRefs.isEmpty()) missingResources += "帮助文档"
    if (i18nRefs.isEmpty()) missingResources += "国际化"
    if (portraitRefs.isEmpty()) missingResources += "Logo"
    val displayName = if (!localizedName.isNullOrBlank()) "$name（$localizedName）" else name
    val resourceStatus = if (missingResources.isEmpty()) "完整" else "缺少：${missingResources.joinToString("、")}"

    return OperatorRow(
      className = className,
      name = name,
      localizedName = localizedName,
      displayName = displayName,
      implClasses = implClasses,
      implModulePomPath = implModulePomPath,
      descriptorModulePomPath = descriptorModulePomPath,
      type = attributes["type"] ?: "-",
      provider = attributes["provider"] ?: "-",
      summary = attributes["summary"] ?: "-",
      missingResources = missingResources,
      resourceStatus = resourceStatus,
      i18nRefs = i18nRefs,
      portraitRefs = portraitRefs,
      helpRefs = helpRefs
    )
  }

  private fun normalizeOperatorName(operatorName: String): String {
    return operatorName.trim().removeSurrounding("\"").lowercase()
  }

  private fun pickResourcesByName(
    entries: List<ResourceEntry>,
    normalizedName: String,
    allowDotPrefix: Boolean
  ): List<ResourceRef> {
    if (normalizedName.isBlank() || normalizedName == "-") return emptyList()

    val exact = entries
      .asSequence()
      .filter { it.normalizedStem == normalizedName }
      .map { it.ref }
      .distinctBy { it.file.path }
      .toList()
    if (exact.isNotEmpty()) return exact

    if (!allowDotPrefix) return emptyList()
    return entries
      .asSequence()
      .filter { it.normalizedStem.endsWith(".$normalizedName") }
      .map { it.ref }
      .distinctBy { it.file.path }
      .toList()
  }

  /**
   * portraits 下除 `算子名.svg` 外，常见 `算子名-bigPortrait.svg` 等：按无扩展名的**文件名**以
   * `算子名` 开头且后续为 `-` 或 `_`（或整段等于算子名）匹配，避免 `add` 误匹配 `addcolumns`。
   */
  private fun pickPortraitResourcesByName(
    entries: List<ResourceEntry>,
    normalizedName: String
  ): List<ResourceRef> {
    val base = pickResourcesByName(entries, normalizedName, allowDotPrefix = true)
    if (base.isNotEmpty()) return base
    if (normalizedName.isBlank() || normalizedName == "-") return emptyList()
    return entries
      .asSequence()
      .filter { e ->
        val stem = e.normalizedStem
        stem == normalizedName ||
          stem.startsWith("${normalizedName}-") ||
          stem.startsWith("${normalizedName}_")
      }
      .map { it.ref }
      .distinctBy { it.file.path }
      .toList()
  }

  private fun resolveLocalizedName(operatorName: String, i18nRefs: List<ResourceRef>): String? {
    if (operatorName.isBlank() || operatorName == "-") return null
    for (ref in i18nRefs) {
      val text = runCatching { loadTextWithFallback(ref.file) }.getOrNull() ?: continue
      val localized = extractLocalizedNameFromText(operatorName, text)
      if (!localized.isNullOrBlank()) {
        return localized
      }
    }
    return null
  }

  /**
   * 从标准 JSON 中取与算子根节点对应的中文名：只在 **`id` 或 `text` 与 [operatorName] 完全相等** 的对象里读 `i18ns.zh_CN`。
   * 不能用正则跨对象匹配：根节点未写 `zh_CN` 时，旧逻辑会误命中后续条目（如 `*.general` 的「一般」）。
   */
  private fun extractLocalizedZhFromJsonStructure(operatorName: String, root: JsonElement): String? {
    if (operatorName.isBlank() || operatorName == "-") return null
    val objects = ArrayList<JsonObject>(32)
    fun collectObjects(element: JsonElement) {
      when {
        element.isJsonObject -> {
          objects.add(element.asJsonObject)
          for (v in element.asJsonObject.entrySet()) {
            collectObjects(v.value)
          }
        }
        element.isJsonArray -> {
          for (e in element.asJsonArray) {
            collectObjects(e)
          }
        }
      }
    }
    collectObjects(root)

    fun zhFromI18ns(obj: JsonObject): String? {
      val i18ns = obj.getAsJsonObject("i18ns") ?: return null
      val zhEl = i18ns.get("zh_CN") ?: i18ns.get("zh-CN") ?: return null
      if (!zhEl.isJsonPrimitive || !zhEl.asJsonPrimitive.isString) return null
      val s = zhEl.asJsonPrimitive.asString.trim()
      if (s.isBlank() || !containsChinese(s)) return null
      return s
    }

    for (obj in objects) {
      val idEl = obj.get("id")
      val textEl = obj.get("text")
      val id = idEl?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
      val textVal = textEl?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
      if (id == operatorName || textVal == operatorName) {
        return zhFromI18ns(obj)
      }
    }
    return null
  }

  private fun extractLocalizedNameFromText(operatorName: String, text: String): String? {
    fun normalizeCandidate(raw: String): String {
      return raw
        .trim()
        .trim(',', ';')
        .trim()
        .removePrefix("\"")
        .removeSuffix("\"")
        .removePrefix("'")
        .removeSuffix("'")
        .trim()
    }

    fun firstChineseValue(regex: Regex): String? {
      return regex.findAll(text)
        .map { normalizeCandidate(it.groupValues[1]) }
        .firstOrNull { containsChinese(it) }
    }

    val trimmed = text.trim()
    if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
      val parsed = runCatching { JsonParser.parseString(trimmed) }.getOrNull()
      if (parsed != null) {
        return extractLocalizedZhFromJsonStructure(operatorName, parsed)
      }
    }

    val escapedName = Regex.escape(operatorName)

    // YAML / properties / 非严格 JSON：保留下列启发式（勿再用跨对象 `[\s\S]*?zh_CN` 命中子节点「一般」）

    val directJsonPatterns = listOf(
      Regex("(?is)\"$escapedName\"\\s*:\\s*\"([^\"]+)\""),
      Regex("(?is)'$escapedName'\\s*:\\s*'([^']+)'"),
      Regex("(?im)^\\s*$escapedName\\s*[=:]\\s*([^\\r\\n#]+)")
    )
    for (pattern in directJsonPatterns) {
      val value = firstChineseValue(pattern)
      if (!value.isNullOrBlank()) return value
    }

    val keyWithSuffixPatterns = listOf(
      Regex("(?is)\"$escapedName\\.[^\"]*\"\\s*:\\s*\"([^\"]+)\""),
      Regex("(?im)^\\s*$escapedName\\.[^\\s=:]+\\s*[=:]\\s*([^\\r\\n#]+)")
    )
    for (pattern in keyWithSuffixPatterns) {
      val value = firstChineseValue(pattern)
      if (!value.isNullOrBlank()) return value
    }

    return firstChineseValue(Regex("(?s)[\"']([^\"'\\r\\n]*[\\u4e00-\\u9fff][^\"'\\r\\n]*)[\"']"))
  }

  private fun containsChinese(text: String): Boolean {
    return text.any { it in '\u4e00'..'\u9fff' }
  }

  private fun buildResourceIndex(roots: List<VirtualFile>): ResourceIndex {
    val i18nEntries = linkedMapOf<String, ResourceEntry>()
    val portraitEntries = linkedMapOf<String, ResourceEntry>()
    val helpEntries = linkedMapOf<String, ResourceEntry>()

    roots.forEach { descriptorRoot ->
      val resourcesRoot = descriptorRoot.findFileByRelativePath("src/main/resources") ?: return@forEach
      if (!resourcesRoot.isDirectory) return@forEach

      val i18nDir = resourcesRoot.findChild("i18n") ?: resourcesRoot.findChild("i18ns")
      if (i18nDir != null && i18nDir.isDirectory) {
        indexResourceDirectory(
          descriptorRoot = descriptorRoot,
          baseDir = resourcesRoot,
          targetDir = i18nDir,
          targetEntries = i18nEntries,
          allowedExtensions = setOf("json", "yml", "yaml", "properties")
        )
      }

      val portraitsDir = resourcesRoot.findChild("portraits")
      if (portraitsDir != null && portraitsDir.isDirectory) {
        indexResourceDirectory(
          descriptorRoot = descriptorRoot,
          baseDir = resourcesRoot,
          targetDir = portraitsDir,
          targetEntries = portraitEntries,
          allowedExtensions = setOf("png", "jpg", "jpeg", "svg", "webp", "gif", "bmp")
        )
      }

      val helpsDir = resourcesRoot.findChild("helps")
      if (helpsDir != null && helpsDir.isDirectory) {
        indexResourceDirectory(
          descriptorRoot = descriptorRoot,
          baseDir = resourcesRoot,
          targetDir = helpsDir,
          targetEntries = helpEntries,
          allowedExtensions = setOf("md", "markdown", "txt", "html", "htm")
        )
      }
    }

    logger.info(
      "Resource index built: i18n=${i18nEntries.size}, portraits=${portraitEntries.size}, helps=${helpEntries.size}"
    )

    return ResourceIndex(
      i18nEntries = i18nEntries.values.toList(),
      portraitEntries = portraitEntries.values.toList(),
      helpEntries = helpEntries.values.toList()
    )
  }

  private fun indexResourceDirectory(
    descriptorRoot: VirtualFile,
    baseDir: VirtualFile,
    targetDir: VirtualFile,
    targetEntries: MutableMap<String, ResourceEntry>,
    allowedExtensions: Set<String>
  ) {
    VfsUtilCore.iterateChildrenRecursively(targetDir, null) { file ->
      if (file.isDirectory) {
        if (file.name in ignoredDirectoryNames) return@iterateChildrenRecursively false
        return@iterateChildrenRecursively true
      }

      val extension = file.extension?.lowercase() ?: return@iterateChildrenRecursively true
      if (extension !in allowedExtensions) return@iterateChildrenRecursively true

      val relativePath = file.path.removePrefix(baseDir.path).trimStart('/').replace('\\', '/')
      val displayPath = "${descriptorRoot.name}/$relativePath"
      val normalizedStem = file.nameWithoutExtension.trim().lowercase()
      if (normalizedStem.isNotBlank()) {
        targetEntries.putIfAbsent(
          file.path,
          ResourceEntry(
            normalizedStem = normalizedStem,
            ref = ResourceRef(displayPath = displayPath, file = file)
          )
        )
      }
      true
    }
  }

  private fun loadHelpDocument(refs: List<ResourceRef>): String {
    if (refs.isEmpty()) return "帮助文档未匹配到资源"
    val target = refs.firstOrNull { !it.file.isDirectory } ?: return "帮助文档未匹配到资源"
    return runCatching { loadTextWithFallback(target.file) }
      .getOrElse { "读取帮助文档失败：${it.message ?: "未知错误"}" }
  }

  private fun buildI18nHtml(row: OperatorRow): String {
    val fontFamily = escapeHtml(UIManager.getFont("Label.font")?.family ?: "Dialog")
    val hasI18n = row.i18nRefs.isNotEmpty()
    val statusText = if (hasI18n) "已匹配" else "未匹配"
    val statusColor = if (hasI18n) "#2B7A0B" else "#A1260D"
    val fileRows = if (hasI18n) {
      row.i18nRefs.joinToString("") { ref ->
        "<tr><td style='border:1px solid #C7CCD1; padding:6px;'>${escapeHtml(ref.displayPath)}</td></tr>"
      }
    } else {
      "<tr><td style='border:1px solid #C7CCD1; padding:6px; color:#6E7781;'>未找到对应国际化资源</td></tr>"
    }
    val localizedName = row.localizedName?.takeIf { it.isNotBlank() } ?: "-"
    return """
      <html>
        <body style="font-family:'$fontFamily'; font-size:12px; margin:8px;">
          <div style="margin-bottom:8px;">显示名称：${escapeHtml(localizedName)}</div>
          <div style="margin-bottom:8px;">匹配状态：<span style="color:$statusColor; font-weight:700;">$statusText</span></div>
          <table style="border-collapse:collapse; width:100%;">
            <thead>
              <tr>
                <th style="border:1px solid #C7CCD1; padding:6px; text-align:left;">国际化资源文件</th>
              </tr>
            </thead>
            <tbody>
              $fileRows
            </tbody>
          </table>
        </body>
      </html>
    """.trimIndent()
  }

  private fun buildI18nJsonText(row: OperatorRow): String {
    val matchedRefs = row.i18nRefs.filter { !it.file.isDirectory }
    if (matchedRefs.isEmpty()) {
      return """
        {
          "matched": false,
          "operatorName": "${escapeJson(row.name)}",
          "message": "未找到对应国际化资源"
        }
      """.trimIndent()
    }

    val selectedRef = matchedRefs.minWithOrNull(
      compareBy<ResourceRef> { ref ->
        if (ref.file.extension.equals("json", ignoreCase = true)) 0 else 1
      }.thenBy { it.displayPath }
    ) ?: matchedRefs.first()

    return runCatching { loadTextWithFallback(selectedRef.file) }
      .map { text -> text.ifBlank { "{}" } }
      .getOrElse { error ->
        """
          {
            "matched": true,
            "operatorName": "${escapeJson(row.name)}",
            "file": "${escapeJson(selectedRef.displayPath)}",
            "error": "${escapeJson(error.message ?: "读取失败")}"
          }
        """.trimIndent()
      }
  }

  private fun loadTextWithFallback(file: VirtualFile): String {
    val bytes = VfsUtilCore.loadBytes(file)
    val utf8Text = bytes.toString(StandardCharsets.UTF_8)
    if (!looksGarbled(utf8Text)) return utf8Text

    val fallbackCharsets = listOf(
      Charset.forName("GB18030"),
      Charset.forName("GBK"),
      StandardCharsets.UTF_16LE,
      StandardCharsets.UTF_16BE
    )
    fallbackCharsets.forEach { charset ->
      val text = bytes.toString(charset)
      if (!looksGarbled(text)) {
        return text
      }
    }
    return utf8Text
  }

  private fun looksGarbled(text: String): Boolean {
    if (text.isEmpty()) return false
    if (text.contains('\u0000')) return true
    if (text.contains('\uFFFD')) return true
    return text.contains("锟斤拷")
  }

  private fun escapeHtml(raw: String): String {
    return raw
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")
  }

  private fun escapeJson(raw: String): String {
    return raw
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\r", "\\r")
      .replace("\n", "\\n")
      .replace("\t", "\\t")
  }

  private fun prettyPrintJson(raw: String): String {
    val input = raw.trim()
    if (input.isEmpty()) return input
    val out = StringBuilder(input.length + 32)
    var indent = 0
    var inString = false
    var escaped = false
    var justAddedNewline = false

    fun appendIndent() {
      repeat(indent) { out.append("  ") }
      justAddedNewline = false
    }

    input.forEach { ch ->
      when {
        inString -> {
          out.append(ch)
          if (escaped) {
            escaped = false
          } else if (ch == '\\') {
            escaped = true
          } else if (ch == '"') {
            inString = false
          }
          justAddedNewline = false
        }
        ch == '"' -> {
          if (justAddedNewline) appendIndent()
          out.append(ch)
          inString = true
          justAddedNewline = false
        }
        ch == '{' || ch == '[' -> {
          if (justAddedNewline) appendIndent()
          out.append(ch).append('\n')
          indent += 1
          justAddedNewline = true
        }
        ch == '}' || ch == ']' -> {
          if (indent == 0) throw IllegalArgumentException("JSON 结构不合法")
          indent -= 1
          if (!justAddedNewline) out.append('\n')
          appendIndent()
          out.append(ch)
          justAddedNewline = false
        }
        ch == ',' -> {
          out.append(ch).append('\n')
          justAddedNewline = true
        }
        ch == ':' -> {
          out.append(": ")
          justAddedNewline = false
        }
        ch.isWhitespace() -> {
          // 忽略原有空白，由格式化逻辑统一输出
        }
        else -> {
          if (justAddedNewline) appendIndent()
          out.append(ch)
          justAddedNewline = false
        }
      }
    }

    if (inString || indent != 0) throw IllegalArgumentException("JSON 结构不合法")
    return out.toString()
  }

  private fun pickReadableUiFont(size: Int): Font {
    val labelFont = UIManager.getFont("Label.font")
    val availableFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
    val preferredFamilies = listOf(
      "Microsoft YaHei UI",
      "Microsoft YaHei",
      "PingFang SC",
      "Noto Sans CJK SC",
      "SimSun",
      "Dialog"
    )
    val chosenFamily = preferredFamilies.firstOrNull { it in availableFamilies } ?: labelFont?.family ?: "Dialog"
    val resolvedSize = if (size > 0) size else (labelFont?.size ?: 12)
    return Font(chosenFamily, Font.PLAIN, resolvedSize)
  }

  private fun fitImageForPreview(image: java.awt.Image, maxSize: Int): ImageIcon {
    val srcWidth = image.getWidth(null)
    val srcHeight = image.getHeight(null)
    if (srcWidth <= 0 || srcHeight <= 0) return ImageIcon(image)

    val scale = minOf(maxSize.toDouble() / srcWidth.toDouble(), maxSize.toDouble() / srcHeight.toDouble())
    if (scale <= 0.0) return ImageIcon(image)

    val targetWidth = maxOf(1, (srcWidth * scale).toInt())
    val targetHeight = maxOf(1, (srcHeight * scale).toInt())
    val output = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val g2 = output.createGraphics()
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.drawImage(image, 0, 0, targetWidth, targetHeight, null)
    g2.dispose()
    return ImageIcon(output)
  }

  private fun loadSvgForPreview(bytes: ByteArray, targetMaxSize: Int): java.awt.Image? {
    val source = bytes.toString(StandardCharsets.UTF_8)
    val widthMatch = Regex("""\bwidth\s*=\s*["']\s*([0-9]+(?:\.[0-9]+)?)(?:px)?\s*["']""").find(source)
    val heightMatch = Regex("""\bheight\s*=\s*["']\s*([0-9]+(?:\.[0-9]+)?)(?:px)?\s*["']""").find(source)
    val viewBoxMatch =
      Regex("""\bviewBox\s*=\s*["']\s*[-+]?[0-9]*\.?[0-9]+\s+[-+]?[0-9]*\.?[0-9]+\s+([0-9]*\.?[0-9]+)\s+([0-9]*\.?[0-9]+)""")
        .find(source)

    val baseWidth = widthMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
      ?: viewBoxMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    val baseHeight = heightMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
      ?: viewBoxMatch?.groupValues?.getOrNull(2)?.toDoubleOrNull()
    val baseMax = maxOf(baseWidth ?: 0.0, baseHeight ?: 0.0)
    val safeBaseMax = if (baseMax > 0.0) baseMax else 24.0
    val scale = (targetMaxSize.toDouble() / safeBaseMax).coerceAtLeast(0.05).toFloat()
    return runCatching { SVGLoader.load(ByteArrayInputStream(bytes), scale) }.getOrNull()
  }

  private fun markdownToHtml(markdown: String): String {
    if (markdown.isBlank()) return "<html><body></body></html>"
    val normalizedMarkdown = markdown.replace("\r\n", "\n").replace('\r', '\n')
    val flavour = GFMFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(normalizedMarkdown)
    val body = runCatching {
      HtmlGenerator(normalizedMarkdown, parsedTree, flavour).generateHtml()
    }.getOrElse { error ->
      logger.warn("Failed to render markdown with IntelliJ parser, fallback to escaped text", error)
      "<pre>${escapeHtml(normalizedMarkdown)}</pre>"
    }
    val uiFontFamily = escapeHtml(UIManager.getFont("Label.font")?.family ?: "Dialog")
    val typographyStyle = """
      body { font-family: '$uiFontFamily'; font-size: 12px; margin: 8px; line-height: 1.6; }
      p { margin: 0.4em 0; }
      h1, h2, h3, h4, h5, h6 { margin: 0.8em 0 0.35em; font-weight: 700; }
      h1 { font-size: 1.5em; }
      h2 { font-size: 1.35em; }
      h3 { font-size: 1.2em; }
      h4 { font-size: 1.1em; }
      h5 { font-size: 1em; }
      h6 { font-size: 1em; }
      ul, ol { margin: 0.35em 0 0.35em 1.2em; padding: 0; }
      li { margin: 0.2em 0; }
      pre, code { font-family: 'Consolas', 'JetBrains Mono', monospace; }
    """.trimIndent()

    return """
      <html>
        <head>
          <style>
            $typographyStyle
          </style>
        </head>
        <body>
          $body
        </body>
      </html>
    """.trimIndent()
  }

  private fun createNativeHelpEditor(project: Project, helpFile: VirtualFile): FileEditor? {
    val providers = FileEditorProvider.EP_FILE_EDITOR_PROVIDER.extensionList
    if (providers.isEmpty()) {
      lastHelpPreviewDebug = "No FileEditorProvider registered."
      return null
    }

    fun pickProvider(): FileEditorProvider? {
      val accepted = providers.asSequence().filter { provider ->
        runCatching { provider.accept(project, helpFile) }.getOrDefault(false)
      }
      return accepted.firstOrNull { it.javaClass.name.contains("MarkdownPreview", ignoreCase = true) }
        ?: accepted.firstOrNull { it.javaClass.name.contains("Preview", ignoreCase = true) && it.javaClass.name.contains("Markdown", ignoreCase = true) }
        ?: accepted.firstOrNull { it.javaClass.name.startsWith("org.intellij.plugins.markdown.") }
        ?: accepted.firstOrNull { it.javaClass.name.contains("Markdown") }
        ?: accepted.firstOrNull { it.javaClass.name.contains("markdown", ignoreCase = true) }
        ?: accepted.firstOrNull()
    }

    val provider = pickProvider() ?: return null
    val providerName = provider.javaClass.name
    var debug = buildString {
      appendLine("file: ${helpFile.path}")
      appendLine("provider: $providerName")
    }

    val editorResult = runCatching { provider.createEditor(project, helpFile) }
    val editor = editorResult.getOrNull()
    if (editor == null) {
      val err = editorResult.exceptionOrNull()
      debug += if (err != null) "createEditor error: ${err.javaClass.name}: ${err.message ?: "(no message)"}\n" else "createEditor returned null.\n"
      lastHelpPreviewDebug = debug.trimEnd()
      return null
    }

    debug += "editor: ${editor.javaClass.name}\n"
    if (editor is TextEditorWithPreview) {
      runCatching { editor.setLayout(TextEditorWithPreview.Layout.SHOW_PREVIEW) }
      debug += "layout: SHOW_PREVIEW\n"
    }
    lastHelpPreviewDebug = debug.trimEnd()
    return editor
  }

  /**
   * Toggle the plugin tool window into a separate window (maximized) and back to docked mode.
   */
  private fun toggleDatayooToolWindowWindowed(project: Project) {
    ApplicationManager.getApplication().invokeLater {
      val tw = ToolWindowManager.getInstance(project).getToolWindow(DATAYOO_TOOL_WINDOW_ID) ?: return@invokeLater
      val currentType = tw.type
      val targetType = if (currentType == ToolWindowType.WINDOWED) ToolWindowType.DOCKED else ToolWindowType.WINDOWED
      tw.setType(targetType, null)
      tw.activate(null)

      if (targetType == ToolWindowType.WINDOWED) {
        // Maximize the separate tool window once it's created
        ApplicationManager.getApplication().invokeLater {
          val rootComponent = ((tw as? ToolWindowEx)?.decorator as? java.awt.Component) ?: tw.component
          val window = SwingUtilities.getWindowAncestor(rootComponent)
          val frame = window as? Frame
          frame?.let {
            runCatching { it.extendedState = it.extendedState or Frame.MAXIMIZED_BOTH }
          }
        }
      }
    }
  }

  private fun findNearestPomDir(start: VirtualFile): VirtualFile? {
    var current: VirtualFile? = start
    while (current != null) {
      val pom = current.findChild("pom.xml")
      if (pom != null && !pom.isDirectory) return current
      current = current.parent
    }
    return null
  }

  private fun findSourceFileByClassName(project: Project, className: String): VirtualFile? {
    if (className.isBlank() || className == "-") return null
    val normalizedClassName = className.substringBefore('$')
    val classPath = normalizedClassName.replace('.', '/')
    val candidates = listOf(
      "src/main/java/$classPath.java",
      "src/main/kotlin/$classPath.kt",
      "src/test/java/$classPath.java",
      "src/test/kotlin/$classPath.kt"
    )

    ProjectRootManager.getInstance(project).contentRoots.forEach { root ->
      candidates.forEach { relativePath ->
        val found = root.findFileByRelativePath(relativePath)
        if (found != null && !found.isDirectory) return found
      }
    }

    val simpleName = normalizedClassName.substringAfterLast('.')
    val sourceFileNames = setOf("$simpleName.java", "$simpleName.kt")
    ProjectRootManager.getInstance(project).contentRoots.forEach { root ->
      var matched: VirtualFile? = null
      VfsUtilCore.iterateChildrenRecursively(
        root,
        { file -> file.isDirectory && file.name !in ignoredDirectoryNames }
      ) { file ->
        if (!file.isDirectory && file.name in sourceFileNames) {
          matched = file
          return@iterateChildrenRecursively false
        }
        true
      }
      if (matched != null) return matched
    }
    return null
  }

  /**
   * 仅按 `src/main/java|kotlin` 等**精确相对路径**解析，不用简单类名全仓库扫描。
   * 避免 `...transformer.file` 这类错误 FQCN 误命中别处的 `File.java`/`file.kt`，导致插件传的 `impl.includes` 与 IDEA 里手点 oyezPack（不带错 includes）行为不一致。
   */
  private fun findSourceFileByFqcnStrict(project: Project, className: String): VirtualFile? {
    if (className.isBlank() || className == "-") return null
    val normalizedClassName = className.substringBefore('$').trim()
    val classPath = normalizedClassName.replace('.', '/')
    val candidates = listOf(
      "src/main/java/$classPath.java",
      "src/main/kotlin/$classPath.kt",
      "src/test/java/$classPath.java",
      "src/test/kotlin/$classPath.kt"
    )
    ProjectRootManager.getInstance(project).contentRoots.forEach { root ->
      candidates.forEach { relativePath ->
        val found = root.findFileByRelativePath(relativePath)
        if (found != null && !found.isDirectory) return found
      }
    }
    return null
  }

  private fun safeArtifactBaseName(name: String): String {
    val trimmed = name.trim().ifBlank { "output" }
    return trimmed.replace(Regex("""[\\/:*?"<>|]"""), "_")
  }

  private fun buildMavenCmdOptions(existing: String?, extra: List<String>): String? {
    val normalizedExisting = existing?.trim().orEmpty()
    val parts = buildList {
      if (normalizedExisting.isNotBlank()) add(normalizedExisting)
      extra.mapNotNullTo(this) { it.trim().takeIf { t -> t.isNotBlank() } }
    }
    return parts.joinToString(" ").trim().ifBlank { null }
  }

  private fun createMavenParams(
    pomDir: VirtualFile,
    goals: List<String>,
    cmdOptions: String?
  ): MavenRunnerParameters {
    return MavenRunnerParameters(
      true,
      pomDir.path,
      "pom.xml",
      goals,
      emptyList<String>()
    ).also { it.cmdOptions = cmdOptions }
  }

  /** 用于路径前缀比较：统一分隔符并去掉末尾 `/`。 */
  private fun normalizeFsPathSlashes(path: String): String =
    path.replace('\\', '/').trimEnd('/')

  private fun canonicalNormalizedFsPath(path: String): String =
    normalizeFsPathSlashes(
      runCatching { java.io.File(path).canonicalFile.absolutePath }.getOrDefault(path)
    )

  /**
   * [descendant] 是否为 [ancestor] 之下的真实子目录（同名前缀误匹配排除）。
   */
  private fun isStrictFilesystemSubdir(ancestorNorm: String, descendantNorm: String): Boolean {
    val a = ancestorNorm.trimEnd('/')
    val d = descendantNorm.trimEnd('/')
    if (a.isEmpty() || d.isEmpty() || a == d) return false
    if (!d.startsWith(a)) return false
    if (d.length <= a.length) return false
    return d[a.length] == '/'
  }

  /**
   * 在 IDEA 已导入的 Maven module 中，找出「文件系统路径上是所选 module 目录的严格祖先」的 module。
   * 对多 module 工程里仅打包子 module 时，先对这些祖先执行 `install` 可把 parent POM 等写入本地仓库，
   * 避免子 module 单独执行 `package` 时出现 `Could not find artifact ...:pom:...`。
   */
  private fun collectStrictAncestorMavenModulePomDirs(project: Project, seedPomDirs: Collection<VirtualFile>): List<VirtualFile> {
    val mpm = MavenProjectsManager.getInstance(project)
    val managed = runCatching { mpm.nonIgnoredProjects }.getOrNull().orEmpty()
    if (managed.isEmpty()) return emptyList()
    val seeds = seedPomDirs.map { canonicalNormalizedFsPath(it.path) }.filter { it.isNotBlank() }.toSet()
    if (seeds.isEmpty()) return emptyList()
    val managedByCanon = LinkedHashMap<String, VirtualFile>()
    for (mp in managed) {
      val pomVf = runCatching { mp.file }.getOrNull() ?: continue
      val vf = pomVf.parent ?: continue
      if (!vf.isValid || !vf.isDirectory) continue
      val canon = canonicalNormalizedFsPath(vf.path)
      managedByCanon.putIfAbsent(canon, vf)
    }
    val ancestorCanons = linkedSetOf<String>()
    for ((managedCanon, _) in managedByCanon) {
      if (seeds.any { seed -> isStrictFilesystemSubdir(managedCanon, seed) }) {
        ancestorCanons += managedCanon
      }
    }
    return ancestorCanons.sortedWith(compareBy({ it.length }, { it })).mapNotNull { managedByCanon[it] }
  }

  private fun buildPreflightCleanInstallForAncestorPomDirs(ancestorPomDirs: List<VirtualFile>): List<MavenRunnerParameters> {
    return ancestorPomDirs.map { pomDir ->
      createMavenParams(
        pomDir,
        listOf(MAVEN_CLEAN_GOAL, MAVEN_INSTALL_GOAL),
        buildMavenCmdOptions(
          existing = null,
          extra = listOf(MAVEN_SKIP_TESTS_OPTION, MAVEN_NON_RECURSIVE_OPTION)
        )
      )
    }
  }

  private fun formatMavenStep(index: Int, total: Int, params: MavenRunnerParameters): String {
    val baseDirName = runCatching { java.io.File(params.workingDirPath).name }.getOrDefault(params.workingDirPath)
    val goalsText = params.goals.joinToString(" ")
    val opts = params.cmdOptions?.takeIf { it.isNotBlank() } ?: "(none)"
    val classHint = runCatching {
      val opt = params.cmdOptions ?: return@runCatching null
      val key = when {
        opt.contains("-Ddescriptor.includes=") -> "descriptor.includes"
        opt.contains("-Dimpl.includes=") -> "impl.includes"
        else -> return@runCatching null
      }
      val value = opt.substringAfter("-D$key=", "").substringBefore(' ').trim()
      if (value.isBlank()) return@runCatching null
      val pieces = value.split(',').map { it.trim() }.filter { it.isNotBlank() }
      val first = pieces.firstOrNull { fq ->
        val rel = "target/classes/" + fq.replace('.', '/') + ".class"
        java.io.File(params.workingDirPath, rel).exists()
      } ?: pieces.firstOrNull().orEmpty()
      if (first.isBlank()) return@runCatching null
      val rel = "target/classes/" + first.replace('.', '/') + ".class"
      val f = java.io.File(params.workingDirPath, rel)
      if (!f.exists()) return@runCatching "class=MISSING($rel)"
      val bytes = runCatching { f.readBytes() }.getOrNull() ?: return@runCatching "class=OK unreadable"
      val hasOpDefiner = bytes
        .toString(Charsets.ISO_8859_1)
        .contains("Lorg/datayoo/sengee/annotation/OpDefiner;")
      val annHint = if (hasOpDefiner) "opdef=YES" else "opdef=NO"
      "class=OK $annHint"
    }.getOrNull()
    val suffix = classHint?.let { " $it" } ?: ""
    return "[$index/$total] pomDir=$baseDirName goals=$goalsText opts=$opts$suffix"
  }

  private fun runMavenSequential(
    project: Project,
    statusArea: JBTextArea,
    steps: List<MavenRunnerParameters>,
    title: String,
    appendDebug: (String, PackLogTone) -> Unit,
    onPackZipProduced: ((OssUploader.PackKind, java.io.File) -> Unit)? = null,
    onAllSucceeded: (() -> Unit)? = null,
    onStepFailed: ((MavenRunnerParameters, Int) -> Unit)? = null,
    /** 将当前步骤的 [OSProcessHandler] 绑定到「运行」工具窗口（attachToProcess + descriptor.processHandler → Run 左侧停止） */
    bindMavenStepHandler: ((OSProcessHandler) -> Unit)? = null,
    /** 打包会话彻底结束（成功、失败、用户停止）时清除 Run 窗口上的进程绑定 */
    onPackSessionFinished: (() -> Unit)? = null,
    /** 单步成功且未因 zip 缺失中止后回调（连接器打包等扩展用；算子流程不传） */
    onStepSucceeded: ((MavenRunnerParameters) -> Unit)? = null
  ) {
    if (steps.isEmpty()) return
    val packCoordinator = runCatching { project.getService(MavenPackCoordinator::class.java) }.getOrNull()
    if (packCoordinator != null && !packCoordinator.tryBeginPack()) {
      appendDebug(
        "PACK 已拒绝：本工程已有打包任务在执行。并行 Maven 会互相 clean / 争抢 target/classes，" +
          "descriptorPack 易出现 descriptor.includes matched 0 operators；请等待当前打包结束后再试。",
        PackLogTone.DEFAULT
      )
      ApplicationManager.getApplication().invokeLater {
        statusArea.text = "打包未开始：已有进行中的打包任务（见打包调试）"
      }
      return
    }
    fun releasePackLock() {
      packCoordinator?.endPack()
      onPackSessionFinished?.invoke()
    }
    val idx = AtomicInteger(0)

    fun mvnExecutable(): String {
      val osName = System.getProperty("os.name")?.lowercase().orEmpty()
      val isWin = osName.contains("win")

      val generalSettings = runCatching { MavenProjectsManager.getInstance(project).generalSettings }.getOrNull()
      val mavenHome = runCatching { generalSettings?.mavenHome }.getOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

      if (!mavenHome.isNullOrBlank()) {
        val bin = java.io.File(mavenHome, "bin")
        val exe = if (isWin) java.io.File(bin, "mvn.cmd") else java.io.File(bin, "mvn")
        if (exe.exists()) return exe.absolutePath
      }

      // Fallback to PATH
      return if (isWin) "mvn.cmd" else "mvn"
    }

    fun buildMvnCommand(params: MavenRunnerParameters): GeneralCommandLine {
      val goals = params.goals
      val options = params.cmdOptions
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.split(Regex("\\s+"))
        .orEmpty()

      val generalSettings = runCatching { MavenProjectsManager.getInstance(project).generalSettings }.getOrNull()
      val userSettings = runCatching { generalSettings?.userSettingsFile }.getOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
      val localRepo = runCatching { generalSettings?.localRepository }.getOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

      fun projectJdkHome(project: Project): String? {
        val sdk: Sdk? = runCatching { ProjectRootManager.getInstance(project).projectSdk }.getOrNull()
        val home = sdk?.homePath?.trim().orEmpty()
        return home.takeIf { it.isNotBlank() }
      }

      val jdkHome = projectJdkHome(project)
      val osName = System.getProperty("os.name")?.lowercase().orEmpty()
      val isWin = osName.contains("win")

      // Use workingDir + -f pom.xml to be explicit (some modules are nested)
      val cmd = mutableListOf<String>()
      cmd += mvnExecutable()
      cmd += "-B"
      cmd += "-f"
      cmd += params.pomFileName
      if (!userSettings.isNullOrBlank()) {
        cmd += "-s"
        cmd += userSettings
      }
      cmd += goals
      if (!localRepo.isNullOrBlank()) {
        cmd += "-Dmaven.repo.local=$localRepo"
      }
      cmd += options

      val commandLine = GeneralCommandLine(cmd)
        .withWorkDirectory(params.workingDirPath)
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      // Windows 下 javac/Maven 子进程默认控制台常为 ANSI/OEM（多为 GBK 系）；不按 UTF-8 解码会出现控制台「菱形问号」乱码。
      if (isWin) {
        commandLine.withCharset(Charset.forName("GB18030"))
      }

      // Force Maven to run with the project's configured JDK, not the user's system JRE.
      if (!jdkHome.isNullOrBlank()) {
        commandLine.withEnvironment("JAVA_HOME", jdkHome)
        val binDir = java.io.File(jdkHome, "bin").absolutePath
        val currentPath = System.getenv("PATH").orEmpty()
        val sep = if (isWin) ";" else ":"
        val newPath = if (currentPath.isBlank()) binDir else "$binDir$sep$currentPath"
        commandLine.withEnvironment("PATH", newPath)
      }
      return commandLine
    }

    fun runNext() {
      // ConsoleView / Run 窗口绑定必须在 EDT；processTerminated 等回调可能在非 EDT。
      val app = ApplicationManager.getApplication()
      if (!app.isDispatchThread) {
        app.invokeLater { runNext() }
        return
      }
      val currentIndex = idx.getAndIncrement()
      if (currentIndex >= steps.size) {
        ApplicationManager.getApplication().invokeLater {
          statusArea.text = "打包完成：$title（共 ${steps.size} 步）"
        }
        appendDebug("== 完成：$title（共 ${steps.size} 步）==", PackLogTone.DEFAULT)
        onAllSucceeded?.invoke()
        releasePackLock()
        return
      }
      val stepNo = currentIndex + 1
      val params = steps[currentIndex]
      val stepLine = runCatching { formatMavenStep(stepNo, steps.size, params) }
        .getOrDefault("[$stepNo/${steps.size}] goals=${params.goals.joinToString(" ")}")
      ApplicationManager.getApplication().invokeLater {
        statusArea.text = "正在执行：$stepLine"
      }
      appendDebug("START $stepLine", PackLogTone.DEFAULT)

      val commandLine = runCatching { buildMvnCommand(params) }.getOrElse { e ->
        appendDebug("ERROR building mvn cmd: ${e.javaClass.name}: ${e.message ?: "(no message)"}", PackLogTone.ERROR)
        ApplicationManager.getApplication().invokeLater {
          statusArea.text = "打包失败：无法构造 mvn 命令（${e.message ?: "未知错误"}）"
        }
        releasePackLock()
        return
      }
      appendDebug("CMD  ${commandLine.commandLineString}", PackLogTone.DEFAULT)

      fun refreshModuleVfsAfterStep() {
        runCatching {
          val workDir = java.io.File(params.workingDirPath)
          val targetDir = java.io.File(workDir, "target")
          // Only refresh if dirs actually exist (mvn clean may have deleted target/)
          if (targetDir.isDirectory) {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetDir)?.let { vf ->
              if (vf.isValid) vf.refresh(true, true)
            }
          }
          if (workDir.isDirectory) {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(workDir)?.let { vf ->
              if (vf.isValid) vf.refresh(true, true)
            }
          }
          workDir.parentFile?.takeIf { it.isDirectory }?.let { parent ->
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parent)?.let { vf ->
              if (vf.isValid) vf.refresh(true, true)
            }
          }
        }.onFailure {
          appendDebug("WARN vfs refresh failed: ${it.message}", PackLogTone.WARN)
        }
      }

      val handler = try {
        OSProcessHandler(commandLine)
      } catch (e: Throwable) {
        appendDebug("ERROR starting mvn: ${e.javaClass.name}: ${e.message ?: "(no message)"}", PackLogTone.ERROR)
        ApplicationManager.getApplication().invokeLater {
          statusArea.text = "打包失败：启动 mvn 进程失败（${e.message ?: "未知错误"}）"
        }
        releasePackLock()
        return
      }

      runCatching { bindMavenStepHandler?.invoke(handler) }
        .onFailure { ex -> appendDebug("WARN bind Run window process: ${ex.message}", PackLogTone.WARN) }

      packCoordinator?.attachMavenHandler(handler)

      handler.addProcessListener(object : ProcessAdapter() {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          val text = event.text.trimEnd()
          if (text.isBlank()) return
          val prefix = when (outputType) {
            ProcessOutputTypes.STDERR -> "ERR"
            ProcessOutputTypes.SYSTEM -> "SYS"
            else -> "OUT"
          }
          appendDebug("$prefix $text", PackLogTone.DEFAULT)
        }

        override fun processTerminated(event: ProcessEvent) {
          appendDebug("EXIT ${event.exitCode} $stepLine", PackLogTone.DEFAULT)
          packCoordinator?.detachMavenHandler(handler)
          val userStopped = packCoordinator?.consumeStopRequested() == true
          refreshModuleVfsAfterStep()
          if (userStopped) {
            ApplicationManager.getApplication().invokeLater {
              statusArea.text = "打包已停止（用户中断）"
            }
            appendDebug("== 已停止：$title（用户中断）==", PackLogTone.DEFAULT)
            releasePackLock()
            return
          }
          if (event.exitCode != 0) {
            ApplicationManager.getApplication().invokeLater {
              statusArea.text = "打包失败：exitCode=${event.exitCode}（请复制“打包调试”查看详情）"
            }
            onStepFailed?.invoke(params, event.exitCode)
            releasePackLock()
            return
          }
          // Record produced zip file for optional OSS upload.
          var packZipMissingMessage: String? = null
          runCatching {
            val goalsText = params.goals.joinToString(" ")
            val kind = when {
              goalsText.contains(":descriptorPack") -> OssUploader.PackKind.DESCRIPTOR
              goalsText.contains(":oyezPack") -> OssUploader.PackKind.OYEZ
              else -> null
            }
            if (kind != null) {
              val opt = params.cmdOptions.orEmpty()
              val outputName = when (kind) {
                OssUploader.PackKind.DESCRIPTOR ->
                  opt.substringAfter("-Ddescriptor.outputName=", "").substringBefore(' ').trim()
                OssUploader.PackKind.OYEZ ->
                  opt.substringAfter("-Dimpl.outputName=", "").substringBefore(' ').trim()
                OssUploader.PackKind.MARKETPLACE -> ""
              }
              val baseName = outputName.ifBlank { java.io.File(params.workingDirPath).name }
              val zip = java.io.File(params.workingDirPath, "target/$baseName.zip")
              if (zip.exists() && zip.isFile) {
                onPackZipProduced?.invoke(kind, zip)
              } else {
                val packKindText = when (kind) {
                  OssUploader.PackKind.DESCRIPTOR -> "定义态"
                  OssUploader.PackKind.OYEZ -> "实现态"
                  OssUploader.PackKind.MARKETPLACE -> "商城"
                }
                packZipMissingMessage =
                  "打包失败：${packKindText}未生成预期 zip（$baseName.zip）。流程已停止。"
                appendDebug("ERROR zip not found: ${zip.path}", PackLogTone.ERROR)
              }
            }
          }.onFailure { appendDebug("WARN pack-zip detect failed: ${it.message}", PackLogTone.WARN) }
          if (!packZipMissingMessage.isNullOrBlank()) {
            val msg = packZipMissingMessage!!
            ApplicationManager.getApplication().invokeLater {
              statusArea.text = msg
            }
            onStepFailed?.invoke(params, -2)
            releasePackLock()
            return
          }
          onStepSucceeded?.invoke(params)
          appendDebug("DONE  $stepLine", PackLogTone.DEFAULT)
          runNext()
        }
      })

      handler.startNotify()
    }

    runNext()
  }

  /**
   * 按 pomDir 并行执行 Maven 打包（仅用于「按Module打包」模式）。
   * 预置阶段串行先跑完，然后不同 pomDir 之间并行，每个 pomDir 内部串行。
   */
  private fun runMavenParallelByPomDir(
    project: Project,
    statusArea: JBTextArea,
    preflightCommands: List<MavenRunnerParameters>,
    pomDirChains: List<Pair<String, List<MavenRunnerParameters>>>,
    totalSteps: Int,
    title: String,
    appendDebug: (String, PackLogTone) -> Unit,
    onPackZipProduced: ((OssUploader.PackKind, java.io.File) -> Unit)? = null,
    onAllSucceeded: (() -> Unit)? = null,
    onStepFailed: ((MavenRunnerParameters, Int) -> Unit)? = null,
    onPackSessionFinished: (() -> Unit)? = null
  ) {
    val chainCount = pomDirChains.size
    val totalChainSteps = pomDirChains.sumOf { it.second.size }
    if (preflightCommands.isEmpty() && totalChainSteps == 0) return

    val packCoordinator = runCatching { project.getService(MavenPackCoordinator::class.java) }.getOrNull()
    if (packCoordinator != null && !packCoordinator.tryBeginPack()) {
      appendDebug(
        "PACK 已拒绝：本工程已有打包任务在执行。请等待当前打包结束后再试。",
        PackLogTone.DEFAULT
      )
      ApplicationManager.getApplication().invokeLater {
        statusArea.text = "打包未开始：已有进行中的打包任务"
      }
      return
    }
    fun releasePackLock() {
      packCoordinator?.endPack()
      onPackSessionFinished?.invoke()
    }

    // ---- Build mvn command (same logic as runMavenSequential) ----
    fun mvnExecutable(): String {
      val osName = System.getProperty("os.name")?.lowercase().orEmpty()
      val isWin = osName.contains("win")
      val generalSettings = runCatching { MavenProjectsManager.getInstance(project).generalSettings }.getOrNull()
      val mavenHome = runCatching { generalSettings?.mavenHome }.getOrNull()
        ?.trim()?.takeIf { it.isNotBlank() }
      if (!mavenHome.isNullOrBlank()) {
        val bin = java.io.File(mavenHome, "bin")
        val exe = if (isWin) java.io.File(bin, "mvn.cmd") else java.io.File(bin, "mvn")
        if (exe.exists()) return exe.absolutePath
      }
      return if (isWin) "mvn.cmd" else "mvn"
    }

    fun buildMvnCommand(params: MavenRunnerParameters): GeneralCommandLine {
      val goals = params.goals
      val options = params.cmdOptions?.trim()?.takeIf { it.isNotBlank() }
        ?.split(Regex("\\s+")).orEmpty()
      val generalSettings = runCatching { MavenProjectsManager.getInstance(project).generalSettings }.getOrNull()
      val userSettings = runCatching { generalSettings?.userSettingsFile }.getOrNull()
        ?.trim()?.takeIf { it.isNotBlank() }
      val localRepo = runCatching { generalSettings?.localRepository }.getOrNull()
        ?.trim()?.takeIf { it.isNotBlank() }
      fun projectJdkHome(project: Project): String? {
        val sdk: Sdk? = runCatching { ProjectRootManager.getInstance(project).projectSdk }.getOrNull()
        val home = sdk?.homePath?.trim().orEmpty()
        return home.takeIf { it.isNotBlank() }
      }
      val jdkHome = projectJdkHome(project)
      val osName = System.getProperty("os.name")?.lowercase().orEmpty()
      val isWin = osName.contains("win")
      val cmd = mutableListOf<String>()
      cmd += mvnExecutable()
      cmd += "-B"
      cmd += "-f"
      cmd += params.pomFileName
      if (!userSettings.isNullOrBlank()) { cmd += "-s"; cmd += userSettings }
      cmd += goals
      if (!localRepo.isNullOrBlank()) { cmd += "-Dmaven.repo.local=$localRepo" }
      cmd += options
      val commandLine = GeneralCommandLine(cmd)
        .withWorkDirectory(params.workingDirPath)
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      if (isWin) { commandLine.withCharset(Charset.forName("GB18030")) }
      if (!jdkHome.isNullOrBlank()) {
        commandLine.withEnvironment("JAVA_HOME", jdkHome)
        val binDir = java.io.File(jdkHome, "bin").absolutePath
        val currentPath = System.getenv("PATH").orEmpty()
        val sep = if (isWin) ";" else ":"
        val newPath = if (currentPath.isBlank()) binDir else "$binDir$sep$currentPath"
        commandLine.withEnvironment("PATH", newPath)
      }
      return commandLine
    }

    fun refreshModuleVfs(workingDirPath: String) {
      runCatching {
        val workDir = java.io.File(workingDirPath)
        val targetDir = java.io.File(workDir, "target")
        if (targetDir.isDirectory) {
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetDir)?.let { vf ->
            if (vf.isValid) vf.refresh(true, true)
          }
        }
        if (workDir.isDirectory) {
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(workDir)?.let { vf ->
            if (vf.isValid) vf.refresh(true, true)
          }
        }
        workDir.parentFile?.takeIf { it.isDirectory }?.let { parent ->
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parent)?.let { vf ->
            if (vf.isValid) vf.refresh(true, true)
          }
        }
      }.onFailure { appendDebug("WARN vfs refresh: ${it.message}", PackLogTone.WARN) }
    }

    fun detectZip(params: MavenRunnerParameters) {
      val goalsText = params.goals.joinToString(" ")
      val kind = when {
        goalsText.contains(":descriptorPack") -> OssUploader.PackKind.DESCRIPTOR
        goalsText.contains(":oyezPack") -> OssUploader.PackKind.OYEZ
        else -> null
      } ?: return
      val opt = params.cmdOptions.orEmpty()
      val outputName = when (kind) {
        OssUploader.PackKind.DESCRIPTOR ->
          opt.substringAfter("-Ddescriptor.outputName=", "").substringBefore(' ').trim()
        OssUploader.PackKind.OYEZ ->
          opt.substringAfter("-Dimpl.outputName=", "").substringBefore(' ').trim()
        OssUploader.PackKind.MARKETPLACE -> ""
      }
      val baseName = outputName.ifBlank { java.io.File(params.workingDirPath).name }
      val zip = java.io.File(params.workingDirPath, "target/$baseName.zip")
      if (zip.exists() && zip.isFile) {
        onPackZipProduced?.invoke(kind, zip)
      }
    }

    // Run one Maven step synchronously (blocks until process exits). Returns exit code.
    fun runOneStep(params: MavenRunnerParameters, stepNo: Int, tag: String): Int {
      val stepLine = runCatching { formatMavenStep(stepNo, totalSteps, params) }
        .getOrDefault("[$stepNo/$totalSteps] goals=${params.goals.joinToString(" ")}")
      ApplicationManager.getApplication().invokeLater {
        statusArea.text = if (tag == "预置") stepLine else "[$tag] $stepLine"
      }
      appendDebug(if (tag == "预置") "START $stepLine" else "[$tag] START $stepLine", PackLogTone.DEFAULT)

      val commandLine = buildMvnCommand(params)
      appendDebug(if (tag == "预置") "CMD  ${commandLine.commandLineString}"
      else "[$tag] CMD  ${commandLine.commandLineString}", PackLogTone.DEFAULT)

      val latch = java.util.concurrent.CountDownLatch(1)
      val exitCodeRef = AtomicInteger(-1)

      val handler = try {
        OSProcessHandler(commandLine)
      } catch (e: Throwable) {
        appendDebug("ERROR starting mvn: ${e.javaClass.name}: ${e.message}", PackLogTone.ERROR)
        return -1
      }
      packCoordinator?.attachMavenHandler(handler)

      handler.addProcessListener(object : ProcessAdapter() {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          val text = event.text.trimEnd()
          if (text.isBlank()) return
          val prefix = when (outputType) {
            ProcessOutputTypes.STDERR -> "ERR"
            ProcessOutputTypes.SYSTEM -> "SYS"
            else -> "OUT"
          }
          appendDebug(if (tag == "预置") "$prefix $text" else "[$tag] $prefix $text", PackLogTone.DEFAULT)
        }

        override fun processTerminated(event: ProcessEvent) {
          exitCodeRef.set(event.exitCode)
          appendDebug(if (tag == "预置") "EXIT ${event.exitCode} $stepLine"
          else "[$tag] EXIT ${event.exitCode} $stepLine", PackLogTone.DEFAULT)
          packCoordinator?.detachMavenHandler(handler)
          refreshModuleVfs(params.workingDirPath)
          detectZip(params)
          latch.countDown()
        }
      })
      handler.startNotify()
      try {
        latch.await()
      } catch (_: InterruptedException) {
        if (!handler.isProcessTerminated) handler.destroyProcess()
      }
      return exitCodeRef.get()
    }

    // ---- 1. Preflight (serial) ----
    val stepCounter = AtomicInteger(0)
    for (params in preflightCommands) {
      val stepNo = stepCounter.incrementAndGet()
      val exitCode = runOneStep(params, stepNo, "预置")
      if (exitCode != 0) {
        packCoordinator?.consumeStopRequested()
        onStepFailed?.invoke(params, exitCode)
        releasePackLock()
        return
      }
    }

    // ---- 2. Per-module parallel ----
    val parallelConcurrency = minOf(chainCount, 3)
    appendDebug("PACK 并行阶段：$chainCount 个 module，并发数 $parallelConcurrency", PackLogTone.PROGRESS)

    val semaphore = java.util.concurrent.Semaphore(parallelConcurrency)
    val stopFlag = AtomicBoolean(false)
    val successCount = AtomicInteger(0)
    val failCount = AtomicInteger(0)
    val executor = java.util.concurrent.Executors.newFixedThreadPool(parallelConcurrency)

    for ((tag, chain) in pomDirChains) {
      executor.submit {
        try {
          semaphore.acquire()
          appendDebug("[并行:$tag] 取得执行槽位（${chain.size} 步）", PackLogTone.PROGRESS)
          var pomDirOk = true
          for (params in chain) {
            if (stopFlag.get() || packCoordinator?.consumeStopRequested() == true) {
              appendDebug("[并行:$tag] 收到停止信号，跳过剩余步骤", PackLogTone.WARN)
              pomDirOk = false
              break
            }
            val stepNo = stepCounter.incrementAndGet()
            val exitCode = runOneStep(params, stepNo, tag)
            if (exitCode != 0) {
              appendDebug("[并行:$tag] 失败，停止本 module 后续步骤", PackLogTone.ERROR)
              onStepFailed?.invoke(params, exitCode)
              pomDirOk = false
              break
            }
          }
          if (pomDirOk) successCount.incrementAndGet() else failCount.incrementAndGet()
        } catch (_: InterruptedException) {
          // ignore
        } finally {
          semaphore.release()
        }
      }
    }

    executor.shutdown()
    try { executor.awaitTermination(30, java.util.concurrent.TimeUnit.MINUTES) }
    catch (_: InterruptedException) { executor.shutdownNow() }

    val userStopped = packCoordinator?.consumeStopRequested() == true
    ApplicationManager.getApplication().invokeLater {
      if (userStopped) {
        statusArea.text = "打包已停止（用户中断）"
      } else if (failCount.get() > 0) {
        statusArea.text = "打包部分失败：${successCount.get()}/${chainCount} 个 module 成功"
      } else {
        statusArea.text = "打包完成：$title（${chainCount} 个 module，共 $totalSteps 步）"
      }
    }
    appendDebug(
      "== 并行结果：成功 ${successCount.get()} / 失败 ${failCount.get()} / 共 $chainCount 个 module ==",
      if (failCount.get() > 0) PackLogTone.ERROR else PackLogTone.OK
    )
    if (failCount.get() == 0 && !userStopped) onAllSucceeded?.invoke()
    releasePackLock()
  }

  /** 商城展示名 / 默认 zip 基名：`算子名`（实现态打包时会再加 `-oyez-{yyyyMMddHHmmss}`，与定义态 `-descriptor-` 对称）。 */
  private fun marketplacePackBaseName(row: OperatorRow): String {
    val raw = row.name.trim().removeSurrounding("\"").trim()
    val base = if (raw.isBlank() || raw == "-") row.className.substringAfterLast('.') else raw
    return safeArtifactBaseName(base)
  }

  private fun implDescriptorStem(row: OperatorRow): String {
    val s = row.className.substringAfterLast('.')
    return s.removeSuffix("Descriptor").removeSuffix("Definer").trim()
  }

  /**
   * `-Dimpl.includes=`：优先只带**严格路径能落到源码**的 FQCN，并按算子名/定义态类名 stem 排序。
   * 索引里若混入 `...transformer.file` 等错串，模糊 [findSourceFileByClassName] 会误认有源文件；手点 oyezPack 不传 includes 则不会中招。
   * 若严格过滤后为空，尝试「索引里第一条的包名 + 定义态 stem」推断实现类 FQCN。
   */
  private fun implIncludesCsvForRow(project: Project, row: OperatorRow): String {
    val raw = row.implClasses.map { it.trim() }.filter { it.isNotBlank() && it != "-" }.distinct()
    if (raw.isEmpty()) return ""

    fun strictOk(fqcn: String): Boolean {
      val vf = findSourceFileByFqcnStrict(project, fqcn) ?: return false
      val path = vf.path.replace('\\', '/')
      val pkgPath = fqcn.substringBeforeLast('.').replace('.', '/')
      return path.contains(pkgPath)
    }

    val strictList = raw.filter { strictOk(it) }
    val nameKey = normalizeOperatorName(row.name)
    val stem = implDescriptorStem(row)
    val stemLower = stem.lowercase()

    fun score(fqcn: String): Int {
      val simple = fqcn.substringAfterLast('.')
      val sl = simple.lowercase()
      var sc = 0
      if (nameKey.isNotBlank() && sl == nameKey) sc += 100
      if (nameKey.isNotBlank() && sl.contains(nameKey)) sc += 50
      if (stemLower.isNotBlank() && (sl == stemLower || sl.contains(stemLower) || stemLower.contains(sl))) sc += 70
      if (simple.length <= 4 && simple.firstOrNull()?.isLowerCase() == true && sl != nameKey) sc -= 120
      return sc
    }

    val pool = if (strictList.isNotEmpty()) strictList else {
      val inferred = run {
        if (stem.isBlank()) return@run null
        val pkg = raw.first().substringBeforeLast('.').trim()
        if (pkg.isBlank()) return@run null
        "$pkg.$stem"
      }
      if (inferred != null && strictOk(inferred)) listOf(inferred) else raw
    }

    return pool.distinct().sortedByDescending { score(it) }.joinToString(",")
  }

  // ---- Implementation index (computionFramework) ----

  private val opDefinerStartPattern = java.util.regex.Pattern.compile("@(?:[A-Za-z0-9_$.]+\\.)?OpDefiner\\b")
  private val classPattern = java.util.regex.Pattern.compile(
    "\\s*(?:(?:public|protected|private|internal|open|final|abstract|sealed|data|static)\\s+)*(?:class|interface)\\s+([A-Za-z_][A-Za-z0-9_]*)",
    java.util.regex.Pattern.DOTALL
  )
  private val packagePattern = java.util.regex.Pattern.compile("package\\s+([A-Za-z0-9_.]+)")

  private data class OpDefinerAnnotation(val arguments: String, val endOffset: Int)

  private data class ImplIndexResult(
    val index: Map<String, List<String>>,
    /** normalized operator name → 实现态 module 根路径（含 `pom.xml`，由命中文件 [findNearestPomDir] 得到） */
    val implModulePomPathByOperator: Map<String, String>,
    val debugText: String
  )

  private fun implModuleRootPathFromScanFile(sourceFile: VirtualFile): String? {
    if (!sourceFile.isValid) return null
    // 不能用 getContentRootForFile：多根/Gradle 等结构下常为 `src/main/java`，其下无 pom.xml，Maven 会报 -f pom.xml 不存在。
    val pomDir = findNearestPomDir(sourceFile) ?: return null
    val p = pomDir.path
    return runCatching { java.io.File(p).canonicalFile.absolutePath }.getOrNull() ?: p
  }

  private fun buildImplementationIndex(
    project: Project,
    scopeRoots: List<VirtualFile>,
    shouldContinue: () -> Boolean
  ): ImplIndexResult {
    // key: normalized operator name, value: impl class fqcn(s)
    val implMap = linkedMapOf<String, LinkedHashSet<String>>()
    val implModulePomByOperator = linkedMapOf<String, String>()
    val debug = StringBuilder()
    debug.appendLine("== 实现态扫描调试 ==")
    debug.appendLine("scopeRoots(${scopeRoots.size}):")
    scopeRoots.forEach { debug.appendLine(" - ${it.path}") }
    debug.appendLine("skip: dirName in $ignoredDirectoryNames OR dirName endsWith 'descriptor'")
    debug.appendLine()
    debug.appendLine("命中明细（最多输出前 300 条）：")

    val first = scopeRoots.firstOrNull()
      ?: return ImplIndexResult(
        index = emptyMap(),
        implModulePomPathByOperator = emptyMap(),
        debugText = debug.appendLine("scopeRoots 为空").toString()
      )
    debug.appendLine("firstRoot: path=${first.path}, valid=${first.isValid}, dir=${first.isDirectory}, children=${runCatching { first.children.size }.getOrDefault(-1)}")
    // noop warmup (forces VFS children initialization in some cases)
    runCatching { first.children.size }

    var visitedDirs = 0
    var visitedFiles = 0
    var opDefinerFiles = 0
    var opDefinerCount = 0
    var emitted = 0
    for (root in scopeRoots) {
      debug.appendLine("root: path=${root.path}, valid=${root.isValid}, dir=${root.isDirectory}, children=${runCatching { root.children.size }.getOrDefault(-1)}")
      VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
        override fun visitFile(file: VirtualFile): Boolean {
          if (!shouldContinue()) throw ProcessCanceledException()

          if (file.isDirectory) {
            visitedDirs++
            if (file.name in ignoredDirectoryNames) return false
            if (file.name.endsWith("descriptor", ignoreCase = true)) return false
            return true
          }

          visitedFiles++
          val ext = file.extension?.lowercase() ?: ""
          if (ext != "java" && ext != "kt") return true

          val text = runCatching { VfsUtilCore.loadText(file) }.getOrNull() ?: return true
          if (!text.contains("OpDefiner")) return true
          opDefinerFiles++

          // In this codebase, one file typically contains one top-level operator type.
          // So we resolve fqcn once per file, then map all OpDefiner(name, framework) in it to that fqcn.
          val pkg = packagePattern.matcher(text).run { if (find()) group(1) else "" }
          val firstTypeName = classPattern.matcher(text).run { if (find()) group(1) else null } ?: return true
          val fqcn = if (pkg.isEmpty()) firstTypeName else "$pkg.$firstTypeName"
          val annotations = findOpDefinerAnnotations(text, shouldContinue)
          for (ann in annotations) {
            val attrs = parseTopLevelAssignments(ann.arguments)
            val nameRaw = attrs["name"] ?: continue
            val name = normalizeOperatorName(stripQuotes(nameRaw))
            if (name.isBlank() || name == "-") continue
            val framework = normalizeOperatorName(stripQuotes(attrs["computionFramework"] ?: ""))
            if (framework.isBlank()) continue
            opDefinerCount++
            if (emitted < 300) {
              debug.appendLine("file=${file.path}")
              debug.appendLine("  fqcn=$fqcn")
              debug.appendLine("  name=$name")
              debug.appendLine("  computionFramework=$framework")
              emitted++
            }
            // sengee = 定义态；其它 framework = 实现态
            if (framework == "sengee") continue
            implMap.getOrPut(name) { linkedSetOf() }.add(fqcn)
            if (!implModulePomByOperator.containsKey(name)) {
              implModuleRootPathFromScanFile(file)?.let { implModulePomByOperator[name] = it }
            }
          }
          return true
        }
      })
    }
    val index = implMap.mapValues { it.value.toList().sorted() }
    val implOperatorCount = index.size
    val implClassCount = index.values.sumOf { it.size }
    debug.appendLine()
    debug.appendLine("汇总：")
    debug.appendLine("visitedDirs=$visitedDirs, visitedFiles=$visitedFiles, opDefinerFiles=$opDefinerFiles, opDefinerCount=$opDefinerCount")
    debug.appendLine("implIndexOperators=$implOperatorCount, implIndexClasses=$implClassCount")
    return ImplIndexResult(
      index = index,
      implModulePomPathByOperator = implModulePomByOperator.toMap(),
      debugText = debug.toString().trimEnd()
    )
  }

  private fun stripQuotes(raw: String): String {
    val t = raw.trim()
    return t.removeSurrounding("\"").removeSurrounding("'").trim()
  }

  private fun findOpDefinerAnnotations(text: String, shouldContinue: () -> Boolean): List<OpDefinerAnnotation> {
    val results = mutableListOf<OpDefinerAnnotation>()
    val matcher = opDefinerStartPattern.matcher(text)
    while (matcher.find()) {
      if (!shouldContinue()) throw ProcessCanceledException()
      if (OperatorScanner.isOffsetInsideCommentOrString(text, matcher.start())) continue
      var cursor = matcher.end()
      while (cursor < text.length && text[cursor].isWhitespace()) cursor++
      if (cursor >= text.length || text[cursor] != '(') {
        results.add(OpDefinerAnnotation("", cursor))
        continue
      }
      val parsed = parseParenthesizedContent(text, cursor) ?: continue
      results.add(OpDefinerAnnotation(parsed.first, parsed.second))
    }
    return results
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
        if (escaping) escaping = false
        else if (ch == '\\') escaping = true
        else if (ch == '"') inString = false
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
            if (depth == 0) return Pair(sb.toString(), cursor + 1)
          }
        }
      }
      if (depth > 0 && !(depth == 1 && ch == '(')) sb.append(ch)
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
      if (ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '$') cursor++ else break
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
        if (escaping) escaping = false
        else if (ch == '\\') escaping = true
        else if (ch == '"') inString = false
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
        ',' -> if (depthParen == 0 && depthBrace == 0 && depthBracket == 0) {
          parts.add(text.substring(start, index))
          start = index + 1
        }
      }
    }
    if (start < text.length) parts.add(text.substring(start))
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
        if (escaping) escaping = false
        else if (ch == '\\') escaping = true
        else if (ch == '"') inString = false
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
        '=' -> if (depthParen == 0 && depthBrace == 0 && depthBracket == 0) {
          val key = part.substring(0, index).trim()
          val value = part.substring(index + 1).trim()
          if (key.isEmpty() || value.isEmpty()) return null
          return Pair(key, value)
        }
      }
    }
    return null
  }

  private fun openHelpFileInEditor(project: Project, helpFile: VirtualFile): Boolean {
    return runCatching {
      FileEditorManager.getInstance(project).openFile(helpFile, true)
      true
    }.getOrElse {
      logger.warn("打开帮助文档失败：${it.message ?: "未知错误"}", it)
      false
    }
  }

  private fun findDescriptorRootForOperator(project: Project, row: OperatorRow): VirtualFile? {
    val sourceFile = findSourceFileByFqcnStrict(project, row.className)
      ?: findSourceFileByClassName(project, row.className)
    if (sourceFile != null) {
      var current: VirtualFile? = sourceFile.parent
      while (current != null) {
        if (current.name.endsWith("descriptor", ignoreCase = true)) return current
        current = current.parent
      }
    }
    return collectScanRoots(collectDefaultRootCandidates(project)).firstOrNull()
  }

  /**
   * 实现态 module（含 `pom.xml` 的目录）：优先用扫描阶段已写入的 [OperatorRow.implModulePomPath]；
   * 仅当未扫描到路径时（例如未跑实现态索引），再按 `-Dimpl.includes=` 与 [OperatorRow.implClasses] 严格反查。
   */
  /**
   * 定义态 module（含 `pom.xml`）：优先 [OperatorRow.descriptorModulePomPath]（扫描命中 descriptor 源文件时写入）；
   * 否则再按 FQCN / descriptor 根目录反查。
   */
  private fun resolveDescriptorModulePomDir(project: Project, row: OperatorRow): VirtualFile? {
    row.descriptorModulePomPath?.trim()?.takeIf { it.isNotBlank() }?.let { path ->
      val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.replace('\\', '/'))
      if (vf != null && vf.isValid && vf.isDirectory) return vf
    }
    val sourceFile = findSourceFileByFqcnStrict(project, row.className)
      ?: findSourceFileByClassName(project, row.className)
    sourceFile?.let { findNearestPomDir(it) }?.let { return it }
    val descriptorRoot = findDescriptorRootForOperator(project, row) ?: return null
    return findNearestPomDir(descriptorRoot)
  }

  private fun resolveImplModulePomDir(project: Project, row: OperatorRow): VirtualFile? {
    row.implModulePomPath?.trim()?.takeIf { it.isNotBlank() }?.let { path ->
      val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.replace('\\', '/'))
      if (vf != null && vf.isValid && vf.isDirectory) {
        findNearestPomDir(vf)?.let { return it }
      }
    }
    val csv = implIncludesCsvForRow(project, row).trim()
    if (csv.isNotBlank()) {
      csv.split(',')
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .firstNotNullOfOrNull { fqcn ->
          findSourceFileByFqcnStrict(project, fqcn)?.let { findNearestPomDir(it) }
        }
        ?.let { return it }
    }
    return row.implClasses
      .asSequence()
      .map { it.trim() }
      .filter { it.isNotBlank() && it != "-" }
      .firstNotNullOfOrNull { fqcn ->
        findSourceFileByFqcnStrict(project, fqcn)?.let { findNearestPomDir(it) }
      }
  }

  private fun collectImplModuleCanonicalPaths(project: Project, row: OperatorRow): Set<String> {
    val scanned = row.implModulePomPath?.trim().orEmpty()
    if (scanned.isNotBlank()) {
      val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(scanned.replace('\\', '/'))
      if (vf != null && vf.isValid) {
        findNearestPomDir(vf)?.path?.let { pomPath ->
          val canon = runCatching { java.io.File(pomPath).canonicalFile.absolutePath }.getOrNull() ?: pomPath
          return setOf(canon)
        }
      }
      val canon = runCatching { java.io.File(scanned).canonicalFile.absolutePath }.getOrNull() ?: scanned
      return setOf(canon)
    }
    val out = linkedSetOf<String>()
    fun add(fqcn: String) {
      val f = findSourceFileByFqcnStrict(project, fqcn.trim()) ?: return
      findNearestPomDir(f)?.path?.let {
        runCatching { java.io.File(it).canonicalFile.absolutePath }.getOrNull()
      }?.let { out += it }
    }
    implIncludesCsvForRow(project, row).split(',').forEach { add(it) }
    row.implClasses.forEach { add(it) }
    return out
  }

  private fun findMatchedHelpFile(helpsDir: VirtualFile, operatorName: String): VirtualFile? {
    val normalizedName = normalizeOperatorName(operatorName)
    if (normalizedName.isBlank() || normalizedName == "-") return null
    val allowedExtensions = setOf("md", "markdown", "txt", "html", "htm")
    return helpsDir.children
      .asSequence()
      .filter { !it.isDirectory }
      .filter { (it.extension?.lowercase() ?: "") in allowedExtensions }
      .firstOrNull { file ->
        val stem = file.nameWithoutExtension.trim().lowercase()
        stem == normalizedName || stem.endsWith(".$normalizedName")
      }
  }

  private fun createHelpFileForRow(project: Project, row: OperatorRow): VirtualFile {
    val descriptorRoot = findDescriptorRootForOperator(project, row)
      ?: throw IllegalStateException("未找到 descriptor 目录")
    return WriteAction.compute<VirtualFile, Throwable> {
      val resourcesRoot = descriptorRoot.findFileByRelativePath("src/main/resources")
        ?: VfsUtil.createDirectoryIfMissing(descriptorRoot, "src/main/resources")
        ?: throw IllegalStateException("创建 resources 目录失败")
      val helpsDir = resourcesRoot.findChild("helps")
        ?: VfsUtil.createDirectoryIfMissing(resourcesRoot, "helps")
        ?: throw IllegalStateException("创建 helps 目录失败")

      findMatchedHelpFile(helpsDir, row.name)?.let { return@compute it }

      val fallbackName = row.name.trim().ifBlank { "help" }
      val safeName = fallbackName.replace(Regex("""[\\/:*?"<>|]"""), "_")
      val targetFileName = "$safeName.md"
      val file = helpsDir.findChild(targetFileName) ?: helpsDir.createChildData(this, targetFileName)
      if (file.length == 0L) {
        file.setBinaryContent(
          "# ${row.name}\n\n## 标签\n\n## 描述\n\n## 输入端口\n\n## 输出端口\n".toByteArray(StandardCharsets.UTF_8)
        )
      }
      file
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    runCatching { toolWindow.setType(ToolWindowType.DOCKED, null) }

    val panel = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
    }
    panel.border = JBUI.Borders.empty(12)

    val statusArea = JBTextArea("点击“扫描算子”开始")
    statusArea.isEditable = false
    statusArea.lineWrap = true
    statusArea.wrapStyleWord = true
    // 必须不透明，否则在布局变化/重绘时容易出现“文字残影叠加”
    statusArea.isOpaque = true
    statusArea.background = UIUtil.getPanelBackground()
    statusArea.foreground = JBColor.GRAY
    statusArea.font = UIManager.getFont("Label.font")
    statusArea.border = JBUI.Borders.empty(2, 0)

    fun setInlineStatus(text: String, tone: PackLogTone = PackLogTone.DEFAULT) {
      statusArea.text = text
      statusArea.foreground = when (tone) {
        PackLogTone.ERROR -> JBColor.namedColor("Label.errorForeground", JBColor.RED)
        PackLogTone.WARN -> JBColor.namedColor("Label.warningForeground", JBColor(0x8a6d00, 0xc8b000))
        PackLogTone.OK -> JBColor(0x0d7d0d, 0x6aab6a)
        PackLogTone.PROGRESS -> JBColor.namedColor("Link.activeForeground", JBColor(0x1866cc, 0x589df5))
        PackLogTone.DEFAULT -> JBColor.GRAY
      }
    }

    // 打包日志面板（直接嵌入插件 tool window，不跟 Run 窗口较劲）
    val packLogTextArea = JBTextArea(8, 60).apply {
      isEditable = false
      lineWrap = true
      wrapStyleWord = true
      font = Font("Monospaced", Font.PLAIN, JBUI.scale(12))
      isOpaque = true
      background = UIUtil.getPanelBackground()
      foreground = UIUtil.getLabelForeground()
    }
    val runConsoleStopPackBtn = JButton("停止打包", AllIcons.Actions.Suspend).apply {
      isFocusable = false
      toolTipText = "终止当前插件发起的 Maven 打包子进程"
    }
    val packLogPanel: JPanel by lazy {
      val northBar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4))).apply {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.merge(
          JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
          JBUI.Borders.empty(4, 8, 0, 8),
          true
        )
        add(runConsoleStopPackBtn)
        add(JBLabel("打包/上传 日志").apply {
          foreground = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
        })
      }
      val scrollPane = JBScrollPane(packLogTextArea).apply {
        border = JBUI.Borders.empty()
        isOpaque = true
        background = UIUtil.getPanelBackground()
      }
      JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        add(northBar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
      }
    }

    fun activateRunConsole() {
      ApplicationManager.getApplication().invokeLater {
        runCatching {
          ToolWindowManager.getInstance(project).getToolWindow("数由 GUI")?.activate(null, true)
        }
      }
    }

    fun closePackConsole() {
    }

    fun bindMavenStepToRunWindow(handler: OSProcessHandler) {
    }

    fun clearRunWindowPackProcessBinding() {
    }

    fun setPackLogContent(plain: String) {
      lastPackDebug = plain.trimEnd()
      val app = ApplicationManager.getApplication()
      val flush = Runnable {
        packLogTextArea.text = plain + "\n"
      }
      if (app.isDispatchThread) flush.run() else app.invokeAndWait(flush)
    }

    fun appendPackLogLine(text: String, tone: PackLogTone = PackLogTone.DEFAULT) {
      lastPackDebug = (lastPackDebug + "\n" + text).trimStart('\n')
      val app = ApplicationManager.getApplication()
      val lineRunnable = Runnable {
        packLogTextArea.append(text + "\n")
      }
      if (app.isDispatchThread) lineRunnable.run() else app.invokeAndWait(lineRunnable)
    }

    fun performStopPackAction() {
      val coord = runCatching { project.getService(MavenPackCoordinator::class.java) }.getOrNull()
      when {
        coord == null ->
          appendPackLogLine("PACK 停止：内部错误（协调器不可用）", PackLogTone.ERROR)
        coord.requestStopPack() -> {
          appendPackLogLine("PACK 停止：已发送终止请求…", PackLogTone.WARN)
          ApplicationManager.getApplication().invokeLater {
            setInlineStatus("正在停止打包…", PackLogTone.WARN)
          }
        }
        else ->
          appendPackLogLine("PACK 停止：当前没有可终止的 Maven 进程。", PackLogTone.WARN)
      }
    }

    fun onPackStarted() {
      ApplicationManager.getApplication().invokeLater {
        runConsoleStopPackBtn.isEnabled = true
        runConsoleStopPackBtn.text = "停止打包"
      }
    }

    fun onPackEnded() {
      ApplicationManager.getApplication().invokeLater {
        runConsoleStopPackBtn.isEnabled = false
        runConsoleStopPackBtn.text = "已停止"
      }
    }

    runConsoleStopPackBtn.addActionListener { performStopPackAction() }

    // Inline Tozoo settings (NO dialog)
    val tozooSettings = ApplicationManager.getApplication().getService(TozooUploadSettings::class.java)
    val tozooUrlField = JBTextField(48).apply {
      text = tozooSettings.state.baseUrl
      toolTipText = "含 context-path，例如 http://127.0.0.1:8080/member"
    }
    val tozooSettingsPanel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.merge(JBUI.Borders.customLine(JBColor.border(), 1), JBUI.Borders.empty(8, 8), true)
      isVisible = false
      val self = this
      add(
        JBLabel("Tozoo 根地址（含 context-path，例如 http://127.0.0.1:8080/member）"),
        BorderLayout.NORTH
      )
      add(
        JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
          isOpaque = true
          background = UIUtil.getPanelBackground()
          add(tozooUrlField, BorderLayout.CENTER)
          add(
            JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
              isOpaque = true
              background = UIUtil.getPanelBackground()
              val saveBtn = JButton("保存")
              val collapseBtn = JButton("收起")
              saveBtn.addActionListener {
                val url = tozooUrlField.text.trim()
                tozooSettings.loadState(TozooUploadSettings.State(baseUrl = url))
                setInlineStatus(if (url.isBlank()) "已清空 Tozoo 根地址（商城上传将不可用）" else "已保存 Tozoo 根地址", if (url.isBlank()) PackLogTone.WARN else PackLogTone.OK)
              }
              collapseBtn.addActionListener {
                self.isVisible = false
                self.parent?.revalidate()
                self.parent?.repaint()
              }
              add(saveBtn)
              add(collapseBtn)
            },
            BorderLayout.EAST
          )
        },
        BorderLayout.CENTER
      )
      add(
        JBLabel("「商城」上传：POST 根地址/commodity/importCommodity/operator 或 /connector（multipart：changeLog、file）。保存后对所有工程生效。").apply {
          foreground = JBColor.GRAY
        },
        BorderLayout.SOUTH
      )
    }

    // Mall upload "commit-like" panel (no modal dialog)
    data class MallPendingOperator(
      val operatorName: String,
      val uploadKind: OssUploader.PackKind,
      val zipFile: java.io.File,
      /** 上传完成刷新历史等（批量上传时为对应行的算子） */
      val row: OperatorRow
    )

    data class MallPendingConnector(
      val displayName: String,
      val zipFile: java.io.File,
      val row: ConnectorScanner.ConnectorRow
    )

    data class MallUploadStep(
      val label: String,
      val zipFile: java.io.File,
      val isConnector: Boolean,
      val operatorUploadKind: OssUploader.PackKind? = null,
      val operatorRow: OperatorRow? = null
    )
    // Best-effort version from nearest pom.xml (descriptor module).
    val pomVersionCache = ConcurrentHashMap<String, String?>()
    fun tryResolvePomVersionFromPomDir(pomDir: VirtualFile?): String? {
      val key = pomDir?.path?.trim().orEmpty()
      if (key.isBlank()) return null
      return pomVersionCache.computeIfAbsent(key) {
        runCatching {
          val pomFile = java.io.File(key, "pom.xml")
          if (!pomFile.exists()) return@runCatching null
          val text = pomFile.readText(Charsets.UTF_8)
          // pick first <version> under <project> (ignore <parent> version)
          val projectBlock = Regex("<project[\\s\\S]*?</project>").find(text)?.value ?: text
          val withoutParent = projectBlock.replace(Regex("<parent>[\\s\\S]*?</parent>"), "")
          Regex("<version>\\s*([^<\\s]+)\\s*</version>").find(withoutParent)?.groupValues?.getOrNull(1)
        }.getOrNull()
      }
    }
    fun normalizeVersionTag(raw: String?): String {
      return raw.orEmpty().trim().ifBlank { "SNAPSHOT" }.replace(Regex("[^0-9A-Za-z._-]"), "_")
    }
    fun descriptorPackOutputBaseName(row: OperatorRow, descriptorPomDir: VirtualFile?): String {
      val base = safeArtifactBaseName(marketplacePackBaseName(row))
      val versionTag = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        .format(java.time.LocalDateTime.now())
      return "$base-descriptor-$versionTag"
    }

    /** 与 [descriptorPackOutputBaseName] 对称：实现态 zip 基名为 `{算子名}-oyez-{yyyyMMddHHmmss}`。 */
    fun implPackOutputBaseName(row: OperatorRow, implPomDir: VirtualFile?): String {
      val base = safeArtifactBaseName(marketplacePackBaseName(row))
      val versionTag = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        .format(java.time.LocalDateTime.now())
      return "$base-oyez-$versionTag"
    }
    fun tryResolvePomVersionFromZip(zip: java.io.File): String? {
      val pomDir = runCatching {
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(zip) ?: return@runCatching null
        findNearestPomDir(vf)
      }.getOrNull()
        ?: runCatching {
          // fallback: walk up from file system path
          var cur: java.io.File? = zip.parentFile
          while (cur != null) {
            val pom = java.io.File(cur, "pom.xml")
            if (pom.exists() && pom.isFile) return@runCatching LocalFileSystem.getInstance().refreshAndFindFileByIoFile(cur)
            cur = cur.parentFile
          }
          null
        }.getOrNull()
      return tryResolvePomVersionFromPomDir(pomDir)
    }

    var mallPendingRow: OperatorRow? = null
    var mallPendingOperators: List<MallPendingOperator> = emptyList()
    var mallPendingConnectors: List<MallPendingConnector> = emptyList()

    fun currentMallUploadSteps(): List<MallUploadStep> {
      if (mallPendingConnectors.isNotEmpty()) {
        return mallPendingConnectors.map {
          MallUploadStep(it.displayName, it.zipFile, isConnector = true)
        }
      }
      return mallPendingOperators.map {
        MallUploadStep(it.operatorName, it.zipFile, isConnector = false, it.uploadKind, it.row)
      }
    }

    // IntelliJ 平台原生风格：JBLabel（见下方 mallSectionTitle）
    fun mallSectionTitle(text: String): JBLabel {
      val base = UIManager.getFont("Label.font") ?: Font("Dialog", Font.PLAIN, JBUI.scale(12))
      return JBLabel(text).apply {
        font = base.deriveFont(Font.BOLD)
        foreground = UIUtil.getLabelForeground()
      }
    }

    fun mallMutedLabel(text: String): JBLabel {
      val base = UIManager.getFont("Label.font") ?: Font("Dialog", Font.PLAIN, JBUI.scale(12))
      return JBLabel(text).apply {
        font = base
        foreground = JBColor.namedColor("Label.disabledForeground", UIUtil.getContextHelpForeground())
      }
    }

    val mallCurrentOpLabel = JBLabel("暂无待上传算子：请在算子列表右键「上传定义态」或「上传实现态」").apply {
      font = UIManager.getFont("Label.font") ?: Font("Dialog", Font.PLAIN, JBUI.scale(13))
      foreground = UIUtil.getLabelForeground()
      border = JBUI.Borders.empty(10, 8)
    }
    val mallCurrentHintLabel = mallMutedLabel("")

    val mallChangeLogArea = JBTextArea().apply {
      lineWrap = true
      wrapStyleWord = true
      rows = 4
      val baseSize = (UIManager.getFont("TextArea.font")
        ?: UIManager.getFont("TextField.font")
        ?: UIManager.getFont("Label.font"))?.size ?: JBUI.scale(13)
      // Use logical Dialog font to ensure CJK glyph fallback on all JBR themes.
      font = Font(Font.DIALOG, Font.PLAIN, baseSize)
      foreground = UIUtil.getTextFieldForeground()
      caretColor = UIUtil.getTextFieldForeground()
      background = UIUtil.getTextFieldBackground()
    }
    val mallUploadStopBtn = JButton("停止上传", AllIcons.Actions.Suspend).apply {
      isEnabled = false
      isFocusable = false
    }
    val runningUploadIndicator = AtomicReference<com.intellij.openapi.progress.ProgressIndicator?>(null)
    val mallSubmitBtn = JButton("提交").apply { isEnabled = false }

    fun refreshMallCommitPanel() {
      val steps = currentMallUploadSteps()
      val first = steps.firstOrNull()
      if (first == null) {
        mallCurrentOpLabel.text = "暂无待上传：算子列表右键/批量上传，或连接器页「上传至商城」。"
        mallCurrentHintLabel.text = ""
      } else if (steps.size == 1) {
        val ver = tryResolvePomVersionFromZip(first.zipFile)
        val prefix = buildString {
          append("1. ")
          if (!ver.isNullOrBlank()) append("v$ver ")
        }
        mallCurrentOpLabel.text = prefix + first.label
        mallCurrentHintLabel.text = if (first.isConnector) {
          "类型：连接器  |  文件：${first.zipFile.name}"
        } else {
            val kindText = when (first.operatorUploadKind) {
              OssUploader.PackKind.MARKETPLACE -> "定义态"
              OssUploader.PackKind.OYEZ -> "实现态"
              OssUploader.PackKind.DESCRIPTOR -> "定义态"
              else -> "算子"
            }
            "类型：$kindText  |  文件：${first.zipFile.name}"
        }
      } else {
        val isConnector = first.isConnector
        mallCurrentOpLabel.text = "批量上传 · 共 ${steps.size} 个${if (isConnector) "连接器" else "算子"}包"
        mallCurrentHintLabel.text = steps.take(8).joinToString("\n") { s ->
          val kt = if (s.isConnector) {
            "连接器"
          } else when (s.operatorUploadKind) {
              OssUploader.PackKind.MARKETPLACE, OssUploader.PackKind.DESCRIPTOR -> "定义态"
              OssUploader.PackKind.OYEZ -> "实现态"
            else -> "算子"
          }
          "· ${s.label} · $kt · ${s.zipFile.name}"
        } + if (steps.size > 8) "\n… 其余 ${steps.size - 8} 个" else ""
      }
      mallSubmitBtn.isEnabled = steps.isNotEmpty() && mallChangeLogArea.text.trim().isNotEmpty()
    }

    mallCurrentOpLabel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (!SwingUtilities.isLeftMouseButton(e)) return
        if (e.clickCount != 2) return
        val zip = currentMallUploadSteps().firstOrNull()?.zipFile ?: return
        revealIoFileInProjectView(project, zip)
      }
    })

    mallChangeLogArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
      override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = refreshMallCommitPanel()
      override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = refreshMallCommitPanel()
      override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = refreshMallCommitPanel()
    })

    val mallCommitBody = JPanel(BorderLayout(0, 0)).apply {
      // Upload page for single operator: keep content pinned to top.
      val changesPanel = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0)
        add(mallCurrentOpLabel, BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
          isOpaque = false
          border = JBUI.Borders.empty(0, 8, 6, 8)
          add(mallCurrentHintLabel, BorderLayout.WEST)
        }, BorderLayout.SOUTH)
      }
      changesPanel.preferredSize = Dimension(JBUI.scale(640), JBUI.scale(86))

      val messageHeader = BorderLayoutPanel().apply {
        border = JBUI.Borders.empty(8, 12, 4, 12)
        addToLeft(mallSectionTitle("提交说明"))
        addToRight(mallMutedLabel("必填"))
      }
      val messageScroll = JBScrollPane(mallChangeLogArea).apply {
        border = JBUI.Borders.empty()
        preferredSize = Dimension(JBUI.scale(640), JBUI.scale(140))
      }
      // Always-visible input border (Git commit message style)
      val messageBox = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UIUtil.getTextFieldBackground()
        border = JBUI.Borders.customLine(JBColor(0x8A8F99, 0x5F6368), 1)
        add(messageScroll, BorderLayout.CENTER)
      }
      val messageFooter = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.empty(4, 6, 6, 6)
        val btnBar = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
          isOpaque = false
          add(mallUploadStopBtn)
          add(mallSubmitBtn)
        }
        add(btnBar, BorderLayout.EAST)
      }
      val messagePanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(messageHeader, BorderLayout.NORTH)
        add(messageBox, BorderLayout.CENTER)
        add(messageFooter, BorderLayout.SOUTH)
      }
      messagePanel.preferredSize = Dimension(JBUI.scale(640), JBUI.scale(210))

      val compactTop = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(10, 10, 10, 10)
        alignmentX = 0f
        add(changesPanel)
        add(Box.createVerticalStrut(JBUI.scale(8)))
        add(messagePanel)
      }

      add(compactTop, BorderLayout.NORTH)
    }
    val mallCommitPanel = JPanel(BorderLayout()).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.customLine(JBColor.border(), 1)
      add(mallCommitBody, BorderLayout.CENTER)
    }

    // Maven-like: overlay commit panel (table height should NOT change)
    val mallCommitScroll = JBScrollPane(mallCommitPanel).apply {
      isOpaque = true
      viewport.isOpaque = true
      background = UIUtil.getPanelBackground()
      viewport.background = UIUtil.getPanelBackground()
      border = JBUI.Borders.customLine(JBColor.border(), 1)
      horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    }

    val scanButton = JButton("扫描算子") // kept for compatibility; not shown in UI (ActionToolbar used)
    val stopScanButton = JButton("停止扫描") // kept for compatibility; not shown in UI (ActionToolbar used)
    stopScanButton.isEnabled = false
    val copyScanDebugButton = JButton("复制扫描调试")
    copyScanDebugButton.toolTipText = "复制本次扫描的实现态索引调试信息（扫描路径、命中到的 OpDefiner(name/framework) 等）"
    val runningScanIndicator = AtomicReference<com.intellij.openapi.progress.ProgressIndicator?>()
    var allRows: List<OperatorRow> = emptyList()
    var currentRows: List<OperatorRow> = emptyList()
    var selectedDetailRow: OperatorRow? = null
    val runtimeHelpFiles = mutableMapOf<String, VirtualFile>()
    val columns = arrayOf("", "算子", "实现态数", "i18n", "帮助", "图标")
    val tableModel = object : DefaultTableModel(columns, 0) {
      override fun isCellEditable(row: Int, column: Int): Boolean = column == 0
      override fun getColumnClass(columnIndex: Int): Class<*> {
        return if (columnIndex == 0) java.lang.Boolean::class.java else String::class.java
      }
    }
    val packSelection = linkedMapOf<String, Boolean>()
    /** 最近一次 Maven 打包失败提示：`OperatorRow className|name` → 简短说明（表格标红 + tooltip） */
    val packFailHintByOpKey = linkedMapOf<String, String>()
    val resultTable = object : JBTable(tableModel) {
      override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): java.awt.Component {
        val c = super.prepareRenderer(renderer, row, column)
        val mr = convertRowIndexToModel(row)
        val op = currentRows.getOrNull(mr)
        val key = op?.let { "${it.className}|${it.name}" }
        val failed = key != null && packFailHintByOpKey.containsKey(key)
        if (failed && !isRowSelected(row)) {
          c.background = JBColor(0xEF9A9A, 0x6B2020)
        } else if (!isRowSelected(row) && !failed) {
          // Explicitly reset to avoid color bleeding between rows on hover
          c.background = UIManager.getColor("Table.background") ?: UIUtil.getPanelBackground()
        }
        return c
      }
    }
    resultTable.setShowGrid(true)
    resultTable.showHorizontalLines = true
    resultTable.showVerticalLines = true
    resultTable.rowHeight = JBUI.scale(30)
    resultTable.fillsViewportHeight = true
    resultTable.rowSelectionAllowed = true
    resultTable.columnSelectionAllowed = true
    resultTable.cellSelectionEnabled = true
    resultTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
    resultTable.autoCreateRowSorter = true
    resultTable.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
    resultTable.emptyText.text = "暂无扫描结果，点击“扫描算子”开始"
    resultTable.toolTipText = "" // enable per-cell tooltip
    resultTable.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
      override fun mouseMoved(e: java.awt.event.MouseEvent) {
        val viewRow = resultTable.rowAtPoint(e.point)
        val viewCol = resultTable.columnAtPoint(e.point)
        if (viewRow < 0 || viewCol < 0) {
          resultTable.toolTipText = null
          return
        }
        val modelRow = resultTable.convertRowIndexToModel(viewRow)
        val row = currentRows.getOrNull(modelRow)
        resultTable.toolTipText = when (viewCol) {
          0 -> row?.let { packFailHintByOpKey["${it.className}|${it.name}"] }
          2 -> row?.implClasses?.takeIf { it.isNotEmpty() }?.joinToString("\n")
            ?: "无实现态（computionFramework!=sengee 未扫描到）"
          1 -> row?.let {
            "⌘/Ctrl+C：复制当前选中单元格/选区。右键可复制显示名或全限定类名。\n全限定类名：${it.className}"
          }
          else -> null
        }
      }
    })

    var packCheckboxAnchorModelRow = -1
    resultTable.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        val row = resultTable.rowAtPoint(e.point)
        val col = resultTable.columnAtPoint(e.point)
        if (row < 0 || col != 0 || !SwingUtilities.isLeftMouseButton(e)) return
        val modelRow = resultTable.convertRowIndexToModel(row)
        if (e.isShiftDown && packCheckboxAnchorModelRow >= 0) {
          val anchor = packCheckboxAnchorModelRow
          val checked = tableModel.getValueAt(anchor, 0) as? Boolean ?: false
          val lo = kotlin.math.min(anchor, modelRow)
          val hi = kotlin.math.max(anchor, modelRow)
          for (r in lo..hi) {
            tableModel.setValueAt(checked, r, 0)
          }
          e.consume()
        }
      }

      override fun mouseReleased(e: MouseEvent) {
        val row = resultTable.rowAtPoint(e.point)
        val col = resultTable.columnAtPoint(e.point)
        if (row < 0 || col != 0 || !SwingUtilities.isLeftMouseButton(e)) return
        val modelRow = resultTable.convertRowIndexToModel(row)
        if (!e.isShiftDown) {
          packCheckboxAnchorModelRow = modelRow
        }
      }
    })

    fun openFileInEditor(file: VirtualFile) {
      if (!file.isValid || file.isDirectory) return
      runCatching { FileEditorManager.getInstance(project).openFile(file, true) }
    }

    fun addPackageIfMissing(
      plan: LinkedHashMap<VirtualFile, MutableList<MavenRunnerParameters>>,
      pomDir: VirtualFile
    ) {
      val list = plan.getOrPut(pomDir) { mutableListOf() }
      val alreadyHasPackage = list.any { it.goals.any { g -> g == MAVEN_PACKAGE_GOAL } }
      if (alreadyHasPackage) return
      val alreadyHasClean = list.any { it.goals.any { g -> g == MAVEN_CLEAN_GOAL } }
      if (!alreadyHasClean) {
        list += createMavenParams(
          pomDir = pomDir,
          goals = listOf(MAVEN_CLEAN_GOAL),
          cmdOptions = null
        )
      }
      list += createMavenParams(
        pomDir = pomDir,
        goals = listOf(MAVEN_PACKAGE_GOAL),
        cmdOptions = buildMavenCmdOptions(existing = null, extra = listOf(MAVEN_SKIP_TESTS_OPTION))
      )
    }


    fun firstExisting(refs: List<ResourceRef>): VirtualFile? =
      refs.asSequence().map { it.file }.firstOrNull { it.isValid && !it.isDirectory }

    val columnModel = resultTable.columnModel
    columnModel.getColumn(0).preferredWidth = JBUI.scale(60)
    columnModel.getColumn(0).minWidth = JBUI.scale(60)
    columnModel.getColumn(0).maxWidth = JBUI.scale(80)
    columnModel.getColumn(1).preferredWidth = JBUI.scale(320)
    columnModel.getColumn(1).minWidth = JBUI.scale(180)
    columnModel.getColumn(2).preferredWidth = JBUI.scale(90)
    columnModel.getColumn(2).minWidth = JBUI.scale(90)
    columnModel.getColumn(2).maxWidth = JBUI.scale(110)
    for (index in 3..5) {
      columnModel.getColumn(index).preferredWidth = JBUI.scale(90)
      columnModel.getColumn(index).minWidth = JBUI.scale(90)
      columnModel.getColumn(index).maxWidth = JBUI.scale(110)
    }

    val packSelectAllHeaderCheck = JBCheckBox().apply {
      border = JBUI.Borders.empty(2)
      toolTipText = "点按可选中或清空当前列表全部算子（含搜索筛选结果）"
    }
    val packSelectHeaderPanel = JPanel(FlowLayout(FlowLayout.CENTER, 0, JBUI.scale(2))).apply {
      isOpaque = true
      add(packSelectAllHeaderCheck)
    }
    columnModel.getColumn(0).headerRenderer = TableCellRenderer { table, _, _, _, _, _ ->
      packSelectHeaderPanel.background = table.tableHeader.background
      packSelectHeaderPanel
    }

    val resourceStatusCellRenderer = object : DefaultTableCellRenderer() {
      init {
        horizontalAlignment = SwingConstants.CENTER
      }

      override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
      ): java.awt.Component {
        val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (c is javax.swing.JLabel) {
          // 固定色值，避免部分主题下 Label.errorForeground / Link.successForeground 解析成非预期颜色
          when (value?.toString()) {
            "✓" -> c.foreground = JBColor(0x1B5E20, 0xA5D6A7)
            "✗" -> c.foreground = JBColor(0xC62828, 0xFF8A80)
            else -> {}
          }
        }
        return c
      }
    }
    for (index in 3..5) {
      resultTable.columnModel.getColumn(index).cellRenderer = resourceStatusCellRenderer
    }

    val implCountCellRenderer = object : DefaultTableCellRenderer() {
      init {
        horizontalAlignment = SwingConstants.CENTER
      }

      override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
      ): java.awt.Component {
        val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (c is javax.swing.JLabel) {
          val n = value?.toString()?.toIntOrNull() ?: 0
          c.foreground = if (n > 0) {
            JBColor(0x1B5E20, 0xA5D6A7)
          } else {
            JBColor(0xC62828, 0xFF8A80)
          }
        }
        return c
      }
    }
    resultTable.columnModel.getColumn(2).cellRenderer = implCountCellRenderer

    // Right-side detail panel removed (list-only toolwindow).
    val helpPreviewDisposable = Disposer.newDisposable("DatayooPicker")

    // Scan target is always project root (simpler UX).
    val scanTarget: ScanTarget? = project.basePath?.takeIf { it.isNotBlank() }?.let {
      ScanTarget(key = it, label = "项目根目录")
    }

    val searchField = SearchTextField(false).apply {
      textEditor.toolTipText = "搜索算子名/中文名/实现态类"
      textEditor.emptyText.text = "搜索算子名/中文名/实现态类"
    }
    val operatorListCountLabel = JBLabel("共 0 个算子").apply {
      border = JBUI.Borders.emptyLeft(8)
    }
    // project scale is small (20-30 operators), keep only search filter

    // List-level pack is "merge selected" only; per-operator pack lives in row context menu.
    val packDescriptorCheck = JBCheckBox("定义态(Descriptor)", true)
    val packOyezCheck = JBCheckBox("实现态(Oyez)", true)
    val packStartButton = JButton("开始打包") // kept for compatibility; not shown in UI (ActionToolbar used)
    packStartButton.toolTipText = "按当前配置执行打包"

    val showExcludedOperatorsCheck = JBCheckBox("显示已排除", false).apply {
      toolTipText =
        "默认隐藏「从此工程排除」的算子；勾选后可查看并右键「取消排除」。排除列表按工程持久保存。"
    }

    var scanning = false
    var scanToolbar: ActionToolbar? = null
    var packToolbar: ActionToolbar? = null

    runCatching {
      val te = searchField.textEditor
      if (te is javax.swing.JTextField) {
        te.columns = 14
      }
    }
    // Scan toolbar is created later; mount it here after init.
    val scanToolbarHolder = JPanel(BorderLayout()).apply {
      isOpaque = false
    }
    val filterEastCluster = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
      isOpaque = false
    }
    filterEastCluster.add(showExcludedOperatorsCheck)
    filterEastCluster.add(scanToolbarHolder)

    /** 顶行：搜索框占中间可伸缩宽度，右侧为「显示已排除」+ 扫描工具栏，避免窄停靠时整行被撑爆。 */
    val filterBar = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(4, 8)
      add(searchField, BorderLayout.CENTER)
      add(filterEastCluster, BorderLayout.EAST)
    }

    fun operatorRuntimeKey(row: OperatorRow): String = "${row.className}|${row.name}"

    fun operatorExcludeSettings(): OperatorExcludeSettings = project.service()

    fun syncPackSelectHeaderCheckbox() {
      val n = currentRows.size
      if (n == 0) {
        packSelectAllHeaderCheck.isEnabled = false
        packSelectAllHeaderCheck.isSelected = false
        return
      }
      packSelectAllHeaderCheck.isEnabled = true
      val selCount = currentRows.count { packSelection[operatorRuntimeKey(it)] == true }
      packSelectAllHeaderCheck.isSelected = selCount == n
    }

    fun resolveHelpFile(row: OperatorRow): VirtualFile? {
      val runtimeFile = runtimeHelpFiles[operatorRuntimeKey(row)]
      if (runtimeFile != null && runtimeFile.isValid && !runtimeFile.isDirectory) return runtimeFile
      return row.helpRefs.firstOrNull { !it.file.isDirectory }?.file
    }

    var implIndex: Map<String, List<String>> = emptyMap()
    var implModulePomPathByOperatorName: Map<String, String> = emptyMap()
    /** FQCN → 扫描 descriptor 源文件时记录的 Maven module 根（canonical） */
    var descriptorModulePomPathByFqcn: Map<String, String> = emptyMap()

    fun refreshSingleOperator(row: OperatorRow): OperatorRow {
      val descriptorRoot = findDescriptorRootForOperator(project, row)
        ?: return row
      val resourceIndex = ReadAction.compute<ResourceIndex, RuntimeException> {
        buildResourceIndex(listOf(descriptorRoot))
      }
      val normalizedName = normalizeOperatorName(row.name)
      val implClasses = implIndex[normalizedName].orEmpty()
      val implModulePomPath = implModulePomPathByOperatorName[normalizedName] ?: row.implModulePomPath
      val descriptorModulePomPath =
        descriptorModulePomPathByFqcn[row.className.trim()] ?: row.descriptorModulePomPath
      val i18nRefs = pickResourcesByName(resourceIndex.i18nEntries, normalizedName, allowDotPrefix = true)
      val portraitRefs = pickPortraitResourcesByName(resourceIndex.portraitEntries, normalizedName)
      val helpRefs = pickResourcesByName(resourceIndex.helpEntries, normalizedName, allowDotPrefix = true)
      val localizedName = resolveLocalizedName(normalizedName, i18nRefs)
      val missingResources = mutableListOf<String>()
      if (helpRefs.isEmpty()) missingResources += "帮助文档"
      if (i18nRefs.isEmpty()) missingResources += "国际化"
      if (portraitRefs.isEmpty()) missingResources += "Logo"
      val displayName = if (!localizedName.isNullOrBlank()) "${row.name}（$localizedName）" else row.name
      val resourceStatus = if (missingResources.isEmpty()) "完整" else "缺少：${missingResources.joinToString("、")}"
      return row.copy(
        localizedName = localizedName,
        displayName = displayName,
        implClasses = implClasses,
        implModulePomPath = implModulePomPath,
        descriptorModulePomPath = descriptorModulePomPath,
        missingResources = missingResources,
        resourceStatus = resourceStatus,
        i18nRefs = i18nRefs,
        portraitRefs = portraitRefs,
        helpRefs = helpRefs
      )
    }

    fun replaceRow(list: List<OperatorRow>, updated: OperatorRow): List<OperatorRow> {
      val idx = list.indexOfFirst { it.className == updated.className && it.name == updated.name }
      if (idx < 0) return list
      return list.toMutableList().also { it[idx] = updated }
    }

    fun updateTableRowForOperator(updated: OperatorRow) {
      allRows = replaceRow(allRows, updated)
      currentRows = replaceRow(currentRows, updated)
      val idx = currentRows.indexOfFirst { it.className == updated.className && it.name == updated.name }
      if (idx < 0) return

      // Update visible table model row (model index == idx because we always rebuild tableModel in refreshResultTable)
      if (idx < tableModel.rowCount) {
        tableModel.setValueAt(updated.displayName, idx, 1)
        tableModel.setValueAt(updated.implClasses.size.toString(), idx, 2)
        tableModel.setValueAt(if (updated.i18nRefs.isNotEmpty()) "✓" else "✗", idx, 3)
        tableModel.setValueAt(if (updated.helpRefs.isNotEmpty()) "✓" else "✗", idx, 4)
        tableModel.setValueAt(if (updated.portraitRefs.isNotEmpty()) "✓" else "✗", idx, 5)
      }
    }

    fun runI18nGenerateForRow(row: OperatorRow) {
      val javaFile = findSourceFileByClassName(project, row.className)
      if (javaFile == null) {
        setInlineStatus("未找到定义态源码：${row.className}", PackLogTone.WARN)
        return
      }
      val descRoot = findDescriptorRootForOperator(project, row)
      if (descRoot == null) {
        setInlineStatus("未找到 descriptor 目录，无法生成 i18n。", PackLogTone.WARN)
        return
      }
      val resourcesRoot = WriteAction.compute<VirtualFile, RuntimeException> {
        descRoot.findFileByRelativePath("src/main/resources")
          ?: VfsUtil.createDirectoryIfMissing(descRoot, "src/main/resources")
      }
      if (resourcesRoot == null) {
        setInlineStatus("无法创建或定位 src/main/resources", PackLogTone.ERROR)
        return
      }
      var result = WriteAction.compute<I18nGenerator.Result, RuntimeException> {
        I18nGenerator.generateForDescriptorFile(javaFile, resourcesRoot, overwriteExisting = false)
      }
      if (!result.wroteFile && result.message.contains("已存在")) {
        if (Messages.showYesNoDialog(
            project,
            "已存在 ${row.name} 的 i18n 文件，是否覆盖生成？",
            "覆盖确认",
            Messages.getWarningIcon()
          ) != Messages.YES
        ) {
          setInlineStatus(result.message, PackLogTone.WARN)
          return
        }
        result = WriteAction.compute<I18nGenerator.Result, RuntimeException> {
          I18nGenerator.generateForDescriptorFile(javaFile, resourcesRoot, overwriteExisting = true)
        }
      }
      setInlineStatus(
        result.message,
        if (result.wroteFile) PackLogTone.OK else PackLogTone.WARN
      )
      if (result.wroteFile) {
        val updated = refreshSingleOperator(row)
        updateTableRowForOperator(updated)
      }
    }

    tableModel.addTableModelListener { e ->
      val rowIndex = e.firstRow
      val col = e.column
      if (rowIndex < 0 || col != 0) return@addTableModelListener
      val row = currentRows.getOrNull(rowIndex) ?: return@addTableModelListener
      val key = operatorRuntimeKey(row)
      val value = tableModel.getValueAt(rowIndex, 0) as? Boolean ?: false
      packSelection[key] = value
      syncPackSelectHeaderCheckbox()
    }

    fun resetOperatorDetail() {
      selectedDetailRow = null
    }

    fun refreshResultTable(rows: List<OperatorRow>) {
      currentRows = rows
      tableModel.setRowCount(0)
      rows.forEach { row ->
        val key = operatorRuntimeKey(row)
        val checked = packSelection[key] ?: false
        tableModel.addRow(
          arrayOf(
            checked,
            row.displayName,
            row.implClasses.size.toString(),
            if (row.i18nRefs.isNotEmpty()) "✓" else "✗",
            if (row.helpRefs.isNotEmpty()) "✓" else "✗",
            if (row.portraitRefs.isNotEmpty()) "✓" else "✗"
          )
        )
      }
      val total = allRows.size
      val q = searchField.text.trim()
      val ex = operatorExcludeSettings()
      val nExcluded = allRows.count { ex.isExcluded(operatorRuntimeKey(it)) }
      val showingExcluded = showExcludedOperatorsCheck.isSelected
      operatorListCountLabel.text = if (q.isBlank()) {
        buildString {
          append("共 ${rows.size} 个算子")
          when {
            nExcluded > 0 && !showingExcluded -> append("（已排除 $nExcluded 个，勾选「显示已排除」可恢复）")
            nExcluded > 0 && showingExcluded -> append("（含已排除 $nExcluded 个）")
          }
        }
      } else {
        "显示 ${rows.size} 个算子（共 $total 个）"
      }
      resetOperatorDetail()
      syncPackSelectHeaderCheckbox()
    }

    fun applyPackSelectionToVisibleRows(checked: Boolean) {
      for (row in currentRows) {
        packSelection[operatorRuntimeKey(row)] = checked
      }
      for (modelIdx in 0 until tableModel.rowCount) {
        tableModel.setValueAt(checked, modelIdx, 0)
      }
      syncPackSelectHeaderCheckbox()
    }

    fun applyPackSelectionByKeys(keys: Set<String>, checked: Boolean = true) {
      if (keys.isEmpty()) return
      for (k in keys) {
        packSelection[k] = checked
      }
      for (modelIdx in 0 until tableModel.rowCount) {
        val row = currentRows.getOrNull(modelIdx) ?: continue
        if (operatorRuntimeKey(row) in keys) {
          tableModel.setValueAt(checked, modelIdx, 0)
        }
      }
      syncPackSelectHeaderCheckbox()
    }

    // 表头仅渲染复选框外观，真正的勾选逻辑必须由 tableHeader 接管点击（否则组件收不到事件）。
    (resultTable.rowSorter as? TableRowSorter<*>)?.setSortable(0, false)
    resultTable.tableHeader.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (!SwingUtilities.isLeftMouseButton(e)) return
        val viewCol = resultTable.tableHeader.columnAtPoint(e.point)
        if (viewCol != 0) return
        e.consume()
        val allSelected =
          currentRows.isNotEmpty() && currentRows.all { packSelection[operatorRuntimeKey(it)] == true }
        applyPackSelectionToVisibleRows(!allSelected)
      }
    })

    fun copyTableSelectionToClipboard() {
      val sel = resultTable.selectedRows
      val viewRows = when {
        sel.isNotEmpty() -> sel
        else -> {
          val lead = resultTable.selectionModel.leadSelectionIndex
          if (lead >= 0) intArrayOf(lead) else intArrayOf()
        }
      }
      if (viewRows.isEmpty()) return
      val viewCols = resultTable.selectedColumns
      val lines = viewRows.sorted().mapNotNull { vr ->
        val mr = resultTable.convertRowIndexToModel(vr)
        val row = currentRows.getOrNull(mr) ?: return@mapNotNull null
        if (viewCols.isNotEmpty()) {
          val cols = viewCols.sorted().mapNotNull { vc ->
            val mc = resultTable.convertColumnIndexToModel(vc)
            when (mc) {
              0 -> if ((tableModel.getValueAt(mr, 0) as? Boolean) == true) "✓" else "✗"
              1 -> row.displayName
              2, 3, 4, 5 -> tableModel.getValueAt(mr, mc)?.toString().orEmpty()
              else -> null
            }
          }
          if (cols.isEmpty()) null else cols.joinToString("\t")
        } else {
          row.displayName.takeIf { it.isNotBlank() }
        }
      }
      if (lines.isEmpty()) return
      CopyPasteManager.getInstance().setContents(StringSelection(lines.joinToString("\n")))
    }

    val copyOperatorsShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
    resultTable.getInputMap(JComponent.WHEN_FOCUSED).put(copyOperatorsShortcut, "DatayooPicker.CopyOperatorNames")
    resultTable.actionMap.put(
      "DatayooPicker.CopyOperatorNames",
      object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
          copyTableSelectionToClipboard()
        }
      }
    )

    data class ZipTargets(
      val descriptorZip: java.io.File?,
      val oyezZip: java.io.File?
    )
    val zipTargetsByKey = linkedMapOf<String, ZipTargets>()

    fun computeZipTargets(row: OperatorRow): ZipTargets {
      val baseName = marketplacePackBaseName(row)

      val descriptorPomDir = row.descriptorModulePomPath?.trim()?.takeIf { it.isNotBlank() }?.let { path ->
        LocalFileSystem.getInstance().refreshAndFindFileByPath(path.replace('\\', '/'))
          ?.takeIf { it.isValid && it.isDirectory }
      } ?: findDescriptorRootForOperator(project, row)?.let { findNearestPomDir(it) }
      // 1) per-operator: {baseName}-descriptor-{version}.zip
      val descriptorPattern = Regex("^${Regex.escape(baseName)}-descriptor-.+\\.zip$", RegexOption.IGNORE_CASE)
      // 2) fallback: any module-level *-descriptor-*.zip (latest)
      val moduleDescPattern = Regex(".+-descriptor-.+\\.zip$", RegexOption.IGNORE_CASE)
      val descriptorZip = descriptorPomDir?.let {
        val targetDir = java.io.File(it.path, "target")
        val match = targetDir.listFiles()
          ?.asSequence()
          ?.filter { f -> f.isFile && f.length() > 0L && descriptorPattern.matches(f.name) }
          ?.sortedByDescending { f -> f.lastModified() }
          ?.firstOrNull()
        match
          ?: targetDir.listFiles()
            ?.asSequence()
            ?.filter { f -> f.isFile && f.length() > 0L && moduleDescPattern.matches(f.name) }
            ?.sortedByDescending { f -> f.lastModified() }
            ?.firstOrNull()
      }
        ?: descriptorPomDir?.let { java.io.File(it.path, "target/$baseName.zip") }?.takeIf { it.exists() && it.isFile && it.length() > 0L }

      val implPomDir = resolveImplModulePomDir(project, row)
      val oyezPattern = Regex("^${Regex.escape(baseName)}-oyez-.+\\.zip$", RegexOption.IGNORE_CASE)
      // fallback: any module-level *-oyez-*.zip (latest)
      val moduleOyezPattern = Regex(".+-oyez-.+\\.zip$", RegexOption.IGNORE_CASE)
      val oyezZip = implPomDir?.let { dir ->
        val targetDir = java.io.File(dir.path, "target")
        val versioned = targetDir.listFiles()
          ?.asSequence()
          ?.filter { f -> f.isFile && f.length() > 0L && oyezPattern.matches(f.name) }
          ?.sortedByDescending { f -> f.lastModified() }
          ?.firstOrNull()
        versioned
          ?: targetDir.listFiles()
            ?.asSequence()
            ?.filter { f -> f.isFile && f.length() > 0L && moduleOyezPattern.matches(f.name) }
            ?.sortedByDescending { f -> f.lastModified() }
            ?.firstOrNull()
          ?: java.io.File(dir.path, "target/$baseName.zip").takeIf { it.exists() && it.isFile && it.length() > 0L }
      }

      return ZipTargets(descriptorZip = descriptorZip, oyezZip = oyezZip)
    }

    /** 上传页必须与当前算子一致：防止 target 里混入同名前缀 zip 或缓存错位。 */
    fun zipMatchesOperator(row: OperatorRow, kind: OssUploader.PackKind, zip: java.io.File): Boolean {
      val base = marketplacePackBaseName(row)
      val n = zip.name
      return when (kind) {
        OssUploader.PackKind.MARKETPLACE, OssUploader.PackKind.DESCRIPTOR -> {
          // 与 computeZipTargets 一致：{base}-descriptor-{version}.zip
          val m = Regex("^${Regex.escape(base)}-descriptor-.+\\.zip$", RegexOption.IGNORE_CASE).matches(n)
          val plain = n.equals("$base.zip", ignoreCase = true)
          m || plain
        }
        OssUploader.PackKind.OYEZ -> {
          val versioned = Regex("^${Regex.escape(base)}-oyez-.+\\.zip$", RegexOption.IGNORE_CASE).matches(n)
          val plain = n.equals("$base.zip", ignoreCase = true)
          versioned || plain
        }
      }
    }

    fun refreshZipTargetsForVisibleRows() {
      zipTargetsByKey.clear()
      currentRows.forEach { row ->
        zipTargetsByKey[operatorRuntimeKey(row)] = computeZipTargets(row)
      }
    }

    fun refreshVisibleRows() {
      val query = searchField.text.trim()
      val q = query.lowercase()
      val ex = operatorExcludeSettings()
      val showExcluded = showExcludedOperatorsCheck.isSelected
      val filtered = allRows.filter { row ->
        val key = operatorRuntimeKey(row)
        if (!showExcluded && ex.isExcluded(key)) return@filter false
        val nameHit = row.name.lowercase().contains(q) ||
          (row.localizedName ?: "").lowercase().contains(q) ||
          row.displayName.lowercase().contains(q) ||
          row.className.lowercase().contains(q) ||
          row.implClasses.any { it.lowercase().contains(q) }
        if (q.isNotBlank() && !nameHit) return@filter false
        true
      }
      refreshResultTable(filtered)
      refreshZipTargetsForVisibleRows()
    }

    showExcludedOperatorsCheck.addActionListener { refreshVisibleRows() }

    fun applyFiltersAndRefreshTable() {
      refreshVisibleRows()
    }

    fun uploadKindLabel(kind: OssUploader.PackKind): String = when (kind) {
      OssUploader.PackKind.DESCRIPTOR -> "定义态 OSS"
      OssUploader.PackKind.OYEZ -> "实现态 OSS"
      OssUploader.PackKind.MARKETPLACE -> "商城"
    }

    fun uploadMallStepDescription(kind: OssUploader.PackKind): String = when (kind) {
      OssUploader.PackKind.MARKETPLACE -> "定义态包（商城描述/元数据）"
      OssUploader.PackKind.OYEZ -> "实现态包（可运行）"
      OssUploader.PackKind.DESCRIPTOR -> "定义态包（descriptor）"
    }
    var reloadHistoryAfterUpload: ((OperatorRow) -> Unit)? = null
    var reloadConnectorHistoryAfterUpload: ((ConnectorScanner.ConnectorRow) -> Unit)? = null

    fun runMallUploadSteps(changeLog: String) {
      val pending = currentMallUploadSteps()
      if (pending.isEmpty()) return
      activateRunConsole()
      val tozooGlobal = ApplicationManager.getApplication().getService(TozooUploadSettings::class.java)
      val baseUrl = tozooGlobal.state.baseUrl.trim()
      if (baseUrl.isEmpty()) {
        appendPackLogLine("Tozoo 根地址未配置：请在工具窗口标题栏「+」→「Tozoo 根地址…」填写后再上传。", PackLogTone.ERROR)
        return
      }

      val titleSuffix = if (pending.size == 1) pending[0].label else "共 ${pending.size} 个包"
      val commodityLabel = if (pending.any { it.isConnector }) "连接器" else "算子"
      ProgressManager.getInstance().run(object : Task.Backgroundable(
        project,
        "数由 商城上传 · $titleSuffix",
        true
      ) {
        override fun run(indicator: ProgressIndicator) {
          runningUploadIndicator.set(indicator)
          mallUploadStopBtn.isEnabled = true
          try {
            indicator.isIndeterminate = false
            val total = pending.size
            appendPackLogLine(
              "== 商城上传（Tozoo · $commodityLabel）：$titleSuffix（共 $total 步）==",
              PackLogTone.DEFAULT
            )
            logger.info("Mall upload start: kind=$commodityLabel items=${pending.size}, steps=$total")
            var errorSummary: String? = null
            for ((idx, step) in pending.withIndex()) {
              indicator.checkCanceled()
              val uploadFileName = step.zipFile.name
              val stepNo = idx + 1
              val desc = if (step.isConnector) {
                "连接器安装包"
              } else {
                uploadMallStepDescription(step.operatorUploadKind!!)
              }
              indicator.fraction = idx.toDouble() / total.toDouble()
              indicator.text = "$stepNo/$total · ${step.label} · $desc · $uploadFileName"

              appendPackLogLine(
                "[$stepNo/$total] ${step.label} · $desc — $uploadFileName（${step.zipFile.length()} bytes）",
                PackLogTone.PROGRESS
              )

              val uploadFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                if (step.isConnector) {
                  TozooUploadClient.uploadConnectorZip(baseUrl, step.zipFile, changeLog, uploadFileName)
                } else {
                  TozooUploadClient.uploadOperatorZip(baseUrl, step.zipFile, changeLog, uploadFileName)
                }
              }
              while (!uploadFuture.isDone) {
                if (indicator.isCanceled) {
                  uploadFuture.cancel(true)
                  throw ProcessCanceledException()
                }
                java.util.concurrent.TimeUnit.MILLISECONDS.sleep(200)
              }
              val r = uploadFuture.get()
              val ok = r.ok
              if (!ok) {
                appendPackLogLine(
                  "Tozoo 失败 · HTTP ${r.status} — ${r.message}",
                  PackLogTone.ERROR
                )
                logger.warn("Mall upload Tozoo fail: $uploadFileName HTTP ${r.status}")
                errorSummary = "${step.label}：第 $stepNo 步失败（Tozoo / $desc）\nHTTP ${r.status}\n${r.message}"
              } else {
                appendPackLogLine("Tozoo 成功 · HTTP ${r.status}", PackLogTone.OK)
                logger.info("Mall upload Tozoo ok: $uploadFileName HTTP ${r.status}")
              }
              indicator.fraction = stepNo.toDouble() / total.toDouble()
              if (!ok) break
            }
            indicator.fraction = 1.0
            ApplicationManager.getApplication().invokeLater {
              if (errorSummary != null) {
                appendPackLogLine(errorSummary!!, PackLogTone.ERROR)
              } else {
                appendPackLogLine("商城上传已完成（${pending.size} 个包，经 Tozoo）。", PackLogTone.OK)
                val last = pending.last()
                if (last.isConnector) {
                  mallPendingConnectors.lastOrNull()?.row?.let { reloadConnectorHistoryAfterUpload?.invoke(it) }
                } else {
                  last.operatorRow?.let { reloadHistoryAfterUpload?.invoke(it) }
                }
              }
            }
          } catch (_: ProcessCanceledException) {
            appendPackLogLine("商城上传已由用户停止。", PackLogTone.WARN)
          } finally {
            runningUploadIndicator.set(null)
            ApplicationManager.getApplication().invokeLater {
              mallUploadStopBtn.isEnabled = false
            }
          }
        }
      })
    }

    // Assigned after navigation UI is created.
    var showMallUploadPage: () -> Unit = {}
    var showHistoryPage: (String) -> Unit = {}

    fun prepareMallUpload(row: OperatorRow, uploadKind: OssUploader.PackKind) {
      activateRunConsole()
      val tozooBase =
        ApplicationManager.getApplication().getService(TozooUploadSettings::class.java).state.baseUrl.trim()
      if (tozooBase.isEmpty()) {
        ApplicationManager.getApplication().invokeLater {
          val msg = "Tozoo 根地址未配置：请在「+」中设置后再上传。"
          appendPackLogLine(msg, PackLogTone.ERROR)
          setInlineStatus(msg, PackLogTone.ERROR)
        }
        return
      }
      val targets = computeZipTargets(row)
      zipTargetsByKey[operatorRuntimeKey(row)] = targets
      val dZip = targets.descriptorZip
      val oZip = targets.oyezZip
      val rawSelected = when (uploadKind) {
        OssUploader.PackKind.MARKETPLACE, OssUploader.PackKind.DESCRIPTOR -> dZip
        OssUploader.PackKind.OYEZ -> oZip
      }
      val selectedZip = rawSelected?.takeIf { zipMatchesOperator(row, uploadKind, it) }
      if (rawSelected != null && selectedZip == null) {
        ApplicationManager.getApplication().invokeLater {
          val msg =
            "target 下的「${rawSelected.name}」与当前算子「${row.name}」不匹配（期望前缀 ${marketplacePackBaseName(row)}），未打开上传页。"
          appendPackLogLine(msg, PackLogTone.ERROR)
          setInlineStatus(msg, PackLogTone.ERROR)
        }
        return
      }
      if (dZip == null && oZip == null) {
        ApplicationManager.getApplication().invokeLater {
          val msg =
            "未发现定义态或实现态 zip（各 module 的 target 下与算子同名的 zip）。请先对该算子执行打包。"
          appendPackLogLine(msg, PackLogTone.ERROR)
          setInlineStatus(msg, PackLogTone.ERROR)
        }
        return
      }
      if (selectedZip == null) {
        val hint = when (uploadKind) {
          OssUploader.PackKind.MARKETPLACE, OssUploader.PackKind.DESCRIPTOR -> "定义态"
          OssUploader.PackKind.OYEZ -> "实现态"
        }
        val expectedFile = when (uploadKind) {
          OssUploader.PackKind.MARKETPLACE, OssUploader.PackKind.DESCRIPTOR -> {
            "${marketplacePackBaseName(row)}-descriptor-<yyyyMMddHHmmss>.zip"
          }
          OssUploader.PackKind.OYEZ -> "${marketplacePackBaseName(row)}-oyez-<yyyyMMddHHmmss>.zip（或旧版同名 ${marketplacePackBaseName(row)}.zip）"
        }
        ApplicationManager.getApplication().invokeLater {
          val msg = "未找到$hint zip（target/$expectedFile）。请先对该算子执行对应打包。"
          appendPackLogLine(msg, PackLogTone.ERROR)
          setInlineStatus(msg, PackLogTone.ERROR)
        }
        return
      }
      mallPendingRow = row
      mallPendingConnectors = emptyList()
      mallPendingOperators = listOf(MallPendingOperator(row.displayName, uploadKind, selectedZip, row))
      refreshMallCommitPanel()
      val kindText = when (uploadKind) {
        OssUploader.PackKind.MARKETPLACE, OssUploader.PackKind.DESCRIPTOR -> "定义态"
        OssUploader.PackKind.OYEZ -> "实现态"
      }
      appendPackLogLine("已准备上传：${row.displayName}（$kindText）。请填写提交说明后点击「提交」。", PackLogTone.DEFAULT)
      ApplicationManager.getApplication().invokeLater { showMallUploadPage() }
    }

    val historyTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    fun formatHistoryTime(raw: String): String {
      val s = raw.trim()
      if (s.isEmpty()) return "-"
      val epoch = s.toLongOrNull()
      if (epoch != null) {
        val millis = if (s.length <= 10) epoch * 1000 else epoch
        return runCatching {
          Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(historyTimeFmt)
        }.getOrDefault(s)
      }
      return s.replace('T', ' ')
    }
    fun historyTimeMillis(raw: String): Long {
      val s = raw.trim()
      if (s.isEmpty()) return 0L
      val epoch = s.toLongOrNull()
      if (epoch != null) return if (s.length <= 10) epoch * 1000 else epoch
      return runCatching {
        val n = s.replace('T', ' ')
        val dt = java.time.LocalDateTime.parse(n, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
      }.getOrDefault(0L)
    }

    val historyColumns = arrayOf("框架", "版本", "时间", "变更说明")
    val historyTableModel = object : DefaultTableModel(historyColumns, 0) {
      override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    var historyCurrentItems: List<TozooUploadClient.HistoryItem> = emptyList()
    var historyCurrentRow: OperatorRow? = null
    var historyCurrentConnector: ConnectorScanner.ConnectorRow? = null
    /** 历史页「返回」回到的左侧导航项：0=算子列表，1=连接器 */
    var historyReturnNavIndex = 0
    val historyInfoLabel = JBLabel("请在算子列表中右键「历史」打开此页；表格支持 Ctrl/⌘、Shift 多选后批量删除").apply {
      border = JBUI.Borders.empty(0, 0, 8, 0)
    }
    fun historyPageStatus(text: String, tone: PackLogTone = PackLogTone.DEFAULT) {
      historyInfoLabel.text = text
      historyInfoLabel.foreground = when (tone) {
        PackLogTone.ERROR -> JBColor.namedColor("Label.errorForeground", JBColor.RED)
        PackLogTone.WARN -> JBColor.namedColor("Label.warningForeground", JBColor(0x8a6d00, 0xc8b000))
        PackLogTone.OK -> JBColor(0x0d7d0d, 0x6aab6a)
        PackLogTone.PROGRESS -> JBColor.namedColor("Link.activeForeground", JBColor(0x1866cc, 0x589df5))
        PackLogTone.DEFAULT -> UIUtil.getLabelForeground()
      }
    }
    val historyDeleteBtn = JButton("批量删除所选版本").apply {
      isEnabled = false
      toolTipText = "先在表格中用 Ctrl/⌘、Shift 多选记录（可选多条），再点此按钮"
    }
    val historyDeleteCommodityBtn = JButton("删除商品…").apply {
      toolTipText =
        "DELETE /manage/commodity/deleteByName：按 name 删除整个商品（含全部版本与 OSS）。区别于「批量删除所选版本」（按版本记录删除）。"
    }
    val historyBackBtn = JButton("返回")
    val historyTable = JBTable(historyTableModel).apply {
      setShowGrid(true)
      showHorizontalLines = true
      showVerticalLines = true
      rowHeight = JBUI.scale(28)
      fillsViewportHeight = true
      autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
      emptyText.text = "暂无历史记录"
      setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
    }
    historyTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(110)
    historyTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(130)
    historyTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(180)
    historyTable.selectionModel.addListSelectionListener {
      if (it.valueIsAdjusting) return@addListSelectionListener
      val rows = historyTable.selectedRows
      if (rows.isEmpty()) {
        historyDeleteBtn.isEnabled = false
        return@addListSelectionListener
      }
      val hasDeletable = rows.any { vr ->
        val modelRow = historyTable.convertRowIndexToModel(vr)
        val item = historyCurrentItems.getOrNull(modelRow)
        item != null && item.id.isNotBlank()
      }
      historyDeleteBtn.isEnabled = hasDeletable
    }

    fun renderHistoryPage(entityLabel: String, displayName: String, result: TozooUploadClient.HistoryResult) {
      historyTableModel.rowCount = 0
      historyCurrentItems = result.items
      result.items.forEach { item ->
        historyTableModel.addRow(
          arrayOf<Any>(
            item.framework.ifBlank { "-" },
            item.version.ifBlank { "-" },
            formatHistoryTime(item.createdTime),
            item.changeLog.ifBlank { "-" }
          )
        )
      }
      val apiHint =
        if (result.message.isNotBlank() && result.message != "OK") "   ${result.message}" else ""
      val tone =
        if (result.message.isNotBlank() && result.message != "OK") PackLogTone.WARN else PackLogTone.DEFAULT
      historyPageStatus(
        "$entityLabel：$displayName   共 ${result.totalElements} 条（当前第 ${result.pageNo} 页，每页 ${result.pageSize} 条）$apiHint",
        tone
      )
      historyDeleteBtn.isEnabled = false
    }

    fun clearHistoryPage(message: String, tone: PackLogTone = PackLogTone.DEFAULT) {
      historyTableModel.rowCount = 0
      historyCurrentItems = emptyList()
      historyPageStatus(message, tone)
      historyDeleteBtn.isEnabled = false
    }

    fun queryCommodityUploadHistory(entityLabel: String, displayName: String, queryName: String) {
      val baseUrl =
        ApplicationManager.getApplication().getService(TozooUploadSettings::class.java).state.baseUrl.trim()
      if (baseUrl.isBlank()) return
      showHistoryPage(displayName)
      clearHistoryPage("$entityLabel：$displayName   正在查询历史...", PackLogTone.PROGRESS)
      ApplicationManager.getApplication().executeOnPooledThread {
        val result = runCatching {
          TozooUploadClient.queryOperatorUploadHistory(
            baseUrl = baseUrl,
            operatorName = queryName,
            pageNo = 1,
            pageSize = 200
          )
        }.getOrElse { ex ->
          TozooUploadClient.HistoryResult(
            ok = false,
            status = -1,
            message = ex.message ?: ex::class.java.simpleName,
            items = emptyList(),
            totalElements = 0,
            pageNo = 1,
            pageSize = 200,
            body = ""
          )
        }
        ApplicationManager.getApplication().invokeLater {
          if (!result.ok) {
            clearHistoryPage(
              "$entityLabel：$displayName   历史查询失败：${result.message}",
              PackLogTone.ERROR
            )
            return@invokeLater
          }
          if (result.items.isEmpty()) {
            clearHistoryPage("$entityLabel：$displayName   暂无历史记录", PackLogTone.WARN)
            return@invokeLater
          }
          renderHistoryPage(entityLabel, displayName, result)
        }
      }
    }

    fun queryMallUploadHistory(row: OperatorRow) {
      val baseUrl =
        ApplicationManager.getApplication().getService(TozooUploadSettings::class.java).state.baseUrl.trim()
      if (baseUrl.isBlank()) {
        Messages.showWarningDialog(
          project,
          "Tozoo 根地址未配置：请先到「配置」页设置。",
          "上传历史"
        )
        return
      }
      historyReturnNavIndex = 0
      historyCurrentRow = row
      historyCurrentConnector = null
      queryCommodityUploadHistory("算子", row.displayName, row.name)
    }

    fun queryConnectorMallUploadHistory(row: ConnectorScanner.ConnectorRow) {
      val baseUrl =
        ApplicationManager.getApplication().getService(TozooUploadSettings::class.java).state.baseUrl.trim()
      if (baseUrl.isBlank()) {
        Messages.showWarningDialog(
          project,
          "Tozoo 根地址未配置：请先到「配置」页设置。",
          "上传历史"
        )
        return
      }
      val queryName = row.pluginName?.takeIf { it.isNotBlank() } ?: row.artifactId
      historyReturnNavIndex = 1
      historyCurrentRow = null
      historyCurrentConnector = row
      queryCommodityUploadHistory("连接器", row.displayName, queryName)
    }

    reloadHistoryAfterUpload = { r -> queryMallUploadHistory(r) }
    reloadConnectorHistoryAfterUpload = { r -> queryConnectorMallUploadHistory(r) }

    fun selectedRowsFromTableForDelete(): List<OperatorRow> {
      val checkedRows = currentRows.filter { row -> packSelection[operatorRuntimeKey(row)] == true }
      if (checkedRows.isNotEmpty()) {
        return checkedRows.distinctBy { "${it.className}|${it.name}" }
      }
      val viewRows = resultTable.selectedRows
      if (viewRows.isEmpty()) {
        val lead = resultTable.selectionModel.leadSelectionIndex
        if (lead < 0) return emptyList()
        val modelRow = resultTable.convertRowIndexToModel(lead)
        return listOfNotNull(currentRows.getOrNull(modelRow))
      }
      return viewRows.map { vr ->
        val modelRow = resultTable.convertRowIndexToModel(vr)
        currentRows.getOrNull(modelRow)
      }.filterNotNull().distinctBy { "${it.className}|${it.name}" }
    }

    fun deleteCommoditiesByName(rows: List<OperatorRow>, sourceTag: String) {
      val baseUrl =
        ApplicationManager.getApplication().getService(TozooUploadSettings::class.java).state.baseUrl.trim()
      if (baseUrl.isBlank()) {
        Messages.showWarningDialog(project, "Tozoo 根地址未配置：请先到「配置」页设置。", "删除商品")
        return
      }
      val targets = rows
        .map { it.name.trim() to it.displayName.trim() }
        .filter { it.first.isNotBlank() }
        .distinctBy { it.first }
      if (targets.isEmpty()) {
        Messages.showWarningDialog(project, "请先选择至少一个算子，再执行删除商品。", "删除商品")
        return
      }
      val preview = targets.take(10).joinToString("\n") { it.second.ifBlank { it.first } }
      val more = if (targets.size > 10) "\n… 共 ${targets.size} 个" else ""

      val okConfirm = Messages.showYesNoDialog(
        project,
        "来源：$sourceTag\n确认按名称删除以下 ${targets.size} 个商品吗？\n\n$preview$more\n\n将删除对应版本记录并删除 OSS（deleteOss=true）。",
        "删除商品",
        Messages.getQuestionIcon()
      )
      if (okConfirm != Messages.YES) return

      historyPageStatus("正在按名称删除商品（共 ${targets.size} 个）…", PackLogTone.PROGRESS)
      appendPackLogLine("删除商品：按名称批量删除 ${targets.size} 个（deleteByName）…", PackLogTone.PROGRESS)
      ApplicationManager.getApplication().executeOnPooledThread {
        val failures = mutableListOf<String>()
        val successes = mutableListOf<String>()
        val total = targets.size
        for ((idx, pair) in targets.withIndex()) {
          val step = idx + 1
          val commodityName = pair.first
          val displayName = pair.second
          ApplicationManager.getApplication().invokeLater {
            historyPageStatus("删除商品进度：$step/$total（${displayName.ifBlank { commodityName }}）…", PackLogTone.PROGRESS)
            setInlineStatus("删除商品进度：$step/$total（${displayName.ifBlank { commodityName }}）", PackLogTone.PROGRESS)
          }
          appendPackLogLine("删除商品 [$step/$total] START ${displayName.ifBlank { commodityName }}", PackLogTone.PROGRESS)
          val result = runCatching {
            TozooUploadClient.deleteCommodityByName(baseUrl, commodityName, deleteOss = true)
          }.getOrElse { ex ->
            TozooUploadClient.DeleteCommodityResult(
              ok = false,
              status = -1,
              message = ex.message ?: ex::class.java.simpleName,
              commodityName = commodityName
            )
          }
          if (result.ok) {
            successes += displayName.ifBlank { commodityName }
            appendPackLogLine("删除商品 [$step/$total] OK ${displayName.ifBlank { commodityName }}", PackLogTone.OK)
          } else {
            val failMsg = if (result.status == 404 && result.requestUrl.isNotBlank()) {
              "${displayName.ifBlank { commodityName }}：HTTP 404，URL=${result.requestUrl}"
            } else {
              "${displayName.ifBlank { commodityName }}：${result.message}"
            }
            failures += failMsg
            appendPackLogLine("删除商品 [$step/$total] FAIL $failMsg", PackLogTone.ERROR)
          }
        }
        ApplicationManager.getApplication().invokeLater {
          val refreshRow = historyCurrentRow
          if (failures.isEmpty()) {
            appendPackLogLine("删除商品成功：共 ${successes.size} 个（deleteByName）", PackLogTone.OK)
            historyPageStatus(
              "删除商品成功：${successes.size}/${targets.size}",
              PackLogTone.OK
            )
            Messages.showInfoMessage(project, "删除商品成功：${successes.size}/${targets.size}", "删除商品")
            refreshRow?.let { queryMallUploadHistory(it) }
          } else if (failures.size == targets.size) {
            appendPackLogLine("删除商品失败：${failures.take(3).joinToString("；")}${if (failures.size > 3) " …" else ""}", PackLogTone.ERROR)
            historyPageStatus("删除商品失败：${failures.take(2).joinToString("；")}${if (failures.size > 2) " …" else ""}", PackLogTone.ERROR)
            val urlHint = runCatching {
              val one = targets.firstOrNull()?.first.orEmpty()
              if (one.isBlank()) "" else {
                val base = baseUrl.trimEnd('/')
                val q = java.net.URLEncoder.encode(one.trim(), java.nio.charset.StandardCharsets.UTF_8)
                "\n\n示例请求URL（首条）:\n$base/manage/commodity/deleteByName?name=$q&deleteOss=true"
              }
            }.getOrDefault("")
            Messages.showErrorDialog(project, failures.take(8).joinToString("\n") + urlHint, "删除商品")
          } else {
            appendPackLogLine(
              "删除商品部分成功：成功 ${successes.size}/${targets.size}；失败 ${failures.size}",
              PackLogTone.WARN
            )
            historyPageStatus(
              "删除商品部分成功：成功 ${successes.size}/${targets.size}；失败 ${failures.size}",
              PackLogTone.WARN
            )
            val urlHint = runCatching {
              val one = targets.firstOrNull()?.first.orEmpty()
              if (one.isBlank()) "" else {
                val base = baseUrl.trimEnd('/')
                val q = java.net.URLEncoder.encode(one.trim(), java.nio.charset.StandardCharsets.UTF_8)
                "\n\n示例请求URL（首条）:\n$base/manage/commodity/deleteByName?name=$q&deleteOss=true"
              }
            }.getOrDefault("")
            Messages.showWarningDialog(project, failures.take(8).joinToString("\n") + urlHint, "删除商品（部分失败）")
            refreshRow?.let { queryMallUploadHistory(it) }
          }
        }
      }
    }

    historyDeleteCommodityBtn.addActionListener {
      val rows = listOfNotNull(historyCurrentRow)
      deleteCommoditiesByName(rows, "上传历史页")
    }

    historyDeleteBtn.addActionListener {
      val row = historyCurrentRow
      if (row == null) {
        Messages.showWarningDialog(project, "请先通过算子列表右键「历史」打开记录。", "批量删除版本")
        return@addActionListener
      }
      val viewRows = historyTable.selectedRows
      if (viewRows.isEmpty()) {
        Messages.showWarningDialog(project, "请先在历史列表中选中至少一条版本。", "批量删除版本")
        return@addActionListener
      }
      val items = viewRows.map { vr ->
        val modelRow = historyTable.convertRowIndexToModel(vr)
        historyCurrentItems.getOrNull(modelRow)
      }.filterNotNull().filter { it.id.isNotBlank() }.distinctBy { it.id }

      if (items.isEmpty()) {
        Messages.showErrorDialog(project, "所选记录缺少版本 ID，无法删除。", "批量删除版本")
        return@addActionListener
      }

      val preview = items.take(12).joinToString("\n") { v ->
        "${v.version.ifBlank { "-" }}  ${v.changeLog.ifBlank { "-" }.let { if (it.length > 80) it.take(80) + "…" else it }}"
      }
      val moreHint = if (items.size > 12) "\n… 共 ${items.size} 条" else ""
      val ok = Messages.showYesNoDialog(
        project,
        "确认删除以下 ${items.size} 个版本吗？将同时删除版本记录与 OSS 文件。\n\n$preview$moreHint",
        "批量删除版本",
        Messages.getQuestionIcon()
      ) == Messages.YES
      if (!ok) return@addActionListener

      val baseUrl =
        ApplicationManager.getApplication().getService(TozooUploadSettings::class.java).state.baseUrl.trim()
      if (baseUrl.isBlank()) {
        Messages.showWarningDialog(project, "Tozoo 根地址未配置：请先到「配置」页设置。", "批量删除版本")
        return@addActionListener
      }

      historyPageStatus(
        "正在批量删除 ${items.size} 个版本（算子：${row.displayName}）…",
        PackLogTone.PROGRESS
      )
      ApplicationManager.getApplication().executeOnPooledThread {
        val failures = mutableListOf<String>()
        for (item in items) {
          val deleteResult = runCatching {
            TozooUploadClient.deleteOperatorUploadHistoryVersion(baseUrl, item.id, deleteOss = true)
          }.getOrElse { ex ->
            TozooUploadClient.DeleteResult(false, -1, ex.message ?: ex::class.java.simpleName)
          }
          if (!deleteResult.ok) {
            failures += "${item.version.ifBlank { item.id }}：${deleteResult.message}"
          }
        }
        ApplicationManager.getApplication().invokeLater {
          when {
            failures.isEmpty() -> queryMallUploadHistory(row)
            failures.size >= items.size ->
              historyPageStatus(
                "批量删除失败：${failures.take(3).joinToString("；")}${if (failures.size > 3) " …" else ""}",
                PackLogTone.ERROR
              )
            else -> {
              historyPageStatus(
                "部分删除失败：成功 ${items.size - failures.size}/${items.size}。${failures.take(3).joinToString("；")}${if (failures.size > 3) " …" else ""}",
                PackLogTone.WARN
              )
              queryMallUploadHistory(row)
            }
          }
        }
      }
    }

    resultTable.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        val viewRow = resultTable.rowAtPoint(e.point)
        val viewCol = resultTable.columnAtPoint(e.point)
        if (viewRow < 0 || viewCol < 0) return
        val modelRow = resultTable.convertRowIndexToModel(viewRow)
        val row = currentRows.getOrNull(modelRow) ?: return

        if (e.clickCount != 2) return

        when (viewCol) {
          1 -> { // 算子：双击打开 descriptor 源码
            val f = findSourceFileByClassName(project, row.className)
            if (f != null) openFileInEditor(f) else setInlineStatus("未找到源码：${row.className}", PackLogTone.WARN)
          }

          2 -> { // 实现态数：打开第一个实现态源码
            val impl = row.implClasses.firstOrNull()
            if (impl.isNullOrBlank()) {
              setInlineStatus("当前算子未扫描到实现态类。", PackLogTone.WARN)
              return
            }
            val f = findSourceFileByClassName(project, impl)
            if (f != null) openFileInEditor(f) else setInlineStatus("未找到源码：$impl", PackLogTone.WARN)
          }

          3 -> { // i18n
            val f = firstExisting(row.i18nRefs)
            if (f != null) openFileInEditor(f) else setInlineStatus("未找到 i18n 文件。", PackLogTone.WARN)
          }

          4 -> { // 帮助
            val f = firstExisting(row.helpRefs)
            if (f != null) openFileInEditor(f) else setInlineStatus("未找到帮助文档。", PackLogTone.WARN)
          }

          5 -> { // 图标
            val f = firstExisting(row.portraitRefs)
            if (f != null) openFileInEditor(f) else setInlineStatus("未找到图标资源。", PackLogTone.WARN)
          }
        }
      }
    })

    fun setScanningState(isScanning: Boolean) {
      scanning = isScanning
      scanToolbar?.updateActionsImmediately()
    }

    fun doScan(showDialogOnEmpty: Boolean) {
      val currentIndicator = runningScanIndicator.get()
      if (currentIndicator != null && !currentIndicator.isCanceled) {
        statusArea.text = "扫描任务进行中..."
        return
      }

      setScanningState(true)
      statusArea.text = "扫描中..."
      val selectedTarget = scanTarget

      ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Datayoo 算子扫描", true) {
        override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
          runningScanIndicator.set(indicator)
          indicator.isIndeterminate = true
          indicator.text = "正在扫描算子"

          try {
            val (rootCandidates, scopeDesc) = ReadAction.compute<Pair<List<VirtualFile>, String>, RuntimeException> {
              resolveScanCandidates(
                project = project,
                scanTarget = selectedTarget
              )
            }
            if (rootCandidates.isEmpty()) {
              ApplicationManager.getApplication().invokeLater {
                setInlineStatus("扫描失败：$scopeDesc", PackLogTone.ERROR)
              }
              return
            }

            val roots = ReadAction.compute<List<VirtualFile>, RuntimeException> {
              collectScanRoots(rootCandidates)
            }
            if (roots.isEmpty()) {
              ApplicationManager.getApplication().invokeLater {
                setInlineStatus("扫描失败：在$scopeDesc 下未找到以 descriptor 结尾的目录。", PackLogTone.ERROR)
              }
              return
            }

            logger.info("Operator scan roots (${roots.size}): ${roots.joinToString(", ") { it.path }}")
            val resourceIndex = ReadAction.compute<ResourceIndex, RuntimeException> {
              buildResourceIndex(roots)
            }
            // Implementation index: scan all OpDefiner under scope roots, excluding *descriptor dirs.
            val implResult = ReadAction.compute<ImplIndexResult, RuntimeException> {
              buildImplementationIndex(project, rootCandidates) { !indicator.isCanceled }
            }
            implIndex = implResult.index
            implModulePomPathByOperatorName = implResult.implModulePomPathByOperator
            lastScanDebug = implResult.debugText
            val implOperatorCount = implIndex.size
            val implClassCount = implIndex.values.sumOf { it.size }
            val operators = linkedSetOf<String>()
            val mergedDescriptorPomByFqcn = linkedMapOf<String, String>()
            var visitedFiles = 0
            var sourceFiles = 0
            var candidateFiles = 0
            var annotationCount = 0
            var totalElapsedMs = 0L

            roots.forEachIndexed { index, root ->
              indicator.checkCanceled()
              indicator.text = "扫描 descriptor 目录 (${index + 1}/${roots.size})"
              val result = ReadAction.compute<OperatorScanner.ScanResult, RuntimeException> {
                OperatorScanner.scan(root) { !indicator.isCanceled }
              }
              operators.addAll(result.operators)
              result.descriptorModulePomByFqcn.forEach { (fqcn, path) ->
                mergedDescriptorPomByFqcn.putIfAbsent(fqcn, path)
              }
              visitedFiles += result.visitedFileCount
              sourceFiles += result.sourceFileCount
              candidateFiles += result.candidateFileCount
              annotationCount += result.annotationCount
              totalElapsedMs += result.elapsedMs
            }

            val rows = if (operators.isEmpty()) {
              emptyList()
            } else {
              operators.map {
                parseOperatorRow(
                  it,
                  resourceIndex,
                  implIndex,
                  implModulePomPathByOperatorName,
                  mergedDescriptorPomByFqcn
                )
              }
            }
            val implSummary = "实现态索引：${implOperatorCount} 个算子 / ${implClassCount} 个实现态类"
            val statusText = if (operators.isEmpty()) {
              "扫描完成：0 个算子（范围=$scopeDesc, descriptor目录=${roots.size}, 文件=$visitedFiles, 源码=$sourceFiles, 候选=$candidateFiles, 注解=$annotationCount, ${totalElapsedMs}ms）"
            } else {
              "扫描完成：${operators.size} 个算子（范围=$scopeDesc, descriptor目录=${roots.size}, 文件=$visitedFiles, 源码=$sourceFiles, 候选=$candidateFiles, 注解=$annotationCount, ${totalElapsedMs}ms）"
            }

            ApplicationManager.getApplication().invokeLater {
              descriptorModulePomPathByFqcn = mergedDescriptorPomByFqcn.toMap()
              allRows = rows
              refreshVisibleRows()
              statusArea.text = "$statusText\n$implSummary"
            }
          } catch (_: ProcessCanceledException) {
            ApplicationManager.getApplication().invokeLater {
              statusArea.text = "扫描已停止"
            }
          } catch (t: Throwable) {
            logger.warn("Operator scan failed", t)
            ApplicationManager.getApplication().invokeLater {
              statusArea.text = "扫描失败：${t.message ?: "未知错误"}"
            }
          } finally {
            runningScanIndicator.compareAndSet(indicator, null)
            ApplicationManager.getApplication().invokeLater {
              setScanningState(false)
            }
          }
        }
      })
    }

    // Scan/Stop actions are handled by ActionToolbar.

    fun copyScanDebug() {
      val text = lastScanDebug.ifBlank { "暂无扫描调试信息（请先执行一次“扫描算子”）。" }
      CopyPasteManager.getInstance().setContents(StringSelection(text))
      setInlineStatus("扫描调试信息已复制到剪贴板。", PackLogTone.OK)
    }

    fun copyPackDebug() {
      val text = lastPackDebug.ifBlank { "暂无打包调试信息（请先执行一次“开始打包”）。" }
      CopyPasteManager.getInstance().setContents(StringSelection(text))
      setInlineStatus("打包调试信息已复制到剪贴板。", PackLogTone.OK)
    }

    fun toggleWindowed() {
      val tw = ToolWindowManager.getInstance(project).getToolWindow(DATAYOO_TOOL_WINDOW_ID)
      val isWindowed = tw?.type == ToolWindowType.WINDOWED
      toggleDatayooToolWindowWindowed(project)
      statusArea.text = if (isWindowed) "已还原停靠" else "已切换为独立窗口（已尝试最大化）"
    }

    copyScanDebugButton.addActionListener { copyScanDebug() }
    // SearchTextField#addDocumentListener is not reliable across IDE builds; listen on the editor document.
    searchField.textEditor.document.addDocumentListener(object : javax.swing.event.DocumentListener {
      override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = refreshVisibleRows()
      override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = refreshVisibleRows()
      override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = refreshVisibleRows()
    })
    fun selectedPackRows(): List<OperatorRow> {
      val checkedRows = currentRows.filter { row -> packSelection[operatorRuntimeKey(row)] == true }
      if (checkedRows.isNotEmpty()) {
        return checkedRows
      }

      val viewRows = resultTable.selectedRows
      val selectedByRow = if (viewRows.isNotEmpty()) {
        viewRows.map { vr ->
          val modelRow = resultTable.convertRowIndexToModel(vr)
          currentRows.getOrNull(modelRow)
        }.filterNotNull().distinctBy { operatorRuntimeKey(it) }
      } else {
        emptyList()
      }
      if (selectedByRow.isNotEmpty()) {
        applyPackSelectionByKeys(selectedByRow.map { operatorRuntimeKey(it) }.toSet(), checked = true)
        setInlineStatus("已按当前选中行作为打包目标，并自动勾选到选择列。", PackLogTone.WARN)
        return selectedByRow
      }

      val lead = resultTable.selectionModel.leadSelectionIndex
      if (lead >= 0) {
        val modelRow = resultTable.convertRowIndexToModel(lead)
        val one = currentRows.getOrNull(modelRow)
        if (one != null) {
          applyPackSelectionByKeys(setOf(operatorRuntimeKey(one)), checked = true)
          setInlineStatus("已按当前焦点行作为打包目标，并自动勾选到选择列。", PackLogTone.WARN)
          return listOf(one)
        }
      }

      if (checkedRows.isEmpty()) {
        Messages.showWarningDialog(project, "请先勾选左侧复选框，或先选中要打包的行。", "打包")
        setInlineStatus("请先在左侧勾选需要打包的算子（选择列）。", PackLogTone.WARN)
      }
      return emptyList()
    }

    /** 工具栏批量上传：对「选择」列已勾选算子逐项校验 zip，聚合成上传页。 */
    fun prepareBatchMallUpload(uploadKind: OssUploader.PackKind) {
      activateRunConsole()
      val tozooBase =
        ApplicationManager.getApplication().getService(TozooUploadSettings::class.java).state.baseUrl.trim()
      if (tozooBase.isEmpty()) {
        appendPackLogLine("上传中止：Tozoo 根地址未配置，请到「配置」页填写。", PackLogTone.ERROR)
        return
      }
      val selectedRows = selectedPackRows()
      if (selectedRows.isEmpty()) return

      refreshZipTargetsForVisibleRows()
      val pending = mutableListOf<MallPendingOperator>()
      val skipped = mutableListOf<String>()
      val skippedKeys = linkedSetOf<String>()
      val kindHint = when (uploadKind) {
        OssUploader.PackKind.MARKETPLACE, OssUploader.PackKind.DESCRIPTOR -> "定义态"
        OssUploader.PackKind.OYEZ -> "实现态"
      }
      for (row in selectedRows) {
        val key = operatorRuntimeKey(row)
        val targets = zipTargetsByKey[key] ?: computeZipTargets(row).also { zipTargetsByKey[key] = it }
        val dZip = targets.descriptorZip
        val oZip = targets.oyezZip
        val rawSelected = when (uploadKind) {
          OssUploader.PackKind.MARKETPLACE, OssUploader.PackKind.DESCRIPTOR -> dZip
          OssUploader.PackKind.OYEZ -> oZip
        }
        val selectedZip = rawSelected?.takeIf { zipMatchesOperator(row, uploadKind, it) }
        when {
          rawSelected != null && selectedZip == null -> {
            skipped += "${row.displayName}：target 下 zip 与算子名不一致"
            skippedKeys += key
          }
          dZip == null && oZip == null -> {
            skipped += "${row.displayName}：未发现 zip，请先打包"
            skippedKeys += key
          }
          selectedZip == null -> {
            val expect = when (uploadKind) {
              OssUploader.PackKind.MARKETPLACE, OssUploader.PackKind.DESCRIPTOR ->
                "${marketplacePackBaseName(row)}-descriptor-<时间戳>.zip"
              OssUploader.PackKind.OYEZ ->
                "${marketplacePackBaseName(row)}-oyez-<时间戳>.zip"
            }
            skipped += "${row.displayName}：缺少${kindHint} zip（期望 target/$expect）"
            skippedKeys += key
          }
          else ->
            pending += MallPendingOperator(row.displayName, uploadKind, selectedZip!!, row)
        }
      }
      applyPackSelectionByKeys(skippedKeys, checked = true)

      if (pending.isEmpty()) {
        val reason = skipped.joinToString("\n").ifBlank { "请确认已勾选算子且对应 zip 已生成。" }
        appendPackLogLine("上传中止：没有可上传的包。\n$reason", PackLogTone.WARN)
        setInlineStatus("上传中止：无可用包。", PackLogTone.WARN)
        return
      }

      mallPendingRow = pending.first().row
      mallPendingConnectors = emptyList()
      mallPendingOperators = pending
      refreshMallCommitPanel()
      if (skipped.isNotEmpty()) {
        appendPackLogLine(
          "批量上传：就绪 ${pending.size} 个；跳过 ${skipped.size} 个 —\n${skipped.joinToString("\n")}",
          PackLogTone.WARN
        )
        setInlineStatus("批量上传：${pending.size} 个就绪，${skipped.size} 个已跳过（见打包调试）", PackLogTone.WARN)
      }
      appendPackLogLine(
        "已准备批量上传 ${pending.size} 个${kindHint}包。请填写提交说明后点击「提交」。",
        PackLogTone.DEFAULT
      )
      ApplicationManager.getApplication().invokeLater { showMallUploadPage() }
    }

    /**
     * @param useImplPom 为 true 时（实现态）：按 [resolveImplModulePomDir]；否则按 [resolveDescriptorModulePomDir]（扫描阶段已写入路径优先）。
     */
    fun groupByPomDir(selected: List<OperatorRow>, useImplPom: Boolean): LinkedHashMap<VirtualFile, MutableList<OperatorRow>> {
      val groups = linkedMapOf<VirtualFile, MutableList<OperatorRow>>()
      for (row in selected) {
        val pomDir = if (useImplPom) {
          resolveImplModulePomDir(project, row)
        } else {
          resolveDescriptorModulePomDir(project, row)
        } ?: continue
        groups.getOrPut(pomDir) { mutableListOf() }.add(row)
      }
      return groups
    }

    fun resolvePackFailKeys(params: MavenRunnerParameters, fallbackRows: List<OperatorRow>): Set<String> {
      val opt = params.cmdOptions.orEmpty()
      val pieces = mutableListOf<String>()
      val di = opt.substringAfter("-Ddescriptor.includes=", "").substringBefore(' ').trim()
      val ii = opt.substringAfter("-Dimpl.includes=", "").substringBefore(' ').trim()
      if (di.isNotBlank()) pieces += di.split(',')
      if (ii.isNotBlank()) pieces += ii.split(',')
      val classes = pieces.map { it.trim() }.filter { it.isNotBlank() }.toSet()
      val keys = mutableSetOf<String>()
      if (classes.isNotEmpty()) {
        for (row in allRows) {
          if (row.className in classes || row.implClasses.any { it in classes }) {
            keys += "${row.className}|${row.name}"
          }
        }
      }
      if (keys.isNotEmpty()) return keys

      val wdNorm = runCatching {
        java.io.File(params.workingDirPath).canonicalFile.absolutePath
      }.getOrNull().orEmpty()
      if (wdNorm.isNotBlank()) {
        for (row in allRows) {
          val descCached = row.descriptorModulePomPath?.trim()?.takeIf { it.isNotBlank() }?.let {
            runCatching { java.io.File(it).canonicalFile.absolutePath }.getOrNull()
          }
          if (descCached != null && descCached.equals(wdNorm, ignoreCase = true)) {
            keys += "${row.className}|${row.name}"
            continue
          }
          val descRoot = findDescriptorRootForOperator(project, row) ?: continue
          val descPom = findNearestPomDir(descRoot)?.path?.let {
            runCatching { java.io.File(it).canonicalFile.absolutePath }.getOrNull()
          }
          if (descPom != null && descPom.equals(wdNorm, ignoreCase = true)) {
            keys += "${row.className}|${row.name}"
            continue
          }
          val implPomPaths = collectImplModuleCanonicalPaths(project, row)
          if (implPomPaths.any { it.equals(wdNorm, ignoreCase = true) }) {
            keys += "${row.className}|${row.name}"
          }
        }
      }
      if (keys.isNotEmpty()) return keys
      return fallbackRows.map { "${it.className}|${it.name}" }.toSet()
    }

    fun packMavenCallbacks(fallbackRows: List<OperatorRow>): Pair<(() -> Unit)?, ((MavenRunnerParameters, Int) -> Unit)?> {
      val onOk: () -> Unit = {
        packFailHintByOpKey.clear()
        ApplicationManager.getApplication().invokeLater { resultTable.repaint() }
      }
      val onFail: (MavenRunnerParameters, Int) -> Unit = { params, code ->
        val hint = "打包失败 exit=$code（见打包调试）"
        val failedKeys = resolvePackFailKeys(params, fallbackRows)
        for (k in failedKeys) {
          packFailHintByOpKey[k] = hint
        }
        applyPackSelectionByKeys(failedKeys, checked = true)
        val goalsText = params.goals.joinToString(" ")
        val stageHint = when {
          goalsText.contains(":descriptorPack") ->
            "定义态打包失败，流程已停止；本轮实现态未执行。"
          goalsText.contains(":oyezPack") ->
            "实现态打包失败，流程已停止。"
          else -> "打包失败，流程已停止。"
        }
        ApplicationManager.getApplication().invokeLater {
          resultTable.repaint()
          setInlineStatus("打包失败（列表中标红且已自动勾选的算子）。$stageHint", PackLogTone.ERROR)
          Messages.showErrorDialog(project, "打包失败并已停止。\n$stageHint\n请查看「打包调试」定位问题。", "打包失败")
        }
      }
      return Pair(onOk, onFail)
    }

    /** 并行打包专用的错误回调：不弹窗、不提示"流程已停止"，仅标红算子并记录日志。 */
    fun packMavenCallbacksParallel(fallbackRows: List<OperatorRow>): Pair<(() -> Unit)?, ((MavenRunnerParameters, Int) -> Unit)?> {
      val onOk: () -> Unit = {
        packFailHintByOpKey.clear()
        ApplicationManager.getApplication().invokeLater { resultTable.repaint() }
      }
      val onFail: (MavenRunnerParameters, Int) -> Unit = { params, code ->
        val hint = "打包失败 exit=$code（见打包调试）"
        val failedKeys = resolvePackFailKeys(params, fallbackRows)
        for (k in failedKeys) {
          packFailHintByOpKey[k] = hint
        }
        applyPackSelectionByKeys(failedKeys, checked = true)
        val pomPath = params.workingDirPath.substringAfterLast('/').substringAfterLast('\\')
        ApplicationManager.getApplication().invokeLater {
          resultTable.repaint()
          setInlineStatus("$pomPath 打包失败（列表中标红），其余 module 继续。", PackLogTone.ERROR)
        }
      }
      return Pair(onOk, onFail)
    }

    fun runMavenPack(
      groups: Map<VirtualFile, List<OperatorRow>>,
      fullGoal: String,
      cmdOptionKey: String,
      useImplIncludes: Boolean
    ) {
      if (groups.isEmpty()) {
        setInlineStatus("未找到可执行的 pom.xml 目录（请确认算子所属 module 有 pom.xml）。", PackLogTone.ERROR)
        return
      }
      val plan = linkedMapOf<VirtualFile, MutableList<MavenRunnerParameters>>()
      val skipped = mutableListOf<String>()
      groups.forEach { (pomDir, rows) ->
        val includes = if (useImplIncludes) {
          rows.asSequence()
            .flatMap { r -> implIncludesCsvForRow(project, r).split(',').map { it.trim() }.asSequence() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")
        } else {
          rows.joinToString(",") { it.className }
        }
        if (includes.isBlank()) {
          skipped += pomDir.name
          return@forEach
        }
        addPackageIfMissing(plan, pomDir)
        val existing = buildString {
          append("-D$cmdOptionKey=$includes")
          // Descriptor merged pack for a single operator should still produce
          // operator-descriptor-version.zip at pack time.
          if (cmdOptionKey == "descriptor.includes" && rows.size == 1) {
            append(" -Ddescriptor.outputName=${descriptorPackOutputBaseName(rows.first(), pomDir)}")
          }
          // 实现态合包单算子时对齐定义态：带上时间戳后缀，避免长期覆盖 target 下同名 zip。
          if (cmdOptionKey == "impl.includes" && rows.size == 1) {
            append(" -Dimpl.outputName=${implPackOutputBaseName(rows.first(), pomDir)}")
          }
        }
        plan.getOrPut(pomDir) { mutableListOf() } += createMavenParams(
          pomDir = pomDir,
          goals = listOf(fullGoal),
          cmdOptions = buildMavenCmdOptions(existing = existing, extra = emptyList())
        )
      }
      val commands = plan.values.flatten()
      if (commands.isEmpty()) {
        val suffix = if (skipped.isNotEmpty()) "（includes 为空：${skipped.joinToString("、")}）" else ""
        setInlineStatus("没有可执行的打包任务$suffix", PackLogTone.ERROR)
        return
      }
      val preview = buildString {
        appendLine("== 打包计划（全局串行）==")
        commands.forEachIndexed { i, p -> appendLine(formatMavenStep(i + 1, commands.size, p)) }
      }.trimEnd()
      statusArea.text = preview
      lastPackDebug = preview
      packFailHintByOpKey.clear()
      resultTable.repaint()
      activateRunConsole()
      val fb = groups.values.flatten().distinctBy { "${it.className}|${it.name}" }
      val pc = packMavenCallbacks(fb)
      onPackStarted()
      runMavenSequential(
        project,
        statusArea,
        commands,
        "打包（package -> $fullGoal）",
        { line, tone -> appendPackLogLine(line, tone) },
        onAllSucceeded = pc.first,
        onStepFailed = pc.second,
        bindMavenStepHandler = { bindMavenStepToRunWindow(it) },
        onPackSessionFinished = {
          clearRunWindowPackProcessBinding()
          onPackEnded()
        }
      )
    }

    fun runMavenPackPerOperator(
      groups: Map<VirtualFile, List<OperatorRow>>,
      fullGoal: String,
      includesKey: String,
      outputNameKey: String,
      useImplIncludes: Boolean
    ) {
      if (groups.isEmpty()) {
        setInlineStatus("未找到可执行的 pom.xml 目录（请确认算子所属 module 有 pom.xml）。", PackLogTone.ERROR)
        return
      }
      val plan = linkedMapOf<VirtualFile, MutableList<MavenRunnerParameters>>()
      for ((pomDir, rows) in groups) {
        addPackageIfMissing(plan, pomDir)
        for (row in rows) {
          val include = if (useImplIncludes) implIncludesCsvForRow(project, row) else row.className.trim()
          if (include.isBlank() || include == "-") continue
          val outName = when (outputNameKey) {
            "descriptor.outputName" -> descriptorPackOutputBaseName(row, pomDir)
            "impl.outputName" -> implPackOutputBaseName(row, pomDir)
            else -> marketplacePackBaseName(row)
          }
          plan.getOrPut(pomDir) { mutableListOf() } += createMavenParams(
            pomDir = pomDir,
            goals = listOf(fullGoal),
            cmdOptions = buildMavenCmdOptions(
              existing = "-D$includesKey=$include -D$outputNameKey=$outName",
              extra = emptyList()
            )
          )
        }
      }
      val commands = plan.values.flatten()
      if (commands.isEmpty()) {
        setInlineStatus("没有可执行的逐算子打包任务（includes 为空）", PackLogTone.ERROR)
        return
      }
      val preview = buildString {
        appendLine("== 逐算子打包计划（全局串行）==")
        commands.forEachIndexed { i, p -> appendLine(formatMavenStep(i + 1, commands.size, p)) }
      }.trimEnd()
      statusArea.text = preview
      lastPackDebug = preview
      packFailHintByOpKey.clear()
      resultTable.repaint()
      activateRunConsole()
      val fb = groups.values.flatten().distinctBy { "${it.className}|${it.name}" }
      val pc = packMavenCallbacks(fb)
      onPackStarted()
      runMavenSequential(
        project,
        statusArea,
        commands,
        "逐算子打包",
        { line, tone -> appendPackLogLine(line, tone) },
        onAllSucceeded = pc.first,
        onStepFailed = pc.second,
        bindMavenStepHandler = { bindMavenStepToRunWindow(it) },
        onPackSessionFinished = {
          clearRunWindowPackProcessBinding()
          onPackEnded()
        }
      )
    }

    fun runWholeModulePack(groups: Map<VirtualFile, List<OperatorRow>>, fullGoal: String) {
      if (groups.isEmpty()) {
        setInlineStatus("未找到可执行的 pom.xml 目录（请确认算子所属 module 有 pom.xml）。", PackLogTone.ERROR)
        return
      }
      val plan = linkedMapOf<VirtualFile, MutableList<MavenRunnerParameters>>()
      groups.keys.forEach { pomDir ->
        addPackageIfMissing(plan, pomDir)
        plan.getOrPut(pomDir) { mutableListOf() } += createMavenParams(
          pomDir = pomDir,
          goals = listOf(fullGoal),
          cmdOptions = null
        )
      }
      val commands = plan.values.flatten()
      if (commands.isEmpty()) {
        setInlineStatus("没有可执行的按Module打包任务", PackLogTone.ERROR)
        return
      }
      val preview = buildString {
        appendLine("== 按Module打包计划（全局串行）==")
        commands.forEachIndexed { i, p -> appendLine(formatMavenStep(i + 1, commands.size, p)) }
      }.trimEnd()
      statusArea.text = preview
      lastPackDebug = preview
      packFailHintByOpKey.clear()
      resultTable.repaint()
      activateRunConsole()
      val fb = groups.values.flatten().distinctBy { "${it.className}|${it.name}" }
      val pc = packMavenCallbacks(fb)
      onPackStarted()
      runMavenSequential(
        project,
        statusArea,
        commands,
        "按Module打包（package -> $fullGoal）",
        { line, tone -> appendPackLogLine(line, tone) },
        onAllSucceeded = pc.first,
        onStepFailed = pc.second,
        bindMavenStepHandler = { bindMavenStepToRunWindow(it) },
        onPackSessionFinished = {
          clearRunWindowPackProcessBinding()
          onPackEnded()
        }
      )
    }

    fun packSelected(mode: String, fullGoal: String, includesKey: String, outputNameKey: String, useImplPom: Boolean) {
      val selected = when (mode) {
        "按Module打包" -> currentRows
        else -> selectedPackRows()
      }
      if (selected.isEmpty()) return
      val groups = groupByPomDir(selected, useImplPom)
      when (mode) {
        "按Module打包" -> runWholeModulePack(groups, fullGoal)
        "所选算子合包" -> runMavenPack(groups, fullGoal, includesKey, useImplIncludes = useImplPom)
        else -> runMavenPackPerOperator(groups, fullGoal, includesKey, outputNameKey, useImplIncludes = useImplPom)
      }
    }

    // ---- ActionToolbar (scan/stop/pack) ----
    val deployCheckbox = JBCheckBox("打包后执行 mvn deploy（推送到远程 Maven 仓库）", false).apply {
      toolTipText = "勾选后，定义态打包完成会额外执行 mvn deploy；默认不执行。"
    }

    fun stopScanIfRunning() {
      val indicator = runningScanIndicator.get()
      if (indicator == null || indicator.isCanceled) {
        statusArea.text = "当前没有进行中的扫描任务"
      } else {
        statusArea.text = "正在停止扫描..."
        indicator.cancel()
      }
    }

    fun runPackWithMode(mode: String, selected: List<OperatorRow>) {
      if (selected.isEmpty()) {
        appendPackLogLine("PACK 跳过：mode=$mode selected=0（请先勾选算子后再打包）", PackLogTone.WARN)
        ApplicationManager.getApplication().invokeLater {
          statusArea.text = "未开始打包：请先勾选算子（选择列）"
        }
        return
      }
      val doDescriptor = true
      val doOyez = true
      activateRunConsole()
      appendPackLogLine("PACK 开始：mode=$mode selected=${selected.size}", PackLogTone.PROGRESS)
      packFailHintByOpKey.clear()
      resultTable.repaint()

      // Build descriptor/oyez in two explicit phases:
      // descriptor phase must fully finish before oyez phase starts.
      val descriptorPlan = linkedMapOf<VirtualFile, MutableList<MavenRunnerParameters>>()
      val oyezPlan = linkedMapOf<VirtualFile, MutableList<MavenRunnerParameters>>()
      val descriptorGroups = groupByPomDir(selected, useImplPom = false)
      val implGroups = groupByPomDir(selected, useImplPom = true)
      val seedPomDirs = buildSet {
        descriptorGroups.keys.forEach { add(it) }
        implGroups.keys.forEach { add(it) }
      }

      fun appendWholeModule(plan: LinkedHashMap<VirtualFile, MutableList<MavenRunnerParameters>>, pomDir: VirtualFile, goal: String) {
        addPackageIfMissing(plan, pomDir)
        plan.getOrPut(pomDir) { mutableListOf() } += createMavenParams(
          pomDir = pomDir,
          goals = listOf(goal),
          cmdOptions = null
        )
      }

      fun appendMergedPack(
        plan: LinkedHashMap<VirtualFile, MutableList<MavenRunnerParameters>>,
        groups: Map<VirtualFile, List<OperatorRow>>,
        goal: String,
        cmdOptionKey: String,
        useImplIncludes: Boolean
      ) {
        for ((pomDir, rows) in groups) {
          val includes = if (useImplIncludes) {
            rows.asSequence().flatMap { it.implClasses.asSequence() }.distinct().joinToString(",")
          } else {
            rows.joinToString(",") { it.className }
          }
          if (includes.isBlank()) continue
          addPackageIfMissing(plan, pomDir)
          val existing = buildString {
            append("-D$cmdOptionKey=$includes")
            if (cmdOptionKey == "descriptor.includes" && rows.size == 1) {
              append(" -Ddescriptor.outputName=${descriptorPackOutputBaseName(rows.first(), pomDir)}")
            }
            if (cmdOptionKey == "impl.includes" && rows.size == 1) {
              append(" -Dimpl.outputName=${implPackOutputBaseName(rows.first(), pomDir)}")
            }
          }
          plan.getOrPut(pomDir) { mutableListOf() } += createMavenParams(
            pomDir = pomDir,
            goals = listOf(goal),
            cmdOptions = buildMavenCmdOptions(existing = existing, extra = emptyList())
          )
        }
      }

      fun appendPerOperatorPack(
        plan: LinkedHashMap<VirtualFile, MutableList<MavenRunnerParameters>>,
        groups: Map<VirtualFile, List<OperatorRow>>,
        goal: String,
        includesKey: String,
        outputNameKey: String,
        useImplIncludes: Boolean
      ) {
        for ((pomDir, rows) in groups) {
          addPackageIfMissing(plan, pomDir)
          for (row in rows) {
            val include = if (useImplIncludes) implIncludesCsvForRow(project, row) else row.className.trim()
            if (include.isBlank() || include == "-") continue
            val outName = when (outputNameKey) {
              "descriptor.outputName" -> descriptorPackOutputBaseName(row, pomDir)
              "impl.outputName" -> implPackOutputBaseName(row, pomDir)
              else -> marketplacePackBaseName(row)
            }
            plan.getOrPut(pomDir) { mutableListOf() } += createMavenParams(
              pomDir = pomDir,
              goals = listOf(goal),
              cmdOptions = buildMavenCmdOptions(
                existing = "-D$includesKey=$include -D$outputNameKey=$outName",
                extra = emptyList()
              )
            )
          }
        }
      }

      // Module-level output name helper (reads artifactId from pom.xml)
      fun moduleArtifactId(pomDir: VirtualFile): String {
        val pomFile = java.io.File(pomDir.path, "pom.xml")
        if (!pomFile.exists()) return pomDir.name
        val text = pomFile.readText(Charsets.UTF_8)
        val parentRemoved = text.replace(Regex("<parent>[\\s\\S]*?</parent>"), "")
        return Regex("<artifactId>\\s*([^<\\s]+)\\s*</artifactId>")
          .find(parentRemoved)?.groupValues?.getOrNull(1)
          ?.trim() ?: pomDir.name
      }
      val modulePackTs = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        .format(java.time.LocalDateTime.now())
      val moduleOutputNames = mutableMapOf<String, Pair<String,String>>() // pomDirPath -> (descBase, oyezBase)

      if (doDescriptor) {
        when (mode) {
          "按Module打包" -> descriptorGroups.keys.forEach { pomDir ->
            val aid = moduleArtifactId(pomDir)
            val descBase = "$aid-descriptor-$modulePackTs"
            moduleOutputNames.getOrPut(pomDir.path) { Pair(descBase, "") }.let { moduleOutputNames[pomDir.path] = Pair(descBase, it.second) }
            addPackageIfMissing(descriptorPlan, pomDir)
            descriptorPlan.getOrPut(pomDir) { mutableListOf() } += createMavenParams(
              pomDir = pomDir,
              goals = listOf(DESCRIPTOR_PACK_GOAL),
              cmdOptions = buildMavenCmdOptions(existing = "-Ddescriptor.outputName=$descBase", extra = emptyList())
            )
          }
          "所选算子合包" -> appendMergedPack(descriptorPlan, descriptorGroups, DESCRIPTOR_PACK_GOAL, "descriptor.includes", useImplIncludes = false)
          else -> appendPerOperatorPack(
            descriptorPlan,
            descriptorGroups,
            DESCRIPTOR_PACK_GOAL,
            includesKey = "descriptor.includes",
            outputNameKey = "descriptor.outputName",
            useImplIncludes = false
          )
        }
      }

      if (doOyez) {
        when (mode) {
          "按Module打包" -> implGroups.keys.forEach { pomDir ->
            val aid = moduleArtifactId(pomDir)
            val oyezBase = "$aid-oyez-$modulePackTs"
            val prev = moduleOutputNames.getOrPut(pomDir.path) { Pair("", oyezBase) }
            moduleOutputNames[pomDir.path] = Pair(prev.first, oyezBase)
            addPackageIfMissing(oyezPlan, pomDir)
            oyezPlan.getOrPut(pomDir) { mutableListOf() } += createMavenParams(
              pomDir = pomDir,
              goals = listOf(OYEZ_PACK_GOAL),
              cmdOptions = buildMavenCmdOptions(existing = "-Dimpl.outputName=$oyezBase", extra = emptyList())
            )
          }
          "所选算子合包" -> appendMergedPack(oyezPlan, implGroups, OYEZ_PACK_GOAL, "impl.includes", useImplIncludes = true)
          else -> appendPerOperatorPack(
            oyezPlan,
            implGroups,
            OYEZ_PACK_GOAL,
            includesKey = "impl.includes",
            outputNameKey = "impl.outputName",
            useImplIncludes = true
          )
        }
      }

      val ancestorPomDirs = collectStrictAncestorMavenModulePomDirs(project, seedPomDirs)
      val preflightCommands = buildPreflightCleanInstallForAncestorPomDirs(ancestorPomDirs)
      if (preflightCommands.isNotEmpty()) {
        appendPackLogLine(
          "PACK 多模块预置：对 ${ancestorPomDirs.size} 个 Maven 祖先 module 执行 clean install（-DskipTests -N，非递归），只安装当前 POM、不顺便编译整棵 reactor 子树",
          PackLogTone.PROGRESS
        )
      }

      val descriptorDeployCommands = if (doDescriptor && descriptorPlan.isNotEmpty() && deployCheckbox.isSelected) {
        descriptorPlan.keys.map { pomDir ->
          createMavenParams(
            pomDir = pomDir,
            goals = listOf(MAVEN_DEPLOY_GOAL),
            cmdOptions = buildMavenCmdOptions(existing = null, extra = listOf(MAVEN_SKIP_TESTS_OPTION))
          )
        }
      } else {
        emptyList()
      }

      if (mode == "按Module打包") {
        // ---- Parallel by pomDir ----
        // Build per-pomDir chains: descriptor + deploy + oyez for each pomDir
        val allPomDirs = linkedSetOf<VirtualFile>()
        allPomDirs.addAll(descriptorPlan.keys)
        allPomDirs.addAll(oyezPlan.keys)
        val deployByPath = descriptorDeployCommands.associateBy { it.workingDirPath }

        val pomDirChains = allPomDirs.mapNotNull { pomDir ->
          val path = pomDir.path
          val chain = mutableListOf<MavenRunnerParameters>()
          descriptorPlan[pomDir]?.let { chain.addAll(it) }
          deployByPath[path]?.let { chain.add(it) }
          oyezPlan[pomDir]?.let { chain.addAll(it) }
          if (chain.isEmpty()) null
          else Pair(pomDir.name, chain)
        }

        val totalSteps = preflightCommands.size + pomDirChains.sumOf { it.second.size }
        val preview = buildString {
          appendLine("== 按Module打包计划（预置串行 -> ${pomDirChains.size} 个 module 并行，最多 3 并发）==")
          appendLine("preflight steps=${preflightCommands.size}, modules=${pomDirChains.size}, total steps=$totalSteps")
          preflightCommands.forEachIndexed { i, p -> appendLine(formatMavenStep(i + 1, totalSteps, p)) }
          pomDirChains.forEach { (name, chain) ->
            appendLine("  [$name] ${chain.size} 步：" + chain.joinToString(" -> ") { it.goals.last() })
          }
        }.trimEnd()
        statusArea.text = preview
        setPackLogContent(preview)

        fun appendPackDebug(line: String, tone: PackLogTone = PackLogTone.DEFAULT) {
          appendPackLogLine(line, tone)
        }

        val pc = packMavenCallbacksParallel(selected)
        appendPackDebug("PACK 按Module并行：preflight 串行（${preflightCommands.size} 步）-> ${pomDirChains.size} 个 module 并行", PackLogTone.DEFAULT)
        onPackStarted()
        runMavenParallelByPomDir(
          project = project,
          statusArea = statusArea,
          preflightCommands = preflightCommands,
          pomDirChains = pomDirChains,
          totalSteps = totalSteps,
          title = "按Module打包（预置串行 + module并行）",
          appendDebug = { line, tone -> appendPackDebug(line, tone) },
          onPackZipProduced = { kind, file -> appendPackDebug("ZIP ${kind.name} ${file.path}", PackLogTone.DEFAULT) },
          onAllSucceeded = {
            pc.first?.invoke()
            // Collect module-level zips to project target/
            val projectTarget = java.io.File(project.basePath, "target")
            if (!projectTarget.exists()) projectTarget.mkdirs()
            var collected = 0
            for ((pomDirPath, names) in moduleOutputNames) {
              val pomTarget = java.io.File(pomDirPath, "target")
              val (descName, oyezName) = names
              listOf(descName, oyezName).filter { it.isNotBlank() }.forEach { base ->
                val src = java.io.File(pomTarget, "$base.zip")
                if (src.exists() && src.isFile) {
                  src.copyTo(java.io.File(projectTarget, src.name), overwrite = true)
                  collected++
                }
              }
            }
            if (collected > 0) {
              appendPackDebug("收集完成：$collected 个 module zip → ${projectTarget.path}", PackLogTone.OK)
              ApplicationManager.getApplication().invokeLater {
                setInlineStatus("打包 & 收集完成：$collected 个 zip → ${projectTarget.path}", PackLogTone.OK)
              }
            }
          },
          onStepFailed = pc.second,
          onPackSessionFinished = {
            clearRunWindowPackProcessBinding()
            onPackEnded()
          }
        )
      } else {
        // ---- Sequential (original path for 逐算子单包 / 所选算子合包) ----
        val descriptorCommands = descriptorPlan.values.flatten()
        val oyezCommands = oyezPlan.values.flatten()
        val commands = preflightCommands + descriptorCommands + descriptorDeployCommands + oyezCommands
        if (commands.isEmpty()) {
          setInlineStatus("没有可执行的打包任务（includes 为空或未找到 pom.xml）。", PackLogTone.ERROR)
          return
        }

        val deployPhase = if (descriptorDeployCommands.isNotEmpty()) " -> deploy" else ""
        val preview = buildString {
          appendLine("== 打包计划（祖先 module 预置 -> 定义态全量$deployPhase -> 实现态全量，严格串行）==")
          appendLine(
            "preflight steps=${preflightCommands.size}, descriptor steps=${descriptorCommands.size}, descriptor deploy=${descriptorDeployCommands.size}, oyez steps=${oyezCommands.size}"
          )
          commands.forEachIndexed { i, p -> appendLine(formatMavenStep(i + 1, commands.size, p)) }
        }.trimEnd()
        statusArea.text = preview
        setPackLogContent(preview)

        fun appendPackDebug(line: String, tone: PackLogTone = PackLogTone.DEFAULT) {
          appendPackLogLine(line, tone)
        }

        val pc = packMavenCallbacks(selected)
        appendPackDebug(
          "PACK 阶段顺序：preflight（${preflightCommands.size} 步）-> descriptor（${descriptorCommands.size} 步）$deployPhase -> oyez（${oyezCommands.size} 步）"
        )
        onPackStarted()
        runMavenSequential(
          project,
          statusArea,
          commands,
          "打包（preflight -> descriptor$deployPhase -> oyez）",
          { line, tone -> appendPackDebug(line, tone) },
          onPackZipProduced = { kind, file ->
            appendPackDebug("ZIP ${kind.name} ${file.path}")
          },
          onAllSucceeded = pc.first,
          onStepFailed = pc.second,
          bindMavenStepHandler = { bindMavenStepToRunWindow(it) },
          onPackSessionFinished = {
            clearRunWindowPackProcessBinding()
            onPackEnded()
          }
        )
      }
    }

    /** 工具栏「打包」：仅 `selectedPackRows()` 勾选的子集；与全勾同一套 `runPackWithMode` 流程，无按条数分支。 */
    fun startPackByModule() {
      if (currentRows.isEmpty()) {
        setInlineStatus("没有扫描到的算子，请先扫描。", PackLogTone.WARN)
        return
      }
      ApplicationManager.getApplication().executeOnPooledThread {
        runPackWithMode("按Module打包", currentRows)
      }
    }

    fun startPackPerOperator() {
      val selected = selectedPackRows()
      runPackWithMode("逐算子单包", selected)
    }

    fun startMergedPack() {
      val selected = selectedPackRows()
      if (selected.isEmpty()) {
        setInlineStatus("未勾选算子，请先勾选后再合包。", PackLogTone.WARN)
        return
      }
      val targetDir = java.io.File(project.basePath, "target")
      if (!targetDir.exists()) targetDir.mkdirs()

      fun rmTree(dir: java.io.File) { java.nio.file.Files.walk(dir.toPath()).sorted(Comparator.reverseOrder()).forEach { java.nio.file.Files.deleteIfExists(it) } }
      targetDir.listFiles()?.forEach { rmTree(it) }

      val ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.systemDefault()).format(Instant.now())

      // 把 srcDir 下所有文件拷到 dstDir，外层目录名丢弃，只保留内部相对路径
      fun copyDirContents(srcDir: java.io.File, dstDir: java.io.File): Int {
        var count = 0
        val prefix = srcDir.absolutePath.let { p -> if (p.endsWith(java.io.File.separator)) p else p + java.io.File.separator }
        srcDir.walkTopDown().forEach { f ->
          if (f.isFile) {
            val rel = f.absolutePath.removePrefix(prefix).replace('\\', '/')
            val target = java.io.File(dstDir, rel)
            target.parentFile.mkdirs()
            f.copyTo(target, overwrite = true)
            count++
          }
        }
        return count
      }

      fun zipDir(dir: java.io.File, outputZip: java.io.File): Int {
        var count = 0
        val prefix = dir.absolutePath.let { p -> if (p.endsWith(java.io.File.separator)) p else p + java.io.File.separator }
        val root = outputZip.name.removeSuffix(".zip") + "/"
        ZipOutputStream(outputZip.outputStream().buffered()).use { zos ->
          dir.walkTopDown().forEach { f ->
            if (f.isFile) {
              val rel = root + f.absolutePath.removePrefix(prefix).replace('\\', '/')
              zos.putNextEntry(ZipEntry(rel))
              f.inputStream().use { it.copyTo(zos) }
              zos.closeEntry()
              count++
            }
          }
        }
        return count
      }

      fun buildMergedZip(sources: List<java.io.File>, outputZip: java.io.File, kind: String): Int {
        val tmpDir = java.io.File(targetDir, ".tmp-$kind-${System.currentTimeMillis()}")
        tmpDir.mkdirs()
        try {
          var fileCount = 0
          for (src in sources) {
            if (!src.exists() || !src.isFile) {
              appendPackLogLine("合包($kind): 跳过缺失 ${src.name}", PackLogTone.WARN)
              continue
            }
            val srcDir = java.io.File(src.parentFile, src.name.removeSuffix(".zip"))
            if (srcDir.isDirectory) {
              fileCount += copyDirContents(srcDir, tmpDir)
            } else {
              ZipInputStream(src.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                  val target = java.io.File(tmpDir, entry.name)
                  if (entry.isDirectory) {
                    target.mkdirs()
                  } else {
                    target.parentFile.mkdirs()
                    target.outputStream().use { out -> zis.copyTo(out) }
                    fileCount++
                  }
                  entry = zis.nextEntry
                }
              }
            }
          }
          if (fileCount == 0) return 0
          return zipDir(tmpDir, outputZip)
        } finally {
          rmTree(tmpDir)
        }
      }

      // 区分单算子 zip（解包合并）与 module 级 zip（直接复制）
      val descPerOp = mutableListOf<java.io.File>()
      val descModule = mutableListOf<java.io.File>()
      for (row in selected) {
        val zip = computeZipTargets(row).descriptorZip ?: continue
        if (!zip.exists() || !zip.isFile) continue
        val baseName = marketplacePackBaseName(row)
        if (Regex("^${Regex.escape(baseName)}-descriptor-.+\\.zip$", RegexOption.IGNORE_CASE).matches(zip.name))
          descPerOp += zip else descModule += zip
      }
      val oyezPerOp = mutableListOf<java.io.File>()
      val oyezModule = mutableListOf<java.io.File>()
      for (row in selected) {
        val zip = computeZipTargets(row).oyezZip ?: continue
        if (!zip.exists() || !zip.isFile) continue
        val baseName = marketplacePackBaseName(row)
        if (Regex("^${Regex.escape(baseName)}-oyez-.+\\.zip$", RegexOption.IGNORE_CASE).matches(zip.name))
          oyezPerOp += zip else oyezModule += zip
      }

      val totalSources = descPerOp.size + descModule.size + oyezPerOp.size + oyezModule.size
      if (totalSources == 0) {
        appendPackLogLine("合包中止: 所选算子尚未打包（未找到 descriptor/oyez zip），请先点击「打包」打包。", PackLogTone.WARN)
        setInlineStatus("合包中止：未找到打包产物，请先「打包」。", PackLogTone.WARN)
        return
      }

      appendPackLogLine("合包开始: 选中${selected.size}个算子, 单算子descriptor${descPerOp.size}个, module descriptor${descModule.size}个, 单算子oyez${oyezPerOp.size}个, module oyez${oyezModule.size}个", PackLogTone.PROGRESS)

      // 单算子 zip → 解包合并
      var descCount = 0
      if (descPerOp.isNotEmpty()) {
        descCount = buildMergedZip(descPerOp, java.io.File(targetDir, "selected-descriptor-$ts.zip"), "descriptor")
        if (descCount > 0) appendPackLogLine("合包完成(定义态) selected-descriptor-$ts.zip: ${descCount} 文件", PackLogTone.OK)
        else appendPackLogLine("合包未生成(定义态): 无可用条目", PackLogTone.ERROR)
      }
      var oyezCount = 0
      if (oyezPerOp.isNotEmpty()) {
        oyezCount = buildMergedZip(oyezPerOp, java.io.File(targetDir, "selected-oyez-$ts.zip"), "oyez")
        if (oyezCount > 0) appendPackLogLine("合包完成(实现态) selected-oyez-$ts.zip: ${oyezCount} 文件", PackLogTone.OK)
        else appendPackLogLine("合包未生成(实现态): 无可用条目", PackLogTone.ERROR)
      }

      // module 级 zip → 直接复制到 target/
      for (zip in (descModule + oyezModule)) {
        val dest = java.io.File(targetDir, zip.name)
        zip.copyTo(dest, overwrite = true)
        appendPackLogLine("合包(module): 复制 ${zip.name} → ${dest.path}", PackLogTone.OK)
      }

      val totalMerged = descCount + oyezCount + descModule.size + oyezModule.size
      if (totalMerged == 0) {
        appendPackLogLine("合包总结: 未生成任何输出", PackLogTone.WARN)
        setInlineStatus("合包未生成任何输出 — 所选算子可能尚未打包。", PackLogTone.WARN)
      } else {
        val parts = mutableListOf<String>()
        if (descCount > 0) parts += "定义态(合并${descCount}文件)"
        if (oyezCount > 0) parts += "实现态(合并${oyezCount}文件)"
        if (descModule.size + oyezModule.size > 0) parts += "module复制${descModule.size + oyezModule.size}个"
        appendPackLogLine("合包总结: ${parts.joinToString("，")} → ${targetDir.path}", PackLogTone.OK)
        setInlineStatus("合包完成 → ${targetDir.path}", PackLogTone.OK)
      }
    }

    val scanAction = object : DumbAwareAction("扫描算子", "扫描当前范围内算子", AllIcons.Actions.Refresh) {
      override fun actionPerformed(e: AnActionEvent) = doScan(showDialogOnEmpty = true)
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !scanning
      }
    }
    val stopAction = object : DumbAwareAction("停止扫描", "停止当前扫描任务", AllIcons.Actions.Suspend) {
      override fun actionPerformed(e: AnActionEvent) = stopScanIfRunning()
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = scanning
      }
    }
    val packPerOperatorAction = object : DumbAwareAction(
      "打包",
      "对当前勾选行组成的列表执行打包：每行 descriptor+oyez，全局串行；勾 1 行与勾多行同一逻辑，仅列表长度不同（非「合包」）",
      AllIcons.Actions.Execute
    ) {
      override fun actionPerformed(e: AnActionEvent) = startPackPerOperator()
    }
    val packMergedAction = object : DumbAwareAction(
      "合包",
      "把已勾选算子打好的 descriptor/oyez zip 按目录结构合并，输出到工程根目录 target/",
      AllIcons.Actions.GroupByPackage
    ) {
      override fun actionPerformed(e: AnActionEvent) = startMergedPack()
    }

    val scanGroup = DefaultActionGroup().apply {
      add(scanAction)
      add(stopAction)
    }
    scanToolbar = ActionManager.getInstance().createActionToolbar("DatayooPicker.ScanToolbar", scanGroup, true).apply {
      targetComponent = panel
    }
    // Scan buttons live next to the search box.
    scanToolbarHolder.add(scanToolbar!!.component.apply { border = JBUI.Borders.empty(0) }, BorderLayout.CENTER)

    val packGroup = DefaultActionGroup().apply {
      add(packPerOperatorAction)
      add(packMergedAction)
    }
    packToolbar = ActionManager.getInstance().createActionToolbar("DatayooPicker.PackToolbar", packGroup, true).apply {
      targetComponent = panel
    }

    val batchUploadMarketBtn = JButton("批量·定义态").apply {
      toolTipText = "对「选择」列已勾选算子批量准备上传定义态 zip（缺失 zip 的可在日志中查看原因）"
      addActionListener { prepareBatchMallUpload(OssUploader.PackKind.MARKETPLACE) }
    }
    val batchUploadOyezBtn = JButton("批量·实现态").apply {
      toolTipText = "对已勾选算子批量准备上传实现态 zip"
      addActionListener { prepareBatchMallUpload(OssUploader.PackKind.OYEZ) }
    }
    val packByModuleBtn = JButton("按Module").apply {
      toolTipText = "按 Module 整体打包：每个 module 执行 clean → package → descriptorPack → deploy(如开启) → oyezPack，不使用 includes 过滤"
      addActionListener { startPackByModule() }
    }
    val packPerOperatorBtn = JButton("打包").apply {
      toolTipText = "对勾选行列表打包（每行 descriptor+oyez）；勾一行与勾多行同一套逻辑，仅列表子集不同"
      addActionListener { startPackPerOperator() }
    }
    val packMergedBtn = JButton("合包").apply {
      toolTipText = "合并已勾选算子的 descriptor/oyez zip 包（按目录结构），输出到工程 target/"
      addActionListener { startMergedPack() }
    }

    val toolbarDeleteCommodityBtn = JButton("删除商品…", AllIcons.General.Remove).apply {
      toolTipText =
        "DELETE …/manage/commodity/deleteByName：按算子名称批量删除商品。与「批量删除所选版本」不同。"
      addActionListener {
        val rows = selectedRowsFromTableForDelete()
        deleteCommoditiesByName(rows, "算子列表工具栏")
      }
    }

    /** 第二行：可换行流式布局，窄宽度时自动折行，避免横向拉满才能点全按钮。 */
    val listToolbarEastPanel = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(0, 0, 4, 0)
    }
    listToolbarEastPanel.add(operatorListCountLabel, BorderLayout.NORTH)
    val actionsGrid = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4))).apply {
      isOpaque = false
    }
    fun label(s: String) = JBLabel(s).apply { foreground = JBColor.GRAY; font = font.deriveFont(font.size2D - 1f) }
    fun sep() = JSeparator(SwingConstants.VERTICAL).apply { preferredSize = java.awt.Dimension(1, JBUI.scale(18)) }
    // 批量删除
    actionsGrid.add(label("删除"))
    actionsGrid.add(toolbarDeleteCommodityBtn)
    actionsGrid.add(sep())
    // 批量打包
    actionsGrid.add(label("打包"))
    actionsGrid.add(packByModuleBtn)
    actionsGrid.add(packPerOperatorBtn)
    actionsGrid.add(packMergedBtn)
    actionsGrid.add(sep())
    // 批量上传
    actionsGrid.add(label("上传"))
    actionsGrid.add(batchUploadMarketBtn)
    actionsGrid.add(batchUploadOyezBtn)
    // ---- 导出 Zip 到工程 target/ 目录 ----
    val exportZipBtn = JButton("导出Zip").apply {
      toolTipText = "把已勾选算子的 descriptor/oyez zip 按目录合并为工程名-descriptor.zip 和 工程名-oyez.zip，输出到 target/"
      addActionListener {
        val targetDir = java.io.File(project.basePath, "target")
        if (!targetDir.exists()) targetDir.mkdirs()

        fun rmTree(dir: java.io.File) { java.nio.file.Files.walk(dir.toPath()).sorted(Comparator.reverseOrder()).forEach { java.nio.file.Files.deleteIfExists(it) } }
        targetDir.listFiles()?.forEach { rmTree(it) }

        val rows = selectedPackRows()
        if (rows.isEmpty()) {
          setInlineStatus("未找到已勾选的算子。", PackLogTone.WARN)
          return@addActionListener
        }
        val descZips = rows.mapNotNull { computeZipTargets(it).descriptorZip }.filter { it.exists() && it.isFile }
        val oyezZips = rows.mapNotNull { computeZipTargets(it).oyezZip }.filter { it.exists() && it.isFile }
        val descMissing = rows.size - descZips.size
        val oyezMissing = rows.size - oyezZips.size
        if (descZips.isEmpty() && oyezZips.isEmpty()) {
          setInlineStatus("导出中止：未找到打包产物，请先「打包」。", PackLogTone.WARN)
          return@addActionListener
        }
        val projectName = project.name
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "导出 Zip", true) {
          override fun run(indicator: ProgressIndicator) {
            fun copyDirContents(srcDir: java.io.File, dstDir: java.io.File): Int {
              var count = 0
              val prefix = srcDir.absolutePath.let { p -> if (p.endsWith(java.io.File.separator)) p else p + java.io.File.separator }
              srcDir.walkTopDown().forEach { f ->
                if (f.isFile) {
                  val rel = f.absolutePath.removePrefix(prefix).replace('\\', '/')
                  val target = java.io.File(dstDir, rel)
                  target.parentFile.mkdirs()
                  f.copyTo(target, overwrite = true)
                  count++
                }
              }
              return count
            }

            fun zipDir(dir: java.io.File, outputZip: java.io.File): Int {
              var count = 0
              val prefix = dir.absolutePath.let { p -> if (p.endsWith(java.io.File.separator)) p else p + java.io.File.separator }
              val root = outputZip.name.removeSuffix(".zip") + "/"
              ZipOutputStream(outputZip.outputStream().buffered()).use { zos ->
                dir.walkTopDown().forEach { f ->
                  if (f.isFile) {
                    val rel = root + f.absolutePath.removePrefix(prefix).replace('\\', '/')
                    zos.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    count++
                  }
                }
              }
              return count
            }

            fun buildMergedZip(sources: List<java.io.File>, outputZip: java.io.File, kind: String, fracStart: Double, fracEnd: Double): Int {
              val tmpDir = java.io.File(targetDir, ".export-$kind-${System.currentTimeMillis()}")
              tmpDir.mkdirs()
              try {
                val n = sources.size
                var fileCount = 0
                for ((i, src) in sources.withIndex()) {
                  indicator.checkCanceled()
                  indicator.fraction = fracStart + (fracEnd - fracStart) * 0.8 * i / n
                  val srcDir = java.io.File(src.parentFile, src.name.removeSuffix(".zip"))
                  if (srcDir.isDirectory) {
                    indicator.text = "复制 ${srcDir.name} ($kind)"
                    indicator.text2 = "${i + 1}/$n"
                    fileCount += copyDirContents(srcDir, tmpDir)
                  } else {
                    indicator.text = "解压 ${src.name} ($kind)"
                    indicator.text2 = "${i + 1}/$n"
                    ZipInputStream(src.inputStream().buffered()).use { zis ->
                      var entry = zis.nextEntry
                      while (entry != null) {
                        val target = java.io.File(tmpDir, entry.name)
                        if (entry.isDirectory) {
                          target.mkdirs()
                        } else {
                          target.parentFile.mkdirs()
                          target.outputStream().use { out -> zis.copyTo(out) }
                          fileCount++
                        }
                        entry = zis.nextEntry
                      }
                    }
                  }
                }
                if (fileCount == 0) return 0
                indicator.fraction = fracStart + (fracEnd - fracStart) * 0.8
                indicator.text = "打包 ${outputZip.name} ($kind)"
                indicator.text2 = ""
                val zipped = zipDir(tmpDir, outputZip)
                indicator.fraction = fracEnd
                return zipped
              } finally {
                rmTree(tmpDir)
              }
            }

            var descCount = 0
            if (descZips.isNotEmpty()) {
              descCount = buildMergedZip(descZips, java.io.File(targetDir, "$projectName-descriptor.zip"), "descriptor",
                0.0, if (oyezZips.isEmpty()) 1.0 else 0.5)
            }
            var oyezCount = 0
            if (oyezZips.isNotEmpty()) {
              oyezCount = buildMergedZip(oyezZips, java.io.File(targetDir, "$projectName-oyez.zip"), "oyez",
                if (descZips.isEmpty()) 0.0 else 0.5, 1.0)
            }

            indicator.fraction = 1.0
            indicator.text = "导出完成"
            indicator.text2 = ""

            ApplicationManager.getApplication().invokeLater {
              val parts = mutableListOf<String>()
              if (descCount > 0) {
                parts += "descriptor($descCount 文件)"
                appendPackLogLine("导出完成(定义态) $projectName-descriptor.zip: $descCount 文件", PackLogTone.OK)
              }
              if (oyezCount > 0) {
                parts += "oyez($oyezCount 文件)"
                appendPackLogLine("导出完成(实现态) $projectName-oyez.zip: $oyezCount 文件", PackLogTone.OK)
              }
              if (parts.isEmpty()) {
                setInlineStatus("导出未生成任何输出 — 所选算子可能尚未打包。", PackLogTone.WARN)
              } else {
                setInlineStatus("导出完成: ${parts.joinToString(", ")} → ${targetDir.path}", PackLogTone.OK)
              }
            }
          }
        })
      }
    }
    val exportZipPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      add(label("导出"))
      add(exportZipBtn)
    }
    listToolbarEastPanel.add(actionsGrid, BorderLayout.CENTER)
    listToolbarEastPanel.add(exportZipPanel, BorderLayout.SOUTH)

    // Right-click context menu for per-operator actions（仅「算子」列）；「i18n」列单独菜单「生成」。
    fun resolveRowAt(point: java.awt.Point): OperatorRow? {
      val viewRow = resultTable.rowAtPoint(point)
      if (viewRow < 0) return null
      val modelRow = resultTable.convertRowIndexToModel(viewRow)
      return currentRows.getOrNull(modelRow)
    }

    val colOperatorName = 1
    val colI18n = 3

    var popupRow: OperatorRow? = null
    var i18nPopupRow: OperatorRow? = null
    val i18nGenMenu = JPopupMenu().apply {
      add(
        JMenuItem("生成").apply {
          addActionListener {
            i18nPopupRow?.let { r -> runI18nGenerateForRow(r) }
            i18nPopupRow = null
          }
        }
      )
    }
    val rowPopupGroup = DefaultActionGroup().apply {
      add(object : DumbAwareAction("复制显示名", "复制表格「算子」列显示名（⌘/Ctrl+C：所选行的名称）", AllIcons.Actions.Copy) {
        override fun actionPerformed(e: AnActionEvent) {
          popupRow?.let { r ->
            CopyPasteManager.getInstance().setContents(StringSelection(r.displayName))
          }
        }
      })
      add(object : DumbAwareAction("复制全限定类名", "复制 descriptor 类的全限定名", AllIcons.Actions.Copy) {
        override fun actionPerformed(e: AnActionEvent) {
          popupRow?.let { r ->
            CopyPasteManager.getInstance().setContents(StringSelection(r.className))
          }
        }
      })
      add(Separator.create())
      add(object : DumbAwareAction(
        "从此工程排除（持久）",
        "写入本工程配置：默认列表中隐藏；打包/批量上传仅针对当前可见行。勾选「显示已排除」后可取消排除。",
        AllIcons.General.Remove
      ) {
        override fun actionPerformed(e: AnActionEvent) {
          val r = popupRow ?: return
          val key = operatorRuntimeKey(r)
          val ex = operatorExcludeSettings()
          if (ex.isExcluded(key)) {
            setInlineStatus("该算子已在排除列表中。", PackLogTone.WARN)
            return
          }
          ex.addExcluded(key)
          packSelection.remove(key)
          refreshVisibleRows()
          refreshZipTargetsForVisibleRows()
          setInlineStatus("已排除（持久）：${r.displayName}", PackLogTone.OK)
        }
      })
      add(object : DumbAwareAction(
        "取消排除",
        "从排除列表移除；需勾选「显示已排除」才能对当前隐藏的算子执行本操作。",
        AllIcons.Actions.Refresh
      ) {
        override fun actionPerformed(e: AnActionEvent) {
          val r = popupRow ?: return
          val key = operatorRuntimeKey(r)
          val ex = operatorExcludeSettings()
          if (!ex.isExcluded(key)) {
            setInlineStatus("该算子不在排除列表中（若列表中看不到该算子，请先勾选「显示已排除」）。", PackLogTone.WARN)
            return
          }
          ex.removeExcluded(key)
          refreshVisibleRows()
          refreshZipTargetsForVisibleRows()
          setInlineStatus("已取消排除：${r.displayName}", PackLogTone.OK)
        }
      })
      add(Separator.create())
      add(object : DumbAwareAction("打包", "逐算子单包", AllIcons.Actions.Execute) {
        override fun actionPerformed(e: AnActionEvent) {
          popupRow?.let { r -> runPackWithMode("逐算子单包", listOf(r)) }
        }
      })
      add(Separator.create())
      add(object : DumbAwareAction("上传定义态", "上传定义态包到商城", AllIcons.Actions.Upload) {
        override fun actionPerformed(e: AnActionEvent) {
          popupRow?.let { r -> prepareMallUpload(r, OssUploader.PackKind.MARKETPLACE) }
        }
      })
      add(object : DumbAwareAction("上传实现态", "上传实现态包到商城", AllIcons.Actions.Upload) {
        override fun actionPerformed(e: AnActionEvent) {
          popupRow?.let { r -> prepareMallUpload(r, OssUploader.PackKind.OYEZ) }
        }
      })
      add(object : DumbAwareAction("历史", "查看商城上传历史", AllIcons.Vcs.History) {
        override fun actionPerformed(e: AnActionEvent) {
          popupRow?.let { r -> queryMallUploadHistory(r) }
        }
      })
      add(object : DumbAwareAction("删除商品…", "DELETE /manage/commodity/deleteByName（按算子 name）", AllIcons.General.Remove) {
        override fun actionPerformed(e: AnActionEvent) {
          popupRow?.let { r -> deleteCommoditiesByName(listOf(r), "算子右键菜单") }
        }
      })
    }
    val rowPopupMenu = ActionManager.getInstance().createActionPopupMenu("DatayooPicker.RowPopup", rowPopupGroup)
    resultTable.addMouseListener(object : MouseAdapter() {
      private fun handle(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val r = resolveRowAt(e.point) ?: return
        val viewRow = resultTable.rowAtPoint(e.point)
        val viewCol = resultTable.columnAtPoint(e.point)
        if (viewRow < 0 || viewCol < 0) return
        if (viewRow >= 0 && !resultTable.isRowSelected(viewRow)) {
          resultTable.setRowSelectionInterval(viewRow, viewRow)
        }
        when (val modelCol = resultTable.convertColumnIndexToModel(viewCol)) {
          colOperatorName -> {
            popupRow = r
            rowPopupMenu.component.show(e.component, e.x, e.y)
          }
          colI18n -> {
            i18nPopupRow = r
            i18nGenMenu.show(e.component, e.x, e.y)
          }
          else -> {
            // 选择/实现态数/帮助/图标列不弹出上述菜单
          }
        }
      }

      override fun mousePressed(e: MouseEvent) = handle(e)
      override fun mouseReleased(e: MouseEvent) = handle(e)
    })

    val northPanel = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
    }
    // 顶区纵向堆叠：避免 BorderLayout WEST+EAST 把「搜索+扫描」与「一长条按钮」横拼导致最小宽度极大。
    val topRow = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.emptyBottom(8)
    }
    filterBar.alignmentX = Component.LEFT_ALIGNMENT
    filterBar.maximumSize = Dimension(Int.MAX_VALUE, filterBar.preferredSize.height)
    listToolbarEastPanel.alignmentX = Component.LEFT_ALIGNMENT
    listToolbarEastPanel.maximumSize = Dimension(Int.MAX_VALUE, listToolbarEastPanel.preferredSize.height)
    topRow.add(filterBar)
    topRow.add(Box.createVerticalStrut(JBUI.scale(6)))
    topRow.add(listToolbarEastPanel)
    // List page only keeps scan/pack controls.
    northPanel.add(topRow, BorderLayout.NORTH)

    val tableContainer = JPanel(BorderLayout()).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.customLine(JBColor.border(), 1)
    }
    val tableScroll = JBScrollPane(resultTable)
    tableContainer.add(tableScroll, BorderLayout.CENTER)
    tableContainer.minimumSize = Dimension(JBUI.scale(280), 0)
    tableContainer.preferredSize = Dimension(JBUI.scale(520), 0)

    // ---- 连接器（与算子列表独立；打包走 connectorPack，不改动算子打包逻辑）----
    var connectorRows: List<ConnectorScanner.ConnectorRow> = emptyList()
    val connectorSelection = ConcurrentHashMap<String, Boolean>()
    var connectorScanning = false

    val connectorScanBtn = JButton("扫描连接器", AllIcons.Actions.Refresh)
    val connectorPackBtn = JButton("打包所选", AllIcons.Actions.Execute).apply {
      isEnabled = false
      toolTipText = "对勾选的 module 执行 clean → package → org.datayoo.pluginx:connector-plugin:connectorPack"
    }
    val connectorMallUploadBtn = JButton("上传至商城", AllIcons.Actions.Upload).apply {
      isEnabled = false
      toolTipText = "将勾选连接器的 target/{artifactId}.zip 上传至商城（POST …/importCommodity/connector）"
    }

    fun syncConnectorPackButton() {
      connectorPackBtn.isEnabled = connectorRows.isNotEmpty() && !connectorScanning
    }

    val connectorStatusArea = JBTextArea("点击「扫描连接器」开始").apply {
      isEditable = false
      lineWrap = true
      wrapStyleWord = true
      isOpaque = true
      background = UIUtil.getPanelBackground()
      foreground = JBColor.GRAY
      font = UIManager.getFont("Label.font")
      border = JBUI.Borders.empty(2, 0)
    }

    fun connectorKey(row: ConnectorScanner.ConnectorRow): String = row.pomDirPath

    fun selectedConnectorRows(): List<ConnectorScanner.ConnectorRow> =
      connectorRows.filter { connectorSelection[connectorKey(it)] == true }

    fun syncConnectorMallUploadButton() {
      connectorMallUploadBtn.isEnabled = selectedConnectorRows().isNotEmpty() && !connectorScanning
    }

    val connectorTableModel = object : DefaultTableModel(
      arrayOf("选择", "名称", "连接器类型"),
      0
    ) {
      override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex == 0) java.lang.Boolean::class.java else String::class.java

      override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 0
    }
    val connectorTable = JBTable(connectorTableModel).apply {
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      rowHeight = JBUI.scale(28)
      autoCreateRowSorter = true
    }
    val connectorSelectAllHeaderCheck = JBCheckBox().apply {
      isFocusable = false
      toolTipText = "全选/取消全选"
    }
    val connectorPackSelectHeaderPanel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      add(connectorSelectAllHeaderCheck)
    }
    connectorTable.tableHeader.reorderingAllowed = false
    connectorTable.columnModel.getColumn(0).apply {
      headerRenderer = javax.swing.table.DefaultTableCellRenderer().apply {
        horizontalAlignment = SwingConstants.CENTER
        layout = BorderLayout()
        add(connectorPackSelectHeaderPanel, BorderLayout.CENTER)
      }
      preferredWidth = JBUI.scale(60)
      maxWidth = JBUI.scale(72)
    }
    connectorTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(160)
    connectorTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(140)

    fun syncConnectorSelectHeaderCheckbox() {
      val n = connectorRows.size
      if (n == 0) {
        connectorSelectAllHeaderCheck.isEnabled = false
        connectorSelectAllHeaderCheck.isSelected = false
        return
      }
      connectorSelectAllHeaderCheck.isEnabled = true
      val sel = connectorRows.count { connectorSelection[connectorKey(it)] == true }
      connectorSelectAllHeaderCheck.isSelected = sel == n
    }

    fun refreshConnectorTable() {
      connectorTableModel.rowCount = 0
      for (row in connectorRows) {
        val key = connectorKey(row)
        if (!connectorSelection.containsKey(key)) {
          connectorSelection[key] = true
        }
        connectorTableModel.addRow(
          arrayOf<Any>(
            connectorSelection[key] == true,
            row.displayName,
            row.connectorTypes ?: "-"
          )
        )
      }
      syncConnectorSelectHeaderCheckbox()
      syncConnectorPackButton()
      syncConnectorMallUploadButton()
    }

    connectorSelectAllHeaderCheck.addActionListener {
      val selectAll = connectorSelectAllHeaderCheck.isSelected
      connectorRows.forEach { connectorSelection[connectorKey(it)] = selectAll }
      refreshConnectorTable()
    }

    connectorTable.model.addTableModelListener { e ->
      if (e.column != 0 || e.firstRow < 0) return@addTableModelListener
      val modelRow = connectorTable.convertRowIndexToModel(e.firstRow)
      if (modelRow < 0 || modelRow >= connectorRows.size) return@addTableModelListener
      val selected = connectorTableModel.getValueAt(modelRow, 0) as? Boolean ?: false
      connectorSelection[connectorKey(connectorRows[modelRow])] = selected
      syncConnectorSelectHeaderCheckbox()
      syncConnectorMallUploadButton()
    }

    fun doConnectorScan() {
      if (connectorScanning) return
      if (project.basePath.isNullOrBlank()) {
        connectorStatusArea.text = "扫描失败：当前窗口没有工程根目录"
        return
      }
      val scopeDesc = ReadAction.compute<String, RuntimeException> {
        ConnectorScanner.describeScanScope(project)
      }
      connectorScanning = true
      syncConnectorPackButton()
      syncConnectorMallUploadButton()
      connectorStatusArea.text = "正在扫描连接器…（$scopeDesc）"
      ProgressManager.getInstance().run(object : Task.Backgroundable(project, "扫描连接器", true) {
        override fun run(indicator: ProgressIndicator) {
          try {
            val result = ReadAction.compute<ConnectorScanner.ScanResult, RuntimeException> {
              ConnectorScanner.scanProject(project) { !indicator.isCanceled }
            }
            ApplicationManager.getApplication().invokeLater {
              connectorRows = result.connectors
              connectorSelection.clear()
              result.connectors.forEach { connectorSelection[connectorKey(it)] = true }
              refreshConnectorTable()
              connectorStatusArea.text = if (result.connectors.isEmpty()) {
                buildString {
                  append("扫描完成：0 个连接器。已检查 plugin.xml=${result.pluginXmlFilesScanned}（${result.elapsedMs}ms）\n")
                  append(scopeDesc)
                  if (result.pluginXmlFilesScanned == 0) {
                    append("\n未找到 ${ConnectorScanner.PLUGIN_XML_REL}（需含 <bracketsourcedescriptor>）。")
                    append("\n请确认 module 下有该文件，例如：…/brackets-db-mysql/src/main/resources/plugin.xml")
                  }
                }
              } else {
                "扫描完成：${result.connectors.size} 个连接器（plugin.xml=${result.pluginXmlFilesScanned}，${result.elapsedMs}ms）\n$scopeDesc"
              }
            }
          } catch (_: ProcessCanceledException) {
            ApplicationManager.getApplication().invokeLater {
              connectorStatusArea.text = "扫描已停止"
            }
          } catch (t: Throwable) {
            ApplicationManager.getApplication().invokeLater {
              connectorStatusArea.text = "扫描失败：${t.message ?: "未知错误"}"
            }
          } finally {
            connectorScanning = false
            ApplicationManager.getApplication().invokeLater {
              syncConnectorPackButton()
              syncConnectorMallUploadButton()
            }
          }
        }
      })
    }

    fun runConnectorPackSelected() {
      val selected = selectedConnectorRows()
      if (selected.isEmpty()) {
        appendPackLogLine("CONN 跳过：未勾选连接器（请先扫描并勾选）", PackLogTone.WARN)
        connectorStatusArea.text = "未开始打包：请先勾选连接器"
        return
      }
      val pomDirs = selected.mapNotNull { ConnectorScanner.pomDirVirtualFile(it.pomDirPath) }
      if (pomDirs.size != selected.size) {
        connectorStatusArea.text = "打包失败：部分 module 目录无效，请重新扫描"
        appendPackLogLine("CONN 失败：${selected.size - pomDirs.size} 个 module 路径无效", PackLogTone.ERROR)
        return
      }
      val commands = ConnectorPackPlanner.buildPackSteps(pomDirs)
      val preview = buildString {
        appendLine("== 连接器打包计划（每 module：clean → package → connectorPack，严格串行）==")
        commands.forEachIndexed { i, p -> appendLine(ConnectorPackPlanner.formatStep(i + 1, commands.size, p)) }
      }.trimEnd()
      connectorStatusArea.text = preview
      activateRunConsole()
      appendPackLogLine("CONN 开始：selected=${selected.size} steps=${commands.size}", PackLogTone.PROGRESS)
      setPackLogContent(preview)
      onPackStarted()

      val artifactByPomPath = selected.associateBy({ it.pomDirPath }, { it.artifactId })
      runMavenSequential(
        project,
        connectorStatusArea,
        commands,
        "连接器打包",
        { line, tone -> appendPackLogLine(line, tone) },
        onStepFailed = { _, exitCode ->
          ApplicationManager.getApplication().invokeLater {
            connectorStatusArea.text = "连接器打包失败：exitCode=$exitCode（见下方日志）"
          }
        },
        onAllSucceeded = {
          ApplicationManager.getApplication().invokeLater {
            connectorStatusArea.text = "连接器打包完成（${selected.size} 个 module）"
          }
        },
        bindMavenStepHandler = { bindMavenStepToRunWindow(it) },
        onPackSessionFinished = {
          clearRunWindowPackProcessBinding()
          onPackEnded()
        },
        onStepSucceeded = { params ->
          val pomPath = runCatching { java.io.File(params.workingDirPath).canonicalPath }.getOrDefault(params.workingDirPath)
          val artifactId = artifactByPomPath[pomPath] ?: return@runMavenSequential
          if (params.goals.any { it.contains("connectorPack") }) {
            val pomDir = ConnectorScanner.pomDirVirtualFile(pomPath) ?: return@runMavenSequential
            val zip = ConnectorPackPlanner.expectedZipFile(pomDir, artifactId)
            if (zip.exists() && zip.isFile) {
              appendPackLogLine("CONN ZIP OK ${zip.path}", PackLogTone.OK)
            } else {
              appendPackLogLine("CONN WARN zip 未找到: ${zip.path}", PackLogTone.WARN)
            }
          }
        }
      )
    }

    fun prepareConnectorMallUpload(rows: List<ConnectorScanner.ConnectorRow>) {
      if (rows.isEmpty()) {
        appendPackLogLine("商城上传跳过：未勾选连接器。", PackLogTone.WARN)
        return
      }
      activateRunConsole()
      val tozooBase =
        ApplicationManager.getApplication().getService(TozooUploadSettings::class.java).state.baseUrl.trim()
      if (tozooBase.isEmpty()) {
        val msg = "Tozoo 根地址未配置：请在「配置」页设置后再上传。"
        appendPackLogLine(msg, PackLogTone.ERROR)
        connectorStatusArea.text = msg
        return
      }
      val pending = mutableListOf<MallPendingConnector>()
      val missing = mutableListOf<String>()
      for (row in rows) {
        val pomDir = ConnectorScanner.pomDirVirtualFile(row.pomDirPath)
        if (pomDir == null) {
          missing += "${row.displayName}（目录无效）"
          continue
        }
        val zip = ConnectorPackPlanner.expectedZipFile(pomDir, row.artifactId)
        if (!zip.isFile) {
          missing += "${row.displayName}（未找到 ${zip.name}，请先打包）"
          continue
        }
        pending += MallPendingConnector(row.displayName, zip, row)
      }
      if (pending.isEmpty()) {
        val msg = buildString {
          append("未发现可上传的连接器 zip。")
          if (missing.isNotEmpty()) append("\n").append(missing.joinToString("\n"))
        }
        appendPackLogLine(msg, PackLogTone.ERROR)
        connectorStatusArea.text = msg
        return
      }
      if (missing.isNotEmpty()) {
        appendPackLogLine("部分连接器已跳过：\n${missing.joinToString("\n")}", PackLogTone.WARN)
      }
      mallPendingRow = null
      mallPendingOperators = emptyList()
      mallPendingConnectors = pending
      refreshMallCommitPanel()
      appendPackLogLine(
        "已准备上传 ${pending.size} 个连接器包，请填写提交说明后点击「提交」。",
        PackLogTone.DEFAULT
      )
      connectorStatusArea.text = "待上传 ${pending.size} 个连接器 → 请在左侧「商城」页填写变更说明并提交"
      ApplicationManager.getApplication().invokeLater { showMallUploadPage() }
    }

    connectorScanBtn.addActionListener { doConnectorScan() }
    connectorPackBtn.addActionListener { runConnectorPackSelected() }
    connectorMallUploadBtn.addActionListener { prepareConnectorMallUpload(selectedConnectorRows()) }

    val connectorToolbarRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4))).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(4, 8)
      add(connectorScanBtn)
      add(connectorPackBtn)
      add(connectorMallUploadBtn)
    }
    val connectorTableScroll = JBScrollPane(connectorTable)
    val connectorPage = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      add(connectorToolbarRow, BorderLayout.NORTH)
      add(
        JPanel(BorderLayout()).apply {
          isOpaque = true
          background = UIUtil.getPanelBackground()
          border = JBUI.Borders.customLine(JBColor.border(), 1)
          add(connectorTableScroll, BorderLayout.CENTER)
        },
        BorderLayout.CENTER
      )
      add(JBScrollPane(connectorStatusArea).apply { border = JBUI.Borders.emptyTop(4) }, BorderLayout.SOUTH)
    }

    // Navigation (left menu) + pages (right content)
    val cardLayout = java.awt.CardLayout()
    val cards = JPanel(cardLayout).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
    }
    val CARD_LIST = "list"
    val CARD_CONNECTOR = "connector"
    val CARD_MALL = "mall"
    val CARD_HISTORY = "history"
    val CARD_CONFIG = "config"

    val listPage = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      add(northPanel, BorderLayout.NORTH)
      add(tableContainer, BorderLayout.CENTER)
    }

    val mallHeader = JPanel(BorderLayout()).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(0, 0, 8, 0)
      add(mallSectionTitle("上传至商城"), BorderLayout.WEST)
    }
    val mallPage = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(0, 16, 0, 16)
      add(mallHeader, BorderLayout.NORTH)
      add(mallCommitScroll, BorderLayout.CENTER)
    }

    val historyHeader = JPanel(BorderLayout()).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(0, 0, 8, 0)
      add(mallSectionTitle("上传历史"), BorderLayout.WEST)
      add(
        JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
          isOpaque = false
          add(historyDeleteCommodityBtn)
          add(historyDeleteBtn)
          add(historyBackBtn)
        },
        BorderLayout.EAST
      )
    }
    val historyPage = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(0, 16, 0, 16)
      add(historyHeader, BorderLayout.NORTH)
      add(JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.customLine(JBColor.border(), 1)
        add(JPanel(BorderLayout(0, JBUI.scale(8))).apply {
          isOpaque = true
          background = UIUtil.getPanelBackground()
          border = JBUI.Borders.empty(10, 10, 10, 10)
          add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(historyInfoLabel, BorderLayout.CENTER)
          }, BorderLayout.NORTH)
          add(JBScrollPane(historyTable), BorderLayout.CENTER)
        }, BorderLayout.CENTER)
      }, BorderLayout.CENTER)
    }

    val configHeader = JPanel(BorderLayout()).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(0, 0, 8, 0)
      add(mallSectionTitle("配置"), BorderLayout.WEST)
    }
    val configTozooField = JBTextField(48).apply {
      text = tozooSettings.state.baseUrl
      toolTipText = "含 context-path，例如 http://127.0.0.1:8080/member"
    }
    val configSaveBtn = JButton("保存 Tozoo 地址").apply {
      addActionListener {
        val url = configTozooField.text.trim()
        tozooSettings.loadState(TozooUploadSettings.State(baseUrl = url))
        tozooUrlField.text = url
        setInlineStatus(
          if (url.isBlank()) "已清空 Tozoo 根地址（商城上传将不可用）" else "已保存 Tozoo 根地址",
          if (url.isBlank()) PackLogTone.WARN else PackLogTone.OK
        )
      }
    }
    val configPanel = JPanel(BorderLayout(0, JBUI.scale(10))).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.customLine(JBColor.border(), 1)
      add(configHeader, BorderLayout.NORTH)
      add(JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(10, 10, 10, 10)

        val form = JPanel().apply {
          isOpaque = false
          layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        form.add(JBLabel("Tozoo 根地址").apply {
          alignmentX = 0f
          border = JBUI.Borders.emptyBottom(6)
        })
        configTozooField.maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(30))
        configTozooField.preferredSize = Dimension(JBUI.scale(560), JBUI.scale(30))
        configTozooField.minimumSize = Dimension(JBUI.scale(240), JBUI.scale(30))
        form.add(configTozooField.apply { alignmentX = 0f })
        form.add(Box.createVerticalStrut(JBUI.scale(10)))
        form.add(deployCheckbox.apply { alignmentX = 0f })
        form.add(Box.createVerticalStrut(JBUI.scale(10)))
        form.add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
          isOpaque = false
          alignmentX = 0f
          add(configSaveBtn)
          add(JButton("复制扫描调试").apply { addActionListener { copyScanDebug() } })
          add(JButton("复制打包调试").apply { addActionListener { copyPackDebug() } })
          add(JButton("切换独立窗口/停靠").apply { addActionListener { toggleWindowed() } })
        })
        form.add(Box.createVerticalGlue())
        add(form, BorderLayout.NORTH)
      }, BorderLayout.CENTER)
    }

    cards.add(listPage, CARD_LIST)
    cards.add(connectorPage, CARD_CONNECTOR)
    cards.add(mallPage, CARD_MALL)
    cards.add(historyPage, CARD_HISTORY)
    cards.add(configPanel, CARD_CONFIG)

    data class NavItem(val id: String, val title: String, val icon: javax.swing.Icon)
    val navModel = DefaultListModel<NavItem>().apply {
      addElement(NavItem(CARD_LIST, "算子列表", AllIcons.Actions.ListFiles))
      addElement(NavItem(CARD_CONNECTOR, "连接器", AllIcons.Nodes.Plugin))
      addElement(NavItem(CARD_CONFIG, "配置", AllIcons.General.Settings))
    }
    val navList = JBList(navModel).apply {
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      selectedIndex = 0
      visibleRowCount = navModel.size()
      fixedCellHeight = JBUI.scale(52)
      border = JBUI.Borders.customLine(JBColor.border(), 1)
      background = UIUtil.getPanelBackground()
      cellRenderer = ListCellRenderer { _, value, _, isSelected, _ ->
        val item = value as NavItem
        JPanel(BorderLayout()).apply {
          isOpaque = true
          background = if (isSelected) JBColor.namedColor("List.selectionBackground", JBColor(0xDDEBFF, 0x2B2D30)) else UIUtil.getPanelBackground()
          border = JBUI.Borders.empty(6, 8)
          val iconLabel = JBLabel(item.icon).apply {
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
          }
          add(iconLabel, BorderLayout.CENTER)
          toolTipText = item.title
        }
      }
    }
    navList.addListSelectionListener {
      if (it.valueIsAdjusting) return@addListSelectionListener
      val item = navModel.getElementAt(navList.selectedIndex)
      cardLayout.show(cards, item.id)
      if (item.id == CARD_CONNECTOR && !connectorScanning) {
        doConnectorScan()
      }
    }

    val navWrapper = JPanel(BorderLayout()).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(0, 0, 0, 8)
      add(navList, BorderLayout.CENTER)
      preferredSize = Dimension(JBUI.scale(56), 0)
      minimumSize = Dimension(JBUI.scale(56), 0)
    }

    val root = JPanel(BorderLayout(0, 0)).apply {
      isOpaque = true
      background = UIUtil.getPanelBackground()
      add(navWrapper, BorderLayout.WEST)
      add(cards, BorderLayout.CENTER)
    }

    // Wire: clicking "上传至商城…" switches to mall page and focuses change log.
    showMallUploadPage = {
      refreshMallCommitPanel()
      cardLayout.show(cards, CARD_MALL)
      SwingUtilities.invokeLater { mallChangeLogArea.requestFocusInWindow() }
    }
    showHistoryPage = { displayName ->
      cardLayout.show(cards, CARD_HISTORY)
      if (historyTableModel.rowCount == 0) {
        clearHistoryPage("$displayName   正在查询历史...", PackLogTone.PROGRESS)
      }
    }
    historyBackBtn.addActionListener {
      val returnCard = when (historyReturnNavIndex) {
        1 -> CARD_CONNECTOR
        else -> CARD_LIST
      }
      cardLayout.show(cards, returnCard)
      navList.selectedIndex = historyReturnNavIndex.coerceIn(0, navModel.size() - 1)
    }

    val mainSplitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, root, packLogPanel).apply {
      dividerSize = JBUI.scale(3)
      resizeWeight = 0.72
      isOpaque = true
      background = UIUtil.getPanelBackground()
    }
    panel.add(mainSplitPane, BorderLayout.CENTER)

    mallSubmitBtn.addActionListener {
      val ops = currentMallUploadSteps()
      val cl = mallChangeLogArea.text.trim()
      if (ops.isEmpty()) {
        appendPackLogLine("没有待上传内容。", PackLogTone.WARN)
        return@addActionListener
      }
      if (cl.isEmpty()) {
        appendPackLogLine("请先填写提交说明。", PackLogTone.WARN)
        return@addActionListener
      }
      runMallUploadSteps(cl)
    }

    mallUploadStopBtn.addActionListener {
      val ind = runningUploadIndicator.get()
      if (ind != null && !ind.isCanceled) {
        ind.cancel()
        mallUploadStopBtn.isEnabled = false
        appendPackLogLine("正在停止上传…", PackLogTone.WARN)
      } else {
        appendPackLogLine("当前没有正在进行的上传。", PackLogTone.WARN)
      }
    }

    val content = ContentFactory.getInstance().createContent(panel, "", false)
    content.setDisposer(helpPreviewDisposable)
    toolWindow.contentManager.addContent(content)

    // Titlebar "+" actions removed; moved into dedicated "配置" page.

    // no split pane
  }
}
