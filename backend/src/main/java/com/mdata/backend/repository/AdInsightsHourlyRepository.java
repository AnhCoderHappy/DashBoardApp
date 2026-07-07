package com.mdata.backend.repository;

import com.mdata.backend.entity.AdInsightsHourly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdInsightsHourlyRepository extends JpaRepository<AdInsightsHourly, UUID> {
    Optional<AdInsightsHourly> findByPlatformAndShopIdAndAdAccountIdAndCampaignIdAndHour(
            String platform, String shopId, String adAccountId, String campaignId, Instant hour
    );

    List<AdInsightsHourly> findByHourGreaterThanEqual(Instant since);

    List<AdInsightsHourly> findByHourBetween(Instant start, Instant end);

    List<AdInsightsHourly> findByShopIdAndHourBetween(String shopId, Instant start, Instant end);

    @Query("SELECT SUM(a.spend) FROM AdInsightsHourly a WHERE a.platform = :platform AND a.shopId = :shopId AND a.adAccountId = :adAccountId AND a.campaignId = :campaignId AND a.hour < :hour")
    BigDecimal sumSpendBeforeHour(
            @Param("platform") String platform,
            @Param("shopId") String shopId,
            @Param("adAccountId") String adAccountId,
            @Param("campaignId") String campaignId,
            @Param("hour") Instant hour
    );

    @Query("SELECT SUM(a.clicks) FROM AdInsightsHourly a WHERE a.platform = :platform AND a.shopId = :shopId AND a.adAccountId = :adAccountId AND a.campaignId = :campaignId AND a.hour < :hour")
    Long sumClicksBeforeHour(
            @Param("platform") String platform,
            @Param("shopId") String shopId,
            @Param("adAccountId") String adAccountId,
            @Param("campaignId") String campaignId,
            @Param("hour") Instant hour
    );

    @Query("SELECT SUM(a.impressions) FROM AdInsightsHourly a WHERE a.platform = :platform AND a.shopId = :shopId AND a.adAccountId = :adAccountId AND a.campaignId = :campaignId AND a.hour < :hour")
    Long sumImpressionsBeforeHour(
            @Param("platform") String platform,
            @Param("shopId") String shopId,
            @Param("adAccountId") String adAccountId,
            @Param("campaignId") String campaignId,
            @Param("hour") Instant hour
    );

    @Query("SELECT SUM(a.reach) FROM AdInsightsHourly a WHERE a.platform = :platform AND a.shopId = :shopId AND a.adAccountId = :adAccountId AND a.campaignId = :campaignId AND a.hour < :hour")
    Long sumReachBeforeHour(
            @Param("platform") String platform,
            @Param("shopId") String shopId,
            @Param("adAccountId") String adAccountId,
            @Param("campaignId") String campaignId,
            @Param("hour") Instant hour
    );

    boolean existsByPlatformAndShopIdAndAdAccountIdAndCampaignId(String platform, String shopId, String adAccountId, String campaignId);
}
