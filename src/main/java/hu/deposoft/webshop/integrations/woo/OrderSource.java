package hu.deposoft.webshop.integrations.woo;

import java.util.List;

/** Port for a source of historical orders (one implementation per source). */
public interface OrderSource {
    List<SourceOrder> load();
}
