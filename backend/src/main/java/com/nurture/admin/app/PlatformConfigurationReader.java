package com.nurture.admin.app;

import com.nurture.admin.domain.PlatformConfig;
import com.nurture.admin.infra.PlatformConfigRepository;
import com.nurture.shared.config.PlatformConfigReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only configuration adapter; independent of cross-module administration workflows. */
@Service
public class PlatformConfigurationReader implements PlatformConfigReader {
    private final PlatformConfigRepository repository;

    public PlatformConfigurationReader(PlatformConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<String> get(String key) {
        return repository.findById(key).map(PlatformConfig::getValue);
    }
}
