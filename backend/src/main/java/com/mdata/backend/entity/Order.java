package com.mdata.backend.entity;

import lombok.Data;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"connection_id", "platform_order_id"})
})
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String platform;

    @Column(name = "source_channel")
    private String sourceChannel = "unknown";

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

    @Column(name = "business_time")
    private Instant businessTime;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "business_hour")
    private Integer businessHour;

    @Column(name = "external_updated_at_platform")
    private Instant externalUpdatedAtPlatform;

    @Column(name = "external_version")
    private String externalVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data")
    private String rawData;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
