package com.kolktech.linxshare

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.ByteArrayOutputStream
import java.net.URI

sealed class UploadResult {
    object Success : UploadResult()
    data class Failure(val message: String) : UploadResult()
}

private const val NOTIFICATION_CHANNEL_ID = "linx_uploads"

class UploadCoordinator(
    context: Context,
    private val settingsRepository: SettingsRepository
) {
    @Serializable
    private data class LinxResponseModel(
        val url: String
    )

    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var nextNotificationId: Int = 1000

    suspend fun run(intent: Intent, state: UploadUiState): UploadResult {
        return try {
            val settings = settingsRepository.getSnapshot()
            val linxUrl = settings.linxUrl
            val apiKey = settings.apiKey
            val expiration = state.expirationValue.toIntOrNull() ?: 0

            val urls = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                uploadMultiple(
                    intent = intent,
                    linxUrl = linxUrl,
                    deleteKey = state.deleteKey,
                    apiKey = apiKey,
                    expiration = expiration,
                    randomizeFilename = state.randomizeFilename,
                    filename = state.filename,
                    convertHeicToJpeg = state.convertHeicToJpeg
                )
            } else {
                uploadSingle(
                    intent = intent,
                    linxUrl = linxUrl,
                    deleteKey = state.deleteKey,
                    apiKey = apiKey,
                    expiration = expiration,
                    randomizeFilename = state.randomizeFilename,
                    filename = state.filename,
                    convertHeicToJpeg = state.convertHeicToJpeg
                )?.let { listOf(it) }
            }

            if (urls.isNullOrEmpty()) {
                return UploadResult.Failure(appContext.getString(R.string.upload_failed))
            }

            if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                if (settings.notifMulti) {
                    urls.forEach { showCopyNotification(it) }
                }
                return UploadResult.Success
            }

            val url = urls.first()
            copyToClipboard(url)
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, appContext.getString(R.string.copied_to_clipboard, url), Toast.LENGTH_SHORT).show()
            }

            if (settings.notifSingle) {
                showCopyNotification(url)
            }

            UploadResult.Success
        } catch (e: Exception) {
            Log.e("UploadCoordinator", "Upload failed", e)
            UploadResult.Failure(appContext.getString(R.string.upload_failed))
        }
    }

    private suspend fun uploadSingle(
        intent: Intent,
        linxUrl: String,
        deleteKey: String,
        apiKey: String,
        expiration: Int,
        randomizeFilename: Boolean,
        filename: String,
        convertHeicToJpeg: Boolean
    ): String? {
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return null
        return uploadSingleUri(
            uri = uri,
            fallbackMimeType = intent.type,
            linxUrl = linxUrl,
            deleteKey = deleteKey,
            apiKey = apiKey,
            expiration = expiration,
            randomizeFilename = randomizeFilename,
            explicitFilename = filename,
            convertHeicToJpeg = convertHeicToJpeg,
            totalCount = 1
        )
    }

    private suspend fun uploadMultiple(
        intent: Intent,
        linxUrl: String,
        deleteKey: String,
        apiKey: String,
        expiration: Int,
        randomizeFilename: Boolean,
        filename: String,
        convertHeicToJpeg: Boolean
    ): List<String>? {
        val uris: List<Uri> =
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
        if (uris.isEmpty()) {
            return null
        }

        val uploaded = coroutineScope {
            uris.map { uri ->
                async {
                    uploadSingleUri(
                        uri = uri,
                        fallbackMimeType = intent.type,
                        linxUrl = linxUrl,
                        deleteKey = deleteKey,
                        apiKey = apiKey,
                        expiration = expiration,
                        randomizeFilename = randomizeFilename,
                        explicitFilename = filename,
                        convertHeicToJpeg = convertHeicToJpeg,
                        totalCount = uris.size
                    )
                }
            }.awaitAll()
        }

        return if (uploaded.all { it != null }) uploaded.filterNotNull() else null
    }

    private suspend fun uploadSingleUri(
        uri: Uri,
        fallbackMimeType: String?,
        linxUrl: String,
        deleteKey: String,
        apiKey: String,
        expiration: Int,
        randomizeFilename: Boolean,
        explicitFilename: String,
        convertHeicToJpeg: Boolean,
        totalCount: Int
    ): String? = withContext(Dispatchers.IO) {
        val sourceMimeType = appContext.contentResolver.getType(uri) ?: fallbackMimeType
        val body = createRequestBody(uri, sourceMimeType, convertHeicToJpeg)
        val convertedToJpeg = shouldConvertHeicToJpeg(uri, sourceMimeType, convertHeicToJpeg) &&
            body.contentType()?.toString() == "image/jpeg"

        val builder = Request.Builder()
            .url("${parseBaseUrlAndAuth(linxUrl).first.trimEnd('/')}/upload/")
            .addHeader("Accept", "application/json")
            .addHeader("Linx-Delete-Key", deleteKey)
            .addHeader("Linx-Expiry", expiration.toString())
            .addHeader("Linx-Api-Key", apiKey)
        parseBaseUrlAndAuth(linxUrl).second?.let { builder.addHeader("Authorization", it) }

        val request: Request = if (randomizeFilename) {
            builder
                .addHeader("Linx-Randomize", "yes")
                .put(body)
                .build()
        } else {
            val name: String = if (totalCount == 1 && explicitFilename.isNotEmpty()) {
                if (convertedToJpeg) withJpegExtension(explicitFilename) else explicitFilename
            } else {
                val last = uri.lastPathSegment ?: "file"
                val resolved = if (last.contains('.')) last else {
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(fallbackMimeType)
                    if (!ext.isNullOrEmpty()) "$last.$ext" else last
                }
                if (convertedToJpeg) withJpegExtension(resolved) else resolved
            }

            val formBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", name, body)
                .build()
            builder.post(formBody).build()
        }

        try {
            client.newCall(request).execute().use { response ->
                parseResponseUrl(response)
            }
        } catch (e: Exception) {
            Log.e("UploadCoordinator", "Request failed", e)
            null
        }
    }

    private fun parseResponseUrl(response: Response): String? {
        val bodyString = response.body?.string()
        return if (response.isSuccessful && bodyString != null) {
            json.decodeFromString<LinxResponseModel>(bodyString).url
        } else {
            null
        }
    }

    private fun createRequestBody(uri: Uri, sourceMimeType: String?, convertHeicToJpeg: Boolean): RequestBody {
        if (shouldConvertHeicToJpeg(uri, sourceMimeType, convertHeicToJpeg)) {
            convertUriToJpegBytes(uri)?.let { bytes ->
                return bytes.toRequestBody("image/jpeg".toMediaType())
            }
        }

        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return sourceMimeType?.toMediaTypeOrNull()
            }

            override fun contentLength(): Long {
                return -1
            }

            override fun writeTo(sink: BufferedSink) {
                appContext.contentResolver.openInputStream(uri)?.source()?.use {
                    sink.writeAll(it)
                }
            }
        }
    }

    private fun shouldConvertHeicToJpeg(uri: Uri, sourceMimeType: String?, convertHeicToJpeg: Boolean): Boolean {
        if (!convertHeicToJpeg) return false

        val mime = sourceMimeType?.lowercase()
        if (mime == "image/heic" || mime == "image/heif" || mime == "image/heic-sequence" || mime == "image/heif-sequence") {
            return true
        }

        val path = uri.lastPathSegment?.lowercase() ?: return false
        return path.endsWith(".heic") || path.endsWith(".heif")
    }

    private fun convertUriToJpegBytes(uri: Uri): ByteArray? {
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return null
                val output = ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, output)
                output.toByteArray()
            }
        } catch (e: Exception) {
            Log.w("UploadCoordinator", "Failed to convert HEIC/HEIF to JPEG for $uri", e)
            null
        }
    }

    private fun withJpegExtension(name: String): String {
        if (name.isEmpty()) return "upload.jpg"
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex <= 0) "$name.jpg" else "${name.substring(0, dotIndex)}.jpg"
    }

    private fun copyToClipboard(url: String) {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(appContext.getString(R.string.clipboard_label), url)
        clipboard.setPrimaryClip(clip)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (mgr.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    appContext.getString(R.string.linx_uploads_channel_name),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
                mgr.createNotificationChannel(channel)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showCopyNotification(url: String) {
        if (!hasNotificationPermission()) return
        ensureChannel()
        val intent = Intent(appContext, CopyUrlReceiver::class.java).putExtra("url", url)
        val pending = PendingIntentCompat.getBroadcast(
            appContext,
            url.hashCode(),
            intent,
            0,
            false
        )
        val notif = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.linxshare)
            .setContentTitle(appContext.getString(R.string.upload_complete))
            .setContentText(url)
            .setLargeIcon(BitmapFactory.decodeResource(appContext.resources, R.mipmap.linxshare))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(appContext).notify(nextNotificationId++, notif)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun parseBaseUrlAndAuth(url: String): Pair<String, String?> {
        return try {
            val uri = URI(url)
            val userInfo = uri.userInfo
            val cleaned = URI(uri.scheme, null, uri.host, uri.port, uri.rawPath, uri.rawQuery, uri.rawFragment).toString()
            val auth = if (!userInfo.isNullOrEmpty()) {
                val value = if (userInfo.contains(":")) userInfo else "$userInfo:"
                "Basic " + Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            } else null
            Pair(cleaned, auth)
        } catch (e: Exception) {
            Pair(url, null)
        }
    }
}
