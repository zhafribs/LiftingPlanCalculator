package com.zhafrisbs.liftingplancalculator.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhafrisbs.liftingplancalculator.ui.AuthViewModel
import com.zhafrisbs.liftingplancalculator.ui.components.BackButton
import com.zhafrisbs.liftingplancalculator.ui.components.PasswordField
import com.zhafrisbs.liftingplancalculator.ui.components.SectionCard
import com.zhafrisbs.liftingplancalculator.ui.theme.Danger
import com.zhafrisbs.liftingplancalculator.ui.theme.StateOver
import com.zhafrisbs.liftingplancalculator.ui.theme.StateWithin
import com.zhafrisbs.liftingplancalculator.ui.theme.TextMuted
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary
import com.zhafrisbs.liftingplancalculator.ui.theme.WindowBackgroundDeep

/**
 * Who you are signed in as, and the one thing about it you can change.
 *
 * **The password form only appears for an account that has one.** A Google sign-in's
 * password belongs to Google, and a form here would be offering to change something
 * this app has no power over — so that case says where to go instead, which is a
 * sentence rather than a control that would fail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AuthViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val user = viewModel.user
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }

    BackHandler(onBack = onClose)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WindowBackgroundDeep),
    ) {
        TopAppBar(
            title = { Text("Account", fontWeight = FontWeight.Bold) },
            navigationIcon = { BackButton(onClick = onClose) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = WindowBackgroundDeep,
                titleContentColor = TextPrimary,
                navigationIconContentColor = TextPrimary,
            ),
        )
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionCard(title = "Signed in") {
                Text(
                    text = user?.email.orEmpty().ifEmpty { "Not signed in" },
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "This is the address the approved list is checked " +
                        "against, so it has to match exactly.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (user?.hasPassword == true) {
                SectionCard(
                    title = "Change password",
                    note = "Your current password is asked for so that a phone left " +
                        "unlocked cannot be used to take the account over.",
                ) {
                    PasswordField(
                        label = "Current password",
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                    )
                    PasswordField(
                        label = "New password",
                        value = newPassword,
                        onValueChange = { newPassword = it },
                    )
                    Button(
                        onClick = {
                            viewModel.changePassword(currentPassword, newPassword)
                            // Cleared on the way out whether it worked or not.  On
                            // failure the operator is about to retype the current
                            // password anyway, and one that stayed in the box would
                            // be a password left sitting on a shared screen.
                            currentPassword = ""
                            newPassword = ""
                        },
                        enabled = !viewModel.busy &&
                            currentPassword.isNotEmpty() &&
                            newPassword.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (viewModel.busy) "Saving\u2026" else "Change password")
                    }

                    // The escape hatch for a password nobody remembers.  It does
                    // not weaken the rule above: the proof here is the inbox, not
                    // the old password, and that is the standard bargain — a reset
                    // link is only as strong as control of the email account, which
                    // is the same thing the allowlist already rests on.
                    Text(
                        text = "Current password not to hand? A reset email lets you " +
                            "set a new one without it.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = { viewModel.sendPasswordReset(user.email) },
                        enabled = !viewModel.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Send a reset email instead")
                    }
                }
            } else if (user != null) {
                SectionCard(title = "Password") {
                    Text(
                        text = "You signed in with Google, so this account has no " +
                            "password here to change.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Change it at myaccount.google.com, and it takes " +
                            "effect the next time you sign in.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (viewModel.message != null) {
                Text(
                    text = viewModel.message!!,
                    color = if (viewModel.message == "Password changed.") {
                        StateWithin
                    } else {
                        StateOver
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Button(
                onClick = {
                    viewModel.signOut()
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger,
                    contentColor = TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign out")
            }

            Box(Modifier.fillMaxWidth())
        }
    }
}
