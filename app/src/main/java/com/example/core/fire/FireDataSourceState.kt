package com.example.core.fire

/**
 * Honest Fire Data Source States adhering strictly to Section 15 of Prompt 006A:
 *
 * Rules:
 * - DATA_SOURCE_AVAILABLE: Request ke NASA FIRMS berhasil dan response valid dengan deteksi.
 * - NO_DETECTIONS_IN_QUERY: Request berhasil dan response valid tetapi memang tidak ada deteksi dalam query.
 * - API_CREDENTIAL_REQUIRED: MAP_KEY NASA FIRMS belum dikonfigurasi.
 * - DATA_SOURCE_UNAVAILABLE: Server error (HTTP 500/502/503/404) atau request gagal.
 * - NETWORK_ERROR: Tidak ada koneksi internet, DNS error, atau socket connection refused.
 * - TIMEOUT: Request melebihi batas waktu koneksi.
 * - INVALID_DATA_RESPONSE: Header atau payload tidak sesuai spesifikasi CSV FIRMS.
 * - RATE_LIMIT_EXCEEDED: HTTP 429 Too Many Requests dari NASA FIRMS.
 * - NOT_VERIFIED: Status awal sebelum request pertama kali dilakukan.
 * - CACHED: Menampilkan data cache saat offline atau dalam periode cache window.
 *
 * ATURAN PALING PENTING:
 * JANGAN PERNAH MENAMPILKAN "0 TITIK API" KETIKA REQUEST DATA GAGAL.
 */
enum class FireDataSourceState(
  val displayName: String,
  val isError: Boolean = false
) {
  NOT_VERIFIED("DATA SOURCE NOT VERIFIED", false),
  API_CREDENTIAL_REQUIRED("API CREDENTIAL REQUIRED", true),
  CONNECTING("CONNECTING TO NASA FIRMS", false),
  DATA_SOURCE_AVAILABLE("DATA SOURCE AVAILABLE", false),
  NO_DETECTIONS_IN_QUERY("NO DETECTIONS IN QUERY", false),
  DATA_SOURCE_UNAVAILABLE("DATA SOURCE UNAVAILABLE", true),
  NETWORK_ERROR("NETWORK ERROR", true),
  TIMEOUT("TIMEOUT", true),
  INVALID_DATA_RESPONSE("INVALID DATA RESPONSE", true),
  RATE_LIMIT_EXCEEDED("RATE LIMIT EXCEEDED (HTTP 429)", true),
  CACHED("CACHED DATA", false)
}
