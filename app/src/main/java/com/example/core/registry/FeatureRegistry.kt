package com.example.core.registry

import com.example.core.contract.FeatureContract
import com.example.core.contract.FeatureStatus

/**
 * Master Feature Registry as required by Section 26.
 * Zero-Dummy policy: FIRE-003 to FIRE-008 are strictly NOT_STARTED.
 */
object FeatureRegistry {

  val features: List<FeatureContract> = listOf(
    FeatureContract(
      id = "FIRE-001",
      name = "Master Development Contract",
      purpose = "Menetapkan aturan absolut Zero-Dummy, validasi bertahap, status kejujuran, dan kontrak pengembangan bukti nyata.",
      input = "Spesifikasi Prompt 001",
      process = "Deklarasi kontrak, status enum, dan registry bukti nyata",
      output = "Kontrak pengawasan arsitektur aktif",
      dataSource = "Prompt 001 Engineering Contract",
      dependencies = emptyList(),
      successCriteria = "Semua aturan absolut terdokumentasi dan diimplementasikan tanpa dummy",
      failureCriteria = "Adanya data dummy, koordinat palsu, atau klaim tidak terverifikasi",
      testProcedure = "Audit statis kode, verifikasi integritas arsitektur",
      verificationLevel = "GATE 2: IMPLEMENTATION COMPLETE",
      status = FeatureStatus.IMPLEMENTED
    ),
    FeatureContract(
      id = "FIRE-002",
      name = "Android Project Foundation",
      purpose = "Fondasi native Android Kotlin Jetpack Compose, package name, build configuration, struktur modular, dan error logging.",
      input = "Android SDK & Jetpack Compose toolchain",
      process = "Konfigurasi Gradle, themes, strings, modular packages, unit tests",
      output = "Aplikasi terkompilasi siap pengembangan bertahap",
      dataSource = "Android Gradle Build System",
      dependencies = listOf("androidx.compose", "androidx.core.ktx", "material3"),
      successCriteria = "Gradle build sukses, unit test Robolectric lolos",
      failureCriteria = "Kompilasi error atau kegagalan konfigurasi project",
      testProcedure = "compile_applet & gradle unit test",
      verificationLevel = "GATE 2: IMPLEMENTATION COMPLETE",
      status = FeatureStatus.IMPLEMENTED
    ),
    FeatureContract(
      id = "FIRE-003",
      name = "Dashboard",
      purpose = "Layar utama monitoring status aplikasi dan hotspot wilayah Hardi Mantangai secara jujur tanpa dummy.",
      input = "DashboardState (DataState NOT_AVAILABLE / NOT_VERIFIED)",
      process = "Render status sistem, indikator lokasi jujur, kartu titik api non-dummy, kartu satelit, dan last update",
      output = "DashboardScreen M3 berbasis Jetpack Compose",
      dataSource = "Internal System State & Zero-Dummy Contract",
      dependencies = listOf("FIRE-001", "FIRE-002", "androidx.compose.material3"),
      successCriteria = "Judul, system status, location card, fire detection card, satellite data card, dan last update tampil tanpa data dummy",
      failureCriteria = "Menampilkan marker atau angka palsu, klaim GPS/satelit aktif padahal belum diintegrasikan",
      testProcedure = "Robolectric Compose UI test & Activity launch test",
      verificationLevel = "GATE 3: RUNTIME VERIFIED (LOCAL)",
      status = FeatureStatus.RUNTIME_VERIFIED,
      knownLimitations = "FIRE-004 GPS & FIRE-006 Real Fire Data Source belum aktif. Menampilkan status NOT_AVAILABLE / NOT_VERIFIED secara jujur."
    ),
    FeatureContract(
      id = "FIRE-004",
      name = "Device GPS",
      purpose = "Akuisisi lokasi riil perangkat Android secara presisi tanpa koordinat palsu",
      input = "Android LocationManager (GPS_PROVIDER / NETWORK_PROVIDER)",
      process = "Pemeriksaan runtime permissions, verifikasi provider GPS, request location fix nyata, sanity check (-90..90, -180..180, acc >= 0), dan update Dashboard",
      output = "DeviceLocation nyata (latitude, longitude, accuracy, fix timestamp, provider)",
      dataSource = "Android Location Service nyata (Zero dummy)",
      dependencies = listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
      successCriteria = "Mendapatkan lokasi riil dari sensor GPS atau menampilkan state jujur saat izin/provider tidak tersedia",
      failureCriteria = "Koordinat hardcoded atau lokasi default dianggap lokasi pengguna",
      testProcedure = "Unit test permission flow & provider state, Robolectric sanity check, Compose UI test",
      verificationLevel = "GATE 3: RUNTIME VERIFIED (LOCAL)",
      status = FeatureStatus.RUNTIME_VERIFIED,
      knownLimitations = "Uji perangkat fisik (Real Device) belum tersedia pada container cloud. GPS diuji via pengujian lokal Robolectric & JVM."
    ),
    FeatureContract(
      id = "FIRE-005",
      name = "Map Foundation",
      purpose = "Visualisasi peta geografis wilayah berbasis osmdroid OpenStreetMap & posisi nyata GPS pengguna",
      input = "DeviceLocation terverifikasi dari FIRE-004",
      process = "Inisialisasi MapView OpenStreetMap native (osmdroid), validasi rentang koordinat geografis, rendering marker posisi pengguna jika valid, zero fire markers policy",
      output = "Layer peta nyata interaktif dengan marker posisi GPS pengguna",
      dataSource = "osmdroid OpenStreetMap Standard Tiles (Mapnik)",
      dependencies = listOf("FIRE-004"),
      successCriteria = "MapView tampil dengan tile OpenStreetMap, posisi pengguna dirender sesuai koordinat GPS nyata, validasi koordinat mencegah out-of-bounds, zero fire markers sampai data satelit aktif",
      failureCriteria = "Marker contoh dibuat tanpa data nyata, hardcoded dummy coordinates, atau penambahan marker api sebelum FIRE-006",
      testProcedure = "Robolectric MapFoundationRobolectricTest, CoordinateValidator boundary test, MapStatus lifecycle test, Dashboard integration test",
      verificationLevel = "GATE 2: REAL_DEVICE_VERIFICATION_PENDING (REAL DEVICE GPS NOT VERIFIED)",
      status = FeatureStatus.REAL_DEVICE_VERIFICATION_PENDING,
      knownLimitations = "REAL DEVICE GPS NOT VERIFIED: Aplikasi diuji di lingkungan emulator/cloud container. Koordinat yang diterima berasal dari virtual location provider emulator, bukan GPS satelit fisik. Memerlukan verifikasi pada perangkat fisik nyata."
    ),
    FeatureContract(
      id = "FIRE-006",
      name = "Real Fire Data Source",
      purpose = "Koneksi API / data satelit hotspot riil",
      input = "Endpoint API resmi (misal NASA FIRMS / BMKG / KLHK)",
      process = "Belum dimulai",
      output = "Raw data hotspot satelit",
      dataSource = "DATA SOURCE NOT VERIFIED",
      dependencies = emptyList(),
      successCriteria = "HTTP response 200 dengan payload valid dari satelit",
      failureCriteria = "Data dummy atau mock response dianggap data produksi",
      testProcedure = "Integrasi API nyata dengan network call",
      verificationLevel = "GATE 1: NOT_STARTED",
      status = FeatureStatus.NOT_STARTED,
      knownLimitations = "Dilarang diimplementasikan pada Prompt 001"
    ),
    FeatureContract(
      id = "FIRE-007",
      name = "Fire Data Processing",
      purpose = "Filter dan parsing data hotspot wilayah Hardi Mantangai",
      input = "Raw data satelit",
      process = "Belum dimulai",
      output = "Model data titik api terverifikasi",
      dataSource = "FIRE-006 output",
      dependencies = emptyList(),
      successCriteria = "Bounding box dan timestamp dihitung dengan akurat",
      failureCriteria = "Timestamp manipulatif (LIVE/REAL-TIME tanpa bukti akuisisi satelit)",
      testProcedure = "Unit test algoritma filter spasial",
      verificationLevel = "GATE 1: NOT_STARTED",
      status = FeatureStatus.NOT_STARTED,
      knownLimitations = "Dilarang diimplementasikan pada Prompt 001"
    ),
    FeatureContract(
      id = "FIRE-008",
      name = "Verified Fire Markers",
      purpose = "Marker titik api di peta berdasarkan data hasil proses",
      input = "Hasil FIRE-007",
      process = "Belum dimulai",
      output = "Marker titik api terverifikasi",
      dataSource = "FIRE-007",
      dependencies = emptyList(),
      successCriteria = "Marker hanya muncul jika data satelit nyata tersedia",
      failureCriteria = "Marker muncul saat data kosong atau data dummy",
      testProcedure = "Verifikasi tampilan marker terhadap data mentah",
      verificationLevel = "GATE 1: NOT_STARTED",
      status = FeatureStatus.NOT_STARTED,
      knownLimitations = "Dilarang diimplementasikan pada Prompt 001"
    )
  )

  fun getFeature(id: String): FeatureContract? {
    return features.firstOrNull { it.id == id }
  }
}
