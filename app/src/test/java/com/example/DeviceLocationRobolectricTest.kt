package com.example

import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.location.LocationTracker
import com.example.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceLocationRobolectricTest {

  @Test
  fun `test location sanity check rule 17`() {
    // Valid location
    val valid = DeviceLocation(
      latitude = -2.123456,
      longitude = 114.654321,
      accuracyMeters = 5.0f,
      timeMillis = System.currentTimeMillis(),
      provider = "gps"
    )
    assertTrue("Valid location should pass sanity check", valid.isValid())

    // Boundary edge cases
    assertTrue(DeviceLocation(latitude = 90.0, longitude = 180.0, timeMillis = 1000L).isValid())
    assertTrue(DeviceLocation(latitude = -90.0, longitude = -180.0, timeMillis = 1000L).isValid())

    // Invalid latitudes
    assertFalse(DeviceLocation(latitude = 90.0001, longitude = 0.0, timeMillis = 1000L).isValid())
    assertFalse(DeviceLocation(latitude = -90.0001, longitude = 0.0, timeMillis = 1000L).isValid())

    // Invalid longitudes
    assertFalse(DeviceLocation(latitude = 0.0, longitude = 180.0001, timeMillis = 1000L).isValid())
    assertFalse(DeviceLocation(latitude = 0.0, longitude = -180.0001, timeMillis = 1000L).isValid())

    // Invalid negative accuracy
    assertFalse(DeviceLocation(latitude = 0.0, longitude = 0.0, accuracyMeters = -1f, timeMillis = 1000L).isValid())

    // Invalid non-positive timestamp
    assertFalse(DeviceLocation(latitude = 0.0, longitude = 0.0, timeMillis = 0L).isValid())
    assertFalse(DeviceLocation(latitude = 0.0, longitude = 0.0, timeMillis = -500L).isValid())

    // Invalid NaN / Infinity (Prompt 005A Section 10)
    assertFalse(DeviceLocation(latitude = Double.NaN, longitude = 0.0, timeMillis = 1000L).isValid())
    assertFalse(DeviceLocation(latitude = 0.0, longitude = Double.POSITIVE_INFINITY, timeMillis = 1000L).isValid())
  }

  @Test
  fun `test location source metadata and mock detection Prompt 005A and 005B`() {
    val liveGps = DeviceLocation(latitude = -2.15, longitude = 114.55, timeMillis = 1000L, provider = "gps")
    assertEquals("GPS", liveGps.locationSource)
    assertFalse("Cloud container / emulator cannot claim real device verified", liveGps.isRealDeviceVerified)

    val cachedGps = DeviceLocation(latitude = -2.15, longitude = 114.55, timeMillis = 1000L, provider = "gps", isFromCache = true)
    assertEquals("CACHED (GPS)", cachedGps.locationSource)

    val mockLoc = DeviceLocation(latitude = -2.15, longitude = 114.55, timeMillis = 1000L, provider = "gps", isMock = true)
    assertEquals("MOCK / VIRTUAL", mockLoc.locationSource)
    assertEquals(com.example.core.location.LocationVerificationLevel.MOCK, mockLoc.verificationLevel)
  }

  @Test
  fun `test prompt 005b runtime environment and verification levels`() {
    val detectedEnv = com.example.core.location.RuntimeEnvironment.detect()
    // In Robolectric / cloud container, it is not a real physical device
    assertTrue(
      "Container/Robolectric environment must NOT be detected as REAL_PHYSICAL_DEVICE",
      detectedEnv != com.example.core.location.RuntimeEnvironment.REAL_PHYSICAL_DEVICE
    )

    // Verify verification level cannot be REAL_DEVICE_VERIFIED when in virtual runtime
    val virtualLoc = DeviceLocation(
      latitude = -2.123456,
      longitude = 114.654321,
      timeMillis = System.currentTimeMillis(),
      runtimeEnvironment = com.example.core.location.RuntimeEnvironment.VIRTUAL_DEVICE,
      verificationLevel = com.example.core.location.LocationVerificationLevel.VIRTUAL
    )
    assertFalse(virtualLoc.isRealDeviceVerified)
    assertEquals(com.example.core.location.LocationVerificationLevel.VIRTUAL, virtualLoc.verificationLevel)
  }

  @Test
  fun `test prompt 005b location age and stale location check`() {
    val now = System.currentTimeMillis()

    // Fresh location (5 seconds ago)
    val freshLoc = DeviceLocation(
      latitude = -2.15,
      longitude = 114.55,
      timeMillis = now - 5000L
    )
    assertFalse("5 seconds old location must not be stale", freshLoc.isStale(now))
    assertEquals("5 detik lalu", freshLoc.getLocationAgeDisplay(now))

    // Stale location (> 15 minutes = 900_000 ms)
    val staleLoc = DeviceLocation(
      latitude = -2.15,
      longitude = 114.55,
      timeMillis = now - (16 * 60 * 1000L) // 16 minutes ago
    )
    assertTrue("16 minutes old location must be flagged as stale (> 15 menit)", staleLoc.isStale(now))
    assertEquals("16 menit lalu", staleLoc.getLocationAgeDisplay(now))
  }

  @Test
  fun `test prompt 005b feature registry contract FIRE-004 status`() {
    val fire004 = com.example.core.registry.FeatureRegistry.getFeature("FIRE-004")
    assertNotNull("FIRE-004 must exist in registry", fire004)
    assertEquals(
      "FIRE-004 must be REAL_DEVICE_VERIFICATION_PENDING until physical device verification",
      com.example.core.contract.FeatureStatus.REAL_DEVICE_VERIFICATION_PENDING,
      fire004?.status
    )
  }

  @Test
  fun `test dashboard viewmodel location states 1 to 6`() = runTest {
    val fakeTracker = FakeLocationTracker()
    val viewModel = DashboardViewModel(fakeTracker)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()

    // 1. Permission required
    fakeTracker.setStatus(LocationStatus.LOCATION_PERMISSION_REQUIRED)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    assertEquals(LocationStatus.LOCATION_PERMISSION_REQUIRED, viewModel.uiState.value.locationStatus)
    assertNull(viewModel.uiState.value.deviceLocation)

    // 2. Permission denied
    fakeTracker.setStatus(LocationStatus.LOCATION_PERMISSION_DENIED)
    fakeTracker.setError("Permission Denied by user")
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    assertEquals(LocationStatus.LOCATION_PERMISSION_DENIED, viewModel.uiState.value.locationStatus)
    assertEquals("Permission Denied by user", viewModel.uiState.value.locationErrorMessage)

    // 3. Provider disabled
    fakeTracker.setStatus(LocationStatus.LOCATION_PROVIDER_DISABLED)
    fakeTracker.setError("GPS disabled on device")
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    assertEquals(LocationStatus.LOCATION_PROVIDER_DISABLED, viewModel.uiState.value.locationStatus)

    // 4. Loading
    fakeTracker.setStatus(LocationStatus.LOCATION_LOADING)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    assertEquals(LocationStatus.LOCATION_LOADING, viewModel.uiState.value.locationStatus)

    // 5. Location available
    val sampleFix = DeviceLocation(
      latitude = -2.15,
      longitude = 114.55,
      accuracyMeters = 8.5f,
      timeMillis = 1700000000000L,
      provider = "gps"
    )
    fakeTracker.setLocation(sampleFix)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    assertEquals(LocationStatus.LOCATION_AVAILABLE, viewModel.uiState.value.locationStatus)
    assertNotNull(viewModel.uiState.value.deviceLocation)
    assertEquals(-2.15, viewModel.uiState.value.deviceLocation?.latitude ?: 0.0, 0.0001)
    assertEquals(114.55, viewModel.uiState.value.deviceLocation?.longitude ?: 0.0, 0.0001)
    assertEquals(8.5f, viewModel.uiState.value.deviceLocation?.accuracyMeters ?: 0f, 0.01f)

    // 6. Location error
    fakeTracker.setStatus(LocationStatus.LOCATION_ERROR)
    fakeTracker.setError("GPS timeout")
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    assertEquals(LocationStatus.LOCATION_ERROR, viewModel.uiState.value.locationStatus)
    assertEquals("GPS timeout", viewModel.uiState.value.locationErrorMessage)
  }

  private class FakeLocationTracker : LocationTracker {
    private val _status = MutableStateFlow(LocationStatus.LOCATION_PERMISSION_REQUIRED)
    override val locationStatus: StateFlow<LocationStatus> = _status

    private val _location = MutableStateFlow<DeviceLocation?>(null)
    override val currentLocation: StateFlow<DeviceLocation?> = _location

    private val _error = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _error

    fun setStatus(status: LocationStatus) {
      _status.value = status
    }

    fun setLocation(loc: DeviceLocation) {
      _location.value = loc
      _status.value = LocationStatus.LOCATION_AVAILABLE
    }

    fun setError(msg: String) {
      _error.value = msg
    }

    override fun requestLocation(context: android.content.Context) {}
    override fun markPermissionDenied(isPermanentlyDenied: Boolean) {
      _status.value = LocationStatus.LOCATION_PERMISSION_DENIED
    }
    override fun stopTracking() {}
  }
}
