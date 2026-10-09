package com.zhafrisbs.liftingplancalculator.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhafrisbs.liftingplancalculator.core.Admission
import com.zhafrisbs.liftingplancalculator.core.AuthUser
import com.zhafrisbs.liftingplancalculator.core.FirebaseAuthGateway
import com.zhafrisbs.liftingplancalculator.core.admission
import com.zhafrisbs.liftingplancalculator.core.describeAuthFailure
import kotlinx.coroutines.launch

/**
 * The sign-in gate.
 *
 * It holds no decisions of its own: whether a user is let in is [admission], which is
 * a pure function of who the provider says they are and what the server said about
 * the list.  This class's whole job is to fetch those two facts and to put the
 * failures into words.
 */
class AuthViewModel(
    private val gateway: FirebaseAuthGateway = FirebaseAuthGateway(),
) : ViewModel() {

    /**
     * What the gate has decided, or null while it is still reading the session.
     *
     * The null lasts as long as it takes Firebase to answer from its own cached
     * session — which is an instant when online and is why a signed-in operator
     * never sees the sign-in form flash up on a cold start.  It is not one of the
     * outcomes [admission] can return, so it is a nullable here rather than a case
     * inside it.
     */
    var admission: Admission? by mutableStateOf(null)
        private set

    /**
     * Who is signed in, for the account screen.
     *
     * Null unless an admitted user is behind the gate: naming someone the app will
     * not open for, and offering them a password to change, would be offering
     * something they cannot have yet.
     */
    var user: AuthUser? by mutableStateOf(null)
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    /** The last failure, in words, or a note that something was sent. */
    var message: String? by mutableStateOf(null)
        private set

    init {
        refresh()
    }

    fun signIn(email: String, password: String) = act { gateway.signIn(email, password) }

    fun signUp(email: String, password: String) = act { gateway.signUp(email, password) }

    /**
     * Exchange a Google ID token for a session.
     *
     * The token itself is fetched by the *screen*, not here, and that split is
     * deliberate: getting it means showing the system account picker, which is a
     * piece of UI.  Keeping it out of this class leaves the class with no `Context`
     * and no Activity, and therefore nothing that needs a device to reason about.
     *
     * No verification step follows — Google has already proved the address, so the
     * token Firebase mints satisfies the rule that would otherwise send the user
     * away to click a link.
     */
    fun signInWithGoogle(idToken: String) = act { gateway.signInWithGoogle(idToken) }

    /**
     * Report a failure that happened before this class was reached.
     *
     * The account picker runs in the screen, so its refusals — a misconfigured
     * build, a device with no Google account — arrive there rather than here.  This
     * is the one way in, so every failure still ends up in the same sentence-shaped
     * place as the rest.
     */
    fun reportFailure(message: String) {
        this.message = message
    }

    /**
     * Re-read the session and ask the server again.
     *
     * This is the button for "I have clicked the link" and for "I have found a
     * signal", which are the only two things that change the answer.
     */
    fun refresh() {
        viewModelScope.launch {
            busy = true
            try {
                // A reload is what refreshes `emailVerified`.  It needs a network,
                // so a failure here is not fatal: the cached user is still who is
                // signed in, and an operator who was allowed once must keep working
                // with no signal.
                val user = runCatching { gateway.reload() }.getOrNull()
                    ?: gateway.currentUser()
                admission = decide(user)
            } catch (failure: Throwable) {
                message = describeAuthFailure(failure)
            } finally {
                busy = false
            }
        }
    }

    fun resendVerification() {
        viewModelScope.launch {
            busy = true
            try {
                gateway.resendVerification()
                message = "Verification email sent. Open the link in it, then press " +
                    "\"I have verified\"."
            } catch (failure: Throwable) {
                message = describeAuthFailure(failure)
            } finally {
                busy = false
            }
        }
    }

    /**
     * Send a reset link to [email].
     *
     * **The success message is deliberately non-committal.** Firebase reports
     * success whether or not the address has an account, so that a sign-in form
     * cannot be used to discover which addresses are registered.  Answering "no
     * account matches that address" would hand that back, and on this app it would
     * also let a stranger find out who is on the approved list one guess at a time.
     *
     * An empty box is a different matter, and is worth a real sentence: there is
     * nothing to send to, and saying so saves them wondering where the mail went.
     */
    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            message = "Type your email address above first, then press Forgot password."
            return
        }
        viewModelScope.launch {
            busy = true
            message = null
            try {
                gateway.sendPasswordReset(email)
                message = "If ${email.trim()} has an account, a reset link is on its " +
                    "way. Check the inbox and the spam folder."
            } catch (failure: Throwable) {
                message = describeAuthFailure(failure)
            } finally {
                busy = false
            }
        }
    }

    /**
     * Change the password, having proved the current one.
     *
     * The success message is set here rather than in the gateway because it is a
     * thing to *say*, and saying it is this class's job.
     */
    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            busy = true
            message = null
            try {
                gateway.changePassword(currentPassword, newPassword)
                message = "Password changed."
            } catch (failure: Throwable) {
                message = describeAuthFailure(failure)
            } finally {
                busy = false
            }
        }
    }

    fun signOut() {
        gateway.signOut()
        message = null
        user = null
        admission = Admission.SignIn
    }

    fun dismissMessage() {
        message = null
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            message = null
            try {
                block()
                admission = decide(gateway.currentUser())
            } catch (failure: Throwable) {
                message = describeAuthFailure(failure)
            } finally {
                busy = false
            }
        }
    }

    /**
     * The two facts, and then the rule.
     *
     * The list is only asked about once the address is **proved**, which is both the
     * right order (a written address is not an owned one) and the only order that
     * works: the security rules refuse an unverified read anyway, so asking early
     * would turn a permission error into "you are not approved".
     */
    private suspend fun decide(who: AuthUser?): Admission {
        val allowlisted: Boolean? = if (who != null && who.emailVerified) {
            runCatching { gateway.isAllowlisted(who.email) }
                .onFailure { message = describeAuthFailure(it) }
                .getOrNull()
        } else {
            false
        }
        val decided = admission(who, allowlisted)
        // The account screen's subject, and only once there is an account to speak
        // of.  Setting it here rather than at each call site is what keeps the two
        // from drifting: `admission` and `user` are two views of one answer.
        this.user = if (decided is Admission.Allowed) who else null
        return decided
    }
}
