package com.mdata.backend.repository;

import com.mdata.backend.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByPlatformAndPlatformOrderId(String platform, String platformOrderId);

    List<Order> findByCreatedAtPlatformGreaterThanEqual(Instant since);

    @Query("SELECT o FROM Order o WHERE o.createdAtPlatform >= :since AND o.normalizedStatus = :status")
    List<Order> findOrdersSinceWithStatus(@Param("since") Instant since, @Param("status") String status);

    @Query("SELECT o FROM Order o WHERE o.createdAtPlatform >= :since AND o.normalizedStatus IN ('completed', 'processing', 'pending')")
    List<Order> findActiveOrdersSince(@Param("since") Instant since);

    @Query("SELECT o FROM Order o WHERE o.createdAtPlatform >= :since AND o.normalizedStatus IN ('completed', 'processing', 'pending') AND o.connectionId IN :connectionIds")
    List<Order> findActiveOrdersSinceAndConnectionIdIn(@Param("since") Instant since, @Param("connectionIds") List<UUID> connectionIds);

    List<Order> findByCreatedAtPlatformBetween(Instant start, Instant end);

    List<Order> findByCreatedAtPlatformBetweenAndConnectionIdIn(Instant start, Instant end, List<UUID> connectionIds);

    List<Order> findByCreatedAtPlatformGreaterThanEqualAndConnectionIdIn(Instant since, List<UUID> connectionIds);

    @Query("SELECT o FROM Order o WHERE o.createdAtPlatform >= :start AND o.createdAtPlatform < :end AND o.normalizedStatus IN ('completed', 'processing', 'pending')")
    List<Order> findActiveOrdersBetween(@Param("start") Instant start, @Param("end") Instant end);
}
