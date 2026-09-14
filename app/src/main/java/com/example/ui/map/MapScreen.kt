package com.example.ui.map

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.logging.AppError
import com.example.core.logging.AppLogger
import com.example.core.logging.ErrorType
import com.example.core.map.CoordinateValidator
import com.example.core.map.MapProviderInfo
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusNotStarted
import com.example.ui.theme.StatusVerified
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen Peta Geografis (FIRE-005 — Map Foundation)
 * Menampilkan peta nyata berbasis osmdroid (OpenStreetMap) dan posisi riil pengguna dari FIRE-004.
 *
 * BATASAN MUTLAK:
 * - ZERO FIRE MARKERS: Tidak ada marker api (karena FIRE-006 s/d FIRE-008 NOT_STARTED).
 * - Tidak ada koordinat hardcoded/dummy.
 * - Koordinat divalidasi sebelum ditampilkan pada peta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
  modifier: Modifier = Modifier,
  deviceLocation: DeviceLocation? = null,
  locationStatus: LocationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED,
  locationErrorMessage: String? = null,
  onBackToDashboard: () -> Unit = {},
  onRefreshLocation: () -> Unit = {},
  onRequestPermission: () -> Unit = {}
) {
  val context = LocalContext.current

  // State inisialisasi peta
  var mapStatus by remember { mutableStateOf(MapStatus.MAP_LOADING) }
  var mapErrorMessage by remember { mutableStateOf<String?>(null) }
  var mapViewRef by remember { mutableStateOf<MapView?>(null) }
  var hasInitialCentered by remember { mutableStateOf(false) }

  // Validasi koordinat sesuai Section 14
  val validationResult = remember(deviceLocation) {
    if (deviceLocation != null) {
      CoordinateValidator.validateLocation(deviceLocation)
    } else {
      CoordinateValidator.ValidationResult(isValid = false)
    }
  }

  // Inisialisasi konfigurasi osmdroid
  DisposableEffect(Unit) {
    try {
      Configuration.getInstance().load(
        context.applicationContext,
        context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
      )
      Configuration.getInstance().userAgentValue = context.packageName
      mapStatus = MapStatus.MAP_READY
    } catch (e: Throwable) {
      mapStatus = MapStatus.MAP_ERROR
      mapErrorMessage = "Gagal inisialisasi peta: ${e.message}"
      AppLogger.recordError(
        AppError(
          type = ErrorType.UNKNOWN_ERROR,
          message = "Gagal inisialisasi peta osmdroid: ${e.message}",
          source = "MapScreen.DisposableEffect",
          recoveryAction = "Periksa permission dan konfigurasi osmdroid",
          cause = e
        )
      )
    }

    onDispose {
      mapViewRef?.onDetach()
    }
  }

  // Efek perpindahan kamera saat lokasi pertama kali tersedia (Section 10)
  LaunchedEffect(deviceLocation, validationResult.isValid, mapViewRef) {
    val mv = mapViewRef
    if (mv != null && deviceLocation != null && validationResult.isValid && !hasInitialCentered) {
      try {
        val targetPoint = GeoPoint(deviceLocation.latitude, deviceLocation.longitude)
        mv.controller.setZoom(16.0)
        mv.controller.animateTo(targetPoint)
        hasInitialCentered = true
      } catch (e: Throwable) {
        AppLogger.recordError(
          AppError(
            type = ErrorType.UNKNOWN_ERROR,
            message = "Gagal mengarahkan kamera ke lokasi: ${e.message}",
            source = "MapScreen.LaunchedEffect",
            recoveryAction = "Verifikasi GeoPoint dan status MapView",
            cause = e
          )
        )
      }
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("map_screen"),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "PETA GEOGRAFIS (FIRE-005)",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              modifier = Modifier.testTag("map_screen_title")
            )
            Text(
              text = "PROVIDER: ${MapProviderInfo.PROVIDER_NAME}",
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
              ),
              modifier = Modifier.testTag("map_provider_info_text")
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onBackToDashboard,
            modifier = Modifier.testTag("back_to_dashboard_from_map_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Kembali ke Dashboard"
            )
          }
        },
        actions = {
          IconButton(
            onClick = onRefreshLocation,
            modifier = Modifier.testTag("refresh_location_from_map_button")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh Lokasi"
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          titleContentColor = MaterialTheme.colorScheme.onSurface
        )
      )
    },
    floatingActionButton = {
      // FAB Pusatkan ke Lokasi Pengguna
      if (locationStatus == LocationStatus.LOCATION_AVAILABLE && deviceLocation != null && validationResult.isValid) {
        FloatingActionButton(
          onClick = {
            mapViewRef?.let { mv ->
              try {
                val pt = GeoPoint(deviceLocation.latitude, deviceLocation.longitude)
                mv.controller.animateTo(pt)
                mv.controller.setZoom(16.5)
              } catch (e: Throwable) {
                AppLogger.recordError(
                  AppError(
                    type = ErrorType.UNKNOWN_ERROR,
                    message = "Gagal memusatkan kamera: ${e.message}",
                    source = "MapScreen.FAB",
                    recoveryAction = "Verifikasi MapView controller",
                    cause = e
                  )
                )
              }
            }
          },
          modifier = Modifier.testTag("recenter_button"),
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
          Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = "Pusatkan ke Posisi Saya"
          )
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // 1. Tampilan Native MapView atau Error Fallback
      if (mapStatus == MapStatus.MAP_ERROR) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            .padding(16.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(48.dp)
            )
            Text(
              text = "MAP INITIALIZATION ERROR",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("map_error_title")
            )
            Text(
              text = mapErrorMessage ?: "Gagal memuat provider peta osmdroid.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer
            )
          }
        }
      } else {
        // Native OpenStreetMap Component
        AndroidView(
          factory = { ctx ->
            try {
              MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(5.0) // Neutral default world view before GPS fix
                mapStatus = MapStatus.MAP_READY
                mapViewRef = this
              }
            } catch (e: Throwable) {
              mapStatus = MapStatus.MAP_ERROR
              mapErrorMessage = e.message ?: "Gagal membuat MapView"
              AppLogger.recordError(
                AppError(
                  type = ErrorType.UNKNOWN_ERROR,
                  message = "Gagal membuat MapView: ${e.message}",
                  source = "MapScreen.AndroidView.factory",
                  recoveryAction = "Periksa konfigurasi runtime MapView",
                  cause = e
                )
              )
              View(ctx)
            }
          },
          update = { mv ->
            if (mv is MapView) {
              // Bersihkan seluruh overlay sebelumnya
              mv.overlays.clear()

              // ZERO FIRE MARKERS: Hanya tambahkan marker posisi pengguna jika lokasi tersedia & valid
              if (locationStatus == LocationStatus.LOCATION_AVAILABLE &&
                deviceLocation != null &&
                validationResult.isValid
              ) {
                val userMarker = Marker(mv).apply {
                  position = GeoPoint(deviceLocation.latitude, deviceLocation.longitude)
                  title = when {
                    deviceLocation.isMock -> "POSISI RUNTIME (MOCK / UNVERIFIED)"
                    deviceLocation.isFromCache -> "POSISI TERAKHIR (CACHED)"
                    else -> "POSISI RUNTIME (${deviceLocation.locationSource})"
                  }
                  val shortTime = if (deviceLocation.timeMillis > 0) {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(deviceLocation.timeMillis))
                  } else "N/A"
                  snippet = String.format(
                    Locale.US,
                    "Lat: %.6f°, Lon: %.6f°\nSumber: %s | Waktu: %s%s",
                    deviceLocation.latitude,
                    deviceLocation.longitude,
                    deviceLocation.locationSource,
                    shortTime,
                    if (deviceLocation.accuracyMeters != null) "\nAkurasi: ±%.1f m".format(deviceLocation.accuracyMeters) else ""
                  )
                  setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mv.overlays.add(userMarker)
              }
              mv.invalidate()
            }
          },
          modifier = Modifier
            .fillMaxSize()
            .testTag("osm_map_view")
        )
      }

      // 2. Status Bar Atas (Status Peta & Status Lokasi)
      MapStatusBar(
        mapStatus = mapStatus,
        locationStatus = locationStatus,
        isValidCoordinate = validationResult.isValid,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(12.dp)
      )

      // 3. Panel Informasi Lokasi Pengguna di Bawah
      UserLocationInfoCard(
        location = deviceLocation,
        locationStatus = locationStatus,
        validationResult = validationResult,
        locationErrorMessage = locationErrorMessage,
        onRequestPermission = onRequestPermission,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(16.dp)
      )
    }
  }
}

