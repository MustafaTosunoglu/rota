package com.rota.iam.internal;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

/** Small helpers for deriving tenant slugs from free-text organization names. */
final class Slugs {

    private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_BASE_LENGTH = 40;

    private Slugs() {
    }

    /** Lowercase, ASCII-fold, and hyphenate a name into a URL-safe slug base. */
    static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) {
            slug = "org";
        }
        return slug.length() > MAX_BASE_LENGTH ? slug.substring(0, MAX_BASE_LENGTH) : slug;
    }

    /** A short random suffix to keep generated slugs globally unique without a pre-check. */
    static String randomSuffix() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(SUFFIX_ALPHABET.charAt(RANDOM.nextInt(SUFFIX_ALPHABET.length())));
        }
        return sb.toString();
    }
}
