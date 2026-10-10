package com.nurture.learning.app;
import com.nurture.learning.domain.*;
import com.nurture.learning.infra.*;
import com.nurture.booking.app.BookingApplicationService;
import com.nurture.shared.security.*;
import com.nurture.shared.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import java.time.*;
import java.util.*;
@Service
public class MistakeNoteService {
    private final MistakeNoteRepository notes;
    private final LearningTaskRepository tasks;
    private final LearningParticipantAccess access;
    private final LearningApplicationService learning;
    private final BookingApplicationService booking;
    private final AuditService audit;
    public MistakeNoteService(MistakeNoteRepository n,LearningTaskRepository t,LearningParticipantAccess a,
            LearningApplicationService l,BookingApplicationService b,AuditService au){notes=n;tasks=t;access=a;learning=l;booking=b;audit=au;}
    public record View(UUID noteId,UUID studentId,String subject,String title,String questionText,String errorReason,
            String knowledgeTags,String status,LocalDate reviewDate,long revision,UUID taskId,String taskStatus,boolean canEdit) {}
    private View view(MistakeNote n) {
        var c=CallerContext.require();
        return new View(n.getId(),n.getStudentId(),n.getSubject(),n.getTitle(),n.getQuestionText(),n.getErrorReason(),n.getKnowledgeTags(),
                n.getStatus().name(),n.getReviewDate(),n.getRevision(),n.getTaskId(),n.getTaskId()==null?null:
                tasks.findById(n.getTaskId()).map(t->t.getStatus().name()).orElse(null),
                c.accountId().equals(n.getStudentId()) || (c.role()==CallerRole.TEACHER && c.accountId().equals(n.getCreatorId())));
    }
    @Transactional(readOnly=true)
    public Page<View> list(UUID student,UUID child,String subject,MistakeNote.Status status,String tag,LocalDate due,int page,int size) {
        var target=access.target(student,child);access.read(target);
        if(subject!=null && subject.length()>32 || tag!=null && (tag.length()>100 || tag.contains("%") || tag.contains("_")))
            throw new IllegalArgumentException("科目或知识点筛选超出范围");
        return notes.findAll((root,query,cb)->{
            List<jakarta.persistence.criteria.Predicate> p=new ArrayList<>();
            p.add(cb.equal(root.get("studentId"),target));p.add(cb.isFalse(root.get("deleted")));
            if(subject!=null)p.add(cb.equal(root.get("subject"),subject));if(status!=null)p.add(cb.equal(root.get("status"),status));
            if(tag!=null)p.add(cb.like(cb.lower(root.get("knowledgeTags")),"%"+tag.toLowerCase(Locale.ROOT)+"%"));
            if(due!=null)p.add(cb.lessThanOrEqualTo(root.get("reviewDate"),due));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },LearningParticipantAccess.page(page,size)).map(this::view);
    }
    @Transactional
    public View create(UUID student,String subject,String title,String question,String reason,String tags,LocalDate review) {
        var target=access.target(student,null);access.maintain(target);
        return view(notes.save(new MistakeNote(target,CallerContext.require().accountId(),subject,title,question,reason,tags,review)));
    }
    private MistakeNote locked(UUID id,long revision,boolean editor) {
        var n=notes.locked(id).orElseThrow(()->new IllegalArgumentException("错题不存在"));access.maintain(n.getStudentId());
        if(n.isDeleted()) throw new IllegalStateException("错题已移除");
        var caller=CallerContext.require().accountId();
        if(editor && !caller.equals(n.getStudentId()) && !caller.equals(n.getCreatorId()))
            throw new AccessDeniedException("教师仅可修改自己代录的错题");
        if(revision<0) throw new IllegalArgumentException("版本不能为负");
        if(n.getRevision()!=revision) throw new IllegalStateException("错题已被修改，请刷新后重试");
        return n;
    }
    @Transactional
    public View update(UUID id,long revision,String subject,String title,String question,String reason,String tags,LocalDate review,MistakeNote.Status status) {
        var n=locked(id,revision,true);n.update(subject,title,question,reason,tags,review,status);return view(n);
    }
    @Transactional
    public void remove(UUID id,long revision) {
        var n=locked(id,revision,true);n.remove();audit.record("MISTAKE_REMOVE","SUCCESS",Map.of("noteId",id),"逻辑移除错题，关联任务保留");
    }
    @Transactional(readOnly=true)
    public List<BookingApplicationService.TeachingLesson> lessons(UUID id) {
        var n=notes.findById(id).orElseThrow(()->new IllegalArgumentException("错题不存在"));access.read(n.getStudentId());
        if(n.isDeleted()) throw new IllegalStateException("错题已移除");
        if(CallerContext.require().role()!=CallerRole.TEACHER) throw new AccessDeniedException("仅教师可布置复习任务");
        return booking.workLessons(CallerContext.require().accountId(),n.getStudentId());
    }
    @Transactional
    public View convert(UUID id,long revision,UUID bookingId) {
        if(CallerContext.require().role()!=CallerRole.TEACHER) throw new AccessDeniedException("仅教师可布置复习任务");
        var n=locked(id,revision,false);
        if(n.getTaskId()!=null) throw new IllegalStateException("该错题已有复习任务，请查看原任务");
        if(!booking.studentOfWorkLesson(bookingId,CallerContext.require().accountId()).equals(n.getStudentId()))
            throw new AccessDeniedException("课程不属于此学生");
        var task=learning.createTask(CallerContext.require().accountId(),bookingId,"错题复习："+n.getTitle(),
                n.getQuestionText()+"\n错误原因："+n.getErrorReason()+"\n知识点："+n.getKnowledgeTags(),"NORMAL",null);
        n.link(UUID.fromString(task.get("taskId").toString()));return view(n);
    }
}
