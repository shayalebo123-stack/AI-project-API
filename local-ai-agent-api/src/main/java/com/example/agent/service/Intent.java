package com.example.agent.service;

import java.util.regex.Pattern;

/** What the user is asking for. Detection is rule-based (keywords), no ML involved. */
public enum Intent {
    TIME, DATE, GREETING, UNKNOWN;

    // English keywords must be whole words ("time" must not match "sometimes", "date" not "update").
    private static final Pattern TIME_WORD = wordPattern("time");
    private static final Pattern DATE_WORD = wordPattern("date");
    private static final Pattern GREETING_WORD = wordPattern("hello", "hi", "hey", "שלום", "היי");

    // Hebrew attaches prefixes to words (השעה, התאריך), so these use a plain "contains" check.
    private static final String HEBREW_HOUR = "שעה";
    private static final String HEBREW_DATE = "תאריך";

    public static Intent detect(String message) {
        if (TIME_WORD.matcher(message).find() || message.contains(HEBREW_HOUR)) {
            return TIME;
        }
        if (DATE_WORD.matcher(message).find() || message.contains(HEBREW_DATE)) {
            return DATE;
        }
        if (GREETING_WORD.matcher(message).find()) {
            return GREETING;
        }
        return UNKNOWN;
    }

    private static Pattern wordPattern(String... words) {
        String alternatives = String.join("|", words);
        return Pattern.compile(
                "(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
