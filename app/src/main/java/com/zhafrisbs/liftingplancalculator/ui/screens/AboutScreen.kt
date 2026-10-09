package com.zhafrisbs.liftingplancalculator.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhafrisbs.liftingplancalculator.BuildConfig
import com.zhafrisbs.liftingplancalculator.R
import com.zhafrisbs.liftingplancalculator.core.Contact
import com.zhafrisbs.liftingplancalculator.ui.components.SectionCard
import com.zhafrisbs.liftingplancalculator.ui.theme.TextMuted
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary

/**
 * What this app is, and who made it.
 *
 * The description is deliberately two sentences.  Someone opening this tab wants to
 * know whether the app does the job in front of them, and a paragraph about
 * architecture answers a question they did not ask — the report is where the
 * working, and the reasoning behind it, actually lives.
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.crane_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(96.dp),
            )
            Text(
                text = "Lifting Plan Calculator",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            // Both numbers, because they answer different questions and only the
            // pair identifies a build.  `3.3` is what a person says on the phone,
            // and `30300` is what Play and the crash log record -- a bug report
            // quoting only the first leaves you guessing which upload it was.
            Text(
                text = "Version ${BuildConfig.VERSION_NAME} " +
                    "(build ${BuildConfig.VERSION_CODE})",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        SectionCard(title = "What this does") {
            Text(
                text = "Sizes a crane lift and prints the working. It totals the load " +
                    "at the hook, checks the crane against its own load chart, and " +
                    "sizes the slings for uniform, off-centre and two-crane lifts.",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Every figure comes with the formula and the values that " +
                    "produced it, so a reviewer can check the arithmetic rather " +
                    "than take it on trust.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard(
            title = "Developer",
            note = "Write to this address to ask for access. Approving somebody is a " +
                "deliberate step rather than an automatic one.",
        ) {
            Text(
                text = Contact.NAME,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = Contact.EMAIL,
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            // Omitted entirely when no number is published, rather than shown blank:
            // "Phone:" followed by nothing reads as a fault.
            if (Contact.PHONE != null) {
                Text(
                    text = Contact.PHONE,
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SectionCard(
            title = "Built with",
            note = "These are the AI assistants used in writing this app. They wrote " +
                "code; the engineering decisions, the checks and the responsibility " +
                "for the figures are the developer's.",
        ) {
            ForEachContributor()
        }

        Text(
            // The one sentence on this page that is not about the app.
            text = "A lifting plan is only as good as the person who checks it. " +
                "Verify every figure against the crane's own load chart before a " +
                "lift is made.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * The assistants, one per line.
 *
 * A list rather than a sentence, because a sentence would have to invent a
 * relationship between them — which of them did what is not something this page
 * knows, and guessing would be a claim about other people's work.
 */
@Composable
private fun ForEachContributor() {
    for (name in listOf("Google AI", "opencode", "DeepSeek v4.1-flash")) {
        Text(
            text = name,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
