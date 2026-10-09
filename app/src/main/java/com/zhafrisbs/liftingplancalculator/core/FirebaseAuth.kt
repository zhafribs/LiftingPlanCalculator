package com.zhafrisbs.liftingplancalculator.core

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** A failure with a sentence written for the person holding the phone. */
class AuthFailure(message: String) : Exception(message)

/**
 * The Firebase half of the gate: sign in, sign up, prove the address, and ask the
 * server whether the address is on the list.
 *
 * **The allowlist read is the point of the whole file.** `isAllowlisted` asks
 * Firestore for `allowed/{email}`, and whether that document comes back is decided
 * by the security rules rather than by anything here — so a patched APK cannot make
 * the answer be yes. Everything else in this class is plumbing around that one
 * question.
 */
class FirebaseAuthGateway(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    /** The signed-in user the SDK already has, from its own persisted session. */
    fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

    suspend fun signIn(email: String, password: String) {
        guardEmail(email)
        guardPassword(password)
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    /**
     * Create an account, and immediately ask for the address to be proved.
     *
     * The verification mail is sent here rather than left to the caller because an
     * account without it can do nothing: the rules refuse an unproved address, so
     * an unverified sign-up is a dead end until the mail arrives.
     */
    suspend fun signUp(email: String, password: String) {
        guardEmail(email)
        guardPassword(password)
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
        auth.currentUser?.sendEmailVerification()?.await()
    }

    /**
     * Exchange a Google ID token for a Firebase session.
     *
     * No verification step follows, and that is not an omission: Google has already
     * proved the address, so `email_verified` is true in the token Firebase mints
     * from this sign-in.  It is the same fact the rules require — arrived at without
     * the user having to leave the app.
     */
    suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    /** Send the verification mail again, for someone who lost the first one. */
    suspend fun resendVerification() {
        val user = auth.currentUser
            ?: throw AuthFailure("Nobody is signed in.")
        user.sendEmailVerification().await()
    }

    /**
     * Re-read the user from the server, for the moment they come back having clicked
     * the link.
     *
     * `reload()` is what refreshes `emailVerified`; without it the SDK keeps serving
     * the user it cached at sign-in, and someone who has just proved their address
     * would be told to prove it again forever.
     */
    suspend fun reload(): AuthUser? {
        val user = auth.currentUser ?: return null
        user.reload().await()
        // **The token, not just the profile.**
        //
        // `reload()` refreshes the user *record*, and `isEmailVerified` comes from
        // it — so the app immediately believes the address is proved.  But the
        // Firestore rules read the **ID token**, and a token minted before
        // verification still carries `email_verified: false` for up to an hour.
        //
        // The result was a loop with no way out: the app asked the server about the
        // allowlist, the server refused because of a stale claim in a token the app
        // had not refreshed, and the refusal surfaced as "could not check" — so the
        // only thing that worked was signing out and back in, which mints a new
        // token.  Forcing a refresh here is what makes "I have verified" actually
        // mean it.
        user.getIdToken(true).await()
        return auth.currentUser?.toAuthUser()
    }

    /**
     * Whether the server says this address is on the list.
     *
     * `Source.DEFAULT` rather than `Source.SERVER`, and that is the safety decision
     * in this file: it reads the server when there is one and Firestore's **local
     * cache** when there is not.  A user who has been allowed once therefore keeps
     * working with no signal — which matters here, because the alternative is a
     * rigger on site, holding a phone with no bars, locked out of a plan they are
     * entitled to see.  A user who has *never* been allowed still needs a network,
     * which is the honest place for the line to fall.
     */
    suspend fun isAllowlisted(email: String): Boolean {
        val document = firestore.collection(ALLOWED).document(allowlistDocumentId(email))
        return try {
            document.get().await()?.exists() == true
        } catch (failure: FirebaseFirestoreException) {
            // A refusal here is not "you are not on the list" — it is "the server
            // would not answer", and the two lead the operator to different actions.
            throw AuthFailure(
                "Could not check the approved list: ${failure.message ?: "no reason given"}. " +
                    "If you are offline, connect once and try again."
            )
        }
    }

    /**
     * Send a password-reset email.
     *
     * Note what this call does **not** tell the caller: whether the address has an
     * account.  Firebase reports success either way, and that is deliberate on its
     * part, so the app must not undo it by inventing a distinction -- see the
     * message the ViewModel sets afterwards.
     */
    suspend fun sendPasswordReset(email: String) {
        guardEmail(email)
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /**
     * Change the password, having first proved the current one.
     *
     * **The old password is asked for even though Firebase does not require it.**
     * `updatePassword` only demands a *recent* sign-in, so on a phone left unlocked
     * it would let whoever is holding it take the account over in two taps.  Asking
     * for the current password is what makes this "the owner changed it" rather than
     * "the last person to pick the phone up changed it".
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser ?: throw AuthFailure("Nobody is signed in.")
        val email = user.email ?: throw AuthFailure("This account has no email address.")
        guardPassword(newPassword)
        if (newPassword == currentPassword) {
            throw AuthFailure("The new password is the same as the current one.")
        }

        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        try {
            user.reauthenticate(credential).await()
        } catch (wrong: FirebaseAuthInvalidCredentialsException) {
            // Named for what they got wrong, rather than reusing the sign-in
            // wording about a password "not matching an account" -- they are
            // already in the account, and one field on this form is wrong.
            throw AuthFailure("That is not your current password.")
        }
        user.updatePassword(newPassword).await()
    }

    fun signOut() {
        auth.signOut()
    }

    private fun guardEmail(email: String) {
        if (email.isBlank() || !email.contains('@')) {
            throw AuthFailure("That does not look like an email address.")
        }
    }

    private fun guardPassword(password: String) {
        if (password.length < MIN_PASSWORD) {
            throw AuthFailure("A password needs at least $MIN_PASSWORD characters.")
        }
    }

    private companion object {
        /** The collection the allowlist lives in. One document per address. */
        const val ALLOWED = "allowed"
        const val MIN_PASSWORD = 6
    }
}

private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
    uid = uid,
    email = email.orEmpty(),
    emailVerified = isEmailVerified,
    // Read from the provider list rather than remembered at sign-in, so it is
    // right however the session was established -- including one restored from
    // disk on a cold start, which never went through this app's sign-in at all.
    hasPassword = providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID },
)

