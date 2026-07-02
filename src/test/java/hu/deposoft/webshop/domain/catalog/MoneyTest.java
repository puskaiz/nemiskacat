package hu.deposoft.webshop.domain.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Money is stored in minor units (CLAUDE.md #6). Hungary has no circulating
 * fillér, so the HUF minor unit equals one forint (scale 0).
 */
class MoneyTest {

    @Test
    void hufFactoryStoresAmountInForint() {
        Money price = Money.huf(3700);

        assertThat(price.amount()).isEqualTo(3700L);
        assertThat(price.currency()).isEqualTo("HUF");
    }

    @Test
    void zeroIsAllowed() {
        assertThat(Money.huf(0).amount()).isZero();
    }

    @Test
    void negativeAmountIsRejected() {
        assertThatThrownBy(() -> Money.huf(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatsWithNbspGroupingAndCurrency() {
        assertThat(Money.huf(3700).formatted()).isEqualTo("3\u00A0700 Ft");
        assertThat(Money.huf(950).formatted()).isEqualTo("950 Ft");
        assertThat(Money.huf(1234567).formatted()).isEqualTo("1\u00A0234\u00A0567 Ft");
    }

    @Test
    void equalAmountsAreEqual() {
        assertThat(Money.huf(1000)).isEqualTo(Money.huf(1000));
    }
}
