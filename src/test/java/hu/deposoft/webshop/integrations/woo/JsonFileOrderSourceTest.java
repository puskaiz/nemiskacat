package hu.deposoft.webshop.integrations.woo;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFileOrderSourceTest {

    @Test
    void parsesOrdersFromJsonFile(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("orders.json");
        Files.writeString(file, """
            [{
              "wooOrderId": 1024, "orderKey": "wc_order_x", "wooStatus": "wc-completed",
              "currency": "HUF", "createdAt": "2015-03-01T10:00:00Z", "paidAt": "2015-03-01T10:05:00Z",
              "wpUserId": 3768, "customerName": "Nagy Ágnes", "email": "agi@example.com",
              "phone": "+36301234567", "postcode": "1011", "city": "Budapest",
              "addressLine": "Fő utca 1.", "note": null, "shipMethodName": "Foxpost",
              "shipGrossHuf": 990, "itemsGrossHuf": 7400, "totalGrossHuf": 8390,
              "paid": true, "transactionId": "txn-1",
              "items": [{
                "wooProductId": 55, "wooVariationId": 56, "sku": "ASFPW",
                "productName": "Pure White Chalk Paint", "variantLabel": "1 kg",
                "quantity": 2, "unitGrossHuf": 3700, "taxRatePercent": 27, "lineGrossHuf": 7400
              }]
            }]
            """);

        List<SourceOrder> result = new JsonFileOrderSource(new ObjectMapper(), file).load();

        assertThat(result).hasSize(1);
        SourceOrder o = result.getFirst();
        assertThat(o.wooOrderId()).isEqualTo(1024L);
        assertThat(o.paid()).isTrue();
        assertThat(o.items()).hasSize(1);
        assertThat(o.items().getFirst().sku()).isEqualTo("ASFPW");
        assertThat(o.items().getFirst().lineGrossHuf()).isEqualTo(7400);
    }
}
