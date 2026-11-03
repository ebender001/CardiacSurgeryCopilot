package dev.benderapps.heartteamprep.ui.components

import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import kotlinx.coroutines.delay

/** The card treatment used across the app: a soft surface with a hairline border, mirroring iOS `polishedCard()`. */
@Composable
fun Modifier.polishedCard(cornerRadius: Int = 18, padding: Int = 16): Modifier {
    val shape = RoundedCornerShape(cornerRadius.dp)
    return this
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f), shape)
        .padding(padding.dp)
}

/**
 * Standard screen frame: warm background, a back arrow, and a title in the
 * top bar. Replaces the iOS `NavigationStack` title/back chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartTeamScreen(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val colors = heartTeamPrepColors()
    Scaffold(
        containerColor = colors.warmBackground,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.warmBackground)
            )
        },
        content = content
    )
}

/** A small pill label (case status, question category). */
@Composable
fun StatusBadge(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = content,
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** A neutral gray [StatusBadge] -- routine workflow states, not alerts. */
@Composable
fun NeutralBadge(text: String, modifier: Modifier = Modifier) {
    StatusBadge(
        text = text,
        container = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
        content = heartTeamPrepColors().slateText,
        modifier = modifier
    )
}

@Composable
fun ListErrorState(title: String, message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.polishedCard(padding = 18), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(message, style = MaterialTheme.typography.bodySmall, color = heartTeamPrepColors().slateText)
        TextButton(onClick = onRetry) {
            Text("Try Again", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = heartTeamPrepColors().primary)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: (@Composable () -> Unit)? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = heartTeamPrepColors().primaryText)
    }
}

/**
 * Full-screen "working on it" overlay with a pulsing heart-monitor icon and
 * rotating status lines, shown over screens that wait on a slow AI call.
 * Swallows touches so the screen underneath can't be interacted with.
 */
@Composable
fun AnalyzingOverlay(title: String, messages: List<String>) {
    val colors = heartTeamPrepColors()
    var messageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(messages) {
        if (messages.size > 1) {
            while (true) {
                delay(1800)
                messageIndex = (messageIndex + 1) % messages.size
            }
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale"
    )
    val alpha by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.warmBackground.copy(alpha = 0.94f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(
                imageVector = Icons.Filled.MonitorHeart,
                contentDescription = null,
                tint = colors.primaryText,
                modifier = Modifier
                    .size(44.dp)
                    .scale(scale)
                    .alpha(alpha)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (messages.isNotEmpty()) {
                    Text(
                        text = messages[messageIndex % messages.size],
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.slateText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CenteredLoading(message: String? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = heartTeamPrepColors().primaryText)
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = heartTeamPrepColors().slateText)
            }
        }
    }
}
