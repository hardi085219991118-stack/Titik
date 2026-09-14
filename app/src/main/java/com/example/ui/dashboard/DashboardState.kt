package com.example.ui.dashboard

import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.map.MapProviderInfo
import com.example.ui.map.MapStatus

/**
 * Explicit data state enum adhering to Section 6 of Prompt 003:
 * NOT_AVAILABLE: Belum ada data.
 * NOT_VERIFIED: Sumber data belum diintegrasikan/diverifikasi.
 * AVAILABLE: Data nyata terverifikasi tersedia.
 * ERROR: Terjadi kegagalan komunikasi/sistem.
 */
enum class DataState {
  NOT_AVAILABLE,
  NOT_VERIFIED,
  AVAILABLE,
  ERROR
}

data class DashboardState(
  val systemStatus: String = "APLIKASI AKTIF",
  val systemDetail: String = "Fondasi sistem, integrasi lokasi Android runtime (FIRE-004), & Peta Geografis (FIRE-005) terpasang (Verifikasi real-device pending). Sumber data satelit belum aktif.",

  // Map Foundation State (FIRE-005)
  val mapStatus: MapStatus = MapStatus.MAP_READY,
  val mapProviderName: String = MapProviderInfo.PROVIDER_NAME,
  val mapCredentialStatus: String = MapProviderInfo.CREDENTIAL_STATUS,

  // Location State (FIRE-004)
  val locationStatus: LocationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED,
  val deviceLocation: DeviceLocation? = null,
  val locationErrorMessage: String? = null,

  // Fire Data State (FIRE-006 - Real Fire Data Source)
  val fireDataState: DataState = DataState.NOT_VERIFIED,
  val fireDataSourceState: com.example.core.fire.FireDataSourceState = com.example.core.fire.FireDataSourceState.NOT_VERIFIED,
  val credentialState: com.example.core.fire.FireDataCredentialState = com.example.core.fire.FireDataCredentialState.NOT_CONFIGURED,
  val validFireRecordCount: Int? = null,
  val fireCountDisplay: String = "--",
  val fireStatusText: String = "FIRE DATA SOURCE NOT VERIFIED",
  val fireNote: String = "Sumber data titik api belum dihubungkan. Menampilkan '--' karena belum ada data (Bukan 0 titik api).",
  val fireRecords: List<com.example.core.fire.FireDataRecord> = emptyList(),

  // Satellite Data State (FIRE-006)
  val satelliteState: DataState = DataState.NOT_VERIFIED,
  val satelliteDisplay: String = "BELUM TERSEDIA",
  val satelliteNote: String = "DATA SOURCE NOT VERIFIED (Modul FIRE-006 belum aktif).",
  val satelliteSensorName: String = "NASA FIRMS (VIIRS NOAA-21 / NOAA-20 / SNPP / MODIS)",
  val freshnessLevel: com.example.core.fire.FreshnessLevel = com.example.core.fire.FreshnessLevel.FRESHNESS_UNKNOWN,
  val isCachedFireData: Boolean = false,
  val cacheAgeSeconds: Long = 0L,

  // Last Satellite Update State
  val lastUpdateState: DataState = DataState.NOT_AVAILABLE,
  val lastUpdateDisplay: String = "BELUM TERSEDIA",
  val lastUpdateNote: String = "Waktu akuisisi satelit belum tersedia. Waktu perangkat tidak disamakan dengan waktu satelit (Aturan 7).",
  val lastFetchDisplay: String = "BELUM PERNAH",

  val isRefreshSatelliteEnabled: Boolean = true,
  val isLoadingSatellite: Boolean = false,
  val refreshSatelliteNote: String = "Tekan untuk memperbarui data satelit NASA FIRMS.",

  // Security Architecture & Evidence Audit (Prompt 006B Section 5, 6, 13)
  val architectureStatus: String = "CLIENT_ONLY_LIMITATION",
  val credentialType: String = "CLIENT_SIDE_CREDENTIAL",
  val securityLimitation: String = "PRODUCTION_SECURITY_LIMITATION",
  val responseSha256Hash: String? = null,
  val rawRecordCount: Int = 0
)
