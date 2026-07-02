package hu.deposoft.webshop.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SlugsTest {
    @Test void stripsDiacriticsAndLowercases() { assertThat(Slugs.slugify("Piros árnyalat")).isEqualTo("piros-arnyalat"); }
    @Test void handlesHungarianLongVowels()    { assertThat(Slugs.slugify("Zöld/Kék")).isEqualTo("zold-kek"); }
    @Test void doubleAcute()                    { assertThat(Slugs.slugify("Őzbarna űr")).isEqualTo("ozbarna-ur"); }
    @Test void collapsesAndTrims()              { assertThat(Slugs.slugify("  Extra   Nagy!!  ")).isEqualTo("extra-nagy"); }
    @Test void keepsDigits()                    { assertThat(Slugs.slugify("250 ml")).isEqualTo("250-ml"); }
    @Test void emptyForSymbolsOnly()            { assertThat(Slugs.slugify("!!!")).isEqualTo(""); }
    @Test void nullSafe()                       { assertThat(Slugs.slugify(null)).isEqualTo(""); }
}
