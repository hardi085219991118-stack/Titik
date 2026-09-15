package com.example.core.fire

/**
 * Kalkulator usia data titik api satelit sesuai Bagian 6 & Prioritas 4:
 * - Memisahkan waktu akuisisi satelit (acquisition timestamp UTC) dari waktu fetch perangkat.
 * - Mengkategorikan usia data secara jujur:
 *   < 1 jam, 1–3 jam, 3–6 jam, 6–12 jam, 12–24 jam, > 24 jam.
 */
object FireDataAgeCalculator {

  enum class AgeCategory(val label: String) {
    UNDER_1_HOUR("< 1 jam"),
    FROM_1_TO_3_HOURS("1–3 jam"),
    FROM_3_TO_6_HOURS("3–6 jam"),
    FROM_6_TO_12_HOURS("6–12 jam"),
    FROM_12_TO_24_HOURS("12–24 jam"),
    OVER_24_HOURS("> 24 jam"),
    UNKNOWN("BELUM TERSEDIA")
  }

  fun categorize(acquisitionTimestampMillis: Long?, nowMillis: Long = System.currentTimeMillis()): AgeCategory {
    if (acquisitionTimestampMillis == null || acquisitionTimestampMillis <= 0) return AgeCategory.UNKNOWN
    val diff = nowMillis - acquisitionTimestampMillis
    if (diff < 0) return AgeCategory.UNKNOWN
    val hours = diff / (1000 * 60 * 60)
    return when {
      hours < 1 -> AgeCategory.UNDER_1_HOUR
      hours < 3 -> AgeCategory.FROM_1_TO_3_HOURS
      hours < 6 -> AgeCategory.FROM_3_TO_6_HOURS
      hours < 12 -> AgeCategory.FROM_6_TO_12_HOURS
      hours < 24 -> AgeCategory.FROM_12_TO_24_HOURS
      else -> AgeCategory.OVER_24_HOURS
    }
  }

  fun formatAgeDetail(acquisitionTimestampMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
    if (acquisitionTimestampMillis == null || acquisitionTimestampMillis <= 0) return "BELUM TERSEDIA"
    val diff = nowMillis - acquisitionTimestampMillis
    if (diff < 0) return "BELUM TERSEDIA"
    val hours = diff / (1000 * 60 * 60)
    val minutes = (diff % (1000 * 60 * 60)) / (1000 * 60)
    val category = categorize(acquisitionTimestampMillis, nowMillis)
    return if (hours == 0L) {
      "${category.label} (${minutes}m lalu)"
    } else {
      "${category.label} (${hours}j ${minutes}m lalu)"
    }
  }
}