/**
 * Suspend until a Play services `Task` finishes, and hand back its result.
 *
 * Written here rather than taken from `kotlinx-coroutines-play-services`, which
 * would be a whole artifact for six lines — and this app's dependency list is short
 * enough to be worth keeping that way.
 *
 * The result is **nullable** because `Task<Void>` has none: a sign-in succeeds with
 * no value at all, and only the Firestore read has something to give back.  A
 * listener rather than a polling loop, so cancellation is honest —
 * `suspendCancellableCoroutine` drops the continuation when the scope ends and the
 * Firebase call simply finishes into nothing.
 */
private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { finished ->
        val failure = finished.exception
        if (failure == null) {
            cont.resume(finished.result)
        } else {
            cont.resumeWithException(failure)
        }
    }
}

/**
 * Turn Firebase's exception types into sentences.
 *
 * Firebase reports *every* wrong password, unknown address and disabled account as
 * one of two exception types with a developer-facing `message` — "The password is
 * invalid or the user does not have a password." — which is not a thing to put in
 * front of a rigger.  These are the same outcomes in words that say what to do.
 */
fun describeAuthFailure(failure: Throwable): String = when (failure) {
    is AuthFailure -> failure.message ?: "That did not work."
    is FirebaseAuthInvalidUserException -> "No account matches that address."
    is FirebaseAuthUserCollisionException -> "An account already exists for that address."
    is FirebaseAuthWeakPasswordException -> "That password is too short, or too easy to guess."
    // Ordered after the specific two above, because both of those *are* credential
    // failures and would otherwise be answered with "check your password".
    is FirebaseAuthInvalidCredentialsException ->
        "That address and password do not match an account. Check both, or create one."
    else -> failure.message ?: "That did not work."
}
