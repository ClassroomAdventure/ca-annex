package com.classroomadventure.annex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.classroomadventure.annex.R

enum class StatusKind { CONNECTING, WAITING_APPROVAL, DENIED, RECONNECTING }

data class Status(val kind: StatusKind, val title: String, val body: String? = null)

/** 映像が出るまで / 切れたときに全画面で出す案内 */
@Composable
fun StatusOverlay(status: Status, onStop: () -> Unit) {
    val b = LocalBrand.current
    Box(Modifier.fillMaxSize().background(b.paper), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (status.kind) {
                StatusKind.DENIED -> Icon(Icons.Outlined.Block, contentDescription = null, tint = b.muted, modifier = Modifier.size(48.dp))
                StatusKind.WAITING_APPROVAL -> Icon(Icons.Outlined.Laptop, contentDescription = null, tint = b.blue, modifier = Modifier.size(48.dp))
                else -> CircularProgressIndicator(color = b.blue, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(status.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (status.body != null) {
                Spacer(Modifier.height(8.dp))
                Text(status.body, style = MaterialTheme.typography.bodyLarge, color = b.muted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            OutlinedButton(onClick = onStop) { Text(stringResource(R.string.status_stop)) }
        }
    }
}
