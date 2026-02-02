/*
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
}*/

package com.intech.ai.utility;

import java.util.List;

public class FuzzyTextUtil {

    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static int levenshtein(String a, String b) {
        a = normalize(a);
        b = normalize(b);

        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];

        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[m];
    }

    public static boolean fuzzyTokenMatch(String message, String token, int maxDistance) {
        String msg = normalize(message);
        String t = normalize(token);

        for (String mt : msg.split(" ")) {
            if (levenshtein(mt, t) <= maxDistance) return true;
        }
        return false;
    }

    public static boolean fuzzyContainsAny(String message, List<String> keywords, int maxDistance) {
        for (String k : keywords) {
            if (normalize(message).contains(normalize(k))) return true;
            if (fuzzyTokenMatch(message, k, maxDistance)) return true;
        }
        return false;
    }
}

