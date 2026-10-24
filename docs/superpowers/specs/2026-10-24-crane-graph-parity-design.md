# Crane and Graph tab parity with the desktop appimage

## Intent

The Android app is a port of the desktop app in
`/mnt/backup/Appimage Projects/lifting-plan-calculator-v2/`
(GitHub: `zhafribs/lifting-plan-calculator`, Tauri + Rust + ES modules).

A load chart workbook was authored for that desktop app —
`TR350M-1-Load-Chart Full.xlsx`. Loading it on the desktop gives jib tables, a
crane floor position, and a Graph tab that follows the workbook. Loading the same
file in the APK silently drops all of that.

The goal is that the APK's **Crane** and **Graph** tabs behave identically to the
desktop's for any workbook the desktop can read, and in particular for that one.

## What the desktop does that the APK does not

1. **The importer.** `ExcelLoader.loadChart` returns on the *first* sheet that
   yields a chart. `Jib & Extend Jib` is therefore never read. `LoadChartData`
   has no field for a jib table or a crane position, so there is nowhere for them
   to land.
2. **The crane engine.** `Crane.kt` has no jib logic. `crane.rs` reads the
   capacity from a jib table at a **boom angle** (not a radius), derives the
   working radius from that angle, and suggests the farthest angle still passing
   the 75% rule.
3. **The Crane tab.** No jib configuration card, no "Crane location on axis"
   line, no capacity-vs-radius plot.
4. **The Graph tab.** The wiring is missing — `workbookSource`, `applyWorkbook`,
   `syncFromWorkbook`, `publishAngle`, `resetToCrane`, `deviated`.
   `CraneGraphScreen` takes no ViewModel and `remember`s its own state, seeded
   from `CRANE_PRESETS`; the desktop's graph *follows* the Crane tab.
5. **The graph's geometry is not the desktop's geometry.** See the next section —
   this is the one finding that changes the shape of the work. Also missing:
   `angleForWorkingRadius` has no counterpart in `CraneGraph.kt`.

## The graph's geometry does not match the desktop — and both claim the same ancestor

This was found by a reviewer reading the design, and verified in the source.

The original module survives at `zhafribs/Crane-Graph` → `crane_graph/geometry.py`
(227 lines, fetched from the public repo). It says:

```python
a = math.radians(angle_deg + jib_offset_deg)      # a positive offset RAISES the jib
jib_length * math.sin(a)                          # + sin
def working_radius(pivot, tip): return abs(tip[0] - pivot[0])   # from the crane
```

| | original Python | APK `CraneGraph.kt` | desktop `geometry.js` |
|---|---|---|---|
| `assemblyTip` angle | `angle + offset` | `angle + offset` | `angle - offset` |
| tip vertical component | `+jib·sin(offset)` | `+jib·sin(offset)` | `−jib·sin(offset)` |
| `workingRadius` | `abs(tip.x − pivot.x)` | `abs(tip.x − pivot.x)` | `tip.x` (from the `x = 0` axis) |
| a positive offset | raises the jib | raises the jib | **lowers** it (clockwise) |

So **the APK is a faithful port of the original Python**, and the desktop's
`geometry.js` deliberately diverged — despite its header claiming to be a
"faithful JavaScript port" of that same module. `CraneGraphTest.kt` pins the
Python's literals, and its header records that they were printed by executing
`crane_graph/geometry.py` under `python3`.

The desktop's own `crane.rs` agrees with its `geometry.js`, and states the intent
outright: *"the jib is pinned at the boom tip and offset **clockwise** from the
boom axis by `jibOffsetDeg` (a positive offset lowers the jib below the boom
line)"*. Its `tipOffsetFromPivot` is `(boom + jib·cos(offset), −jib·sin(offset))`
and its reach is `boom·cos(a) + jib·cos(a − offset)`.

**Decision (from "identical to appimage"): the desktop is the reference of
record.** So for parity, not merely wiring:

