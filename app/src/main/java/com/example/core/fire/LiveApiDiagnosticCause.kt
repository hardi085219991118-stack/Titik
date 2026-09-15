package com.example.core.fire

/**
 * 26 Explicit Diagnostic Status Codes for NASA FIRMS Live Connection Audit
 * adhering strictly to Section 1 of Prompt 006C.
 */
enum class LiveApiDiagnosticCause(
  val code: String,
  val description: String
) {
  REQUEST_NOT_STARTED(
    code = "REQUEST_NOT_STARTED",
    description = "Permintaan live ke NASA FIRMS belum pernah dieksekusi sejak aplikasi dibuka."
  ),
  DNS_FAILURE(
    code = "DNS_FAILURE",
    description = "Gagal menyelesaikan domain firms.modaps.eosdis.nasa.gov (UnknownHostException)."
  ),
  NETWORK_FAILURE(
    code = "NETWORK_FAILURE",
    description = "Kegagalan koneksi jaringan atau tidak ada rute ke host (IOException)."
  ),
  TLS_FAILURE(
    code = "TLS_FAILURE",
    description = "Kegagalan negosiasi TLS/SSL handshake atau sertifikat tidak valid (SSLException)."
  ),
  TIMEOUT(
    code = "TIMEOUT",
    description = "Batas waktu koneksi atau pembacaan respon habis (SocketTimeoutException)."
  ),
  INVALID_URL(
    code = "INVALID_URL",
    description = "Format URL endpoint NASA FIRMS tidak valid atau malformed."
  ),
  INVALID_SOURCE(
    code = "INVALID_SOURCE",
    description = "Sensor satelit yang diminta tidak dikenali atau tidak didukung NASA FIRMS."
  ),
  INVALID_AREA(
    code = "INVALID_AREA",
    description = "Koordinat bounding box tidak valid (WEST >= EAST, SOUTH >= NORTH, atau diluar batas)."
  ),
  INVALID_DAY_RANGE(
    code = "INVALID_DAY_RANGE",
    description = "Parameter rentang hari (dayRange) tidak berada dalam batas 1 s/d 10."
  ),
  MISSING_CREDENTIAL(
    code = "MISSING_CREDENTIAL",
    description = "MAP_KEY NASA FIRMS belum dikonfigurasi pada storage lokal perangkat."
  ),
  INVALID_CREDENTIAL(
    code = "INVALID_CREDENTIAL",
    description = "MAP_KEY NASA FIRMS ditolak oleh server (HTTP 401/403 atau respon 'Invalid MAP_KEY')."
  ),
  HTTP_400(
    code = "HTTP_400",
    description = "Server NASA FIRMS mengembalikan HTTP 400 Bad Request (parameter query salah)."
  ),
  HTTP_401(
    code = "HTTP_401",
    description = "Server NASA FIRMS mengembalikan HTTP 401 Unauthorized (MAP_KEY tidak diizinkan)."
  ),
  HTTP_403(
    code = "HTTP_403",
    description = "Server NASA FIRMS mengembalikan HTTP 403 Forbidden (akses API ditolak)."
  ),
  HTTP_404(
    code = "HTTP_404",
    description = "Server NASA FIRMS mengembalikan HTTP 404 Not Found (endpoint tidak ditemukan)."
  ),
  HTTP_429(
    code = "HTTP_429",
    description = "Server NASA FIRMS mengembalikan HTTP 429 Too Many Requests (Rate limit kuota tercapai)."
  ),
  HTTP_500(
    code = "HTTP_500",
    description = "Server NASA FIRMS mengembalikan HTTP 500 Internal Server Error."
  ),
  HTTP_502(
    code = "HTTP_502",
    description = "Server NASA FIRMS mengembalikan HTTP 502 Bad Gateway."
  ),
  HTTP_503(
    code = "HTTP_503",
    description = "Server NASA FIRMS mengembalikan HTTP 503 Service Unavailable."
  ),
  EMPTY_RESPONSE(
    code = "EMPTY_RESPONSE",
    description = "Server mengembalikan status HTTP 200 namun body respon berukuran 0 byte."
  ),
  INVALID_CSV(
    code = "INVALID_CSV",
    description = "Respon yang diterima bukan format CSV yang valid (misal: halaman error HTML)."
  ),
  INVALID_SCHEMA(
    code = "INVALID_SCHEMA",
    description = "Header kolom CSV tidak sesuai skema FIRMS (kolom latitude/longitude/waktu tidak ditemukan)."
  ),
  PARSER_ERROR(
    code = "PARSER_ERROR",
    description = "Terjadi kegagalan parsing saat membaca baris record data titik api."
  ),
  VALID_RESPONSE_NOT_RECORDED(
    code = "VALID_RESPONSE_NOT_RECORDED",
    description = "Respon valid berhasil diterima namun gagal disimpan ke dalam state repository."
  ),
  LIVE_GATE_LOGIC_ERROR(
    code = "LIVE_GATE_LOGIC_ERROR",
    description = "Kontradiksi logika internal pada transisi gerbang verifikasi live."
  ),
  UNKNOWN_ERROR(
    code = "UNKNOWN_ERROR",
    description = "Kegagalan yang belum terklasifikasi."
  );

  val isFailure: Boolean get() = this != REQUEST_NOT_STARTED
}
