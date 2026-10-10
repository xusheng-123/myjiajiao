package com.nurture.learning.app;

import com.nurture.learning.domain.*;
import com.nurture.learning.infra.*;
import com.nurture.booking.app.BookingApplicationService;
import com.nurture.parent.app.ParentApplicationService;
import com.nurture.student.app.StudentApplicationService;
import com.nurture.identity.app.IdentityApplicationService;
import com.nurture.shared.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.*;
import java.time.*;
import java.util.*;

@Service
public class LearningPlanService {
    private final LearningPlanRepository plans;
    private final PlanMilestoneRepository milestones;
    private final PlanTaskLinkRepository links;
    private final PlanChangeRepository changes;
    private final LearningTaskRepository tasks;
    private final LearningApplicationService learning;
    private final BookingApplicationService booking;
    private final ParentApplicationService parent;
    private final StudentApplicationService students;
    private final IdentityApplicationService identity;
    public LearningPlanService(LearningPlanRepository plans,PlanMilestoneRepository milestones,
            PlanTaskLinkRepository links,PlanChangeRepository changes,LearningTaskRepository tasks,
            LearningApplicationService learning,BookingApplicationService booking,ParentApplicationService parent,
            StudentApplicationService students,IdentityApplicationService identity) {
        this.plans=plans; this.milestones=milestones; this.links=links; this.changes=changes; this.tasks=tasks;
        this.learning=learning; this.booking=booking; this.parent=parent; this.students=students; this.identity=identity;
    }
    public record MilestoneView(UUID milestoneId,String title,String goal,LocalDate targetDate,boolean completed) {}
    public record TaskView(UUID taskId,String title,String status,String submission,String feedback,boolean canSubmit,boolean canFeedback) {}
    public record PlanView(UUID planId,UUID studentId,String subject,String title,String longTermGoal,
            String status,long revision,List<MilestoneView> milestones,List<TaskView> tasks,
            long milestonesCompleted,long tasksCompleted,long lessonsCompleted,Instant updatedAt) {}
    public record ChangeView(UUID changeId,long revision,String action,String actorName,String details,Instant at) {}

