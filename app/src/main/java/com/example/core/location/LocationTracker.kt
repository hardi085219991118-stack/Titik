package com.example.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.core.logging.AppError
import com.example.core.logging.AppLogger
import com.example.core.logging.ErrorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface LocationTracker {
  val locationStatus: StateFlow<LocationStatus>
  val currentLocation: StateFlow<DeviceLocation?>
  val errorMessage: StateFlow<String?>

  fun requestLocation(context: Context)
  fun markPermissionDenied(isPermanentlyDenied: Boolean)
  fun stopTracking()
}

class AndroidLocationTracker(
  private val applicationContext: Context
) : LocationTracker {

  private val _locationStatus = MutableStateFlow(LocationStatus.LOCATION_PERMISSION_REQUIRED)
  override val locationStatus: StateFlow<LocationStatus> = _locationStatus.asStateFlow()

  private val _currentLocation = MutableStateFlow<DeviceLocation?>(null)
  override val currentLocation: StateFlow<DeviceLocation?> = _currentLocation.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private var activeListener: LocationListener? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private var timeoutRunnable: Runnable? = null

  override fun requestLocation(context: Context) {
    // 1. Periksa Permissions
    val hasFine = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val hasCoarse = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasFine && !hasCoarse) {
      _locationStatus.value = LocationStatus.LOCATION_PERMISSION_REQUIRED
      _errorMessage.value = "Izin lokasi (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION) belum diberikan."
      return
    }

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
      _locationStatus.value = LocationStatus.LOCATION_ERROR
      _errorMessage.value = "LocationManager tidak tersedia pada perangkat."
      AppLogger.recordError(
        AppError(
          type = ErrorType.UNKNOWN_ERROR,
          message = "LocationManager system service null",
          source = "AndroidLocationTracker",
          recoveryAction = "Periksa konfigurasi sistem perangkat."
        )
      )
      return
    }

    // 2. Periksa Provider GPS & Network
    val isGpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    val isNetworkEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)

    if (!isGpsEnabled && !isNetworkEnabled) {
      _locationStatus.value = LocationStatus.LOCATION_PROVIDER_DISABLED
      _errorMessage.value = "Layanan lokasi / GPS perangkat nonaktif. Mohon aktifkan GPS pada pengaturan perangkat."
      return
    }

    // 3. Status Memuat Lokasi Nyata
    _locationStatus.value = LocationStatus.LOCATION_LOADING
    _errorMessage.value = null

    // Periksa cached lastKnownLocation terlebih dahulu secara aman jika ada
    val providerToUse = when {
      isGpsEnabled -> LocationManager.GPS_PROVIDER
      else -> LocationManager.NETWORK_PROVIDER
    }

    try {
      val lastKnown = locationManager.getLastKnownLocation(providerToUse)
      if (lastKnown != null) {
        val cachedLoc = lastKnown.toDeviceLocation(isCache = true)
        if (cachedLoc.isValid()) {
          _currentLocation.value = cachedLoc
          _locationStatus.value = LocationStatus.LOCATION_AVAILABLE
          AppLogger.recordEvent("Cached location fix dimuat: lat=${cachedLoc.latitude}, lon=${cachedLoc.longitude}")
        }
      }

      // Hentikan listener lama jika ada
      stopTracking()

      // Buat LocationListener baru untuk request fresh fix
      val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
          cancelTimeout()
          val freshLoc = location.toDeviceLocation(isCache = false)
          if (freshLoc.isValid()) {
            _currentLocation.value = freshLoc
            _locationStatus.value = LocationStatus.LOCATION_AVAILABLE
            _errorMessage.value = null
            AppLogger.recordEvent("Real device location fix diterima: ${freshLoc.latitude}, ${freshLoc.longitude}, acc=±${freshLoc.accuracyMeters}m")
          } else {
            _locationStatus.value = LocationStatus.LOCATION_ERROR
            _errorMessage.value = "Data lokasi tidak lolos sanity check geografis (-90..90, -180..180)."
          }
          // Hentikan tracking setelah single fix diperoleh untuk menghemat baterai & mencegah leak
          stopTracking()
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {
          _locationStatus.value = LocationStatus.LOCATION_PROVIDER_DISABLED
          _errorMessage.value = "Provider $provider dinonaktifkan oleh pengguna."
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
      }

      activeListener = listener

      // Pasang timeout 20 detik untuk mencegah infinite loading
      val timeout = Runnable {
        if (_locationStatus.value == LocationStatus.LOCATION_LOADING) {
          if (_currentLocation.value != null) {
            _locationStatus.value = LocationStatus.LOCATION_AVAILABLE
          } else {
            _locationStatus.value = LocationStatus.LOCATION_ERROR
            _errorMessage.value = "Timeout mencari sinyal GPS nyata. Silakan pastikan berada di area terbuka dan coba lagi."
          }
          stopTracking()
        }
      }
      timeoutRunnable = timeout
      mainHandler.postDelayed(timeout, 20_000)

      locationManager.requestLocationUpdates(
        providerToUse,
        1000L,
        1f,
        listener,
        Looper.getMainLooper()
      )

    } catch (e: SecurityException) {
      _locationStatus.value = LocationStatus.LOCATION_PERMISSION_DENIED
      _errorMessage.value = "SecurityException: Izin lokasi ditolak oleh sistem."
      AppLogger.recordError(
        AppError(
          type = ErrorType.LOCATION_PERMISSION_DENIED,
          message = e.message ?: "SecurityException saat mengakses lokasi",
          source = "AndroidLocationTracker",
          recoveryAction = "Minta izin akses lokasi kepada pengguna."
        )
      )
    } catch (e: Exception) {
      _locationStatus.value = LocationStatus.LOCATION_ERROR
      _errorMessage.value = "Gagal mengakses layanan lokasi: ${e.localizedMessage}"
      AppLogger.recordError(
        AppError(
          type = ErrorType.UNKNOWN_ERROR,
          message = e.message ?: "Exception saat requestLocation",
          source = "AndroidLocationTracker",
          recoveryAction = "Coba lagi pembaruan lokasi."
        )
      )
    }
  }

  override fun markPermissionDenied(isPermanentlyDenied: Boolean) {
    _locationStatus.value = LocationStatus.LOCATION_PERMISSION_DENIED
    _errorMessage.value = if (isPermanentlyDenied) {
      "Izin lokasi ditolak secara permanen. Mohon buka Pengaturan Aplikasi untuk mengaktifkan izin lokasi."
    } else {
      "LOCATION PERMISSION DENIED: Aplikasi memerlukan izin lokasi nyata untuk menentukan koordinat."
    }
    AppLogger.recordEvent("User menolak izin lokasi (permanen=$isPermanentlyDenied)")
  }

  override fun stopTracking() {
    cancelTimeout()
    activeListener?.let { listener ->
      val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
      try {
        lm?.removeUpdates(listener)
      } catch (_: SecurityException) {
      }
      activeListener = null
    }
  }

  private fun cancelTimeout() {
    timeoutRunnable?.let {
      mainHandler.removeCallbacks(it)
      timeoutRunnable = null
    }
  }
}

private fun Location.toDeviceLocation(isCache: Boolean): DeviceLocation {
  return DeviceLocation(
    latitude = this.latitude,
    longitude = this.longitude,
    accuracyMeters = if (this.hasAccuracy()) this.accuracy else null,
    timeMillis = this.time,
    provider = this.provider,
    altitudeMeters = if (this.hasAltitude()) this.altitude else null,
    speedMps = if (this.hasSpeed()) this.speed else null,
    isFromCache = isCache
  )
}
