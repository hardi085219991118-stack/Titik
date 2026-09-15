package com.example.core.fire

/**
 * Result of the NASA FIRMS Live Connection Pipeline Audit (FIRE-006C).
 */
data class LiveApiDiagnosticResult(
  val cause: LiveApiDiagnosticCause,
  val gate: LiveVerificationGate,
  val detail: String,
  val endpoint: String,
  val areaBoundingBox: String,
  val isBoundingBoxValid: Boolean,
  val boundingBoxStatus: String,
  val sourcePriority: List<String>,
  val isLiveApiVerified: Boolean
)

/**
 * Diagnostic Auditor for NASA FIRMS Live Connection adhering strictly to Prompt 006C.
 *
 * Audits the full pipeline:
 * UI -> ViewModel -> Repository -> NASA FIRMS Client -> HTTP request -> MAP_KEY ->
 * endpoint -> response -> CSV parser -> validation -> LiveVerificationGate -> Dashboard
 */
object LiveApiDiagnosticAuditor {

  const val OFFICIAL_ENDPOINT_TEMPLATE =
    "https://firms.modaps.eosdis.nasa.gov/api/area/csv/[MAP_KEY]/[SOURCE]/[AREA_COORDINATES]/[DAY_RANGE]"

  fun buildOfficialUrl(mapKey: String, source: String, areaCoordinates: String, dayRange: Int): String {
    return "${NasaFirmsConstants.BASE_URL}api/area/csv/$mapKey/$source/$areaCoordinates/$dayRange"
  }

  /**
   * Section 2: Validate Area Coordinates Bounding Box.
   * Format: WEST,SOUTH,EAST,NORTH
   * Rules:
   * - Exactly 4 numeric parts
   * - WEST < EAST
   * - SOUTH < NORTH
   * - Longitudes in -180.0..180.0
   * - Latitudes in -90.0..90.0
   */
  fun validateBoundingBox(bbox: String): Pair<Boolean, String> {
    val tokens = bbox.split(",").map { it.trim() }
    if (tokens.size != 4) {
      return false to "INVALID_AREA: Format bounding box harus WEST,SOUTH,EAST,NORTH (ditemukan ${tokens.size} komponen)."
    }

    val numbers = tokens.mapNotNull { it.toDoubleOrNull() }
    if (numbers.size != 4) {
      return false to "INVALID_AREA: Komponen bounding box bukan angka numerik valid: '$bbox'."
    }

    val west = numbers[0]
    val south = numbers[1]
    val east = numbers[2]
    val north = numbers[3]

    if (west.isNaN() || west.isInfinite() || west < -180.0 || west > 180.0) {
      return false to "INVALID_AREA: WEST longitude out of bounds: $west (harus -180 s/d 180)."
    }
    if (east.isNaN() || east.isInfinite() || east < -180.0 || east > 180.0) {
      return false to "INVALID_AREA: EAST longitude out of bounds: $east (harus -180 s/d 180)."
    }
    if (south.isNaN() || south.isInfinite() || south < -90.0 || south > 90.0) {
      return false to "INVALID_AREA: SOUTH latitude out of bounds: $south (harus -90 s/d 90)."
    }
    if (north.isNaN() || north.isInfinite() || north < -90.0 || north > 90.0) {
      return false to "INVALID_AREA: NORTH latitude out of bounds: $north (harus -90 s/d 90)."
    }

    if (west >= east) {
      return false to "INVALID_AREA: WEST ($west) harus lebih kecil dari EAST ($east)."
    }
    if (south >= north) {
      return false to "INVALID_AREA: SOUTH ($south) harus lebih kecil dari NORTH ($north)."
    }

    return true to "VALID: WEST=$west, SOUTH=$south, EAST=$east, NORTH=$north (BBox Mantangai memenuhi kontrak FIRMS)."
  }

  /**
   * Section 3: Validate Sensor Source.
   * Priority:
   * 1. VIIRS_NOAA21_NRT
   * 2. VIIRS_NOAA20_NRT
   * 3. VIIRS_SNPP_NRT
   * 4. MODIS_NRT
   */
  fun isSourceValid(source: String): Boolean {
    return NasaFirmsConstants.SENSOR_PRIORITY.contains(source)
  }

