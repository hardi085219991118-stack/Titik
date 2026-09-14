package com.example.core.fire

import com.example.core.logging.AppError
import com.example.core.logging.AppLogger
import com.example.core.logging.ErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Data Source Contract for NASA FIRMS API.
 */
interface FireDataSource {
  suspend fun fetchFireData(
    mapKey: String,
    source: String = NasaFirmsConstants.SENSOR_VIIRS_NOAA21,
    areaCoordinates: String = NasaFirmsConstants.DEFAULT_MANTHANGAI_BBOX,
    dayRange: Int = 1
  ): FireDataResponse
}

class NasaFirmsNetworkDataSource(
  private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false)
    .build()
) : FireDataSource {

  override suspend fun fetchFireData(
    mapKey: String,
    source: String,
    areaCoordinates: String,
    dayRange: Int
  ): FireDataResponse = withContext(Dispatchers.IO) {
    val requestTime = System.currentTimeMillis()

    // Validate MAP_KEY before initiating network call
    if (mapKey.isBlank()) {
      return@withContext FireDataResponse.error(
        state = FireDataSourceState.API_CREDENTIAL_REQUIRED,
        error = FireDataError.MissingCredential,
        requestTimeMillis = requestTime,
        sourceSensor = source
      )
    }

    // Official NASA FIRMS Endpoint:
    // https://firms.modaps.eosdis.nasa.gov/api/area/csv/[MAP_KEY]/[SOURCE]/[AREA_COORDINATES]/[DAY_RANGE]
    val url = "${NasaFirmsConstants.BASE_URL}api/area/csv/$mapKey/$source/$areaCoordinates/$dayRange"

    AppLogger.recordEvent("Initiating NASA FIRMS query for sensor $source")

    val request = Request.Builder()
      .url(url)
      .header("User-Agent", "HardiMantangaiFire/1.0 (Android; ZeroDummy)")
      .build()

    try {
      val response = okHttpClient.newCall(request).execute()
      val fetchTime = System.currentTimeMillis()
      val statusCode = response.code
      val responseBody = response.body?.string()

      // Calculate SHA-256 hash of response for internal verification audit (zero credential leak)
      val responseHash = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(responseBody?.toByteArray(Charsets.UTF_8) ?: ByteArray(0))
        bytes.joinToString("") { "%02x".format(it) }
      } catch (e: Exception) {
        null
      }

      // Check if NASA returned Invalid MAP_KEY (either in HTTP 400 or HTTP 200/401/403)
      if (responseBody?.contains("Invalid MAP_KEY", ignoreCase = true) == true) {
        val error = FireDataError.HttpError(if (statusCode == 200) 401 else statusCode, "Invalid MAP_KEY returned by NASA FIRMS")
        AppLogger.recordError(
          AppError(
            type = ErrorType.AUTHENTICATION_ERROR,
            message = "NASA FIRMS API menolak MAP_KEY (Invalid MAP_KEY)",
            source = "NasaFirmsNetworkDataSource",
            recoveryAction = "Periksa dan masukkan MAP_KEY resmi NASA FIRMS yang valid."
          )
        )
        return@withContext FireDataResponse(
          state = FireDataSourceState.API_CREDENTIAL_REQUIRED,
          records = emptyList(),
          rawRecordCount = 0,
          validRecordCount = 0,
          invalidRecordCount = 0,
          requestTimeMillis = requestTime,
          fetchTimeMillis = fetchTime,
          sourceSensor = source,
          error = error,
          httpStatusCode = statusCode,
          requestArea = areaCoordinates,
          requestDayRange = dayRange,
          responseSha256Hash = responseHash
        )
      }

      if (!response.isSuccessful) {
        val error = FireDataError.HttpError(statusCode, responseBody?.take(200) ?: "No body")
        val state = when (statusCode) {
          401, 403 -> FireDataSourceState.API_CREDENTIAL_REQUIRED
          429 -> FireDataSourceState.RATE_LIMIT_EXCEEDED
          else -> FireDataSourceState.DATA_SOURCE_UNAVAILABLE
        }
        AppLogger.recordError(
          AppError(
            type = if (statusCode in 401..403) ErrorType.AUTHENTICATION_ERROR else ErrorType.NETWORK_ERROR,
            message = error.message,
            source = "NasaFirmsNetworkDataSource",
            recoveryAction = error.recoveryAction
          )
        )
        return@withContext FireDataResponse(
          state = state,
          records = emptyList(),
          rawRecordCount = 0,
          validRecordCount = 0,
          requestTimeMillis = requestTime,
          fetchTimeMillis = fetchTime,
          sourceSensor = source,
          error = error,
          httpStatusCode = statusCode,
          requestArea = areaCoordinates,
          requestDayRange = dayRange,
          responseSha256Hash = responseHash
        )
      }

      if (responseBody.isNullOrBlank()) {
        val error = FireDataError.EmptyResponse
        AppLogger.recordError(
          AppError(
            type = ErrorType.EMPTY_DATA,
            message = error.message,
            source = "NasaFirmsNetworkDataSource",
            recoveryAction = error.recoveryAction
          )
        )
        return@withContext FireDataResponse(
          state = FireDataSourceState.INVALID_DATA_RESPONSE,
          records = emptyList(),
          requestTimeMillis = requestTime,
          fetchTimeMillis = fetchTime,
          sourceSensor = source,
          error = error,
          httpStatusCode = statusCode,
          requestArea = areaCoordinates,
          requestDayRange = dayRange,
          responseSha256Hash = responseHash
        )
      }

      // Parse CSV Response
      val parseResult = try {
        val instrument = if (source.contains("VIIRS", ignoreCase = true)) "VIIRS" else "MODIS"
        val satellite = when {
          source.contains("NOAA21", ignoreCase = true) -> "NOAA-21"
          source.contains("NOAA20", ignoreCase = true) -> "NOAA-20"
          source.contains("SNPP", ignoreCase = true) -> "Suomi-NPP"
          else -> "Terra/Aqua"
        }
        FireDataParser.parseCsv(
          csvContent = responseBody,
          defaultInstrument = instrument,
          defaultSatellite = satellite
        )
      } catch (e: Exception) {
        val error = FireDataError.InvalidResponse(e.message ?: "Failed parsing CSV response")
        AppLogger.recordError(
          AppError(
            type = ErrorType.PARSING_ERROR,
            message = error.message,
            source = "NasaFirmsNetworkDataSource",
            recoveryAction = error.recoveryAction,
            cause = e
          )
        )
        return@withContext FireDataResponse(
          state = FireDataSourceState.INVALID_DATA_RESPONSE,
          records = emptyList(),
          requestTimeMillis = requestTime,
          fetchTimeMillis = fetchTime,
          sourceSensor = source,
          error = error,
          httpStatusCode = statusCode,
          requestArea = areaCoordinates,
          requestDayRange = dayRange,
          responseSha256Hash = responseHash
        )
      }

      // Calculate Freshness
      val freshness = calculateFreshness(parseResult.records, fetchTime)

      // Distributions & metrics
      val satelliteDist = parseResult.records.groupingBy { it.satellite }.eachCount()
      val instrumentDist = parseResult.records.groupingBy { it.instrument }.eachCount()
      val minAcq = parseResult.records.mapNotNull { it.acquisitionTimestampMillis }.minOrNull()
      val maxAcq = parseResult.records.mapNotNull { it.acquisitionTimestampMillis }.maxOrNull()

      // Section 14 Mandate:
      // If response is valid and really contains 0 detections:
      // status: NO_DETECTIONS_IN_QUERY, count: 0
      // If contains valid records:
      // status: DATA_SOURCE_AVAILABLE, count: validCount
      val state = if (parseResult.validCount == 0) {
        FireDataSourceState.NO_DETECTIONS_IN_QUERY
      } else {
        FireDataSourceState.DATA_SOURCE_AVAILABLE
      }

      AppLogger.recordEvent(
        "NASA FIRMS response parsed successfully. Status=$state, Valid=${parseResult.validCount}, Invalid=${parseResult.invalidCount}"
      )

      return@withContext FireDataResponse(
        state = state,
        records = parseResult.records,
        rawRecordCount = parseResult.rawCount,
        validRecordCount = parseResult.validCount,
        invalidRecordCount = parseResult.invalidCount,
        requestTimeMillis = requestTime,
        fetchTimeMillis = fetchTime,
        sourceSensor = source,
        freshness = freshness,
        isCached = false,
        httpStatusCode = statusCode,
        requestArea = areaCoordinates,
        requestDayRange = dayRange,
        satelliteDistribution = satelliteDist,
        instrumentDistribution = instrumentDist,
        minAcquisitionTimeMillis = minAcq,
        maxAcquisitionTimeMillis = maxAcq,
        responseSha256Hash = responseHash
      )

    } catch (e: SocketTimeoutException) {
      val error = FireDataError.Timeout
      logNetworkException(error, e)
      return@withContext FireDataResponse.error(
        state = FireDataSourceState.TIMEOUT,
        error = error,
        requestTimeMillis = requestTime,
        sourceSensor = source
      )
    } catch (e: UnknownHostException) {
      val error = FireDataError.DnsError
      logNetworkException(error, e)
      return@withContext FireDataResponse.error(
        state = FireDataSourceState.NETWORK_ERROR,
        error = error,
        requestTimeMillis = requestTime,
        sourceSensor = source
      )
    } catch (e: SSLException) {
      val error = FireDataError.SslError
      logNetworkException(error, e)
      return@withContext FireDataResponse.error(
        state = FireDataSourceState.NETWORK_ERROR,
        error = error,
        requestTimeMillis = requestTime,
        sourceSensor = source
      )
    } catch (e: IOException) {
      val error = FireDataError.NoInternet
      logNetworkException(error, e)
      return@withContext FireDataResponse.error(
        state = FireDataSourceState.NETWORK_ERROR,
        error = error,
        requestTimeMillis = requestTime,
        sourceSensor = source
      )
    } catch (e: Throwable) {
      val error = FireDataError.InvalidResponse(e.message ?: "Unknown network exception")
      AppLogger.recordError(
        AppError(
          type = ErrorType.UNKNOWN_ERROR,
          message = "Unexpected failure: ${e.message}",
          source = "NasaFirmsNetworkDataSource",
          recoveryAction = "Periksa exception",
          cause = e
        )
      )
      return@withContext FireDataResponse.error(
        state = FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
        error = error,
        requestTimeMillis = requestTime,
        sourceSensor = source
      )
    }
  }

  private fun calculateFreshness(records: List<FireDataRecord>, fetchTimeMillis: Long): FreshnessLevel {
    if (records.isEmpty()) return FreshnessLevel.FRESHNESS_UNKNOWN
    val latestAcq = records.mapNotNull { it.acquisitionTimestampMillis }.maxOrNull()
      ?: return FreshnessLevel.FRESHNESS_UNKNOWN

    val ageMillis = fetchTimeMillis - latestAcq
    return when {
      ageMillis < 0 -> FreshnessLevel.FRESHNESS_UNKNOWN // Invalid timestamp in future
      ageMillis < 60 * 60 * 1000L -> FreshnessLevel.RT // < 1 hour
      ageMillis < 2 * 60 * 60 * 1000L -> FreshnessLevel.URT // < 2 hours
      ageMillis < 24 * 60 * 60 * 1000L -> FreshnessLevel.NRT // < 24 hours
      else -> FreshnessLevel.STALE // > 24 hours
    }
  }

  private fun logNetworkException(error: FireDataError, cause: Throwable) {
    AppLogger.recordError(
      AppError(
        type = if (error is FireDataError.Timeout) ErrorType.API_TIMEOUT else ErrorType.NETWORK_ERROR,
        message = error.message,
        source = "NasaFirmsNetworkDataSource",
        recoveryAction = error.recoveryAction,
        cause = cause
      )
    )
  }
}
