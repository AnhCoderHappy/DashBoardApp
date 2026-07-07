package com.mdata.backend.connector;

import java.time.Instant;
import java.util.UUID;

public interface PlatformConnector {
    String getPlatform();
    void refreshToken(UUID connectionId) throws Exception;
    void syncOrders(UUID connectionId, Instant since) throws Exception;
    default void syncAdsInsights(UUID connectionId, Instant since) throws Exception {
        // default no-op
    }
    default boolean testConnection(UUID connectionId) throws Exception {
        return true;
    }
}
