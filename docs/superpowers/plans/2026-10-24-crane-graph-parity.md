# Crane and Graph Tab Parity — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the APK's Crane and Graph tabs behave identically to the desktop appimage when a load-chart workbook carrying jib tables and a crane position is loaded.

**Architecture:** Extend the existing pure-Kotlin `core/` (chart model → import helpers → crane engine), correct the graph geometry to the desktop's convention, then move the crane solve, the manual-row mirror and the graph's sync bookkeeping into `LiftViewModel` so both tabs share one state, and render the new controls in the two Compose screens.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4 JVM unit tests, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-24-crane-graph-parity-design.md`

## Global Constraints

- `core/` stays free of Android and Compose imports — the unit suite runs on the JVM.
- **Reference of record:** `/mnt/backup/Appimage Projects/lifting-plan-calculator-v2/`. Every rule quoted below is authoritative there.
- `ExcelLoader.loadChart` keeps "the first sheet that yields a chart wins" **for the main chart**.
- Test commands: `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug`.
- This project has **no UI test harness** — only JVM tests against `core/`. UI tasks are verified by compiling plus the listed manual checks.
- Commit after every task.
- Never pipe content through a display-limiting command (`head`, `tail`). Take whole files.

## Review Focus

Five input classes the spec implies but no single task's tests fully cover. Each is pinned to the owning task below.

1. A sheet whose **title text** mentions "boom angle" above the real heading row — the parser must find the heading row, not the title. *(Task 2)*
2. A workbook with a **position but no jib tables**, or jib tables but no position — the two are found independently and neither's absence breaks the other. *(Task 2)*
3. A lift **no jib chart can carry**, or `gross = 0` — no suggested angle, and the Crane tab publishes **no** capacity rather than the main boom's. *(Task 4)*
4. The crane **off the `x = 0` axis** (`crane_x = −1.7`) — the working radius is measured from the axis, so engine and diagram agree. *(Tasks 1, 4)*
5. **Rotating the device after dragging** the diagram — the deviation survives and "Reset to Crane Tab" is still offered. *(Task 5)*

---

### Task 1: Correct the graph geometry to the desktop's convention

**Files:**
- Modify: `app/src/main/java/com/zhafrisbs/liftingplancalculator/core/CraneGraph.kt`
- Modify: `app/src/test/java/com/zhafrisbs/liftingplancalculator/CraneGraphTest.kt`

**Interfaces:**
- Produces (later tasks rely on these exact signatures):
  - `assemblyTip(pivot: GraphPoint, boomLength: Double, jibLength: Double, jibOffsetDeg: Double, angleDeg: Double): GraphPoint`
  - `tipOffsetFromPivot(boomLength: Double, jibLength: Double, jibOffsetDeg: Double): GraphPoint`
  - `workingRadius(tip: GraphPoint): Double` — **signature changes**, `pivot` drops
  - `angleForWorkingRadius(boomLength: Double, jibLength: Double, jibOffsetDeg: Double, radius: Double): Double` — **new**

The three decided values, from the desktop's `ui/js/graph/geometry.js`:
- `assemblyTip` uses `angleDeg − jibOffsetDeg` (a positive offset **lowers** the jib).
- `tipOffsetFromPivot` returns `(boomLength + jibLength·cos(offset), −jibLength·sin(offset))`.
- `workingRadius(tip)` is `tip.x` — measured from the `x = 0` axis, not from the crane.

- [ ] **Step 1: Re-derive every literal in `CraneGraphTest.kt` from the desktop module**

Values below are already verified against the module — they match a `node` run of `geometry.js` to the last digit. Transcribe them; do not re-invent.

| Call | Expected |
|---|---|
| `tipOffsetFromPivot(30.0, 12.0, 15.0)` | `x = 41.59110991546882`, `y = -3.105828541230249` |
| `envelopeRadius(30.0, 12.0, 0.0)` | `42.0` |
| `envelopeRadius(30.0, 12.0, 15.0)` | `41.706913035228695` |
| `envelopeRadius(30.0, 24.0, -10.0)` | `53.79705535006148` |
| `assemblyTip(GraphPoint(0.0, 0.0), 30.0, 12.0, 15.0, 30.0)` | `x = 37.57187202900198`, `y = 18.105828541230245` |
| `assemblyTip(GraphPoint(0.0, 0.0), 30.0, 24.0, -10.0, 60.0)` | `x = 23.208483439816057`, `y = 48.53338501239496` |
| `angleForWorkingRadius(30.0, 12.0, 0.0, 23.4)` | `56.14156334054405` |
| `readouts(DEFAULT_STATE).workingRadius` | `21.213203435596427` |
| `readouts(DEFAULT_STATE).envelopeRadius` | `30.0` |

Update the file's header comment: the literals now come from executing the desktop's `geometry.js`, and record the command, `node --input-type=module -e "import * as geo from '<abs path>/geometry.js'; ..."`.

- [ ] **Step 2: Run the suite and watch the re-derived expectations fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*CraneGraphTest*'`
Expected: FAIL — the offset sign and the radius basis are still the old ones.

