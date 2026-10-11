package com.nurture.booking.app;
import com.nurture.booking.infra.BookingRepository;
import com.nurture.scheduling.app.SchedulingApplicationService;
import com.nurture.parent.app.ParentApplicationService;
import com.nurture.student.app.StudentApplicationService;
import com.nurture.shared.security.*;
import com.nurture.shared.calendar.ICalendarWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
@Service
public class BookingCalendarService {
    private final BookingRepository bookings;private final SchedulingApplicationService scheduling;
    private final ParentApplicationService parent;private final StudentApplicationService students;
    public BookingCalendarService(BookingRepository b,SchedulingApplicationService s,ParentApplicationService p,StudentApplicationService st){bookings=b;scheduling=s;parent=p;students=st;}
    @Transactional(readOnly=true)
    public String export(UUID student,UUID child,OffsetDateTime from,OffsetDateTime to) {
        if(from==null)from=OffsetDateTime.now();if(to==null)to=from.plusDays(30);
        if(!to.isAfter(from) || Duration.between(from,to).compareTo(Duration.ofDays(180))>0)throw new IllegalArgumentException("日历范围须为1至180天");
        if(student!=null && child!=null)throw new IllegalArgumentException("只能选择一个学生目标");
        var c=CallerContext.require();List<ICalendarWriter.Event> events;
        if(c.role()==CallerRole.TEACHER) {
            if(student!=null || child!=null)throw new AccessDeniedException("教师只能导出自己的档期");
            final var end=to;
            events=scheduling.calendarViewsOf(c.accountId(),from,to).stream().filter(s->!s.status().equals("BLOCKED") && s.startAt().isBefore(end))
                .map(s->new ICalendarWriter.Event(s.slotId(),switch(s.status()){case "OPEN"->"可用教学档期";case "HELD"->"待确认教学档期";default->"已预约教学档期";},s.startAt(),s.endAt(),!s.status().equals("BOOKED"))).toList();
        } else {
            if(child!=null){if(c.role()!=CallerRole.PARENT)throw new AccessDeniedException("子女入口仅家长使用");student=students.profileAccountIdOf(child);if(student==null)throw new AccessDeniedException("无有效监护绑定");}
            if(c.role()==CallerRole.STUDENT){if(student!=null && !student.equals(c.accountId()))throw new AccessDeniedException("仅可导出本人课程");student=c.accountId();}
            else if(c.role()!=CallerRole.PARENT || student==null || !parent.isActiveGuardian(c.accountId(),student))throw new AccessDeniedException("家长仅可导出有效绑定孩子的课程");
            var rows=bookings.calendarBookings(student,from,to);if(rows.size()>2000)throw new IllegalArgumentException("课程过多，请缩小导出日期范围");
            events=rows.stream().map(b->{var s=scheduling.slotOf(b.getSlotId()).orElseThrow();return new ICalendarWriter.Event(b.getId(),b.getSubject()+"课程"+(b.getStatus()==com.nurture.booking.domain.Booking.BookingStatus.REQUESTED?"（待审批）":""),s.startAt(),s.endAt(),b.getStatus()==com.nurture.booking.domain.Booking.BookingStatus.REQUESTED);}).toList();
        }
        if(events.size()>2000)throw new IllegalArgumentException("档期过多，请缩小导出日期范围");return ICalendarWriter.write(events);
    }
}