- `assemblyTip` and `tipOffsetFromPivot` adopt the clockwise convention.
- `workingRadius` becomes `tip.x` — measured from the `x = 0` axis, as the
  desktop and the load charts both measure it. This is latent today only because
  the crane sits at the origin; it becomes real the moment the workbook supplies
  `crane_x = −1.7`.
- The `R` dimension line in `drawDimensions` moves from the pivot to the axis.
- `CraneGraphTest`'s expected literals are re-derived by executing the desktop's
  `geometry.js`, and its provenance comment updated to say so.

The consequence to be aware of: **the APK's diagram changes behaviour for a
non-zero offset, and the APK stops matching the original Python app it came
from.** With the workbook's actual offsets (5°, 25°, 45°) the two conventions put
the tip in entirely different places — boom 31, jib 7.2, offset 25°, angle 83°
gives a reach of ≈ 7.59 m clockwise against ≈ 1.55 m counter-clockwise.

## Goals

- Reading `TR350M-1-Load-Chart Full.xlsx` in the APK offers the same jib
  configurations, lengths, offsets and boom angles the desktop offers.
- The Crane tab checks the lift against the jib table at a boom angle, and
  reports the same figures the desktop reports.
- The Crane tab shows the crane position from the workbook, the jib rows, and the
  capacity plot it shows today for main-boom charts.
- The Graph tab adopts the workbook's position, boom length, configuration, jib
  length, offset and angle, keeps following the Crane tab as it changes, offers
  "Reset to Crane Tab" once the operator drags away, and publishes the dragged
  angle back so both tabs agree.
- The behaviour matches the reference rule for rule, so the ported tests pass with
  the desktop's own expected literals.

## Non-goals

- The Overall, Uniform, Nonuniform, Tandem and Summary tabs. (`report.rs`'s
  `crane_section` is untouched by the jib work.)
- Any change to the desktop app.
- Workbooks the desktop cannot read.

## Anti-goals

- A second source of truth for jib configuration. The whole point of Approach A
  is that the two tabs share one state.
- The graph silently overriding figures the operator set on the Crane tab, or the
  Crane tab silently resetting the graph.
- Duplicating geometry maths. The wiring must call `CraneGraph.kt`, not re-derive
  it — though `CraneGraph.kt` itself must first be corrected to the desktop's
  convention (above).

## Constraints

- `core/` is pure Kotlin with no Android or Compose imports, because the test
  suite runs against it on the JVM. All engine and import work belongs there.
- The `ExcelLoader` "first sheet that yields a chart wins" rule must stay for the
  **main** chart.
- The JVM unit suite must keep passing (`./gradlew :app:testDebugUnitTest`).

## Approaches

Three were considered. All reach the same end state; they differ in where the
shared state lives.

**A — Move the graph state into the ViewModel, shared with the Crane tab.
CHOSEN.**

This is the desktop's own model: one `state.crane` both tabs read and write.
`CraneGraphState` stops being `remember`ed inside `CraneGraphScreen` and becomes a
`LiftViewModel` property beside `craneInputs`, with `chooseJib`, `setBoomAngle`
and `applyWorkbook` next to `importCraneChart`.

Trades off: the largest structural change — it also moves the crane solve and the
manual-row mirror into the ViewModel (see "Ownership", below), which is larger
than "`CraneGraphState` beside `craneInputs`" first implied.

**B — Leave the graph local; bridge with explicit sync.** *(rejected)*

Smaller diff, but two sources of truth. Change the jib config on the Crane tab and
the graph only catches up when re-synced — the exact split the desktop was built
to avoid, and the source of the drift this change exists to remove.

**C — Fold the graph geometry into the crane state entirely.** *(rejected)*

Cleanest on paper, but most churn in working graph code and its tests, for no
behavioural difference over A.

## Ownership

Decided here, because the reviewer correctly flagged that three pieces of the
desktop's graph live in the screen closure and the original write-up gave them no
home.

