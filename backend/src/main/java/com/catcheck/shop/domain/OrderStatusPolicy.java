package com.catcheck.shop.domain;

import java.util.List;

public final class OrderStatusPolicy {
    private OrderStatusPolicy() { }

    // PAYOS orders leave PENDING_PAYMENT only via the webhook (PAID) or cancel; admin never marks them paid.
    public static List<String> adminNextStatuses(String paymentMethod, String status) {
        return switch (status) {
            case "PENDING_PAYMENT" -> "COD".equals(paymentMethod) ? List.of("PACKING", "CANCELLED") : List.of("CANCELLED");
            case "PAID" -> List.of("PACKING", "CANCELLED");
            case "PACKING" -> List.of("SHIPPING", "CANCELLED");
            case "SHIPPING" -> List.of("DELIVERED");
            default -> List.of();
        };
    }

    public static boolean buyerCanCancel(String status) {
        return "PENDING_PAYMENT".equals(status);
    }
}
