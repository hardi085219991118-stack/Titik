package com.example

import com.example.core.fire.FireDataCredentialState
import com.example.core.fire.FireDataError
import com.example.core.fire.FireDataRecord
import com.example.core.fire.FireDataResponse
import com.example.core.fire.FireDataSourceState
import com.example.core.fire.LiveApiDiagnosticAuditor
import com.example.core.fire.LiveApiDiagnosticCause
import com.example.core.fire.LiveVerificationGate
import com.example.core.fire.NasaFirmsConstants
import com.example.ui.dashboard.DashboardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diagnostic Suite for FIRE-006C: NASA FIRMS Live Connection Audit
 * Verifies all 26 diagnostic codes, Bounding Box validation, Source Priority,
 * Gate transitions, and Zero Dummy mandate.
 */
class FireDataDiagnosticTest {

  // 1. Verify all 26 Diagnostic Status Codes exist and are uniquely named
  @Test
  fun testAll26DiagnosticCausesExist() {
    val expectedCodes = listOf(
      "REQUEST_NOT_STARTED",
      "DNS_FAILURE",
      "NETWORK_FAILURE",
      "TLS_FAILURE",
      "TIMEOUT",
      "INVALID_URL",
      "INVALID_SOURCE",
      "INVALID_AREA",
      "INVALID_DAY_RANGE",
      "MISSING_CREDENTIAL",
      "INVALID_CREDENTIAL",
      "HTTP_400",
      "HTTP_401",
      "HTTP_403",
      "HTTP_404",
      "HTTP_429",
      "HTTP_500",
      "HTTP_502",
      "HTTP_503",
      "EMPTY_RESPONSE",
      "INVALID_CSV",
      "INVALID_SCHEMA",
      "PARSER_ERROR",
      "VALID_RESPONSE_NOT_RECORDED",
      "LIVE_GATE_LOGIC_ERROR",
      "UNKNOWN_ERROR"
    )

    assertEquals(26, LiveApiDiagnosticCause.values().size)
    for (code in expectedCodes) {
      val found = LiveApiDiagnosticCause.values().find { it.code == code }
      assertNotNull("Diagnostic code $code must exist", found)
      assertTrue(found!!.description.isNotBlank())
    }
  }

  // 2. Test Bounding Box Validation (Section 2)
  @Test
  fun testBoundingBoxValidationRules() {
    // Valid Mantangai BBox: 113.5,-3.5,115.0,-2.0
    val (valid, _) = LiveApiDiagnosticAuditor.validateBoundingBox("113.5,-3.5,115.0,-2.0")
    assertTrue("Valid Mantangai BBox must pass", valid)

    // Inverted West >= East
    val (westGreater, msgWest) = LiveApiDiagnosticAuditor.validateBoundingBox("115.0,-3.5,113.5,-2.0")
    assertFalse(westGreater)
    assertTrue(msgWest.contains("WEST") && msgWest.contains("EAST"))

    // Inverted South >= North
    val (southGreater, msgSouth) = LiveApiDiagnosticAuditor.validateBoundingBox("113.5,-2.0,115.0,-3.5")
    assertFalse(southGreater)
    assertTrue(msgSouth.contains("SOUTH") && msgSouth.contains("NORTH"))

    // Out of bounds longitude
    val (outOfBoundsLon, msgLon) = LiveApiDiagnosticAuditor.validateBoundingBox("185.0,-3.5,190.0,-2.0")
    assertFalse(outOfBoundsLon)
    assertTrue(msgLon.contains("longitude out of bounds"))

    // Malformed string
    val (malformed, msgMal) = LiveApiDiagnosticAuditor.validateBoundingBox("113.5,-3.5,115.0")
    assertFalse(malformed)
    assertTrue(msgMal.contains("Format bounding box harus WEST,SOUTH,EAST,NORTH"))
  }

  // 3. Test Source Sensor Priority (Section 3)
  @Test
  fun testSourceSensorPriority() {
    val priority = NasaFirmsConstants.SENSOR_PRIORITY
    assertEquals(4, priority.size)
    assertEquals(NasaFirmsConstants.SENSOR_VIIRS_NOAA21, priority[0])
    assertEquals(NasaFirmsConstants.SENSOR_VIIRS_NOAA20, priority[1])
    assertEquals(NasaFirmsConstants.SENSOR_VIIRS_SNPP, priority[2])
    assertEquals(NasaFirmsConstants.SENSOR_MODIS, priority[3])

    for (sensor in priority) {
      assertTrue("Sensor $sensor must be valid", LiveApiDiagnosticAuditor.isSourceValid(sensor))
    }

    assertFalse("Unknown sensor must be rejected", LiveApiDiagnosticAuditor.isSourceValid("UNKNOWN_SENSOR"))
  }

