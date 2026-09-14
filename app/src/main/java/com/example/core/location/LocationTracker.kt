package com.example.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
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
  fun refreshLocation(context: Context) {
    requestLocation(context)
  }
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
  private var cancellationSignal: CancellationSignal? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private var timeoutRunnable: Runnable? = null

  override fun requestLocation(context: Context) {
    // 1. Audit Permissions (Section 3: High Accuracy & Permission Handling)
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

    // 2. Audit Location Settings (Section 4: Location Services Enabled Check)
    val isLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      runCatching { locationManager.isLocationEnabled }.getOrDefault(false)
    } else {
      runCatching {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
      }.getOrDefault(false)
    }

    val isGpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    val isNetworkEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)

    if (!isLocationEnabled || (!isGpsEnabled && !isNetworkEnabled)) {
      _locationStatus.value = LocationStatus.LOCATION_PROVIDER_DISABLED
      _errorMessage.value = "LOCATION PROVIDER DISABLED: Layanan lokasi / GPS perangkat nonaktif. Mohon aktifkan GPS pada Pengaturan Perangkat."
      return
    }

    // 3. High Accuracy Provider Selection (Section 3)
    // Jangan meminta GPS_PROVIDER high accuracy jika ACCESS_FINE_LOCATION tidak diberikan
    val providerToUse = when {
      hasFine && isGpsEnabled -> LocationManager.GPS_PROVIDER
      isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
      isGpsEnabled -> LocationManager.GPS_PROVIDER
      else -> LocationManager.PASSIVE_PROVIDER
    }

    // 4. Set status loading
    _locationStatus.value = LocationStatus.LOCATION_LOADING
    _errorMessage.value = null

    // 5. Fallback ke cached lastKnownLocation dengan label CACHED LOCATION (Section 2)
    try {
      val lastKnown = locationManager.getLastKnownLocation(providerToUse)
      if (lastKnown != null) {
        val cachedLoc = lastKnown.toDeviceLocation(isCache = true)
        if (cachedLoc.isValid()) {
          _currentLocation.value = cachedLoc
          AppLogger.recordEvent("Cached location dimuat: lat=${cachedLoc.latitude}, lon=${cachedLoc.longitude}, time=${cachedLoc.timeMillis}")
        }
      }

      // Hentikan request/listener lama jika ada
      stopTracking()

      // 6. Section 2: Prioritaskan getCurrentLocation() untuk lokasi terbaru
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val signal = CancellationSignal()
        cancellationSignal = signal
        try {
          locationManager.getCurrentLocation(
            providerToUse,
            signal,
            ContextCompat.getMainExecutor(context)
          ) { location ->
            if (location != null) {
              handleFreshLocation(location)
            }
          }
        } catch (e: SecurityException) {
          _locationStatus.value = LocationStatus.LOCATION_PERMISSION_DENIED
          _errorMessage.value = "SecurityException pada getCurrentLocation: ${e.message}"
        } catch (_: Throwable) {
          // Fallback ke requestLocationUpdates
        }
      }

      // 7. Request updates untuk fresh single/continuous fix
      val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
          handleFreshLocation(location)
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

      // Pasang timeout 20 detik (Section 14: Jika gagal update -> LOCATION UPDATE FAILED)
      val timeout = Runnable {
        if (_locationStatus.value == LocationStatus.LOCATION_LOADING) {
          val current = _currentLocation.value
          if (current != null) {
            // Tetap gunakan cached yang ada jika ada, namun update status
            _locationStatus.value = LocationStatus.LOCATION_AVAILABLE
            AppLogger.recordEvent("Timeout fresh fix; menggunakan cached location fix.")
          } else {
            _locationStatus.value = LocationStatus.LOCATION_ERROR
            _errorMessage.value = "LOCATION UPDATE FAILED: Timeout mencari sinyal GPS nyata."
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

  private fun handleFreshLocation(location: Location) {
    cancelTimeout()
    val freshLoc = location.toDeviceLocation(isCache = false)

    if (freshLoc.isMock) {
      AppLogger.recordError(
        AppError(
          type = ErrorType.UNKNOWN_ERROR,
          message = "CRITICAL: Mock/Virtual location provider terdeteksi (lat=${freshLoc.latitude}, lon=${freshLoc.longitude})",
          source = "AndroidLocationTracker",
          recoveryAction = "Nonaktifkan mock provider pada pengaturan perangkat."
        )
      )
    }

    if (freshLoc.isValid()) {
      _currentLocation.value = freshLoc
      _locationStatus.value = LocationStatus.LOCATION_AVAILABLE
      _errorMessage.value = null
      AppLogger.recordEvent("Fresh location fix diterima: ${freshLoc.latitude}, ${freshLoc.longitude}, source=${freshLoc.locationSource}, verification=${freshLoc.verificationLevel}")
    } else {
      _locationStatus.value = LocationStatus.LOCATION_ERROR
      _errorMessage.value = "Data lokasi tidak lolos sanity check geografis (-90..90, -180..180)."
    }

    // Stop tracking setelah fix berhasil didapat untuk efisiensi baterai
    stopTracking()
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
    cancellationSignal?.cancel()
    cancellationSignal = null

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

/**
 * Section 5, 7, 8, 9 Prompt 005B: Konversi Location Android ke DeviceLocation.
 */
fun Location.toDeviceLocation(
  isCache: Boolean,
  runtimeEnvOverride: RuntimeEnvironment? = null
): DeviceLocation {
  val isMockLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    this.isMock
  } else {
    @Suppress("DEPRECATION")
    this.isFromMockProvider
  }

  val runtimeEnv = runtimeEnvOverride ?: RuntimeEnvironment.detect()
  val isStaleLocation = if (this.time > 0) {
    (System.currentTimeMillis() - this.time) > 15 * 60 * 1000L
  } else false

  val verificationLevel = when {
    isMockLocation -> LocationVerificationLevel.MOCK
    runtimeEnv == RuntimeEnvironment.EMULATOR || runtimeEnv == RuntimeEnvironment.VIRTUAL_DEVICE -> LocationVerificationLevel.VIRTUAL
    isCache || isStaleLocation -> LocationVerificationLevel.CACHED
    runtimeEnv == RuntimeEnvironment.REAL_PHYSICAL_DEVICE -> LocationVerificationLevel.REAL_DEVICE_UNVERIFIED
    else -> LocationVerificationLevel.UNVERIFIED
  }

  return DeviceLocation(
    latitude = this.latitude,
    longitude = this.longitude,
    accuracyMeters = if (this.hasAccuracy()) this.accuracy else null,
    timeMillis = this.time,
    provider = this.provider,
    altitudeMeters = if (this.hasAltitude()) this.altitude else null,
    speedMps = if (this.hasSpeed()) this.speed else null,
    isFromCache = isCache,
    isMock = isMockLocation,
    isRealDeviceVerified = false, // Cloud emulator / preview environment cannot claim real physical device verification
    runtimeEnvironment = runtimeEnv,
    verificationLevel = verificationLevel
  )
}
