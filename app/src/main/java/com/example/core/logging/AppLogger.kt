package com.example.core.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Audit Logger enforcing Section 12 (Error Handling) and Section 1 (Absolut Truthfulness).
 *
 * Rules:
 * - DILARANG try/catch kosong.
 * - DILARANG catch error -> tampilkan "Success".
 * - DILARANG menyembunyikan error agar dashboard terlihat normal.
 */
object AppLogger {

  private const val TAG = "HardiMantangaiFire"

  private val _errorLog = MutableStateFlow<List<AppError>>(emptyList())
  val errorLog: StateFlow<List<AppError>> = _errorLog.asStateFlow()

  private val _auditEvents = MutableStateFlow<List<String>>(
    listOf("System initialized under Master Development Contract (Zero Dummy Mode)")
  )
  val auditEvents: StateFlow<List<String>> = _auditEvents.asStateFlow()

  fun recordError(error: AppError) {
    Log.e(TAG, error.toFormattedString(), error.cause)
    val current = _errorLog.value.toMutableList()
    current.add(0, error)
    if (current.size > 50) current.removeAt(current.lastIndex)
    _errorLog.value = current
  }

  fun recordEvent(event: String) {
    Log.i(TAG, "[AUDIT] $event")
    val current = _auditEvents.value.toMutableList()
    current.add(0, event)
    if (current.size > 50) current.removeAt(current.lastIndex)
    _auditEvents.value = current
  }

  fun clearErrors() {
    _errorLog.value = emptyList()
  }
}
