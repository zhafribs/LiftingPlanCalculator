package com.zhafrisbs.liftingplancalculator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhafrisbs.liftingplancalculator.core.Admission
import com.zhafrisbs.liftingplancalculator.core.reportSectionsFor
import com.zhafrisbs.liftingplancalculator.ui.screens.AboutScreen
import com.zhafrisbs.liftingplancalculator.ui.screens.AccountScreen
import com.zhafrisbs.liftingplancalculator.ui.screens.CraneGraphScreen
import com.zhafrisbs.liftingplancalculator.ui.screens.CraneScreen
import com.zhafrisbs.liftingplancalculator.ui.screens.NonuniformLoadScreen
import com.zhafrisbs.liftingplancalculator.ui.screens.OverallWeightScreen
import com.zhafrisbs.liftingplancalculator.ui.screens.ReportScreen
import com.zhafrisbs.liftingplancalculator.ui.screens.SignInScreen
import com.zhafrisbs.liftingplancalculator.ui.screens.TandemScreen
import com.zhafrisbs.liftingplancalculator.ui.screens.UniformLoadScreen
import com.zhafrisbs.liftingplancalculator.ui.theme.AccentBlue
import com.zhafrisbs.liftingplancalculator.ui.theme.BorderLine
import com.zhafrisbs.liftingplancalculator.ui.theme.PanelSurface
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary
import com.zhafrisbs.liftingplancalculator.ui.theme.TextSecondary
import com.zhafrisbs.liftingplancalculator.ui.theme.WindowBackground
import com.zhafrisbs.liftingplancalculator.ui.theme.WindowBackgroundDeep

/**
 * The tabs, in the reference's own order and with its own wording.
 *
 * The eight working titles are not invented here: the desktop's `addTab` list and
 * the phone's `tabs.py` both read "Overall Weight", "Crane", "Uniform Load",
 * "Nonuniform Load", "Tandem".  The reference once had a tab called "Lift weight"
 * where the desktop said "Overall Weight", and nobody would have noticed until
 * the two were open side by side — so the wording travels with the port.
 *
 * **"About" is this app's own addition, and it is last on purpose.**  The reference
 * has no such tab, and the check that compares this list against the desktop's
 * compares it as a *subsequence* — so a tab appended at the end leaves the
 * reference's own order intact as a prefix, where one inserted in the middle would
 * read as a reordering.  It is also not a lift calculation: the five before it are
 * the workflow, and this one is about the app itself, so it belongs after them
 * rather than among them.
 *
 * **"Graph" is a second app's tab, and it sits immediately after "Crane".**  It is
 * the port of the separate `Crane Graph` desktop program, whose subject is the one
 * thing the Crane tab next door cannot show: the *working range* of the boom.  It is
 * placed there rather than appended at the end because a reader looking for the
 * crane's geometry will look beside the crane — which makes this the one deliberate
 * exception to the subsequence rule above, and the reason the tab's key
 * (`tab_graph`) is its own rather than a variant of `tab_crane`.
 */
