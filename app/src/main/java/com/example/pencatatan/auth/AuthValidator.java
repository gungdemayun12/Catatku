package com.example.pencatatan.auth;

import androidx.annotation.StringRes;

import com.example.pencatatan.R;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict, reusable rules for the auth forms.
 *
 * <p>Everything here is pure logic (no Android views), so the same rules feed the live
 * validation, the final check on submit, and could be unit tested.
 */
public final class AuthValidator {

    /** 8 keeps us above Firebase's own minimum and above trivially guessable passwords. */
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 64;
    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 60;
    public static final int EMAIL_MAX = 254;
    /** A symbol *or* this length also satisfies the "symbol" requirement. */
    public static final int PASSWORD_LONG = 12;
    /** A password must reach this score and tick every required rule to be accepted. */
    public static final int PASSWORD_MIN_SCORE = 3;

    // Local part: dot separated atoms, no leading/trailing/double dots.
    private static final Pattern EMAIL_LOCAL = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$");
    // Domain: dot separated labels, TLD must be at least two letters.
    private static final Pattern EMAIL_DOMAIN = Pattern.compile(
            "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*"
                    + "\\.[A-Za-z]{2,}$");
    private static final Pattern NAME_SHAPE = Pattern.compile(
            "^(?!.*\\s{2})[\\p{L}][\\p{L} .'\\-]*[\\p{L}.]$");
    private static final Pattern HAS_LOWER = Pattern.compile("[a-z]");
    private static final Pattern HAS_UPPER = Pattern.compile("[A-Z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
    private static final Pattern HAS_SYMBOL = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern HAS_SPACE = Pattern.compile("\\s");
    private static final Pattern HAS_EMOJI = Pattern.compile("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}]");
    private static final Pattern REPEATED_CHAR = Pattern.compile("(.)\\1{2,}");

    private static final Set<String> COMMON_PASSWORDS = new HashSet<>(Arrays.asList(
            "123456", "1234567", "12345678", "123456789", "1234567890", "1234512345",
            "password", "password1", "password123", "passw0rd", "p@ssw0rd", "p@ssword",
            "qwerty", "qwerty123", "qwertyuiop", "asdfghjkl", "zxcvbnm", "1q2w3e4r",
            "abc123", "abcd1234", "a1b2c3d4", "iloveyou", "admin", "admin123", "administrator",
            "welcome", "welcome1", "welcome123", "letmein", "monkey", "dragon", "football",
            "baseball", "superman", "batman", "sunshine", "princess", "trustno1", "master",
            "shadow", "michael", "jennifer", "solo", "starwars", "whatever", "freedom",
            "computer", "internet", "samsung", "android", "google", "facebook", "instagram",
            "sayang", "cintaku", "indonesia", "rahasia", "katasandi", "katasandi123",
            "janganlupa", "bismillah", "anaksenja", "nama1234", "rahasia123", "qwerty1234"
    ));

    private AuthValidator() {
    }

    // ------------------------------------------------------------------ name

    @StringRes
    public static int nameError(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) return R.string.name_required;
        if (name.length() > NAME_MAX) return R.string.name_too_long;
        if (name.length() < NAME_MIN) return R.string.name_invalid;
        for (int i = 0; i < name.length(); i++) {
            if (Character.isDigit(name.charAt(i))) return R.string.name_invalid;
        }
        if (!NAME_SHAPE.matcher(name).matches()) return R.string.name_invalid;
        if (REPEATED_CHAR.matcher(name.toLowerCase(Locale.ROOT)).find()) return R.string.name_invalid;
        return 0;
    }

    // ----------------------------------------------------------------- email

    @StringRes
    public static int emailError(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        if (email.isEmpty()) return R.string.email_required;
        if (email.length() > EMAIL_MAX) return R.string.email_too_long;
        if (HAS_SPACE.matcher(email).find()) return R.string.email_invalid;
        int at = email.indexOf('@');
        if (at < 0) return R.string.email_invalid;
        if (email.indexOf('@', at + 1) >= 0) return R.string.email_invalid;
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        if (local.isEmpty()) return R.string.email_invalid;
        if (domain.isEmpty()) return R.string.email_domain_missing;
        if (local.length() > 64) return R.string.email_invalid;
        if (!EMAIL_LOCAL.matcher(local).matches()) return R.string.email_invalid;
        String rawDomain = email.substring(at + 1);
        if (!rawDomain.equals(rawDomain.toLowerCase(Locale.ROOT))) {
            return R.string.email_domain_invalid;
        }
        if (!EMAIL_DOMAIN.matcher(domain).matches()) return R.string.email_domain_invalid;
        return 0;
    }

    // -------------------------------------------------------------- password

    /** A single checklist line on the register form. */
    public static final class Rule {
        @StringRes
        public final int label;
        public final boolean required;
        public final boolean met;

        Rule(@StringRes int label, boolean required, boolean met) {
            this.label = label;
            this.required = required;
            this.met = met;
        }
    }

