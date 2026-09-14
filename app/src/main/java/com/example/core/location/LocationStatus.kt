package com.example.core.location

/**
 * State status lokasi perangkat sesuai Section 6 Prompt 004:
 * - LOCATION_PERMISSION_REQUIRED: Izin lokasi Android belum diminta/diberikan
 * - LOCATION_PERMISSION_DENIED: Pengguna menolak izin lokasi
 * - LOCATION_PROVIDER_DISABLED: GPS/Location service pada perangkat dinonaktifkan
 * - LOCATION_LOADING: Sedang melakukan permintaan location fix nyata dari sensor
 * - LOCATION_AVAILABLE: Data koordinat nyata dari provider berhasil diperoleh
 * - LOCATION_ERROR: Terjadi kesalahan teknis atau timeout saat mengambil lokasi
 */
enum class LocationStatus {
  LOCATION_PERMISSION_REQUIRED,
  LOCATION_PERMISSION_DENIED,
  LOCATION_PROVIDER_DISABLED,
  LOCATION_LOADING,
  LOCATION_AVAILABLE,
  LOCATION_ERROR
}
