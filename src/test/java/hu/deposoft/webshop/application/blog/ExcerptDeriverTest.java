package hu.deposoft.webshop.application.blog;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExcerptDeriverTest {

    private final ExcerptDeriver deriver = new ExcerptDeriver();

    @Test
    void stripsHtmlTagsAndKeepsPlainProse() {
        String html = "<h1>Cím</h1><p>Ez egy <strong>fontos</strong> bekezdés egy <a href=\"https://x.hu\">linkkel</a> és egyéb szöveggel.</p>";
        String out = deriver.deriveFromHtml(html);
        assertThat(out).contains("Ez egy fontos bekezdés egy linkkel és egyéb szöveggel");
        assertThat(out).doesNotContain("<").doesNotContain(">").doesNotContain("href");
    }

    @Test
    void blankOrNullReturnsEmpty() {
        assertThat(deriver.deriveFromHtml(null)).isEmpty();
        assertThat(deriver.deriveFromHtml("   ")).isEmpty();
    }

    @Test
    void leadingImageDoesNotDominate() {
        String html = "<img src=\"/media/up/abc.jpg\" alt=\"\"><p>Ez a tényleges bevezető szöveg a cikkhez.</p>";
        String out = deriver.deriveFromHtml(html);
        assertThat(out).contains("Ez a tényleges bevezető szöveg a cikkhez");
        assertThat(out).doesNotContain("/media/").doesNotContain(".jpg");
    }

    @Test
    void cutsAtFirstSentenceEndBetween30And50Words() {
        StringBuilder words = new StringBuilder("<p>");
        for (int i = 1; i <= 28; i++) words.append("szo").append(i).append(' ');
        words.append("vege."); // 29th word ends a sentence — BEFORE 30, must NOT cut here
        for (int i = 30; i <= 40; i++) words.append(" szo").append(i);
        words.append(" zaras."); // a sentence end at ~word 42 (in [30,50]) -> cut here
        words.append(" szo50 szo51 szo52 tovabbi szoveg ami nem kell.</p>");
        String out = deriver.deriveFromHtml(words.toString());
        assertThat(out).endsWith("zaras.");      // cut at the in-window sentence end
        assertThat(out).doesNotEndWith("…");      // complete sentence -> no ellipsis
        assertThat(out).doesNotContain("szo51");  // nothing past the cut
        assertThat(out).contains("vege.");        // the pre-30 sentence end was NOT a cut point
    }

    @Test
    void cutsAt50WordsWithEllipsisWhenNoSentenceEndInWindow() {
        StringBuilder words = new StringBuilder("<p>");
        for (int i = 1; i <= 70; i++) words.append("szo").append(i).append(' '); // no '.'/'!'/'?'
        words.append("</p>");
        String out = deriver.deriveFromHtml(words.toString().trim());
        assertThat(out).endsWith("…");
        assertThat(out).contains("szo50");
        assertThat(out).doesNotContain("szo51");
    }

    @Test
    void shortBodyReturnedWholeNoEllipsis() {
        assertThat(deriver.deriveFromHtml("<p>Rövid kis bevezető szöveg.</p>")).isEqualTo("Rövid kis bevezető szöveg.");
    }

    @Test
    void naturalEndUnderFiftyWordsNoEllipsis() {
        StringBuilder words = new StringBuilder("<p>");
        for (int i = 1; i <= 40; i++) words.append("szo").append(i).append(' '); // 40 words, no sentence punctuation, text ends
        words.append("</p>");
        String out = deriver.deriveFromHtml(words.toString().trim());
        assertThat(out).doesNotEndWith("…");      // whole text included, nothing truncated
        assertThat(out).contains("szo40");
    }

    @Test
    void cutsAtExactlyThirtiethWord() {
        // 29 plain words, then "harminc." as the 30th word (exactly MIN_WORDS), then more words
        StringBuilder words = new StringBuilder("<p>");
        for (int i = 1; i <= 29; i++) words.append("szo").append(i).append(' ');
        words.append("harminc."); // 30th word — exactly at MIN_WORDS boundary, must cut here
        words.append(" szo31 szo32 szo33 tovabbi szoveg vege.</p>");
        String out = deriver.deriveFromHtml(words.toString());
        assertThat(out).endsWith("harminc.");     // 30th word is the minimum legal cut point
        assertThat(out).doesNotEndWith("…");       // complete sentence -> no ellipsis
        assertThat(out).doesNotContain("szo31");  // nothing past the cut
    }

    @Test
    void cutsAtSentenceEndWithHungarianClosingQuote() {
        // ≥30 leading plain words so that "idezet."" lands in [30,50]
        StringBuilder words = new StringBuilder("<p>");
        for (int i = 1; i <= 34; i++) words.append("szo").append(i).append(' ');
        words.append("idezet.”"); // 35th word — sentence end with right double curly quote
        words.append(" szo36 szo37 szo38 nem kell.</p>");
        String out = deriver.deriveFromHtml(words.toString());
        assertThat(out).endsWith("idezet.”");  // cut at the curly-quote-closed sentence
        assertThat(out).doesNotEndWith("…");          // complete sentence -> no ellipsis
        assertThat(out).doesNotContain("szo36");      // nothing past the cut
    }
}
