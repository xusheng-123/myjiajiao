package com.nurture.parent.infra;

import com.nurture.parent.domain.FamilyWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FamilyWalletRepository extends JpaRepository<FamilyWallet, UUID> {

    Optional<FamilyWallet> findByParentId(UUID parentId);
}
