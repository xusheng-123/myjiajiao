package com.nurture.verification.app;

import com.nurture.verification.domain.VerificationCase;
import com.nurture.verification.infra.VerificationCaseRepository;
import com.nurture.identity.app.IdentityApplicationService;
import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.shared.audit.AuditService;
import com.nurture.shared.storage.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

/**
 * 资质合规应用服务 — 教师身份/学历材料上传、审核状态机流转。
 * 审核决定的跨模块通知只依赖 identity.app 的账户存在性校验（不直取 identity.infra）。
 */
@Service
public class VerificationApplicationService {

    private final VerificationCaseRepository verificationRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final MessagingApplicationService messagingService;
    private final com.nurture.identity.app.IdentityApplicationService identityService;

    public VerificationApplicationService(VerificationCaseRepository verificationRepository,
                                          FileStorageService fileStorageService,
                                          AuditService auditService,
                                          MessagingApplicationService messagingService,
                                          com.nurture.identity.app.IdentityApplicationService identityService) {
        this.verificationRepository = verificationRepository;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
        this.messagingService = messagingService;
        this.identityService = identityService;
    }

    /** 导师提交资质材料（身份证明 + 学历证明）。 */
    @Transactional
    public VerificationCase submit(UUID teacherAccountId, MultipartFile idDocument, MultipartFile degreeCert) {
        String idKey = store("verification/id", idDocument);
        String degreeKey = store("verification/degree", degreeCert);
        VerificationCase verificationCase = new VerificationCase(teacherAccountId, idKey, degreeKey);
        VerificationCase saved = verificationRepository.save(verificationCase);
        auditService.record("verification:submit", "SUCCESS",
                java.util.Map.of("teacherId", teacherAccountId.toString()), "导师提交资质认证材料");
        return saved;
    }

    @Transactional(readOnly = true)
    public VerificationCase latestOfTeacher(UUID teacherAccountId) {
        return verificationRepository.findFirstByTeacherIdOrderBySubmittedAtDesc(teacherAccountId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<VerificationCaseView> listView(VerificationCase.VerificationStatus status, Pageable pageable) {
        Page<VerificationCase> page = status == null
                ? verificationRepository.findByOrderBySubmittedAtDesc(pageable)
                : verificationRepository.findByStatus(status, pageable);
        return page.map(VerificationCaseView::from);
    }

    /** 审核决定视图（供运营端 DTO 化返回）。 */
    public record VerificationCaseView(UUID caseId, UUID teacherId, String status, String decisionNote,
                                       Instant submittedAt, Instant decidedAt) {
        static VerificationCaseView from(VerificationCase c) {
            return new VerificationCaseView(c.getId(), c.getTeacherId(), c.getStatus().name(),
                    c.getDecisionNote(), c.getSubmittedAt(), c.getDecidedAt());
        }
    }

    /** 运营审核决定：approve / reject（非法迁移由状态机拒绝）。 */
    @Transactional
    public VerificationCaseView decide(UUID caseId, boolean approve, String note, UUID adminAccountId) {
        VerificationCase verificationCase = verificationRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("审核案例不存在"));
        if (approve) {
            verificationCase.approve(adminAccountId, note);
        } else {
            verificationCase.reject(adminAccountId, note);
        }
        if (identityService.existsAccount(verificationCase.getTeacherId())) {
            messagingService.notify(verificationCase.getTeacherId(), "VERIFICATION",
                    approve ? "资质认证通过" : "资质认证需补充材料",
                    approve ? "恭喜！您的导师资质已通过审核，可以在工作台完善档期与定价了。"
                            : "您的资质审核未通过：" + note + " 补充材料后可重新提交。");
        }
        auditService.record("verification:decide", approve ? "APPROVED" : "REJECTED",
                java.util.Map.of("caseId", caseId.toString(), "approved", approve), note);
        return VerificationCaseView.from(verificationCase);
    }

    /** 导师是否已通过资质认证（供 matching 信任证据使用）。 */
    @Transactional(readOnly = true)
    public boolean isApproved(UUID teacherAccountId) {
        return verificationRepository.findFirstByTeacherIdOrderBySubmittedAtDesc(teacherAccountId)
                .map(c -> c.getStatus() == VerificationCase.VerificationStatus.APPROVED)
                .orElse(false);
    }

    public record Material(byte[] content, String filename) {}

    /** Private documents are resolved by case ID; storage keys never leave this module. */
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Material material(UUID caseId, String kind) {
        if (!"identity".equals(kind) && !"degree".equals(kind)) throw new IllegalArgumentException("材料类型无效");
        VerificationCase c = verificationRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("审核案例不存在"));
        String key = "identity".equals(kind) ? c.getIdDocumentKey() : c.getDegreeCertKey();
        byte[] content;
        try { content = fileStorageService.load(key); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("材料不存在或不可读，请联系导师补交"); }
        int dot = key.lastIndexOf('.');
        String ext = dot >= 0 ? key.substring(dot).toLowerCase(java.util.Locale.ROOT) : ".bin";
        if (!java.util.Set.of(".pdf", ".jpg", ".jpeg", ".png", ".webp").contains(ext)) ext = ".bin";
        auditService.record("verification:material-view", "SUCCESS", java.util.Map.of("caseId", caseId, "kind", kind), "运营读取资质材料");
        return new Material(content, kind + "-" + caseId + ext);
    }

    /** 资质材料允许的格式：扩展名 → MIME → 文件魔数三重校验（客户端字段可伪造，以内容为准）。 */
    private static final java.util.Map<String, String> ALLOWED_MATERIAL_TYPES = java.util.Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");

    private String store(String category, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("资质材料缺失：请上传完整文件");
        }
        validateMaterial(file);
        try {
            FileStorageService.StoredFile stored = fileStorageService.store(category,
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
            return stored.storageKey();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("资质材料读取失败", e);
        }
    }

    /**
     * 材料格式校验（质量加固 2026-10-10）：扩展名与 MIME 任一不在白名单即拒绝；
     * 再按魔数识别真实内容（PDF=%PDF / JPEG=FF D8 FF / PNG=89 50 4E 47），
     * 伪造扩展名或 MIME 但内容不符的文件一律拒绝。不声明病毒扫描能力。
     */
    private void validateMaterial(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1);
        String expectedMime = ALLOWED_MATERIAL_TYPES.get(ext);
        if (expectedMime == null) {
            throw new IllegalArgumentException("不支持的文件格式：仅支持 PDF/JPG/PNG");
        }
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!expectedMime.equals(mime)) {
            throw new IllegalArgumentException("文件类型与内容不符：仅支持 PDF/JPG/PNG");
        }
        try {
            byte[] content = file.getBytes();
            boolean magicOk = switch (ext) {
                case "pdf" -> content.length >= 4 && content[0] == '%' && content[1] == 'P'
                        && content[2] == 'D' && content[3] == 'F';
                case "jpg", "jpeg" -> content.length >= 3
                        && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF;
                case "png" -> content.length >= 4 && (content[0] & 0xFF) == 0x89 && content[1] == 'P'
                        && content[2] == 'N' && content[3] == 'G';
                default -> false;
            };
            if (!magicOk) {
                throw new IllegalArgumentException("文件内容与声明格式不符：请上传真实的 PDF/JPG/PNG 文件");
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("资质材料读取失败", e);
        }
    }
}
