package com.mdata.backend.repository;

import com.mdata.backend.entity.RealtimeOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RealtimeOutboxRepository extends JpaRepository<RealtimeOutbox, UUID> {
    List<RealtimeOutbox> findByStatusOrderByCreatedAtAsc(String status);
}