- [ ] **Step 3: Change `assemblyTip` and `tipOffsetFromPivot`**

In `CraneGraph.kt`, `assemblyTip`'s angle becomes `angleDeg - jibOffsetDeg`; `tipOffsetFromPivot`'s `y` becomes `-jibLength * sin(a)`.

- [ ] **Step 4: Change `workingRadius` and add `angleForWorkingRadius`**

`workingRadius(tip: GraphPoint): Double = tip.x`. Update its one caller in `readouts`. Add `angleForWorkingRadius` porting `geometry.js`'s body verbatim: `base = atan2(vy, vx)`, `ratio = min(1, radius / envelope)`, result `normalizeAngle(toDegrees(acos(ratio)) - base)`.

- [ ] **Step 5: Add the two Review Focus tests**

`theWorkingRadiusIsMeasuredFromTheAxisNotTheCrane`: crane at `(-1.7, 2.8)`, boom `30.0`, angle `60.0`, no jib → `workingRadius` is `13.300000000000004`, **not** `15.0`.
`angleForWorkingRadiusIsTheInverseOfTheTipReach`: assert the `56.14156334054405` above.

- [ ] **Step 6: Run the suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, including the untouched `CraneGraphTest` cases.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zhafrisbs/liftingplancalculator/core/CraneGraph.kt \
        app/src/test/java/com/zhafrisbs/liftingplancalculator/CraneGraphTest.kt
git commit -m "fix: measure the working radius from the axis, and the jib offset clockwise

The desktop deliberately diverged from the original crane_graph/geometry.py: its
jib offset lowers the jib and its working radius is measured from the x = 0 axis
rather than from the crane. The APK followed the Python. Parity means following
the desktop, so the literals in CraneGraphTest are re-derived from geometry.js."
```

---

### Task 2: Read the jib sheet and the crane position from a workbook

**Files:**
- Create: `app/src/main/java/com/zhafrisbs/liftingplancalculator/core/Jib.kt`
- Modify: `app/src/main/java/com/zhafrisbs/liftingplancalculator/core/ExcelLoader.kt`
- Create: `app/src/test/java/com/zhafrisbs/liftingplancalculator/JibSheetImportTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `JibColumn(length: Double, offset: Double, points: List<Pair<Double, Double>>)`; `JibOption(length: Double, offset: Double)`; `LoadChartData.position: Pair<Double, Double>?`; `LoadChartData.jibColumns: List<JibColumn>`; `ExcelLoader.findPosition(rows: List<List<Any?>>): Pair<Double, Double>?`; `ExcelLoader.parseJibSheet(rows: List<List<Any?>>): List<JibColumn>?`; `ExcelLoader.firstNumber(text: String): Double?`; `ExcelLoader.pairFromText(text: String): Pair<Double, Double>?`.

Port the rules verbatim from `src-tauri/src/excel.rs`: `first_number` (line 304), `pair_from_text` (line 325), `find_position` (line 346), `parse_jib_sheet` (line 386). The spec restates all four.

- [ ] **Step 1: Write the failing tests**

