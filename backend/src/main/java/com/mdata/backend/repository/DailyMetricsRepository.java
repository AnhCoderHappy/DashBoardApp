package com.mdata.backend.repository;

import com.mdata.backend.entity.DailyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyMetricsRepository extends JpaRepository<DailyMetrics, UUID> {
    Optional<DailyMetrics> findByPlatformAndShopIdAndDate(String platform, String shopId, LocalDate date);

    List<DailyMetrics> findByDate(LocalDate date);
    
    List<DailyMetrics> findByShopIdAndDate(String shopId, LocalDate date);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM DailyMetrics dm WHERE dm.date >= :since")
    void deleteByDateGreaterThanEqual(@org.springframework.data.repository.query.Param("since") LocalDate since);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM DailyMetrics dm WHERE dm.date = :date")
    void deleteByDate(@org.springframework.data.repository.query.Param("date") LocalDate date);
}
