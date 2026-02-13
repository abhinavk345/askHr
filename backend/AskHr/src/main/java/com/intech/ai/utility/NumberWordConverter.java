package com.intech.ai.utility;

import java.util.Map;

public class NumberWordConverter {

    private static final Map<String, Integer> WORD_TO_NUMBER = Map.ofEntries(
            Map.entry("zero", 0),
            Map.entry("one", 1),
            Map.entry("two", 2),
            Map.entry("three", 3),
            Map.entry("four", 4),
            Map.entry("five", 5),
            Map.entry("six", 6),
            Map.entry("seven", 7),
            Map.entry("eight", 8),
            Map.entry("nine", 9),
            Map.entry("ten", 10)
    );

    public static Integer convert(String input) {
        if (input == null) return null;

        input = input.toLowerCase().trim();

        // Case 1: numeric
        if (input.matches("\\d+")) {
            return Integer.parseInt(input);
        }

        // Case 2: word
        return WORD_TO_NUMBER.get(input);
    }
}