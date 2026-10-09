package com.nurture.identity.infra;

import com.nurture.identity.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 重用检测响应：吊销指定账户的全部刷新令牌。 */
    List<RefreshToken> findByAccountId(UUID accountId);
}
