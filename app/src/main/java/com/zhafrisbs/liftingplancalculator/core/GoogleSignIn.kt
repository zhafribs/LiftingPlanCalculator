package com.zhafrisbs.liftingplancalculator.core

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * A Google ID token, from the device's own account picker.
 *
 * This is not a second sign-in system.  It is a second way to *get* one: the token
 * goes to Firebase, which exchanges it for the same kind of session the email form
 * produces — so everything after this point, including the allowlist, is unchanged.
 *
 * **Why it is worth having at all**, beyond convenience: a Google address arrives
 * already verified by Google.  With email/password, anyone can register any address
 * they like, which is why the rules insist on `email_verified` and why a password
 * user has to go and click a link.  A Google user is past that on the first tap.
 *
 * [serverClientId] is the project's **web** client id, read from the resource the
 * Google services plugin generates out of `google-services.json`.  Reading it rather
 * than pasting it in means there is one place the project's identity lives — and a
 * web client id pasted into source is a thing that goes stale silently when the
 * project is recreated.
 *
 * @return the token, or **null** if the operator backed out, which is not a failure.
 * @throws AuthFailure when Google refused for a reason worth telling them about.
 */
suspend fun googleIdToken(context: Context, serverClientId: String): String? {
    val option = GetGoogleIdOption.Builder()
        // `false`, so the picker is always offered.  `true` would prefer an account
        // already authorised for this app and skip the sheet when it finds one —
        // which is exactly wrong on a shared site phone, where the previous user's
        // account would be signed straight back in.
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(serverClientId)
        .setAutoSelectEnabled(false)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()

    return try {
        val result = CredentialManager.create(context).getCredential(context, request)
        GoogleIdTokenCredential.createFrom(result.credential.data).idToken
    } catch (_: GetCredentialCancellationException) {
        // The operator closed the sheet. Saying nothing is the correct response:
        // "you cancelled" is not a problem they need told about.
        null
    } catch (failure: GetCredentialException) {
        throw AuthFailure(describeGoogleFailure(failure))
    }
}

/**
 * Turn a Credential Manager failure into something the operator can act on.
 *
 * **The configuration case is the one that earns this function.** When the app's
 * signing certificate has not been registered in the Firebase project, Google
 * answers with a bare `DEVELOPER_ERROR` — a sentence that means nothing to a rigger
 * and not much more to most developers, and which is indistinguishable from "it is
 * broken" unless somebody says what to do.  Naming it here turns a dead button into
 * an instruction.
 */
private fun describeGoogleFailure(failure: GetCredentialException): String = when (failure) {
    is GetCredentialProviderConfigurationException ->
        "Google sign-in is not set up for this build yet. The app's signing " +
            "certificate has to be registered in the Firebase project before " +
            "Google will issue a token. Use email and password for now."

    is NoCredentialException ->
        "No Google account was found on this device. Add one in Android Settings, " +
            "or sign in with an email address."

    is GetCredentialInterruptedException ->
        "Google sign-in was interrupted before it finished. Try again."

    is GetCredentialUnsupportedException ->
        "This device cannot sign in with Google. Use an email address instead."

    else -> failure.message ?: "Google sign-in did not work."
}