**The crane solve moves into the ViewModel.** On Android it currently happens in
the composable (`CraneScreen`: `runCatching { Crane.solve(inputs, totals.gross) }`).
But the desktop performs its **manual-row mirror** from the post-solve render pass,
and that mirror writes solved figures back into the inputs the graph's sync keys
and the report both read. A write-back triggered by a solve has no sane home in a
composable. So `LiftViewModel` owns the gross total, the solved `CraneResult`, and
the mirror.

**The graph's sync bookkeeping becomes ViewModel state**: `workbookMode`, the
chassis/input keys, and `deviated`. `deviated` in particular means "the diagram is
drawn somewhere the Crane tab is not" — if it lives in `remember`, rotating the
phone after a drag silently discards the operator's position and flips the caption
back.

**Loop safety.** The mirror and `publishAngle` both write only when a value
actually differs, which is the desktop's own guard against the solve cycle
looping. That guard is load-bearing here too.

## The reference behaviour to match

### The workbook

`TR350M-1-Load-Chart Full.xlsx` has two sheets: `Main Boom` and
`Jib & Extend Jib`. The jib sheet's first column is `Boom angle (°)`, its headings
name a jib length (`Jib 7.2 m`), and the row beneath the headings holds the offset
angles — one column per (jib length, offset) pair. It also carries a label naming
the crane's floor position: `Crane location on axis (X, Y)`.

Its shared strings match the desktop's Rust test fixture cell for cell —
`"Jib 7.2 m"`, `"Jib 12.8 m"`, `"5°/25°/45°"`, `"-1.7,2.8"` — so the ported tests
and the operator's real workbook agree.

### Importer rules (`src-tauri/src/excel.rs`)

- `first_number(text)` — the first number written in a string. `"Jib 7.2 m"` gives
  7.2, `"5°"` gives 5. Text with no number gives None.
- `chart_number(cell)` — a numeric cell, else `first_number` of its text.
- `pair_from_text(text)` — `"1.7,2.8"`, `"1.7 2.8"` and `"1.7;2.8"` all give
  `(1.7, 2.8)`.
- `find_position(rows)` — the first cell whose **normalised** text contains
  `cranelocation`. The value comes from the cell to its right, else the cell
  directly below it, else the cell below-right — taken as an `x,y` text or as two
  numeric cells.
- `parse_jib_sheet(rows)`:
  - **Header row**: the first of the first 40 rows whose first non-empty cell
    normalises to start with `boomangle`, or starts with `angle` *and* the row has
    a cell containing `jib`. (The sheet's own title text mentions "boom angle" on
    many charts, which is why the first cell must *be* the heading.)
  - **Groups**: header cells whose normalised text contains `jib` and whose first
    number is > 0. Each gives `(length, column)`.
  - **Sources**: the row below the header holds offsets. A group spans from its
    own column to the next group's column (or the end of the row); every column in
    that span holding a number gives `(length, offset, column)`.
  - **Points**: data rows start two below the header. The first cell is the boom
    angle; each source column gives a capacity when > 0, producing
    `(angle, capacity)` on that column.
  - `detect_unit` runs over every capacity to pick a multiplier; all capacities are
    scaled by it.
  - Returns None when there are no sources, or no positive capacities at all.
- **The scan is global, and independent of the main chart.** After the main chart
  is parsed: `position` is the first sheet that yields one, and `jib_columns` the
  first sheet that yields them.
- `jib_options(chart)` — the `(length, offset)` pairs, de-duplicated, in order.
- `jib_points(chart, length, offset)` — the matching column's points, else empty.

### Suggesting an angle (`excel.rs`)

`suggest_jib_angle(points, gross)`: None when `gross <= 0`. Otherwise the required
chart capacity is `gross / 0.75` (the existing `Calc` helper), the feasible points
are those whose capacity meets it, and the answer is the **smallest** angle among
them — the farthest reach that still carries the lift.

