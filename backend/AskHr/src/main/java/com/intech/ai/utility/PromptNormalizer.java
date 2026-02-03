package com.intech.ai.utility;

public class PromptNormalizer {

    public static String normalize(String input) {
        return input
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
