package com.mdata.backend.repository;

import com.mdata.backend.entity.AdInsightsHourly;
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
public interface AdInsightsHourlyRepository extends JpaRepository<AdInsightsHourly, UUID> {
    Optional<AdInsightsHourly> findByPlatformAndShopIdAndAdAccountIdAndCampaignIdAndHour(
            String platform, String shopId, String adAccountId, String campaignId, Instant hour
    );

    Optional<AdInsightsHourly> findByPlatformAndShopIdAndHour(String platform, String shopId, Instant hour);

    boolean existsByPlatformAndShopId(String platform, String shopId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO ad_insights_hourly (
                id, platform, shop_id, ad_account_id, campaign_id, campaign_name, hour,
                spend, impressions, clicks, reach, cpc, cpm, ctr, conversions, raw_data, created_at
            ) VALUES (
                :id, :platform, :shopId, :adAccountId, :campaignId, :campaignName, :hour,
                :spend, :impressions, :clicks, :reach, :cpc, :cpm, :ctr, 0, CAST(:rawData AS jsonb), :createdAt
            )
            ON CONFLICT (platform, shop_id, hour) DO UPDATE SET
                ad_account_id = EXCLUDED.ad_account_id,
                campaign_id = EXCLUDED.campaign_id,
                campaign_name = EXCLUDED.campaign_name,
                spend = EXCLUDED.spend,
                impressions = EXCLUDED.impressions,
                clicks = EXCLUDED.clicks,
                reach = EXCLUDED.reach,
                cpc = EXCLUDED.cpc,
                cpm = EXCLUDED.cpm,
                ctr = EXCLUDED.ctr,
                raw_data = EXCLUDED.raw_data,
                created_at = EXCLUDED.created_at
            """, nativeQuery = true)
    int upsertByPlatformShopIdHour(
            @Param("id") UUID id,
            @Param("platform") String platform,
            @Param("shopId") String shopId,
            @Param("adAccountId") String adAccountId,
            @Param("campaignId") String campaignId,
            @Param("campaignName") String campaignName,
            @Param("hour") Instant hour,
            @Param("spend") BigDecimal spend,
            @Param("impressions") Integer impressions,
            @Param("clicks") Integer clicks,
            @Param("reach") Integer reach,
            @Param("cpc") BigDecimal cpc,
            @Param("cpm") BigDecimal cpm,
            @Param("ctr") BigDecimal ctr,
            @Param("rawData") String rawData,
            @Param("createdAt") Instant createdAt
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

    @Query("SELECT SUM(a.spend) FROM AdInsightsHourly a WHERE a.platform = :platform AND a.shopId = :shopId AND a.hour < :hour")
    BigDecimal sumSpendBeforeHour(
            @Param("platform") String platform,
            @Param("shopId") String shopId,
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

    @Query("SELECT SUM(a.clicks) FROM AdInsightsHourly a WHERE a.platform = :platform AND a.shopId = :shopId AND a.hour < :hour")
    Long sumClicksBeforeHour(
            @Param("platform") String platform,
            @Param("shopId") String shopId,
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

    @Query("SELECT SUM(a.impressions) FROM AdInsightsHourly a WHERE a.platform = :platform AND a.shopId = :shopId AND a.hour < :hour")
    Long sumImpressionsBeforeHour(
            @Param("platform") String platform,
            @Param("shopId") String shopId,
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

    @Query("SELECT SUM(a.reach) FROM AdInsightsHourly a WHERE a.platform = :platform AND a.shopId = :shopId AND a.hour < :hour")
    Long sumReachBeforeHour(
            @Param("platform") String platform,
            @Param("shopId") String shopId,
            @Param("hour") Instant hour
    );

    boolean existsByPlatformAndShopIdAndAdAccountIdAndCampaignId(String platform, String shopId, String adAccountId, String campaignId);
}