    /** Score 0–5 plus every rule, so the UI can show a bar and a live checklist. */
    public static final class Strength {
        public final int score;
        public final int points;
        public final boolean hasMinLength;
        public final boolean hasLower;
        public final boolean hasUpper;
        public final boolean hasDigit;
        public final boolean hasSymbolOrLength;
        public final boolean noSpace;
        public final boolean notCommon;
        public final boolean noIdentity;
        public final boolean noRepeats;
        public final boolean noSequence;

        Strength(int score, int points, boolean hasMinLength, boolean hasLower, boolean hasUpper,
                 boolean hasDigit, boolean hasSymbolOrLength, boolean noSpace, boolean notCommon,
                 boolean noIdentity, boolean noRepeats, boolean noSequence) {
            this.score = score;
            this.points = points;
            this.hasMinLength = hasMinLength;
            this.hasLower = hasLower;
            this.hasUpper = hasUpper;
            this.hasDigit = hasDigit;
            this.hasSymbolOrLength = hasSymbolOrLength;
            this.noSpace = noSpace;
            this.notCommon = notCommon;
            this.noIdentity = noIdentity;
            this.noRepeats = noRepeats;
            this.noSequence = noSequence;
        }

        public boolean isPasswordAllowed() {
            return score >= PASSWORD_MIN_SCORE
                    && hasMinLength && hasLower && hasUpper && hasDigit && hasSymbolOrLength
                    && noSpace && notCommon && noIdentity && noRepeats && noSequence;
        }

        /** Stricter than {@link #isPasswordAllowed()}: format rules must pass too. */
        public boolean isFormatValid() {
            return hasMinLength && hasLower && hasUpper && hasDigit && hasSymbolOrLength
                    && noSpace && noRepeats && noSequence;
        }

        @StringRes
        public int labelRes() {
            if (score <= 1) return R.string.strength_very_weak;
            if (score == 2) return R.string.strength_weak;
            if (score == 3) return R.string.strength_fair;
            if (score == 4) return R.string.strength_good;
            return R.string.strength_strong;
        }

        /** Required rules in display order, then the optional "bonus" tips. */
        public Rule[] rules() {
            return new Rule[]{
                    new Rule(R.string.rule_min_length, true, hasMinLength),
                    new Rule(R.string.rule_lower, true, hasLower),
                    new Rule(R.string.rule_upper, true, hasUpper),
                    new Rule(R.string.rule_digit, true, hasDigit),
                    new Rule(R.string.rule_symbol_or_length, true, hasSymbolOrLength),
                    new Rule(R.string.rule_no_space, true, noSpace),
                    new Rule(R.string.rule_not_common, true, notCommon),
                    new Rule(R.string.rule_no_identity, true, noIdentity),
                    new Rule(R.string.rule_no_repeats, false, noRepeats),
                    new Rule(R.string.rule_no_sequence, false, noSequence)
            };
        }
    }

    /**
     * @param password the raw password (never trimmed, spaces matter)
     * @param identity optional name/email so we can reject "budi1234B!" for Budi
     */
    public static Strength strength(String password, String identity) {
        String pwd = password == null ? "" : password;
        int length = pwd.length();

        boolean hasMinLength = length >= PASSWORD_MIN;
        boolean hasLower = HAS_LOWER.matcher(pwd).find();
        boolean hasUpper = HAS_UPPER.matcher(pwd).find();
        boolean hasDigit = HAS_DIGIT.matcher(pwd).find();
        boolean hasSymbol = HAS_SYMBOL.matcher(pwd).find();
        boolean hasSymbolOrLength = hasSymbol || length >= PASSWORD_LONG;
        boolean noSpace = !HAS_SPACE.matcher(pwd).find();
        boolean noEmoji = !HAS_EMOJI.matcher(pwd).find();
        boolean notCommon = !isCommonPassword(pwd);
        boolean noIdentity = !containsIdentity(pwd, identity);
        boolean noRepeats = !REPEATED_CHAR.matcher(pwd).find();
        boolean noSequence = !hasAscendingSequence(pwd);

        int points = 0;
        if (length > 0) points++;
        if (hasMinLength) points++;
        if (length >= 12) points++;
        if (hasLower && hasUpper) points++;
        if (hasDigit) points++;
        if (hasSymbol) points++;
        if (notCommon) points++;
        if (noIdentity) points++;
        if (noSpace && noEmoji) points++;
        if (noRepeats && noSequence) points++;

        int score;
        if (length == 0) score = 0;
        else if (points <= 2) score = 1;
        else if (points <= 4) score = 2;
        else if (points <= 6) score = 3;
        else if (points == 7) score = 4;
        else score = 5;

        // A password that repeats a personal name ("sitisitisiti") must not look decent.
        if (!noIdentity || !notCommon) {
            score = Math.min(score, 1);
        }
        // Collapse an over-generous score when a *required* rule fails; otherwise a five
        // character "Ab1!x" collects bonus points and is shown as "strong".
        if (!hasMinLength) {
            score = Math.min(score, 2);
        }
        if (!(hasLower && hasUpper && hasDigit && hasSymbolOrLength)) {
            score = Math.min(score, 2);
        }

        return new Strength(score, points, hasMinLength, hasLower, hasUpper, hasDigit,
                hasSymbolOrLength, noSpace && noEmoji, notCommon, noIdentity, noRepeats, noSequence);
    }

