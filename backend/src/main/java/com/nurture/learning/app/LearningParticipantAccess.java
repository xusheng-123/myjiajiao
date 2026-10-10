package com.nurture.learning.app;
import com.nurture.booking.app.BookingApplicationService;
import com.nurture.parent.app.ParentApplicationService;
import com.nurture.student.app.StudentApplicationService;
import com.nurture.shared.security.*;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.*;
import java.util.UUID;
@Component
public class LearningParticipantAccess {
    private final BookingApplicationService booking;
    private final ParentApplicationService parent;
    private final StudentApplicationService students;
    public LearningParticipantAccess(BookingApplicationService b,ParentApplicationService p,StudentApplicationService s){booking=b;parent=p;students=s;}
    public UUID target(UUID student,UUID child) {
        if(student!=null && child!=null) throw new IllegalArgumentException("只能选择一个学生目标");
        if(child!=null) {
            if(CallerContext.require().role()!=CallerRole.PARENT) throw new AccessDeniedException("子女入口仅家长使用");
            student=students.profileAccountIdOf(child);
            if(student==null) throw new AccessDeniedException("无有效监护绑定");
        }
        if(student==null) {
            if(CallerContext.require().role()!=CallerRole.STUDENT) throw new IllegalArgumentException("请选择学生");
            student=CallerContext.require().accountId();
        }
        return student;
    }
    public void read(UUID student) {
        var c=CallerContext.require();
        boolean allowed=switch(c.role()) {
            case STUDENT -> c.accountId().equals(student);
            case TEACHER -> booking.hasLearningRelation(c.accountId(),student);
            case PARENT -> parent.isActiveGuardian(c.accountId(),student);
            default -> false;
        };
        if(!allowed) throw new AccessDeniedException("仅本人、有效授课教师或有效监护家长可查看");
    }
    public void maintain(UUID student) {
        var role=CallerContext.require().role();
        if(role!=CallerRole.STUDENT && role!=CallerRole.TEACHER) throw new AccessDeniedException("仅学生与授课教师可以维护");
        read(student);
    }
    public static Pageable page(int page,int size) {
        if(page<0 || page>10000 || size<1 || size>50) throw new IllegalArgumentException("分页参数超出范围");
        return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt","id"));
    }
}
