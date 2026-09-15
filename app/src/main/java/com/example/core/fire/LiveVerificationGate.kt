package com.example.core.fire

/**
 * Explicit Live Verification Gate contract adhering strictly to Prompt 006B Section 16.
 *
 * Mandate:
 * - Hanya LIVE_API_VERIFIED yang boleh membuat FIRE-006 mendapatkan status DATA_VERIFIED.
 * - Semua status lainnya (NOT_VERIFIED, ERROR, CREDENTIAL_REQUIRED, INVALID_RESPONSE, NETWORK_ERROR)
 *   WAJIB menahan FIRE-006 pada status IMPLEMENTED.
 * - JANGAN PERNAH memberikan status PRODUCTION_READY hanya berdasarkan unit test atau build pass.
 */
enum class LiveVerificationGate {
  LIVE_API_NOT_VERIFIED,
  LIVE_API_VERIFIED,
  LIVE_API_NETWORK_ERROR,
  LIVE_API_CREDENTIAL_REQUIRED,
  LIVE_API_INVALID_RESPONSE,
  LIVE_API_HTTP_ERROR,
  LIVE_API_PARSER_ERROR,
  LIVE_API_ERROR,
  LIVE_API_UNKNOWN_ERROR
}
