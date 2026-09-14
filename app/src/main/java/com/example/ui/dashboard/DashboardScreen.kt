package com.example.ui.dashboard

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.location.LocationVerificationLevel
import com.example.core.location.RuntimeEnvironment
import com.example.core.logging.AppLogger
import com.example.ui.FoundationScreen
import com.example.ui.map.MapScreen
import com.example.ui.map.MapStatus
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusNotStarted
import com.example.ui.theme.StatusVerified
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  modifier: Modifier = Modifier,
  state: DashboardState = remember { DashboardState() },
  onRequestPermission: () -> Unit = {},
  onRefreshLocation: () -> Unit = {}
) {
  val context = LocalContext.current
  var showContractScreen by remember { mutableStateOf(false) }
  var showMapScreen by remember { mutableStateOf(false) }
  val auditEvents by AppLogger.auditEvents.collectAsState()
  val errorLogs by AppLogger.errorLog.collectAsState()

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      onRefreshLocation()
    } else {
      onRequestPermission()
    }
  }

  if (showMapScreen) {
    MapScreen(
      deviceLocation = state.deviceLocation,
      locationStatus = state.locationStatus,
      locationErrorMessage = state.locationErrorMessage,
      onBackToDashboard = { showMapScreen = false },
      onRefreshLocation = onRefreshLocation,
      onRequestPermission = {
        permissionLauncher.launch(
          arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
          )
        )
      }
    )
    return
  }

  if (showContractScreen) {
    Column(modifier = Modifier.fillMaxSize()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        OutlinedButton(
          onClick = { showContractScreen = false },
          modifier = Modifier.testTag("back_to_dashboard_button")
        ) {
          Text("← Kembali ke Dashboard")
        }
      }
      FoundationScreen(modifier = Modifier.weight(1f))
    }
    return
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "HARDI MANTANGAI FIRE NOW",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
              ),
              modifier = Modifier.testTag("app_title")
            )
            Text(
              text = "SYSTEM STATUS — ZERO DUMMY MODE",
              style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
              ),
              modifier = Modifier.testTag("system_status_subtitle")
            )
          }
        },
        actions = {
          TextButton(
            onClick = { showMapScreen = true },
            modifier = Modifier.testTag("open_map_button")
          ) {
            Text(
              text = "Peta",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary
            )
          }
          TextButton(
            onClick = { showContractScreen = true },
            modifier = Modifier.testTag("view_contract_button")
          ) {
            Text(
              text = "Kontrak & Registri",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          titleContentColor = MaterialTheme.colorScheme.onSurface
        )
      )
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Spacer(modifier = Modifier.height(4.dp))
        SystemStatusCard(state = state)
      }

      item {
        RealLocationCard(
          state = state,
          onRequestPermission = {
            permissionLauncher.launch(
              arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
              )
            )
          },
          onRefreshLocation = onRefreshLocation,
          onOpenSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
              data = Uri.fromParts("package", context.packageName, null)
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
          },
          onOpenLocationSettings = {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
          }
        )
      }

      item {
        MapFoundationCard(
          state = state,
          onOpenMap = { showMapScreen = true }
        )
      }

      item {
        FireDetectionCard(state = state)
      }

      item {
        SatelliteDataCard(state = state)
      }

      item {
        LastUpdateCard(state = state)
      }

      item {
        RefreshSection(state = state)
      }

      item {
        AuditSummaryCard(events = auditEvents, errorCount = errorLogs.size)
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
fun SystemStatusCard(state: DashboardState) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("system_status_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = "Status Sistem",
            tint = StatusVerified,
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = "STATUS SISTEM",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        StateBadge(text = state.systemStatus, color = StatusVerified)
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = state.systemDetail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/**
 * Real Location Card (FIRE-004)
 * Menampilkan status lokasi nyata perangkat tanpa koordinat dummy / fake.
 */
@Composable
fun RealLocationCard(
  state: DashboardState,
  onRequestPermission: () -> Unit,
  onRefreshLocation: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenLocationSettings: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("location_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      when (state.locationStatus) {
        LocationStatus.LOCATION_AVAILABLE -> StatusVerified.copy(alpha = 0.6f)
        LocationStatus.LOCATION_ERROR, LocationStatus.LOCATION_PERMISSION_DENIED -> StatusBlocked.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
      }
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header Card
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val cardTitle = when {
          state.deviceLocation?.verificationLevel == LocationVerificationLevel.REAL_DEVICE_VERIFIED -> "REAL DEVICE: VERIFIED"
          state.deviceLocation?.isMock == true -> "LOCATION: MOCK LOCATION"
          state.deviceLocation?.isFromCache == true -> "LOCATION: CACHED LOCATION"
          state.deviceLocation?.runtimeEnvironment == RuntimeEnvironment.EMULATOR -> "RUNTIME: EMULATOR (VIRTUAL)"
          state.deviceLocation?.runtimeEnvironment == RuntimeEnvironment.VIRTUAL_DEVICE -> "RUNTIME: VIRTUAL DEVICE"
          state.deviceLocation?.runtimeEnvironment == RuntimeEnvironment.CLOUD_CONTAINER -> "RUNTIME: CLOUD CONTAINER"
          state.locationStatus == LocationStatus.LOCATION_AVAILABLE -> "REAL DEVICE: NOT VERIFIED"
          else -> "LOKASI PERANGKAT"
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val icon = when (state.locationStatus) {
            LocationStatus.LOCATION_AVAILABLE -> Icons.Default.LocationOn
            LocationStatus.LOCATION_LOADING -> Icons.Default.GpsFixed
            LocationStatus.LOCATION_PROVIDER_DISABLED -> Icons.Default.GpsOff
            LocationStatus.LOCATION_PERMISSION_DENIED -> Icons.Default.LocationOff
            else -> Icons.Default.GpsNotFixed
          }
          val iconTint = when (state.locationStatus) {
            LocationStatus.LOCATION_AVAILABLE -> StatusVerified
            LocationStatus.LOCATION_LOADING -> MaterialTheme.colorScheme.primary
            LocationStatus.LOCATION_ERROR, LocationStatus.LOCATION_PERMISSION_DENIED -> StatusBlocked
            else -> StatusNotStarted
          }

          Icon(
            imageVector = icon,
            contentDescription = cardTitle,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = cardTitle,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        val badgeText = state.locationStatus.name
        val badgeColor = when (state.locationStatus) {
          LocationStatus.LOCATION_AVAILABLE -> StatusVerified
          LocationStatus.LOCATION_LOADING -> MaterialTheme.colorScheme.primary
          LocationStatus.LOCATION_ERROR, LocationStatus.LOCATION_PERMISSION_DENIED -> StatusBlocked
          else -> StatusNotStarted
        }
        StateBadge(text = badgeText, color = badgeColor)
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Content berdasarkan LocationStatus
      when (state.locationStatus) {
        LocationStatus.LOCATION_AVAILABLE -> {
          val loc = state.deviceLocation
          if (loc != null) {
            LocationDataDisplay(location = loc, onRefresh = onRefreshLocation)
          } else {
            Text(
              text = "LOCATION NOT AVAILABLE",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        LocationStatus.LOCATION_LOADING -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Mengakses sinyal GPS nyata perangkat...",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        LocationStatus.LOCATION_PERMISSION_REQUIRED -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "LOCATION NOT AVAILABLE",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              ),
              color = StatusNotStarted,
              modifier = Modifier.testTag("location_status_text")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Aplikasi memerlukan izin lokasi Android nyata untuk menentukan posisi tanpa data dummy.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = onRequestPermission,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("request_permission_button"),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
              )
            ) {
              Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("MINTA IZIN LOKASI")
            }
          }
        }

        LocationStatus.LOCATION_PERMISSION_DENIED -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = StatusBlocked,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "LOCATION PERMISSION DENIED",
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = StatusBlocked,
                  fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.testTag("location_denied_text")
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = state.locationErrorMessage
                ?: "Pengguna menolak izin akses lokasi. Koordinat dummy tidak akan digunakan.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = onRequestPermission,
                modifier = Modifier
                  .weight(1f)
                  .testTag("retry_permission_button")
              ) {
                Text("COBA LAGI")
              }
              Button(
                onClick = onOpenSettings,
                modifier = Modifier
                  .weight(1f)
                  .testTag("open_settings_button")
              ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("PENGATURAN")
              }
            }
          }
        }

        LocationStatus.LOCATION_PROVIDER_DISABLED -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.GpsOff,
                contentDescription = null,
                tint = StatusBlocked,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "LOCATION PROVIDER DISABLED",
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = StatusBlocked,
                  fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.testTag("location_provider_disabled_text")
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Sensor GPS/Layanan Lokasi perangkat sedang nonaktif. Mohon aktifkan GPS pada perangkat untuk memperoleh koordinat nyata.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = onOpenLocationSettings,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("enable_gps_button")
            ) {
              Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("AKTIFKAN GPS PERANGKAT")
            }
          }
        }

        LocationStatus.LOCATION_ERROR -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "LOCATION ERROR",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = StatusBlocked,
                fontFamily = FontFamily.Monospace
              ),
              modifier = Modifier.testTag("location_error_text")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = state.locationErrorMessage ?: "Terjadi kesalahan sistem saat meminta lokasi perangkat.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
              onClick = onRefreshLocation,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("retry_location_button")
            ) {
              Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("COBA LAGI MENGAMBIL LOKASI")
            }
          }
        }
      }
    }
  }
}

