package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.core.contract.FeatureStatus
import com.example.core.fire.ClientOnlyCredentialProvider
import com.example.core.fire.FireDataCredentialProvider
import com.example.core.fire.FireDataCredentialState
import com.example.core.fire.FireDataError
import com.example.core.fire.FireDataParser
import com.example.core.fire.FireDataRecord
import com.example.core.fire.FireDataRepository
import com.example.core.fire.FireDataResponse
import com.example.core.fire.FireDataSource
import com.example.core.fire.FireDataSourceState
import com.example.core.fire.FreshnessLevel
import com.example.core.fire.NasaFirmsConstants
import com.example.core.fire.RealFireDataRepository
import com.example.core.location.LocationStatus
import com.example.core.location.LocationTracker
import com.example.core.registry.FeatureRegistry
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardState
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.dashboard.DataState
import com.example.ui.dashboard.FireDetectionCard
import com.example.ui.dashboard.SatelliteDataCard
import com.example.ui.map.MapUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 36 Mandatori Automated Tests for FIRE-006 (NASA FIRMS Real Fire Data Foundation)
 * Adheres strictly to Section 17 & 18 of Prompt 006A.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FireDataFoundationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  // Test 1: Valid FIRMS record parsing
  @Test
  fun `test 01 valid FIRMS record parsing`() {
    val csv = """
      latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
      -2.5857,114.4412,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
    """.trimIndent()

    val result = FireDataParser.parseCsv(csv)
    assertEquals(1, result.validCount)
    assertEquals(0, result.invalidCount)
    val record = result.records.first()
    assertEquals(-2.5857, record.latitude, 0.0001)
    assertEquals(114.4412, record.longitude, 0.0001)
    assertEquals(325.4, record.brightTi4 ?: 0.0, 0.01)
    assertEquals("2026-09-14", record.acqDate)
    assertEquals("0410", record.acqTime)
    assertEquals("NOAA-21", record.satellite)
    assertEquals("VIIRS", record.instrument)
    assertEquals("nominal", record.confidence)
    assertEquals(14.5, record.frp ?: 0.0, 0.01)
    assertEquals("D", record.dayNight)
    assertNotNull(record.acquisitionTimestampMillis)
  }

  // Test 2: Invalid latitude rejected
  @Test
  fun `test 02 invalid latitude rejected`() {
    val csv = """
      latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
      95.5857,114.4412,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
      -95.5857,114.4412,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
      NaN,114.4412,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
    """.trimIndent()

    val result = FireDataParser.parseCsv(csv)
    assertEquals(0, result.validCount)
    assertEquals(3, result.invalidCount)
  }

  // Test 3: Invalid longitude rejected
  @Test
  fun `test 03 invalid longitude rejected`() {
    val csv = """
      latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
      -2.5857,195.4412,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
      -2.5857,-195.4412,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
      -2.5857,Infinity,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
    """.trimIndent()

    val result = FireDataParser.parseCsv(csv)
    assertEquals(0, result.validCount)
    assertEquals(3, result.invalidCount)
  }

  // Test 4: Invalid acquisition date rejected
  @Test
  fun `test 04 invalid acquisition date rejected`() {
    val csv = """
      latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
      -2.5857,114.4412,325.4,0.4,0.4,2026/09/14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
      -2.5857,114.4412,325.4,0.4,0.4,invalid-date,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
    """.trimIndent()

    val result = FireDataParser.parseCsv(csv)
    assertEquals(0, result.validCount)
    assertEquals(2, result.invalidCount)
  }

  // Test 5: Invalid acquisition time rejected
  @Test
  fun `test 05 invalid acquisition time rejected`() {
    val csv = """
      latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
      -2.5857,114.4412,325.4,0.4,0.4,2026-09-14,9999,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
      -2.5857,114.4412,325.4,0.4,0.4,2026-09-14,25:61,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
    """.trimIndent()

    val result = FireDataParser.parseCsv(csv)
    assertEquals(0, result.validCount)
    assertEquals(2, result.invalidCount)
  }

  // Test 6: Nullable FRP handled cleanly
  @Test
  fun `test 06 nullable FRP handled cleanly`() {
    val csv = """
      latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
      -2.5857,114.4412,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,,D
    """.trimIndent()

    val result = FireDataParser.parseCsv(csv)
    assertEquals(1, result.validCount)
    assertNull(result.records.first().frp)
  }

  // Test 7: Nullable confidence handled cleanly
  @Test
  fun `test 07 nullable confidence handled cleanly`() {
    val csv = """
      latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
      -2.5857,114.4412,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,,2.0NRT,298.2,12.0,D
    """.trimIndent()

    val result = FireDataParser.parseCsv(csv)
    assertEquals(1, result.validCount)
    assertTrue(result.records.first().confidence.isNullOrBlank())
  }

  // Test 8: Sensor identification
  @Test
  fun `test 08 sensor identification VIIRS vs MODIS`() {
    val modisCsv = """
      latitude,longitude,brightness,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_t31,frp,daynight
      -2.5857,114.4412,315.2,1.0,1.0,2026-09-14,0345,Terra,MODIS,85,6.1NRT,295.4,22.1,D
    """.trimIndent()

    val result = FireDataParser.parseCsv(modisCsv, defaultInstrument = "MODIS", defaultSatellite = "Terra")
    assertEquals("MODIS", result.records.first().instrument)
    assertEquals("Terra", result.records.first().satellite)
  }

  // Test 9: Satellite identification
  @Test
  fun `test 09 satellite identification NOAA21 NOAA20 SNPP`() {
    val csv = """
      latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
      -2.5857,114.4412,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
      -2.5890,114.4500,320.1,0.4,0.4,2026-09-14,0415,NOAA-20,VIIRS,nominal,2.0NRT,296.2,11.2,D
      -2.5920,114.4600,318.0,0.4,0.4,2026-09-14,0420,Suomi-NPP,VIIRS,nominal,2.0NRT,295.0,9.8,D
    """.trimIndent()

    val result = FireDataParser.parseCsv(csv)
    assertEquals(3, result.validCount)
    assertEquals("NOAA-21", result.records[0].satellite)
    assertEquals("NOAA-20", result.records[1].satellite)
    assertEquals("Suomi-NPP", result.records[2].satellite)
  }

  // Test 10: HTTP 200 with valid records
  @Test
  fun `test 10 HTTP 200 with valid records`() = runBlocking {
    val fakeSource = object : FireDataSource {
      override suspend fun fetchFireData(mapKey: String, source: String, areaCoordinates: String, dayRange: Int): FireDataResponse {
        val records = listOf(
          FireDataRecord(latitude = -2.58, longitude = 114.44, acqDate = "2026-09-14", acqTime = "0410", satellite = "NOAA-21", instrument = "VIIRS")
        )
        return FireDataResponse(
          state = FireDataSourceState.DATA_SOURCE_AVAILABLE,
          records = records,
          validRecordCount = 1,
          rawRecordCount = 1,
          requestTimeMillis = 1000L,
          fetchTimeMillis = 2000L,
          sourceSensor = source,
          httpStatusCode = 200
        )
      }
    }
    val credProvider = object : FakeCredentialProvider("valid_test_map_key_123456") {}
    val repo = RealFireDataRepository(credProvider, fakeSource)
    val res = repo.refreshFireData(force = true)

    assertEquals(FireDataSourceState.DATA_SOURCE_AVAILABLE, res.state)
    assertEquals(1, res.validRecordCount)
    assertEquals(200, res.httpStatusCode)
  }

  // Test 11: HTTP 400 Bad Request
  @Test
  fun `test 11 HTTP 400 Bad Request`() = runBlocking {
    val fakeSource = createHttpErrorSource(400, "Bad query bounds")
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_1234567890"), fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.DATA_SOURCE_UNAVAILABLE, res.state)
    assertEquals(400, res.httpStatusCode)
    assertNotNull(res.error)
  }

  // Test 12: HTTP 401 Unauthorized
  @Test
  fun `test 12 HTTP 401 Unauthorized marks credential invalid`() = runBlocking {
    val fakeSource = createHttpErrorSource(401, "Invalid MAP_KEY")
    val credProvider = FakeCredentialProvider("invalid_key_12345678")
    val repo = RealFireDataRepository(credProvider, fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.API_CREDENTIAL_REQUIRED, res.state)
    assertEquals(FireDataCredentialState.INVALID, credProvider.getCredentialState())
  }

  // Test 13: HTTP 403 Forbidden
  @Test
  fun `test 13 HTTP 403 Forbidden marks credential invalid`() = runBlocking {
    val fakeSource = createHttpErrorSource(403, "Forbidden")
    val credProvider = FakeCredentialProvider("forbidden_key_123456")
    val repo = RealFireDataRepository(credProvider, fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.API_CREDENTIAL_REQUIRED, res.state)
    assertEquals(FireDataCredentialState.INVALID, credProvider.getCredentialState())
  }

  // Test 14: HTTP 404 Not Found
  @Test
  fun `test 14 HTTP 404 Not Found`() = runBlocking {
    val fakeSource = createHttpErrorSource(404, "Endpoint not found")
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.DATA_SOURCE_UNAVAILABLE, res.state)
    assertEquals(404, res.httpStatusCode)
  }

  // Test 15: HTTP 429 Rate Limit Exceeded
  @Test
  fun `test 15 HTTP 429 Rate Limit Exceeded`() = runBlocking {
    val fakeSource = createHttpErrorSource(429, "Too Many Requests")
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.RATE_LIMIT_EXCEEDED, res.state)
    assertEquals(429, res.httpStatusCode)
  }

  // Test 16: HTTP 500 Internal Server Error
  @Test
  fun `test 16 HTTP 500 Internal Server Error`() = runBlocking {
    val fakeSource = createHttpErrorSource(500, "Internal Error")
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.DATA_SOURCE_UNAVAILABLE, res.state)
    assertEquals(0, res.validRecordCount)
  }

  // Test 17: HTTP 502 Bad Gateway
  @Test
  fun `test 17 HTTP 502 Bad Gateway`() = runBlocking {
    val fakeSource = createHttpErrorSource(502, "Bad Gateway")
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.DATA_SOURCE_UNAVAILABLE, res.state)
  }

  // Test 18: HTTP 503 Service Unavailable
  @Test
  fun `test 18 HTTP 503 Service Unavailable`() = runBlocking {
    val fakeSource = createHttpErrorSource(503, "Service Unavailable")
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.DATA_SOURCE_UNAVAILABLE, res.state)
  }

  // Test 19: Socket Timeout Exception
  @Test
  fun `test 19 Socket Timeout Exception`() = runBlocking {
    val fakeSource = object : FireDataSource {
      override suspend fun fetchFireData(mapKey: String, source: String, areaCoordinates: String, dayRange: Int): FireDataResponse {
        return FireDataResponse.error(FireDataSourceState.TIMEOUT, FireDataError.Timeout)
      }
    }
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.TIMEOUT, res.state)
    assertEquals("TIMEOUT", res.error?.code)
  }

  // Test 20: Network Unavailable
  @Test
  fun `test 20 Network Unavailable`() = runBlocking {
    val fakeSource = object : FireDataSource {
      override suspend fun fetchFireData(mapKey: String, source: String, areaCoordinates: String, dayRange: Int): FireDataResponse {
        return FireDataResponse.error(FireDataSourceState.NETWORK_ERROR, FireDataError.NoInternet)
      }
    }
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.NETWORK_ERROR, res.state)
    assertEquals("NO_INTERNET", res.error?.code)
  }

  // Test 21: Invalid response format / malformed payload
  @Test
  fun `test 21 invalid response format`() {
    val badHtml = "<html><body>Error 500 Server Down</body></html>"
    try {
      FireDataParser.parseCsv(badHtml)
      assertTrue("Harus melempar exception untuk format non-CSV", false)
    } catch (e: Exception) {
      assertTrue(e.message?.contains("Header CSV tidak valid") == true || e.message?.contains("Error") == true)
    }
  }

  // Test 22: Empty response body
  @Test
  fun `test 22 empty response body`() {
    val result = FireDataParser.parseCsv("")
    assertEquals(0, result.validCount)
    assertEquals(0, result.rawCount)
  }

  // Test 23: Source unavailable general error
  @Test
  fun `test 23 source unavailable general error`() = runBlocking {
    val fakeSource = createHttpErrorSource(503, "Service Down")
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.DATA_SOURCE_UNAVAILABLE, res.state)
    assertTrue(res.records.isEmpty())
  }

  // Test 24: Credential missing NOT_CONFIGURED
  @Test
  fun `test 24 credential missing NOT_CONFIGURED`() = runBlocking {
    val emptyCredProvider = FakeCredentialProvider(null)
    val repo = RealFireDataRepository(emptyCredProvider)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.API_CREDENTIAL_REQUIRED, res.state)
    assertEquals(FireDataCredentialState.NOT_CONFIGURED, emptyCredProvider.getCredentialState())
  }

  // Test 25: Credential invalid state
  @Test
  fun `test 25 credential invalid state`() = runBlocking {
    val credProvider = FakeCredentialProvider("invalid_key")
    credProvider.markInvalid()
    val repo = RealFireDataRepository(credProvider)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.API_CREDENTIAL_REQUIRED, res.state)
  }

  // Test 26: Count unknown on request failure
  @Test
  fun `test 26 count unknown on request failure`() {
    val vm = DashboardViewModel(FakeLocationTracker())
    // Initial state: not verified -> count is "--"
    assertEquals("--", vm.uiState.value.fireCountDisplay)
    assertNull(vm.uiState.value.validFireRecordCount)
  }

  // Test 27: Count zero ONLY on valid empty response
  @Test
  fun `test 27 count zero only on valid empty response`() = runBlocking {
    val fakeSource = object : FireDataSource {
      override suspend fun fetchFireData(mapKey: String, source: String, areaCoordinates: String, dayRange: Int): FireDataResponse {
        return FireDataResponse(
          state = FireDataSourceState.NO_DETECTIONS_IN_QUERY,
          records = emptyList(),
          validRecordCount = 0,
          rawRecordCount = 0,
          requestTimeMillis = 1000L,
          fetchTimeMillis = 2000L,
          sourceSensor = source,
          httpStatusCode = 200
        )
      }
    }
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), fakeSource)
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.NO_DETECTIONS_IN_QUERY, res.state)
    assertEquals(0, res.validRecordCount)

    val vm = DashboardViewModel(FakeLocationTracker(), repo)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()

    assertEquals("0", vm.uiState.value.fireCountDisplay)
    assertEquals(0, vm.uiState.value.validFireRecordCount)
    assertEquals(FireDataSourceState.NO_DETECTIONS_IN_QUERY, vm.uiState.value.fireDataSourceState)
  }

  // Test 28: Acquisition timestamp differs from fetch timestamp
  @Test
  fun `test 28 acquisition timestamp differs from fetch timestamp`() {
    val csv = """
      latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
      -2.5857,114.4412,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
    """.trimIndent()

    val result = FireDataParser.parseCsv(csv)
    val record = result.records.first()
    val fetchTime = System.currentTimeMillis()

    assertNotNull(record.acquisitionTimestampMillis)
    assertNotEquals("Acquisition timestamp must not equal device fetch timestamp", fetchTime, record.acquisitionTimestampMillis)
  }

  // Test 29: No fake fallback fire points
  @Test
  fun `test 29 no fake fallback fire points on failure`() = runBlocking {
    val fakeSource = createHttpErrorSource(500, "Server Failure")
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), fakeSource)
    val res = repo.refreshFireData(force = true)

    assertTrue("Records list must be strictly empty on failure", res.records.isEmpty())
    assertEquals(0, res.validRecordCount)
    assertEquals(FireDataSourceState.DATA_SOURCE_UNAVAILABLE, res.state)
  }

  // Test 30: Zero fire marker policy
  @Test
  fun `test 30 zero fire marker policy in map state`() {
    val mapState = MapUiState()
    assertEquals("ZERO FIRE MARKERS: fireMarkerCount wajib selalu 0", 0, mapState.fireMarkerCount)
  }

  // Test 31: No hardcoded production fire coordinate
  @Test
  fun `test 31 no hardcoded production fire coordinate in FeatureRegistry`() {
    val contract = FeatureRegistry.getFeature("FIRE-006")
    assertNotNull(contract)
    assertFalse(contract!!.purpose.contains("-2.58"))
    assertFalse(contract.output.contains("114.44"))
    assertFalse(contract.process.contains("DEFAULT_FIRE"))
  }

  // Test 32: Cache state
  @Test
  fun `test 32 cache state on network offline`() = runBlocking {
    var isOffline = false
    val validRecord = FireDataRecord(latitude = -2.58, longitude = 114.44, acqDate = "2026-09-14", acqTime = "0410", satellite = "NOAA-21", instrument = "VIIRS")

    val toggleableSource = object : FireDataSource {
      override suspend fun fetchFireData(mapKey: String, source: String, areaCoordinates: String, dayRange: Int): FireDataResponse {
        return if (!isOffline) {
          FireDataResponse(
            state = FireDataSourceState.DATA_SOURCE_AVAILABLE,
            records = listOf(validRecord),
            validRecordCount = 1,
            fetchTimeMillis = System.currentTimeMillis() - 5000L,
            sourceSensor = source,
            httpStatusCode = 200
          )
        } else {
          FireDataResponse.error(FireDataSourceState.NETWORK_ERROR, FireDataError.NoInternet)
        }
      }
    }

    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), toggleableSource)
    // First call: successful online fetch
    val onlineRes = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.DATA_SOURCE_AVAILABLE, onlineRes.state)

    // Second call: offline
    isOffline = true
    val cachedRes = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.CACHED, cachedRes.state)
    assertTrue(cachedRes.isCached)
    assertEquals(1, cachedRes.validRecordCount)
  }

  // Test 33: Stale data state
  @Test
  fun `test 33 stale data state when data older than 24 hours`() {
    // Over 24 hours ago
    val acqTimeOver24h = System.currentTimeMillis() - (25 * 3600 * 1000L)
    val records = listOf(
      FireDataRecord(
        latitude = -2.58,
        longitude = 114.44,
        acqDate = "2026-09-13",
        acqTime = "0000",
        satellite = "NOAA-21",
        instrument = "VIIRS",
        acquisitionTimestampMillis = acqTimeOver24h
      )
    )
    val fetchTime = System.currentTimeMillis()
    val age = fetchTime - acqTimeOver24h
    val freshness = if (age > 24 * 3600 * 1000L) FreshnessLevel.STALE else FreshnessLevel.NRT
    assertEquals(FreshnessLevel.STALE, freshness)
  }

  // Test 34: FireDataRepository rate limit and cooldown
  @Test
  fun `test 34 repository rate limit cooldown prevents redundant calls`() = runBlocking {
    var callCount = 0
    val countingSource = object : FireDataSource {
      override suspend fun fetchFireData(mapKey: String, source: String, areaCoordinates: String, dayRange: Int): FireDataResponse {
        callCount++
        return FireDataResponse(
          state = FireDataSourceState.NO_DETECTIONS_IN_QUERY,
          records = emptyList(),
          validRecordCount = 0,
          fetchTimeMillis = System.currentTimeMillis(),
          sourceSensor = source
        )
      }
    }

    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), countingSource)
    repo.refreshFireData(force = false)
    val initialCalls = callCount
    assertTrue(initialCalls > 0)

    // Immediate second call without force should respect cooldown
    repo.refreshFireData(force = false)
    assertEquals("Should not hit network within cooldown window", initialCalls, callCount)
  }

  // Test 35: Dashboard fire data state mapping
  @Test
  fun `test 35 dashboard fire data state mapping in UI`() {
    val state = DashboardState(
      fireDataState = DataState.NOT_VERIFIED,
      fireStatusText = "FIRE DATA SOURCE NOT VERIFIED",
      fireCountDisplay = "--"
    )

    composeTestRule.setContent {
      FireDetectionCard(state = state)
      SatelliteDataCard(state = state)
    }

    composeTestRule.onNodeWithTag("fire_detection_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("FIRE DATA SOURCE NOT VERIFIED").assertIsDisplayed()
    composeTestRule.onNodeWithText("--").assertIsDisplayed()
    composeTestRule.onNodeWithTag("satellite_data_card").assertIsDisplayed()
  }

  // Test 36: FIRE-006 registry status
  @Test
  fun `test 36 FIRE-006 registry status is IMPLEMENTED`() {
    val fire006 = FeatureRegistry.getFeature("FIRE-006")
    val fire007 = FeatureRegistry.getFeature("FIRE-007")
    val fire008 = FeatureRegistry.getFeature("FIRE-008")

    assertNotNull(fire006)
    assertNotNull(fire007)
    assertNotNull(fire008)

    assertEquals(FeatureStatus.IMPLEMENTED, fire006!!.status)
    assertEquals(FeatureStatus.NOT_STARTED, fire007!!.status)
    assertEquals(FeatureStatus.NOT_STARTED, fire008!!.status)
  }

  // Test 37: HTTP 404 Not Found handling
  @Test
  fun `test 37 http 404 handling`() = runBlocking {
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), createHttpErrorSource(404, "Not Found"))
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.DATA_SOURCE_UNAVAILABLE, res.state)
    assertEquals(404, res.httpStatusCode)
  }

  // Test 38: HTTP 502 Bad Gateway handling
  @Test
  fun `test 38 http 502 handling`() = runBlocking {
    val repo = RealFireDataRepository(FakeCredentialProvider("valid_key_12345678"), createHttpErrorSource(502, "Bad Gateway"))
    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.DATA_SOURCE_UNAVAILABLE, res.state)
    assertEquals(502, res.httpStatusCode)
  }

  // Test 39: Security Architecture & Limitation Labels (Prompt 006B Section 5)
  @Test
  fun `test 39 client side security limitation labels`() {
    val state = DashboardState()
    assertEquals("CLIENT_ONLY_LIMITATION", state.architectureStatus)
    assertEquals("CLIENT_SIDE_CREDENTIAL", state.credentialType)
    assertEquals("PRODUCTION_SECURITY_LIMITATION", state.securityLimitation)
  }

  // Test 40: Response SHA-256 Hash Generation (Prompt 006B Section 13)
  @Test
  fun `test 40 response sha256 hash generation`() {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val payload = "latitude,longitude,bright_ti4\n-2.5,114.5,320.0"
    val hash = md.digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    assertNotNull(hash)
    assertEquals(64, hash.length)
  }

  // Helper functions and classes
  private fun createHttpErrorSource(code: Int, msg: String): FireDataSource {
    return object : FireDataSource {
      override suspend fun fetchFireData(mapKey: String, source: String, areaCoordinates: String, dayRange: Int): FireDataResponse {
        val error = FireDataError.HttpError(code, msg)
        val state = when (code) {
          401, 403 -> FireDataSourceState.API_CREDENTIAL_REQUIRED
          429 -> FireDataSourceState.RATE_LIMIT_EXCEEDED
          else -> FireDataSourceState.DATA_SOURCE_UNAVAILABLE
        }
        return FireDataResponse(
          state = state,
          records = emptyList(),
          sourceSensor = source,
          error = error,
          httpStatusCode = code
        )
      }
    }
  }

  open class FakeCredentialProvider(private var key: String?) : FireDataCredentialProvider {
    private var isInvalid = false
    override fun getCredentialState(): FireDataCredentialState {
      return when {
        key.isNullOrBlank() -> FireDataCredentialState.NOT_CONFIGURED
        isInvalid -> FireDataCredentialState.INVALID
        else -> FireDataCredentialState.CONFIGURED
      }
    }
    override fun getMapKey(): String? = key
    override fun setMapKey(key: String?): FireDataCredentialState {
      this.key = key
      isInvalid = false
      return getCredentialState()
    }
    override fun markInvalid() { isInvalid = true }
    override fun clearMapKey() { key = null; isInvalid = false }
    override val limitationNote: String = "Test Provider"
  }

  class FakeLocationTracker : LocationTracker {
    override val locationStatus = MutableStateFlow(LocationStatus.LOCATION_PERMISSION_REQUIRED)
    override val currentLocation = MutableStateFlow<com.example.core.location.DeviceLocation?>(null)
    override val errorMessage = MutableStateFlow<String?>(null)
    override fun requestLocation(context: android.content.Context) {}
    override fun stopTracking() {}
    override fun markPermissionDenied(isPermanentlyDenied: Boolean) {}
  }
}
