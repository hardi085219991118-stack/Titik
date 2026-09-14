package com.example.core.fire

/**
 * Real Hotspot Data Record strictly holding authentic fields from NASA FIRMS API.
 *
 * ZERO-DUMMY MANDATE:
 * - Field values strictly come from source response.
 * - Optional fields are nullable if not provided by sensor/satellite.
 * - acquisitionTimestampMillis is strictly derived from acqDate and acqTime UTC.
 * - System.currentTimeMillis() is NEVER used as acquisition timestamp.
 * - Coordinates must pass strict validation (-90..90, -180..180, not NaN, not Infinite).
 */
data class FireDataRecord(
  val latitude: Double,
  val longitude: Double,
  val brightTi4: Double? = null,
  val brightTi5: Double? = null,
  val scan: Double? = null,
  val track: Double? = null,
  val acqDate: String,                 // e.g. "2026-09-14"
  val acqTime: String,                 // e.g. "0410" (04:10 UTC)
  val satellite: String,               // e.g. "N21", "N20", "N", "Aqua", "Terra"
  val instrument: String,              // e.g. "VIIRS", "MODIS"
  val confidence: String? = null,      // e.g. "l", "n", "h" (VIIRS) or percentage string "85" (MODIS)
  val version: String? = null,         // e.g. "2.0NRT"
  val frp: Double? = null,             // Fire Radiative Power (MW)
  val dayNight: String? = null,        // "D" (Day) or "N" (Night)
  val acquisitionTimestampMillis: Long? = null, // Derived from acqDate & acqTime UTC only
  val rawLine: String? = null
)
