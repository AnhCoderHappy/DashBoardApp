package com.mdata.backend.repository;

import com.mdata.backend.entity.ProductDailyMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductDailyMetricRepository extends JpaRepository<ProductDailyMetric, UUID> {
    List<ProductDailyMetric> findByConnectionIdAndMetricDateOrderByGrossRevenueDesc(UUID connectionId, LocalDate metricDate);

    @Modifying
    @Transactional
    @Query("DELETE FROM ProductDailyMetric p WHERE p.connectionId = :connectionId AND p.metricDate = :metricDate")
    void deleteByConnectionIdAndMetricDate(@Param("connectionId") UUID connectionId, @Param("metricDate") LocalDate metricDate);
}