In `JibSheetImportTest.kt`. Transcribe the desktop's own fixtures **verbatim** from `src-tauri/src/excel.rs` lines 1710–1800 — do not invent rows:

- `theNewFormatPositionIsReadFromItsLabel` — port `the_new_format_position_is_read_from_its_label` (line 1739): the label's value to its right, directly below, below-right, and as two numeric cells; all give `(-1.7, 2.8)`; text with no label gives `null`.
- `theJibSheetParsesToLengthByOffsetTables` — port `the_jib_sheet_parses_to_length_by_offset_tables` (line 1760), asserting the same column count, lengths, offsets and points. Include its negative case: a sheet that is not a jib chart (`["Radius", 9.8, 13.3]`) parses to `null`.
- `aSheetWhoseTitleMentionsBoomAngleIsNotMistakenForTheHeader` — **Review Focus 1.** A title row containing the words "boom angle" above the real `Boom angle (°)` heading must be skipped: the header is the row whose *first* cell normalises to start with `boomangle`.
- `aWorkbookCarriesItsPositionAndJibTablesIntoTheChart` — port `a_workbook_carries_its_position_and_jib_tables_into_the_chart` (line 1785) via `ExcelLoader.loadChart`, asserting `position == (-1.7, 2.8)` and six jib columns.
- `aPositionWithoutJibTablesStillYieldsThePosition` — **Review Focus 2.** A workbook whose only sheet carries a position and a main-boom chart yields `position != null` and `jibColumns.isEmpty()`.
- `aJibSheetWithoutAPositionStillYieldsTheJibTables` — **Review Focus 2**, the converse.

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*JibSheetImportTest*'`
Expected: FAIL — `JibColumn` unresolved.

- [ ] **Step 3: Create `Jib.kt` and extend `LoadChartData`**

Add the two data classes. Add `position` and `jibColumns` to `LoadChartData`, defaulting to `null` and `emptyList()`. Update every `LoadChartData(...)` construction in `ExcelLoader` accordingly.

- [ ] **Step 4: Implement the four parsing helpers in `ExcelLoader`**

Port the four named functions from `excel.rs`. Reuse `ExcelLoader.normalise` where the Rust calls `normalise`.

- [ ] **Step 5: Run the global scan in `loadChart`**

Restructure so the main chart is still picked by the existing first-wins loop, but the position and jib scan then runs across **all** sheets on the same cleaned rows, and the result is `chart.copy(position = ..., jibColumns = ...)`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, and the existing `ExcelImportTest` untouched.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zhafrisbs/liftingplancalculator/core/Jib.kt \
        app/src/main/java/com/zhafrisbs/liftingplancalculator/core/ExcelLoader.kt \
        app/src/test/java/com/zhafrisbs/liftingplancalculator/JibSheetImportTest.kt
git commit -m "feat: read the workbook's jib sheet and crane position

