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
  val isRealDeviceVerified: Boolean = false
) {
  /**
   * Section 9 Prompt 005A: Location Source Metadata.
   * Menampilkan asal provider secara eksplisit dan jujur:
   * GPS, NETWORK, PASSIVE, FUSED, CACHED (...), MOCK, atau UNKNOWN.
   */
  val locationSource: String
    get() = when {
      isMock -> "MOCK_PROVIDER (UNVERIFIED)"
      isFromCache -> "CACHED (${provider?.uppercase(java.util.Locale.ROOT) ?: "UNKNOWN"})"
      provider.isNullOrBlank() -> "UNKNOWN"
      else -> provider.uppercase(java.util.Locale.ROOT)
    }

  /**
   * Section 10 Prompt 005A: Coordinate Range Sanity Check
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
