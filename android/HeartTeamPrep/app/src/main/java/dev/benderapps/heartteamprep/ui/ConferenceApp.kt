package dev.benderapps.heartteamprep.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.benderapps.heartteamprep.model.AuthenticatedUser
import dev.benderapps.heartteamprep.model.ConferenceCase
import dev.benderapps.heartteamprep.model.ConferenceCaseStatus
import dev.benderapps.heartteamprep.model.ConferenceConversationEntry
import dev.benderapps.heartteamprep.model.HeartTeamRole
import dev.benderapps.heartteamprep.model.PubMedArticle
import dev.benderapps.heartteamprep.ui.evidence.HeartTeamEvidenceScreen
import dev.benderapps.heartteamprep.ui.evidence.PubMedArticleDetailScreen
import dev.benderapps.heartteamprep.ui.home.ConferenceHomeScreen
import dev.benderapps.heartteamprep.ui.intake.ConferenceIntakeScreen
import dev.benderapps.heartteamprep.ui.intake.HeartTeamResponsesScreen
import dev.benderapps.heartteamprep.ui.interview.ConferenceFollowUpQuestionsScreen
import dev.benderapps.heartteamprep.ui.interview.ConferenceInterviewScreen
import dev.benderapps.heartteamprep.ui.report.ConferenceReferenceLookupScreen
import dev.benderapps.heartteamprep.ui.report.ConferenceReportScreen
import dev.benderapps.heartteamprep.viewmodel.AuthenticationViewModel
import dev.benderapps.heartteamprep.viewmodel.ConferenceHomeViewModel
import dev.benderapps.heartteamprep.viewmodel.ConferenceInterviewViewModel
import dev.benderapps.heartteamprep.viewmodel.ConferenceIntakeViewModel
import dev.benderapps.heartteamprep.viewmodel.ConferenceReferenceLookupViewModel
import dev.benderapps.heartteamprep.viewmodel.ConferenceReportViewModel
import dev.benderapps.heartteamprep.viewmodel.HeartTeamEvidenceViewModel
import dev.benderapps.heartteamprep.viewmodel.HeartTeamResponsesViewModel

/** Typed-ish navigation destinations for the signed-in app, mirroring the iOS client's `ConferenceRoute`. */
private object Routes {
    const val HOME = "home"
    const val NEW_CASE = "newCase"
    const val INTERVIEW = "interview/{caseId}"
    const val RESPONSES = "responses/{caseId}"
    const val EVIDENCE = "evidence/{caseId}/{role}"
    const val REPORT = "report/{caseId}"
    const val FOLLOW_UPS = "followUps/{caseId}"
    const val REFERENCE = "reference/{caseId}?topic={topic}&intent={intent}"
    const val ARTICLE = "article/{pmid}"

    fun interview(caseId: String) = "interview/$caseId"
    fun responses(caseId: String) = "responses/$caseId"
    fun evidence(caseId: String, role: HeartTeamRole) = "evidence/$caseId/${role.raw}"
    fun report(caseId: String) = "report/$caseId"
    fun followUps(caseId: String) = "followUps/$caseId"
    fun reference(caseId: String, topic: String, intent: String) =
        "reference/$caseId?topic=${Uri.encode(topic)}&intent=${Uri.encode(intent)}"
    fun article(pmid: String) = "article/$pmid"
}

/**
 * Hands data between destinations that the iOS client passes by value in
 * its route enum (a just-created case, a tapped article, the already-loaded
 * follow-up history). Navigation routes here are strings, so anything
 * bigger than an id goes through this instead. Scoped per signed-in user.
 */
class ConferenceSessionStore : ViewModel() {
    private val pendingCases = mutableMapOf<String, ConferenceCase>()
    private val articles = mutableMapOf<String, PubMedArticle>()
    private val followUps = mutableMapOf<String, List<ConferenceConversationEntry>>()

    fun putPendingCase(case: ConferenceCase) {
        pendingCases[case.id] = case
    }

    /**
     * The just-created case for an interview screen about to open, consumed
     * so a later visit to the same case (from Recent Cases) refetches
     * current state instead of resuming from a stale snapshot.
     */
    fun takePendingCase(caseId: String): ConferenceCase? = pendingCases.remove(caseId)

    fun putArticle(article: PubMedArticle) {
        articles[article.pmid] = article
    }

    fun article(pmid: String): PubMedArticle? = articles[pmid]

    fun putFollowUps(caseId: String, entries: List<ConferenceConversationEntry>) {
        followUps[caseId] = entries
    }

    fun followUps(caseId: String): List<ConferenceConversationEntry> = followUps[caseId].orEmpty()
}

