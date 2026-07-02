package hu.deposoft.webshop.application.blog;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/**
 * Derives a short plain-text excerpt from an HTML body — used as the list-card
 * description and article meta/JSON-LD description when a post has no manual excerpt
 * (CLAUDE.md #9 blog). Mirrors WordPress's auto-excerpt behaviour.
 */
@Component
public class ExcerptDeriver {

    private static final int MIN_WORDS = 30;
    private static final int MAX_WORDS = 50;
    private static final int MAX_CHARS = 500; // safety cap against a pathological single token

    public String deriveFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = Jsoup.parse(html).text();
        if (text == null) {
            return "";
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return "";
        }

        return excerptFromText(text);
    }

    private String excerptFromText(String text) {
        String[] words = text.split(" ");

        // \u2264 MIN_WORDS: return whole text.
        if (words.length <= MIN_WORDS) {
            return capChars(text, false);
        }

        StringBuilder out = new StringBuilder();
        boolean sentenceEnd = false;
        int taken = Math.min(words.length, MAX_WORDS);
        for (int i = 0; i < taken; i++) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(words[i]);
            // a sentence ending only counts once we have at least MIN_WORDS words
            if (i + 1 >= MIN_WORDS && endsSentence(words[i])) {
                sentenceEnd = true;
                break;
            }
        }
        // ellipsis only when we truncated mid-text without landing on a sentence end
        boolean truncated = !sentenceEnd && words.length > MAX_WORDS;
        return capChars(out.toString(), truncated);
    }

    /** True if the word ends a sentence (., !, ?, … — trailing closing quote/paren allowed). */
    private static boolean endsSentence(String word) {
        String w = word.replaceAll("[\"’’’)\\]”’»«]+$", "");
        if (w.isEmpty()) {
            return false;
        }
        char c = w.charAt(w.length() - 1);
        return c == '.' || c == '!' || c == '?' || c == '…';
    }


    /** Append "…" when truncated; enforce the safety char cap. */
    private static String capChars(String s, boolean truncated) {
        String result = s;
        if (result.length() > MAX_CHARS) {
            result = result.substring(0, MAX_CHARS);
            int sp = result.lastIndexOf(' ');
            if (sp > 0) {
                result = result.substring(0, sp);
            }
            return result + "…";
        }
        return truncated ? result + "…" : result;
    }
}
