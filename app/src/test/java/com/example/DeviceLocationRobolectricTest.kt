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

  @Test
  fun `test valid coordinate Prompt 005C`() {
    val valid = DeviceLocation(
      latitude = -2.585765,
      longitude = 114.441215,
      accuracyMeters = 5.3f,
      timeMillis = 1700000000000L,
      provider = "gps"
    )
    assertTrue("Real device coordinate must pass validation", valid.isValid())
  }

  @Test
  fun `test invalid latitude Prompt 005C`() {
    assertFalse(DeviceLocation(latitude = 90.00001, longitude = 100.0, timeMillis = 1000L).isValid())
    assertFalse(DeviceLocation(latitude = -90.00001, longitude = 100.0, timeMillis = 1000L).isValid())
    assertFalse(DeviceLocation(latitude = Double.NaN, longitude = 100.0, timeMillis = 1000L).isValid())
    assertFalse(DeviceLocation(latitude = Double.POSITIVE_INFINITY, longitude = 100.0, timeMillis = 1000L).isValid())
    assertFalse(DeviceLocation(latitude = Double.NEGATIVE_INFINITY, longitude = 100.0, timeMillis = 1000L).isValid())
  }

  @Test
  fun `test invalid longitude Prompt 005C`() {
    assertFalse(DeviceLocation(latitude = 0.0, longitude = 180.00001, timeMillis = 1000L).isValid())
    assertFalse(DeviceLocation(latitude = 0.0, longitude = -180.00001, timeMillis = 1000L).isValid())
    assertFalse(DeviceLocation(latitude = 0.0, longitude = Double.NaN, timeMillis = 1000L).isValid())
    assertFalse(DeviceLocation(latitude = 0.0, longitude = Double.POSITIVE_INFINITY, timeMillis = 1000L).isValid())
    assertFalse(DeviceLocation(latitude = 0.0, longitude = Double.NEGATIVE_INFINITY, timeMillis = 1000L).isValid())
  }

  @Test
  fun `test null location Prompt 005C`() {
    val fakeTracker = FakeLocationTracker()
    assertNull(fakeTracker.currentLocation.value)
    assertEquals(LocationStatus.LOCATION_PERMISSION_REQUIRED, fakeTracker.locationStatus.value)
  }

  @Test
  fun `test mock location detection Prompt 005C`() {
    val mockLoc = DeviceLocation(
      latitude = -2.585765,
      longitude = 114.441215,
      timeMillis = 1000L,
      provider = "gps",
      isMock = true
    )
    assertEquals("MOCK / VIRTUAL", mockLoc.locationSource)
    assertEquals(com.example.core.location.LocationVerificationLevel.MOCK, mockLoc.verificationLevel)
    assertFalse("Mock location must never be verified as real device", mockLoc.isRealDeviceVerified)
  }

  @Test
  fun `test cached location source and level Prompt 005C`() {
    val cachedLoc = DeviceLocation(
      latitude = -2.585765,
      longitude = 114.441215,
      timeMillis = 1000L,
      provider = "gps",
      isFromCache = true
    )
    assertEquals("CACHED (GPS)", cachedLoc.locationSource)
    assertEquals(com.example.core.location.LocationVerificationLevel.CACHED, cachedLoc.verificationLevel)
  }

  @Test
  fun `test fresh location fix Prompt 005C`() {
    val now = System.currentTimeMillis()
    val freshFix = DeviceLocation(
      latitude = -2.585765,
      longitude = 114.441215,
      accuracyMeters = 5.3f,
      timeMillis = now,
      provider = "gps",
      isFromCache = false,
      isMock = false
    )
    assertEquals("GPS", freshFix.locationSource)
    assertFalse(freshFix.isFromCache)
    assertFalse(freshFix.isMock)
    assertFalse(freshFix.isStale(now))
  }

  @Test
  fun `test location sources GPS network fused unknown Prompt 005C`() {
    val gps = DeviceLocation(latitude = 0.0, longitude = 0.0, timeMillis = 1L, provider = "gps")
    assertEquals("GPS", gps.locationSource)

    val net = DeviceLocation(latitude = 0.0, longitude = 0.0, timeMillis = 1L, provider = "network")
    assertEquals("NETWORK", net.locationSource)

    val fused = DeviceLocation(latitude = 0.0, longitude = 0.0, timeMillis = 1L, provider = "fused")
    assertEquals("FUSED", fused.locationSource)

    val passive = DeviceLocation(latitude = 0.0, longitude = 0.0, timeMillis = 1L, provider = "passive")
    assertEquals("PASSIVE", passive.locationSource)

    val unk = DeviceLocation(latitude = 0.0, longitude = 0.0, timeMillis = 1L, provider = null)
    assertEquals("UNKNOWN", unk.locationSource)
  }

  @Test
  fun `test emulator and virtual environments Prompt 005C`() {
    val emuLevel = DeviceLocation.resolveVerificationLevel(
      isMock = false,
      isFromCache = false,
      runtimeEnvironment = com.example.core.location.RuntimeEnvironment.EMULATOR,
      isRealDeviceVerified = false
    )
    assertEquals(com.example.core.location.LocationVerificationLevel.VIRTUAL, emuLevel)

    val virtLevel = DeviceLocation.resolveVerificationLevel(
      isMock = false,
      isFromCache = false,
      runtimeEnvironment = com.example.core.location.RuntimeEnvironment.VIRTUAL_DEVICE,
      isRealDeviceVerified = false
    )
    assertEquals(com.example.core.location.LocationVerificationLevel.VIRTUAL, virtLevel)

    val cloudLevel = DeviceLocation.resolveVerificationLevel(
      isMock = false,
      isFromCache = false,
      runtimeEnvironment = com.example.core.location.RuntimeEnvironment.CLOUD_CONTAINER,
      isRealDeviceVerified = false
    )
    assertEquals(com.example.core.location.LocationVerificationLevel.VIRTUAL, cloudLevel)
  }

  @Test
  fun `test unknown runtime environment Prompt 005C`() {
    val unkLevel = DeviceLocation.resolveVerificationLevel(
      isMock = false,
      isFromCache = false,
      runtimeEnvironment = com.example.core.location.RuntimeEnvironment.UNKNOWN,
      isRealDeviceVerified = false
    )
    assertEquals(com.example.core.location.LocationVerificationLevel.UNKNOWN, unkLevel)
  }

  @Test
  fun `test REAL_DEVICE_VERIFIED cannot be claimed without valid verification evidence Prompt 005C`() {
    // 1. In physical device runtime, without isRealDeviceVerified: MUST be REAL_DEVICE_UNVERIFIED
    val unverifiedPhysical = DeviceLocation.resolveVerificationLevel(
      isMock = false,
      isFromCache = false,
      runtimeEnvironment = com.example.core.location.RuntimeEnvironment.REAL_PHYSICAL_DEVICE,
      isRealDeviceVerified = false
    )
    assertEquals(
      "Without explicit manual verification evidence, physical device fix must be REAL_DEVICE_UNVERIFIED",
      com.example.core.location.LocationVerificationLevel.REAL_DEVICE_UNVERIFIED,
      unverifiedPhysical
    )

    // 2. If isMock is true, even with isRealDeviceVerified = true: MUST be MOCK
    val mockFraud = DeviceLocation.resolveVerificationLevel(
      isMock = true,
      isFromCache = false,
      runtimeEnvironment = com.example.core.location.RuntimeEnvironment.REAL_PHYSICAL_DEVICE,
      isRealDeviceVerified = true
    )
    assertEquals(com.example.core.location.LocationVerificationLevel.MOCK, mockFraud)

    // 3. If isFromCache is true, even in physical device: MUST be CACHED
    val cacheFraud = DeviceLocation.resolveVerificationLevel(
      isMock = false,
      isFromCache = true,
      runtimeEnvironment = com.example.core.location.RuntimeEnvironment.REAL_PHYSICAL_DEVICE,
      isRealDeviceVerified = true
    )
    assertEquals(com.example.core.location.LocationVerificationLevel.CACHED, cacheFraud)

    // 4. If runtime is EMULATOR or VIRTUAL, even with isRealDeviceVerified = true: MUST be VIRTUAL
    val emuFraud = DeviceLocation.resolveVerificationLevel(
      isMock = false,
      isFromCache = false,
      runtimeEnvironment = com.example.core.location.RuntimeEnvironment.EMULATOR,
      isRealDeviceVerified = true
    )
    assertEquals(com.example.core.location.LocationVerificationLevel.VIRTUAL, emuFraud)

    // 5. Only when ALL conditions met (real hardware, not mock, not cached, verified): REAL_DEVICE_VERIFIED
    val verified = DeviceLocation.resolveVerificationLevel(
      isMock = false,
      isFromCache = false,
      runtimeEnvironment = com.example.core.location.RuntimeEnvironment.REAL_PHYSICAL_DEVICE,
      isRealDeviceVerified = true
    )
    assertEquals(com.example.core.location.LocationVerificationLevel.REAL_DEVICE_VERIFIED, verified)
  }

  @Test
  fun `test zero fire markers policy across registries Prompt 005C`() {
    val fire006 = com.example.core.registry.FeatureRegistry.getFeature("FIRE-006")
    assertEquals(com.example.core.contract.FeatureStatus.IMPLEMENTED, fire006?.status)

    val fire007 = com.example.core.registry.FeatureRegistry.getFeature("FIRE-007")
    assertEquals(com.example.core.contract.FeatureStatus.NOT_STARTED, fire007?.status)

    val fire008 = com.example.core.registry.FeatureRegistry.getFeature("FIRE-008")
    assertEquals(com.example.core.contract.FeatureStatus.NOT_STARTED, fire008?.status)
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
