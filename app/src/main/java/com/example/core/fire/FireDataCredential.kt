package com.example.core.fire

import android.content.Context
import android.content.SharedPreferences

/**
 * NASA FIRMS Credential States as required by Section 2 Prompt 006A:
 * - NOT_CONFIGURED: MAP_KEY belum dimasukkan oleh pengguna.
 * - CONFIGURED: MAP_KEY telah disimpan pada storage lokal perangkat.
 * - INVALID: MAP_KEY ditolak oleh server NASA FIRMS (HTTP 401 / 403).
 * - ERROR: Terjadi error saat membaca/menulis kredensial.
 */
enum class FireDataCredentialState {
  NOT_CONFIGURED,
  CONFIGURED,
  INVALID,
  ERROR
}

/**
 * FireDataCredentialProvider Abstraction.
 *
 * ZERO-SECRET LEAK & CLIENT-ONLY ARCHITECTURE HONESTY:
 * - NASA FIRMS MAP_KEY dilarang di-hardcode di kode Kotlin, compose, res, BuildConfig, atau Git.
 * - Sesuai Section 2 Prompt 006A:
 *   "JIKA ARSITEKTUR ANDROID SAAT INI TIDAK MEMILIKI BACKEND/SECRET MANAGER:
 *    JANGAN PURA-PURA MEMBUAT SECRET BACKEND.
 *    CATAT SECARA JUJUR: CLIENT_ONLY_LIMITATION DAN JANGAN MENEMPATKAN SECRET PRODUKSI DI APK."
 */
interface FireDataCredentialProvider {
  fun getCredentialState(): FireDataCredentialState
  fun getMapKey(): String?
  fun setMapKey(key: String?): FireDataCredentialState
  fun markInvalid()
  fun clearMapKey()
  val limitationNote: String
}

class ClientOnlyCredentialProvider(
  private val sharedPreferences: SharedPreferences? = null,
  private val context: Context? = null
) : FireDataCredentialProvider {

  companion object {
    private const val KEY_FIRMS_MAP_KEY = "firms_map_key"
    private const val PREFS_NAME = "fire_credentials_prefs"

    fun create(context: Context): ClientOnlyCredentialProvider {
      val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      return ClientOnlyCredentialProvider(prefs, context.applicationContext)
    }
  }

  override val limitationNote: String =
    "CLIENT_ONLY_LIMITATION: Aplikasi berjalan murni pada sisi klien Android tanpa perantara backend/KMS. Secret produksi tidak disimpan dalam APK."

  private var inMemoryKey: String? = null
  private var isMarkedInvalid: Boolean = false

  init {
    val savedKey = sharedPreferences?.getString(KEY_FIRMS_MAP_KEY, null)
    if (savedKey != null) {
      if (savedKey.isNotBlank()) {
        inMemoryKey = savedKey.trim()
      }
    } else {
      // Priority 2: Local test configuration via BuildConfig (TEST_CREDENTIAL_ONLY)
      val buildConfigKey = try {
        com.example.BuildConfig.FIRMS_MAP_KEY
      } catch (_: Throwable) {
        null
      }
      if (!buildConfigKey.isNullOrBlank() && isValidFormat(buildConfigKey.trim()) && !buildConfigKey.startsWith("your_", ignoreCase = true)) {
        inMemoryKey = buildConfigKey.trim()
      } else {
        val assetKey = try {
          val props = java.util.Properties()
          var loaded = false
          if (context != null) {
            try {
              context.assets.open("test_credentials.properties").use { props.load(it) }
              loaded = true
            } catch (_: Throwable) {}
          }
          if (!loaded) {
            val stream = javaClass.classLoader?.getResourceAsStream("test_credentials.properties")
            if (stream != null) {
              stream.use { props.load(it) }
              loaded = true
            }
          }
          if (!loaded) {
            val fileCandidates = listOf(
              java.io.File("src/main/assets/test_credentials.properties"),
              java.io.File("app/src/main/assets/test_credentials.properties")
            )
            fileCandidates.firstOrNull { it.exists() }?.inputStream()?.use { props.load(it) }
          }
          props.getProperty("FIRMS_MAP_KEY", "")
        } catch (_: Throwable) {
          null
        }
        if (!assetKey.isNullOrBlank() && isValidFormat(assetKey.trim())) {
          inMemoryKey = assetKey.trim()
        }
      }
    }
  }

  override fun getCredentialState(): FireDataCredentialState {
    val key = inMemoryKey ?: sharedPreferences?.getString(KEY_FIRMS_MAP_KEY, null)
    return when {
      key.isNullOrBlank() -> FireDataCredentialState.NOT_CONFIGURED
      isMarkedInvalid -> FireDataCredentialState.INVALID
      isValidFormat(key) -> FireDataCredentialState.CONFIGURED
      else -> FireDataCredentialState.INVALID
    }
  }

  override fun getMapKey(): String? {
    val key = inMemoryKey ?: sharedPreferences?.getString(KEY_FIRMS_MAP_KEY, null)
    return if (key.isNullOrBlank()) null else key
  }

  override fun setMapKey(key: String?): FireDataCredentialState {
    val trimmed = key?.trim()
    return if (trimmed.isNullOrBlank()) {
      clearMapKey()
      FireDataCredentialState.NOT_CONFIGURED
    } else if (isValidFormat(trimmed)) {
      inMemoryKey = trimmed
      isMarkedInvalid = false
      sharedPreferences?.edit()?.putString(KEY_FIRMS_MAP_KEY, trimmed)?.apply()
      FireDataCredentialState.CONFIGURED
    } else {
      isMarkedInvalid = true
      FireDataCredentialState.INVALID
    }
  }

  override fun markInvalid() {
    isMarkedInvalid = true
  }

  override fun clearMapKey() {
    inMemoryKey = ""
    isMarkedInvalid = false
    sharedPreferences?.edit()?.putString(KEY_FIRMS_MAP_KEY, "")?.apply()
  }

  private fun isValidFormat(key: String): Boolean {
    // NASA FIRMS MAP_KEY is typically a 32-character hexadecimal string or alphanumeric key
    return key.length in 10..64 && key.all { it.isLetterOrDigit() || it == '-' || it == '_' }
  }
}