`jib_changed(inputs, chart, config, length, offset, gross)`: the config is
normalised to `boom` / `boom_jib` / `boom_ext_jib` (anything else becomes `boom`);
`active` is "not boom"; `chart_jib_points` is the selected column's points when
active, else empty; `boom_angle_deg` is the suggested angle when active, else None;
`rows[0].angle` is the suggested angle or 0; `jib_length` / `jib_offset` are set
when active and None when not.

### Crane engine rules (`src-tauri/src/crane.rs`)

- New `JibOption { length, offset }`.
- New `CraneInputs` fields: `chart_position`, `chart_jibs`, `jib_config` (default
  `"boom"`), `jib_length`, `jib_offset`, `chart_jib_points`, `boom_angle_deg`.
- `jib_active()` — `jib_config` is `boom_jib` or `boom_ext_jib`.
- **Boom angle resolution order** in the solve:
  1. `inputs.boom_angle_deg`, when set **and > 0** — the figure the Graph tab
     publishes.
  2. the angle the assembly geometry implies for the working radius
     (`implied_boom_angle`), **but only when `chart_position` is present** — so a
     jib chart can be checked before the Graph tab is ever opened, without a
     position-less matrix workbook suddenly reporting an angle it reports nothing
     for today.
  3. the manual row's own angle — the original behaviour.
- `implied_boom_angle(inputs, radius)`: `vx = boom + jib·cos(offset)`,
  `vy = −jib·sin(offset)` (clockwise, so the vertical part is negative),
  `envelope = hypot(vx, vy)`; the upper solution
  `acos(clamp(radius / envelope)) − atan2(vy, vx)`. None when `envelope <= 0`.
- `assembly_radius(inputs, angle)` — `crane_x + boom·cos(a) + jib·cos(a − offset)`.
- **With a jib, the jib table replaces the main-boom chart entirely.** When
  `jib_active` and `chart_source == "excel"` and `chart_jib_points` is non-empty,
  `chart_capacity_kg` is a lookup on `chart_jib_points` at the **boom angle**, not
  a radius. With no angle yet, no capacity is published — the boom chart's figure
  would be a lie once a jib is fitted.
- `radius_m` when active is `assembly_radius(inputs, boom_angle)`.
- **The radius is measured from the `x = 0` axis, not from the crane.**

### Crane tab UI (`ui/js/screens/crane.js`)

Shown **only when the workbook carries jib tables**:

- a `Jib configuration` card with three radios — `Boom length`,
  `Boom length + jib`, `Boom length + extended jib`;