  // 4. Test Official Endpoint Construction
  @Test
  fun testOfficialEndpointUrlConstruction() {
    val url = LiveApiDiagnosticAuditor.buildOfficialUrl(
      mapKey = "TEST_MAP_KEY_123",
      source = NasaFirmsConstants.SENSOR_VIIRS_NOAA21,
      areaCoordinates = "113.5,-3.5,115.0,-2.0",
      dayRange = 1
    )
    val expected = "https://firms.modaps.eosdis.nasa.gov/api/area/csv/TEST_MAP_KEY_123/VIIRS_NOAA21_NRT/113.5,-3.5,115.0,-2.0/1"
    assertEquals(expected, url)
  }

  // 5. Test Pipeline Audit: Initial State (REQUEST_NOT_STARTED -> LIVE_API_NOT_VERIFIED)
  @Test
  fun testAuditInitialStateRequestNotStarted() {
    val result = LiveApiDiagnosticAuditor.auditPipeline(
      credState = FireDataCredentialState.NOT_CONFIGURED,
      sourceState = FireDataSourceState.NOT_VERIFIED,
      response = FireDataResponse.unverified()
    )

    assertEquals(LiveApiDiagnosticCause.REQUEST_NOT_STARTED, result.cause)
    assertEquals(LiveVerificationGate.LIVE_API_NOT_VERIFIED, result.gate)
    assertFalse(result.isLiveApiVerified)
    assertTrue(result.isBoundingBoxValid)
  }

  // 6. Test Pipeline Audit: Missing Credential
  @Test
  fun testAuditMissingCredential() {
    val result = LiveApiDiagnosticAuditor.auditPipeline(
      credState = FireDataCredentialState.NOT_CONFIGURED,
      sourceState = FireDataSourceState.API_CREDENTIAL_REQUIRED,
      response = FireDataResponse.credentialRequired()
    )

    assertEquals(LiveApiDiagnosticCause.MISSING_CREDENTIAL, result.cause)
    assertEquals(LiveVerificationGate.LIVE_API_CREDENTIAL_REQUIRED, result.gate)
    assertFalse(result.isLiveApiVerified)
  }

  // 7. Test Pipeline Audit: Network Failures (DNS, TIMEOUT, TLS, GENERAL)
  @Test
  fun testAuditNetworkFailures() {
    // DNS
    val dnsResp = FireDataResponse.error(
      state = FireDataSourceState.NETWORK_ERROR,
      error = FireDataError.DnsError
    )
    val dnsResult = LiveApiDiagnosticAuditor.auditPipeline(
      credState = FireDataCredentialState.CONFIGURED,
      sourceState = FireDataSourceState.NETWORK_ERROR,
      response = dnsResp
    )
    assertEquals(LiveApiDiagnosticCause.DNS_FAILURE, dnsResult.cause)
    assertEquals(LiveVerificationGate.LIVE_API_NETWORK_ERROR, dnsResult.gate)

    // Timeout
    val timeoutResp = FireDataResponse.error(
      state = FireDataSourceState.TIMEOUT,
      error = FireDataError.Timeout
    )
    val timeoutResult = LiveApiDiagnosticAuditor.auditPipeline(
      credState = FireDataCredentialState.CONFIGURED,
      sourceState = FireDataSourceState.TIMEOUT,
      response = timeoutResp
    )
    assertEquals(LiveApiDiagnosticCause.TIMEOUT, timeoutResult.cause)
    assertEquals(LiveVerificationGate.LIVE_API_NETWORK_ERROR, timeoutResult.gate)

    // TLS
    val tlsResp = FireDataResponse.error(
      state = FireDataSourceState.NETWORK_ERROR,
      error = FireDataError.SslError
    )
    val tlsResult = LiveApiDiagnosticAuditor.auditPipeline(
      credState = FireDataCredentialState.CONFIGURED,
      sourceState = FireDataSourceState.NETWORK_ERROR,
      response = tlsResp
    )
    assertEquals(LiveApiDiagnosticCause.TLS_FAILURE, tlsResult.cause)
    assertEquals(LiveVerificationGate.LIVE_API_NETWORK_ERROR, tlsResult.gate)
  }

  // 8. Test Pipeline Audit: HTTP Status Errors (400, 401, 403, 404, 429, 500, 502, 503)
  @Test
  fun testAuditHttpStatusErrors() {
    val httpCodes = listOf(400, 401, 403, 404, 429, 500, 502, 503)

    for (code in httpCodes) {
      val resp = FireDataResponse(
        state = when (code) {
          401, 403 -> FireDataSourceState.API_CREDENTIAL_REQUIRED
          429 -> FireDataSourceState.RATE_LIMIT_EXCEEDED
          else -> FireDataSourceState.DATA_SOURCE_UNAVAILABLE
        },
        httpStatusCode = code,
        error = FireDataError.HttpError(code, "HTTP Error $code")
      )

      val result = LiveApiDiagnosticAuditor.auditPipeline(
        credState = if (code in listOf(401, 403)) FireDataCredentialState.INVALID else FireDataCredentialState.CONFIGURED,
        sourceState = resp.state,
        response = resp
      )

      val expectedCause = when (code) {
        400 -> LiveApiDiagnosticCause.HTTP_400
        401 -> LiveApiDiagnosticCause.HTTP_401
        403 -> LiveApiDiagnosticCause.HTTP_403
        404 -> LiveApiDiagnosticCause.HTTP_404
        429 -> LiveApiDiagnosticCause.HTTP_429
        500 -> LiveApiDiagnosticCause.HTTP_500
        502 -> LiveApiDiagnosticCause.HTTP_502
        503 -> LiveApiDiagnosticCause.HTTP_503
        else -> LiveApiDiagnosticCause.UNKNOWN_ERROR
      }

      assertEquals("For code $code cause must match", expectedCause, result.cause)
      assertFalse("Error code $code must not be live verified", result.isLiveApiVerified)
    }
  }