/**
 * Top Status Bar displaying MAP STATUS and LOCATION STATUS
 */
@Composable
private fun MapStatusBar(
  mapStatus: MapStatus,
  locationStatus: LocationStatus,
  isValidCoordinate: Boolean,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ),
    shape = RoundedCornerShape(10.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // MAP STATUS
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Map,
          contentDescription = null,
          tint = when (mapStatus) {
            MapStatus.MAP_READY -> StatusVerified
            MapStatus.MAP_ERROR -> StatusBlocked
            MapStatus.MAP_LOADING -> MaterialTheme.colorScheme.primary
          },
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = "MAP: ${mapStatus.name}",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          ),
          color = when (mapStatus) {
            MapStatus.MAP_READY -> StatusVerified
            MapStatus.MAP_ERROR -> StatusBlocked
            MapStatus.MAP_LOADING -> MaterialTheme.colorScheme.primary
          },
          modifier = Modifier.testTag("map_status_badge")
        )
      }

      // LOCATION STATUS
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val locIcon = when (locationStatus) {
          LocationStatus.LOCATION_AVAILABLE -> if (isValidCoordinate) Icons.Default.LocationOn else Icons.Default.Warning
          LocationStatus.LOCATION_LOADING -> Icons.Default.GpsFixed
          LocationStatus.LOCATION_PROVIDER_DISABLED -> Icons.Default.GpsOff
          LocationStatus.LOCATION_PERMISSION_DENIED -> Icons.Default.LocationOff
          else -> Icons.Default.LocationOff
        }
        val locColor = when (locationStatus) {
          LocationStatus.LOCATION_AVAILABLE -> if (isValidCoordinate) StatusVerified else StatusBlocked
          LocationStatus.LOCATION_LOADING -> MaterialTheme.colorScheme.primary
          LocationStatus.LOCATION_ERROR, LocationStatus.LOCATION_PERMISSION_DENIED -> StatusBlocked
          else -> StatusNotStarted
        }

        Icon(
          imageVector = locIcon,
          contentDescription = null,
          tint = locColor,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = if (locationStatus == LocationStatus.LOCATION_AVAILABLE && !isValidCoordinate) {
            "INVALID COORDINATE"
          } else {
            locationStatus.name
          },
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          ),
          color = locColor,
          modifier = Modifier.testTag("map_location_status_badge")
        )
      }
    }
  }
}

