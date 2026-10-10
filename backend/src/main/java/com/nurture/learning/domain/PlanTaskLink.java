package com.nurture.learning.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="plan_task_links")
public class PlanTaskLink extends AggregateRoot {
    @Column(nullable=false) private UUID planId;
    @Column(nullable=false) private UUID taskId;
    protected PlanTaskLink() {}
    public PlanTaskLink(UUID planId,UUID taskId) { this.planId=planId; this.taskId=taskId; }
    public UUID getTaskId() { return taskId; }
}
