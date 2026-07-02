package hu.deposoft.webshop.domain.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** Lock the line row for the duration of a money operation (line cancel) — serializes concurrent cancels. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from OrderItem i where i.id = :id")
    Optional<OrderItem> findByIdForUpdate(@Param("id") Long id);
}
