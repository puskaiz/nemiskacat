package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import hu.deposoft.webshop.integrations.woo.SourceOrderItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OrderImporterResolveTest {

    @Mock VariantRepository variants;
    @Mock OrderRepository orders;
    @Mock PaymentRepository payments;
    @Mock Variant byVariation;
    @Mock Variant byProduct;
    @Mock Variant bySku;

    @Test
    void prefersVariationIdOverProductIdAndSku() {
        OrderImporter importer = new OrderImporter(variants, orders, payments, null);
        lenient().when(variants.findByExternalId(56L)).thenReturn(Optional.of(byVariation));
        lenient().when(variants.findBySku("ASFPW")).thenReturn(Optional.of(bySku));

        Variant resolved = importer.resolveVariant(
                new SourceOrderItem(55L, 56L, "ASFPW", "p", "v", 1, 3700, 27, 3700));

        assertThat(resolved).isSameAs(byVariation);
    }

    @Test
    void prefersProductIdOverSku() {
        OrderImporter importer = new OrderImporter(variants, orders, payments, null);
        lenient().when(variants.findByExternalId(56L)).thenReturn(Optional.empty());
        lenient().when(variants.findByExternalId(55L)).thenReturn(Optional.of(byProduct));

        Variant resolved = importer.resolveVariant(
                new SourceOrderItem(55L, 56L, "ASFPW", "p", "v", 1, 3700, 27, 3700));

        assertThat(resolved).isSameAs(byProduct);
    }

    @Test
    void fallsBackToSkuThenNull() {
        OrderImporter importer = new OrderImporter(variants, orders, payments, null);
        lenient().when(variants.findByExternalId(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.empty());
        lenient().when(variants.findBySku("ASFPW")).thenReturn(Optional.of(bySku));

        assertThat(importer.resolveVariant(
                new SourceOrderItem(55L, 56L, "ASFPW", "p", "v", 1, 3700, 27, 3700)))
                .isSameAs(bySku);
        assertThat(importer.resolveVariant(
                new SourceOrderItem(55L, 56L, "GONE", "p", "v", 1, 3700, 27, 3700)))
                .isNull();
    }
}
