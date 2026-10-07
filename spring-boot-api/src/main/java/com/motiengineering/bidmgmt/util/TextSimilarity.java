package com.motiengineering.bidmgmt.util;

/** Shared fuzzy-matching helper for the importer's organization/user name resolution. */
public final class TextSimilarity {

    private TextSimilarity() {
    }

    /** Normalized Levenshtein similarity in [0,1]; 1 = identical strings. */
    public static double similarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        int distance = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return maxLen == 0 ? 1.0 : 1.0 - ((double) distance / maxLen);
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
