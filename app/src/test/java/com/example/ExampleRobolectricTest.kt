package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.contract.FeatureStatus
import com.example.core.logging.AppError
import com.example.core.logging.AppLogger
import com.example.core.logging.ErrorType
import com.example.core.registry.FeatureRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("HARDI MANTANGAI FIRE NOW", appName)
  }

  @Test
  fun `verify initial feature registry adheres to contract`() {
    val features = FeatureRegistry.features
    assertEquals(8, features.size)

    val fire001 = FeatureRegistry.getFeature("FIRE-001")
    assertNotNull(fire001)
    assertEquals("Master Development Contract", fire001?.name)
    assertEquals(FeatureStatus.IMPLEMENTED, fire001?.status)

    val fire002 = FeatureRegistry.getFeature("FIRE-002")
    assertNotNull(fire002)
    assertEquals("Android Project Foundation", fire002?.name)
    assertEquals(FeatureStatus.IMPLEMENTED, fire002?.status)

    // Prompt 003: FIRE-003 is RUNTIME_VERIFIED
    val fire003 = FeatureRegistry.getFeature("FIRE-003")
    assertNotNull(fire003)
    assertEquals("Dashboard", fire003?.name)
    assertEquals(FeatureStatus.RUNTIME_VERIFIED, fire003?.status)

    // Prompt 005B: FIRE-004 status must be REAL_DEVICE_VERIFICATION_PENDING (Hardware GPS pending physical verification)
    val fire004 = FeatureRegistry.getFeature("FIRE-004")
    assertNotNull(fire004)
    assertEquals("Device GPS", fire004?.name)
    assertEquals(
      "Feature FIRE-004 must be REAL_DEVICE_VERIFICATION_PENDING on Prompt 005B",
      FeatureStatus.REAL_DEVICE_VERIFICATION_PENDING,
      fire004?.status
    )

    // Prompt 005A: FIRE-005 status must be REAL_DEVICE_VERIFICATION_PENDING (PRODUCTION_READY revoked)
    val fire005 = FeatureRegistry.getFeature("FIRE-005")
    assertNotNull(fire005)
    assertEquals("Map Foundation", fire005?.name)
    assertEquals(
      "Feature FIRE-005 must be REAL_DEVICE_VERIFICATION_PENDING on Prompt 005A",
      FeatureStatus.REAL_DEVICE_VERIFICATION_PENDING,
      fire005?.status
    )

    val fire006 = FeatureRegistry.getFeature("FIRE-006")
    assertNotNull(fire006)
    assertEquals(
      "Feature FIRE-006 must be IMPLEMENTED on Prompt 006A",
      FeatureStatus.IMPLEMENTED,
      fire006?.status
    )

    listOf("FIRE-007", "FIRE-008").forEach { id ->
      val feat = FeatureRegistry.getFeature(id)
      assertNotNull(feat)
      assertEquals(
        "Feature $id must strictly be NOT_STARTED on Prompt 006A",
        FeatureStatus.NOT_STARTED,
        feat?.status
      )
    }
  }

  @Test
  fun `verify error logging structure adheres to rule 12`() {
    val error = AppError(
      type = ErrorType.NETWORK_ERROR,
      message = "Failed to reach endpoint",
      source = "RemoteServiceTest",
      recoveryAction = "Retry with exponential backoff"
    )

    AppLogger.recordError(error)
    val loggedErrors = AppLogger.errorLog.value
    assertTrue("Error should be recorded in error log", loggedErrors.isNotEmpty())
    assertEquals(ErrorType.NETWORK_ERROR, loggedErrors.first().type)
    assertEquals("RemoteServiceTest", loggedErrors.first().source)
  }

  @Test
  fun `verify MainActivity startup lifecycle finishes without crash`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
    val activity = controller.setup().get()
    assertNotNull(activity)
    assertTrue("Activity should not be finishing", !activity.isFinishing)
  }
}

