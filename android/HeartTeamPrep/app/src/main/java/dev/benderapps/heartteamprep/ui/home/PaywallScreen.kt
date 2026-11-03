package dev.benderapps.heartteamprep.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.benderapps.heartteamprep.LegalLinks
import dev.benderapps.heartteamprep.service.SubscriptionPeriod
import dev.benderapps.heartteamprep.service.SubscriptionPlan
import dev.benderapps.heartteamprep.ui.components.StatusBadge
import dev.benderapps.heartteamprep.ui.components.polishedCard
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepColors
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.PaywallState
import dev.benderapps.heartteamprep.viewmodel.PaywallViewModel
import java.util.UUID

private const val REDEEM_URL = "https://play.google.com/redeem"

/**
 * The subscription paywall, shown when a non-subscribed trainee taps
 * "Start a New Case". Mirrors the iOS `PaywallView`: header, benefits,
 * plans, restore, promo code, legal footer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(onUnlocked: () -> Unit, onDismiss: () -> Unit) {
    val colors = heartTeamPrepColors()
    // A fresh instance per presentation, so a previous unlock never replays.
    val viewModel: PaywallViewModel = viewModel(key = remember { "paywall-" + UUID.randomUUID() })
    val activity = LocalContext.current.findActivity()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(viewModel.didUnlockAccess) { if (viewModel.didUnlockAccess) onUnlocked() }

    ModalBottomSheet(
        onDismissRequest = { if (!viewModel.isBusy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.warmBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Header(colors)
            Benefits(colors)
            Plans(viewModel, colors, onPurchase = { plan -> activity?.let { viewModel.purchase(plan, it) } })
            Restore(viewModel, colors)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Have a promo code?", style = MaterialTheme.typography.bodySmall, color = colors.slateText)
                LinkText("Redeem Code", colors) { uriHandler.openUri(REDEEM_URL) }
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Subscriptions automatically renew unless canceled at least 24 hours before the end of the current period. Manage or cancel in Google Play.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.slateText,
                    textAlign = TextAlign.Center
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinkText("Terms of Use", colors) { uriHandler.openUri(LegalLinks.termsOfUse) }
                    Text("·", color = colors.slateText)
                    LinkText("Privacy Policy", colors) { uriHandler.openUri(LegalLinks.privacyPolicy) }
                }
            }
        }
    }
}

@Composable
private fun Header(colors: HeartTeamPrepColors) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(colors.primaryTint), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MonitorHeart, contentDescription = null, tint = colors.primaryText)
        }
        Text("Prepare every case with confidence", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(
            "Turn a real case into a structured report, complete with three heart-team perspectives and relevant literature.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.slateText
        )
    }
}

@Composable
private fun Benefits(colors: HeartTeamPrepColors) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BenefitRow(Icons.Filled.FactCheck, "Structured conference reports", "Diagnosis through postoperative concerns, organized the way you'd present it.", colors)
        BenefitRow(Icons.Filled.Groups, "Three heart-team perspectives", "See how a surgeon, a non-interventional cardiologist, and an interventional cardiologist would each approach your case.", colors)
        BenefitRow(Icons.Filled.Search, "Evidence and guidelines", "Automatically search PubMed for abstracts relevant to your case.", colors)
    }
}

@Composable
private fun BenefitRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, colors: HeartTeamPrepColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.slateText)
        }
    }
}

@Composable
private fun Plans(viewModel: PaywallViewModel, colors: HeartTeamPrepColors, onPurchase: (SubscriptionPlan) -> Unit) {
    when (val state = viewModel.state) {
        PaywallState.LoadingProducts -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primaryText)
        }

        PaywallState.UnableToLoadProducts -> Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Unable to Load Subscriptions", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                "Subscription options couldn't be loaded from Google Play. Please check your connection and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.slateText,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = { viewModel.load() }) { Text("Try Again") }
        }

        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            viewModel.sortedPlans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    isRecommended = plan.period == SubscriptionPeriod.ANNUAL,
                    isBusy = viewModel.isBusy,
                    isPurchasingThis = state == PaywallState.Purchasing(plan.id),
                    colors = colors,
                    onClick = { onPurchase(plan) }
                )
            }
            if (state is PaywallState.PurchaseFailed) {
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Your free trial converts to a paid subscription at the price shown above unless canceled before it ends.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.slateText
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    isRecommended: Boolean,
    isBusy: Boolean,
    isPurchasingThis: Boolean,
    colors: HeartTeamPrepColors,
    onClick: () -> Unit
) {
    val billed = if (plan.period == SubscriptionPeriod.ANNUAL) "${plan.displayPrice}/year" else "${plan.displayPrice}/month"
    // States both the trial length and the amount billed after it -- never just one.
    val priceLine = plan.freeTrialDuration?.let { "$it free, then $billed" } ?: billed
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isBusy && !isPurchasingThis) 0.5f else 1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(if (isRecommended) 1.5.dp else 1.dp, if (isRecommended) colors.accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), shape)
            .clickable(enabled = !isBusy, role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plan.displayName, style = MaterialTheme.typography.titleMedium)
                if (isRecommended) StatusBadge("Best Value", container = colors.accentTint, content = colors.accent)
            }
            Text(priceLine, style = MaterialTheme.typography.bodyMedium, color = colors.slateText)
        }
        if (isPurchasingThis) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.primaryText)
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.slateText)
        }
    }
}

@Composable
private fun Restore(viewModel: PaywallViewModel, colors: HeartTeamPrepColors) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (viewModel.state as? PaywallState.RestoreFailed)?.let {
            Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Already subscribed?", style = MaterialTheme.typography.bodySmall, color = colors.slateText)
            if (viewModel.state == PaywallState.Restoring) {
                CircularProgressIndicator(Modifier.padding(start = 8.dp).size(16.dp), strokeWidth = 2.dp, color = colors.primaryText)
            } else {
                LinkText("Restore Purchases", colors, enabled = !viewModel.isBusy) { viewModel.restore() }
            }
        }
    }
}

@Composable
private fun LinkText(text: String, colors: HeartTeamPrepColors, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
        color = colors.primaryText,
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp)
    )
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