  // 9. Test Pipeline Audit: Payload Failures (EMPTY_RESPONSE, INVALID_CSV, INVALID_SCHEMA, PARSER_ERROR)
  @Test
  fun testAuditPayloadFailures() {
    // EMPTY_RESPONSE
    val emptyResp = FireDataResponse(
      state = FireDataSourceState.INVALID_DATA_RESPONSE,
      error = FireDataError.EmptyResponse
    )
    val emptyResult = LiveApiDiagnosticAuditor.auditPipeline(
      credState = FireDataCredentialState.CONFIGURED,
      sourceState = FireDataSourceState.INVALID_DATA_RESPONSE,
      response = emptyResp
    )
    assertEquals(LiveApiDiagnosticCause.EMPTY_RESPONSE, emptyResult.cause)
    assertEquals(LiveVerificationGate.LIVE_API_INVALID_RESPONSE, emptyResult.gate)

    // INVALID_CSV (HTML page returned)
    val htmlResp = FireDataResponse(
      state = FireDataSourceState.INVALID_DATA_RESPONSE,
      error = FireDataError.InvalidResponse("Server mengembalikan halaman HTML alih-alih data CSV")
    )
    val htmlResult = LiveApiDiagnosticAuditor.auditPipeline(
      credState = FireDataCredentialState.CONFIGURED,
      sourceState = FireDataSourceState.INVALID_DATA_RESPONSE,
      response = htmlResp
    )
    assertEquals(LiveApiDiagnosticCause.INVALID_CSV, htmlResult.cause)
    assertEquals(LiveVerificationGate.LIVE_API_INVALID_RESPONSE, htmlResult.gate)

    // INVALID_SCHEMA (missing latitude/longitude header)
    val schemaResp = FireDataResponse(
      state = FireDataSourceState.INVALID_DATA_RESPONSE,
      error = FireDataError.InvalidResponse("Header CSV tidak valid: kolom latitude tidak ditemukan")
    )
    val schemaResult = LiveApiDiagnosticAuditor.auditPipeline(
      credState = FireDataCredentialState.CONFIGURED,
      sourceState = FireDataSourceState.INVALID_DATA_RESPONSE,
      response = schemaResp
    )
    assertEquals(LiveApiDiagnosticCause.INVALID_SCHEMA, schemaResult.cause)
    assertEquals(LiveVerificationGate.LIVE_API_PARSER_ERROR, schemaResult.gate)
  }

  // 10. Test Pipeline Audit: Successful Live Response (LIVE_API_VERIFIED)
  @Test
  fun testAuditSuccessfulLiveResponse() {
    val record = FireDataRecord(
      latitude = -2.5,
      longitude = 114.2,
      brightTi4 = 320.0,
      acqDate = "2026-09-14",
      acqTime = "0410",
      satellite = "NOAA-21",
      instrument = "VIIRS",
      confidence = "n",
      version = "2.0NRT",
      acquisitionTimestampMillis = 1789445400000L
    )
    val liveResp = FireDataResponse(
      state = FireDataSourceState.DATA_SOURCE_AVAILABLE,
      records = listOf(record),
      rawRecordCount = 1,
      validRecordCount = 1,
      httpStatusCode = 200,
      isCached = false,
      responseSha256Hash = "abc123sha256hash"
    )

    val result = LiveApiDiagnosticAuditor.auditPipeline(
      credState = FireDataCredentialState.CONFIGURED,
      sourceState = FireDataSourceState.DATA_SOURCE_AVAILABLE,
      response = liveResp
    )

    assertEquals(LiveVerificationGate.LIVE_API_VERIFIED, result.gate)
    assertTrue(result.isLiveApiVerified)
    assertEquals(LiveApiDiagnosticCause.REQUEST_NOT_STARTED, result.cause) // No failure
  }

  // 11. Test Zero-Dummy Mandate: DashboardState Defaults
  @Test
  fun testDashboardStateZeroDummyDefaults() {
    val state = DashboardState()
    assertEquals(LiveVerificationGate.LIVE_API_NOT_VERIFIED, state.liveVerificationGate)
    assertEquals(LiveApiDiagnosticCause.REQUEST_NOT_STARTED, state.diagnosticCause)
    assertEquals("--", state.fireCountDisplay)
    assertTrue(state.fireRecords.isEmpty())
  }
}
