package com.mdata.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PancakeOrderNormalizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PancakeOrderNormalizer normalizer = new PancakeOrderNormalizer();

    @Test
    void extractsSourceChannelFromPancakeOrder() throws Exception {
        var order = objectMapper.readTree("""
                {"id":"P1","order_sources_name":"TikTok Shop","status":"3","inserted_at":"2026-07-08T10:00:00"}
                """);

        var normalized = normalizer.normalize(order, UUID.randomUUID());

        assertThat(normalized.platform()).isEqualTo("pancake");
        assertThat(normalized.sourceChannel()).isEqualTo("tiktok-shop");
    }

    @Test
    void calculatesBusinessDateUsingVietnamTimezone() throws Exception {
        var order = objectMapper.readTree("""
                {"id":"P2","source":"Shopee","status":"3","inserted_at":"2026-07-07T18:30:00Z"}
                """);

        var normalized = normalizer.normalize(order, UUID.randomUUID());

        assertThat(normalized.businessDate()).isEqualTo(LocalDate.of(2026, 7, 8));
        assertThat(normalized.businessHour()).isEqualTo(1);
    }

    @Test
    void treatsPancakeNaiveTimestampAsUtc() throws Exception {
        var order = objectMapper.readTree("""
                {"id":"P3","source":"Shopee","status":"3","inserted_at":"2026-07-08T12:23:20"}
                """);

        var normalized = normalizer.normalize(order, UUID.randomUUID());

        assertThat(normalized.businessHour()).isEqualTo(19);
    }
}
