package org.datayoo.picker

import com.aliyun.oss.OSSClientBuilder
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.FileInputStream

object OssUploader {
  private val logger = Logger.getInstance(OssUploader::class.java)

  data class UploadResult(
    val objectKey: String? = null,
    val error: String? = null
  ) {
    val ok: Boolean get() = !objectKey.isNullOrBlank() && error.isNullOrBlank()
  }

  data class UploadConfig(
    val endpoint: String,
    val accessKeyId: String,
    val accessKeySecret: String,
    val bucketName: String,
    /**
     * Base path on OSS, e.g. operators/
     */
    val testBasePath: String,
    val cacheVersionCount: Int = 3
  )

  /**
   * [DESCRIPTOR] / [OYEZ]: 开发环境 OSS 目录。
   * [MARKETPLACE]: 商城目录 `marketplace/`（定义态 zip）；实现态 zip 仍用 [OYEZ] 走 `oyez/`。
   * GUI「商城」列仅经 Tozoo 串行上传，不再使用本类直连 OSS。
   */
  enum class PackKind { DESCRIPTOR, OYEZ, MARKETPLACE }

  fun uploadZip(config: UploadConfig, kind: PackKind, zipFile: File): UploadResult {
    if (!zipFile.exists() || !zipFile.isFile) {
      return UploadResult(
        error = "未发现单算子安装包（zip 不存在）：${zipFile.path}。请先对该算子执行单包打包，确认 target 下生成了同名 zip。"
      )
    }

    val base = config.testBasePath.trim().let { if (it.endsWith("/")) it else "$it/" }
    val dir = when (kind) {
      PackKind.DESCRIPTOR -> "descriptor/"
      PackKind.OYEZ -> "oyez/"
      PackKind.MARKETPLACE -> "marketplace/"
    }
    val objectKey = (base + dir + zipFile.name).replace("\\", "/").replace(Regex("/+"), "/")

    val client = OSSClientBuilder().build(config.endpoint, config.accessKeyId, config.accessKeySecret)
    try {
      FileInputStream(zipFile).use { input ->
        client.putObject(config.bucketName, objectKey, input)
      }
      logger.info("OSS upload OK: bucket=${config.bucketName}, key=$objectKey")
      return UploadResult(objectKey = objectKey)
    } catch (t: Throwable) {
      val msg = buildString {
        append(t.javaClass.name)
        val m = t.message?.trim()
        if (!m.isNullOrBlank()) append(": ").append(m)
      }
      logger.warn("OSS upload failed: $msg", t)
      return UploadResult(error = msg)
    } finally {
      runCatching { client.shutdown() }
    }
  }
}