- a line reading `Jib 7.20 m — set by the configuration` (the plain jib is the
  workbook's shortest table, the extended jib its longest);
- a `Boom angle` select listing the selected jib chart's own angles, sorted;
- a `Jib offset angle` select listing the offsets the selected jib length allows.

Other Crane tab behaviour:

- The **working radius picker is hidden while a jib is active** — the radius
  follows from the boom angle.
- With a jib, the manual entry's boom column reads as the assembly, `30+7.2`.
- A line reading `Crane location on axis (X, Y): (x, y) — from the workbook`, only
  when the workbook carries a position.
- **The mirror** into the manual row now also covers the angle; and with a jib,
  the radius, the capacity and the boom. All rounded to 2 decimals, the resolution
  the tab and the report print. Gated on the workbook carrying a position.
- The capacity check gains a `Jib` row when active (`7.20 m at 25° offset`), and
  the capacity row's label becomes `Jib capacity at boom angle`.
- **The capacity-vs-radius plot is suppressed while a jib is active** — the boom
  chart's marker would name the wrong thing.

Not carried across: the desktop's `fillSelect` signature caching, which exists to
stop WebKit scrolling a focused `<select>` back into view when its options are
replaced. Compose has no equivalent.

### Graph tab wiring (`ui/js/screens/graph.js`)

- `workbookSource()` — None unless `chart_source == "excel"` and the workbook has
  a position. Otherwise `{ x, y, boom (chartBoom), radius (workingRadius),
  config, jib (jibLength), offset (jibOffset), angle (boomAngleDeg) }`.
- `applyWorkbook(src, fit, user=false)`:
  - `config` normalised to one of `CONFIGS`, else `boom`;
  - `crane_x`, `crane_y` from the workbook;
  - `boom_length` = `src.boom` when > 0, else the state's own;
  - `jib_length` for `boom_jib`, `ext_jib_length` for `boom_ext_jib`;
  - `jib_offset`;
  - **the angle**: with a jib active and `src.angle > 0`, the angle is the *input*,
    clamped to `[ANGLE_MIN, ANGLE_MAX]` and rounded to 2 decimals. Otherwise (main
    boom) it is *derived* from the radius via
    `angleForWorkingRadius(boom_length, jib, offset, reach)` where
    `reach = radius − x`. A radius the geometry cannot solve leaves the angle
    alone.
- `syncFromWorkbook()` — workbook mode is on only when a source exists *and*
  `src.boom > 0`. A **chassis key** (x, y, boom, config, jib, offset) and an
  **input key** (the chassis plus the angle when a jib is active, else the radius)
  decide whether to re-pull the diagram toward the Crane tab.
- `deviated` — set once the operator drags; the caption then becomes a **Reset to
  Crane Tab** button (`resetToCrane()` clears the keys and the flag and re-syncs).
- `publishAngle()` — writes the graph's angle back: `boom_angle_deg`,
  `rows[0].angle`, and the derived working radius when a jib is active and the
  diagram is not deviated. Writes only when something differs. Without a jib it
  also requires a live working radius, because the angle comes *from* it.
- In workbook mode the config radios call the shared `chooseJib({ config })`.

Carried across deliberately unchanged: the desktop **greys out** the spin boxes a
configuration does not use; `CraneGraphScreen` already **hides** them, with a
recorded reason. That divergence stays.

## Proposed changes

### Chart model — `core/ExcelLoader.kt`, new `core/Jib.kt`

- `JibColumn(length, offset, points)`, `JibOption(length, offset)` (new file).
- `LoadChartData` gains `position: Pair<Double, Double>?` and
  `jibColumns: List<JibColumn>`.
- `ExcelLoader` gains `findPosition`, `parseJibSheet`, `firstNumber`,
  `pairFromText`; `loadChart` runs the global position/jib scan across all sheets
  after picking the main chart.

### Import helpers — `core/ExcelImport.kt`

- `CraneInputs` gains the seven jib/position fields.
- `jibOptions`, `jibPoints`, `suggestJibAngle`, `jibChanged`.
- `applied`, `boomChanged`, `radiusChanged`, `forgetChart` and
  `LiftViewModel.clearCrane` must carry or reset the new fields.

### Geometry correction — `core/CraneGraph.kt`

- `assemblyTip`, `tipOffsetFromPivot` → clockwise.
- `workingRadius(pivot, tip)` → `tip.x`.
- `angleForWorkingRadius` added (from `geometry.js`).

### Crane engine — `core/Crane.kt`

- `jibActive`; the gated angle resolution order; `impliedBoomAngle`;
  `assemblyRadius`; the jib-table branch in `solve`; `radiusM` from
  `assemblyRadius` when active.

### ViewModel — `ui/LiftViewModel.kt`

- `graphState: CraneGraphState`, the solved `CraneResult`, the gross total, the
  mirror, and the graph sync bookkeeping (`workbookMode`, keys, `deviated`).
- `chooseJib`, `setBoomAngle`, `updateGraphState`, `applyWorkbook`, `publishAngle`,
  `resetGraphToCrane`. `clearCrane` also resets the graph.

### Crane tab — `ui/screens/CraneScreen.kt`

- The jib card, position line, assembly boom display, new result rows, and the
  plot as a Compose `Canvas` replacing the desktop's inline SVG.
- The mirror extended per the reference rules (now performed by the ViewModel).

### Graph tab — `ui/screens/CraneGraphScreen.kt`

- Reads and writes the ViewModel's state; the local `remember`ed state and
  `CraneGraphStateSaver` go.
- `syncFromWorkbook` / `deviated` / `resetToCrane` / `publishAngle` equivalents.
- `drawDimensions`' `R` line moves to the axis.

### Tests

Ported from the Rust suite, keeping the desktop's own expected literals:

*excel.rs* — `the_new_format_position_is_read_from_its_label`,
`the_jib_sheet_parses_to_length_by_offset_tables`,
`a_workbook_carries_its_position_and_jib_tables_into_the_chart`,
`importing_keeps_the_longest_main_boom_and_offers_the_jib_tables`,
`choosing_a_jib_column_loads_its_points`,
`the_suggested_jib_angle_is_the_farthest_that_still_carries_the_lift`,
`choosing_a_jib_configuration_suggests_an_angle_that_passes_the_75_rule`.

*crane.rs* — `a_jib_configuration_derives_the_working_radius_from_the_boom_angle`,
`a_jib_configuration_is_checked_against_the_jib_table_at_the_boom_angle`,
`a_jib_without_an_angle_publishes_no_capacity_rather_than_the_boom_one`,
`a_jib_angle_is_implied_from_the_radius_for_the_lower_solution`,
`a_jib_offset_is_clockwise_and_raises_the_boom_for_the_same_reach`.

`CraneGraphTest`'s literals are **re-derived** by executing the desktop's
`geometry.js`, and its provenance comment updated.

## Verification

### Automated

```bash
./gradlew :app:testDebugUnitTest     # the ported suite, and the existing one
./gradlew :app:assembleDebug         # compiles the new UI
```

### Manual

Load `TR350M-1-Load-Chart Full.xlsx` on the device and compare against the desktop
running the same file:

- the Jib configuration card appears, with the same jib lengths and offsets;
- the position line matches;
- the same jib and offset produce the same capacity, radius and usage;
- dragging the tip on the Graph tab updates the Crane tab's boom angle, and
  "Reset to Crane Tab" restores it;
- main-boom-only workbooks still behave exactly as they do today.

## Calls made during this design

For review while reading:

1. **The desktop is the reference of record — CONFIRMED by the operator.** The
   APK's graph changes its offset sign and radius basis, and `CraneGraphTest`'s
   Python-derived literals are re-derived. The APK deliberately stops matching the
   original `Crane-Graph` Python app. Operator's words: *"desktop appimage is
   correct. follow what outcome of appimage"*.
2. **The crane solve, the manual-row mirror and the graph's sync bookkeeping move
   into the ViewModel**; `deviated` becomes ViewModel state so rotation cannot
   discard a dragged position.
3. **The implied-angle branch is gated on the workbook carrying a position**, as
   the reference does — otherwise a position-less matrix workbook would start
   reporting a boom angle it reports nothing for today, and the existing
   `CraneTest` expectation would break.
4. **The APK keeps hiding rather than greying out** the length fields a
   configuration does not use. Reread against *"follow what outcome of appimage"*:
   the two differ only in whether an unused box is visible-but-disabled or absent,
   and no figure changes either way — so the operator's own recorded decision for
   the phone stands. Flagged in case that reading is wrong.

## What is left to the builder

- Exact Compose layout of the jib card and the plot; the desktop's structure is the
  target (`docs/screenshots/02-crane.png`, `03-graph.png` in the desktop repo).
- Whether the phone plot keeps the desktop's 480×220 proportions or adapts.
- Where the jib options' display strings live (the desktop formats in the view).
- Fixture-building helpers for the jib sheet in the tests.
- Whether anything else depends on `CraneGraphStateSaver`.