@Composable
fun LocationDataDisplay(
  location: DeviceLocation,
  onRefresh: () -> Unit
) {
  val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()) }
  val formattedFixTime = remember(location.timeMillis) {
    if (location.timeMillis > 0) timeFormat.format(Date(location.timeMillis)) else "Waktu tidak valid"
  }
  val isStale = remember(location.timeMillis) { location.isStale() }
  val locationAgeDisplay = remember(location.timeMillis) { location.getLocationAgeDisplay() }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // 1. Status Banner
    when {
      location.isMock -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "CRITICAL: MOCK / VIRTUAL LOCATION TERDETEKSI (DATA TIDAK TERVERIFIKASI)",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onErrorContainer
          )
        }
      }
      isStale -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "STALE LOCATION (> 15 menit): Koordinat lama tersimpan, bukan Current GPS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onErrorContainer
          )
        }
      }
      location.isFromCache -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "CACHED LOCATION: Lokasi terakhir tersimpan, bukan Real-time GPS Now",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onTertiaryContainer
          )
        }
      }
      else -> {
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
    }

    // 2. Real Device & Runtime Environment Disclaimer (Section 10)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
      Column {
        Text(
          text = if (location.verificationLevel == LocationVerificationLevel.REAL_DEVICE_VERIFIED) {
            "REAL DEVICE: VERIFIED (Perangkat Fisik Nyata Terkonfirmasi Lapangan)"
          } else {
            "REAL DEVICE: NOT VERIFIED (Verifikasi fisik manual lapangan belum dilakukan)"
          },
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          ),
          color = if (location.verificationLevel == LocationVerificationLevel.REAL_DEVICE_VERIFIED) StatusVerified else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "RUNTIME: ${location.runtimeEnvironment.displayName} (${location.runtimeEnvironment.description})",
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // 3. LATITUDE & LONGITUDE
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
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          ),
          modifier = Modifier.testTag("location_latitude_value")
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
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          ),
          modifier = Modifier.testTag("location_longitude_value")
        )
      }
    }

    HorizontalDivider(
      modifier = Modifier.padding(vertical = 4.dp),
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )

    // 4. ACCURACY & LOCATION AGE (Section 13 & 15)
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
            "ACCURACY NOT AVAILABLE"
          },
          style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
          ),
          modifier = Modifier.testTag("location_accuracy_value")
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "LOCATION AGE",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        )
        Text(
          text = locationAgeDisplay,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
          ),
          modifier = Modifier.testTag("location_age_value")
        )
      }
    }

    // 5. LOCATION SOURCE & LOCATION FIX TIME (Section 13)
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
          style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
          ),
          modifier = Modifier.testTag("location_provider_value")
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
          text = formattedFixTime,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace
          ),
          modifier = Modifier.testTag("location_time_value")
        )
      }
    }

    // 6. RUNTIME ENVIRONMENT & VERIFICATION STATUS (Section 7, 8, 13)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "RUNTIME ENVIRONMENT",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        )
        Text(
          text = location.runtimeEnvironment.name,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
          ),
          modifier = Modifier.testTag("location_runtime_env_value")
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "VERIFICATION STATUS",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        )
        Text(
          text = location.verificationLevel.name,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
          ),
          color = when (location.verificationLevel) {
            LocationVerificationLevel.REAL_DEVICE_VERIFIED -> StatusVerified
            LocationVerificationLevel.MOCK, LocationVerificationLevel.UNVERIFIED -> StatusBlocked
            else -> MaterialTheme.colorScheme.onSurface
          },
          modifier = Modifier.testTag("location_verification_value")
        )
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Section 14: Tombol PERBARUI LOKASI
    OutlinedButton(
      onClick = onRefresh,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("refresh_location_button")
    ) {
      Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("PERBARUI LOKASI")
    }
  }
}

