package com.example.core.contract

/**
 * Contract status definition based on Master Development Contract Section 3.
 *
 * Rule: JANGAN PERNAH mengubah IMPLEMENTED menjadi PRODUCTION_READY tanpa bukti pengujian.
 */
enum class FeatureStatus {
  NOT_STARTED,
  IMPLEMENTING,
  IMPLEMENTED,
  BUILD_VERIFIED,
  RUNTIME_VERIFIED,
  DATA_VERIFIED,
  PRODUCTION_READY,
  BLOCKED
}