The importer stopped at the first sheet that yielded a chart, so a two-sheet
workbook's jib tables were never read and there was nowhere to put a crane
position. Both are now found independently, across every sheet."
```

---

### Task 3: Carry the jib through the imported inputs

**Files:**
- Modify: `app/src/main/java/com/zhafrisbs/liftingplancalculator/core/ExcelImport.kt`
- Modify: `app/src/test/java/com/zhafrisbs/liftingplancalculator/JibSheetImportTest.kt`

**Interfaces:**
- Consumes: `JibOption`, `JibColumn`, `LoadChartData.position` / `.jibColumns` (Task 2).
- Produces: `CraneInputs`' seven new fields; `CraneInputs.jibActive: Boolean`; `ExcelImport.jibOptions(chart: LoadChartData): List<JibOption>`; `ExcelImport.jibPoints(chart: LoadChartData, length: Double?, offset: Double?): List<Pair<Double, Double>>`; `ExcelImport.suggestJibAngle(points: List<Pair<Double, Double>>, gross: Double): Double?`; `ExcelImport.jibChanged(inputs: CraneInputs, chart: LoadChartData, config: String, length: Double?, offset: Double?, gross: Double): CraneInputs`.

Port verbatim from `src-tauri/src/excel.rs`: `jib_options` (line 1158), `jib_points` (line 1173), `suggest_jib_angle` (line 1196), `jib_changed` (line 1216). Reuse the existing `Calc` helper for `gross / 0.75`.

- [ ] **Step 1: Write the failing tests**

- `importingKeepsTheLongestMainBoomAndOffersTheJibTables` — port `importing_keeps_the_longest_main_boom_and_offers_the_jib_tables` (`excel.rs` line 1804): after `applied`, `chartPosition == (-1.7, 2.8)`, `chartJibs.size == 6`, `jibConfig == "boom"`, `chartJibPoints.isEmpty()`.
- `choosingAJibColumnLoadsItsPoints` — port `choosing_a_jib_column_loads_its_points` (line 1836): `jibChanged(inputs, chart, "boom_jib", 7.2, 25.0, 0.0)` gives `jibConfig == "boom_jib"`, `jibLength == 7.2`, `jibOffset == 25.0`, `chartJibPoints == [(45.0, 550.0), (83.0, 1800.0)]`; and switching back to `"boom"` clears `jibLength`, `jibOffset` and `chartJibPoints`.
- `theSuggestedJibAngleIsTheFarthestThatStillCarriesTheLift` — port line 1857: `suggestJibAngle(points, 1000.0) == 65.0`, `(points, 400.0) == 50.0`, `(points, 100000.0) == null`, `(points, 0.0) == null`.
- `choosingAJibConfigurationSuggestsAnAngleThatPassesThe75Rule` — port line 1876.
- `forgettingTheChartClearsTheJib` — `forgetChart` empties `chartJibs`, `chartJibPoints`, resets `jibConfig` to `"boom"`, and nulls `jibLength`, `jibOffset`, `boomAngleDeg`, `chartPosition`.

- [ ] **Step 2: Run and watch them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*JibSheetImportTest*'`
Expected: FAIL — `CraneInputs` has no `chartJibs`.

- [ ] **Step 3: Add the seven fields and `jibActive`**

`chartPosition: Pair<Double, Double>? = null`, `chartJibs: List<JibOption> = emptyList()`, `jibConfig: String = "boom"`, `jibLength: Double? = null`, `jibOffset: Double? = null`, `chartJibPoints: List<Pair<Double, Double>> = emptyList()`, `boomAngleDeg: Double? = null`. `jibActive` is true when `jibConfig` is `"boom_jib"` or `"boom_ext_jib"`.

- [ ] **Step 4: Implement the four helpers and thread the fields through `applied` / `boomChanged` / `radiusChanged` / `forgetChart`**

`applied` must populate `chartPosition`, `chartJibs`, `jibConfig = "boom"`. A boom or radius change must not clear the jib. `forgetChart` clears them all.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zhafrisbs/liftingplancalculator/core/ExcelImport.kt \
        app/src/test/java/com/zhafrisbs/liftingplancalculator/JibSheetImportTest.kt
git commit -m "feat: offer the workbook's jib tables on the imported inputs"
```

---

### Task 4: Check a jib configuration against the jib table

**Files:**
- Modify: `app/src/main/java/com/zhafrisbs/liftingplancalculator/core/Crane.kt`
- Create: `app/src/test/java/com/zhafrisbs/liftingplancalculator/CraneJibTest.kt`

**Interfaces:**
- Consumes: `CraneInputs.jibActive`, the seven fields (Task 3).
- Produces: `Crane.impliedBoomAngle(inputs: CraneInputs, radius: Double): Double?`; `Crane.assemblyRadius(inputs: CraneInputs, angle: Double): Double?`. `Crane.solve` and `CraneResult` keep their existing shapes.

Port verbatim from `src-tauri/src/crane.rs`: the angle resolution block (line 344), `implied_boom_angle` (line 459), `assembly_radius` (line 498), and the jib branch (line 359).

- [ ] **Step 1: Write the failing tests**

Port these five from `crane.rs`, keeping the desktop's own literals:

- `aJibConfigurationDerivesTheWorkingRadiusFromTheBoomAngle` — line 875.
- `aJibConfigurationIsCheckedAgainstTheJibTableAtTheBoomAngle` — line 897: boom 34, jib 7.2 at 25°, points `[(83.0, 1800.0), (72.0, 1650.0)]`, angle 72 → `chartCapacityKg == 1650.0`, `capacitySource == "Excel chart"`.
- `aJibWithoutAnAnglePublishesNoCapacityRatherThanTheBoomOne` — line 925. **Review Focus 3.**
- `aJibAngleIsImpliedFromTheRadiusForTheLowerSolution` — line 945.
- `aJibOffsetIsClockwiseAndRaisesTheBoomForTheSameReach` — line 962: with a 30 m boom + 12.8 m jib at 0° offset, radius 23.4 → `impliedBoomAngle` is `60.0`; at 5° offset it is `61.3419`. **Review Focus 4.**

- [ ] **Step 2: Run and watch them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*CraneJibTest*'`
Expected: FAIL — `impliedBoomAngle` unresolved.

