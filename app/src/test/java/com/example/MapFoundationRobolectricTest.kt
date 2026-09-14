package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.core.contract.FeatureStatus
import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.map.CoordinateValidator
import com.example.core.map.MapProviderInfo
import com.example.core.registry.FeatureRegistry
import com.example.ui.dashboard.DashboardState
import com.example.ui.dashboard.DataState
import com.example.ui.dashboard.FireDetectionCard
import com.example.ui.dashboard.MapFoundationCard
import com.example.ui.map.MapStatus
import com.example.ui.map.MapUiState
import com.example.ui.map.UserLocationInfoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pengujian Otomatis Mandatori sesuai Section 17 & 18 Prompt 005.
 *
 * Cakupan Pengujian:
 * 1. Inisialisasi MapStatus & Provider Info.
 * 2. Kebijakan Zero Fire Markers (fireMarkerCount == 0, FIRE-006 s/d FIRE-008 NOT_STARTED).
 * 3. Validasi batas geografis koordinat (valid vs out-of-range, NaN, Infinity).
 * 4. UI State pada Map Component saat lokasi tersedia vs tidak tersedia.
 * 5. Integrasi Dashboard: Memverifikasi Map Foundation card & memastikan status data titik api
 *    TETAP "FIRE DATA SOURCE NOT VERIFIED" (tidak diubah menjadi "NO FIRE DETECTED").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapFoundationRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `test map provider info and credential status`() {
    assertEquals("NOT_REQUIRED", MapProviderInfo.CREDENTIAL_STATUS)
    assertTrue(MapProviderInfo.PROVIDER_NAME.contains("osmdroid"))
    assertTrue(MapProviderInfo.TILE_SOURCE.contains("OpenStreetMap"))
    assertTrue(MapProviderInfo.ZERO_FIRE_MARKERS_POLICY.contains("ZERO FIRE MARKERS"))
  }

  @Test
  fun `test zero fire markers rule in map state`() {
    val mapUiState = MapUiState()
    assertEquals(
      "ZERO FIRE MARKERS: fireMarkerCount wajib selalu 0 karena sumber data satelit belum ada",
      0,
      mapUiState.fireMarkerCount
    )
  }

  @Test
  fun `test coordinate validator boundaries and edge cases`() {
    // 1. Valid coordinates
    assertTrue(CoordinateValidator.isValid(0.0, 0.0))
    assertTrue(CoordinateValidator.isValid(-2.123456, 114.654321))
    assertTrue(CoordinateValidator.isValid(90.0, 180.0))
    assertTrue(CoordinateValidator.isValid(-90.0, -180.0))

    // 2. Latitude out of range
    assertFalse(CoordinateValidator.isValid(90.0001, 100.0))
    assertFalse(CoordinateValidator.isValid(-90.0001, 100.0))
    assertFalse(CoordinateValidator.isValid(120.0, 0.0))

    // 3. Longitude out of range
    assertFalse(CoordinateValidator.isValid(0.0, 180.0001))
    assertFalse(CoordinateValidator.isValid(0.0, -180.0001))
    assertFalse(CoordinateValidator.isValid(0.0, 200.0))

    // 4. Special floating point values
    assertFalse(CoordinateValidator.isValid(Double.NaN, 0.0))
    assertFalse(CoordinateValidator.isValid(0.0, Double.NaN))
    assertFalse(CoordinateValidator.isValid(Double.POSITIVE_INFINITY, 0.0))
    assertFalse(CoordinateValidator.isValid(0.0, Double.NEGATIVE_INFINITY))

    // 5. Detailed validation result
    val validLoc = DeviceLocation(-2.123456, 114.654321, 5.0f, 1000L)
    val resValid = CoordinateValidator.validateLocation(validLoc)
    assertTrue(resValid.isValid)

    val invalidLoc = DeviceLocation(95.0, 114.0, 5.0f, 1000L)
    val resInvalid = CoordinateValidator.validateLocation(invalidLoc)
    assertFalse(resInvalid.isValid)
    assertNotNull(resInvalid.errorMessage)
  }

  @Test
  fun `test dashboard state preserves fire data not verified rule 16`() {
    val state = DashboardState()

    // Map foundation fields must be present
    assertEquals(MapStatus.MAP_READY, state.mapStatus)
    assertEquals(MapProviderInfo.PROVIDER_NAME, state.mapProviderName)
    assertEquals("NOT_REQUIRED", state.mapCredentialStatus)

    // Section 16 constraint: FIRE DATA NOT VERIFIED dilarang diubah menjadi NO FIRE DETECTED
    assertEquals(DataState.NOT_VERIFIED, state.fireDataState)
    assertEquals("--", state.fireCountDisplay)
    assertEquals("FIRE DATA SOURCE NOT VERIFIED", state.fireStatusText)
    assertFalse(state.fireStatusText.contains("NO FIRE DETECTED"))
  }

  @Test
  fun `test feature registry contract status for FIRE-005 and FIRE-006`() {
    val fire005 = FeatureRegistry.getFeature("FIRE-005")
    assertNotNull("FIRE-005 must exist in registry", fire005)
    assertEquals(
      "FIRE-005 must be REAL_DEVICE_VERIFICATION_PENDING until physical device verification (Section 13 & 14)",
      FeatureStatus.REAL_DEVICE_VERIFICATION_PENDING,
      fire005?.status
    )

    // Section 2: FIRE-006 s/d FIRE-008 TETAP NOT_STARTED
    val fire006 = FeatureRegistry.getFeature("FIRE-006")
    assertNotNull("FIRE-006 must exist in registry", fire006)
    assertEquals("FIRE-006 must remain NOT_STARTED in Prompt 005", FeatureStatus.NOT_STARTED, fire006?.status)

    val fire007 = FeatureRegistry.getFeature("FIRE-007")
    assertEquals("FIRE-007 must remain NOT_STARTED", FeatureStatus.NOT_STARTED, fire007?.status)

    val fire008 = FeatureRegistry.getFeature("FIRE-008")
    assertEquals("FIRE-008 must remain NOT_STARTED", FeatureStatus.NOT_STARTED, fire008?.status)
  }

  @Test
  fun `test user location info card renders valid real coordinates`() {
    val location = DeviceLocation(
      latitude = -2.123456,
      longitude = 114.654321,
      accuracyMeters = 7.5f,
      timeMillis = 1700000000000L,
      provider = "gps"
    )
    val validation = CoordinateValidator.validateLocation(location)

    composeTestRule.setContent {
      UserLocationInfoCard(
        location = location,
        locationStatus = LocationStatus.LOCATION_AVAILABLE,
        validationResult = validation,
        locationErrorMessage = null,
        onRequestPermission = {}
      )
    }

    composeTestRule.onNodeWithTag("user_location_info_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("user_location_title").assertIsDisplayed()
    composeTestRule.onNodeWithTag("zero_fire_markers_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("user_latitude_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("user_longitude_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("user_accuracy_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("user_time_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("user_source_text").assertIsDisplayed()
  }

  @Test
  fun `test user location info card renders belum tersedia when location missing`() {
    val validation = CoordinateValidator.ValidationResult(isValid = false)

    composeTestRule.setContent {
      UserLocationInfoCard(
        location = null,
        locationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED,
        validationResult = validation,
        locationErrorMessage = null,
        onRequestPermission = {}
      )
    }

    composeTestRule.onNodeWithTag("user_location_info_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("location_not_available_box").assertIsDisplayed()
    composeTestRule.onNodeWithTag("zero_fire_markers_badge").assertIsDisplayed()
  }

  @Test
  fun `test user location info card renders invalid coordinate warning when out of bounds`() {
    val invalidLocation = DeviceLocation(
      latitude = 95.0,
      longitude = 114.0,
      accuracyMeters = 5.0f,
      timeMillis = 1700000000000L
    )
    val validation = CoordinateValidator.validateLocation(invalidLocation)

    composeTestRule.setContent {
      UserLocationInfoCard(
        location = invalidLocation,
        locationStatus = LocationStatus.LOCATION_AVAILABLE,
        validationResult = validation,
        locationErrorMessage = null,
        onRequestPermission = {}
      )
    }

    composeTestRule.onNodeWithTag("invalid_location_warning").assertIsDisplayed()
  }

  @Test
  fun `test dashboard map foundation card and fire detection card integration`() {
    var openedMap = false
    val state = DashboardState(
      mapStatus = MapStatus.MAP_READY,
      locationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED
    )

    composeTestRule.setContent {
      MapFoundationCard(
        state = state,
        onOpenMap = { openedMap = true }
      )
    }

    composeTestRule.onNodeWithTag("map_status_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("map_card_status_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("map_status_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("map_location_status_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("map_location_not_available_text").assertIsDisplayed()

    // Test button interaction
    composeTestRule.onNodeWithTag("open_map_screen_button").performClick()
    assertTrue("Clicking open map button must trigger callback", openedMap)
  }

  @Test
  fun `test fire detection card never shows no fire detected when source unverified`() {
    val state = DashboardState()

    composeTestRule.setContent {
      FireDetectionCard(state = state)
    }

    composeTestRule.onNodeWithTag("fire_detection_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("FIRE DATA SOURCE NOT VERIFIED").assertIsDisplayed()
    composeTestRule.onNodeWithText("--").assertIsDisplayed()
  }
}
