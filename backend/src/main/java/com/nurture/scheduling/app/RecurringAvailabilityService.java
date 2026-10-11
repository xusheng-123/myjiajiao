package com.nurture.scheduling.app;
import com.nurture.scheduling.domain.*;
import com.nurture.scheduling.infra.*;
import com.nurture.shared.security.*;
import com.nurture.shared.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
@Service
public class RecurringAvailabilityService {
    public record ExceptionTime(@NotNull LocalDate date,@NotNull LocalTime startTime) {}
    public record Rule(@NotNull LocalDate startDate,@NotNull LocalTime startTime,@NotBlank @Size(max=80) String timeZone,
            @Min(15) @Max(180) int durationMinutes,@Min(1) @Max(12) int weekCount,
            @NotNull @Size(max=12) List<@NotNull LocalDate> skipDates,@NotNull @Size(max=12) List<@Valid ExceptionTime> exceptions) {}
    public record Conflict(UUID slotId,String status,OffsetDateTime startAt,OffsetDateTime endAt) {}
    public record Occurrence(LocalDate date,OffsetDateTime startAt,OffsetDateTime endAt,boolean skipped,List<Conflict> conflicts) {}
    public record Preview(List<Occurrence> occurrences,int publishCount,boolean ready) {}
    public record Created(UUID seriesId,String timeZone,List<SchedulingApplicationService.SlotView> slots) {}
    public record SeriesSummary(UUID seriesId,String timeZone,LocalDate startDate,LocalTime startTime,int weekCount) {}
    private final AvailabilitySlotRepository slots;
    private final AvailabilitySeriesRepository series;
    private final SchedulingPublicationLock lock;
    private final AuditService audit;
    public RecurringAvailabilityService(AvailabilitySlotRepository s,AvailabilitySeriesRepository r,SchedulingPublicationLock l,AuditService a){slots=s;series=r;lock=l;audit=a;}
    private UUID teacher(){var c=CallerContext.require();if(c.role()!=CallerRole.TEACHER)throw new AccessDeniedException("重复档期由教师发布");return c.accountId();}
    private Preview calculate(Rule r,UUID teacher) {
        if(r.startDate()==null || r.startTime()==null || r.weekCount()<1 || r.weekCount()>12 || r.durationMinutes()<15 || r.durationMinutes()>180)
            throw new IllegalArgumentException("重复规则无效，最多12周，每次15至180分钟");
        ZoneId zone;try{zone=ZoneId.of(r.timeZone());}catch(RuntimeException e){throw new IllegalArgumentException("时区无效");}
        var dates=new HashSet<LocalDate>();for(int i=0;i<r.weekCount();i++)dates.add(r.startDate().plusWeeks(i));
        var skipped=new HashSet<>(r.skipDates());if(skipped.size()!=r.skipDates().size() || !dates.containsAll(skipped))throw new IllegalArgumentException("跳过日期必须属于本次重复规则且不能重复");
        var exceptions=new HashMap<LocalDate,LocalTime>();for(var ex:r.exceptions()) {
            if(ex==null || !dates.contains(ex.date()) || skipped.contains(ex.date()) || exceptions.putIfAbsent(ex.date(),ex.startTime())!=null)
                throw new IllegalArgumentException("例外日期必须在本次规则内，不可重复或同时跳过");
        }
        List<Occurrence> result=new ArrayList<>();int count=0;boolean ready=true;
        for(int i=0;i<r.weekCount();i++) {
            var date=r.startDate().plusWeeks(i);
            if(skipped.contains(date)){result.add(new Occurrence(date,null,null,true,List.of()));continue;}
            var local=LocalDateTime.of(date,exceptions.getOrDefault(date,r.startTime()));
            var offsets=zone.getRules().getValidOffsets(local);
            if(offsets.size()!=1)throw new IllegalArgumentException("所选时间处于夏令时跳转或重复区间，请调整该日时间");
            var start=OffsetDateTime.of(local,offsets.get(0));var end=start.toInstant().plusSeconds(r.durationMinutes()*60L).atZone(zone).toOffsetDateTime();
            boolean skip=skipped.contains(date);
            if(!skip && (!start.isAfter(OffsetDateTime.now()) || start.isAfter(OffsetDateTime.now().plusDays(180))))
                throw new IllegalArgumentException("发布档期须在未来180天内");
            var conflicts=skip?List.<Conflict>of():slots.findOverlapping(teacher,start,end).stream().map(s->new Conflict(s.getId(),s.getStatus().name(),s.getStartAt(),s.getEndAt())).toList();
            if(!skip){count++;if(!conflicts.isEmpty())ready=false;}
            result.add(new Occurrence(date,start,end,skip,conflicts));
        }
        if(count==0)throw new IllegalArgumentException("请至少保留一次档期");
        return new Preview(result,count,ready);
    }
    @Transactional(readOnly=true) public Preview preview(Rule r){return calculate(r,teacher());}
    @Transactional public Created publish(Rule r) {
        var teacher=teacher();lock.lock(teacher);var preview=calculate(r,teacher);
        if(!preview.ready())throw new IllegalStateException("仍有档期冲突，请先跳过或调整例外后重新预览");
        var saved=series.saveAndFlush(new AvailabilitySeries(teacher,r.timeZone(),r.startDate(),r.startTime(),r.durationMinutes(),r.weekCount()));
        var result=new ArrayList<SchedulingApplicationService.SlotView>();
        for(var occurrence:preview.occurrences())if(!occurrence.skipped()) {
            var slot=new AvailabilitySlot(teacher,occurrence.startAt(),occurrence.endAt());slot.attachSeries(saved.getId());
            result.add(SchedulingApplicationService.SlotView.from(slots.save(slot)));
        }
        slots.flush();audit.record("AVAILABILITY_SERIES_PUBLISH","SUCCESS",Map.of("seriesId",saved.getId(),"rule",r),"材料化有限重复档期，不创建预约或订单");
        return new Created(saved.getId(),saved.getTimeZone(),result);
    }
    @Transactional(readOnly=true) public Created detail(UUID id) {
        var s=series.findById(id).orElseThrow(()->new IllegalArgumentException("重复档期不存在"));
        if(!s.getTeacherId().equals(teacher()))throw new AccessDeniedException("仅归属教师可查看重复档期");
        return new Created(id,s.getTimeZone(),slots.findBySeriesIdOrderByStartAtAsc(id).stream().map(SchedulingApplicationService.SlotView::from).toList());
    }
    @Transactional(readOnly=true)
    public org.springframework.data.domain.Page<SeriesSummary> list(int page,int size) {
        if(page<0 || page>10000 || size<1 || size>50)throw new IllegalArgumentException("分页参数超出范围");
        return series.findByTeacherIdOrderByCreatedAtDesc(teacher(),org.springframework.data.domain.PageRequest.of(page,size))
            .map(s->new SeriesSummary(s.getId(),s.getTimeZone(),s.getStartDate(),s.getStartTime(),s.getWeekCount()));
    }
}
