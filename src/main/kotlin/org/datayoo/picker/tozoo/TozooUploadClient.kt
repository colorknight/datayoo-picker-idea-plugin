package org.datayoo.picker.tozoo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

object TozooUploadClient {

  data class Result(val ok: Boolean, val status: Int, val message: String)
  data class HistoryItem(
    val id: String,
    val framework: String,
    val version: String,
    val changeLog: String,
    val kind: String,
    val createdTime: String
  )
  data class HistoryResult(
    val ok: Boolean,
    val status: Int,
    val message: String,
    val items: List<HistoryItem>,
    val totalElements: Long,
    val pageNo: Int,
    val pageSize: Int,
    val body: String
  )
  data class DeleteResult(val ok: Boolean, val status: Int, val message: String)

  /** [DELETE /manage/commodity/delete] 删除商品（含版本记录与 OSS）。 */
  data class DeleteCommodityResult(
    val ok: Boolean,
    val status: Int,
    /** 成功时为摘要；失败时为错误说明或响应体片段 */
    val message: String,
    /** 本次请求的完整 URL（排查 404/路由问题时使用） */
    val requestUrl: String = "",
    val commodityId: String = "",
    val commodityName: String = "",
    val deletedCommodity: Boolean? = null,
    val deletedVersionCount: Int? = null,
    val rawBody: String = ""
  )

  /**
   * 导入算子安装包（OpenAPI：`POST /member/commodity/importCommodity/operator`，`multipart/form-data`，参数 `file`）。
   * 定义态、实现态均走同一接口，仅上传 zip 文件。
   *
   * @param baseUrl 须含 context-path `/member`，例如 `http://127.0.0.1:8080/member`，
   *   实际请求为 `{baseUrl}/commodity/importCommodity/operator`。
   */
  fun uploadOperatorZip(
    baseUrl: String,
    zipFile: File,
    changeLog: String,
    uploadFileName: String? = null
  ): Result = uploadCommodityZip(baseUrl, "operator", zipFile, changeLog, uploadFileName)

  /**
   * 导入连接器安装包（OpenAPI：`POST /member/commodity/importCommodity/connector`，`multipart/form-data`）。
   */
  fun uploadConnectorZip(
    baseUrl: String,
    zipFile: File,
    changeLog: String,
    uploadFileName: String? = null
  ): Result = uploadCommodityZip(baseUrl, "connector", zipFile, changeLog, uploadFileName)