/** The signed-in app: home, case intake, follow-up interview, heart-team responses, report, and evidence. */
@Composable
fun ConferenceApp(user: AuthenticatedUser, authViewModel: AuthenticationViewModel) {
    val navController = rememberNavController()
    val store: ConferenceSessionStore = viewModel(key = "session-${user.id}")

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            // Scoped to this back stack entry (not the activity), so a
            // different account signing in never sees a previous account's list.
            val viewModel: ConferenceHomeViewModel = viewModel()
            ConferenceHomeScreen(
                viewModel = viewModel,
                user = user,
                onNewCase = { navController.navigate(Routes.NEW_CASE) },
                onOpenCase = { record ->
                    navController.navigate(
                        when (record.status) {
                            ConferenceCaseStatus.COMPLETED -> Routes.report(record.id)
                            // No pending question by definition -- skip the interview screen.
                            ConferenceCaseStatus.READY_TO_FINALIZE -> Routes.responses(record.id)
                            ConferenceCaseStatus.COLLECTING_INFORMATION -> Routes.interview(record.id)
                        }
                    )
                },
                onSignOut = { authViewModel.signOut() },
                onDeleteAccount = { onError -> authViewModel.deleteAccount(onError) }
            )
        }

        composable(Routes.NEW_CASE) {
            val viewModel: ConferenceIntakeViewModel = viewModel()
            ConferenceIntakeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCaseCreated = { created ->
                    // Replace the intake screen so Back from here can't
                    // return to an already-submitted draft.
                    if (created.nextQuestion == null) {
                        navController.replaceWith(Routes.responses(created.id), Routes.NEW_CASE)
                    } else {
                        store.putPendingCase(created)
                        navController.replaceWith(Routes.interview(created.id), Routes.NEW_CASE)
                    }
                }
            )
        }

        composable(Routes.INTERVIEW, arguments = listOf(navArgument("caseId") { type = NavType.StringType })) { entry ->
            val caseId = entry.arguments?.getString("caseId").orEmpty()
            val viewModel: ConferenceInterviewViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ConferenceInterviewViewModel(caseId, store.takePendingCase(caseId)) }
                }
            )
            ConferenceInterviewScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onReady = { navController.replaceWith(Routes.responses(caseId), Routes.INTERVIEW) }
            )
        }

        composable(Routes.RESPONSES, arguments = listOf(navArgument("caseId") { type = NavType.StringType })) { entry ->
            val caseId = entry.arguments?.getString("caseId").orEmpty()
            val viewModel: HeartTeamResponsesViewModel = viewModel(
                factory = viewModelFactory { initializer { HeartTeamResponsesViewModel(caseId) } }
            )
            HeartTeamResponsesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenEvidence = { role -> navController.navigate(Routes.evidence(caseId, role)) },
                onOpenReport = { navController.navigate(Routes.report(caseId)) },
                onOpenFollowUps = { entries ->
                    store.putFollowUps(caseId, entries)
                    navController.navigate(Routes.followUps(caseId))
                }
            )
        }

        composable(Routes.FOLLOW_UPS, arguments = listOf(navArgument("caseId") { type = NavType.StringType })) { entry ->
            val caseId = entry.arguments?.getString("caseId").orEmpty()
            ConferenceFollowUpQuestionsScreen(
                entries = store.followUps(caseId),
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.EVIDENCE,
            arguments = listOf(
                navArgument("caseId") { type = NavType.StringType },
                navArgument("role") { type = NavType.StringType }
            )
        ) { entry ->
            val caseId = entry.arguments?.getString("caseId").orEmpty()
            val role = HeartTeamRole.fromRaw(entry.arguments?.getString("role").orEmpty())
            val viewModel: HeartTeamEvidenceViewModel = viewModel(
                factory = viewModelFactory { initializer { HeartTeamEvidenceViewModel(caseId, role) } }
            )
            HeartTeamEvidenceScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenArticle = { article ->
                    store.putArticle(article)
                    navController.navigate(Routes.article(article.pmid))
                }
            )
        }

        composable(Routes.REPORT, arguments = listOf(navArgument("caseId") { type = NavType.StringType })) { entry ->
            val caseId = entry.arguments?.getString("caseId").orEmpty()
            val viewModel: ConferenceReportViewModel = viewModel(
                factory = viewModelFactory { initializer { ConferenceReportViewModel(caseId) } }
            )
            ConferenceReportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenTopic = { topic -> navController.navigate(Routes.reference(caseId, topic.topic, topic.searchIntent)) }
            )
        }

        composable(
            Routes.REFERENCE,
            arguments = listOf(
                navArgument("caseId") { type = NavType.StringType },
                navArgument("topic") { type = NavType.StringType; defaultValue = "" },
                navArgument("intent") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val caseId = entry.arguments?.getString("caseId").orEmpty()
            val topic = entry.arguments?.getString("topic").orEmpty()
            val intent = entry.arguments?.getString("intent").orEmpty()
            val viewModel: ConferenceReferenceLookupViewModel = viewModel(
                factory = viewModelFactory { initializer { ConferenceReferenceLookupViewModel(caseId, topic, intent) } }
            )
            ConferenceReferenceLookupScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenArticle = { article ->
                    store.putArticle(article)
                    navController.navigate(Routes.article(article.pmid))
                }
            )
        }

        composable(Routes.ARTICLE, arguments = listOf(navArgument("pmid") { type = NavType.StringType })) { entry ->
            val article = store.article(entry.arguments?.getString("pmid").orEmpty())
            if (article != null) {
                PubMedArticleDetailScreen(article = article, onBack = { navController.popBackStack() })
            } else {
                // Only reachable if the process was recreated while on this
                // screen -- the article lived only in memory.
                androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
    }
}

/** Navigates to [route], dropping [replacing] (and anything above it) from the back stack. */
private fun NavHostController.replaceWith(route: String, replacing: String) {
    navigate(route) {
        popUpTo(replacing) { inclusive = true }
    }
}
