package com.example.core.location

/**
 * Model data lokasi perangkat nyata yang diperoleh dari Location Provider Android.
 * DILARANG menyuntikkan nilai hardcoded / dummy ke model ini di production code.
 */
data class DeviceLocation(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float? = null,
  val timeMillis: Long,
  val provider: String? = null,
  val altitudeMeters: Double? = null,
  val speedMps: Float? = null,
  val isFromCache: Boolean = false,
  val isMock: Boolean = false,
  val isRealDeviceVerified: Boolean = false,
  val runtimeEnvironment: RuntimeEnvironment = RuntimeEnvironment.UNKNOWN,
  val verificationLevel: LocationVerificationLevel = resolveVerificationLevel(
    isMock,
    isFromCache,
    runtimeEnvironment,
    isRealDeviceVerified
  )
) {
  companion object {
    fun resolveVerificationLevel(
      isMock: Boolean,
      isFromCache: Boolean,
      runtimeEnvironment: RuntimeEnvironment,
      isRealDeviceVerified: Boolean
    ): LocationVerificationLevel = when {
      isMock -> LocationVerificationLevel.MOCK
      runtimeEnvironment == RuntimeEnvironment.EMULATOR ||
        runtimeEnvironment == RuntimeEnvironment.VIRTUAL_DEVICE ||
        runtimeEnvironment == RuntimeEnvironment.CLOUD_CONTAINER -> LocationVerificationLevel.VIRTUAL
      isFromCache -> LocationVerificationLevel.CACHED
      isRealDeviceVerified && runtimeEnvironment == RuntimeEnvironment.REAL_PHYSICAL_DEVICE -> LocationVerificationLevel.REAL_DEVICE_VERIFIED
      runtimeEnvironment == RuntimeEnvironment.REAL_PHYSICAL_DEVICE -> LocationVerificationLevel.REAL_DEVICE_UNVERIFIED
      runtimeEnvironment == RuntimeEnvironment.UNKNOWN -> LocationVerificationLevel.UNKNOWN
      else -> LocationVerificationLevel.UNVERIFIED
    }
  }
  /**
   * Section 5 & 6 Prompt 005B: Provider & Location Source Metadata.
   * Format:
   * - MOCK / VIRTUAL jika isMock
   * - CACHED (PROVIDER) jika isFromCache
   * - PROVIDER (GPS, NETWORK, FUSED, PASSIVE, UNKNOWN)
   */
  val locationSource: String
    get() = when {
      isMock -> "MOCK / VIRTUAL"
      isFromCache -> "CACHED (${provider?.uppercase(java.util.Locale.ROOT) ?: "UNKNOWN"})"
      provider.isNullOrBlank() -> "UNKNOWN"
      else -> provider.uppercase(java.util.Locale.ROOT)
    }

  /**
   * Section 15 Prompt 005B: Perhitungan Umur Lokasi (Location Age).
   */
  fun getLocationAgeMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    if (timeMillis <= 0) return 0L
    return (nowMillis - timeMillis).coerceAtLeast(0L)
  }

  fun getLocationAgeDisplay(nowMillis: Long = System.currentTimeMillis()): String {
    if (timeMillis <= 0) return "Waktu tidak valid"
    val diffSeconds = getLocationAgeMillis(nowMillis) / 1000
    return when {
      diffSeconds < 5 -> "baru saja"
      diffSeconds < 60 -> "$diffSeconds detik lalu"
      diffSeconds < 3600 -> "${diffSeconds / 60} menit lalu"
      diffSeconds < 86400 -> "${diffSeconds / 3600} jam lalu"
      else -> "${diffSeconds / 86400} hari lalu"
    }
  }

  /**
   * Section 16 Prompt 005B: Pemeriksaan Stale Location.
   * Lokasi fix dianggap stale jika berumur lebih dari 15 menit.
   */
  fun isStale(nowMillis: Long = System.currentTimeMillis(), staleThresholdMillis: Long = 15 * 60 * 1000L): Boolean {
    if (timeMillis <= 0) return true
    return getLocationAgeMillis(nowMillis) > staleThresholdMillis
  }

  /**
   * Section 10 Prompt 005A / 005B: Coordinate Range Sanity Check
   * PERHATIAN: Validasi rentang matematis BUKANLAH bukti verifikasi perangkat fisik nyata!
   */
  fun isValid(): Boolean {
    return latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        (accuracyMeters == null || accuracyMeters >= 0f) &&
        timeMillis > 0 &&
        !latitude.isNaN() &&
        !longitude.isNaN() &&
        !latitude.isInfinite() &&
        !longitude.isInfinite()
  }
}