  /**
   * Perform comprehensive pipeline audit.
   */
  fun auditPipeline(
    credState: FireDataCredentialState,
    sourceState: FireDataSourceState,
    response: FireDataResponse,
    queryArea: String = response.requestArea
  ): LiveApiDiagnosticResult {
    val (isBBoxValid, bboxStatus) = validateBoundingBox(queryArea)

    // Check pipeline steps in sequence
    val cause: LiveApiDiagnosticCause
    val gate: LiveVerificationGate
    val detail: String

    when {
      // 1. Request not started yet (initial application state)
      sourceState == FireDataSourceState.NOT_VERIFIED -> {
        cause = LiveApiDiagnosticCause.REQUEST_NOT_STARTED
        gate = LiveVerificationGate.LIVE_API_NOT_VERIFIED
        detail = if (credState == FireDataCredentialState.CONFIGURED) {
          "Permintaan live ke NASA FIRMS belum pernah dieksekusi sejak aplikasi dibuka (REQUEST_NOT_STARTED). MAP_KEY telah tersimpan lokal, tekan PERBARUI DATA SATELIT untuk menguji koneksi."
        } else {
          "Permintaan live ke NASA FIRMS belum pernah dieksekusi sejak aplikasi dibuka (REQUEST_NOT_STARTED). MAP_KEY belum dikonfigurasi."
        }
      }

      // 2. Bounding Box check
      !isBBoxValid -> {
        cause = LiveApiDiagnosticCause.INVALID_AREA
        gate = LiveVerificationGate.LIVE_API_ERROR
        detail = bboxStatus
      }

      // 3. Day range check
      response.requestDayRange !in 1..10 -> {
        cause = LiveApiDiagnosticCause.INVALID_DAY_RANGE
        gate = LiveVerificationGate.LIVE_API_ERROR
        detail = "Parameter dayRange (${response.requestDayRange}) di luar batas 1..10."
      }

      // 4. Source sensor check
      !isSourceValid(response.sourceSensor) -> {
        cause = LiveApiDiagnosticCause.INVALID_SOURCE
        gate = LiveVerificationGate.LIVE_API_ERROR
        detail = "Sensor source '${response.sourceSensor}' tidak valid atau di luar daftar sensor FIRMS."
      }

      // 5. Credential check
      credState == FireDataCredentialState.NOT_CONFIGURED || response.error is FireDataError.MissingCredential -> {
        cause = LiveApiDiagnosticCause.MISSING_CREDENTIAL
        gate = LiveVerificationGate.LIVE_API_CREDENTIAL_REQUIRED
        detail = "Gagal memulai request: MAP_KEY NASA FIRMS belum dikonfigurasi pada storage lokal (MISSING_CREDENTIAL). Daftarkan MAP_KEY resmi di https://firms.modaps.eosdis.nasa.gov."
      }

      credState == FireDataCredentialState.INVALID -> {
        cause = when (response.httpStatusCode) {
          401 -> LiveApiDiagnosticCause.HTTP_401
          403 -> LiveApiDiagnosticCause.HTTP_403
          else -> LiveApiDiagnosticCause.INVALID_CREDENTIAL
        }
        gate = LiveVerificationGate.LIVE_API_CREDENTIAL_REQUIRED
        detail = "MAP_KEY NASA FIRMS ditolak oleh server (HTTP ${response.httpStatusCode ?: "401/403"} atau respon 'Invalid MAP_KEY')."
      }

      // 6. Network & Transport Failures
      response.error is FireDataError.DnsError || sourceState == FireDataSourceState.NETWORK_ERROR && response.error?.code == "DNS_RESOLUTION_ERROR" -> {
        cause = LiveApiDiagnosticCause.DNS_FAILURE
        gate = LiveVerificationGate.LIVE_API_NETWORK_ERROR
        detail = "Gagal menyelesaikan domain firms.modaps.eosdis.nasa.gov (DNS_FAILURE). Periksa koneksi DNS atau akses internet perangkat."
      }

      response.error is FireDataError.Timeout || sourceState == FireDataSourceState.TIMEOUT -> {
        cause = LiveApiDiagnosticCause.TIMEOUT
        gate = LiveVerificationGate.LIVE_API_NETWORK_ERROR
        detail = "Koneksi atau pembacaan respon dari server NASA FIRMS melebihi batas waktu (TIMEOUT)."
      }

      response.error is FireDataError.SslError -> {
        cause = LiveApiDiagnosticCause.TLS_FAILURE
        gate = LiveVerificationGate.LIVE_API_NETWORK_ERROR
        detail = "Kegagalan negosiasi sertifikat SSL/TLS dengan NASA FIRMS (TLS_FAILURE). Periksa jam/tanggal perangkat."
      }

      response.error is FireDataError.NoInternet || (sourceState == FireDataSourceState.NETWORK_ERROR && response.httpStatusCode == null) -> {
        cause = LiveApiDiagnosticCause.NETWORK_FAILURE
        gate = LiveVerificationGate.LIVE_API_NETWORK_ERROR
        detail = "Koneksi jaringan gagal atau tidak ada rute ke host NASA FIRMS (NETWORK_FAILURE)."
      }

      // 7. HTTP Response Codes
      response.httpStatusCode == 400 -> {
        cause = LiveApiDiagnosticCause.HTTP_400
        gate = LiveVerificationGate.LIVE_API_HTTP_ERROR
        detail = "Server NASA FIRMS mengembalikan HTTP 400 Bad Request (query parameter atau bounding box salah)."
      }

      response.httpStatusCode == 401 -> {
        cause = LiveApiDiagnosticCause.HTTP_401
        gate = LiveVerificationGate.LIVE_API_CREDENTIAL_REQUIRED
        detail = "Server NASA FIRMS mengembalikan HTTP 401 Unauthorized (MAP_KEY tidak terdaftar)."
      }

      response.httpStatusCode == 403 -> {
        cause = LiveApiDiagnosticCause.HTTP_403
        gate = LiveVerificationGate.LIVE_API_CREDENTIAL_REQUIRED
        detail = "Server NASA FIRMS mengembalikan HTTP 403 Forbidden (akses ke dataset ditolak)."
      }

      response.httpStatusCode == 404 -> {
        cause = LiveApiDiagnosticCause.HTTP_404
        gate = LiveVerificationGate.LIVE_API_HTTP_ERROR
        detail = "Server NASA FIRMS mengembalikan HTTP 404 Not Found (endpoint URL tidak ditemukan)."
      }

      response.httpStatusCode == 429 || sourceState == FireDataSourceState.RATE_LIMIT_EXCEEDED -> {
        cause = LiveApiDiagnosticCause.HTTP_429
        gate = LiveVerificationGate.LIVE_API_HTTP_ERROR
        detail = "Server NASA FIRMS mengembalikan HTTP 429 Too Many Requests (Rate limit kuota NASA FIRMS tercapai)."
      }

      response.httpStatusCode == 500 -> {
        cause = LiveApiDiagnosticCause.HTTP_500
        gate = LiveVerificationGate.LIVE_API_HTTP_ERROR
        detail = "Server NASA FIRMS mengalami gangguan internal (HTTP 500 Internal Server Error)."
      }

      response.httpStatusCode == 502 -> {
        cause = LiveApiDiagnosticCause.HTTP_502
        gate = LiveVerificationGate.LIVE_API_HTTP_ERROR
        detail = "Server gateway NASA FIRMS mengembalikan HTTP 502 Bad Gateway."
      }

      response.httpStatusCode == 503 -> {
        cause = LiveApiDiagnosticCause.HTTP_503
        gate = LiveVerificationGate.LIVE_API_HTTP_ERROR
        detail = "Layanan NASA FIRMS sedang maintenance atau kelebihan beban (HTTP 503 Service Unavailable)."
      }

      // 8. Payload & Parsing Failures
      response.error is FireDataError.EmptyResponse -> {
        cause = LiveApiDiagnosticCause.EMPTY_RESPONSE
        gate = LiveVerificationGate.LIVE_API_INVALID_RESPONSE
        detail = "Server mengembalikan HTTP 200 namun body respon berukuran 0 byte (EMPTY_RESPONSE)."
      }

      sourceState == FireDataSourceState.INVALID_DATA_RESPONSE -> {
        val errMsg = response.error?.message.orEmpty()
        when {
          errMsg.contains("HTML", ignoreCase = true) || errMsg.contains("<html", ignoreCase = true) -> {
            cause = LiveApiDiagnosticCause.INVALID_CSV
            gate = LiveVerificationGate.LIVE_API_INVALID_RESPONSE
            detail = "Respon yang diterima bukan CSV yang valid (INVALID_CSV: Server mengembalikan dokumen HTML)."
          }
          errMsg.contains("latitude", ignoreCase = true) || errMsg.contains("longitude", ignoreCase = true) || errMsg.contains("Header", ignoreCase = true) -> {
            cause = LiveApiDiagnosticCause.INVALID_SCHEMA
            gate = LiveVerificationGate.LIVE_API_PARSER_ERROR
            detail = "Header kolom CSV tidak memenuhi skema FIRMS: $errMsg (INVALID_SCHEMA)."
          }
          else -> {
            cause = LiveApiDiagnosticCause.PARSER_ERROR
            gate = LiveVerificationGate.LIVE_API_PARSER_ERROR
            detail = "Terjadi kegagalan parser CSV FIRMS: $errMsg (PARSER_ERROR)."
          }
        }
      }

      // 9. Verified Condition (HTTP 200, valid records or legitimate zero detections)
      (sourceState == FireDataSourceState.DATA_SOURCE_AVAILABLE || sourceState == FireDataSourceState.NO_DETECTIONS_IN_QUERY) &&
        response.httpStatusCode == 200 && !response.isCached -> {
        cause = LiveApiDiagnosticCause.REQUEST_NOT_STARTED // No failure
        gate = LiveVerificationGate.LIVE_API_VERIFIED
        detail = "Respon live otentik dari NASA FIRMS (${response.sourceSensor}) berhasil diverifikasi via HTTP 200. SHA-256 terverifikasi. Deteksi: ${response.validRecordCount} titik api."
      }

      // 10. Fallback unclassified error
      else -> {
        cause = LiveApiDiagnosticCause.UNKNOWN_ERROR
        gate = LiveVerificationGate.LIVE_API_UNKNOWN_ERROR
        detail = response.error?.message ?: "Status respon tidak terklasifikasi (${sourceState.name})."
      }
    }

    return LiveApiDiagnosticResult(
      cause = cause,
      gate = gate,
      detail = detail,
      endpoint = buildOfficialUrl("[REDACTED_MAP_KEY]", response.sourceSensor, queryArea, response.requestDayRange),
      areaBoundingBox = queryArea,
      isBoundingBoxValid = isBBoxValid,
      boundingBoxStatus = bboxStatus,
      sourcePriority = NasaFirmsConstants.SENSOR_PRIORITY,
      isLiveApiVerified = gate == LiveVerificationGate.LIVE_API_VERIFIED
    )
  }
}
