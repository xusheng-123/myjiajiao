package com.nurture.learning.app;
import com.nurture.learning.domain.*;
import com.nurture.learning.infra.*;
import com.nurture.booking.app.BookingApplicationService;
import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.shared.security.*;
import com.nurture.shared.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.*;
import java.util.*;
import java.time.Instant;
@Service
public class AssessmentService{
 private final LearningAssessmentRepository quizzes;private final AssessmentAttemptRepository attempts;private final LearningTaskRepository tasks;private final LessonRecordRepository records;
 private final LearningParticipantAccess access;private final BookingApplicationService booking;private final MessagingApplicationService messaging;private final AuditService audit;private final ObjectMapper json;
 public AssessmentService(LearningAssessmentRepository q,AssessmentAttemptRepository a,LearningTaskRepository t,LessonRecordRepository r,LearningParticipantAccess ac,BookingApplicationService b,MessagingApplicationService m,AuditService au,ObjectMapper j){quizzes=q;attempts=a;tasks=t;records=r;access=ac;booking=b;messaging=m;audit=au;json=j;}
 public record Question(String prompt,List<String> choices,Integer correctIndex){}
 public record AttemptView(UUID attemptId,List<Integer> answers,int correctCount,int totalCount,String teacherComment,long commentRevision,Instant submittedAt){}
 public record View(UUID assessmentId,UUID studentId,UUID bookingId,String subject,String title,String scope,String status,long revision,List<Question> questions,AttemptView attempt){}
 public record Score(UUID assessmentId,String subject,String title,int correctCount,int totalCount,Instant submittedAt,String teacherComment){}
 public record Report(long tasksTotal,long tasksCompleted,long lessonsCompleted,Page<Score> scores,List<Map<String,String>> classroomNotes,String notice){}
 private void teacher(){if(CallerContext.require().role()!=CallerRole.TEACHER)throw new AccessDeniedException("小测由授课教师录入与批注");}
 private static String text(String value,int max){if(value==null || value.isBlank() || value.length()>max)throw new IllegalArgumentException("内容不能为空或超长");return value.trim();}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("测评数据编码失败",e);}}
 private <T>T decode(String value,TypeReference<T> type){try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("测评数据读取失败",e);}}
 private List<Question> bank(LearningAssessment q){return decode(q.getQuestionsJson(),new TypeReference<List<Question>>(){});}
 private String validate(List<Question> list){if(list==null || list.isEmpty() || list.size()>20)throw new IllegalArgumentException("小测须包含1–20道单选客观题");for(var q:list){if(q==null)throw new IllegalArgumentException("题目不能为空");text(q.prompt(),1000);if(q.choices()==null || q.choices().size()<2 || q.choices().size()>6 || q.correctIndex()==null || q.correctIndex()<0 || q.correctIndex()>=q.choices().size())throw new IllegalArgumentException("选项须为2–6项且正确答案必须有效");q.choices().forEach(c->text(c,300));if(q.choices().stream().map(String::trim).distinct().count()!=q.choices().size())throw new IllegalArgumentException("选项不能重复");}return encode(list);}
 private void read(LearningAssessment q){access.read(q.getStudentId());var c=CallerContext.require();if(c.role()==CallerRole.TEACHER && !q.getTeacherId().equals(c.accountId()))throw new AccessDeniedException("仅此小测的授课教师可查看");if(q.getStatus()==LearningAssessment.Status.DRAFT && c.role()!=CallerRole.TEACHER)throw new AccessDeniedException("草稿仅归属教师可查看");}
 private LearningAssessment get(UUID id,boolean lock){var q=(lock?quizzes.locked(id):quizzes.findById(id)).orElseThrow(()->new IllegalArgumentException("小测不存在"));read(q);return q;}
 private void own(LearningAssessment q){teacher();if(!q.getTeacherId().equals(CallerContext.require().accountId()))throw new AccessDeniedException("仅创建教师可以维护此小测");}
 private View view(LearningAssessment q){var a=attempts.findByAssessmentId(q.getId()).orElse(null);boolean answers=CallerContext.require().role()==CallerRole.TEACHER || a!=null;
  var items=bank(q).stream().map(i->new Question(i.prompt(),i.choices(),answers?i.correctIndex():null)).toList();
  return new View(q.getId(),q.getStudentId(),q.getBookingId(),q.getSubject(),q.getTitle(),q.getScope(),q.getStatus().name(),q.getRevision(),items,a==null?null:new AttemptView(a.getId(),decode(a.getAnswersJson(),new TypeReference<List<Integer>>(){}),a.getCorrectCount(),a.getTotalCount(),a.getTeacherComment(),a.getCommentRevision(),a.getCreatedAt()));}
 @Transactional(readOnly=true) public Page<View> list(UUID student,UUID child,int page,int size){var target=access.target(student,child);access.read(target);var c=CallerContext.require();return quizzes.findAll((root,query,cb)->{
  List<jakarta.persistence.criteria.Predicate> p=new ArrayList<>();p.add(cb.equal(root.get("studentId"),target));if(c.role()==CallerRole.TEACHER)p.add(cb.equal(root.get("teacherId"),c.accountId()));else p.add(cb.notEqual(root.get("status"),LearningAssessment.Status.DRAFT));return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));},LearningParticipantAccess.page(page,size)).map(this::view);}
 @Transactional(readOnly=true) public View detail(UUID id){return view(get(id,false));}
 @Transactional public View create(UUID bookingId,String subject,String title,String scope,List<Question> questions){teacher();var teacher=CallerContext.require().accountId();var student=booking.studentOfWorkLesson(bookingId,teacher);access.maintain(student);var q=quizzes.saveAndFlush(new LearningAssessment(bookingId,teacher,student,text(subject,60),text(title,120),text(scope,1000),validate(questions)));audit.record("ASSESSMENT_CREATE","SUCCESS",Map.of("assessmentId",q.getId()),"有限客观题小测草稿");return view(q);}
 private void revision(LearningAssessment q,long value){if(q.getRevision()!=value)throw new IllegalStateException("小测已更新，请刷新后再操作");}
 @Transactional public View edit(UUID id,long revision,String title,String scope,List<Question> questions){var q=get(id,true);own(q);revision(q,revision);if(q.getStatus()!=LearningAssessment.Status.DRAFT)throw new IllegalStateException("发布后的题目与答案已固定");q.edit(text(title,120),text(scope,1000),validate(questions));audit.record("ASSESSMENT_EDIT","SUCCESS",Map.of("assessmentId",id,"revision",revision),"修改小测草稿");return view(quizzes.saveAndFlush(q));}
 @Transactional public View publish(UUID id,long revision){var q=get(id,true);own(q);revision(q,revision);if(q.getStatus()!=LearningAssessment.Status.DRAFT)throw new IllegalStateException("仅草稿可以发布");booking.studentOfWorkLesson(q.getBookingId(),q.getTeacherId());q.publish();messaging.notify(q.getStudentId(),"ASSESSMENT","有新的阶段小测",q.getTitle()+"；请在阶段小测与报告中查看。");audit.record("ASSESSMENT_PUBLISH","SUCCESS",Map.of("assessmentId",id),"题目与答案固定后发布");return view(quizzes.saveAndFlush(q));}
 @Transactional public View archive(UUID id,long revision){var q=get(id,true);own(q);revision(q,revision);if(q.getStatus()==LearningAssessment.Status.ARCHIVED)return view(q);if(q.getStatus()!=LearningAssessment.Status.PUBLISHED)throw new IllegalStateException("草稿仍为教师私有；仅已发布小测可以归档");q.archive();audit.record("ASSESSMENT_ARCHIVE","SUCCESS",Map.of("assessmentId",id),"停止接收作答，既有成绩保留");return view(quizzes.saveAndFlush(q));}
 @Transactional public View submit(UUID id,List<Integer> answers){var q=get(id,true);var caller=CallerContext.require();if(caller.role()!=CallerRole.STUDENT || !q.getStudentId().equals(caller.accountId()))throw new AccessDeniedException("仅小测归属学生可作答");if(q.getStatus()!=LearningAssessment.Status.PUBLISHED || attempts.findByAssessmentId(id).isPresent())throw new IllegalStateException("只能对已发布的小测提交一次，归档后不接受作答");var items=bank(q);if(answers==null || answers.size()!=items.size())throw new IllegalArgumentException("请完整作答所有题目");int correct=0;for(int i=0;i<items.size();i++){var a=answers.get(i);if(a==null || a<0 || a>=items.get(i).choices().size())throw new IllegalArgumentException("作答选项无效");if(a.equals(items.get(i).correctIndex()))correct++;}attempts.saveAndFlush(new AssessmentAttempt(id,caller.accountId(),encode(answers),correct,items.size()));messaging.notify(q.getTeacherId(),"ASSESSMENT","学生已提交小测",q.getTitle()+"；请查看真实成绩并补充批注。");audit.record("ASSESSMENT_SUBMIT","SUCCESS",Map.of("assessmentId",id,"correct",correct,"total",items.size()),"服务端客观题评分，不将得分推断为综合掌握度");return view(q);}
 @Transactional public View comment(UUID id,long revision,String note){var q=get(id,true);own(q);var a=attempts.findByAssessmentId(id).orElseThrow(()->new IllegalStateException("学生尚未提交小测"));if(a.getCommentRevision()!=revision)throw new IllegalStateException("批注已更新，请刷新");a.comment(text(note,4000));attempts.flush();audit.record("ASSESSMENT_COMMENT","SUCCESS",Map.of("assessmentId",id,"revision",revision),"教师批注不覆盖客观评分");messaging.notify(q.getStudentId(),"ASSESSMENT","小测有新的教师批注",q.getTitle()+"；请查看阶段报告。");return view(q);}
 @Transactional(readOnly=true) public Report report(UUID student,UUID child,int page,int size){var target=access.target(student,child);access.read(target);var c=CallerContext.require();var teacher=c.role()==CallerRole.TEACHER?c.accountId():null;
  var ts=tasks.findByStudentIdOrderByCreatedAtDesc(target).stream().filter(t->teacher==null || t.getTeacherId().equals(teacher)).toList();
  var rs=(teacher==null?records.findByStudentIdOrderByCreatedAtDesc(target):records.findByTeacherIdAndStudentIdOrderByCreatedAtDesc(teacher,target)).stream().limit(10).map(r->{Map<String,String> m=new LinkedHashMap<>();m.put("subject",Objects.toString(r.getSubject(),""));m.put("knowledgePoints",Objects.toString(r.getKnowledgePoints(),""));m.put("performance",Objects.toString(r.getPerformance(),""));m.put("nextSuggestion",Objects.toString(r.getNextSuggestion(),""));return m;}).toList();
  var scores=attempts.findAll((root,query,cb)->{
   var studentMatch=cb.equal(root.get("studentId"),target);
   if(teacher==null)return studentMatch;
   var sq=query.subquery(UUID.class);var quiz=sq.from(LearningAssessment.class);sq.select(quiz.get("id")).where(cb.equal(quiz.get("teacherId"),teacher));
   return cb.and(studentMatch,root.get("assessmentId").in(sq));
  },LearningParticipantAccess.page(page,size)).map(a->{var q=quizzes.findById(a.getAssessmentId()).orElseThrow();return new Score(q.getId(),q.getSubject(),q.getTitle(),a.getCorrectCount(),a.getTotalCount(),a.getCreatedAt(),a.getTeacherComment());});
  return new Report(ts.size(),ts.stream().filter(t->t.getStatus()==LearningTask.Status.COMPLETED).count(),booking.completedAssessmentLessons(target,teacher),scores,rs,"阶段性学习小结，非全面能力诊断；得分只反映本次题目范围，任务和课程统计基于真实记录。");
 }
}
