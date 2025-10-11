package com.kolktech.linxshare

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.graphics.BitmapFactory
import android.view.Menu
import android.view.MenuItem
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import android.view.View
import java.util.concurrent.CountDownLatch
import androidx.core.content.IntentCompat
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import okio.source
import kotlin.concurrent.thread

class UploadActivity : AppCompatActivity() {
    @Serializable
    data class LinxResponseModel(
        val url: String
    )

    private var compatibleIntent = false
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var optionsMenu: Menu
    private lateinit var progressOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_upload)
        progressOverlay = findViewById(R.id.progress_overlay)
        ensureChannel()
        ensureNotificationPermission()

        val uploadSettingsFragment: UploadSettingsFragment = if (savedInstanceState == null) {
            val fragment = UploadSettingsFragment()
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.upload_settings_container, fragment, "UPLOAD_SETTINGS")
                .commit()
            fragment
        } else {
            supportFragmentManager.findFragmentByTag("UPLOAD_SETTINGS") as UploadSettingsFragment
        }

        when {
            intent?.action == Intent.ACTION_SEND -> {
                compatibleIntent = true

                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uri ->
                    uri.lastPathSegment?.let {
                        if (!it.contains('.')) {
                            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(intent.type)
                            uploadSettingsFragment.filename = "$it.$ext"
                        } else {
                            uploadSettingsFragment.filename = uri.lastPathSegment ?: ""
                        }
                    }
                }
            }
            intent?.action == Intent.ACTION_SEND_MULTIPLE -> {
                compatibleIntent = true
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        optionsMenu = menu
        menuInflater.inflate(R.menu.appbar, menu)
        menu.findItem(R.id.upload).isEnabled = compatibleIntent
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val uploadSettings = supportFragmentManager.findFragmentByTag("UPLOAD_SETTINGS") as UploadSettingsFragment

        if (item.itemId == R.id.upload) {
            item.isEnabled = false

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val linxUrl = sharedPreferences.getString("linx_url", "") ?: ""
            val apiKey = sharedPreferences.getString("api_key", "") ?: ""
            val deleteKey = uploadSettings.deleteKey
            val expiration = uploadSettings.expiration
            val randomizeFilename = uploadSettings.randomizeFilename
            val filename = uploadSettings.filename

            progressOverlay.visibility = View.VISIBLE

            if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
                handleSendMultiple(
                    intent,
                    linxUrl,
                    deleteKey,
                    apiKey,
                    expiration,
                    randomizeFilename,
                    filename
                )
            } else {
                handleSendImage(
                    intent,
                    linxUrl,
                    deleteKey,
                    apiKey,
                    expiration,
                    randomizeFilename,
                    filename
                )
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun handleSendImage(
        intent: Intent,
        linxUrl: String,
        deleteKey: String,
        apiKey: String,
        expiration: Int,
        randomizeFilename: Boolean,
        filename: String
    ) {
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uri ->
            thread(start = true) {
                val body = object : RequestBody() {
                    override fun contentType(): MediaType? {
                        return intent.type?.toMediaType()
                    }

                    override fun contentLength(): Long {
                        return -1
                    }

                    override fun writeTo(sink: BufferedSink) {
                        contentResolver.openInputStream(uri)?.source()?.use {
                            sink.writeAll(it)
                        }
                    }
                }

                val builder = Request.Builder()
                    .url("${linxUrl.trimEnd('/')}/upload/")
                    .addHeader("Accept", "application/json")
                    .addHeader("Linx-Delete-Key", deleteKey)
                    .addHeader("Linx-Expiry", expiration.toString())
                    .addHeader("Linx-Api-Key", apiKey)

                val request: Request = if (randomizeFilename) {
                    builder
                        .addHeader("Linx-Randomize", "yes")
                        .put(body)
                        .build()
                } else {
                    val formBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", filename, body)
                        .build()
                    builder
                        .post(formBody)
                        .build()
                }

                try {
                    client.newCall(request).execute().use { response ->
                        handleResponse(response)
                    }
                }
                catch (e: Exception) {
                    Log.e("MainActivity", "Request failed: $e")
                    handleFailure()
                }
            }
        }
    }

    private fun handleSendMultiple(
        intent: Intent,
        linxUrl: String,
        deleteKey: String,
        apiKey: String,
        expiration: Int,
        randomizeFilename: Boolean,
        filename: String
    ) {
        val uris: List<Uri> = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
        if (uris.isEmpty()) {
            handleFailure()
            return
        }

        thread(start = true) {
            val latch = CountDownLatch(uris.size)
            val results = mutableListOf<String?>()
            val lock = Any()

            uris.forEach { uri ->
                thread(start = true) {
                    val body = object : RequestBody() {
                        override fun contentType(): MediaType? {
                            return intent.type?.toMediaType()
                        }

                        override fun contentLength(): Long {
                            return -1
                        }

                        override fun writeTo(sink: BufferedSink) {
                            contentResolver.openInputStream(uri)?.source()?.use {
                                sink.writeAll(it)
                            }
                        }
                    }

                    val builder = Request.Builder()
                        .url("${linxUrl.trimEnd('/')}/upload/")
                        .addHeader("Accept", "application/json")
                        .addHeader("Linx-Delete-Key", deleteKey)
                        .addHeader("Linx-Expiry", expiration.toString())
                        .addHeader("Linx-Api-Key", apiKey)

                    val request: Request = if (randomizeFilename) {
                        builder
                            .addHeader("Linx-Randomize", "yes")
                            .put(body)
                            .build()
                    } else {
                        val name: String = if (uris.size == 1 && filename.isNotEmpty()) {
                            filename
                        } else {
                            val last = uri.lastPathSegment ?: "file"
                            if (last.contains('.')) last else {
                                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(intent.type)
                                if (!ext.isNullOrEmpty()) "$last.$ext" else last
                            }
                        }
                        val formBody = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("file", name, body)
                            .build()
                        builder
                            .post(formBody)
                            .build()
                    }

                    try {
                        client.newCall(request).execute().use { response ->
                            val bodyString = response.body?.string()
                            val url = if (response.isSuccessful && bodyString != null) {
                                val lr = json.decodeFromString<LinxResponseModel>(bodyString)
                                lr.url
                            } else null
                            synchronized(lock) { results.add(url) }
                        }
                    } catch (e: Exception) {
                        Log.e("UploadActivity", "Request failed: $e")
                        synchronized(lock) { results.add(null) }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()

            val successful = results.filterNotNull()
            if (successful.isNotEmpty() && successful.size == uris.size) {
                runOnUiThread {
                    progressOverlay.visibility = View.GONE
                    val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@UploadActivity)
                    val notifMulti = prefs.getBoolean("notif_multi_enable", true)
                    if (notifMulti) {
                        successful.forEach { showCopyNotification(it) }
                    }
                    finish()
                }
            } else {
                handleFailure()
            }
        }
    }

    private fun handleResponse(response: Response) {
        val body = response.body?.string()
        if (response.isSuccessful && body != null) {
            val lr = json.decodeFromString<LinxResponseModel>(body)
            handleSuccess(lr.url)
        } else {
            handleFailure()
        }
    }

    private fun handleSuccess(url: String) {
        runOnUiThread {
            progressOverlay.visibility = View.GONE
            // For single-file uploads: copy immediately and optionally show notification per setting
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("linx url", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext, "Copied $url to clipboard", Toast.LENGTH_SHORT).show()
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            val notifSingle = prefs.getBoolean("notif_single_enable", false)
            if (notifSingle) {
                showCopyNotification(url)
            }
            finish()
        }
    }

    private fun handleFailure() {
        runOnUiThread {
            progressOverlay.visibility = View.GONE

            val toast = Toast.makeText(
                applicationContext,
                "Failed to upload to linx-server, check settings!",
                Toast.LENGTH_LONG
            )
            toast.show()

            optionsMenu.findItem(R.id.upload).isEnabled = true
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val id = "linx_uploads"
            if (mgr.getNotificationChannel(id) == null) {
                val channel = NotificationChannel(id, "Linx uploads", NotificationManager.IMPORTANCE_DEFAULT)
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private var nextNotificationId: Int = 1000

    private fun showCopyNotification(url: String) {
        if (!hasNotificationPermission()) return
        ensureChannel()
        val intent = Intent(this, CopyUrlReceiver::class.java).putExtra("url", url)
        val pending = PendingIntentCompat.getBroadcast(
            this,
            url.hashCode(),
            intent,
            0,
            false
        )
        val notif = NotificationCompat.Builder(this, "linx_uploads")
            .setSmallIcon(R.mipmap.linxshare)
            .setContentTitle("Upload complete")
            .setContentText(url)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.linxshare))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(this).notify(nextNotificationId++, notif)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
