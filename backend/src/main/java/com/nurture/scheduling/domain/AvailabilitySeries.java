package com.nurture.scheduling.domain;
import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="availability_series")
public class AvailabilitySeries extends AggregateRoot {
    @Column(nullable=false) private UUID teacherId;
    @Column(nullable=false,length=80) private String timeZone;
    @Column(nullable=false) private LocalDate startDate;
    @Column(nullable=false) private LocalTime startTime;
    @Column(nullable=false) private int durationMinutes;
    @Column(nullable=false) private int weekCount;
    protected AvailabilitySeries() {}
    public AvailabilitySeries(UUID teacher,String zone,LocalDate date,LocalTime time,int duration,int weeks){teacherId=teacher;timeZone=zone;startDate=date;startTime=time;durationMinutes=duration;weekCount=weeks;}
    public UUID getTeacherId(){return teacherId;}public String getTimeZone(){return timeZone;}
    public LocalDate getStartDate(){return startDate;}public LocalTime getStartTime(){return startTime;}
    public int getWeekCount(){return weekCount;}
}
