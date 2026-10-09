package com.zhafrisbs.liftingplancalculator.core

/**
 * Who the sign-in provider says is asking, and whether they may use the app.
 *
 * The rule lives here rather than in the screen for the reason every rule in this
 * project does: a rule written inside a composable can only be read, and a rule
 * that can only be read can only be hoped for.  [admission] is a pure function of
 * two facts, so the order they are checked in is a thing that can be asserted
 * rather than a thing that happens to be true today.
 */

/** A signed-in user, as far as the app cares. */
data class AuthUser(
    val uid: String,
    val email: String,
    val emailVerified: Boolean,
    /**
     * Whether this account signs in with a password the user can change here.
     *
     * False for a Google sign-in, and that is not a gap: Google owns that password
     * and changes it on its own account page.  Offering a form would be offering to
     * change something this app cannot change.
     */
    val hasPassword: Boolean = false,
)

/** Whether the app may be used, and if not, what to say about it. */
sealed interface Admission {

    /** Nobody is signed in. */
    data object SignIn : Admission

    /**
     * Signed in, but the address is not proved to belong to them.
     *
     * See [admission] for why this comes before the allowlist.
     */
    data class Verify(val email: String) : Admission

    /**
     * The server could not be asked, so nothing is decided.
     *
     * **Deliberately not the same as [NotOnTheList].** "You are not approved" and
     * "I could not find out" lead the operator to different actions -- ask for
     * access, versus find a signal -- and collapsing them would tell a permitted
     * user to go and request permission they already have.
     */
    data class CouldNotCheck(val email: String) : Admission

    /** Signed in and proved, but not on the list. */
    data class NotOnTheList(val email: String) : Admission

    /** In. */
    data class Allowed(val email: String) : Admission
}

/**
 * What to do about a user, from the two facts that decide it.
 *
 * **The order is the security.** An address being *written* is not an address being
 * *owned*: with email/password sign-up anyone can register any address they like,
 * including one on your list, without ever receiving mail at it.  So verification is
 * checked **first**, and an unverified user never reaches the allowlist question at
 * all — asking it would be asking whether a stranger typed a name you recognise.
 *
 * `allowlisted` is what the **server** said, and that is load-bearing too.  This
 * function is happy to be told "yes"; it is not the thing enforcing anything.  The
 * enforcement is the Firestore rule, because anything decided in this process can be
 * removed from the APK by anyone willing to unzip it.  What this function buys is
 * that the app and the rule agree, and that the *reason* a user was turned away is
 * the right one to show them.
 *
 * A null [allowlisted] is "the server could not be asked", which is a third answer
 * rather than a no — see [Admission.CouldNotCheck].
 */
fun admission(user: AuthUser?, allowlisted: Boolean?): Admission = when {
    user == null -> Admission.SignIn
    !user.emailVerified -> Admission.Verify(user.email)
    allowlisted == null -> Admission.CouldNotCheck(user.email)
    !allowlisted -> Admission.NotOnTheList(user.email)
    else -> Admission.Allowed(user.email)
}

/**
 * The document id an address is stored under.
 *
 * Firestore document ids cannot contain a slash, and a forward slash *is* legal in
 * an email's local part, so an address containing one cannot be stored as itself.
 * There is no clever encoding here on purpose: an address this affects is one the
 * operator can add some other way, and inventing a scheme would mean two places
 * that have to agree about it.  The app lowercases for the same reason the rule
 * does — so the stored id and the token's address compare equal.
 */
fun allowlistDocumentId(email: String): String = email.trim().lowercase()
