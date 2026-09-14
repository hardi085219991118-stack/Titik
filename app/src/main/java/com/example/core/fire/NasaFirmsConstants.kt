package com.example.core.fire

/**
 * Official NASA FIRMS API Constants adhering strictly to NASA documentation.
 * Verified via NASA FIRMS Web Services Documentation:
 * https://firms.modaps.eosdis.nasa.gov/api/
 *
 * SENSOR PRIORITY (Section 6 Prompt 006A):
 * 1. VIIRS NOAA-21 (Primary)
 * 2. VIIRS NOAA-20 (Secondary)
 * 3. VIIRS Suomi-NPP (Tertiary)
 * 4. MODIS (Fallback / Supporting)
 */
object NasaFirmsConstants {

  const val BASE_URL = "https://firms.modaps.eosdis.nasa.gov/"

  // Sensor Sources (Official NASA FIRMS source keys)
  const val SENSOR_VIIRS_NOAA21 = "VIIRS_NOAA21_NRT"
  const val SENSOR_VIIRS_NOAA20 = "VIIRS_NOAA20_NRT"
  const val SENSOR_VIIRS_SNPP = "VIIRS_SNPP_NRT"
  const val SENSOR_MODIS = "MODIS_NRT"

  // Ordered Sensor Priority List
  val SENSOR_PRIORITY = listOf(
    SENSOR_VIIRS_NOAA21,
    SENSOR_VIIRS_NOAA20,
    SENSOR_VIIRS_SNPP,
    SENSOR_MODIS
  )

  // NASA FIRMS Rate Limits & Policies
  // Typically 5000 transactions per 10 minutes
  const val MIN_REQUEST_INTERVAL_MS = 30_000L // 30 seconds cooldown between manual calls
  const val RATE_LIMIT_BACKOFF_BASE_MS = 60_000L // 60 seconds backoff for HTTP 429
  const val CACHE_EXPIRY_MS = 15 * 60 * 1000L // 15 minutes cache freshness window

  // Hardi Mantangai reference query bounding box:
  // Note: Section 7 forbids hardcoded fire coordinates. Query bounding box is only for area bounds.
  // Central Kalimantan / Hardi Mantangai region: West 113.5, South -3.5, East 115.0, North -2.0
  const val DEFAULT_MANTHANGAI_BBOX = "113.5,-3.5,115.0,-2.0"
  const val DEFAULT_COUNTRY_CODE = "IDN"
}
