package com.example.core.fire

import com.example.core.logging.AppError
import com.example.core.logging.AppLogger
import com.example.core.logging.ErrorType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Robust, zero-dummy CSV Parser for NASA FIRMS responses.
 *
 * Adheres strictly to Section 12 (Response Validation) and Section 4 (Acquisition Time):
 * - Validates latitude (-90..90, not NaN, not Infinite)
 * - Validates longitude (-180..180, not NaN, not Infinite)
 * - Validates acq_date and acq_time strictly in UTC
 * - Optional fields (frp, brightTi4, brightTi5, confidence) are nullable
 * - Rejects malformed rows and logs them into AppLogger without throwing
 * - NEVER synthesizes or fixes corrupted records with artificial values.
 */
object FireDataParser {

  data class ParseResult(
    val records: List<FireDataRecord>,
    val rawCount: Int,
    val validCount: Int,
    val invalidCount: Int
  )

  fun parseCsv(
    csvContent: String,
    defaultInstrument: String = "VIIRS",
    defaultSatellite: String = "NOAA-21"
  ): ParseResult {
    val trimmed = csvContent.trim()
    if (trimmed.isEmpty()) {
      return ParseResult(emptyList(), 0, 0, 0)
    }

    val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) {
      return ParseResult(emptyList(), 0, 0, 0)
    }

    // Check if the response is an error message rather than CSV
    val firstLine = lines.first()
    if (firstLine.contains("Invalid MAP_KEY", ignoreCase = true) ||
      firstLine.contains("Unauthorized", ignoreCase = true) ||
      firstLine.contains("Error:", ignoreCase = true) ||
      firstLine.contains("Bad Request", ignoreCase = true)
    ) {
      throw IllegalArgumentException("NASA FIRMS API Error Response: $firstLine")
    }

    // Header line
    val headers = parseCsvLine(firstLine).map { it.lowercase().trim() }
    val latIndex = headers.indexOf("latitude")
    val lonIndex = headers.indexOf("longitude")

    if (latIndex == -1 || lonIndex == -1) {
      throw IllegalArgumentException("Header CSV tidak valid: kolom latitude dan/atau longitude tidak ditemukan di '$firstLine'")
    }

    val brightTi4Index = headers.indexOfFirst { it == "bright_ti4" || it == "brightness" }
    val brightTi5Index = headers.indexOfFirst { it == "bright_ti5" || it == "bright_t31" }
    val scanIndex = headers.indexOf("scan")
    val trackIndex = headers.indexOf("track")
    val acqDateIndex = headers.indexOf("acq_date")
    val acqTimeIndex = headers.indexOf("acq_time")
    val satelliteIndex = headers.indexOf("satellite")
    val instrumentIndex = headers.indexOf("instrument")
    val confidenceIndex = headers.indexOf("confidence")
    val versionIndex = headers.indexOf("version")
    val frpIndex = headers.indexOf("frp")
    val dayNightIndex = headers.indexOfFirst { it == "daynight" || it == "day_night" }

