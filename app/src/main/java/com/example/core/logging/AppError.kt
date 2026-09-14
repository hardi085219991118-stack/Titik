package com.example.core.logging

/**
 * Standard Error Types adhering strictly to Section 12 of Master Development Contract.
 */
enum class ErrorType {
  NETWORK_ERROR,
  API_TIMEOUT,
  LOCATION_PERMISSION_DENIED,
  GPS_DISABLED,
  INVALID_RESPONSE,
  AUTHENTICATION_ERROR,
  EMPTY_DATA,
  PARSING_ERROR,
  UNKNOWN_ERROR
}

/**
 * Audit-compliant Error representation.
 * Errors must never be silenced or disguised as success.
 */
data class AppError(
  val type: ErrorType,
  val message: String,
  val source: String,
  val recoveryAction: String,
  val timestampMillis: Long = System.currentTimeMillis(),
  val cause: Throwable? = null
) {
  fun toFormattedString(): String {
    return """
      [ERROR REPORT]
      TYPE: $type
      MESSAGE: $message
      SOURCE: $source
      RECOVERY ACTION: $recoveryAction
      TIMESTAMP: $timestampMillis
    """.trimIndent()
  }
}
