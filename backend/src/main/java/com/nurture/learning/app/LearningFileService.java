package com.nurture.learning.app;
import com.nurture.learning.domain.*;
import com.nurture.learning.infra.*;
import com.nurture.booking.app.BookingApplicationService;
import com.nurture.shared.security.*;
import com.nurture.shared.storage.FileStorageService;
import com.nurture.shared.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.slf4j.LoggerFactory;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
@Service
public class LearningFileService {
    public static final long OWNER_QUOTA=200L*1024*1024;
    private final LearningFileRepository files;
    private final LearningStorageQuotaRepository quotas;
    private final LearningTaskRepository tasks;
    private final QuestionRepository questions;
    private final BookingApplicationService booking;
    private final LearningParticipantAccess access;
    private final LearningFileValidator validator;
    private final FileStorageService storage;
    private final AuditService audit;
    private final com.nurture.identity.app.IdentityApplicationService identity;
    public LearningFileService(LearningFileRepository f,LearningStorageQuotaRepository q,LearningTaskRepository t,QuestionRepository questions,
            BookingApplicationService b,LearningParticipantAccess a,LearningFileValidator v,FileStorageService s,AuditService au,com.nurture.identity.app.IdentityApplicationService i) {
        files=f;quotas=q;tasks=t;this.questions=questions;booking=b;access=a;validator=v;storage=s;audit=au;identity=i;
    }
    public record View(UUID fileId,String filename,String contentType,long sizeBytes,String category,UUID bookingId,UUID taskId,
            UUID questionId,String sha256,boolean canDelete,Instant createdAt) {}
    public record Download(String filename,String contentType,byte[] content) {}
    public record Quota(long storedBytes,long limitBytes,long maxFileBytes) {}
    public record Context(UUID studentId,String category,boolean canUpload) {}
    public record TaskWork(UUID taskId,String title,String description,String status,String submission,String feedback,boolean canSubmit,boolean canFeedback) {}
    public record QuestionWork(UUID questionId,String title,String content,String answer,String status,boolean canAnswer) {}
    @Transactional(readOnly=true)
    public Page<QuestionWork> questions(UUID student,UUID child,int page,int size) {
        var target=access.target(student,child);access.read(target);var c=CallerContext.require();
        var teacher=c.role()==CallerRole.TEACHER?c.accountId():null;
        return questions.findAll((root,query,cb)->teacher==null?cb.equal(root.get("studentId"),target):
                cb.and(cb.equal(root.get("studentId"),target),cb.equal(root.get("teacherId"),teacher)),LearningParticipantAccess.page(page,size))
            .map(q->new QuestionWork(q.getId(),q.getTitle(),q.getContent(),q.getAnswer(),q.getStatus().name(),c.role()==CallerRole.TEACHER && q.getStatus()!=Question.Status.CLOSED));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> teachers() {
        var c=CallerContext.require();if(c.role()!=CallerRole.STUDENT)throw new AccessDeniedException("教师选择由学生使用");
        return booking.learningTeachers(c.accountId()).stream().map(id->Map.<String,Object>of("teacherId",id,"displayName",identity.displayNameOf(id).orElse("教师"))).toList();
    }
    @Transactional(readOnly=true)
    public Page<TaskWork> work(UUID student,UUID child,int page,int size) {
        var target=access.target(student,child);access.read(target);var c=CallerContext.require();
        var teacher=c.role()==CallerRole.TEACHER?c.accountId():null;
        return tasks.findAll((root,query,cb)->teacher==null?cb.equal(root.get("studentId"),target):
                cb.and(cb.equal(root.get("studentId"),target),cb.equal(root.get("teacherId"),teacher)),LearningParticipantAccess.page(page,size)).map(t->new TaskWork(t.getId(),t.getTitle(),t.getDescription(),t.getStatus().name(),
                t.getSubmissionContent(),t.getFeedback(),c.role()==CallerRole.STUDENT && t.getStatus()==LearningTask.Status.PENDING,
                c.role()==CallerRole.TEACHER && t.getStatus()==LearningTask.Status.SUBMITTED));
    }
    @Transactional(readOnly=true)
    public Context context(UUID taskId,UUID questionId) {
        if((taskId==null)==(questionId==null)) throw new IllegalArgumentException("必须指定一个任务或答疑");
        var c=CallerContext.require();UUID student,teacher;boolean open;
        if(taskId!=null){var t=task(taskId);student=t.getStudentId();teacher=t.getTeacherId();open=t.getStatus()==LearningTask.Status.PENDING;}
        else {var q=question(questionId);student=q.getStudentId();teacher=q.getTeacherId();open=q.getStatus()!=Question.Status.CLOSED;}
        access.read(student);
        if(c.role()==CallerRole.TEACHER && !c.accountId().equals(teacher)) throw new AccessDeniedException("此附件仅归属教师可查看");
        String category=questionId!=null?"QUESTION":c.role()==CallerRole.TEACHER?"TASK_MATERIAL":"TASK_SUBMIT";
        return new Context(student,category,open && (c.role()==CallerRole.STUDENT || c.role()==CallerRole.TEACHER));
    }
    private View view(LearningFile f){return new View(f.getId(),f.getOriginalFilename(),f.getContentType(),f.getSizeBytes(),f.getCategory().name(),
            f.getBookingId(),f.getTaskId(),f.getQuestionId(),f.getSha256(),f.getOwnerId().equals(CallerContext.require().accountId()) &&
            (f.getCategory()!=LearningFile.Category.TASK_SUBMIT || task(f.getTaskId()).getStatus()==LearningTask.Status.PENDING),f.getCreatedAt());}
    private void read(LearningFile f) {
        access.read(f.getStudentId());
        if(CallerContext.require().role()==CallerRole.TEACHER && !f.getTeacherId().equals(CallerContext.require().accountId()))
            throw new AccessDeniedException("仅此课程、任务或答疑的教师可读取附件");
        if(f.isDeleted()) throw new IllegalStateException("文件已移除");
    }
    @Transactional(readOnly=true)
    public Page<View> list(UUID student,UUID child,UUID taskId,UUID questionId,int page,int size) {
        var target=access.target(student,child);access.read(target);
        if(taskId!=null && questionId!=null) throw new IllegalArgumentException("只能筛选一个关联对象");
        if(taskId!=null && !task(taskId).getStudentId().equals(target) || questionId!=null && !question(questionId).getStudentId().equals(target))
            throw new AccessDeniedException("附件关联不属于该学生");
        var teacher=CallerContext.require().role()==CallerRole.TEACHER?CallerContext.require().accountId():null;
        return files.findAll((root,query,cb)->{
            List<jakarta.persistence.criteria.Predicate> p=new ArrayList<>();p.add(cb.equal(root.get("studentId"),target));p.add(cb.isFalse(root.get("deleted")));
            if(teacher!=null)p.add(cb.equal(root.get("teacherId"),teacher));if(taskId!=null)p.add(cb.equal(root.get("taskId"),taskId));if(questionId!=null)p.add(cb.equal(root.get("questionId"),questionId));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },LearningParticipantAccess.page(page,size)).map(this::view);
    }
    @Transactional(readOnly=true)
    public Quota quota() {
        var role=CallerContext.require().role();
        if(role!=CallerRole.STUDENT && role!=CallerRole.TEACHER) throw new AccessDeniedException("仅上传者可查看个人容量");
        return new Quota(quotas.findById(CallerContext.require().accountId()).map(LearningStorageQuota::getStoredBytes).orElse(0L),OWNER_QUOTA,LearningFileValidator.MAX_BYTES);
    }
    @Transactional(readOnly=true)
    public List<BookingApplicationService.TeachingLesson> lessons(UUID student) {
        if(CallerContext.require().role()!=CallerRole.TEACHER) throw new AccessDeniedException("教学资料由教师上传");
        access.read(student);return booking.workLessons(CallerContext.require().accountId(),student);
    }
    private LearningTask task(UUID id){return tasks.findById(id).orElseThrow(()->new IllegalArgumentException("任务不存在"));}
    private Question question(UUID id){return questions.findById(id).orElseThrow(()->new IllegalArgumentException("答疑不存在"));}
    @Transactional
    public View upload(LearningFile.Category category,UUID contextId,String filename,String contentType,byte[] content) {
        var caller=CallerContext.require();UUID student,teacher,bookingId=null,taskId=null,questionId=null;
        switch(category) {
            case MATERIAL -> {
                if(caller.role()!=CallerRole.TEACHER) throw new AccessDeniedException("教学资料由课程教师上传");
                bookingId=contextId;teacher=caller.accountId();student=booking.studentOfWorkLesson(bookingId,teacher);
            }
            case TASK_MATERIAL,TASK_SUBMIT -> {
                taskId=contextId;var task=tasks.locked(taskId).orElseThrow(()->new IllegalArgumentException("任务不存在"));student=task.getStudentId();teacher=task.getTeacherId();
                boolean studentSubmit=category==LearningFile.Category.TASK_SUBMIT;
                if(studentSubmit?caller.role()!=CallerRole.STUDENT || !student.equals(caller.accountId()):
                        caller.role()!=CallerRole.TEACHER || !teacher.equals(caller.accountId()))
                    throw new AccessDeniedException("只能由此任务的学生上传作业、归属教师上传资料");
                if(task.getStatus()!=LearningTask.Status.PENDING) throw new IllegalStateException("任务提交后附件已固定，不能补传");
            }
            case QUESTION -> {
                questionId=contextId;var q=questions.locked(questionId).orElseThrow(()->new IllegalArgumentException("答疑不存在"));student=q.getStudentId();teacher=q.getTeacherId();
                if(!(caller.role()==CallerRole.STUDENT && student.equals(caller.accountId())) &&
                        !(caller.role()==CallerRole.TEACHER && teacher.equals(caller.accountId())))
                    throw new AccessDeniedException("仅答疑双方可以上传附件");
                if(q.getStatus()==Question.Status.CLOSED) throw new IllegalStateException("答疑已关闭，不能补传");
            }
            default -> throw new IllegalArgumentException("不支持此类附件");
        }
        access.maintain(student);
        var type=validator.validate(filename,contentType,content);
        quotas.ensure(caller.accountId());var quota=quotas.locked(caller.accountId()).orElseThrow();
        if(quota.getStoredBytes()+content.length>OWNER_QUOTA) throw new IllegalStateException("个人资料容量上限200MB，已移除文件仍计保留容量");
        // 任务/答疑行锁与个人容量行锁串行化上传；课程资料只有归属教师，个人容量锁也串行化同课上传。
        long count=category==LearningFile.Category.MATERIAL?files.countByBookingIdAndDeletedFalse(contextId):
                category==LearningFile.Category.QUESTION?files.countByQuestionIdAndDeletedFalse(contextId):files.countByTaskIdAndDeletedFalse(contextId);
        if(count>=20) throw new IllegalStateException("每节课程、任务或答疑最多20个有效附件");
        var stored=storage.store("learning",filename,type,content);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{storage.delete(stored.storageKey());}
                catch(RuntimeException e){LoggerFactory.getLogger(LearningFileService.class).error("学习附件事务回滚后文件清理失败",e);}}
        });
        String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("内容校验不可用",e);}
        var saved=files.saveAndFlush(new LearningFile(caller.accountId(),student,teacher,bookingId,taskId,questionId,category,
                stored.storageKey(),filename,type,content.length,hash));quota.add(content.length);
        audit.record("LEARNING_FILE_UPLOAD","SUCCESS",Map.of("fileId",saved.getId(),"sha256",hash),"授权学习附件上传");
        return view(saved);
    }
    @Transactional
    public Download download(UUID id) {
        var f=files.findById(id).orElseThrow(()->new IllegalArgumentException("附件不存在"));read(f);
        var bytes=storage.load(f.getStorageKey());
        audit.record("LEARNING_FILE_DOWNLOAD","SUCCESS",Map.of("fileId",id),"受控附件下载");
        return new Download(f.getOriginalFilename(),f.getContentType(),bytes);
    }
    @Transactional
    public void remove(UUID id) {
        var f=files.locked(id).orElseThrow(()->new IllegalArgumentException("附件不存在"));
        access.maintain(f.getStudentId());
        if(!f.getOwnerId().equals(CallerContext.require().accountId())) throw new AccessDeniedException("仅上传者可以移除附件");
        if(f.isDeleted()) return;
        if(f.getCategory()==LearningFile.Category.TASK_SUBMIT && tasks.locked(f.getTaskId()).orElseThrow().getStatus()!=LearningTask.Status.PENDING)
            throw new IllegalStateException("已提交的作业附件不可移除");
        f.remove();audit.record("LEARNING_FILE_REMOVE","SUCCESS",Map.of("fileId",id),"逻辑移除，文件和容量保留供审计");
    }
}
