package com.nurture.scheduling.app;

import com.nurture.scheduling.domain.AvailabilitySlot;
import com.nurture.scheduling.infra.AvailabilitySlotRepository;
import com.nurture.shared.security.CallerContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 档期排定应用服务 — 教师可用时间发布、排期日历维护与并发冲突排他检测。
 */
@Service
public class SchedulingApplicationService {

    private final AvailabilitySlotRepository slotRepository;
    private final com.nurture.scheduling.infra.SchedulingPublicationLock publicationLock;

    public SchedulingApplicationService(AvailabilitySlotRepository slotRepository,com.nurture.scheduling.infra.SchedulingPublicationLock publicationLock) {
        this.slotRepository = slotRepository;
        this.publicationLock=publicationLock;
    }

    @Transactional
    public AvailabilitySlot publishSlot(UUID teacherId, OffsetDateTime startAt, OffsetDateTime endAt) {
        if(startAt==null || endAt==null || !endAt.isAfter(startAt))throw new IllegalArgumentException("档期起止时间无效");
        publicationLock.lock(teacherId);
        List<AvailabilitySlot> overlapping = slotRepository.findOverlapping(teacherId, startAt, endAt);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException("所发布时段与现有档期重叠（含重叠边界），请调整后再发布");
        }
        return slotRepository.save(new AvailabilitySlot(teacherId, startAt, endAt));
    }

    /** 档期视图（D11/架构收口：跨模块消费视图而非实体）。 */
    public record SlotView(UUID slotId, UUID teacherId, OffsetDateTime startAt,
                           OffsetDateTime endAt, String status) {
        static SlotView from(AvailabilitySlot s) {
            return new SlotView(s.getId(), s.getTeacherId(), s.getStartAt(), s.getEndAt(), s.getStatus().name());
        }
    }

    public Optional<SlotView> slotOf(UUID slotId) {
        return slotRepository.findById(slotId).map(SlotView::from);
    }

    /** 批量档期视图（分页场景避免逐行 N+1 查询）。 */
    @Transactional(readOnly = true)
    public List<SlotView> slotViewsOf(java.util.Collection<UUID> slotIds) {
        if (slotIds.isEmpty()) return List.of();
        return slotRepository.findAllById(slotIds).stream().map(SlotView::from).toList();
    }

    public List<SlotView> openSlotViewsOf(UUID teacherAccountId) {
        return openSlotsOf(teacherAccountId).stream().map(SlotView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlot> openSlotsOf(UUID teacherId) {
        // 质量加固：仅返回尚未开始的 OPEN 档期——已过去的 OPEN 残留不再作为有效可约时间
        // （教师日历 calendarOf 不受影响，历史档期仍可见可管理，不物理删除）。
        return slotRepository.findByTeacherIdAndStatusAndStartAtAfterOrderByStartAtAsc(
                teacherId, AvailabilitySlot.SlotStatus.OPEN, OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlot> calendarOf(UUID teacherId, OffsetDateTime from, OffsetDateTime to) {
        return slotRepository.findByTeacherIdAndStartAtBetweenOrderByStartAtAsc(teacherId, from, to);
    }
    @Transactional(readOnly=true)
    public List<SlotView> calendarViewsOf(UUID teacherId,OffsetDateTime from,OffsetDateTime to){return calendarOf(teacherId,from,to).stream().map(SlotView::from).toList();}

    /** 供 booking 域调用：占用/释放时段。 */
    @Transactional
    public java.time.OffsetDateTime holdSlotStartAt(UUID slot,UUID teacher){return holdSlot(slot,teacher).getStartAt();}

    @Transactional
    public AvailabilitySlot holdSlot(UUID slotId, UUID requestingTeacherId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("档期时段不存在"));
        if (requestingTeacherId != null && !slot.getTeacherId().equals(requestingTeacherId)) {
            throw new IllegalArgumentException("该时段不属于当前导师");
        }
        slot.hold();
        return slot;
    }

    @Transactional
    public AvailabilitySlot bookSlot(UUID slotId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("档期时段不存在"));
        slot.book();
        return slot;
    }

    @Transactional
    public AvailabilitySlot releaseSlot(UUID slotId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("档期时段不存在"));
        slot.release();
        return slot;
    }

    @Transactional
    public void blockSlot(UUID slotId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("档期时段不存在"));
        if (!slot.getTeacherId().equals(CallerContext.require().accountId())) {
            throw new AccessDeniedException("仅档期归属导师可屏蔽时段");
        }
        slot.block();
    }
}
