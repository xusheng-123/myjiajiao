package com.nurture.learning.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="plan_changes")
public class PlanChange extends AggregateRoot {
    @Column(nullable=false) private UUID planId;
    @Column(nullable=false) private UUID actorId;
    @Column(nullable=false) private long revision;
    @Column(nullable=false,length=32) private String action;
    @Column(nullable=false,columnDefinition="text") private String details;
    protected PlanChange() {}
    public PlanChange(UUID planId,UUID actorId,long revision,String action,String details) {
        this.planId=planId; this.actorId=actorId; this.revision=revision; this.action=action; this.details=details;
    }
    public UUID getActorId() { return actorId; }
    public long getRevision() { return revision; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
}
