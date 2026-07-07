package com.mdata.backend.repository;

import com.mdata.backend.entity.PlatformConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PlatformConnectionRepository extends JpaRepository<PlatformConnection, UUID> {
    List<PlatformConnection> findByPlatform(String platform);
}
