package com.example.ui.map

import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.map.MapProviderInfo

/**
 * UI State untuk MapScreen sesuai Section 11, 12, dan 15 Prompt 005.
 *
 * Batasan:
 * - fireMarkerCount WAJIB selalu 0 (ZERO FIRE MARKERS).
 * - locationStatus diambil dari FIRE-004.
 */
data class MapUiState(
  val mapStatus: MapStatus = MapStatus.MAP_LOADING,
  val mapErrorMessage: String? = null,
  val mapProviderName: String = MapProviderInfo.PROVIDER_NAME,
  val credentialStatus: String = MapProviderInfo.CREDENTIAL_STATUS,

  // Location Integration (FIRE-004)
  val locationStatus: LocationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED,
  val deviceLocation: DeviceLocation? = null,
  val isLocationValid: Boolean = false,
  val coordinateErrorMessage: String? = null,

  // Camera & Interaction
  val isCenteredOnUser: Boolean = false,

  // Strict Zero-Dummy Constraint (Section 15)
  val fireMarkerCount: Int = 0
)
