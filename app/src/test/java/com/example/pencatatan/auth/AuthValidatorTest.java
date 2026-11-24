package com.example.pencatatan.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.example.pencatatan.R;

import org.junit.Test;

/** Pure-logic checks for the strict auth rules used by both forms. */
public class AuthValidatorTest {

    private static final String IDENTITY = "Budi Santoso budi.santoso@email.com";

    // ------------------------------------------------------------- password

    @Test
    public void strongPassword_isAccepted() {
        String pwd = "Hujan#2026Pagi";
        assertEquals(0, AuthValidator.passwordError(pwd, IDENTITY));
        AuthValidator.Strength strength = AuthValidator.strength(pwd, IDENTITY);
        assertTrue(strength.isPasswordAllowed());
        assertTrue("strong password should score high", strength.score >= 4);
    }

    @Test
    public void tooShort_isRejected() {
        // "Hujan#2" has every character class, so only the length rule can reject it.
        assertEquals(R.string.password_shortest,
                AuthValidator.passwordError("Hujan#2", IDENTITY));
        assertEquals(R.string.password_shortest,
                AuthValidator.passwordError("Hujan#2", null));
    }

    @Test
    public void missingCharacterClasses_areRejected() {
        assertEquals(R.string.password_needs_lower,
                AuthValidator.passwordError("HANYA#BESAR12", IDENTITY));
        assertEquals(R.string.password_needs_upper,
                AuthValidator.passwordError("hanya#kecil12", IDENTITY));
        assertEquals(R.string.password_needs_digit,
                AuthValidator.passwordError("Tanpa#AngkaSatu", IDENTITY));
    }

    @Test
    public void twelveCharacters_doNotNeedSymbol() {
        assertEquals(0, AuthValidator.passwordError("TanpaSimbol12", IDENTITY));
    }

    @Test
    public void longPassword_doesNotNeedSymbol() {
        assertEquals(0, AuthValidator.passwordError("PanjangSekali2026", IDENTITY));
    }

    @Test
    public void spacesAndEmoji_areRejected() {
        assertEquals(R.string.password_has_space,
                AuthValidator.passwordError("Hujan #2026Pagi", IDENTITY));
        assertEquals(R.string.password_has_emoji,
                AuthValidator.passwordError("Hujan#2026Pagi😀", IDENTITY));
    }

    @Test
    public void commonPasswords_areRejected() {
        assertEquals(R.string.password_too_common,
                AuthValidator.passwordError("Rahasia2026!", IDENTITY));
        assertEquals(R.string.password_too_common,
                AuthValidator.passwordError("INDONESIA1a", IDENTITY));
    }

    @Test
    public void leakedPasswords_areCaughtByPattern() {
        // Trailing digits and a bang do not hide a dictionary word, so these are named for
        // what they are instead of being sent back for another symbol.
        assertEquals(R.string.password_too_common,
                AuthValidator.passwordError("Password123!", IDENTITY));
        assertEquals(R.string.password_too_common,
                AuthValidator.passwordError("Qwerty!2026", IDENTITY));
    }

    @Test
    public void identityInsidePassword_isRejected() {
        assertEquals(R.string.password_has_identity,
                AuthValidator.passwordError("BudiSantoso#26", IDENTITY));
        assertEquals(R.string.password_has_identity,
                AuthValidator.passwordError("Xyz!budi.santoso26", IDENTITY));
        assertEquals(R.string.password_has_identity,
                AuthValidator.passwordError("SitiNurAini#26", "Siti Nur'aini siti@email.com"));
        assertEquals(R.string.password_has_identity,
                AuthValidator.passwordError("Aman#Santoso26", IDENTITY));
    }

    @Test
    public void repeatsAndSequences_neverBlockTheAccount() {
        // Repeats and easy sequences are *advisory*: they show on the checklist and
        // drag the strength label down, but they must not reject an otherwise valid
        // password, or the Save button would look dead while the checklist reads
        // complete. See AuthValidator.passwordError.
        assertEquals(0, AuthValidator.passwordError("Hujan#aaa2026", IDENTITY));
        assertEquals(0, AuthValidator.passwordError("Hujan#1234Pagi", IDENTITY));
        assertEquals(0, AuthValidator.passwordError("Hujan#bcdePagi1", IDENTITY));
        assertEquals(0, AuthValidator.passwordError("Hujan#x7QmPagi", IDENTITY));

        // They are still detected and reported to the user...
        assertFalse(AuthValidator.strength("Hujan#aaa2026", IDENTITY).noRepeats);
        assertFalse(AuthValidator.strength("Hujan#1234Pagi", IDENTITY).noSequence);
        assertFalse(AuthValidator.strength("Hujan#bcdePagi1", IDENTITY).noSequence);
        assertTrue(AuthValidator.strength("Hujan#x7QmPagi", IDENTITY).noRepeats);
        assertTrue(AuthValidator.strength("Hujan#x7QmPagi", IDENTITY).noSequence);
    }