- [ ] **Step 3: Implement `impliedBoomAngle` and `assemblyRadius`**

`impliedBoomAngle` returns `null` when the envelope is `<= 0`, and takes the upper solution. `assemblyRadius` is `chartPosition.x + boom·cos(a) + jib·cos(a − offset)`, with `jib = 0` and `offset = 0` when not `jibActive`.

- [ ] **Step 4: Wire the angle resolution into `solve`**

Order: `boomAngleDeg` when `> 0`, else `impliedBoomAngle` **only when `chartPosition != null`**, else `manualAngle`. Then the jib branch: when `jibActive` and `chartSource == "excel"` and `chartJibPoints` is non-empty, `chartCapacityKg` is the chart lookup on `chartJibPoints` at the boom angle, and `null` when there is no angle. `radiusM` when active is `assemblyRadius(inputs, boomAngle)`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS. `CraneTest` and `CraneManualEntryTest` must be untouched — a position-less workbook still reports no boom angle.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zhafrisbs/liftingplancalculator/core/Crane.kt \
        app/src/test/java/com/zhafrisbs/liftingplancalculator/CraneJibTest.kt
git commit -m "feat: check a jib configuration against the workbook's jib table

With a jib fitted the table is read at the boom angle rather than the radius,
and it replaces the main-boom chart entirely. With no angle yet, no capacity is
published rather than the boom chart's."
```

---

### Task 5: Share one crane state between the two tabs

**Files:**
- Modify: `app/src/main/java/com/zhafrisbs/liftingplancalculator/ui/LiftViewModel.kt`

**Interfaces:**
- Consumes: everything from Tasks 3 and 4.
- Produces, on `LiftViewModel`: `graphState: CraneGraphState`; `craneResult: CraneResult?`; `graphDeviated: Boolean`; `chooseJib(config: String? = null, offset: Double? = null)`; `setBoomAngle(angle: Double)`; `updateGraphState(state: CraneGraphState)`; `applyWorkbook(fit: Boolean)`; `publishAngle()`; `resetGraphToCrane()`; `updateGraphCranePosition(x: Double, y: Double)`.

**This replaces `CraneScreen`'s in-composable solve.** `CraneScreen` and `CraneGraphScreen` both read `viewModel.craneResult` from here on.

Port the reference behaviour from `ui/js/screens/graph.js`: `syncFromWorkbook` (line 838), `publishAngle` (line 882), `applyWorkbook` (line 778), `workbookSource` (line 761), `resetToCrane` (line 832), and the manual-row mirror in `crane.js`'s `render` (the `if (craneState.chart_position)` block).

- [ ] **Step 1: Add the state and the solve**

`graphState` starts at `DEFAULT_STATE`. Compute the gross from `loadItems` + `tackleItems`, solve, and keep the result in `craneResult`. Perform the **manual-row mirror** immediately after each solve: when `chartPosition != null`, write the solved `boomAngleDeg` into `rows[0].angle`, and when a jib is active also `radiusM` → the working radius, `chartCapacityKg` → the capacity, and `chartBoom` → the boom — each rounded to 2 decimals, and **only when the value actually differs**, which is the guard against a solve loop.

- [ ] **Step 2: Add the graph sync bookkeeping**

`graphDeviated: Boolean`, and private chassis/input keys. `applyWorkbook(fit)` reads `workbookSource()`-equivalent values from `craneInputs` and patches `graphState`: crane position, boom length, config, the jib or extended-jib length, the offset, and the angle — the angle being the workbook's when a jib is active and `> 0`, otherwise derived via `angleForWorkingRadius(boomLength, jib, offset, radius − craneX)` clamped to `[ANGLE_MIN, ANGLE_MAX]`. Re-pull only when a key changed or the diagram is not deviated.

- [ ] **Step 3: Add `publishAngle`, `resetGraphToCrane`, `chooseJib`, `setBoomAngle`**

`publishAngle` writes `graphState.angle` back to `craneInputs.boomAngleDeg` and `rows[0].angle`, plus the derived working radius when a jib is active and not deviated — only when a value differs. `chooseJib` routes through `ExcelImport.jibChanged` and then re-syncs. `resetGraphToCrane` clears the keys and `graphDeviated`, then re-syncs.

- [ ] **Step 4: Reset the graph in `clearCrane`**

`clearCrane` also returns `graphState` to `DEFAULT_STATE` and clears `graphDeviated`.

- [ ] **Step 5: Compile and run the existing suite**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS and compiles. `CraneScreen` keeps its own solve for now and still compiles — Task 6 switches it to `craneResult`. If the mirror and the screen's own field writes fight, fix it here rather than in Task 6.

- [ ] **Step 6: Verify Review Focus 5 by inspection**

Confirm `graphDeviated` and the sync keys are ViewModel state, not `remember`. A rotation must not discard a dragged position. There is no automation for this; state it in the commit message.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zhafrisbs/liftingplancalculator/ui/LiftViewModel.kt
git commit -m "refactor: one crane state shared by the Crane and Graph tabs

The desktop keeps one state.crane both tabs read and write, which is what makes
the diagram follow the workbook. The graph's state, the solve, the manual-row
mirror and the sync bookkeeping (including whether the operator has dragged away)
move into the ViewModel so they survive recomposition and rotation."
```

