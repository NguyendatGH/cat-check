package com.catcheck.shop.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusPolicyTest {

    @Test
    void codFlow() {
        assertThat(OrderStatusPolicy.adminNextStatuses("COD", "PENDING_PAYMENT")).containsExactly("PACKING", "CANCELLED");
        assertThat(OrderStatusPolicy.adminNextStatuses("COD", "PACKING")).containsExactly("SHIPPING", "CANCELLED");
        assertThat(OrderStatusPolicy.adminNextStatuses("COD", "SHIPPING")).containsExactly("DELIVERED");
        assertThat(OrderStatusPolicy.adminNextStatuses("COD", "DELIVERED")).isEmpty();
    }

    @Test
    void payosCannotBeMarkedPaidByAdmin() {
        assertThat(OrderStatusPolicy.adminNextStatuses("PAYOS", "PENDING_PAYMENT")).containsExactly("CANCELLED");
        assertThat(OrderStatusPolicy.adminNextStatuses("PAYOS", "PAID")).containsExactly("PACKING", "CANCELLED");
    }

    @Test
    void cancelledIsTerminal() {
        for (String method : List.of("COD", "PAYOS")) {
            assertThat(OrderStatusPolicy.adminNextStatuses(method, "CANCELLED")).isEmpty();
        }
    }

    @Test
    void buyerCancel() {
        assertThat(OrderStatusPolicy.buyerCanCancel("PENDING_PAYMENT")).isTrue();
        assertThat(OrderStatusPolicy.buyerCanCancel("PAID")).isFalse();
        assertThat(OrderStatusPolicy.buyerCanCancel("PACKING")).isFalse();
    }
}
