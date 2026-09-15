package com.example.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.fire.FireDataCredentialState
import com.example.core.fire.FireDataRepository
import com.example.core.fire.FireDataResponse
import com.example.core.fire.FireDataSourceState
import com.example.core.fire.FreshnessLevel
import com.example.core.fire.RealFireDataRepository
import com.example.core.location.AndroidLocationTracker
import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.location.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DashboardViewModel(
  private val locationTracker: LocationTracker,
  private val fireRepository: FireDataRepository? = null
) : ViewModel() {

  private val _uiState = MutableStateFlow(DashboardState())
  val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

  private val utcDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
  }
  private val localTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

  init {
    combine(
      locationTracker.locationStatus,
      locationTracker.currentLocation,
      locationTracker.errorMessage
    ) { status, location, errorMsg ->
      _uiState.value = _uiState.value.copy(
        locationStatus = status,
        deviceLocation = location,
        locationErrorMessage = errorMsg
      )
    }.launchIn(viewModelScope)

    fireRepository?.let { repo ->
      combine(
        repo.fireDataResponse,
        repo.credentialState,
        repo.dataSourceState
      ) { response, credState, sourceState ->
        mapFireResponseToUiState(response, credState, sourceState)
      }.launchIn(viewModelScope)
    }
  }

  private fun mapFireResponseToUiState(
    response: FireDataResponse,
    credState: FireDataCredentialState,
    sourceState: FireDataSourceState
  ) {
    val current = _uiState.value
    val firstRecord = response.records.firstOrNull()

    val formattedAcqTime = if (firstRecord?.acquisitionTimestampMillis != null) {
      utcDateFormat.format(Date(firstRecord.acquisitionTimestampMillis))
    } else if (!firstRecord?.acqDate.isNullOrBlank()) {
      "${firstRecord?.acqDate} ${firstRecord?.acqTime} UTC"
    } else {
      "BELUM TERSEDIA"
    }

    val formattedFetchTime = if (response.fetchTimeMillis > 0) {
      localTimeFormat.format(Date(response.fetchTimeMillis))
    } else {
      "BELUM PERNAH"
    }

    val newState = when (sourceState) {
      FireDataSourceState.NOT_VERIFIED -> current.copy(
        fireDataState = DataState.NOT_VERIFIED,
        fireDataSourceState = FireDataSourceState.NOT_VERIFIED,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = if (credState == FireDataCredentialState.CONFIGURED) "READY FOR LIVE REQUEST" else "FIRE DATA SOURCE NOT VERIFIED",
        fireNote = if (credState == FireDataCredentialState.CONFIGURED) {
          "MAP_KEY terkonfigurasi (TEST_CREDENTIAL_ONLY). Tekan 'Perbarui Data Satelit' untuk melakukan live request ke NASA FIRMS."
        } else {
          "Sumber data titik api belum dihubungkan. Menampilkan '--' karena belum ada data (Bukan 0 titik api)."
        },
        fireRecords = emptyList(),
        satelliteState = DataState.NOT_VERIFIED,
        satelliteDisplay = if (credState == FireDataCredentialState.CONFIGURED) "READY FOR LIVE REQUEST" else "BELUM TERSEDIA",
        satelliteNote = if (credState == FireDataCredentialState.CONFIGURED) {
          "NASA FIRMS: READY FOR LIVE REQUEST. Kredensial terdeteksi, siap mengirim live HTTPS request."
        } else {
          "DATA SOURCE NOT VERIFIED (MAP_KEY belum dikonfigurasi)."
        },
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "BELUM TERSEDIA",
        lastUpdateNote = "Waktu akuisisi satelit belum tersedia. Waktu perangkat tidak disamakan dengan waktu satelit (Aturan 7).",
        isLoadingSatellite = false
      )

      FireDataSourceState.DATA_SOURCE_AVAILABLE -> current.copy(
        fireDataState = DataState.AVAILABLE,
        fireDataSourceState = FireDataSourceState.DATA_SOURCE_AVAILABLE,
        credentialState = credState,
        validFireRecordCount = response.validRecordCount,
        rawRecordCount = response.rawRecordCount,
        responseSha256Hash = response.responseSha256Hash,
        fireCountDisplay = response.validRecordCount.toString(),
        fireStatusText = "DATA SOURCE AVAILABLE",
        fireNote = "Ditemukan ${response.validRecordCount} deteksi titik api valid dari sensor ${response.sourceSensor}.",
        fireRecords = response.records,
        satelliteState = DataState.AVAILABLE,
        satelliteDisplay = response.sourceSensor,
        satelliteNote = "Sensor: ${response.sourceSensor} | Satelit: ${firstRecord?.satellite ?: "N/A"}",
        freshnessLevel = response.freshness,
        isCachedFireData = response.isCached,
        lastUpdateState = DataState.AVAILABLE,
        lastUpdateDisplay = formattedAcqTime,
        lastUpdateNote = "Waktu akuisisi satelit: $formattedAcqTime. Waktu fetch perangkat: $formattedFetchTime (TIDAK DISAMAKAN).",
        lastFetchDisplay = formattedFetchTime,
        isLoadingSatellite = false
      )

      FireDataSourceState.NO_DETECTIONS_IN_QUERY -> current.copy(
        fireDataState = DataState.AVAILABLE,
        fireDataSourceState = FireDataSourceState.NO_DETECTIONS_IN_QUERY,
        credentialState = credState,
        validFireRecordCount = 0,
        rawRecordCount = response.rawRecordCount,
        responseSha256Hash = response.responseSha256Hash,
        fireCountDisplay = "0",
        fireStatusText = "NO DETECTIONS IN QUERY",
        fireNote = "Tidak ada titik api terdeteksi dalam area query pada overpass satelit terakhir (${response.sourceSensor}).",
        fireRecords = emptyList(),
        satelliteState = DataState.AVAILABLE,
        satelliteDisplay = response.sourceSensor,
        satelliteNote = "Sensor: ${response.sourceSensor} | Status: 0 Deteksi dalam query",
        freshnessLevel = FreshnessLevel.FRESHNESS_UNKNOWN,
        isCachedFireData = response.isCached,
        lastUpdateState = DataState.AVAILABLE,
        lastUpdateDisplay = "0 DETEKSI",
        lastUpdateNote = "Query valid berhasil dieksekusi. Tidak ada hotspot teramati. Waktu fetch: $formattedFetchTime.",
        lastFetchDisplay = formattedFetchTime,
        isLoadingSatellite = false
      )

      FireDataSourceState.API_CREDENTIAL_REQUIRED -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.API_CREDENTIAL_REQUIRED,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = if (credState == FireDataCredentialState.INVALID) "INVALID CREDENTIAL" else "API CREDENTIAL REQUIRED",
        fireNote = if (credState == FireDataCredentialState.INVALID) {
          "MAP_KEY NASA FIRMS ditolak atau tidak valid. Daftarkan MAP_KEY resmi di https://firms.modaps.eosdis.nasa.gov."
        } else {
          "MAP_KEY NASA FIRMS belum dikonfigurasi. Daftarkan MAP_KEY resmi di https://firms.modaps.eosdis.nasa.gov."
        },
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = if (credState == FireDataCredentialState.INVALID) "INVALID CREDENTIAL" else "CREDENTIAL REQUIRED",
        satelliteNote = "MAP_KEY FIRMS diperlukan untuk mengakses web service NASA.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "BELUM TERSEDIA",
        isLoadingSatellite = false
      )

      FireDataSourceState.NETWORK_ERROR -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.NETWORK_ERROR,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = "NETWORK ERROR",
        fireNote = "Gagal menghubungi server NASA FIRMS. Periksa koneksi internet perangkat.",
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = "NETWORK ERROR",
        satelliteNote = "Koneksi internet terputus atau DNS gagal.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "BELUM TERSEDIA",
        isLoadingSatellite = false
      )

      FireDataSourceState.TIMEOUT -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.TIMEOUT,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = "TIMEOUT",
        fireNote = "Waktu koneksi ke server NASA FIRMS habis (Timeout).",
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = "TIMEOUT",
        satelliteNote = "Koneksi ke NASA FIRMS melebihi batas waktu.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "BELUM TERSEDIA",
        isLoadingSatellite = false
      )

      FireDataSourceState.RATE_LIMIT_EXCEEDED -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.RATE_LIMIT_EXCEEDED,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = "RATE LIMIT EXCEEDED (HTTP 429)",
        fireNote = "Permintaan melebihi kuota NASA FIRMS. Cooldown dan backoff sedang aktif.",
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = "RATE LIMITED",
        satelliteNote = "HTTP 429 Too Many Requests dari NASA FIRMS.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "BELUM TERSEDIA",
        isLoadingSatellite = false
      )

      FireDataSourceState.DATA_SOURCE_UNAVAILABLE -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = "DATA SOURCE UNAVAILABLE",
        fireNote = response.error?.message ?: "Server NASA FIRMS tidak dapat dihubungi.",
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = "UNAVAILABLE",
        satelliteNote = "Server NASA FIRMS mengalami gangguan.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "BELUM TERSEDIA",
        isLoadingSatellite = false
      )

      FireDataSourceState.INVALID_DATA_RESPONSE -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.INVALID_DATA_RESPONSE,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = "INVALID NASA FIRMS RESPONSE",
        fireNote = response.error?.message ?: "Payload CSV dari NASA FIRMS tidak valid.",
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = "INVALID NASA FIRMS RESPONSE",
        satelliteNote = "Format respons tidak dikenali.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "BELUM TERSEDIA",
        isLoadingSatellite = false
      )

      FireDataSourceState.CONNECTING -> current.copy(
        fireDataSourceState = FireDataSourceState.CONNECTING,
        isLoadingSatellite = true,
        fireStatusText = "MENGHUBUNGI NASA FIRMS...",
        satelliteDisplay = "MENGHUBUNGI NASA FIRMS...",
        refreshSatelliteNote = "MENGHUBUNGI NASA FIRMS..."
      )

      FireDataSourceState.CACHED -> current.copy(
        fireDataState = DataState.AVAILABLE,
        fireDataSourceState = FireDataSourceState.CACHED,
        credentialState = credState,
        validFireRecordCount = response.validRecordCount,
        fireCountDisplay = response.validRecordCount.toString(),
        fireStatusText = "CACHED DATA",
        fireNote = "Data cache tersimpan (${response.cacheAgeMillis / 1000}s lalu). BUKAN DATA LIVE.",
        fireRecords = response.records,
        satelliteState = DataState.AVAILABLE,
        satelliteDisplay = "${response.sourceSensor} (CACHE)",
        satelliteNote = "Menampilkan data lokal dari cache. Usia cache: ${response.cacheAgeMillis / 1000} detik.",
        freshnessLevel = response.freshness,
        isCachedFireData = true,
        cacheAgeSeconds = response.cacheAgeMillis / 1000,
        lastUpdateState = DataState.AVAILABLE,
        lastUpdateDisplay = formattedAcqTime,
        lastUpdateNote = "Waktu akuisisi satelit: $formattedAcqTime. Data berasal dari cache lokal.",
        lastFetchDisplay = formattedFetchTime,
        isLoadingSatellite = false
      )
    }

    val auditResult = com.example.core.fire.LiveApiDiagnosticAuditor.auditPipeline(
      credState = credState,
      sourceState = sourceState,
      response = response,
      queryArea = response.requestArea
    )

    val dataAgeDisplay = if (response.records.isNotEmpty() && response.fetchTimeMillis > 0) {
      val latestAcq = response.records.mapNotNull { it.acquisitionTimestampMillis }.maxOrNull()
      if (latestAcq != null) {
        val diffMs = response.fetchTimeMillis - latestAcq
        if (diffMs >= 0) {
          val hours = diffMs / (1000 * 60 * 60)
          val minutes = (diffMs % (1000 * 60 * 60)) / (1000 * 60)
          "${hours}j ${minutes}m"
        } else {
          "UNKNOWN"
        }
      } else {
        "BELUM TERSEDIA"
      }
    } else {
      "BELUM TERSEDIA"
    }

    val acqRangeDisplay = if (response.minAcquisitionTimeMillis != null && response.maxAcquisitionTimeMillis != null) {
      val minStr = utcDateFormat.format(Date(response.minAcquisitionTimeMillis))
      val maxStr = utcDateFormat.format(Date(response.maxAcquisitionTimeMillis))
      if (minStr == maxStr) minStr else "$minStr s/d $maxStr"
    } else if (firstRecord != null) {
      formattedAcqTime
    } else {
      "BELUM TERSEDIA"
    }

    val satDistDisplay = if (response.satelliteDistribution.isNotEmpty()) {
      response.satelliteDistribution.entries.joinToString(", ") { "${it.key}: ${it.value}" }
    } else if (firstRecord?.satellite != null) {
      firstRecord.satellite
    } else {
      "BELUM TERSEDIA"
    }

    val instDistDisplay = if (response.instrumentDistribution.isNotEmpty()) {
      response.instrumentDistribution.entries.joinToString(", ") { "${it.key}: ${it.value}" }
    } else if (firstRecord?.instrument != null) {
      firstRecord.instrument
    } else {
      "BELUM TERSEDIA"
    }

    _uiState.value = newState.copy(
      liveVerificationGate = auditResult.gate,
      diagnosticCause = auditResult.cause,
      diagnosticDetail = auditResult.detail,
      boundingBoxValidationStatus = auditResult.boundingBoxStatus,
      endpointAudited = auditResult.endpoint,
      httpStatusCode = response.httpStatusCode,
      invalidRecordCount = response.invalidRecordCount,
      queryArea = response.requestArea,
      dayRange = response.requestDayRange,
      acquisitionRangeDisplay = acqRangeDisplay,
      satelliteDistributionDisplay = satDistDisplay,
      instrumentDistributionDisplay = instDistDisplay,
      dataAgeDisplay = dataAgeDisplay,
      responseSha256Hash = response.responseSha256Hash
    )
  }

  fun requestLocation(context: Context) {
    locationTracker.requestLocation(context)
  }

  fun onPermissionResult(granted: Boolean, isPermanentlyDenied: Boolean = false, context: Context? = null) {
    if (granted) {
      if (context != null) {
        locationTracker.requestLocation(context)
      }
    } else {
      locationTracker.markPermissionDenied(isPermanentlyDenied)
    }
  }

  fun refreshLocation(context: Context) {
    locationTracker.requestLocation(context)
  }

  fun setMapStatus(status: com.example.ui.map.MapStatus) {
    _uiState.value = _uiState.value.copy(mapStatus = status)
  }

  fun refreshFireData(force: Boolean = false) {
    if (fireRepository == null) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoadingSatellite = true)
      fireRepository.refreshFireData(force = force)
    }
  }

  fun setMapKey(key: String?): FireDataCredentialState {
    return fireRepository?.setMapKey(key) ?: FireDataCredentialState.NOT_CONFIGURED
  }

  fun clearMapKey() {
    fireRepository?.clearMapKey()
  }

  fun getMapKey(): String? {
    return fireRepository?.getMapKey()
  }

  override fun onCleared() {
    super.onCleared()
    locationTracker.stopTracking()
  }

  companion object {
    fun create(context: Context): DashboardViewModel {
      val tracker = AndroidLocationTracker(context.applicationContext)
      val fireRepo = RealFireDataRepository.create(context.applicationContext)
      return DashboardViewModel(tracker, fireRepo)
    }
  }
}

