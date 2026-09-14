package com.example.core.map

import com.example.core.location.DeviceLocation

/**
 * Validator koordinat geografis sesuai Section 14 Prompt 005:
 * latitude: -90.0 <= lat <= +90.0
 * longitude: -180.0 <= lon <= +180.0
 * accuracy: >= 0 jika tersedia.
 *
 * Jika invalid: DILARANG menampilkan marker, tampilkan "INVALID LOCATION DATA".
 */
object CoordinateValidator {

  data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
  )

  fun isValid(latitude: Double, longitude: Double, accuracyMeters: Float? = null): Boolean {
    return validate(latitude, longitude, accuracyMeters).isValid
  }

  fun validate(latitude: Double, longitude: Double, accuracyMeters: Float? = null): ValidationResult {
    if (latitude.isNaN() || latitude.isInfinite() || latitude < -90.0 || latitude > 90.0) {
      return ValidationResult(
        isValid = false,
        errorMessage = "Latitude $latitude berada di luar batas valid [-90.0, +90.0]"
      )
    }
    if (longitude.isNaN() || longitude.isInfinite() || longitude < -180.0 || longitude > 180.0) {
      return ValidationResult(
        isValid = false,
        errorMessage = "Longitude $longitude berada di luar batas valid [-180.0, +180.0]"
      )
    }
    if (accuracyMeters != null && (accuracyMeters.isNaN() || accuracyMeters.isInfinite() || accuracyMeters < 0f)) {
      return ValidationResult(
        isValid = false,
        errorMessage = "Akurasi $accuracyMeters m tidak valid (harus >= 0)"
      )
    }
    return ValidationResult(isValid = true)
  }

  fun validateLocation(location: DeviceLocation?): ValidationResult {
    if (location == null) {
      return ValidationResult(isValid = false, errorMessage = "Data lokasi null")
    }
    return validate(location.latitude, location.longitude, location.accuracyMeters)
  }
}
