package com.mdata.backend.repository;

import com.mdata.backend.entity.HourlyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HourlyMetricsRepository extends JpaRepository<HourlyMetrics, UUID> {
    Optional<HourlyMetrics> findByPlatformAndShopIdAndHour(String platform, String shopId, Instant hour);

    List<HourlyMetrics> findByHourGreaterThanEqualOrderByHourAsc(Instant since);
    
    List<HourlyMetrics> findByShopIdAndHourGreaterThanEqualOrderByHourAsc(String shopId, Instant since);

    List<HourlyMetrics> findByHourBetweenOrderByHourAsc(Instant start, Instant end);

    List<HourlyMetrics> findByShopIdAndHourBetweenOrderByHourAsc(String shopId, Instant start, Instant end);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM HourlyMetrics hm WHERE hm.hour >= :since")
    void deleteByHourGreaterThanEqual(@org.springframework.data.repository.query.Param("since") Instant since);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM HourlyMetrics hm WHERE hm.hour >= :start AND hm.hour < :end")
    void deleteByHourBetween(@org.springframework.data.repository.query.Param("start") Instant start, @org.springframework.data.repository.query.Param("end") Instant end);
}