    @Test
    public void shortButWellFormedPassword_isRejected() {
        assertEquals(R.string.password_shortest,
                AuthValidator.passwordError("Hujan#2", null));
        // The strength meter must never call it "good" either.
        assertTrue(AuthValidator.strength("Hujan#2", null).score <= 2);
        assertTrue(AuthValidator.strength("Ab1!x", null).score <= 2);
    }

    @Test
    public void weakPassword_isNeverReportedAsStrong() {
        AuthValidator.Strength strength = AuthValidator.strength("password1", IDENTITY);
        assertTrue(strength.score <= 1);
        assertNotEquals(R.string.strength_strong, strength.labelRes());
    }

    @Test
    public void strengthGrowsWithBetterPasswords() {
        int weak = AuthValidator.strength("abcdefgh", null).score;
        int better = AuthValidator.strength("HujanPagi", null).score;
        int best = AuthValidator.strength("Hujan#2026Pagi", null).score;
        // Only lowercase: two required rules fail, so the bar must not look comforting.
        assertEquals(2, weak);
        // Lowercase + uppercase + length, but still no digit/symbol.
        assertEquals(2, better);
        assertEquals(5, best);
        assertTrue(weak <= better && better < best);
    }

    // -------------------------------------------------------------- confirm

    @Test
    public void confirmMustMatch() {
        assertEquals(R.string.password_confirm_required,
                AuthValidator.confirmError("Hujan#2026Pagi", ""));
        assertEquals(R.string.password_mismatch,
                AuthValidator.confirmError("Hujan#2026Pagi", "Hujan#2026Malam"));
        assertEquals(0, AuthValidator.confirmError("Hujan#2026Pagi", "Hujan#2026Pagi"));
        assertEquals(R.string.password_missing, AuthValidator.confirmError("", "abc"));
    }

    // ---------------------------------------------------------------- email

    @Test
    public void validEmails_pass() {
        assertEquals(0, AuthValidator.emailError("budi@email.com"));
        assertEquals(0, AuthValidator.emailError(" budi.santoso+catatku@gmail.co.id "));
    }

    @Test
    public void brokenEmails_areRejected() {
        assertEquals(R.string.email_required, AuthValidator.emailError(""));
        assertEquals(R.string.email_invalid, AuthValidator.emailError("budi"));
        assertEquals(R.string.email_invalid, AuthValidator.emailError("budi@@email.com"));
        assertEquals(R.string.email_invalid, AuthValidator.emailError(".budi@email.com"));
        assertEquals(R.string.email_domain_missing, AuthValidator.emailError("budi@"));
        assertEquals(R.string.email_domain_invalid, AuthValidator.emailError("budi@email"));
        assertEquals(R.string.email_domain_invalid, AuthValidator.emailError("budi@email.c"));
        assertEquals(R.string.email_domain_invalid, AuthValidator.emailError("budi@Email.com"));
    }

    // ----------------------------------------------------------------- name

    @Test
    public void validNames_pass() {
        assertEquals(0, AuthValidator.nameError("Budi Santoso"));
        assertEquals(0, AuthValidator.nameError("  Siti Nur'aini  "));
        assertEquals(0, AuthValidator.nameError("Ni Made Ayu"));
        assertEquals(0, AuthValidator.nameError("J. R. R. Tolkien"));
    }

    @Test
    public void brokenNames_areRejected() {
        assertEquals(R.string.name_required, AuthValidator.nameError("   "));
        assertEquals(R.string.name_invalid, AuthValidator.nameError("A"));
        assertEquals(R.string.name_invalid, AuthValidator.nameError("Budi123"));
        assertEquals(R.string.name_invalid, AuthValidator.nameError("Budi  Santoso"));
        assertEquals(R.string.name_invalid, AuthValidator.nameError("@#$%"));
        assertEquals(R.string.name_invalid, AuthValidator.nameError("Budiii"));
    }
}
