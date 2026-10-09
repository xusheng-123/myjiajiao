package com.nurture.admin.infra;

import com.nurture.admin.domain.PlatformConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformConfigRepository extends JpaRepository<PlatformConfig, String> {
}
