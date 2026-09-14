package com.example.core.map

/**
 * Informasi eksplisit provider peta geografis sesuai Section 3 & 4 Prompt 005.
 *
 * Provider: osmdroid (OpenStreetMap for Android)
 * Credential: NOT_REQUIRED (Layanan open-source tanpa proprietary API key)
 * Tile Source: OpenStreetMap Standard Tiles (Mapnik)
 * Zero Fake Map: Peta native interaktif dengan proyeksi koordinat geografis nyata.
 */
object MapProviderInfo {
  const val PROVIDER_NAME = "osmdroid (OpenStreetMap Native Android SDK)"
  const val CREDENTIAL_STATUS = "NOT_REQUIRED"
  const val TILE_SOURCE = "OpenStreetMap Standard (Mapnik)"
  const val ZERO_FIRE_MARKERS_POLICY = "ZERO FIRE MARKERS — FIRE-006 s/d FIRE-008 NOT_STARTED"
}
