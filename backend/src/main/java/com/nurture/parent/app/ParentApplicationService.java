package com.nurture.parent.app;

import com.nurture.parent.domain.FamilyWallet;
import com.nurture.parent.domain.ParentProfile;
import com.nurture.parent.domain.WalletTransaction;
import com.nurture.parent.infra.FamilyWalletRepository;
import com.nurture.parent.infra.ParentProfileRepository;
import com.nurture.parent.infra.WalletTransactionRepository;
import com.nurture.parent.domain.GuardianBinding;
import com.nurture.parent.infra.GuardianBindingRepository;
import com.nurture.student.app.StudentApplicationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 家长领域应用服务 — 绑定子女、家庭统一教育支付钱包与消费对账。
 */
@Service
public class ParentApplicationService {

    private final ParentProfileRepository parentProfileRepository;
    private final FamilyWalletRepository walletRepository;
    private final WalletTransactionRepository txnRepository;
    private final GuardianBindingRepository bindingRepository;
    private final StudentApplicationService studentService;

    public ParentApplicationService(ParentProfileRepository parentProfileRepository,
                                    FamilyWalletRepository walletRepository,
                                    WalletTransactionRepository txnRepository,
                                    GuardianBindingRepository bindingRepository,
                                    StudentApplicationService studentService) {
        this.parentProfileRepository = parentProfileRepository;
        this.walletRepository = walletRepository;
        this.txnRepository = txnRepository;
        this.bindingRepository = bindingRepository;
        this.studentService = studentService;
    }

    @Transactional
    public ParentProfile ensureProfile(UUID accountId) {
        return parentProfileRepository.findByAccountId(accountId)
                .orElseGet(() -> parentProfileRepository.save(new ParentProfile(accountId, nextNo("PAR"))));
    }

    /** 绑定子女：家长提交学生账号编号，建立 PENDING 关联（学生侧确认后激活）。 */
    @Transactional
    public GuardianBinding bindChild(UUID parentAccountId, String studentNo, String relation) {
        ParentProfile parent = ensureProfile(parentAccountId);
        var student = studentService.refByStudentNo(studentNo)
                .orElseThrow(() -> new IllegalArgumentException("学生编号不存在，请核对后重试"));
        if (bindingRepository.findByParentIdAndStudentId(parent.getId(), student.profileId()).isPresent()) {
            throw new IllegalStateException("该学生已绑定，无需重复绑定");
        }
        return bindingRepository.save(new GuardianBinding(parent.getId(), student.profileId(), relation));
    }

    @Transactional
    public GuardianBinding confirmBinding(UUID studentAccountId) {
        var student = studentService.refByAccountId(studentAccountId)
                .orElseThrow(() -> new IllegalArgumentException("学生档案不存在"));
        var binding = bindingRepository.findByStudentId(student.profileId()).stream()
                .filter(b -> b.getStatus() == GuardianBinding.BindingStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("暂无待确认的监护绑定申请"));
        binding.activate();
        return binding;
    }

    @Transactional(readOnly = true)
    public List<GuardianBinding> childrenOf(UUID parentAccountId) {
        ParentProfile parent = ensureProfile(parentAccountId);
        return bindingRepository.findByParentIdAndStatus(parent.getId(), GuardianBinding.BindingStatus.ACTIVE);
    }

    /** 供交易域调用（D1）：校验家长与学生之间存在「已确认（ACTIVE）」的监护绑定。 */
    @Transactional(readOnly = true)
    public boolean isActiveGuardian(UUID parentAccountId, UUID studentAccountId) {
        var parent = parentProfileRepository.findByAccountId(parentAccountId);
        if (parent.isEmpty()) {
            return false;
        }
        var studentRef = studentService.refByAccountId(studentAccountId);
        if (studentRef.isEmpty()) {
            return false;
        }
        return bindingRepository
                .findByParentIdAndStudentId(parent.get().getId(), studentRef.get().profileId())
                .map(b -> b.getStatus() == GuardianBinding.BindingStatus.ACTIVE)
                .orElse(false);
    }

    @Transactional
    public FamilyWallet ensureWallet(UUID parentAccountId) {
        ParentProfile parent = ensureProfile(parentAccountId);
        return walletRepository.findByParentId(parent.getId())
                .orElseGet(() -> walletRepository.save(new FamilyWallet(parent.getId())));
    }

    /** 家庭钱包充值（首版经由支付网关 Mock 渠道）。 */
    @Transactional
    public WalletTransaction topUp(UUID parentAccountId, long amountCents, String memo) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("充值金额必须为正数（单位：分）");
        }
        FamilyWallet wallet = ensureWallet(parentAccountId);
        wallet.credit(amountCents);
        WalletTransaction txn = new WalletTransaction(wallet.getId(), WalletTransaction.TxnType.TOPUP,
                amountCents, wallet.getBalanceCents(), null, memo == null ? "家庭钱包充值" : memo);
        return txnRepository.save(txn);
    }

    /** 供支付域调用：订单扣款。 */
    @Transactional
    public WalletTransaction chargeForOrder(UUID parentAccountId, UUID orderId, long amountCents, String memo) {
        FamilyWallet wallet = ensureWallet(parentAccountId);
        wallet.debit(amountCents);
        WalletTransaction txn = new WalletTransaction(wallet.getId(), WalletTransaction.TxnType.PAYMENT,
                -amountCents, wallet.getBalanceCents(), orderId, memo);
        return txnRepository.save(txn);
    }

    /** 供退款域调用：退款入账家庭钱包。 */
    @Transactional
    public WalletTransaction creditRefund(UUID parentAccountId, UUID orderId, long amountCents, String memo) {
        FamilyWallet wallet = ensureWallet(parentAccountId);
        wallet.credit(amountCents);
        WalletTransaction txn = new WalletTransaction(wallet.getId(), WalletTransaction.TxnType.REFUND,
                amountCents, wallet.getBalanceCents(), orderId, memo);
        return txnRepository.save(txn);
    }

    @Transactional(readOnly = true)
    public WalletOverview walletOverview(UUID parentAccountId) {
        FamilyWallet wallet = ensureWallet(parentAccountId);
        Page<WalletTransaction> page = txnRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(),
                PageRequest.of(0, 20));
        return new WalletOverview(wallet.getBalanceCents(), wallet.getCurrency(),
                page.getContent().stream().map(t -> new TxnView(t.getId(), t.getType().name(),
                        t.getAmountCents(), t.getBalanceAfterCents(), t.getRelatedOrderId(),
                        t.getMemo(), t.getCreatedAt())).toList());
    }

    private String nextNo(String prefix) {
        return "NUR-" + prefix + "-" + String.format("%06d", 1 + new java.security.SecureRandom().nextInt(999_999));
    }

    public record WalletOverview(long balanceCents, String currency, List<TxnView> recentTransactions) {
    }

    public record TxnView(UUID txnId, String type, long amountCents, long balanceAfterCents,
                          UUID relatedOrderId, String memo, java.time.Instant createdAt) {
    }

    /** 学习周报：学生档案 profileId → accountId 归一化（不存在返回 null）。 */
    @Transactional(readOnly = true)
    public java.util.UUID accountIdOfStudentProfile(java.util.UUID studentProfileId) {
        return studentService.profileAccountIdOf(studentProfileId);
    }
}