/**
 * User Location Information Card adhering to Section 11 Prompt 005.
 */
@Composable
fun UserLocationInfoCard(
  location: DeviceLocation?,
  locationStatus: LocationStatus,
  validationResult: CoordinateValidator.ValidationResult,
  locationErrorMessage: String?,
  onRequestPermission: () -> Unit,
  modifier: Modifier = Modifier
) {
  val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()) }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("user_location_info_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ),
    shape = RoundedCornerShape(14.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      when {
        locationStatus == LocationStatus.LOCATION_AVAILABLE && validationResult.isValid -> StatusVerified.copy(alpha = 0.6f)
        locationStatus == LocationStatus.LOCATION_PERMISSION_DENIED || locationStatus == LocationStatus.LOCATION_ERROR -> StatusBlocked.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
      }
    )
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Header Card
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = "Posisi Saya",
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(16.dp)
            )
          }
          Column {
            Text(
              text = "POSISI SAYA",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("user_location_title")
            )
            Text(
              text = "ZERO FIRE MARKERS — FIRE-006 NOT_STARTED",
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
              ),
              color = StatusNotStarted,
              modifier = Modifier.testTag("zero_fire_markers_badge")
            )
          }
        }

        // Credential badge
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = "CREDENTIAL: ${MapProviderInfo.CREDENTIAL_STATUS}",
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(vertical = 2.dp)
      )

      // Detail Koordinat & Status
      when {
        locationStatus == LocationStatus.LOCATION_AVAILABLE && location != null && validationResult.isValid -> {
          // Source & Truth Status Banner
          if (location.isMock) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "CRITICAL: MOCK / FAKE PROVIDER TERDETEKSI (UNVERIFIED)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onErrorContainer
              )
            }
          } else if (location.isFromCache) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "CACHED FIX (Posisi terakhir tersimpan, bukan Real-time GPS Now)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
            }
          } else {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "RUNTIME FIX: ${location.locationSource}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
          }

          // Real device verification disclaimer
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(4.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = "REAL DEVICE GPS: NOT VERIFIED (Menjalankan Android Runtime / Virtual Provider)",
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          // Latitude & Longitude
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "LATITUDE",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
              )
              Text(
                text = String.format(Locale.US, "%.6f°", location.latitude),
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.testTag("user_latitude_text")
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "LONGITUDE",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
              )
              Text(
                text = String.format(Locale.US, "%.6f°", location.longitude),
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.testTag("user_longitude_text")
              )
            }
          }

          // Accuracy & Timestamp
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "ACCURACY",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              )
              Text(
                text = if (location.accuracyMeters != null) {
                  String.format(Locale.US, "± %.1f m", location.accuracyMeters)
                } else {
                  "BELUM TERSEDIA"
                },
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.testTag("user_accuracy_text")
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "LOCATION TIME (${if (location.isFromCache) "Cache" else "Fix"})",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              )
              Text(
                text = if (location.timeMillis > 0) {
                  timeFormat.format(Date(location.timeMillis))
                } else {
                  "BELUM TERSEDIA"
                },
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.testTag("user_time_text")
              )
            }
          }

          // Source / Provider Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "LOCATION SOURCE",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              )
              Text(
                text = location.locationSource,
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.testTag("user_source_text")
              )
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "PROVIDER STATUS",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              )
              Text(
                text = if (location.isMock) "MOCK (UNTRUSTED)" else "SYSTEM PROVIDER",
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.SemiBold
                ),
                color = if (location.isMock) StatusBlocked else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        locationStatus == LocationStatus.LOCATION_AVAILABLE && !validationResult.isValid -> {
          // Invalid coordinate state (Section 14)
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("invalid_location_warning")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = StatusBlocked,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "INVALID LOCATION DATA",
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace,
                  color = StatusBlocked
                )
              )
            }
            Text(
              text = validationResult.errorMessage ?: "Koordinat berada di luar batas rentang geografis valid.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        locationStatus == LocationStatus.LOCATION_LOADING -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Mengakses sinyal GPS perangkat nyata...",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        locationStatus == LocationStatus.LOCATION_PERMISSION_DENIED -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "LOCATION PERMISSION DENIED",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = StatusBlocked
              )
            )
            Text(
              text = locationErrorMessage ?: "Pengguna menolak izin akses lokasi. Posisi pengguna tidak dapat ditampilkan.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
              onClick = onRequestPermission,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("MINTA IZIN LOKASI LAGI")
            }
          }
        }

        locationStatus == LocationStatus.LOCATION_PROVIDER_DISABLED -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "LOCATION PROVIDER DISABLED",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = StatusBlocked
              )
            )
            Text(
              text = "Sensor GPS nonaktif pada perangkat. Aktifkan GPS untuk menampilkan posisi saya di peta.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        else -> {
          // LOCATION NOT AVAILABLE (Section 8 & 11)
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("location_not_available_box")
          ) {
            Text(
              text = "LOCATION NOT AVAILABLE",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = StatusNotStarted
              )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "LATITUDE", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                Text(
                  text = "BELUM TERSEDIA",
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.testTag("user_latitude_text")
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "LONGITUDE", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                Text(
                  text = "BELUM TERSEDIA",
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.testTag("user_longitude_text")
                )
              }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "ACCURACY", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                Text(
                  text = "BELUM TERSEDIA",
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.testTag("user_accuracy_text")
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "LOCATION TIME", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                Text(
                  text = "BELUM TERSEDIA",
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.testTag("user_time_text")
                )
              }
            }
            if (locationStatus == LocationStatus.LOCATION_PERMISSION_REQUIRED) {
              Spacer(modifier = Modifier.height(6.dp))
              Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("MINTA IZIN LOKASI")
              }
            }
          }
        }
      }
    }
  }
}
