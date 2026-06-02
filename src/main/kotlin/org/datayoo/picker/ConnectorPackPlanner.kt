package org.datayoo.picker

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.execution.MavenRunnerParameters

/**
 * 连接器 module 打包计划：每个 module 串行 clean → package → connectorPack。
 */
object ConnectorPackPlanner {
  const val DATAYOO_PLUGINX_VERSION = "1.0-SNAPSHOT"
  const val CONNECTOR_PACK_GOAL =
    "org.datayoo.pluginx:connector-plugin:$DATAYOO_PLUGINX_VERSION:connectorPack"

  private const val MAVEN_CLEAN_GOAL = "clean"
  private const val MAVEN_PACKAGE_GOAL = "package"
  private const val MAVEN_SKIP_TESTS_OPTION = "-DskipTests"

  fun formatStep(index: Int, total: Int, params: MavenRunnerParameters): String {
    val baseDirName = runCatching { java.io.File(params.workingDirPath).name }.getOrDefault(params.workingDirPath)
    val goalsText = params.goals.joinToString(" ")
    val opts = params.cmdOptions?.takeIf { it.isNotBlank() } ?: "(none)"
    return "[$index/$total] connectorModule=$baseDirName goals=$goalsText opts=$opts"
  }

  fun buildPackSteps(pomDirs: List<VirtualFile>): List<MavenRunnerParameters> {
    if (pomDirs.isEmpty()) return emptyList()
    val steps = mutableListOf<MavenRunnerParameters>()
    for (pomDir in pomDirs) {
      steps += mavenParams(pomDir, listOf(MAVEN_CLEAN_GOAL), null)
      steps += mavenParams(pomDir, listOf(MAVEN_PACKAGE_GOAL), MAVEN_SKIP_TESTS_OPTION)
      steps += mavenParams(pomDir, listOf(CONNECTOR_PACK_GOAL), MAVEN_SKIP_TESTS_OPTION)
    }
    return steps
  }

  fun expectedZipFile(pomDir: VirtualFile, artifactId: String): java.io.File =
    java.io.File(pomDir.path, "target/$artifactId.zip")

  private fun mavenParams(pomDir: VirtualFile, goals: List<String>, cmdOption: String?): MavenRunnerParameters {
    return MavenRunnerParameters(
      true,
      pomDir.path,
      "pom.xml",
      goals,
      emptyList()
    ).also { params ->
      params.cmdOptions = cmdOption?.trim()?.takeIf { it.isNotBlank() }
    }
  }
}
