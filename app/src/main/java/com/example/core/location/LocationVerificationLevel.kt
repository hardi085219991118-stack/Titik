package com.example.core.location

/**
 * Section 8 & 9 Prompt 005B: Tingkat Verifikasi Lokasi.
 *
 * Nilai:
 * - NOT_AVAILABLE: Lokasi belum tersedia dari sensor/provider.
 * - UNVERIFIED: Lokasi diterima tetapi belum lolos pemeriksaan integritas.
 * - CACHED: Lokasi berasal dari cache sistem (lastKnownLocation) atau sudah kadaluarsa.
 * - MOCK: Lokasi terdeteksi berasal dari Mock/Virtual Location Provider (TIDAK BOLEH DISEBUT REAL GPS).
 * - VIRTUAL: Lokasi berasal dari emulator/cloud runtime AI Studio (BUKAN REAL DEVICE GPS).
 * - REAL_DEVICE_UNVERIFIED: Lokasi berasal dari perangkat fisik, namun belum dikonfirmasi fisik manual.
 * - REAL_DEVICE_VERIFIED: Lokasi berasal dari perangkat Android fisik nyata, bukan emulator,
 *   bukan virtual, bukan mock, permission granted, provider aktif, timestamp valid,
 *   koordinat valid, dan zero hardcoded coordinates.
 */
enum class LocationVerificationLevel {
  NOT_AVAILABLE,
  UNVERIFIED,
  CACHED,
  MOCK,
  VIRTUAL,
  REAL_DEVICE_UNVERIFIED,
  REAL_DEVICE_VERIFIED,
  UNKNOWN
}