  private fun uploadCommodityZip(
    baseUrl: String,
    commodityPath: String,
    zipFile: File,
    changeLog: String,
    uploadFileName: String?
  ): Result {
    val root = baseUrl.trimEnd('/')
    val uri = URI.create("$root/commodity/importCommodity/$commodityPath")
    val boundary = "----DatayooFormBoundary${UUID.randomUUID()}"
    val fileBytes = zipFile.readBytes()
    val body = buildMultipart(boundary, uploadFileName?.trim().takeUnless { it.isNullOrEmpty() } ?: zipFile.name, fileBytes, changeLog)

    val client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMinutes(2))
      .build()
    val request = HttpRequest.newBuilder(uri)
      .timeout(Duration.ofMinutes(15))
      .header("Content-Type", "multipart/form-data; boundary=$boundary")
      .POST(HttpRequest.BodyPublishers.ofByteArray(body))
      .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    val text = (response.body() ?: "").take(2000)
    return if (response.statusCode() in 200..299) {
      Result(ok = true, status = response.statusCode(), message = text.ifBlank { "OK" })
    } else {
      Result(ok = false, status = response.statusCode(), message = text.ifBlank { "HTTP ${response.statusCode()}" })
    }
  }

  /**
   * 查询算子/商品版本导入历史（Tozoo 后端接口）。
   *
   * 默认接口路径：
   * `GET /member/manage/commodity/version/history`（`operatorName`、`computionFramework`、`pageNo`、`pageSize`）
   *
   * 成功响应为 **JSON 数组** `CommodityVersion[]`；仍兼容旧版 Spring `Page` 包装（`content` 数组）。
   */
  fun queryOperatorUploadHistory(
    baseUrl: String,
    operatorName: String,
    computionFramework: String? = null,
    pageNo: Int = 1,
    pageSize: Int = 20,
    endpointPath: String = "/manage/commodity/version/history"
  ): HistoryResult {
    val root = baseUrl.trimEnd('/')
    val op = URLEncoder.encode(operatorName.trim(), StandardCharsets.UTF_8)
    val fw = computionFramework?.trim().orEmpty()
    val frameworkQuery = if (fw.isEmpty()) "" else "&computionFramework=" + URLEncoder.encode(fw, StandardCharsets.UTF_8)
    val uri = URI.create(
      "$root${if (endpointPath.startsWith("/")) endpointPath else "/$endpointPath"}" +
        "?operatorName=$op$frameworkQuery&pageNo=$pageNo&pageSize=$pageSize"
    )

    val client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMinutes(2))
      .build()
    val request = HttpRequest.newBuilder(uri)
      .timeout(Duration.ofMinutes(15))
      .GET()
      .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    val raw = response.body() ?: ""
    val text = raw.take(HISTORY_JSON_MAX)
    if (response.statusCode() !in 200..299) {
      return HistoryResult(
        ok = false,
        status = response.statusCode(),
        message = text.ifBlank { "HTTP ${response.statusCode()}" },
        items = emptyList(),
        totalElements = 0,
        pageNo = pageNo,
        pageSize = pageSize,
        body = text
      )
    }

    return runCatching {
      parseCommodityVersionHistory(
        text = text,
        httpStatus = response.statusCode(),
        requestPageNo = pageNo,
        requestPageSize = pageSize
      )
    }.getOrElse { ex ->
      HistoryResult(
        ok = false,
        status = response.statusCode(),
        message = "解析历史记录失败：${ex.message ?: ex::class.java.simpleName}",
        items = emptyList(),
        totalElements = 0,
        pageNo = pageNo,
        pageSize = pageSize,
        body = text
      )
    }
  }

  private const val HISTORY_JSON_MAX = 1_000_000

  private fun parseCommodityVersionHistory(
    text: String,
    httpStatus: Int,
    requestPageNo: Int,
    requestPageSize: Int
  ): HistoryResult {
    val root = JsonParser.parseString(text)
    if (root.isJsonArray) {
      val items = mapCommodityVersionArray(root.asJsonArray)
      return HistoryResult(
        ok = true,
        status = httpStatus,
        message = "OK",
        items = items,
        totalElements = items.size.toLong(),
        pageNo = requestPageNo,
        pageSize = requestPageSize,
        body = text
      )
    }
    if (root.isJsonObject) {
      val rootObj = root.asJsonObject
      val content = rootObj.getAsJsonArray("content")
      if (content != null) {
        val items = mapCommodityVersionArray(content)
        return HistoryResult(
          ok = true,
          status = httpStatus,
          message = "OK",
          items = items,
          totalElements = rootObj.get("totalElements")?.asLong ?: items.size.toLong(),
          pageNo = (rootObj.get("number")?.asInt ?: (requestPageNo - 1)) + 1,
          pageSize = rootObj.get("size")?.asInt ?: requestPageSize,
          body = text
        )
      }
    }
    error("不支持的 JSON 结构（需数组或 Page.content）")
  }

  private fun mapCommodityVersionArray(content: JsonArray): List<HistoryItem> {
    return content.mapNotNull { item ->
      if (!item.isJsonObject) return@mapNotNull null
      val obj = item.asJsonObject
      HistoryItem(
        id = obj.readAsText("id"),
        framework = obj.readAsText("computionFramework"),
        version = obj.readAsText("version"),
        changeLog = obj.readAsText("changeLog"),
        kind = obj.readAsText("kind"),
        createdTime = obj.readAsText("createdTime")
      )
    }
  }

  fun deleteOperatorUploadHistoryVersion(
    baseUrl: String,
    versionId: String,
    deleteOss: Boolean = true,
    endpointPath: String = "/manage/commodity/version/delete"
  ): DeleteResult {
    val root = baseUrl.trimEnd('/')
    val vid = URLEncoder.encode(versionId.trim(), StandardCharsets.UTF_8)
    val uri = URI.create(
      "$root${if (endpointPath.startsWith("/")) endpointPath else "/$endpointPath"}" +
        "?versionId=$vid&deleteOss=$deleteOss"
    )
    val client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMinutes(2))
      .build()
    val request = HttpRequest.newBuilder(uri)
      .timeout(Duration.ofMinutes(15))
      .DELETE()
      .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    val text = (response.body() ?: "").take(4000)
    return if (response.statusCode() in 200..299) {
      DeleteResult(ok = true, status = response.statusCode(), message = text.ifBlank { "OK" })
    } else {
      DeleteResult(ok = false, status = response.statusCode(), message = text.ifBlank { "HTTP ${response.statusCode()}" })
    }
  }

  /**
   * 删除商品（OpenAPI：`DELETE /member/manage/commodity/delete`，query：`commodityId`、`deleteOss`）。
   */
  fun deleteCommodity(
    baseUrl: String,
    commodityId: String,
    deleteOss: Boolean = true,
    endpointPath: String = "/manage/commodity/delete"
  ): DeleteCommodityResult {
    val root = baseUrl.trimEnd('/')
    val cid = URLEncoder.encode(commodityId.trim(), StandardCharsets.UTF_8)
    val uri = URI.create(
      "$root${if (endpointPath.startsWith("/")) endpointPath else "/$endpointPath"}" +
        "?commodityId=$cid&deleteOss=$deleteOss"
    )
    val reqUrl = uri.toString()
    val client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMinutes(2))
      .build()
    val request = HttpRequest.newBuilder(uri)
      .timeout(Duration.ofMinutes(15))
      .DELETE()
      .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    val code = response.statusCode()
    val text = (response.body() ?: "").take(8000)
    if (code !in 200..299 && code != 204) {
      return DeleteCommodityResult(
        ok = false,
        status = code,
        message = text.ifBlank { "HTTP $code" },
        requestUrl = reqUrl,
        rawBody = text
      )
    }
    if (code == 204 || text.isBlank()) {
      return DeleteCommodityResult(
        ok = true,
        status = code,
        message = "删除成功（HTTP $code，无响应体）",
        requestUrl = reqUrl,
        commodityId = commodityId.trim(),
        deletedCommodity = true,
        rawBody = text
      )
    }
    return parseDeleteCommodityResponse(
      text,
      code,
      requestCommodityId = commodityId.trim(),
      requestUrl = reqUrl
    )
  }

  /**
   * 删除商品（OpenAPI：`DELETE /member/manage/commodity/deleteByName`，query：`name`、`deleteOss`）。
   */
  fun deleteCommodityByName(
    baseUrl: String,
    name: String,
    deleteOss: Boolean = true,
    endpointPath: String = "/manage/commodity/deleteByName"
  ): DeleteCommodityResult {
    val root = baseUrl.trimEnd('/')
    val nm = URLEncoder.encode(name.trim(), StandardCharsets.UTF_8)
    val uri = URI.create(
      "$root${if (endpointPath.startsWith("/")) endpointPath else "/$endpointPath"}" +
        "?name=$nm&deleteOss=$deleteOss"
    )
    val reqUrl = uri.toString()
    val client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMinutes(2))
      .build()
    val request = HttpRequest.newBuilder(uri)
      .timeout(Duration.ofMinutes(15))
      .DELETE()
      .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    val code = response.statusCode()
    val text = (response.body() ?: "").take(8000)
    if (code !in 200..299 && code != 204) {
      return DeleteCommodityResult(
        ok = false,
        status = code,
        message = text.ifBlank { "HTTP $code" },
        requestUrl = reqUrl,
        commodityName = name.trim(),
        rawBody = text
      )
    }
    if (code == 204 || text.isBlank()) {
      return DeleteCommodityResult(
        ok = true,
        status = code,
        message = "删除成功（HTTP $code，无响应体）",
        requestUrl = reqUrl,
        commodityName = name.trim(),
        deletedCommodity = true,
        rawBody = text
      )
    }
    return parseDeleteCommodityResponse(
      text,
      code,
      requestCommodityName = name.trim(),
      requestUrl = reqUrl
    )
  }

  private fun parseDeleteCommodityResponse(
    text: String,
    httpStatus: Int,
    requestCommodityId: String = "",
    requestCommodityName: String = "",
    requestUrl: String = ""
  ): DeleteCommodityResult {
    return runCatching {
      val obj = JsonParser.parseString(text).asJsonObject
      val id = obj.readAsText("commodityId").ifBlank { requestCommodityId }
      val name = obj.readAsText("commodityName").ifBlank { requestCommodityName }
      val deletedCommodity = obj.get("deletedCommodity")?.takeIf { !it.isJsonNull }?.asBoolean
      val cnt = obj.get("deletedVersionCount")?.takeIf { !it.isJsonNull }?.asInt
      val ossFail = obj.get("ossDeleteFailedCount")?.takeIf { !it.isJsonNull }?.asInt
      val summary = buildString {
        if (id.isNotBlank()) append("commodityId=").append(id)
        if (name.isNotBlank()) {
          if (isNotEmpty()) append("  ")
          append("name=").append(name)
        }
        deletedCommodity?.let { append("\ndeletedCommodity=").append(it) }
        cnt?.let { append("\ndeletedVersionCount=").append(it) }
        ossFail?.let { if (it > 0) append("\nossDeleteFailedCount=").append(it) }
      }
      DeleteCommodityResult(
        ok = true,
        status = httpStatus,
        message = summary.ifBlank { text.take(1200) },
        requestUrl = requestUrl,
        commodityId = id,
        commodityName = name,
        deletedCommodity = deletedCommodity,
        deletedVersionCount = cnt,
        rawBody = text
      )
    }.getOrElse {
      DeleteCommodityResult(
        ok = true,
        status = httpStatus,
        message = text.take(2000),
        requestUrl = requestUrl,
        commodityId = requestCommodityId,
        commodityName = requestCommodityName,
        rawBody = text
      )
    }
  }

  private fun JsonElement.readAsText(field: String): String {
    val v = asJsonObject.get(field) ?: return ""
    return when {
      v.isJsonNull -> ""
      v.isJsonPrimitive -> v.asJsonPrimitive.run {
        when {
          isString -> asString
          isNumber -> asNumber.toString()
          isBoolean -> asBoolean.toString()
          else -> toString()
        }
      }
      else -> v.toString()
    }
  }

  private fun buildMultipart(boundary: String, filename: String, fileBytes: ByteArray, changeLog: String): ByteArray {
    val crlf = "\r\n"
    val sb = StringBuilder()
    // changeLog (required by backend)
    sb.append("--").append(boundary).append(crlf)
    sb.append("Content-Disposition: form-data; name=\"changeLog\"").append(crlf)
    sb.append("Content-Type: text/plain; charset=UTF-8").append(crlf).append(crlf)
    sb.append((changeLog.ifBlank { "更新" }).replace("\r", " ").replace("\n", "\n")).append(crlf)

    sb.append("--").append(boundary).append(crlf)
    sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
      .append(filename.replace("\"", "_")).append("\"").append(crlf)
    sb.append("Content-Type: application/octet-stream").append(crlf).append(crlf)
    val head = sb.toString().toByteArray(StandardCharsets.UTF_8)
    val tail = ("\r\n--$boundary--\r\n").toByteArray(StandardCharsets.UTF_8)
    return concat(head, fileBytes, tail)
  }

  private fun concat(a: ByteArray, b: ByteArray, c: ByteArray): ByteArray {
    val out = ByteArray(a.size + b.size + c.size)
    System.arraycopy(a, 0, out, 0, a.size)
    System.arraycopy(b, 0, out, a.size, b.size)
    System.arraycopy(c, 0, out, a.size + b.size, c.size)
    return out
  }
}
