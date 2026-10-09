package com.zhafrisbs.liftingplancalculator.ui.screens

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhafrisbs.liftingplancalculator.core.KATEX_ASSET_DIR
import com.zhafrisbs.liftingplancalculator.core.ReportInputs
import com.zhafrisbs.liftingplancalculator.core.buildReportHtml
import com.zhafrisbs.liftingplancalculator.core.wrapKatex
import com.zhafrisbs.liftingplancalculator.ui.components.BackButton
import com.zhafrisbs.liftingplancalculator.ui.theme.BorderLine
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary
import com.zhafrisbs.liftingplancalculator.ui.theme.WindowBackgroundDeep

/**
 * The report, rendered and printable.
 *
 * The reference's phone UI has **no Summary tab**: each tab carries its own
 * "Show report" button, and `TAB_REPORT_SECTIONS` decides which sections that
 * button's report carries, so the set of sections is a property of the button
 * pressed rather than a second decision to make.  This screen is what those buttons
 * open.
 *
 * **The page is the reference's page, not a reimplementation of it.**  `wrapKatex`
 * and `buildReportHtml` are ports of `report_html.py` and `report_1leg.py`, and the
 * KaTeX distribution ships inside the APK, so the equations are typeset offline and
 * the report cannot change because a CDN did.  The app declares no INTERNET
 * permission, which makes that structural rather than a promise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    inputs: ReportInputs,
    sections: Set<String>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val page = remember(inputs, sections) {
        wrapKatex(buildReportHtml(inputs, sections))
    }

    var webView by remember { mutableStateOf<WebView?>(null) }

    // Loaded through the asset base URL rather than as a bare data string, because
    // that is what makes the page's relative `katex.min.css`, the two scripts and
    // the fonts inside them resolve against the APK's own assets.
    LaunchedEffect(page) {
        webView?.loadDataWithBaseURL(KATEX_ASSET_DIR, page, "text/html", "utf-8", null)
    }

    // The report is a full-screen destination, so the back gesture has to leave it
    // rather than leave the app.
    BackHandler(onBack = onClose)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WindowBackgroundDeep),
    ) {
        TopAppBar(
            title = { Text("Lifting Plan", fontWeight = FontWeight.Bold) },
            // A back arrow, not a cross: the report sits exactly where the tabs
            // were, so leaving it is going back rather than dismissing something
            // that was floating on top.
            navigationIcon = { BackButton(onClick = onClose) },
            actions = {
                TextButton(
                    onClick = {
                        webView?.let { printReport(context, it) }
                    },
                ) {
                    Text("Print")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = WindowBackgroundDeep,
                titleContentColor = TextPrimary,
                navigationIconContentColor = TextPrimary,
                actionIconContentColor = TextPrimary,
            ),
        )
        HorizontalDivider(color = BorderLine)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(androidx.compose.ui.graphics.Color.White),
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        // KaTeX's auto-render pass is JavaScript, so the page cannot
                        // typeset without this.  Nothing else here is scripted: the
                        // HTML is built in-process from solved state, and the only
                        // assets the page can reach are the bundled ones.
                        settings.javaScriptEnabled = true
                        // Assets stay reachable over `file:///android_asset/`
                        // regardless of file access, so the broader `file://`
                        // permission is deliberately left off.
                        setBackgroundColor(android.graphics.Color.WHITE)
                        webView = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

/**
 * Hand the page to the platform print framework.
 *
 * `A4_MARGIN_MILS` is deliberately **not** set.  The reference measured
 * `PrintAttributes.setMinMargins` to have no effect at all on this device — a page
 * saved with 1000 mils measured identical, to a tenth of a millimetre, to one saved
 * with none — so the margin comes from the `@page` rule in [wrapKatex] instead.  A
 * margin that is silently dropped is worse than no margin control, because it reads
 * as though it were applied.
 */
@Suppress("DEPRECATION")
private fun printReport(context: Context, webView: WebView) {
    val jobName = "Lifting Plan Calculation"
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val attributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .build()
    printManager.print(
        jobName,
        webView.createPrintDocumentAdapter(jobName),
        attributes,
    )
}
