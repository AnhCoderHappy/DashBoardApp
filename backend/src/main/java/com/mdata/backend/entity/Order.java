package com.mdata.backend.entity;

import lombok.Data;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"platform", "platform_order_id"})
})
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String platform;

    @Column(name = "platform_order_id", nullable = false)
    private String platformOrderId;

    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(nullable = false)
    private String status;

    @Column(name = "normalized_status", nullable = false)
    private String normalizedStatus;

    @Column(name = "gross_revenue")
    private BigDecimal grossRevenue = BigDecimal.ZERO;

    @Column(name = "net_revenue")
    private BigDecimal netRevenue = BigDecimal.ZERO;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_fee")
    private BigDecimal shippingFee = BigDecimal.ZERO;

    private String currency = "VND";

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "created_at_platform")
    private Instant createdAtPlatform;

    @Column(name = "updated_at_platform")
    private Instant updatedAtPlatform;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data")
    private String rawData;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
