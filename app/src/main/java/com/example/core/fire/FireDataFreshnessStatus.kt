package com.example.core.fire

/**
 * Status kesegaran data satelit sesuai Bagian 14 & Prioritas 4:
 *
 * NOT_VERIFIED: Request belum pernah dilakukan sejak aplikasi dibuka.
 * REQUESTING: Sedang melakukan HTTPS query live ke server NASA FIRMS.
 * FRESH: Data observasi satelit < 1 jam dari overpass.
 * RECENT: Data observasi satelit 1 s/d 6 jam dari overpass.
 * STALE: Data observasi satelit > 24 jam.
 * NO_DATA: Query valid selesai, namun tidak ditemukan titik api dalam area observasi (0 deteksi).
 * ERROR: Terjadi kegagalan komunikasi, DNS, rate limit, atau kredensial.
 *
 * Aturan Kejujuran:
 * - NO DATA tidak sama dengan REQUEST BELUM PERNAH DILAKUKAN.
 * - NO FIRE DETECTED tidak sama dengan API ERROR.
 */
enum class FireDataFreshnessStatus(val displayName: String, val description: String) {
  NOT_VERIFIED(
    displayName = "NOT_VERIFIED",
    description = "Request ke satelit belum pernah dilakukan sejak aplikasi dibuka."
  ),
  REQUESTING(
    displayName = "REQUESTING",
    description = "Sedang menghubungi server NASA FIRMS..."
  ),
  FRESH(
    displayName = "FRESH (< 1 jam)",
    description = "Observasi satelit sangat baru (< 1 jam lalu)."
  ),
  RECENT(
    displayName = "RECENT (1–6 jam)",
    description = "Observasi satelit beberapa jam lalu."
  ),
  STALE(
    displayName = "STALE (> 24 jam)",
    description = "Observasi satelit lebih dari 24 jam lalu."
  ),
  NO_DATA(
    displayName = "NO_DATA (0 DETEKSI)",
    description = "Query satelit valid, tidak ada hotspot teramati dalam area query."
  ),
  ERROR(
    displayName = "ERROR",
    description = "Gagal mengambil data dari NASA FIRMS."
  );

  companion object {
    fun evaluate(
      sourceState: FireDataSourceState,
      records: List<FireDataRecord>,
      fetchTimeMillis: Long
    ): FireDataFreshnessStatus {
      return when (sourceState) {
        FireDataSourceState.NOT_VERIFIED -> NOT_VERIFIED
        FireDataSourceState.CONNECTING -> REQUESTING
        FireDataSourceState.NO_DETECTIONS_IN_QUERY -> NO_DATA
        FireDataSourceState.DATA_SOURCE_AVAILABLE,
        FireDataSourceState.CACHED -> {
          if (records.isEmpty()) {
            NO_DATA
          } else {
            val latestAcq = records.mapNotNull { it.acquisitionTimestampMillis }.maxOrNull()
            if (latestAcq == null) {
              RECENT
            } else {
              val age = fetchTimeMillis - latestAcq
              when {
                age < 60 * 60 * 1000L -> FRESH
                age < 24 * 60 * 60 * 1000L -> RECENT
                else -> STALE
              }
            }
          }
        }
        FireDataSourceState.API_CREDENTIAL_REQUIRED,
        FireDataSourceState.NETWORK_ERROR,
        FireDataSourceState.TIMEOUT,
        FireDataSourceState.RATE_LIMIT_EXCEEDED,
        FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
        FireDataSourceState.INVALID_DATA_RESPONSE -> ERROR
      }
    }
  }
}
