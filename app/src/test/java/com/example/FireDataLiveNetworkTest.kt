package com.example

import com.example.core.fire.ClientOnlyCredentialProvider
import com.example.core.fire.FireDataCredentialState
import com.example.core.fire.FireDataSourceState
import com.example.core.fire.LiveApiDiagnosticAuditor
import com.example.core.fire.LiveApiDiagnosticCause
import com.example.core.fire.LiveVerificationGate
import com.example.core.fire.NasaFirmsConstants
import com.example.core.fire.NasaFirmsNetworkDataSource
import com.example.core.fire.RealFireDataRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Live Network & Pipeline Test for FIRE-006F
 * Executes live connection against NASA FIRMS Web Services using testing credentials.
 * ZERO-DUMMY MANDATE:
 * - Real HTTPS connection to NASA FIRMS.
 * - Real parsing of authentic CSV.
 * - No mock data, no fake coordinates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FireDataLiveNetworkTest {

  @Test
  fun testLiveNasaFirmsConnectionAndVerification() = runBlocking {
    val credentialProvider = ClientOnlyCredentialProvider()
    val mapKey = credentialProvider.getMapKey()

    assumeTrue(
      "Testing MAP_KEY must be configured via environment or assets to run live network tests",
      !mapKey.isNullOrBlank()
    )
    assertEquals(FireDataCredentialState.CONFIGURED, credentialProvider.getCredentialState())

    val dataSource = NasaFirmsNetworkDataSource()
    val response = dataSource.fetchFireData(
      mapKey = mapKey!!,
      source = NasaFirmsConstants.SENSOR_VIIRS_NOAA21,
      areaCoordinates = NasaFirmsConstants.DEFAULT_MANTHANGAI_BBOX,
      dayRange = 1
    )

    // Verify HTTP & Response
    assertEquals(200, response.httpStatusCode)
    assertTrue("Response state must be DATA_SOURCE_AVAILABLE or NO_DETECTIONS_IN_QUERY",
      response.state == FireDataSourceState.DATA_SOURCE_AVAILABLE || response.state == FireDataSourceState.NO_DETECTIONS_IN_QUERY)

    // Verify SHA-256 hash was generated from authentic response
    assertNotNull(response.responseSha256Hash)
    assertEquals(64, response.responseSha256Hash!!.length)

    // Audit pipeline
    val audit = LiveApiDiagnosticAuditor.auditPipeline(
      credState = FireDataCredentialState.CONFIGURED,
      sourceState = response.state,
      response = response,
      queryArea = NasaFirmsConstants.DEFAULT_MANTHANGAI_BBOX
    )

    assertEquals(LiveVerificationGate.LIVE_API_VERIFIED, audit.gate)
    assertTrue("Pipeline must achieve LIVE_API_VERIFIED", audit.isLiveApiVerified)
    assertEquals(LiveApiDiagnosticCause.REQUEST_NOT_STARTED, audit.cause) // No error

    // Verify Records & Strict Data Model
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    val earliestAcq = response.records.mapNotNull { it.acquisitionTimestampMillis }.minOrNull()
    val latestAcq = response.records.mapNotNull { it.acquisitionTimestampMillis }.maxOrNull()
    val earliestStr = if (earliestAcq != null) sdf.format(Date(earliestAcq)) else "N/A"
    val latestStr = if (latestAcq != null) sdf.format(Date(latestAcq)) else "N/A"
    val fetchStr = sdf.format(Date(response.fetchTimeMillis))
    val dataAgeStr = if (latestAcq != null) {
      val diffMs = response.fetchTimeMillis - latestAcq
      val hours = diffMs / (1000 * 60 * 60)
      val mins = (diffMs % (1000 * 60 * 60)) / (1000 * 60)
      "${hours}j ${mins}m"
    } else "N/A"
    val satelliteName = response.records.firstOrNull()?.satellite ?: "NOAA-21"
    val instrumentName = response.records.firstOrNull()?.instrument ?: "VIIRS"

    println("==================================================")
    println("NASA FIRMS LIVE RESPONSE EVIDENCE")
    println("==================================================")
    println("LIVE API:\nVERIFIED\n")
    println("HTTP STATUS:\n${response.httpStatusCode}\n")
    println("SOURCE:\n${response.sourceSensor}\n")
    println("AREA:\n${response.requestArea}\n")
    println("DAY RANGE:\n${response.requestDayRange}\n")
    println("RESPONSE BYTES:\n${response.responseByteCount}\n")
    println("RAW RECORDS:\n${response.rawRecordCount}\n")
    println("VALID RECORDS:\n${response.validRecordCount}\n")
    println("INVALID RECORDS:\n${response.invalidRecordCount}\n")
    println("SATELLITE:\n$satelliteName\n")
    println("INSTRUMENT:\n$instrumentName\n")
    println("EARLIEST ACQUISITION:\n$earliestStr\n")
    println("LATEST ACQUISITION:\n$latestStr\n")
    println("FETCH TIME:\n$fetchStr\n")
    println("DATA AGE:\n$dataAgeStr\n")
    println("RESPONSE SHA-256:\n${response.responseSha256Hash}\n")
    println("CREDENTIAL:\nCONFIGURED\n")
    println("MAP_KEY:\nHIDDEN\n")
    println("==================================================")

    if (response.validRecordCount > 0) {
      assertTrue("Raw record count must be >= valid count", response.rawRecordCount >= response.validRecordCount)
      val sample = response.records.first()
      // Authentic coordinate validation
      assertTrue("Latitude within Indonesia/Mantangai bounds", sample.latitude in -10.0..10.0)
      assertTrue("Longitude within Indonesia/Mantangai bounds", sample.longitude in 95.0..141.0)
      assertEquals("VIIRS", sample.instrument)
      assertNotNull("Satellite acquisition timestamp must be derived from acqDate and acqTime", sample.acquisitionTimestampMillis)
    }

    // Verify Repository integration
    val repository = RealFireDataRepository(credentialProvider, dataSource)
    val repoResponse = repository.refreshFireData(force = true)
    assertEquals(200, repoResponse.httpStatusCode)
    assertEquals(FireDataCredentialState.CONFIGURED, repository.credentialState.value)
  }
}
