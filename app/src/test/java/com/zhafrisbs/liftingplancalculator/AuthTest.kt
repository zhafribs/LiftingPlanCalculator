package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.Admission
import com.zhafrisbs.liftingplancalculator.core.AuthUser
import com.zhafrisbs.liftingplancalculator.core.admission
import com.zhafrisbs.liftingplancalculator.core.allowlistDocumentId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the sign-in gate's decision rule.
 *
 * The rule is a pure function of two facts — who the provider says is asking, and
 * what the server said about the list — and it is tested here rather than on a
 * device because **the order the facts are checked in is the security**. A test
 * that needs a phone to run is a test that will not be run before a release.
 *
 * Nothing here tests Firebase. That half cannot be unit-tested, and it does not
 * decide anything: it fetches these two facts and reports failures in words.
 */
class AuthTest {

    private val allowedEmail = "rigger@example.com"
    private val proved = AuthUser(uid = "uid-1", email = allowedEmail, emailVerified = true)
    private val unproved = proved.copy(emailVerified = false)

    @Test
    fun nobodySignedInIsAskedToSignInWhateverTheListSays() {
        assertEquals(Admission.SignIn, admission(null, allowlisted = true))
        assertEquals(Admission.SignIn, admission(null, allowlisted = false))
        // Even "the server could not be asked" is not a reason to show a stranger
        // anything but the form.
        assertEquals(Admission.SignIn, admission(null, allowlisted = null))
    }

    @Test
    fun anUnprovedAddressIsNeverEvenAskedAboutTheList() {
        // **This is the test that matters.** With email/password sign-up, anyone can
        // register *any* address they like -- including one on the allowlist --
        // without ever receiving mail at it. So verification is decided **first**,
        // and an unproved address never reaches the allowlist question at all.
        //
        // A gate that checked the list first would let a stranger in by typing an
        // address the operator recognises, which is the whole attack.
        assertEquals(Admission.Verify(allowedEmail), admission(unproved, allowlisted = true))
        assertEquals(Admission.Verify(allowedEmail), admission(unproved, allowlisted = false))
        assertEquals(Admission.Verify(allowedEmail), admission(unproved, allowlisted = null))
    }

    @Test
    fun aProvedAddressOnTheListIsLetIn() {
        assertEquals(Admission.Allowed(allowedEmail), admission(proved, allowlisted = true))
    }

    @Test
    fun aProvedAddressNotOnTheListIsTurnedAway() {
        assertEquals(
            Admission.NotOnTheList(allowedEmail),
            admission(proved, allowlisted = false),
        )
    }

    @Test
    fun failingToAskIsNotTheSameAsBeingTurnedAway() {
        // "You are not approved" and "I could not find out" send the operator to
        // different people. Collapsing them would tell a permitted rigger to go and
        // request access they already have.
        assertEquals(
            Admission.CouldNotCheck(allowedEmail),
            admission(proved, allowlisted = null),
        )
    }

    @Test
    fun theStoredAddressIsTheLowercasedOne() {
        // The rule compares the lowercased token email against the document id, so
        // the app has to write the same shape -- otherwise an operator who types
        // their address with a capital letter is refused by a list they are on.
        assertEquals("rigger@example.com", allowlistDocumentId("Rigger@Example.COM"))
        assertEquals("rigger@example.com", allowlistDocumentId("  rigger@example.com  "))
        assertEquals(allowedEmail, allowlistDocumentId(allowedEmail))
    }
}
