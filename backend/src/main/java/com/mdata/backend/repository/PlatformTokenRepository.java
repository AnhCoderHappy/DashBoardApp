package com.mdata.backend.repository;

import com.mdata.backend.entity.PlatformToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformTokenRepository extends JpaRepository<PlatformToken, UUID> {
    Optional<PlatformToken> findByConnectionId(UUID connectionId);
}
