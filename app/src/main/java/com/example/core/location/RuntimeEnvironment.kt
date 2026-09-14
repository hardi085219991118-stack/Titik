package com.example.core.location

import android.os.Build

/**
 * Section 7 Prompt 005B: Status Lingkungan Runtime Perangkat.
 *
 * Nilai eksplisit:
 * - REAL_PHYSICAL_DEVICE: Perangkat Android fisik nyata
 * - EMULATOR: Android Virtual Device / QEMU / Goldfish / Ranchu
 * - VIRTUAL_DEVICE: Cloud container / Container-based Android / VirtualBox
 * - UNKNOWN: Lingkungan tidak dapat dipastikan
 *
 * Aturan Mutlak: JANGAN MENEBAK. Jika tidak dapat dibuktikan sebagai perangkat fisik,
 * DILARANG menulis REAL DEVICE.
 */
enum class RuntimeEnvironment {
  REAL_PHYSICAL_DEVICE,
  EMULATOR,
  VIRTUAL_DEVICE,
  UNKNOWN;

  companion object {
    fun detect(): RuntimeEnvironment {
      val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
      val model = Build.MODEL.orEmpty().lowercase()
      val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
      val hardware = Build.HARDWARE.orEmpty().lowercase()
      val product = Build.PRODUCT.orEmpty().lowercase()
      val brand = Build.BRAND.orEmpty().lowercase()
      val device = Build.DEVICE.orEmpty().lowercase()

      val isEmulator = fingerprint.startsWith("generic") ||
          fingerprint.startsWith("unknown") ||
          model.contains("google_sdk") ||
          model.contains("emulator") ||
          model.contains("android sdk built for") ||
          manufacturer.contains("genymotion") ||
          hardware.contains("goldfish") ||
          hardware.contains("ranchu") ||
          product.contains("sdk") ||
          product.contains("google_sdk") ||
          product.contains("sdk_gphone") ||
          product.contains("vbox86p") ||
          (brand.startsWith("generic") && device.startsWith("generic"))

      val isVirtual = hardware.contains("vbox") ||
          model.contains("virtual") ||
          product.contains("virtual") ||
          manufacturer.contains("cros")

      return when {
        isEmulator -> EMULATOR
        isVirtual -> VIRTUAL_DEVICE
        hardware.isNotBlank() && !isEmulator && !isVirtual &&
            !fingerprint.contains("robolectric") &&
            !hardware.contains("unknown") -> REAL_PHYSICAL_DEVICE
        else -> UNKNOWN
      }
    }
  }
}
