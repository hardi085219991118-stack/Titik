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
  val isFromCache: Boolean = false
) {
  /**
   * Section 17 Prompt 004: Location Sanity Check
   * Validasi umum geografis dan fisik, BUKAN berdasarkan wilayah tertentu.
   */
  fun isValid(): Boolean {
    return latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        (accuracyMeters == null || accuracyMeters >= 0f) &&
        timeMillis > 0
  }
}
