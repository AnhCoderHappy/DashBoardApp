package com.mdata.backend.repository;

import com.mdata.backend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderId(UUID orderId);

    @Modifying
    @Transactional
    @Query("DELETE FROM OrderItem oi WHERE oi.orderId = :orderId")
    void deleteByOrderId(@Param("orderId") UUID orderId);

    @Query("SELECT oi.productName, SUM(oi.quantity), SUM(oi.totalPrice) " +
           "FROM OrderItem oi JOIN Order o ON oi.orderId = o.id " +
           "WHERE o.createdAtPlatform >= :start AND o.createdAtPlatform < :end " +
           "GROUP BY oi.productName " +
           "ORDER BY SUM(oi.totalPrice) DESC")
    List<Object[]> findTopProductsBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT oi.productName, SUM(oi.quantity), SUM(oi.totalPrice) " +
           "FROM OrderItem oi JOIN Order o ON oi.orderId = o.id " +
           "WHERE o.createdAtPlatform >= :start AND o.createdAtPlatform < :end " +
           "AND o.connectionId IN :connectionIds " +
           "GROUP BY oi.productName " +
           "ORDER BY SUM(oi.totalPrice) DESC")
    List<Object[]> findTopProductsBetweenAndConnectionIdIn(@Param("start") Instant start, @Param("end") Instant end, @Param("connectionIds") List<UUID> connectionIds);

    @Query("SELECT oi.productName, o.platform, o.rawData, oi.quantity, oi.totalPrice " +
           "FROM OrderItem oi JOIN Order o ON oi.orderId = o.id " +
           "WHERE o.createdAtPlatform >= :start AND o.createdAtPlatform < :end")
    List<Object[]> findItemsWithPlatformBetween(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT oi.productName, o.platform, o.rawData, oi.quantity, oi.totalPrice " +
           "FROM OrderItem oi JOIN Order o ON oi.orderId = o.id " +
           "WHERE o.createdAtPlatform >= :start AND o.createdAtPlatform < :end " +
           "AND o.connectionId IN :connectionIds")
    List<Object[]> findItemsWithPlatformBetweenAndConnectionIdIn(@Param("start") Instant start, @Param("end") Instant end, @Param("connectionIds") List<UUID> connectionIds);
}
