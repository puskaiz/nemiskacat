package hu.deposoft.webshop.config;

import hu.deposoft.webshop.domain.checkout.ShippingFeeCalculator;
import hu.deposoft.webshop.domain.checkout.ShippingMethod;
import hu.deposoft.webshop.domain.checkout.WeightBand;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Shipping fee table from configuration (TASKS T9: "díjtábla konfigurációból").
 * Values: the business-confirmed gross amounts in
 * docs/poc/2026-06-13-shipping-vat-config.md.
 */
@ConfigurationProperties(prefix = "webshop.shipping")
public record ShippingProperties(long freeThresholdGrossHuf, List<Method> methods) {

    public record Method(String code, String name, boolean freeOverThreshold,
                         Long flatGrossHuf, List<Band> bands) {
    }

    public record Band(int maxWeightGrams, long grossHuf) {
    }

    public ShippingFeeCalculator calculator() {
        List<ShippingMethod> domainMethods = methods.stream()
                .map(m -> m.flatGrossHuf() != null
                        ? ShippingMethod.flat(m.code(), m.name(), m.flatGrossHuf())
                        : ShippingMethod.weightBanded(m.code(), m.name(), m.freeOverThreshold(),
                        m.bands().stream().map(b -> new WeightBand(b.maxWeightGrams(), b.grossHuf())).toList()))
                .toList();
        return new ShippingFeeCalculator(domainMethods, freeThresholdGrossHuf);
    }
}
