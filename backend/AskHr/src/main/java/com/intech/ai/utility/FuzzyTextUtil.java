package com.intech.ai.utility;

import java.util.List;

public class FuzzyTextUtil {

    // simple normalization
    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // Levenshtein distance
    public static int levenshtein(String a, String b) {
        a = normalize(a);
        b = normalize(b);

        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    // fuzzy contains: check if any keyword approximately matches message
    public static boolean fuzzyContains(String message, List<String> phrases, int maxDistance) {
        String msg = normalize(message);

        for (String phrase : phrases) {
            String p = normalize(phrase);

            if (msg.contains(p)) return true;

            // token based fuzzy
            for (String token : msg.split(" ")) {
                if (levenshtein(token, p) <= maxDistance) return true;
            }

            // phrase based fuzzy
            if (levenshtein(msg, p) <= maxDistance) return true;
        }
        return false;
    }
}
