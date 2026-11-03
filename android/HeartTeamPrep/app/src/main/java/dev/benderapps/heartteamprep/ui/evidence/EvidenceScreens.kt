package dev.benderapps.heartteamprep.ui.evidence

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.benderapps.heartteamprep.model.PubMedArticle
import dev.benderapps.heartteamprep.ui.components.AnalyzingOverlay
import dev.benderapps.heartteamprep.ui.components.HeartTeamScreen
import dev.benderapps.heartteamprep.ui.components.ListErrorState
import dev.benderapps.heartteamprep.ui.components.SecondaryButton
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.HeartTeamEvidenceViewModel

private val evidenceLoadingMessages = listOf(
    "Searching PubMed for supporting evidence…",
    "Searching PubMed for opposing evidence…",
    "Searching recent literature…"
)

/** One role's pro/con PubMed evidence for its stated recommendation. */
@Composable
fun HeartTeamEvidenceScreen(
    viewModel: HeartTeamEvidenceViewModel,
    onBack: () -> Unit,
    onOpenArticle: (PubMedArticle) -> Unit
) {
    val colors = heartTeamPrepColors()
    val roleName = viewModel.role.displayName
    HeartTeamScreen(title = "$roleName Evidence", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val evidence = viewModel.evidence
            val errorMessage = viewModel.errorMessage
            if (evidence != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Literature for and against the ${roleName.lowercase()}'s recommendation, favoring the last 10 years.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.slateText
                    )
                    EvidenceSection("Supports the $roleName", evidence.pro.results, onOpenArticle)
                    EvidenceSection("Favors an Alternative to the $roleName", evidence.con.results, onOpenArticle)
                }
            } else if (errorMessage != null) {
                ListErrorState(
                    title = "Couldn't load evidence",
                    message = errorMessage,
                    onRetry = { viewModel.load() },
                    modifier = Modifier.padding(20.dp)
                )
            }

            if (viewModel.isLoading) {
                AnalyzingOverlay(title = "Searching the literature", messages = evidenceLoadingMessages)
            }
        }
    }
}

@Composable
private fun EvidenceSection(title: String, articles: List<PubMedArticle>, onOpenArticle: (PubMedArticle) -> Unit) {
    val colors = heartTeamPrepColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.primaryText)
        if (articles.isEmpty()) {
            Text(
                "No matching articles found.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.slateText
            )
        } else {
            articles.forEachIndexed { index, article ->
                ArticleRow(article, onClick = { onOpenArticle(article) })
                if (index < articles.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                }
            }
        }
    }
}

/** A tappable PubMed article row (title + journal/year), shared by the evidence and reference-lookup screens. */
@Composable
fun ArticleRow(article: PubMedArticle, onClick: () -> Unit) {
    val colors = heartTeamPrepColors()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                article.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (article.journalAndYear.isNotEmpty()) {
                Text(article.journalAndYear, style = MaterialTheme.typography.bodySmall, color = colors.slateText)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.slateText.copy(alpha = 0.6f))
    }
}

/** One PubMed article's metadata and abstract, with a link out to PubMed. */
@Composable
fun PubMedArticleDetailScreen(article: PubMedArticle, onBack: () -> Unit) {
    val colors = heartTeamPrepColors()
    val uriHandler = LocalUriHandler.current
    HeartTeamScreen(title = "Article", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(article.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (article.authors.isNotEmpty()) {
                    Text(article.authors.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = colors.slateText)
                }
                if (article.journalAndYear.isNotEmpty()) {
                    Text(article.journalAndYear, style = MaterialTheme.typography.bodySmall, color = colors.slateText)
                }
                Text("PMID: ${article.pmid}", style = MaterialTheme.typography.labelMedium, color = colors.slateText)
            }

            if (article.abstractSections.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    article.abstractSections.forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            section.label?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                            Text(section.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            if (article.url.isNotEmpty()) {
                SecondaryButton(
                    text = "View on PubMed",
                    onClick = { uriHandler.openUri(article.url) },
                    icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = colors.primaryText) }
                )
            }
        }
    }
}
