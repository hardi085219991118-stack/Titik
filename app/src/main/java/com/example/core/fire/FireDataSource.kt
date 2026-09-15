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
    AppLogger.recordEvent("REQUEST_STARTED: sensor=$source bbox=$areaCoordinates dayRange=$dayRange")
    AppLogger.recordEvent("LIVE_REQUEST_STARTED: sensor=$source")
    AppLogger.recordEvent("NASA_FIRMS_REQUEST_STARTED: sensor=$source, area=$areaCoordinates, dayRange=$dayRange")

    // Validate MAP_KEY before initiating network call
    if (mapKey.isBlank()) {
      AppLogger.recordEvent("NASA_FIRMS_REQUEST_ABORTED: MAP_KEY is missing/blank")
      return@withContext FireDataResponse.error(
        state = FireDataSourceState.API_CREDENTIAL_REQUIRED,
        error = FireDataError.MissingCredential,
        requestTimeMillis = requestTime,
        sourceSensor = source,
        diagnosticCause = LiveApiDiagnosticCause.MISSING_CREDENTIAL
      )
    }

    // Validate sensor source
    if (!LiveApiDiagnosticAuditor.isSourceValid(source)) {
      AppLogger.recordEvent("NASA_FIRMS_REQUEST_ABORTED: Invalid source sensor '$source'")
      val error = FireDataError.InvalidResponse("Sensor satelit '$source' tidak valid atau tidak didukung NASA FIRMS")
      return@withContext FireDataResponse.error(
        state = FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
        error = error,
        requestTimeMillis = requestTime,
        sourceSensor = source,
        diagnosticCause = LiveApiDiagnosticCause.INVALID_SOURCE
      )
    }

    // Validate area bounding box
    val (isBBoxValid, bboxStatus) = LiveApiDiagnosticAuditor.validateBoundingBox(areaCoordinates)
    if (!isBBoxValid) {
      AppLogger.recordEvent("NASA_FIRMS_REQUEST_ABORTED: $bboxStatus")
      val error = FireDataError.InvalidResponse(bboxStatus)
      return@withContext FireDataResponse.error(
        state = FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
        error = error,
        requestTimeMillis = requestTime,
        sourceSensor = source,
        diagnosticCause = LiveApiDiagnosticCause.INVALID_AREA
      )
    }

    // Validate dayRange
    if (dayRange !in 1..10) {
      AppLogger.recordEvent("NASA_FIRMS_REQUEST_ABORTED: Invalid dayRange $dayRange")
      val error = FireDataError.InvalidResponse("dayRange harus antara 1 s/d 10 (diberikan: $dayRange)")
      return@withContext FireDataResponse.error(
        state = FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
        error = error,
        requestTimeMillis = requestTime,
        sourceSensor = source,
        diagnosticCause = LiveApiDiagnosticCause.INVALID_DAY_RANGE
      )
    }

    // Official NASA FIRMS Endpoint:
    // https://firms.modaps.eosdis.nasa.gov/api/area/csv/[MAP_KEY]/[SOURCE]/[AREA_COORDINATES]/[DAY_RANGE]
    val url = "${NasaFirmsConstants.BASE_URL}api/area/csv/$mapKey/$source/$areaCoordinates/$dayRange"
    AppLogger.recordEvent("NASA_FIRMS_REQUEST_URL_BUILT: endpoint=api/area/csv/[REDACTED_MAP_KEY]/$source/$areaCoordinates/$dayRange")

    val request = Request.Builder()
      .url(url)
      .header("User-Agent", "HardiMantangaiFire/1.0 (Android; ZeroDummy)")
      .build()

    AppLogger.recordEvent("NASA_FIRMS_REQUEST_SENT: source=$source area=$areaCoordinates dayRange=$dayRange")
    AppLogger.recordEvent("REQUEST_SENT: method=GET url=api/area/csv/[REDACTED]/$source/$areaCoordinates/$dayRange")

    try {
      val response = okHttpClient.newCall(request).execute()
      val fetchTime = System.currentTimeMillis()
      val statusCode = response.code
      val responseBody = response.body?.string()
      val byteCount = responseBody?.length ?: 0

      AppLogger.recordEvent("HTTP_STATUS: code=$statusCode")
      AppLogger.recordEvent("RESPONSE_RECEIVED: code=$statusCode message=${response.message}")
      AppLogger.recordEvent("RESPONSE_BYTES: count=$byteCount bytes")
      AppLogger.recordEvent("NASA_FIRMS_RESPONSE_RECEIVED: httpStatus=$statusCode bytes=$byteCount")

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
        when (statusCode) {
          401, 403 -> AppLogger.recordEvent("LIVE_AUTH_FAILED: status=$statusCode")
          429 -> AppLogger.recordEvent("LIVE_RATE_LIMITED: status=$statusCode")
          else -> AppLogger.recordEvent("LIVE_NETWORK_FAILED: status=$statusCode")
        }
        val diagCause = when (statusCode) {
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
          responseSha256Hash = responseHash,
          diagnosticCause = diagCause,
          diagnosticDetail = error.message
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
          responseSha256Hash = responseHash,
          diagnosticCause = LiveApiDiagnosticCause.EMPTY_RESPONSE,
          diagnosticDetail = "Server mengembalikan HTTP 200 dengan payload kosong."
        )
      }

      val isHtmlResponse = responseBody.trim().startsWith("<html", ignoreCase = true) ||
        responseBody.trim().startsWith("<!doctype", ignoreCase = true)
      if (isHtmlResponse) {
        val error = FireDataError.InvalidResponse("Server mengembalikan halaman HTML alih-alih data CSV")
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
          responseSha256Hash = responseHash,
          diagnosticCause = LiveApiDiagnosticCause.INVALID_CSV,
          diagnosticDetail = "Server mengembalikan dokumen HTML alih-alih CSV."
        )
      }

      AppLogger.recordEvent("NASA_FIRMS_RESPONSE_VALIDATED: httpStatus=$statusCode, isSuccessful=${response.isSuccessful}")
      AppLogger.recordEvent("PARSE_STARTED: bytes=$byteCount sensor=$source")

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
        val isSchemaError = e.message?.contains("Header", ignoreCase = true) == true ||
          e.message?.contains("latitude", ignoreCase = true) == true ||
          e.message?.contains("longitude", ignoreCase = true) == true
        val diagCause = if (isSchemaError) LiveApiDiagnosticCause.INVALID_SCHEMA else LiveApiDiagnosticCause.PARSER_ERROR
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
          responseSha256Hash = responseHash,
          diagnosticCause = diagCause,
          diagnosticDetail = error.message
        )
      }

      AppLogger.recordEvent("NASA_FIRMS_RESPONSE_PARSED: validRecords=${parseResult.validCount}, rawRecords=${parseResult.rawCount}, invalidRecords=${parseResult.invalidCount}")
      AppLogger.recordEvent("PARSE_COMPLETED: valid=${parseResult.validCount} invalid=${parseResult.invalidCount} raw=${parseResult.rawCount}")
      AppLogger.recordEvent("VALID_RECORD_COUNT: ${parseResult.validCount}")
      AppLogger.recordEvent("INVALID_RECORD_COUNT: ${parseResult.invalidCount}")
      AppLogger.recordEvent("LIVE_RESPONSE_PARSED: validCount=${parseResult.validCount}")

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
        AppLogger.recordEvent("LIVE_REQUEST_SUCCESS: sensor=$source")
        AppLogger.recordEvent("LIVE_NO_RECORDS: 0 detections")
        FireDataSourceState.NO_DETECTIONS_IN_QUERY
      } else {
        AppLogger.recordEvent("LIVE_REQUEST_SUCCESS: sensor=$source")
        AppLogger.recordEvent("LIVE_RECORDS_AVAILABLE: count=${parseResult.validCount}")
        FireDataSourceState.DATA_SOURCE_AVAILABLE
      }

      AppLogger.recordEvent("NASA_FIRMS_LIVE_GATE_UPDATED: state=$state")

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
        responseSha256Hash = responseHash,
        responseByteCount = byteCount
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
    AppLogger.recordEvent("LIVE_NETWORK_FAILED: error=${error.message}")
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
