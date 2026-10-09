package com.zhafrisbs.liftingplancalculator.core

/**
 * Assemble the report into a page that can render its own equations.
 *
 * A port of `lifting_calculator/report_html.py`.  The report body from [Report] is
 * a *fragment*: it carries `\[...\]` and `\(...\)` delimiters but no stylesheet, no
 * script, and no way to turn those delimiters into typeset mathematics.  This
 * supplies the missing half — the wrapper document itself.
 *
 * It is string concatenation and an asset path, nothing more, which is why it lives
 * beside the builder rather than inside the screen.  The reference's own note is
 * worth repeating: the equations are the evidence the whole plan rests on, so "the
 * same page" has to mean the same strings, not merely a similar-looking
 * reimplementation.
 *
 * **The KaTeX distribution is bundled rather than fetched**, so the page renders
 * with no network and no remote content: a lift plan read on site must not change
 * because a CDN did.  This app declares no INTERNET permission at all, which makes
 * that guarantee structural rather than a promise.
 */

/**
 * The page margin in millimetres.
 *
 * The desktop supplies its margin through `QPageLayout` and *ignores* the CSS rule;
 * on Android the print framework was measured to drop `setMinMargins` entirely
 * (identical output at 1000 mils and at none), so **the stylesheet is the only
 * channel that works here**.  The reference keeps both because they are two
 * engines' idioms for one number; on this device only one of them applies, and this
 * is it.
 */
const val PRINT_MARGIN_MM = 25.4

/**
 * The directory the wrapper's relative asset paths resolve against.
 *
 * The trailing separator is load-bearing: the wrapper asks for `katex.min.css`,
 * which resolves as a sibling only if the base behaves as a directory rather than
 * as a file.  `file:///android_asset/` is the one base URL a `WebView` will both
 * accept and resolve further assets against, which is what lets the page load
 * KaTeX from the APK with no file copied out to disk first.
 */
const val KATEX_ASSET_DIR = "file:///android_asset/katex/"

/**
 * The auto-render pass, as a raw string so the backslashes reach JavaScript intact.
 *
 * KaTeX's delimiters are `\(` and `\[` *after* JavaScript has read the literal, so
 * each needs a doubled backslash in the source.  A raw string keeps that legible
 * instead of burying it in four-backslash escapes.
 */
private val KATEX_AUTO_RENDER: String = """
document.addEventListener("DOMContentLoaded", function () {
  try { renderMathInElement(document.body, {
    delimiters: [
      {left: "\\(", right: "\\)", display: false},
      {left: "\\[", right: "\\]", display: true}
    ], throwOnError: false, strict: false
  }); } catch (e) {
    document.body.setAttribute("data-katex-error", e.message);
  }
});
""".trimIndent()

/**
 * [body] as a complete page that typesets its own equations.
 *
 * The inline script runs KaTeX's auto-render over the whole body on
 * `DOMContentLoaded`.  It cannot throw into the host, so a failure records the
 * reason as a `data-katex-error` attribute on the body instead: the page still
 * displays, with the delimiters visible as literal text, which is far easier to
 * diagnose than a blank view.  `throwOnError` is off for the same reason — one
 * malformed expression should not blank the whole report.
 */
fun wrapKatex(body: String): String = buildString {
    append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
    append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
    append("<style>")
    append("@page { size: A4; margin: ${PRINT_MARGIN_MM}mm; } ")
    append("html, body { background: #fff; margin: 0; padding: 0; } ")
    append("body { background-color: #fff; color: #111; font-size: 7pt; line-height: 1.2; } ")
    append("table { font-size: inherit; } ")
    append(".katex-display { text-align: left; margin: 3px 0; } ")
    append("</style>")
    append("<link rel=\"stylesheet\" href=\"katex.min.css\">")
    append("<script src=\"katex.min.js\"></script>")
    append("<script src=\"auto-render.min.js\"></script>")
    append("</head><body>")
    append(body)
    append("<script>")
    append(KATEX_AUTO_RENDER)
    append("</script></body></html>")
}
