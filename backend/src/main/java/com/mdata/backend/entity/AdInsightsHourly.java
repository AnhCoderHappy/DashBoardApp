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
@Table(name = "ad_insights_hourly", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"platform", "ad_account_id", "campaign_id", "hour"})
})
public class AdInsightsHourly {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String platform;

    @Column(name = "ad_account_id")
    private String adAccountId;

    @Column(name = "campaign_id")
    private String campaignId;

    @Column(name = "campaign_name")
    private String campaignName;

    @Column(nullable = false)
    private Instant hour;

    private BigDecimal spend = BigDecimal.ZERO;

    private Integer impressions = 0;

    private Integer clicks = 0;

    private Integer reach = 0;

    private BigDecimal cpc = BigDecimal.ZERO;

    private BigDecimal cpm = BigDecimal.ZERO;

    private BigDecimal ctr = BigDecimal.ZERO;

    private BigDecimal conversions = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data")
    private String rawData;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