    private void access(UUID studentId,boolean write) {
        var caller=CallerContext.require();
        switch(caller.role()) {
            case STUDENT -> { if(!caller.accountId().equals(studentId)) throw new AccessDeniedException("仅可维护本人的学习计划"); }
            case TEACHER -> { if(!booking.hasLearningRelation(caller.accountId(),studentId))
                throw new AccessDeniedException("仅有已确认或已完成课程的教师可以维护该学生计划"); }
            case PARENT -> { if(write || !parent.isActiveGuardian(caller.accountId(),studentId))
                throw new AccessDeniedException("家长仅可查看有效绑定孩子的计划"); }
            case ADMIN -> { if(write) throw new AccessDeniedException("计划由学生与授课教师维护"); }
            default -> throw new AccessDeniedException("无权访问学习计划");
        }
    }
    private UUID target(UUID studentId,UUID childProfileId) {
        if(studentId!=null && childProfileId!=null) throw new IllegalArgumentException("只能指定一个学生目标");
        if(childProfileId!=null) {
            if(CallerContext.require().role()!=CallerRole.PARENT) throw new AccessDeniedException("子女入口仅家长使用");
            var id=students.profileAccountIdOf(childProfileId);
            if(id==null) throw new AccessDeniedException("无有效监护绑定");
            return id;
        }
        if(studentId!=null) return studentId;
        if(CallerContext.require().role()!=CallerRole.STUDENT) throw new IllegalArgumentException("请选择学生");
        return CallerContext.require().accountId();
    }
    private Pageable page(int page,int size) {
        if(page<0 || page>10000 || size<1 || size>50) throw new IllegalArgumentException("分页参数超出范围");
        return PageRequest.of(page,size);
    }
    public record StudentView(UUID studentId,String displayName) {}
    @Transactional(readOnly=true)
    public List<StudentView> students() {
        requireTeacher(); var teacherId=CallerContext.require().accountId();
        return booking.studentsOfTeacher(teacherId).stream().filter(id->booking.hasLearningRelation(teacherId,id))
                .map(id->new StudentView(id,identity.displayNameOf(id).orElse("学生"))).toList();
    }
    @Transactional(readOnly=true)
    public Page<PlanView> list(UUID studentId,UUID childProfileId,int page,int size) {
        var id=target(studentId,childProfileId); access(id,false);
        return plans.findByStudentIdOrderByCreatedAtDesc(id,page(page,size)).map(this::view);
    }
    @Transactional(readOnly=true)
    public PlanView detail(UUID id) {
        var p=require(id); access(p.getStudentId(),false); return view(p);
    }
    @Transactional(readOnly=true)
    public Page<ChangeView> history(UUID id,int page,int size) {
        var p=require(id); access(p.getStudentId(),false);
        return changes.findByPlanIdOrderByRevisionDesc(id,page(page,size)).map(c->new ChangeView(c.getId(),c.getRevision(),
                c.getAction(),identity.displayNameOf(c.getActorId()).orElse("成员"),c.getDetails(),c.getCreatedAt()));
    }
    @Transactional(readOnly=true)
    public List<TaskView> eligibleTasks(UUID id) {
        var p=require(id); access(p.getStudentId(),false);
        return tasks.findByStudentIdOrderByCreatedAtDesc(p.getStudentId()).stream().map(this::taskView).toList();
    }
    @Transactional(readOnly=true)
    public List<BookingApplicationService.TeachingLesson> eligibleLessons(UUID id) {
        var p=require(id); requireTeacher(); access(p.getStudentId(),true);
        return booking.workLessons(CallerContext.require().accountId(),p.getStudentId());
    }
    @Transactional
    public PlanView create(UUID studentId,String subject,String title,String goal) {
        var id=target(studentId,null); access(id,true);
        if(identity.adminAccountById(id).map(a->a.role()!=CallerRole.STUDENT).orElse(true))
            throw new IllegalArgumentException("计划必须属于学生账户");
        var p=plans.save(new LearningPlan(id,subject.trim(),title.trim(),goal.trim()));
        change(p,"CREATE",description(p)); return view(p);
    }
    @Transactional
    public PlanView update(UUID id,long revision,String subject,String title,String goal,LearningPlan.Status status) {
        var p=locked(id,revision,false); var old=description(p);
        p.update(subject.trim(),title.trim(),goal.trim(),status);
        change(p,"UPDATE","原计划："+old+"\n调整为："+description(p)); return view(p);
    }
    @Transactional
    public PlanView addMilestone(UUID id,long revision,String title,String goal,LocalDate date) {
        var p=locked(id,revision,true);
        if(milestones.countByPlanId(id)>=100) throw new IllegalStateException("每个计划最多100个阶段目标");
        milestones.save(new PlanMilestone(id,title.trim(),goal.trim(),date)); p.advance();
        change(p,"MILESTONE_CREATE","新增阶段目标「"+title.trim()+"」："+goal.trim()+"；目标日期："+date); return view(p);
    }
    @Transactional
    public PlanView updateMilestone(UUID id,UUID milestoneId,long revision,String title,String goal,LocalDate date,boolean completed) {
        var p=locked(id,revision,true);
        var m=milestones.findById(milestoneId).orElseThrow(()->new IllegalArgumentException("阶段目标不存在"));
        if(!m.getPlanId().equals(id)) throw new AccessDeniedException("阶段目标不属于此计划");
        var old="「"+m.getTitle()+"」："+m.getGoal()+"；日期："+m.getTargetDate()+"；达成："+m.isCompleted();
        m.update(title.trim(),goal.trim(),date,completed); p.advance();
        change(p,"MILESTONE_UPDATE","原阶段目标："+old+"\n调整为：「"+title.trim()+"」："+goal.trim()+"；日期："+date+"；达成："+completed);
        return view(p);
    }
    @Transactional
    public PlanView linkTask(UUID id,UUID taskId,long revision) {
        var p=locked(id,revision,true);
        var task=tasks.findById(taskId).orElseThrow(()->new IllegalArgumentException("任务不存在"));
        if(!task.getStudentId().equals(p.getStudentId())) throw new AccessDeniedException("不可关联其他学生的任务");
        if(links.findByPlanIdAndTaskId(id,taskId).isPresent()) throw new IllegalStateException("任务已经关联");
        if(links.countByPlanId(id)>=200) throw new IllegalStateException("每个计划最多200个关联任务");
        links.save(new PlanTaskLink(id,taskId)); p.advance(); change(p,"TASK_LINK","关联课后任务「"+task.getTitle()+"」"); return view(p);
    }
    @Transactional
    public PlanView createTask(UUID id,long revision,UUID bookingId,String title,String description,Instant deadline) {
        requireTeacher(); var p=locked(id,revision,true);
        if(!booking.studentOfWorkLesson(bookingId,CallerContext.require().accountId()).equals(p.getStudentId()))
            throw new AccessDeniedException("课程不属于计划中的学生");
        if(links.countByPlanId(id)>=200) throw new IllegalStateException("每个计划最多200个关联任务");
        var created=learning.createTask(CallerContext.require().accountId(),bookingId,title.trim(),description.trim(),"NORMAL",deadline);
        var taskId=UUID.fromString(created.get("taskId").toString());
        links.save(new PlanTaskLink(id,taskId)); p.advance();
        change(p,"TASK_CREATE","发布并关联课后任务「"+title.trim()+"」："+description.trim()); return view(p);
    }
    private void requireTeacher() {
        if(CallerContext.require().role()!=CallerRole.TEACHER) throw new AccessDeniedException("仅授课教师可以发布任务");
    }
    @Transactional
    public PlanView unlinkTask(UUID id,UUID taskId,long revision) {
        var p=locked(id,revision,true);
        var link=links.findByPlanIdAndTaskId(id,taskId).orElseThrow(()->new IllegalArgumentException("任务未关联"));
        var title=tasks.findById(taskId).map(LearningTask::getTitle).orElse("任务");
        links.delete(link); p.advance(); change(p,"TASK_UNLINK","取消关联课后任务「"+title+"」（任务本身保留）"); return view(p);
    }
    private LearningPlan require(UUID id) {
        return plans.findById(id).orElseThrow(()->new IllegalArgumentException("计划不存在"));
    }
    private LearningPlan locked(UUID id,long revision,boolean activeOnly) {
        if(revision<0) throw new IllegalArgumentException("计划版本必须为非负数");
        var p=plans.locked(id).orElseThrow(()->new IllegalArgumentException("计划不存在")); access(p.getStudentId(),true);
        if(p.getRevision()!=revision) throw new IllegalStateException("计划已被调整，请刷新后重试");
        if(activeOnly && p.getStatus()!=LearningPlan.Status.ACTIVE) throw new IllegalStateException("归档计划请先恢复再调整");
        return p;
    }
    private void change(LearningPlan p,String action,String details) {
        changes.save(new PlanChange(p.getId(),CallerContext.require().accountId(),p.getRevision(),action,details));
    }
    private String description(LearningPlan p) {
        return "科目："+p.getSubject()+"；标题："+p.getTitle()+"；长期目标："+p.getLongTermGoal()+"；状态："+(p.getStatus()==LearningPlan.Status.ACTIVE?"进行中":"已归档");
    }
    private TaskView taskView(LearningTask t) {
        var caller=CallerContext.require();
        return new TaskView(t.getId(),t.getTitle(),t.getStatus().name(),t.getSubmissionContent(),t.getFeedback(),
                caller.role()==CallerRole.STUDENT && caller.accountId().equals(t.getStudentId()),
                caller.role()==CallerRole.TEACHER && caller.accountId().equals(t.getTeacherId()));
    }
    private PlanView view(LearningPlan p) {
        var ms=milestones.findByPlanIdOrderByCreatedAtAsc(p.getId()).stream()
                .map(m->new MilestoneView(m.getId(),m.getTitle(),m.getGoal(),m.getTargetDate(),m.isCompleted())).toList();
        var ts=tasks.findAllById(links.findByPlanIdOrderByCreatedAtAsc(p.getId()).stream().map(PlanTaskLink::getTaskId).toList())
                .stream().map(this::taskView).toList();
        return new PlanView(p.getId(),p.getStudentId(),p.getSubject(),p.getTitle(),p.getLongTermGoal(),p.getStatus().name(),p.getRevision(),ms,ts,
                ms.stream().filter(MilestoneView::completed).count(),ts.stream().filter(t->t.status().equals("COMPLETED")).count(),
                booking.completedLessons(p.getStudentId(),p.getSubject()),p.getUpdatedAt());
    }
}
