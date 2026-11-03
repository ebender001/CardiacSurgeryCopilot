package dev.benderapps.heartteamprep.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.benderapps.heartteamprep.service.DictationController
import dev.benderapps.heartteamprep.service.DictationPhase
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors

/**
 * A text field with an attached mic-dictation control, shared by the
 * case-intake and interview-answer flows -- both compose a
 * [DictationController] the same way, so this only needs the current text,
 * a way to change it, and the controller. Mirrors the iOS `DictationEditorView`.
 */
@Composable
fun DictationEditor(
    text: String,
    onTextChange: (String) -> Unit,
    dictation: DictationController,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = heartTeamPrepColors()
    val context = LocalContext.current
    val phase = dictation.phase

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dictation.toggle(text) else dictation.reportPermissionDenied()
    }
    val onMicClick = {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (phase == DictationPhase.IDLE && !hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            dictation.toggle(text)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(placeholder) },
                enabled = enabled && phase == DictationPhase.IDLE,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxSize()
            )
            if (phase != DictationPhase.IDLE) StatusOverlay(phase)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val micDescription = when (phase) {
                DictationPhase.IDLE -> "Start dictation"
                DictationPhase.LISTENING -> "Stop dictation"
                DictationPhase.FINISHING_UP -> "Finishing dictation"
                DictationPhase.CORRECTING -> "Checking medical terms"
            }
            MicButton(phase, enabled = enabled, onClick = onMicClick, modifier = Modifier.semantics { contentDescription = micDescription })
            Text(
                text = when (phase) {
                    DictationPhase.IDLE -> "Tap to dictate"
                    DictationPhase.LISTENING -> "Listening — tap to stop"
                    DictationPhase.FINISHING_UP -> "Finishing up…"
                    DictationPhase.CORRECTING -> "Checking medical terms…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.slateText
            )
        }
    }
}

@Composable
private fun StatusOverlay(phase: DictationPhase) {
    val colors = heartTeamPrepColors()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            // Swallow touches so the field underneath can't be edited mid-dictation.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (phase == DictationPhase.LISTENING) {
                Waveform()
            } else {
                CircularProgressIndicator(color = colors.primaryText)
            }
            Text(
                text = when (phase) {
                    DictationPhase.LISTENING ->
                        "Listening… don't include patient names, staff or institution names, dates, or locations."
                    DictationPhase.FINISHING_UP -> "Finishing up…"
                    else -> "Checking medical terms…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.slateText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun MicButton(phase: DictationPhase, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = heartTeamPrepColors()
    val busy = phase == DictationPhase.FINISHING_UP || phase == DictationPhase.CORRECTING
    val listening = phase == DictationPhase.LISTENING

    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(if (listening) colors.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(
                width = if (listening) 3.dp else 1.5.dp,
                color = if (listening) colors.accent else colors.primaryText.copy(alpha = 0.25f),
                shape = CircleShape
            )
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            busy -> CircularProgressIndicator(modifier = Modifier.size(26.dp), color = colors.primaryText, strokeWidth = 2.dp)
            listening -> Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            else -> Icon(Icons.Outlined.Mic, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(28.dp))
        }
    }
}

/** Six bars rising and falling out of phase -- a "we're listening" cue, not a level meter. */
@Composable
private fun Waveform() {
    val colors = heartTeamPrepColors()
    val heights = listOf(14, 26, 34, 22, 30, 16)
    val transition = rememberInfiniteTransition(label = "waveform")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(34.dp)
    ) {
        heights.forEachIndexed { index, target ->
            val height by transition.animateFloat(
                initialValue = 8f,
                targetValue = target.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 550),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 100)
                ),
                label = "bar$index"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accent)
            )
        }
    }
}

/**
 * The two dialogs a [DictationController] can raise: a failure/fallback
 * notice, and "we removed possible patient information from what you said".
 */
@Composable
fun DictationDialogs(dictation: DictationController) {
    dictation.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { dictation.errorMessage = null },
            title = { Text("Dictation Unavailable") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { dictation.errorMessage = null }) { Text("OK") } }
        )
    }
    dictation.phiNoticeMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { dictation.phiNoticeMessage = null },
            title = { Text("Possible Patient Information Removed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { dictation.phiNoticeMessage = null }) { Text("OK") } }
        )
    }
}
