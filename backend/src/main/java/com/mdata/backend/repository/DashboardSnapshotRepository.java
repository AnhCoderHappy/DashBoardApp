package com.mdata.backend.repository;

import com.mdata.backend.entity.DashboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardSnapshotRepository extends JpaRepository<DashboardSnapshot, UUID> {
    Optional<DashboardSnapshot> findByConnectionIdAndSnapshotDateAndSnapshotType(UUID connectionId, LocalDate snapshotDate, String snapshotType);
}
