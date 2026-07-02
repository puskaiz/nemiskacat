package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** Whole-order lookup. For CREDIT_NOTE prefer {@link #findByOrderAndSourceAndTypeAndOrderItemIsNull} (line-scoped notes share this key). */
    Optional<Invoice> findByOrderAndSourceAndType(Order order, InvoiceSource source, InvoiceType type);

    Optional<Invoice> findByOrderItemAndType(OrderItem orderItem, InvoiceType type);

    Optional<Invoice> findByOrderAndSourceAndTypeAndOrderItemIsNull(Order order, InvoiceSource source, InvoiceType type);

    List<Invoice> findByState(InvoiceState state);
}