---

### Task 6: The Crane tab's jib card, position line, rows and plot

**Files:**
- Modify: `app/src/main/java/com/zhafrisbs/liftingplancalculator/ui/screens/CraneScreen.kt`

**Interfaces:**
- Consumes: `LiftViewModel.craneResult`, `graphState`, `chooseJib`, `setBoomAngle` (Task 5), and `CraneInputs.chartJibs` / `chartJibPoints` / `jibConfig` / `jibLength` / `jibOffset` / `jibActive` / `chartPosition` (Task 3).
- Produces: nothing later tasks consume.

Target structure is `ui/js/screens/crane.js`: the jib card (line 87), `syncJib` (line 303), the position line (line 245), the mirror's display rules (line 267), the result rows (line 425) and `plot` (line 516).

- [ ] **Step 1: Replace the local solve with the ViewModel's result**

`CraneScreen` reads `viewModel.craneResult` and drops its `runCatching { Crane.solve(...) }`, its `solveError` and its `chartName` (the mirror now lives in the ViewModel).

- [ ] **Step 2: Add the position line**

When `inputs.chartPosition != null`, show `Crane location on axis (X, Y): (x, y) — from the workbook`, formatted as the desktop formats it.

- [ ] **Step 3: Add the jib card**

Shown only when `inputs.chartJibs.isNotEmpty()`. Three radios (`Boom length`, `Boom length + jib`, `Boom length + extended jib`) calling `chooseJib(config = …)`; the line `Jib 7.20 m — set by the configuration`; a Boom angle dropdown from the selected chart's angles, sorted, calling `setBoomAngle`; a Jib offset dropdown from the offsets the selected length allows, calling `chooseJib(offset = …)`.

- [ ] **Step 4: Hide the working-radius picker while a jib is active, and show the assembly in the boom column**

Gate the existing `Working Radius & Boom Length` card on `!inputs.jibActive`. With a jib, the manual entry's boom field displays `30+7.2` and is not directly editable; the desktop's text is authoritative.

- [ ] **Step 5: Add the jib result rows**

