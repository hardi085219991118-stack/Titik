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

  // Fire Data State (FIRE-006 & FIRE-007 - tetap NOT_STARTED)
  val fireDataState: DataState = DataState.NOT_VERIFIED,
  val fireCountDisplay: String = "--",
  val fireStatusText: String = "FIRE DATA SOURCE NOT VERIFIED",
  val fireNote: String = "Sumber data titik api belum dihubungkan. Menampilkan '--' karena belum ada data (Bukan 0 titik api).",

  // Satellite Data State (FIRE-006 - tetap NOT_STARTED)
  val satelliteState: DataState = DataState.NOT_VERIFIED,
  val satelliteDisplay: String = "BELUM TERSEDIA",
  val satelliteNote: String = "DATA SOURCE NOT VERIFIED (Modul FIRE-006 belum aktif).",

  // Last Satellite Update State (tetap NOT_STARTED)
  val lastUpdateState: DataState = DataState.NOT_AVAILABLE,
  val lastUpdateDisplay: String = "BELUM TERSEDIA",
  val lastUpdateNote: String = "Waktu akuisisi satelit belum tersedia. Waktu perangkat tidak disamakan dengan waktu satelit (Aturan 7).",

  val isRefreshSatelliteEnabled: Boolean = false,
  val refreshSatelliteNote: String = "Tombol dinonaktifkan: Sumber data satelit (FIRE-006) belum diintegrasikan."
)
