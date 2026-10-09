package com.nurture.scheduling.api;

import com.nurture.scheduling.app.SchedulingApplicationService;
import com.nurture.scheduling.domain.AvailabilitySlot;
import com.nurture.shared.security.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 档期排定 API。
 */
@RestController
@RequestMapping("/api/v1/scheduling")
@Tag(name = "scheduling / 档期排定", description = "教师可用时间发布、排期日历、并发冲突排他检测")
@SecurityRequirement(name = "bearerJWT")
public class SchedulingController {

    private final SchedulingApplicationService schedulingService;

    public SchedulingController(SchedulingApplicationService schedulingService) {
        this.schedulingService = schedulingService;
    }

    public record PublishSlotRequest(@NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {
    }

    public record SlotResponse(UUID slotId, UUID teacherId, OffsetDateTime startAt, OffsetDateTime endAt,
                               String status) {
        static SlotResponse from(AvailabilitySlot slot) {
            return new SlotResponse(slot.getId(), slot.getTeacherId(), slot.getStartAt(), slot.getEndAt(),
                    slot.getStatus().name());
        }
    }

    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN') or hasAuthority('SCOPE_scheduling:write')")
    @PostMapping("/availability-slots")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "发布可用时段", description = "重叠时段将被排他检测拒绝；需 booking:create/teacher:write Scope")
    public SlotResponse publish(@RequestBody PublishSlotRequest request) {
        UUID teacherId = CallerContext.require().accountId();
        return SlotResponse.from(schedulingService.publishSlot(teacherId, request.startAt(), request.endAt()));
    }

    @GetMapping("/teachers/{teacherId}/open-slots")
    @Operation(summary = "查询导师可约时段")
    public List<SlotResponse> openSlots(
            @Parameter(description = "导师ID") @PathVariable UUID teacherId) {
        return schedulingService.openSlotsOf(teacherId).stream().map(SlotResponse::from).toList();
    }

    @GetMapping("/me/calendar")
    @Operation(summary = "我的排期日历")
    public List<SlotResponse> calendar(@RequestParam OffsetDateTime from, @RequestParam OffsetDateTime to) {
        UUID teacherId = CallerContext.require().accountId();
        return schedulingService.calendarOf(teacherId, from, to).stream().map(SlotResponse::from).toList();
    }

    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN') or hasAuthority('SCOPE_scheduling:write')")
    @DeleteMapping("/availability-slots/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "屏蔽（下架）可用时段")
    public void block(@PathVariable UUID slotId) {
        schedulingService.blockSlot(slotId);
    }
}
