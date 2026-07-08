package com.mdata.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "product_daily_metrics", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"connection_id", "metric_date", "source_channel", "platform_product_id", "platform_sku_id"})
})
public class ProductDailyMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(name = "source_channel", nullable = false)
    private String sourceChannel;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "platform_product_id")
    private String platformProductId;

    @Column(name = "platform_sku_id")
    private String platformSkuId;

    private String sku;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "quantity_sold")
    private Integer quantitySold = 0;

    @Column(name = "gross_revenue")
    private BigDecimal grossRevenue = BigDecimal.ZERO;

    @Column(name = "net_revenue")
    private BigDecimal netRevenue = BigDecimal.ZERO;

    @Column(name = "order_count")
    private Integer orderCount = 0;

    @Column(name = "refund_count")
    private Integer refundCount = 0;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
