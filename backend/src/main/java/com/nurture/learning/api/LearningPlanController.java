package com.nurture.learning.api;
import com.nurture.learning.app.LearningPlanService;
import com.nurture.learning.domain.LearningPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/learning/plans")
@PreAuthorize("hasAnyRole('STUDENT','TEACHER','PARENT','ADMIN')")
public class LearningPlanController {
    private final LearningPlanService plans;
    public LearningPlanController(LearningPlanService plans) { this.plans=plans; }
    public record CreatePlan(UUID studentId,@NotBlank @Size(max=32) String subject,@NotBlank @Size(max=120) String title,
            @NotBlank @Size(max=2000) String longTermGoal) {}
    public record UpdatePlan(@NotNull @Min(0) Long revision,@NotBlank @Size(max=32) String subject,@NotBlank @Size(max=120) String title,
            @NotBlank @Size(max=2000) String longTermGoal,@NotNull LearningPlan.Status status) {}
    public record Milestone(@NotNull @Min(0) Long revision,@NotBlank @Size(max=120) String title,
            @NotBlank @Size(max=2000) String goal,LocalDate targetDate,@NotNull Boolean completed) {}
    public record TaskLink(@NotNull UUID taskId,@NotNull @Min(0) Long revision) {}
    public record NewTask(@NotNull UUID bookingId,@NotNull @Min(0) Long revision,@NotBlank @Size(max=120) String title,
            @NotBlank @Size(max=2000) String description,@FutureOrPresent java.time.Instant deadline) {}
    @GetMapping("/students") public Object students() { return plans.students(); }
    @GetMapping public Object list(@RequestParam(required=false) UUID studentId,@RequestParam(required=false) UUID childProfileId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return plans.list(studentId,childProfileId,page,size);
    }
    @GetMapping("/{id}") public Object detail(@PathVariable UUID id) { return plans.detail(id); }
    @GetMapping("/{id}/history") public Object history(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return plans.history(id,page,size); }
    @GetMapping("/{id}/eligible-tasks") public Object tasks(@PathVariable UUID id) { return plans.eligibleTasks(id); }
    @GetMapping("/{id}/eligible-lessons") public Object lessons(@PathVariable UUID id) { return plans.eligibleLessons(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Object create(@Valid @RequestBody CreatePlan r) { return plans.create(r.studentId(),r.subject(),r.title(),r.longTermGoal()); }
    @PutMapping("/{id}") public Object update(@PathVariable UUID id,@Valid @RequestBody UpdatePlan r) {
        return plans.update(id,r.revision(),r.subject(),r.title(),r.longTermGoal(),r.status());
    }
    @PostMapping("/{id}/milestones") @ResponseStatus(HttpStatus.CREATED)
    public Object milestone(@PathVariable UUID id,@Valid @RequestBody Milestone r) {
        if(r.completed()) throw new IllegalArgumentException("新增阶段目标应为待完成");
        return plans.addMilestone(id,r.revision(),r.title(),r.goal(),r.targetDate());
    }
    @PutMapping("/{id}/milestones/{milestoneId}") public Object updateMilestone(@PathVariable UUID id,@PathVariable UUID milestoneId,
            @Valid @RequestBody Milestone r) {
        return plans.updateMilestone(id,milestoneId,r.revision(),r.title(),r.goal(),r.targetDate(),r.completed());
    }
    @PostMapping("/{id}/tasks") @ResponseStatus(HttpStatus.CREATED)
    public Object link(@PathVariable UUID id,@Valid @RequestBody TaskLink r) { return plans.linkTask(id,r.taskId(),r.revision()); }
    @PostMapping("/{id}/tasks/new") @ResponseStatus(HttpStatus.CREATED)
    public Object newTask(@PathVariable UUID id,@Valid @RequestBody NewTask r) {
        return plans.createTask(id,r.revision(),r.bookingId(),r.title(),r.description(),r.deadline());
    }
    @DeleteMapping("/{id}/tasks/{taskId}") public Object unlink(@PathVariable UUID id,@PathVariable UUID taskId,@RequestParam long revision) {
        return plans.unlinkTask(id,taskId,revision);
    }
}
