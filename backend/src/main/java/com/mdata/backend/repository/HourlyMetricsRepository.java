package com.mdata.backend.repository;

import com.mdata.backend.entity.HourlyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HourlyMetricsRepository extends JpaRepository<HourlyMetrics, UUID> {
    Optional<HourlyMetrics> findByPlatformAndShopIdAndHour(String platform, String shopId, Instant hour);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO hourly_metrics (
                id, platform, shop_id, hour, order_count, gross_revenue, net_revenue,
                cancelled_count, refund_count, ad_spend, roas, created_at, updated_at
            ) VALUES (
                :id, :platform, :shopId, :hour, :orderCount, :grossRevenue, :netRevenue,
                :cancelledCount, :refundCount, :adSpend, :roas, :createdAt, :updatedAt
            )
            ON CONFLICT (platform, shop_id, hour) DO UPDATE SET
                order_count = EXCLUDED.order_count,
                gross_revenue = EXCLUDED.gross_revenue,
                net_revenue = EXCLUDED.net_revenue,
                cancelled_count = EXCLUDED.cancelled_count,
                refund_count = EXCLUDED.refund_count,
                ad_spend = EXCLUDED.ad_spend,
                roas = EXCLUDED.roas,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertByPlatformShopIdHour(
            @Param("id") UUID id,
            @Param("platform") String platform,
            @Param("shopId") String shopId,
            @Param("hour") Instant hour,
            @Param("orderCount") Integer orderCount,
            @Param("grossRevenue") BigDecimal grossRevenue,
            @Param("netRevenue") BigDecimal netRevenue,
            @Param("cancelledCount") Integer cancelledCount,
            @Param("refundCount") Integer refundCount,
            @Param("adSpend") BigDecimal adSpend,
            @Param("roas") BigDecimal roas,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt
    );

    List<HourlyMetrics> findByHourGreaterThanEqualOrderByHourAsc(Instant since);
    
    List<HourlyMetrics> findByShopIdAndHourGreaterThanEqualOrderByHourAsc(String shopId, Instant since);

    List<HourlyMetrics> findByHourBetweenOrderByHourAsc(Instant start, Instant end);

    List<HourlyMetrics> findByShopIdAndHourBetweenOrderByHourAsc(String shopId, Instant start, Instant end);

    @Modifying
    @Transactional
    @Query("DELETE FROM HourlyMetrics hm WHERE hm.hour >= :since")
    void deleteByHourGreaterThanEqual(@Param("since") Instant since);

    @Modifying
    @Transactional
    @Query("DELETE FROM HourlyMetrics hm WHERE hm.hour >= :start AND hm.hour < :end")
    void deleteByHourBetween(@Param("start") Instant start, @Param("end") Instant end);
}