    @StringRes
    public static int passwordError(String password, String identity) {
        String pwd = password == null ? "" : password;
        if (pwd.isEmpty()) return R.string.password_required;
        if (HAS_EMOJI.matcher(pwd).find()) return R.string.password_has_emoji;
        if (HAS_SPACE.matcher(pwd).find()) return R.string.password_has_space;
        if (pwd.length() > PASSWORD_MAX) return R.string.password_too_long;
        if (pwd.length() < PASSWORD_MIN) return R.string.password_shortest;
        // Dictionary and personal-data hits are reported first: telling someone that
        // "INDONESIA1a" merely needs a symbol hides the real problem, and they would then
        // "fix" it into "INDONESIA1a!", which is just as guessable.
        if (containsIdentity(pwd, identity)) return R.string.password_has_identity;
        if (isCommonPassword(pwd)) return R.string.password_too_common;
        if (!HAS_LOWER.matcher(pwd).find()) return R.string.password_needs_lower;
        if (!HAS_UPPER.matcher(pwd).find()) return R.string.password_needs_upper;
        if (!HAS_DIGIT.matcher(pwd).find()) return R.string.password_needs_digit;
        if (!HAS_SYMBOL.matcher(pwd).find() && pwd.length() < PASSWORD_LONG) {
            return R.string.password_needs_symbol_or_length;
        }
        // Everything the form lists as a requirement is satisfied. Repeats and easy
        // sequences only lower the strength label; they never block the account, so the
        // button can never look dead while the checklist appears complete.
        return 0;
    }

    @StringRes
    public static int confirmError(String password, String confirm) {
        String pwd = password == null ? "" : password;
        String confirmValue = confirm == null ? "" : confirm;
        if (pwd.isEmpty()) return R.string.password_missing;
        if (confirmValue.isEmpty()) return R.string.password_confirm_required;
        return pwd.equals(confirmValue) ? 0 : R.string.password_mismatch;
    }

    // --------------------------------------------------------------- helpers

    private static boolean isCommonPassword(String password) {
        if (password.isEmpty()) return false;
        String normalized = Normalizer.normalize(password, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        // Padding at the ends is not protection: "Rahasia2026!" is still "rahasia". Letters
        // that keep a word readable are only trimmed from the ends, never from the middle.
        String stripped = normalized.replaceAll("^[^a-z]+", "").replaceAll("\\d+$", "")
                .replaceAll("[^a-z]+$", "");
        // Leetspeak folding ("p@ssw0rd" -> "password") so digit swaps do not sneak past.
        String folded = fold(stripped);
        // Finally the alphabetic run at the front, so "indonesia1a" still reads as "indonesia"
        // while ordinary passwords such as "Hujan#2026Pagi" keep their mixed shape.
        String leadingWord = normalized.replaceAll("^([a-z]+).*$", "$1");
        return COMMON_PASSWORDS.contains(normalized)
                || COMMON_PASSWORDS.contains(stripped)
                || COMMON_PASSWORDS.contains(folded)
                || COMMON_PASSWORDS.contains(leadingWord);
    }

    /** Maps the usual digit/symbol stand-ins onto letters: p@ssw0rd -> password. */
    private static String fold(String value) {
        return value
                .replace('0', 'o').replace('1', 'i').replace('3', 'e')
                .replace('4', 'a').replace('5', 's').replace('7', 't')
                .replace('@', 'a').replace('$', 's').replace('!', 'i');
    }

    private static boolean containsIdentity(String password, String identity) {
        if (identity == null) return false;
        String pwd = password.toLowerCase(Locale.ROOT);
        if (pwd.length() < 4) return false;
        String who = identity.toLowerCase(Locale.ROOT).trim();
        if (!who.isEmpty() && pwd.contains(who)) return true;
        // Name words and the email's local part are checked on their own; short tokens are
        // skipped so that a two-letter surname does not reject every password.
        for (String token : who.split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 4 && pwd.contains(token)) return true;
        }
        return false;
    }

    /**
     * True when the password contains a real run of three consecutive letters or digits,
     * e.g. "abc" or "321". The check walks the neighbours so that an arbitrary window such
     * as "qwe" is not treated as an alphabet slice.
     */
    private static boolean hasAscendingSequence(String password) {
        String value = password.toLowerCase(Locale.ROOT);
        int run = 1;
        int step = 0;
        for (int i = 1; i < value.length(); i++) {
            char prev = value.charAt(i - 1);
            char current = value.charAt(i);
            int delta = current - prev;
            boolean sameKind = isLetter(prev) && isLetter(current)
                    || isDigit(prev) && isDigit(current);
            if (sameKind && (delta == 1 || delta == -1)) {
                if (delta == step) {
                    run++;
                } else {
                    step = delta;
                    run = 2;
                }
                if (run >= 3) return true;
            } else {
                step = 0;
                run = 1;
            }
        }
        return false;
    }

    private static boolean isLetter(char value) {
        return value >= 'a' && value <= 'z';
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }
}
