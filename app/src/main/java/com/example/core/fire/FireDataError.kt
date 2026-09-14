package com.example.core.fire

/**
 * Detailed Fire Data Error Classification adhering strictly to Section 9 of Prompt 006A:
 *
 * All errors must have explicit internal status and user-facing recovery actions.
 * JANGAN PERNAH MENAMPILKAN "NO FIRE" UNTUK ERROR NETWORK / HTTP.
 */
sealed class FireDataError(
  val code: String,
  val message: String,
  val httpStatusCode: Int? = null,
  val recoveryAction: String
) {
  class HttpError(
    statusCode: Int,
    detailMessage: String
  ) : FireDataError(
    code = "HTTP_$statusCode",
    message = "NASA FIRMS server mengembalikan error HTTP $statusCode: $detailMessage",
    httpStatusCode = statusCode,
    recoveryAction = when (statusCode) {
      400 -> "Periksa parameter bounding box atau format query."
      401, 403 -> "MAP_KEY NASA FIRMS tidak valid atau belum terdaftar. Daftarkan MAP_KEY di https://firms.modaps.eosdis.nasa.gov."
      404 -> "Endpoint FIRMS tidak ditemukan atau data untuk tanggal tersebut tidak tersedia."
      429 -> "Rate limit NASA FIRMS tercapai (5000 trans/10m). Tunggu cooldown sebelum request ulang."
      500, 502, 503 -> "Server NASA FIRMS sedang dalam perbaikan atau kelebihan beban. Coba lagi beberapa saat lagi."
      else -> "Periksa respon server NASA FIRMS."
    }
  )

  object NoInternet : FireDataError(
    code = "NO_INTERNET",
    message = "Koneksi internet perangkat tidak tersedia.",
    recoveryAction = "Pastikan Wi-Fi atau data seluler perangkat aktif."
  )

  object DnsError : FireDataError(
    code = "DNS_RESOLUTION_ERROR",
    message = "Gagal menyelesaikan domain NASA FIRMS (firms.modaps.eosdis.nasa.gov).",
    recoveryAction = "Periksa koneksi DNS perangkat atau periksa koneksi internet."
  )

  object Timeout : FireDataError(
    code = "TIMEOUT",
    message = "Waktu koneksi ke server NASA FIRMS habis (Timeout).",
    recoveryAction = "Periksa kecepatan jaringan Anda dan coba lagi."
  )

  object SslError : FireDataError(
    code = "SSL_ERROR",
    message = "Kegagalan sertifikat SSL saat berkomunikasi dengan server NASA FIRMS.",
    recoveryAction = "Periksa pengaturan tanggal/waktu perangkat Anda agar sertifikat SSL valid."
  )

  class InvalidResponse(val reason: String) : FireDataError(
    code = "INVALID_RESPONSE",
    message = "Respon server NASA FIRMS tidak valid: $reason",
    recoveryAction = "Format data yang diterima tidak sesuai kontrak CSV NASA FIRMS."
  )

  object EmptyResponse : FireDataError(
    code = "EMPTY_RESPONSE",
    message = "Respon HTTP kosong dari server NASA FIRMS.",
    recoveryAction = "Server NASA FIRMS tidak mengembalikan payload data."
  )

  object MissingCredential : FireDataError(
    code = "MISSING_CREDENTIAL",
    message = "MAP_KEY NASA FIRMS belum dikonfigurasi.",
    recoveryAction = "Masukkan MAP_KEY NASA FIRMS resmi di halaman pengaturan/dashboard."
  )

  object RateLimitCooldown : FireDataError(
    code = "COOLDOWN_ACTIVE",
    message = "Permintaan terlalu sering. Cooldown aktif untuk melindungi API NASA.",
    recoveryAction = "Tunggu beberapa detik sebelum menekan tombol perbarui kembali."
  )
}
