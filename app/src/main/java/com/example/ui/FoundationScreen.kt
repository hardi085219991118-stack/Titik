package com.example.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.contract.FeatureContract
import com.example.core.contract.FeatureStatus
import com.example.core.logging.AppLogger
import com.example.core.registry.FeatureRegistry
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusImplemented
import com.example.ui.theme.StatusImplementing
import com.example.ui.theme.StatusNotStarted
import com.example.ui.theme.StatusVerified

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundationScreen(modifier: Modifier = Modifier) {
  val auditEvents by AppLogger.auditEvents.collectAsState()
  val errorLogs by AppLogger.errorLog.collectAsState()

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
              text = "STAGE 001 — MASTER DEVELOPMENT CONTRACT",
              style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
              )
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
        ZeroDummyMandateCard()
      }

      item {
        EvidenceStatusCard()
      }

      item {
        Text(
          text = "FEATURE REGISTRY (FIRE-001 - FIRE-008)",
          style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("registry_header")
        )
      }

      items(FeatureRegistry.features, key = { it.id }) { feature ->
        FeatureContractCard(feature = feature)
      }

      item {
        AuditTrailCard(events = auditEvents, errorCount = errorLogs.size)
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
fun ZeroDummyMandateCard() {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("zero_dummy_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = "Peringatan Kontrak",
          tint = MaterialTheme.colorScheme.primary
        )
        Text(
          text = "ATURAN ABSOLUT: ZERO-DUMMY",
          style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold
          ),
          color = MaterialTheme.colorScheme.primary
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "CODE ≠ VERIFIED  •  UI ≠ FEATURE  •  DATA DUMMY ≠ DATA NYATA",
        style = MaterialTheme.typography.labelMedium.copy(
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Dilarang keras menggunakan data dummy, marker api palsu, koordinat hardcoded, atau timestamp manipulatif. Fitur dinyatakan selesai hanya dengan bukti nyata.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun EvidenceStatusCard() {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("evidence_status_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "STATUS BUKTI REAL-TIME (STAGE 001)",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(12.dp))

      EvidenceRow(
        label = "GPS Device Status",
        value = "LOCATION_NOT_AVAILABLE (Rule 8: No fake coordinates)"
      )
      EvidenceRow(
        label = "Satelit Hotspot Source",
        value = "DATA SOURCE NOT VERIFIED (Rule 6: No fake fires)"
      )
      EvidenceRow(
        label = "Verified Fire Markers",
        value = "Tidak ada data titik api yang terverifikasi."
      )
      EvidenceRow(
        label = "Prompt Scope Guard",
        value = "FIRE-003 s/d FIRE-008 terkunci (NOT_STARTED)"
      )
    }
  }
}

@Composable
fun EvidenceRow(label: String, value: String) {
  Column(modifier = Modifier.padding(vertical = 4.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
fun FeatureContractCard(feature: FeatureContract) {
  val (statusColor, statusIcon) = when (feature.status) {
    FeatureStatus.NOT_STARTED -> Pair(StatusNotStarted, Icons.Default.Lock)
    FeatureStatus.IMPLEMENTING -> Pair(StatusImplementing, Icons.Default.Info)
    FeatureStatus.IMPLEMENTED -> Pair(StatusImplemented, Icons.Default.CheckCircle)
    FeatureStatus.BUILD_VERIFIED -> Pair(StatusVerified, Icons.Default.CheckCircle)
    FeatureStatus.RUNTIME_VERIFIED -> Pair(StatusVerified, Icons.Default.CheckCircle)
    FeatureStatus.DATA_VERIFIED -> Pair(StatusVerified, Icons.Default.CheckCircle)
    FeatureStatus.PRODUCTION_READY -> Pair(StatusVerified, Icons.Default.CheckCircle)
    FeatureStatus.BLOCKED -> Pair(StatusBlocked, Icons.Default.Warning)
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("feature_card_${feature.id}"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = feature.id,
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = feature.name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        StatusBadge(status = feature.status.name, color = statusColor)
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = feature.purpose,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Gate: ${feature.verificationLevel}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "Source: ${feature.dataSource}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (feature.knownLimitations.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Catatan: ${feature.knownLimitations}",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.error
        )
      }
    }
  }
}

@Composable
fun StatusBadge(status: String, color: Color) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(color.copy(alpha = 0.15f))
      .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = status,
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
      ),
      color = color
    )
  }
}

@Composable
fun AuditTrailCard(events: List<String>, errorCount: Int) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("audit_trail_card"),
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
        Text(
          text = "AUDIT TRAIL & ERROR MONITOR",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "Active Errors: $errorCount",
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
          ),
          color = if (errorCount == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      events.take(3).forEach { event ->
        Text(
          text = "• $event",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 2.dp)
        )
      }
    }
  }
}
