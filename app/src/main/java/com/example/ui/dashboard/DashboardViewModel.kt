package com.example.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.location.AndroidLocationTracker
import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.location.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn

class DashboardViewModel(
  private val locationTracker: LocationTracker
) : ViewModel() {

  private val _uiState = MutableStateFlow(DashboardState())
  val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

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

  override fun onCleared() {
    super.onCleared()
    locationTracker.stopTracking()
  }

  companion object {
    fun create(context: Context): DashboardViewModel {
      val tracker = AndroidLocationTracker(context.applicationContext)
      return DashboardViewModel(tracker)
    }
  }
}
