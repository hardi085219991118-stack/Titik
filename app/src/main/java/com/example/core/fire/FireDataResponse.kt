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
  val responseSha256Hash: String? = null
) {
  val hasValidRecords: Boolean get() = validRecordCount > 0

  companion object {
    fun unverified(): FireDataResponse = FireDataResponse(
      state = FireDataSourceState.NOT_VERIFIED,
      records = emptyList(),
      rawRecordCount = 0,
      validRecordCount = 0
    )

    fun credentialRequired(): FireDataResponse = FireDataResponse(
      state = FireDataSourceState.API_CREDENTIAL_REQUIRED,
      records = emptyList(),
      error = FireDataError.MissingCredential
    )

    fun error(
      state: FireDataSourceState,
      error: FireDataError,
      requestTimeMillis: Long = System.currentTimeMillis(),
      sourceSensor: String = NasaFirmsConstants.SENSOR_VIIRS_NOAA21
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
      httpStatusCode = error.httpStatusCode
    )
  }
}