enum class AppTab(val title: String, val key: String) {
    OVERALL("Overall Weight", "tab_overall"),
    CRANE("Crane", "tab_crane"),
    GRAPH("Graph", "tab_graph"),
    UNIFORM("Uniform Load", "tab_uniform"),
    NONUNIFORM("Nonuniform Load", "tab_nonuniform"),
    TANDEM("Tandem", "tab_tandem"),
    ABOUT("About", "tab_about"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiftingPlanApp(
    viewModel: LiftViewModel = viewModel(),
    auth: AuthViewModel = viewModel(),
) {
    // **Re-check when the app comes back to the front.**
    //
    // The allowlist used to be consulted only at a cold start, so removing somebody
    // took effect whenever they next launched the app -- and on a phone left open or
    // backgrounded, that could be a long time.  This closes the window to "the next
    // time they open it", without signing anyone out mid-lift.
    //
    // Doing it on every foreground is safe for the reason the offline design is: the
    // check *degrades* rather than failing.  Firestore falls back to its local copy
    // of the allowlist document with no signal, and a reload that cannot reach the
    // server falls back to the persisted session -- so an operator on site keeps the
    // access they were already given instead of being locked out of a plan.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // The first ON_START *is* the cold start, which `init` has already covered.
        // Skipping it keeps a launch to one check rather than two.
        var started = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (started) auth.refresh() else started = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // **The gate comes before everything, including the report.**  A report is a page
    // of a plan, and a plan is the thing the gate exists to protect -- so the check
    // sits outside the tabs rather than beside them, where a future screen could
    // forget to make it.
    //
    // A null is the moment between the process starting and Firebase answering from
    // its own persisted session.  It is left blank rather than showing a spinner,
    // because when it matters -- an operator who is already signed in -- it lasts a
    // frame or two, and a spinner that flashes is worse than nothing.
    val decided = auth.admission
    if (decided == null) return
    if (decided !is Admission.Allowed) {
        SignInScreen(viewModel = auth, admission = decided)
        return
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // A comma-joined section list, empty when no report is open.  A string rather
    // than a Set because it survives a rotation through the saved-instance Bundle
    // without a custom Saver, and losing an open report to a rotation is exactly
    // the kind of thing that makes a phone UI feel broken.
    //
    // **The set of sections is a property of the button pressed, not a second
    // decision to make.**  The reference has no Summary tab on the phone: each tab
    // carries its own control and `TAB_REPORT_SECTIONS` says what it carries.
    // The account screen sits **inside** the gate but outside the tabs: it is about
    // who is using the app rather than about a lift, and it is the only place a
    // password can be changed.
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var reportSections by rememberSaveable { mutableStateOf("") }
    // The tab whose report is waiting on the "Include: Crane" question, empty when no
    // dialog is open.  A sling tab's report carries the crane **by default** -- the
    // crane is booked whichever arrangement is used -- and the operator who wants
    // the arrangement on its own unticks it.
    var reportOffer by rememberSaveable { mutableStateOf("") }
    var includeCrane by rememberSaveable { mutableStateOf(true) }
    val tabs = AppTab.entries

    if (showAccount) {
        AccountScreen(viewModel = auth, onClose = { showAccount = false })
        return
    }

    // The report is a whole page -- the reference opens it as its own Android
    // activity -- so it replaces the tab shell rather than sitting inside it.
    if (reportSections.isNotEmpty()) {
        ReportScreen(
            inputs = viewModel.reportInputs(),
            sections = reportSections.split(",").toSet(),
            onClose = { reportSections = "" },
        )
        return
    }

    if (reportOffer.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { reportOffer = "" },
            title = { Text("Show report") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeCrane,
                        onCheckedChange = { includeCrane = it },
                    )
                    Text("Include: Crane")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reportSections = reportSectionsFor(reportOffer, includeCrane)
                            .joinToString(",")
                        reportOffer = ""
                    },
                ) {
                    Text("Show report")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportOffer = "" }) { Text("Cancel") }
            },
            containerColor = PanelSurface,
        )
    }

    Scaffold(
        containerColor = WindowBackground,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Lifting Plan Calculator",
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    // The account controls live here rather than inside a tab,
                    // because they are not about a tab: they are about who is using
                    // the app.  One entry point rather than two buttons, so the app
                    // bar still reads as a title with an aside.
                    actions = {
                        TextButton(onClick = { showAccount = true }) {
                            Text("Account", color = TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = WindowBackgroundDeep,
                        titleContentColor = TextPrimary,
                    ),
                )
                TabStrip(
                    tabs = tabs,
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Without this the keyboard simply covers the bottom of a form:
                // the reference shipped that bug and had to build its own
                // keyboard-height strip to work around it.
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            // On a tablet the form keeps a readable column instead of stretching
            // a 118dp number box across a metre of glass.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp),
            ) {
                when (tabs[selectedTab]) {
                    AppTab.OVERALL -> OverallWeightScreen(viewModel)
                    AppTab.CRANE -> CraneScreen(
                        viewModel,
                        onShowReport = { reportSections = "crane" },
                    )
                    // The diagram is drawn *from* the crane — its position, boom length,
                    // configuration and angle — so it takes the lift's ViewModel for the
                    // same reason the Crane tab next door does: the desktop keeps one
                    // `state.crane` that both read and write, and that one state is what
                    // makes the Graph tab follow the workbook.  It still contributes no
                    // section to the report: a working range is not part of the lift.
                    AppTab.GRAPH -> CraneGraphScreen(viewModel)
                    AppTab.UNIFORM -> UniformLoadScreen(
                        viewModel,
                        onShowReport = {
                            includeCrane = true
                            reportOffer = "uniform"
                        },
                    )
                    AppTab.NONUNIFORM -> NonuniformLoadScreen(
                        viewModel,
                        onShowReport = {
                            includeCrane = true
                            reportOffer = "nonuniform"
                        },
                    )
                    AppTab.TANDEM -> TandemScreen(
                        viewModel,
                        onShowReport = {
                            includeCrane = true
                            reportOffer = "tandem"
                        },
                    )
                    AppTab.ABOUT -> AboutScreen()
                }
            }
        }
    }
}

/**
 * The tab strip, scrolled sideways.
 *
 * Five titles do not fit across a phone, and the reference chose a strip you
 * swipe rather than shrinking or wrapping the text — the titles stay
 * unabbreviated, at the cost that the last two must be scrolled to.  This is the
 * same choice, and the selected tab is scrolled into view rather than left half
 * off the edge.
 */
@Composable
private fun TabStrip(
    tabs: List<AppTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    Column {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().background(WindowBackgroundDeep),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(tabs) { index, tab ->
                TabItem(
                    title = tab.title,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
        HorizontalDivider(color = BorderLine)
    }
}

@Composable
private fun TabItem(title: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .clickable(onClick = onClick)
            .background(if (selected) PanelSurface else WindowBackgroundDeep)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = if (selected) AccentBlue else TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .fillMaxWidth()
                .background(if (selected) AccentBlue else Color.Transparent),
        )
    }
}
