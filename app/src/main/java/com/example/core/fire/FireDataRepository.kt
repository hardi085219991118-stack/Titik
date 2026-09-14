package com.example.core.fire

import android.content.Context
import com.example.core.logging.AppError
import com.example.core.logging.AppLogger
import com.example.core.logging.ErrorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository interface managing NASA FIRMS fire data acquisition.
 *
 * ZERO-DUMMY MANDATE:
 * - When data source is unverified / fails: fire count is unknown (display "--"), NEVER "0".
 * - When request succeeds and has 0 detections: fire count is "0", state NO_DETECTIONS_IN_QUERY.
 * - No fake coordinates, no fake timestamps, no artificial fallback fire points.
 */
interface FireDataRepository {
  val fireDataResponse: StateFlow<FireDataResponse>
  val dataSourceState: StateFlow<FireDataSourceState>
  val credentialState: StateFlow<FireDataCredentialState>

  suspend fun refreshFireData(
    force: Boolean = false,
    areaCoordinates: String = NasaFirmsConstants.DEFAULT_MANTHANGAI_BBOX
  ): FireDataResponse

  fun setMapKey(mapKey: String?): FireDataCredentialState
  fun clearMapKey()
  fun getMapKey(): String?
}

class RealFireDataRepository(
  private val credentialProvider: FireDataCredentialProvider,
  private val dataSource: FireDataSource = NasaFirmsNetworkDataSource()
) : FireDataRepository {

  private val _fireDataResponse = MutableStateFlow(FireDataResponse.unverified())
  override val fireDataResponse: StateFlow<FireDataResponse> = _fireDataResponse.asStateFlow()

  private val _dataSourceState = MutableStateFlow(FireDataSourceState.NOT_VERIFIED)
  override val dataSourceState: StateFlow<FireDataSourceState> = _dataSourceState.asStateFlow()

  private val _credentialState = MutableStateFlow(credentialProvider.getCredentialState())
  override val credentialState: StateFlow<FireDataCredentialState> = _credentialState.asStateFlow()

  private val mutex = Mutex()
  private var lastRequestTimeMillis: Long = 0L
  private var rateLimitBackoffUntilMillis: Long = 0L
  private var cachedResponse: FireDataResponse? = null

  override suspend fun refreshFireData(
    force: Boolean,
    areaCoordinates: String
  ): FireDataResponse = mutex.withLock {
    val now = System.currentTimeMillis()

    // 1. Check Credentials
    val currentCredState = credentialProvider.getCredentialState()
    _credentialState.value = currentCredState
    val mapKey = credentialProvider.getMapKey()

    if (mapKey.isNullOrBlank() || currentCredState == FireDataCredentialState.NOT_CONFIGURED) {
      val response = FireDataResponse.credentialRequired()
      _fireDataResponse.value = response
      _dataSourceState.value = response.state
      return response
    }

    if (currentCredState == FireDataCredentialState.INVALID) {
      val response = FireDataResponse.error(
        state = FireDataSourceState.API_CREDENTIAL_REQUIRED,
        error = FireDataError.HttpError(401, "MAP_KEY NASA FIRMS sebelumnya ditolak atau tidak valid.")
      )
      _fireDataResponse.value = response
      _dataSourceState.value = response.state
      return response
    }

    // 2. Rate Limit Cooldown (Section 10)
    if (now < rateLimitBackoffUntilMillis) {
      val remainingSec = (rateLimitBackoffUntilMillis - now) / 1000
      val response = FireDataResponse.error(
        state = FireDataSourceState.RATE_LIMIT_EXCEEDED,
        error = FireDataError.HttpError(429, "Rate limit NASA FIRMS aktif. Backoff $remainingSec detik.")
      )
      _fireDataResponse.value = response
      _dataSourceState.value = response.state
      return response
    }

    if (!force && (now - lastRequestTimeMillis) < NasaFirmsConstants.MIN_REQUEST_INTERVAL_MS) {
      // Cooldown active, return current response or cached data without hitting API
      val existing = _fireDataResponse.value
      if (existing.state != FireDataSourceState.NOT_VERIFIED) {
        return existing
      }
    }

    lastRequestTimeMillis = now
    _dataSourceState.value = FireDataSourceState.CONNECTING

    // 3. Sensor Priority Loop (Section 6)
    // Try VIIRS NOAA-21 -> VIIRS NOAA-20 -> VIIRS SNPP -> MODIS
    var lastErrorResponse: FireDataResponse? = null

    for (sensorSource in NasaFirmsConstants.SENSOR_PRIORITY) {
      val response = dataSource.fetchFireData(
        mapKey = mapKey,
        source = sensorSource,
        areaCoordinates = areaCoordinates,
        dayRange = 1
      )

      // Handle Authentication Failure
      if (response.httpStatusCode in listOf(401, 403)) {
        credentialProvider.markInvalid()
        _credentialState.value = FireDataCredentialState.INVALID
        _fireDataResponse.value = response
        _dataSourceState.value = response.state
        return response
      }

      // Handle Rate Limit HTTP 429
      if (response.httpStatusCode == 429) {
        rateLimitBackoffUntilMillis = System.currentTimeMillis() + NasaFirmsConstants.RATE_LIMIT_BACKOFF_BASE_MS
        _fireDataResponse.value = response
        _dataSourceState.value = response.state
        return response
      }

      // If successful, save cache and return immediately
      if (response.state == FireDataSourceState.DATA_SOURCE_AVAILABLE ||
        response.state == FireDataSourceState.NO_DETECTIONS_IN_QUERY
      ) {
        cachedResponse = response
        _fireDataResponse.value = response
        _dataSourceState.value = response.state
        return response
      }

      lastErrorResponse = response
      // If error is network/timeout, no need to retry other sensors with same network
      if (response.state == FireDataSourceState.NETWORK_ERROR ||
        response.state == FireDataSourceState.TIMEOUT
      ) {
        break
      }
    }

    // 4. Offline / Failure Handling with Cache (Section 18 & 19)
    val fallbackCache = cachedResponse
    if (fallbackCache != null && (now - fallbackCache.fetchTimeMillis) < (24 * 60 * 60 * 1000L)) {
      val cacheAge = now - fallbackCache.fetchTimeMillis
      val cached = fallbackCache.copy(
        state = FireDataSourceState.CACHED,
        isCached = true,
        cacheAgeMillis = cacheAge
      )
      AppLogger.recordEvent(
        "Menampilkan data satelit dari Cache lokal (usia ${cacheAge / 1000} detik). Status: CACHED"
      )
      _fireDataResponse.value = cached
      _dataSourceState.value = FireDataSourceState.CACHED
      return cached
    }

    val finalResponse = lastErrorResponse ?: FireDataResponse.error(
      state = FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
      error = FireDataError.InvalidResponse("Semua sensor prioritas NASA FIRMS gagal dihubungi.")
    )

    _fireDataResponse.value = finalResponse
    _dataSourceState.value = finalResponse.state
    return finalResponse
  }

  override fun setMapKey(mapKey: String?): FireDataCredentialState {
    val state = credentialProvider.setMapKey(mapKey)
    _credentialState.value = state
    if (state == FireDataCredentialState.CONFIGURED) {
      AppLogger.recordEvent("NASA FIRMS MAP_KEY berhasil dikonfigurasi pada storage lokal.")
    } else if (state == FireDataCredentialState.NOT_CONFIGURED) {
      AppLogger.recordEvent("NASA FIRMS MAP_KEY dikosongkan.")
      _fireDataResponse.value = FireDataResponse.credentialRequired()
      _dataSourceState.value = FireDataSourceState.API_CREDENTIAL_REQUIRED
    }
    return state
  }

  override fun clearMapKey() {
    credentialProvider.clearMapKey()
    _credentialState.value = FireDataCredentialState.NOT_CONFIGURED
    _fireDataResponse.value = FireDataResponse.credentialRequired()
    _dataSourceState.value = FireDataSourceState.API_CREDENTIAL_REQUIRED
  }

  override fun getMapKey(): String? {
    return credentialProvider.getMapKey()
  }

  companion object {
    fun create(context: Context): RealFireDataRepository {
      val credProvider = ClientOnlyCredentialProvider.create(context)
      return RealFireDataRepository(credProvider)
    }
  }
}
