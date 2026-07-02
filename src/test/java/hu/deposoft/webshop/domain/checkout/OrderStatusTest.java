package hu.deposoft.webshop.domain.checkout;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Order state machine: NEW -> PAID -> PACKING -> SHIPPED -> COMPLETED; CANCELLED from NEW/PAID. */
class OrderStatusTest {

    @Test
    void happyPathTransitionsAreAllowed() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.PACKING)).isTrue();
        assertThat(OrderStatus.PACKING.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
    }

    @Test
    void cancellationOnlyBeforePacking() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PACKING.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void skippingAndBackwardsAreForbidden() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.PACKING)).isFalse();
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.NEW)).isFalse();
        assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.NEW)).isFalse();
    }

    @Test
    void paidAndPackingCanBeRefunded() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        assertThat(OrderStatus.PACKING.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.REFUNDED)).isFalse();
        assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.NEW)).isFalse();
    }

    @Test
    void importStatusesExistAndAreTerminal() {
        for (OrderStatus s : new OrderStatus[]{
                OrderStatus.PROCESSING, OrderStatus.ON_HOLD,
                OrderStatus.FAILED, OrderStatus.AWAITING_SHIPMENT}) {
            for (OrderStatus target : OrderStatus.values()) {
                assertThat(s.canTransitionTo(target))
                        .as("%s should be terminal", s)
                        .isFalse();
            }
        }
    }

    @Test
    void existingNativeTransitionsUnchanged() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.PACKING)).isTrue();
        assertThat(OrderStatus.PACKING.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
    }
}
