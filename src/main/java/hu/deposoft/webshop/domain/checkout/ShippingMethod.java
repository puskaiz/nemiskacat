package hu.deposoft.webshop.domain.checkout;

import java.util.List;

/**
 * A configured shipping method: either flat-fee or weight-banded, optionally free
 * above the cart-total threshold (freeOverThreshold).
 */
public record ShippingMethod(String code, String name, boolean freeOverThreshold,
                             List<WeightBand> bands, Long flatGrossHuf) {

    public static ShippingMethod weightBanded(String code, String name,
                                              boolean freeOverThreshold, List<WeightBand> bands) {
        return new ShippingMethod(code, name, freeOverThreshold, bands, null);
    }

    public static ShippingMethod flat(String code, String name, long grossHuf) {
        return new ShippingMethod(code, name, false, List.of(), grossHuf);
    }

    boolean isFlat() {
        return flatGrossHuf != null;
    }
}
