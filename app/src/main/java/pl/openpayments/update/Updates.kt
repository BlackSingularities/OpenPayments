package pl.openpayments.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import pl.openpayments.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class Release(val version: String, val apk: String, val checksum: String, val size: Long = 0)

/**
 * Aktualizacje przez redzikowski.com: SHA-256 z wydania, zgodność pakietu, numeru wersji
 * i certyfikatu podpisu z zainstalowaną aplikacją, instalacja przez PackageInstaller.
 */
class Updates(private val context: Context) {

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 60_000
            setRequestProperty("User-Agent", "OpenPayments/${BuildConfig.VERSION_NAME}")
        }

    private fun text(url: String): String {
        val c = open(url)
        try {
            check(c.responseCode in 200..299) { "HTTP ${c.responseCode}" }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    fun check(): Release? {
        val c = open("$API/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
        val body = try {
            if (c.responseCode == 404) return null
            check(c.responseCode in 200..299) { "Serwer aktualizacji odpowiedział HTTP ${c.responseCode}" }
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
        return parse(body, BuildConfig.VERSION_NAME)
    }

    fun download(release: Release, progress: (Float) -> Unit = {}): File {
        require(allowed(release.apk) && allowed(release.checksum)) { "Nieprawidłowe źródło aktualizacji" }
        val expected = text(release.checksum).trim().substringBefore(' ')
        require(expected.matches(Regex("[a-fA-F0-9]{64}"))) { "Brak prawidłowej sumy kontrolnej" }
        val folder = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val part = File(folder, "update.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val c = open(release.apk)
            try {
                check(c.responseCode in 200..299) { "Nie udało się pobrać aktualizacji (HTTP ${c.responseCode})" }
                val total = c.contentLengthLong.takeIf { it > 0 } ?: release.size
                c.inputStream.use { source ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024); var read = 0L
                        while (true) {
                            val n = source.read(buffer); if (n < 0) break
                            out.write(buffer, 0, n); digest.update(buffer, 0, n); read += n
                            if (total > 0) progress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally { c.disconnect() }
            check(digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, true)) { "Suma kontrolna pliku nie zgadza się" }
            verify(part)
            val apk = File(folder, APK_NAME)
            check(part.renameTo(apk)) { "Nie można zapisać aktualizacji" }
            return apk
        } finally { part.delete() }
    }

    /** Ten sam pakiet, wyższy versionCode i podpis tym samym certyfikatem (z obsługą rotacji klucza v3). */
    private fun verify(apk: File) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(apk.path, flags) ?: error("Pobrany plik nie jest prawidłową aplikacją")
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        check(archive.packageName == context.packageName) { "Aktualizacja dotyczy innej aplikacji (${archive.packageName})" }
        check(PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(current)) { "Pobrana wersja nie jest nowsza od zainstalowanej" }
        val installed = signers(current)
        val incoming = history(archive).ifEmpty { signers(archive) }
        check(installed.isNotEmpty() && incoming.isNotEmpty()) { "Nie można odczytać podpisu" }
        check(incoming.containsAll(installed)) { "Podpis aktualizacji nie zgadza się z zainstalowaną aplikacją. Zainstalowana wersja mogła pochodzić z innego źródła." }
    }

    fun install(apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            error("Zezwól OpenPayments na instalowanie aplikacji, wróć i naciśnij Zainstaluj ponownie")
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    companion object {
        const val API = "https://redzikowski.com/api/releases"
        const val ASSET_PREFIX = "https://redzikowski.com/api/projects/openpayments/asset/"
        const val APK_NAME = "OpenPayments.apk"
        const val ACTION_STATUS = "pl.openpayments.UPDATE_STATUS"

        fun allowed(url: String) = url.startsWith(ASSET_PREFIX)

        /** Odpowiedź w kształcie GitHub Releases → wydanie, o ile jest nowsze od [current]. */
        fun parse(json: String, current: String): Release? {
            val o = JSONObject(json)
            val version = o.getString("tag_name").removePrefix("v")
            if (!newer(version, current)) return null
            val assets = o.getJSONArray("assets")
            fun asset(name: String) = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name") == name } ?: error("Wydanie nie zawiera $name")
            val apk = asset(APK_NAME)
            return Release(version, apk.getString("browser_download_url"),
                asset("$APK_NAME.sha256").getString("browser_download_url"), apk.optLong("size"))
        }

        /** Porównanie numeryczne wersji 1.2.3 lub 1.2.3.45; wersje z dopiskami są pomijane. */
        fun newer(candidate: String, current: String): Boolean {
            if (!candidate.matches(Regex("\\d+(\\.\\d+){2,3}"))) return false
            val a = candidate.split('.').map { it.toLong() }
            val b = current.substringBefore('-').split('.').map { it.toLongOrNull() ?: return false }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        @Suppress("DEPRECATION")
        fun signers(info: PackageInfo): Set<String> = if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            val s = info.signingInfo!!
            (if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory.takeLast(1).toTypedArray())
                .map { sha(it.toByteArray()) }.toSet()
        } else info.signatures.orEmpty().map { sha(it.toByteArray()) }.toSet()

        private fun history(info: PackageInfo): Set<String> =
            if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null && !info.signingInfo!!.hasMultipleSigners())
                info.signingInfo!!.signingCertificateHistory.map { sha(it.toByteArray()) }.toSet() else emptySet()

        /** SHA-256 certyfikatu zainstalowanej aplikacji – do pokazania w „O aplikacji”. */
        fun installedCertificate(context: Context): String? = runCatching {
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
            signers(context.packageManager.getPackageInfo(context.packageName, flags)).firstOrNull()
        }.getOrNull()
    }
}

/** Status instalacji; gdy system wymaga potwierdzenia – pokazuje jego okno. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "kod $status"
                val hint = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
                    " Zainstalowana wersja ma inny podpis – odinstaluj ją raz i zainstaluj wydanie ze strony redzikowski.com." else ""
                Toast.makeText(context, "Aktualizacja nieudana: $message.$hint", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** Po aktualizacji system powiadamia nową wersję – otwieramy aplikację ponownie. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
    }
}
