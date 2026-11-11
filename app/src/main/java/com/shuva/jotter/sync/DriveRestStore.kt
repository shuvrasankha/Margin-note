package com.shuva.jotter.sync

import com.shuva.jotter.data.JotterJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class DriveHttpException(val code: Int, body: String) : IOException("Drive request failed ($code): ${body.take(200)}")

/** Google Drive REST v3, appDataFolder only. Plain HttpURLConnection, no client library. */
class DriveRestStore(private val accessToken: String) : DriveStore {

    @Serializable
    private data class FileList(val nextPageToken: String? = null, val files: List<DriveFile> = emptyList())

    @Serializable
    private data class DriveFile(val id: String, val name: String, val appProperties: Map<String, String> = emptyMap())

    override suspend fun list(): List<RemoteFile> = withContext(Dispatchers.IO) {
        val out = mutableListOf<RemoteFile>()
        var pageToken: String? = null
        do {
            val url = "$API/files?spaces=appDataFolder&pageSize=1000" +
                "&fields=${enc("nextPageToken,files(id,name,appProperties)")}" +
                (pageToken?.let { "&pageToken=${enc(it)}" } ?: "")
            val page = JotterJson.decodeFromString<FileList>(request("GET", url))
            page.files.mapTo(out) { RemoteFile(it.id, it.name, it.appProperties[UPDATED_AT]?.toLongOrNull()) }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        out
    }

    override suspend fun download(fileId: String): String = withContext(Dispatchers.IO) {
        request("GET", "$API/files/${enc(fileId)}?alt=media")
    }

    override suspend fun upload(name: String, fileId: String?, content: String, updatedAt: Long): String =
        withContext(Dispatchers.IO) {
            val metadata = buildJsonObject {
                if (fileId == null) {
                    put("name", name)
                    putJsonArray("parents") { add(JsonPrimitive("appDataFolder")) }
                }
                putJsonObject("appProperties") { put(UPDATED_AT, updatedAt.toString()) }
            }.toString()
            val boundary = "jotter-${UUID.randomUUID()}"
            val body = buildString {
                append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n")
                append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$content\r\n")
                append("--$boundary--")
            }.toByteArray()
            val url = if (fileId == null) "$UPLOAD/files?uploadType=multipart&fields=id"
            else "$UPLOAD/files/${enc(fileId)}?uploadType=multipart&fields=id"
            val response = request(if (fileId == null) "POST" else "PATCH", url, body, "multipart/related; boundary=$boundary")
            JotterJson.decodeFromString<FileId>(response).id
        }

    @Serializable
    private data class FileId(val id: String)

    private fun request(method: String, url: String, body: ByteArray? = null, contentType: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            // HttpURLConnection has no PATCH; Google APIs accept the override header.
            conn.requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw DriveHttpException(code, text)
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val UPDATED_AT = "updatedAt"
    }
}
