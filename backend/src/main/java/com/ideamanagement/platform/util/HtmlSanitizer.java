package com.ideamanagement.platform.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class HtmlSanitizer {
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        // Using Safelist.none() strips out all HTML elements, escaping entities.
        return Jsoup.clean(input, Safelist.none());
    }
}

// Sanitizer utility.
// Sanitizer utility.
// Sanitizer utility.
// Sanitizer utility.