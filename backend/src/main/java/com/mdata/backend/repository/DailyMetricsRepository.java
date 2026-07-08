package com.mdata.backend.repository;

import com.mdata.backend.entity.DailyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyMetricsRepository extends JpaRepository<DailyMetrics, UUID> {
    Optional<DailyMetrics> findByPlatformAndShopIdAndDate(String platform, String shopId, LocalDate date);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO daily_metrics (
                id, platform, shop_id, date, order_count, gross_revenue, net_revenue,
                cancelled_count, refund_count, ad_spend, roas, created_at, updated_at
            ) VALUES (
                :id, :platform, :shopId, :date, :orderCount, :grossRevenue, :netRevenue,
                :cancelledCount, :refundCount, :adSpend, :roas, :createdAt, :updatedAt
            )
            ON CONFLICT (platform, shop_id, date) DO UPDATE SET
                order_count = EXCLUDED.order_count,
                gross_revenue = EXCLUDED.gross_revenue,
                net_revenue = EXCLUDED.net_revenue,
                cancelled_count = EXCLUDED.cancelled_count,
                refund_count = EXCLUDED.refund_count,
                ad_spend = EXCLUDED.ad_spend,
                roas = EXCLUDED.roas,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertByPlatformShopIdDate(
            @Param("id") UUID id,
            @Param("platform") String platform,
            @Param("shopId") String shopId,
            @Param("date") LocalDate date,
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

    List<DailyMetrics> findByDate(LocalDate date);
    
    List<DailyMetrics> findByShopIdAndDate(String shopId, LocalDate date);

    @Modifying
    @Transactional
    @Query("DELETE FROM DailyMetrics dm WHERE dm.date >= :since")
    void deleteByDateGreaterThanEqual(@Param("since") LocalDate since);

    @Modifying
    @Transactional
    @Query("DELETE FROM DailyMetrics dm WHERE dm.date = :date")
    void deleteByDate(@Param("date") LocalDate date);
}
