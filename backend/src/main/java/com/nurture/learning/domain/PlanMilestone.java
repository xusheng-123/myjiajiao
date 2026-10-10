package com.nurture.learning.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name="plan_milestones")
public class PlanMilestone extends AggregateRoot {
    @Column(nullable=false) private UUID planId;
    @Column(nullable=false,length=120) private String title;
    @Column(nullable=false,length=2000) private String goal;
    private LocalDate targetDate;
    @Column(nullable=false) private boolean completed;
    protected PlanMilestone() {}
    public PlanMilestone(UUID planId,String title,String goal,LocalDate targetDate) {
        this.planId=planId; update(title,goal,targetDate,false);
    }
    public void update(String title,String goal,LocalDate date,boolean completed) {
        this.title=title; this.goal=goal; this.targetDate=date; this.completed=completed;
    }
    public UUID getPlanId() { return planId; }
    public String getTitle() { return title; }
    public String getGoal() { return goal; }
    public LocalDate getTargetDate() { return targetDate; }
    public boolean isCompleted() { return completed; }
}
