package hu.deposoft.webshop.domain.checkout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Computes the offered shipping options for a cart (gross total + total weight).
 * Weight-banded methods pick the first band with weight <= max (edges inclusive);
 * a cart heavier than the last band makes the method unavailable. Methods flagged
 * freeOverThreshold cost 0 at gross cart >= threshold.
 */
public class ShippingFeeCalculator {

    private final List<ShippingMethod> methods;
    private final long freeThresholdGrossHuf;

    public ShippingFeeCalculator(List<ShippingMethod> methods, long freeThresholdGrossHuf) {
        this.methods = List.copyOf(methods);
        this.freeThresholdGrossHuf = freeThresholdGrossHuf;
    }

    public List<ShippingOption> options(long cartGrossHuf, int totalWeightGrams) {
        List<ShippingOption> options = new ArrayList<>();
        for (ShippingMethod method : methods) {
            if (method.freeOverThreshold() && cartGrossHuf >= freeThresholdGrossHuf) {
                options.add(new ShippingOption(method.code(), method.name(), 0L, true));
                continue;
            }
            if (method.isFlat()) {
                options.add(new ShippingOption(method.code(), method.name(), method.flatGrossHuf(),
                        method.flatGrossHuf() == 0));
                continue;
            }
            bandFee(method, totalWeightGrams).ifPresent(fee ->
                    options.add(new ShippingOption(method.code(), method.name(), fee, false)));
        }
        return options;
    }

    public Optional<ShippingOption> option(String code, long cartGrossHuf, int totalWeightGrams) {
        return options(cartGrossHuf, totalWeightGrams).stream()
                .filter(o -> o.code().equals(code))
                .findFirst();
    }

    private Optional<Long> bandFee(ShippingMethod method, int weightGrams) {
        return method.bands().stream()
                .filter(b -> weightGrams <= b.maxWeightGrams())
                .findFirst()
                .map(WeightBand::grossHuf);
    }
}
