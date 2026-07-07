package com.mdata.backend.repository;

import com.mdata.backend.entity.PlatformHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformHealthRepository extends JpaRepository<PlatformHealth, String> {
}
