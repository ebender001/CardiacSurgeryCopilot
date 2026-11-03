package dev.benderapps.heartteamprep.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.benderapps.heartteamprep.model.AuthenticatedUser
import dev.benderapps.heartteamprep.ui.components.PrimaryButton
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors

/**
 * Account details with sign-out and permanent account deletion (required by
 * Play Store policy). Mirrors the iOS client's `AccountView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    user: AuthenticatedUser,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: ((String) -> Unit) -> Unit
) {
    val colors = heartTeamPrepColors()
    var isConfirmingSignOut by remember { mutableStateOf(false) }
    var isConfirmingDelete by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = { if (!isDeleting) onDismiss() }, containerColor = colors.warmBackground) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Account", style = MaterialTheme.typography.titleLarge)
            Text(user.email ?: "No email on file", style = MaterialTheme.typography.bodyLarge)
            Text("Signed in with email", style = MaterialTheme.typography.bodySmall, color = colors.slateText)

            Spacer(Modifier.height(8.dp))
            PrimaryButton(text = "Sign Out", onClick = { isConfirmingSignOut = true }, enabled = !isDeleting)

            OutlinedButton(
                onClick = { isConfirmingDelete = true },
                enabled = !isDeleting,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Delete Account", color = MaterialTheme.colorScheme.error)
            }

            Text(
                "Permanently deletes your account and every case you've prepared. This cannot be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.slateText
            )
            deleteErrorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (isConfirmingSignOut) {
        AlertDialog(
            onDismissRequest = { isConfirmingSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need to sign back in to access your cases. Your cases aren't affected.") },
            confirmButton = { TextButton(onClick = { isConfirmingSignOut = false; onSignOut() }) { Text("Sign Out") } },
            dismissButton = { TextButton(onClick = { isConfirmingSignOut = false }) { Text("Cancel") } }
        )
    }

    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently deletes your account and every case you've prepared. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingDelete = false
                    isDeleting = true
                    deleteErrorMessage = null
                    onDeleteAccount { message ->
                        isDeleting = false
                        deleteErrorMessage = message
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text("Cancel") } }
        )
    }
}