@Composable
fun FireDetectionCard(state: DashboardState) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("fire_detection_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Whatshot,
            contentDescription = "Titik Api",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
          )
          Text(
            text = "TITIK API",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        StateBadge(text = state.fireStatusText, color = StatusBlocked)
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = state.fireCountDisplay,
          style = MaterialTheme.typography.displayMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
          ),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag("fire_count_value")
        )
        Text(
          text = "DATA BELUM TERSEDIA",
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp)
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = state.fireNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun SatelliteDataCard(state: DashboardState) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("satellite_data_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = "Data Satelit",
            tint = StatusNotStarted,
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = "DATA SATELIT",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        StateBadge(text = state.satelliteDisplay, color = StatusNotStarted)
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = state.satelliteNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun LastUpdateCard(state: DashboardState) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("last_update_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = "Data Terakhir",
            tint = StatusNotStarted,
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = "DATA TERAKHIR",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        StateBadge(text = state.lastUpdateDisplay, color = StatusNotStarted)
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = state.lastUpdateNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun RefreshSection(state: DashboardState) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      OutlinedButton(
        onClick = {
          AppLogger.recordEvent("User clicked Refresh: Data source belum tersedia (FIRE-006 NOT_STARTED)")
        },
        enabled = state.isRefreshSatelliteEnabled,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("refresh_button")
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = "Perbarui Data",
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("PERBARUI DATA SATELIT")
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = state.refreshSatelliteNote,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun AuditSummaryCard(events: List<String>, errorCount: Int) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("audit_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "AUDIT LOGGER STATUS",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = if (errorCount == 0) "0 Active Errors" else "$errorCount Errors",
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
          ),
          color = if (errorCount == 0) StatusVerified else StatusBlocked
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      events.take(2).forEach { event ->
        Text(
          text = "• $event",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun StateBadge(text: String, color: Color, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(color.copy(alpha = 0.15f))
      .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
      ),
      color = color
    )
  }
}

/**
 * Map Foundation Card (FIRE-005)
 * Menampilkan integrasi peta geografis dengan posisi pengguna tanpa marker api palsu.
 */
@Composable
fun MapFoundationCard(
  state: DashboardState,
  onOpenMap: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("map_status_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      when (state.mapStatus) {
        MapStatus.MAP_READY -> StatusVerified.copy(alpha = 0.5f)
        MapStatus.MAP_ERROR -> StatusBlocked.copy(alpha = 0.5f)
        MapStatus.MAP_LOADING -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
      }
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Map,
            contentDescription = "Peta Geografis",
            tint = when (state.mapStatus) {
              MapStatus.MAP_READY -> StatusVerified
              MapStatus.MAP_ERROR -> StatusBlocked
              MapStatus.MAP_LOADING -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = "PETA GEOGRAFIS (FIRE-005)",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        StateBadge(
          text = state.mapStatus.name,
          color = when (state.mapStatus) {
            MapStatus.MAP_READY -> StatusVerified
            MapStatus.MAP_ERROR -> StatusBlocked
            MapStatus.MAP_LOADING -> MaterialTheme.colorScheme.primary
          },
          modifier = Modifier.testTag("map_card_status_badge")
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Status Peta & Lokasi
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(
            text = "MAP STATUS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          )
          Text(
            text = state.mapStatus.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.testTag("map_status_text")
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "LOCATION STATUS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          )
          Text(
            text = state.locationStatus.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.testTag("map_location_status_text")
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Detail Koordinat jika lokasi tersedia
      if (state.locationStatus == LocationStatus.LOCATION_AVAILABLE && state.deviceLocation != null) {
        val loc = state.deviceLocation
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "LATITUDE", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(
              text = String.format(Locale.US, "%.6f°", loc.latitude),
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
              modifier = Modifier.testTag("map_latitude_text")
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "LONGITUDE", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(
              text = String.format(Locale.US, "%.6f°", loc.longitude),
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
              modifier = Modifier.testTag("map_longitude_text")
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "ACCURACY", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(
              text = if (loc.accuracyMeters != null) String.format(Locale.US, "±%.1fm", loc.accuracyMeters) else "N/A",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
              modifier = Modifier.testTag("map_accuracy_text")
            )
          }
        }
      } else {
        Text(
          text = "LOCATION NOT AVAILABLE — Posisi pengguna belum terhubung ke kamera peta.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("map_location_not_available_text")
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "Provider: ${state.mapProviderName} | Credential: ${state.mapCredentialStatus}",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "ZERO FIRE MARKERS — FIRE-006 s/d FIRE-008 NOT_STARTED",
        style = MaterialTheme.typography.labelSmall.copy(
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold
        ),
        color = StatusNotStarted
      )

      Spacer(modifier = Modifier.height(12.dp))

      Button(
        onClick = onOpenMap,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("open_map_screen_button"),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
      ) {
        Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("BUKA PETA GEOGRAFIS")
      }
    }
  }
}
