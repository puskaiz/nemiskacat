package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import hu.deposoft.webshop.domain.catalog.ProductType;
import hu.deposoft.webshop.domain.catalog.Variant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    private OrderItem line(int qty) {
        return OrderItem.create(null, null, "Workshop", "Alkalom", "WS-1",
                15_000L, 27, qty, InvoiceSource.BILLINGO);
    }

    @Test
    void cancelWholeLineSetsCancelledQuantityToQuantity() {
        OrderItem item = line(3);
        item.cancelWholeLine();
        assertThat(item.getCancelledQuantity()).isEqualTo(3);
    }

    @Test
    void newLineHasZeroCancelledQuantity() {
        assertThat(line(2).getCancelledQuantity()).isZero();
    }

    @Test
    void cancellingAnAlreadyCancelledLineThrows() {
        OrderItem item = line(1);
        item.cancelWholeLine();
        assertThatThrownBy(item::cancelWholeLine).isInstanceOf(IllegalStateException.class);
    }

    private Variant seat(String sku, long priceHuf) {
        Product ws = Product.create(null, "ws-" + sku, "WS", ProductType.WORKSHOP, ProductStatus.PUBLISHED);
        Variant v = Variant.create(ws, null, false);
        v.setSku(sku);
        v.setRegularPriceHuf(priceHuf);
        return v;
    }

    @Test
    void moveToSeatRepointsVariantAndSku() {
        OrderItem item = OrderItem.create(null, seat("WS-A", 15_000L), "WS", "Alkalom",
                "WS-A", 15_000L, 27, 1, InvoiceSource.BILLINGO);
        Variant target = seat("WS-B", 15_000L);

        item.moveToSeat(target);

        assertThat(item.getVariant()).isSameAs(target);
        assertThat(item.getSku()).isEqualTo("WS-B");
        assertThat(item.getUnitGrossHuf()).isEqualTo(15_000L);
    }

    @Test
    void moveToSeatRejectsDifferentPrice() {
        OrderItem item = OrderItem.create(null, seat("WS-A", 15_000L), "WS", "Alkalom",
                "WS-A", 15_000L, 27, 1, InvoiceSource.BILLINGO);
        Variant pricier = seat("WS-B", 18_000L);

        assertThatThrownBy(() -> item.moveToSeat(pricier)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createsOrphanLineWithoutVariant() {
        OrderItem item = OrderItem.create(
                null, null, "Régi termék", "1 kg", "OLD-SKU",
                3700, 27, 2, InvoiceSource.KULCS_SOFT);

        assertThat(item.getVariant()).isNull();
        assertThat(item.getProductName()).isEqualTo("Régi termék");
        assertThat(item.getVariantLabel()).isEqualTo("1 kg");
        assertThat(item.getSku()).isEqualTo("OLD-SKU");
        assertThat(item.getUnitGrossHuf()).isEqualTo(3700);
        assertThat(item.getLineGrossHuf()).isEqualTo(7400);
    }
}