    val dataLines = lines.drop(1)
    val rawCount = dataLines.size
    val validRecords = mutableListOf<FireDataRecord>()
    var invalidCount = 0

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
      isLenient = false
    }

    for (line in dataLines) {
      val tokens = parseCsvLine(line)
      if (tokens.size <= maxOf(latIndex, lonIndex)) {
        invalidCount++
        logInvalidRow(line, "Jumlah kolom tidak mencukupi untuk koordinat")
        continue
      }

      val latStr = tokens.getOrNull(latIndex)
      val lonStr = tokens.getOrNull(lonIndex)
      val lat = latStr?.toDoubleOrNull()
      val lon = lonStr?.toDoubleOrNull()

      // Section 12 Coordinate Validation
      if (lat == null || lon == null ||
        lat.isNaN() || lat.isInfinite() || lat < -90.0 || lat > 90.0 ||
        lon.isNaN() || lon.isInfinite() || lon < -180.0 || lon > 180.0
      ) {
        invalidCount++
        logInvalidRow(line, "Koordinat di luar rentang valid: lat=$latStr, lon=$lonStr")
        continue
      }

      val acqDate = if (acqDateIndex != -1) tokens.getOrNull(acqDateIndex)?.trim().orEmpty() else ""
      val acqTime = if (acqTimeIndex != -1) tokens.getOrNull(acqTimeIndex)?.trim().orEmpty() else ""

      // Validate acquisition date format
      if (!isValidDateString(acqDate)) {
        invalidCount++
        logInvalidRow(line, "acq_date tidak valid: '$acqDate'")
        continue
      }

      // Validate acquisition time format (e.g. "0410", "410", or "04:10")
      if (!isValidTimeString(acqTime)) {
        invalidCount++
        logInvalidRow(line, "acq_time tidak valid: '$acqTime'")
        continue
      }

      // Derive UTC acquisition timestamp
      val acqTimestampMillis = parseAcqTimestamp(acqDate, acqTime, dateFormat)
      if (acqTimestampMillis == null) {
        invalidCount++
        logInvalidRow(line, "Gagal mengonversi acq_date + acq_time ke UTC timestamp: $acqDate $acqTime")
        continue
      }

      val brightTi4 = if (brightTi4Index != -1) tokens.getOrNull(brightTi4Index)?.toDoubleOrNull() else null
      val brightTi5 = if (brightTi5Index != -1) tokens.getOrNull(brightTi5Index)?.toDoubleOrNull() else null
      val scan = if (scanIndex != -1) tokens.getOrNull(scanIndex)?.toDoubleOrNull() else null
      val track = if (trackIndex != -1) tokens.getOrNull(trackIndex)?.toDoubleOrNull() else null
      val satelliteRaw = if (satelliteIndex != -1) tokens.getOrNull(satelliteIndex)?.trim() else null
      val instrumentRaw = if (instrumentIndex != -1) tokens.getOrNull(instrumentIndex)?.trim() else null
      val confidence = if (confidenceIndex != -1) tokens.getOrNull(confidenceIndex)?.trim() else null
      val version = if (versionIndex != -1) tokens.getOrNull(versionIndex)?.trim() else null
      val frp = if (frpIndex != -1) {
        val f = tokens.getOrNull(frpIndex)?.toDoubleOrNull()
        if (f != null && !f.isNaN() && !f.isInfinite()) f else null
      } else null
      val dayNight = if (dayNightIndex != -1) tokens.getOrNull(dayNightIndex)?.trim() else null

      val satellite = if (!satelliteRaw.isNullOrBlank()) satelliteRaw else defaultSatellite
      val instrument = if (!instrumentRaw.isNullOrBlank()) instrumentRaw else defaultInstrument

      validRecords.add(
        FireDataRecord(
          latitude = lat,
          longitude = lon,
          brightTi4 = brightTi4,
          brightTi5 = brightTi5,
          scan = scan,
          track = track,
          acqDate = acqDate,
          acqTime = acqTime,
          satellite = satellite,
          instrument = instrument,
          confidence = confidence,
          version = version,
          frp = frp,
          dayNight = dayNight,
          acquisitionTimestampMillis = acqTimestampMillis,
          rawLine = line
        )
      )
    }

    return ParseResult(
      records = validRecords,
      rawCount = rawCount,
      validCount = validRecords.size,
      invalidCount = invalidCount
    )
  }

  private fun parseCsvLine(line: String): List<String> {
    val tokens = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false

    for (ch in line) {
      when (ch) {
        '"' -> inQuotes = !inQuotes
        ',' -> {
          if (inQuotes) {
            sb.append(ch)
          } else {
            tokens.add(sb.toString().trim())
            sb.clear()
          }
        }
        else -> sb.append(ch)
      }
    }
    tokens.add(sb.toString().trim())
    return tokens
  }

  fun isValidDateString(dateStr: String): Boolean {
    // Format YYYY-MM-DD
    if (dateStr.length != 10) return false
    val parts = dateStr.split("-")
    if (parts.size != 3) return false
    val year = parts[0].toIntOrNull() ?: return false
    val month = parts[1].toIntOrNull() ?: return false
    val day = parts[2].toIntOrNull() ?: return false
    return year in 2000..2100 && month in 1..12 && day in 1..31
  }

  fun isValidTimeString(timeStr: String): Boolean {
    // Format HHmm (e.g. "0410" or "410") or HH:mm
    val clean = timeStr.replace(":", "")
    if (clean.length !in 3..4) return false
    val timeInt = clean.toIntOrNull() ?: return false
    val hours = if (clean.length == 3) clean.substring(0, 1).toInt() else clean.substring(0, 2).toInt()
    val minutes = clean.takeLast(2).toInt()
    return hours in 0..23 && minutes in 0..59
  }

  fun parseAcqTimestamp(dateStr: String, timeStr: String, dateFormat: SimpleDateFormat): Long? {
    return try {
      val clean = timeStr.replace(":", "")
      val padded = clean.padStart(4, '0')
      val formattedTime = "${padded.substring(0, 2)}:${padded.substring(2, 4)}"
      val combined = "$dateStr $formattedTime"
      dateFormat.parse(combined)?.time
    } catch (e: Exception) {
      null
    }
  }

  private fun logInvalidRow(row: String, reason: String) {
    AppLogger.recordError(
      AppError(
        type = ErrorType.PARSING_ERROR,
        message = "Baris data satelit ditolak ($reason): $row",
        source = "FireDataParser",
        recoveryAction = "Abaikan record data invalid sesuai Aturan Section 12"
      )
    )
  }
}
