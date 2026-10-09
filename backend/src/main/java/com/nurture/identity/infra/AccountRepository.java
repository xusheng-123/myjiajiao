package com.nurture.identity.infra;

import com.nurture.identity.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByPhone(String phone);

    Optional<Account> findByAccountNo(String accountNo);

    List<Account> findByRole(com.nurture.shared.security.CallerRole role);
}
