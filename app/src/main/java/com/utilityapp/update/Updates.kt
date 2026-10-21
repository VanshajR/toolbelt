package com.utilityapp.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.utilityapp.BuildConfig
import com.utilityapp.MainActivity
import com.utilityapp.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** A published release on GitHub that's newer than this build. */
data class Release(val version: String, val apkUrl: String, val notes: String, val pageUrl: String)

/**
 * Self-updating from GitHub Releases: a daily background check that notifies when a newer
 * version is out, and an in-app download that hands the APK to Android's installer. Android
 * only accepts it if it's signed with the same key as the installed app.
 */
object Updates {
    private const val TAG = "Updates"
    private const val JOB_ID = 4201
    private const val CHANNEL = "updates"
    const val EXTRA_ROUTE = "route"

    // Settings

    fun autoCheck(context: Context) = prefs(context).getBoolean("auto_check", true)

    fun setAutoCheck(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("auto_check", enabled).apply()
        schedule(context)
    }

    /** The newest release found by the last check, if it's newer than this build. */
    fun known(context: Context): Release? {
        val p = prefs(context)
        val version = p.getString("version", null) ?: return null
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return null
        return Release(version, p.getString("apk", "")!!, p.getString("notes", "")!!, p.getString("page", "")!!)
    }

    // Checking

    /** Asks GitHub for the latest release. Blocking; call off the main thread. */
    fun check(context: Context): Release? {
        val connection = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null // nothing published yet
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url") ?: return null
            val release = Release(version, apk, json.optString("body"), json.getString("html_url"))
            prefs(context).edit()
                .putString("version", release.version)
                .putString("apk", release.apkUrl)
                .putString("notes", release.notes)
                .putString("page", release.pageUrl)
                .apply()
            return release.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
        } finally {
            connection.disconnect()
        }
    }

    /** Compares dotted versions numerically: 1.10.0 is newer than 1.9.2. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    // Background check

    /** Arms (or cancels) the daily check. Safe to call repeatedly. */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!autoCheck(context)) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .setPersisted(true)
                .build(),
        )
    }

    fun notifyAvailable(context: Context, release: Release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ROUTE, "settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        notifications.notify(
            JOB_ID,
            android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_sound)
                .setContentTitle("Toolbelt ${release.version} is available")
                .setContentText("Tap to update")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    // Installing

    /** Downloads the release APK, reporting progress 0..1. Blocking; call off the main thread. */
    fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File {
        val file = File(context.cacheDir, "update.apk")
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            val total = connection.contentLengthLong.takeIf { it > 0 }
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        total?.let { onProgress(done.toFloat() / it) }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return file
    }

    /** Hands the APK to Android's installer. On Android 12+ it may go through without a prompt. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("toolbelt.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                // Mutable so the installer can attach its result.
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
            )
            session.commit(callback.intentSender)
        }
    }

    /** Whether the user has let Toolbelt install apps (Android asks once). */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)

    internal fun log(message: String, e: Throwable? = null) = Log.w(TAG, message, e)
}

class UpdateCheckJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                Updates.check(this)?.let { Updates.notifyAvailable(this, it) }
            } catch (e: Exception) {
                Updates.log("Update check failed", e)
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}

/** Receives the installer's verdict; shows Android's confirmation screen when it asks for one. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Updates.log("Install failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
