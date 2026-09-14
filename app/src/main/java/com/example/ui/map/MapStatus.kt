package com.example.ui.map

/**
 * State status peta geografis sesuai Section 12 Prompt 005:
 * - MAP_LOADING: Komponen peta sedang dalam proses inisialisasi tile engine
 * - MAP_READY: Peta geografis nyata berhasil diinisialisasi dan siap berinteraksi
 * - MAP_ERROR: Terjadi kegagalan inisialisasi provider peta atau network tile
 */
enum class MapStatus {
  MAP_LOADING,
  MAP_READY,
  MAP_ERROR
}
