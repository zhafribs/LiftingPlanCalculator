package com.zhafrisbs.liftingplancalculator.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Who to ask for access, in one place.
 *
 * Declared once because it appears in three: the About tab, the refusal a
 * not-yet-approved user sees, and the mail that request sends.  Three copies of an
 * email address is three chances for one of them to be out of date, and the one
 * that goes stale will be the one nobody reads.
 */
object Contact {

    const val NAME = "Zhafri Syazwi"
    const val EMAIL = "zhafribs@gmail.com"

    /**
     * The number to ring, or null when none is published.
     *
     * Null rather than an empty string so the screens can *omit* the line rather
     * than print a blank one: "Phone:" followed by nothing reads as a fault.  It is
     * set here once and every screen picks it up.
     *
     * Grouped for reading rather than left as `+601121425200`.  The digits are
     * unchanged -- `+60` `11-2142` `5200`, the Malaysian mobile form -- but a
     * number nobody can read at a glance is a number somebody dials wrong, which on
     * this screen means a rigger with no access and no idea why.
     */
    val PHONE: String? = "+60 11-2142 5200"

    /**
     * Open the operator's own mail app, addressed here, about [theirEmail].
     *
     * **Why the operator's mail app rather than a server sending this.** Telling the
     * developer that somebody has asked for access needs something watching the
     * sign-ups, and Firebase has no built-in "new user" mail — it takes a Cloud
     * Function, which takes a billing account.  This needs neither: the person
     * asking is already holding a device with a mail account on it, the request
     * arrives from their own address (so replying is one tap), and the message
     * carries the exact address to approve.
     *
     * The trade is honest and worth stating: it depends on them pressing send.  If
     * one never arrives, that is what happened.
     *
     * @return false when the device has no mail app to hand it to.
     */
    fun requestAccess(context: Context, theirEmail: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            // `mailto:` alone, with the address in the URI rather than an EXTRA, is
            // what every mail app understands; `ACTION_SEND` with a text/plain type
            // offers every app on the phone, including ones that cannot send.
            data = Uri.parse("mailto:$EMAIL")
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Access request: ${theirEmail.ifBlank { "unknown address" }}",
            )
            putExtra(Intent.EXTRA_TEXT, body(theirEmail))
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun body(theirEmail: String): String = buildString {
        appendLine("Hello,")
        appendLine()
        appendLine(
            "I would like access to the Lifting Plan Calculator app. " +
                "Please add this address to the approved list:"
        )
        appendLine()
        appendLine(theirEmail.ifBlank { "(my email address)" })
        appendLine()
        appendLine("Thank you.")
    }
}
