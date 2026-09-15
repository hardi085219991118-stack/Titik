package com.example.core.fire

/**
 * Result payload of a NASA FIRMS active fire query.
 *
 * ZERO-DUMMY MANDATE:
 * - records contains ONLY valid records that passed strict coordinate and timestamp validation.
 * - If request fails, records is strictly empty, and state represents the exact failure reason.
 * - validRecordCount is exactly records.size.
 * - Acquisition time is never mixed with requestTimeMillis or fetchTimeMillis.
 */
data class FireDataResponse(
  val state: FireDataSourceState,
  val records: List<FireDataRecord> = emptyList(),
  val rawRecordCount: Int = 0,
  val validRecordCount: Int = 0,
  val invalidRecordCount: Int = 0,
  val requestTimeMillis: Long = 0L,
  val fetchTimeMillis: Long = 0L,
  val sourceSensor: String = NasaFirmsConstants.SENSOR_VIIRS_NOAA21,
  val freshness: FreshnessLevel = FreshnessLevel.FRESHNESS_UNKNOWN,
  val isCached: Boolean = false,
  val cacheAgeMillis: Long = 0L,
  val error: FireDataError? = null,
  val httpStatusCode: Int? = null,
  val responseFormat: String = "CSV",
  val requestArea: String = NasaFirmsConstants.DEFAULT_MANTHANGAI_BBOX,
  val requestDayRange: Int = 1,
  val satelliteDistribution: Map<String, Int> = emptyMap(),
  val instrumentDistribution: Map<String, Int> = emptyMap(),
  val minAcquisitionTimeMillis: Long? = null,
  val maxAcquisitionTimeMillis: Long? = null,
  val responseSha256Hash: String? = null,
  val responseByteCount: Int = 0,
  val diagnosticCause: LiveApiDiagnosticCause = LiveApiDiagnosticCause.REQUEST_NOT_STARTED,
  val diagnosticDetail: String? = null
) {
  val hasValidRecords: Boolean get() = validRecordCount > 0

  companion object {
    fun unverified(): FireDataResponse = FireDataResponse(
      state = FireDataSourceState.NOT_VERIFIED,
      records = emptyList(),
      rawRecordCount = 0,
      validRecordCount = 0,
      diagnosticCause = LiveApiDiagnosticCause.REQUEST_NOT_STARTED,
      diagnosticDetail = "Permintaan live ke NASA FIRMS belum pernah dieksekusi sejak aplikasi dibuka (REQUEST_NOT_STARTED)."
    )

    fun credentialRequired(): FireDataResponse = FireDataResponse(
      state = FireDataSourceState.API_CREDENTIAL_REQUIRED,
      records = emptyList(),
      error = FireDataError.MissingCredential,
      diagnosticCause = LiveApiDiagnosticCause.MISSING_CREDENTIAL,
      diagnosticDetail = "Gagal memulai request: MAP_KEY NASA FIRMS belum dikonfigurasi (MISSING_CREDENTIAL)."
    )

    fun error(
      state: FireDataSourceState,
      error: FireDataError,
      requestTimeMillis: Long = System.currentTimeMillis(),
      sourceSensor: String = NasaFirmsConstants.SENSOR_VIIRS_NOAA21,
      diagnosticCause: LiveApiDiagnosticCause = when (error) {
        is FireDataError.MissingCredential -> LiveApiDiagnosticCause.MISSING_CREDENTIAL
        is FireDataError.DnsError -> LiveApiDiagnosticCause.DNS_FAILURE
        is FireDataError.Timeout -> LiveApiDiagnosticCause.TIMEOUT
        is FireDataError.SslError -> LiveApiDiagnosticCause.TLS_FAILURE
        is FireDataError.NoInternet -> LiveApiDiagnosticCause.NETWORK_FAILURE
        is FireDataError.EmptyResponse -> LiveApiDiagnosticCause.EMPTY_RESPONSE
        is FireDataError.HttpError -> when (error.httpStatusCode) {
          400 -> LiveApiDiagnosticCause.HTTP_400
          401 -> LiveApiDiagnosticCause.HTTP_401
          403 -> LiveApiDiagnosticCause.HTTP_403
          404 -> LiveApiDiagnosticCause.HTTP_404
          429 -> LiveApiDiagnosticCause.HTTP_429
          500 -> LiveApiDiagnosticCause.HTTP_500
          502 -> LiveApiDiagnosticCause.HTTP_502
          503 -> LiveApiDiagnosticCause.HTTP_503
          else -> LiveApiDiagnosticCause.UNKNOWN_ERROR
        }
        is FireDataError.InvalidResponse -> when {
          error.reason.contains("HTML", ignoreCase = true) || error.reason.contains("<html", ignoreCase = true) -> LiveApiDiagnosticCause.INVALID_CSV
          error.reason.contains("Header", ignoreCase = true) || error.reason.contains("latitude", ignoreCase = true) -> LiveApiDiagnosticCause.INVALID_SCHEMA
          else -> LiveApiDiagnosticCause.PARSER_ERROR
        }
        else -> LiveApiDiagnosticCause.UNKNOWN_ERROR
      }
    ): FireDataResponse = FireDataResponse(
      state = state,
      records = emptyList(),
      rawRecordCount = 0,
      validRecordCount = 0,
      invalidRecordCount = 0,
      requestTimeMillis = requestTimeMillis,
      fetchTimeMillis = System.currentTimeMillis(),
      sourceSensor = sourceSensor,
      error = error,
      httpStatusCode = error.httpStatusCode,
      diagnosticCause = diagnosticCause,
      diagnosticDetail = error.message
    )
  }
}
