package com.nurture.identity.infra;

import com.nurture.identity.domain.ApiToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {

    Optional<ApiToken> findByTokenHash(String tokenHash);

    /** 验证路径直查：定位哈希 = 未加盐 SHA-256(raw)，唯一索引命中单行。 */
    Optional<ApiToken> findByLookupHash(String lookupHash);
}
