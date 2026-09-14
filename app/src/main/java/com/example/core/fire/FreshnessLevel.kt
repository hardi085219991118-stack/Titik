package com.example.core.fire

/**
 * Honest data freshness classification based on official NASA FIRMS latency:
 *
 * - RT: Real-Time / Direct Broadcast (< 1 hour from satellite overpass)
 * - URT: Ultra Near Real-Time (< 2 hours from satellite overpass)
 * - NRT: Near Real-Time (~3 hours from satellite overpass)
 * - STALE: Data older than freshness window (> 24 hours from satellite overpass)
 * - FRESHNESS_UNKNOWN: Cannot be determined with certainty from data payload
 *
 * RULE: JANGAN PERNAH melabeli data "LIVE" atau "REAL-TIME" hanya karena HTTP request berhasil.
 */
enum class FreshnessLevel(val displayName: String, val description: String) {
  RT("RT (Real-Time)", "Direct broadcast overpass (< 1 jam dari observasi satelit)"),
  URT("URT (Ultra NRT)", "Ultra Near Real-Time (< 2 jam dari observasi satelit)"),
  NRT("NRT (Near Real-Time)", "Near Real-Time (~3 jam dari observasi satelit)"),
  STALE("STALE (> 24 Jam)", "Data lama / observasi satelit lebih dari 24 jam"),
  FRESHNESS_UNKNOWN("FRESHNESS_UNKNOWN", "Tingkat kesegaran tidak dapat ditentukan secara pasti dari payload")
}
