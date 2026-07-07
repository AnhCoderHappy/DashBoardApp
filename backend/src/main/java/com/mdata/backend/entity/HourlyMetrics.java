package com.mdata.backend.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "hourly_metrics", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"platform", "shop_id", "hour"})
})
public class HourlyMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String platform;

    @Column(name = "shop_id")
    private String shopId;

    @Column(nullable = false)
    private Instant hour;

    @Column(name = "order_count")
    private Integer orderCount = 0;

    @Column(name = "gross_revenue")
    private BigDecimal grossRevenue = BigDecimal.ZERO;

    @Column(name = "net_revenue")
    private BigDecimal netRevenue = BigDecimal.ZERO;

    @Column(name = "cancelled_count")
    private Integer cancelledCount = 0;

    @Column(name = "refund_count")
    private Integer refundCount = 0;

    @Column(name = "ad_spend")
    private BigDecimal adSpend = BigDecimal.ZERO;

    private BigDecimal roas = BigDecimal.ZERO;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
