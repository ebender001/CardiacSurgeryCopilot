package dev.benderapps.heartteamprep.ui.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.benderapps.heartteamprep.model.ConferenceReferenceTopic
import dev.benderapps.heartteamprep.model.ConferenceReport
import dev.benderapps.heartteamprep.model.PubMedArticle
import dev.benderapps.heartteamprep.ui.components.AnalyzingOverlay
import dev.benderapps.heartteamprep.ui.components.HeartTeamScreen
import dev.benderapps.heartteamprep.ui.components.ListErrorState
import dev.benderapps.heartteamprep.ui.components.polishedCard
import dev.benderapps.heartteamprep.ui.evidence.ArticleRow
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.ConferenceReferenceLookupViewModel
import dev.benderapps.heartteamprep.viewmodel.ConferenceReportViewModel

private val reportLoadingMessages = listOf(
    "Reviewing the case as a whole…",
    "Weighing operative strategy and alternatives…",
    "Checking recent evidence and guidelines…"
)

/** The nine-section preoperative case conference report. */
@Composable
fun ConferenceReportScreen(
    viewModel: ConferenceReportViewModel,
    onBack: () -> Unit,
    onOpenTopic: (ConferenceReferenceTopic) -> Unit
) {
    HeartTeamScreen(title = "Case Report", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val report = viewModel.report
            val errorMessage = viewModel.errorMessage
            if (report != null) {
                ReportContent(report, onOpenTopic)
            } else if (errorMessage != null) {
                ListErrorState(
                    title = "Couldn't load the report",
                    message = errorMessage,
                    onRetry = { viewModel.load() },
                    modifier = Modifier.padding(20.dp)
                )
            }

            if (viewModel.isLoading) {
                AnalyzingOverlay(title = "Preparing the report", messages = reportLoadingMessages)
            }
        }
    }
}

@Composable
private fun ReportContent(report: ConferenceReport, onOpenTopic: (ConferenceReferenceTopic) -> Unit) {
    val colors = heartTeamPrepColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ReportSection("Diagnosis", report.diagnosis)
        ReportSection("Indication", report.indication)
        ReportSection("Missing Information", report.missingInformation)
        ReportSection("Operative Strategy", report.operativeStrategy)
        ReportSection("Alternatives", report.alternatives)
        ReportSection("Controversies", report.controversies)
        ReportSection("Technical Considerations", report.technicalConsiderations)
        ReportSection("Postoperative Concerns", report.postoperativeConcerns)

        Column(modifier = Modifier.polishedCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Evidence & Guidelines")
            if (report.evidenceGuidelines.isEmpty()) {
                Text(
                    "No evidence or guideline topics identified for this case.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateText
                )
            } else {
                report.evidenceGuidelines.forEach { topic ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenTopic(topic) }.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(topic.topic, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
                        if (topic.searchIntent.isNotEmpty()) {
                            Text(
                                topic.searchIntent,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.slateText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = heartTeamPrepColors().primaryText)
}

@Composable
private fun ReportSection(title: String, text: String) {
    Column(modifier = Modifier.polishedCard(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A live PubMed search for one evidence/guideline topic from the report. */
@Composable
fun ConferenceReferenceLookupScreen(
    viewModel: ConferenceReferenceLookupViewModel,
    onBack: () -> Unit,
    onOpenArticle: (PubMedArticle) -> Unit
) {
    val colors = heartTeamPrepColors()
    HeartTeamScreen(title = "PubMed Search", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val results = viewModel.results
            val errorMessage = viewModel.errorMessage
            if (results != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(viewModel.topic, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    if (viewModel.searchIntent.isNotEmpty()) {
                        Text(viewModel.searchIntent, style = MaterialTheme.typography.bodySmall, color = colors.slateText)
                    }
                    if (results.isEmpty()) {
                        Text("No matching articles found.", style = MaterialTheme.typography.bodySmall, color = colors.slateText)
                    } else {
                        results.forEach { article -> ArticleRow(article, onClick = { onOpenArticle(article) }) }
                    }
                }
            } else if (errorMessage != null) {
                ListErrorState(
                    title = "Couldn't search PubMed",
                    message = errorMessage,
                    onRetry = { viewModel.search() },
                    modifier = Modifier.padding(20.dp)
                )
            }

            if (viewModel.isLoading) {
                AnalyzingOverlay(
                    title = "Searching the literature",
                    messages = listOf("Searching PubMed…", "Filtering to relevant articles…")
                )
            }
        }
    }
}