A `Jib` row (`7.20 m at 25° offset`) when active, and the capacity row's label becomes `Jib capacity at boom angle`.

- [ ] **Step 6: Add the capacity plot**

A Compose `Canvas` drawing the desktop's plot when the chart is not a jib check: capacity against radius, grid, the charted points joined, and the working radius marked with its value. Suppressed entirely while a jib is active. The desktop's own version is an SVG generated at line 516 of `crane.js`, 480×220 with margins `l 46, r 16, t 14, b 30`.

- [ ] **Step 7: Compile and check by eye**

Run: `./gradlew :app:assembleDebug`
Expected: compiles. Then deploy and load `TR350M-1-Load-Chart Full.xlsx`: the jib card appears, the position line reads `(-1.7, 2.8)`, the working-radius picker is gone, and the boom column reads `31+7.2`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/zhafrisbs/liftingplancalculator/ui/screens/CraneScreen.kt
git commit -m "feat: the Crane tab's jib configuration, position line and capacity plot"
```

---

### Task 7: Make the Graph tab follow the Crane tab

**Files:**
- Modify: `app/src/main/java/com/zhafrisbs/liftingplancalculator/ui/screens/CraneGraphScreen.kt`
- Modify: `app/src/main/java/com/zhafrisbs/liftingplancalculator/ui/LiftingPlanApp.kt`

**Interfaces:**
- Consumes: `LiftViewModel.graphState`, `graphDeviated`, `applyWorkbook`, `publishAngle`, `resetGraphToCrane`, `updateGraphState` (Task 5).
- Produces: nothing.

Port from `ui/js/screens/graph.js`: `syncFromWorkbook` (838), `renderNote` (814), `resetToCrane` (832), `publishAngle` (882), and the sync calls in `sync` (1094).

- [ ] **Step 1: Pass the ViewModel into the screen**

`CraneGraphScreen(viewModel, modifier)` replaces `CraneGraphScreen(modifier)`. Update the call site in `LiftingPlanApp.kt`. Delete the local `remember`ed state and `CraneGraphStateSaver`.

- [ ] **Step 2: Wire the sync and the deviation flag**

On each sync: `viewModel.applyWorkbook(fit = …)`, then `viewModel.publishAngle()`. A drag sets `graphDeviated`; the caption then becomes a **Reset to Crane Tab** button calling `viewModel.resetGraphToCrane()`.

- [ ] **Step 3: Route the configuration radios through `chooseJib` in workbook mode**

As `graph.js` does at line 936, so a configuration chosen on the diagram is the same choice the Crane tab shows.

- [ ] **Step 4: Measure the `R` dimension from the axis**

`drawDimensions` currently draws the radius line from the pivot; move it to the `x = 0` axis to match the desktop and the readout.

- [ ] **Step 5: Compile and run the whole suite**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS and compiles.

- [ ] **Step 6: Manual parity check against the desktop**

Run the desktop app on the same `TR350M-1-Load-Chart Full.xlsx`, and on the device confirm:

- the same jib length and offset give the same capacity, working radius and usage;
- dragging the tip changes the Crane tab's **Boom angle**;
- **Reset to Crane Tab** restores it;
- rotating the device after a drag keeps the dragged position;
- a main-boom-only workbook behaves exactly as it does today.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zhafrisbs/liftingplancalculator/ui/screens/CraneGraphScreen.kt \
        app/src/main/java/com/zhafrisbs/liftingplancalculator/ui/LiftingPlanApp.kt
git commit -m "feat: the Graph tab follows the Crane tab's workbook

The diagram adopts the workbook's crane position, boom length, configuration and
angle, offers Reset to Crane Tab once the operator drags away, and publishes the
dragged angle back so the two tabs agree — the desktop's own arrangement."
```

---

## Final verification

- [ ] `./gradlew :app:testDebugUnitTest` — the ported suite and every existing test pass.
- [ ] `./gradlew :app:assembleDebug` — compiles.
- [ ] Side-by-side run against the desktop with `TR350M-1-Load-Chart Full.xlsx`, per Task 7 Step 6.
- [ ] Re-check the five Review Focus items, each against the task that owns it.