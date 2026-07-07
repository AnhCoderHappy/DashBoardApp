package com.mdata.backend.repository;

import com.mdata.backend.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    Optional<WebhookEvent> findByPlatformAndEventId(String platform, String eventId);

    List<WebhookEvent> findByStatus(String status);
}
