package domain.config;

import java.time.Duration;

public final class PurchaseConfig {
    private PurchaseConfig() {}

    public static final int MAX_ACTIVE_ORDERS_PER_EVENT = 50;
    public static final Duration ACTIVE_ORDER_TTL = Duration.ofMinutes(10);
}