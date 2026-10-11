package com.nurture.admin.app;
import com.nurture.admin.domain.*;
import com.nurture.admin.infra.*;
import com.nurture.booking.app.BookingApplicationService;
import com.nurture.parent.app.ParentApplicationService;
import com.nurture.identity.app.IdentityApplicationService;
import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.learning.app.LearningFileValidator;
import com.nurture.shared.audit.AuditService;
import com.nurture.shared.security.*;
import com.nurture.shared.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.*;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.*;
import java.security.MessageDigest;
@Service
public class SupportCaseService {
 public static final long QUOTA=100L*1024*1024,MAX_FILE=10L*1024*1024;
 private final SupportCaseRepository cases;private final SupportReplyRepository replies;private final SupportEvidenceRepository evidence;
 private final SupportStorageQuotaRepository quotas;private final BookingApplicationService booking;private final ParentApplicationService parents;
 private final IdentityApplicationService identity;private final MessagingApplicationService messaging;private final LearningFileValidator validator;
 private final FileStorageService storage;private final AuditService audit;
 public SupportCaseService(SupportCaseRepository c,SupportReplyRepository r,SupportEvidenceRepository e,SupportStorageQuotaRepository q,
     BookingApplicationService b,ParentApplicationService p,IdentityApplicationService i,MessagingApplicationService m,LearningFileValidator v,FileStorageService s,AuditService a){
  cases=c;replies=r;evidence=e;quotas=q;booking=b;parents=p;identity=i;messaging=m;validator=v;storage=s;audit=a;
 }
 public record CourseView(UUID bookingId,String subject,String status,String type,java.time.OffsetDateTime startAt,String studentName,String teacherName){}
 public record CaseView(UUID caseId,String ownerName,String category,String title,String description,UUID bookingId,String status,long revision,Instant createdAt,Instant updatedAt,CourseView course){}
 public record ReplyView(UUID replyId,String authorName,String authorRole,String body,String kind,Instant createdAt){}
 public record EvidenceView(UUID fileId,String filename,long sizeBytes,String sha256,boolean canDelete,Instant createdAt){}
 public record Download(String filename,byte[] content){}
 private boolean admin(){return CallerContext.require().role()==CallerRole.ADMIN;}
 private void member(){var r=CallerContext.require().role();if(r!=CallerRole.ADMIN && r!=CallerRole.STUDENT && r!=CallerRole.TEACHER && r!=CallerRole.PARENT)throw new AccessDeniedException("客服工单仅账户本人及运营可访问");}
 private void customer(){member();if(admin())throw new AccessDeniedException("运营负责处理用户提交的工单");}
 private void read(SupportCase c){member();if(!admin() && !c.getOwnerId().equals(CallerContext.require().accountId()))throw new AccessDeniedException("只能查看本人提交的客服工单");}
 private SupportCase get(UUID id,boolean lock){var c=(lock?cases.locked(id):cases.findById(id)).orElseThrow(()->new IllegalArgumentException("工单不存在"));read(c);return c;}
 private String name(UUID id){return identity.displayNameOf(id).orElse("账户");}
 private CourseView course(BookingApplicationService.SupportCourse b){return new CourseView(b.bookingId(),b.subject(),b.status(),b.type(),b.startAt(),name(b.studentId()),name(b.teacherId()));}
 private CourseView linkedCourse(SupportCase c){
  if(c.getBookingId()==null)return null;var caller=CallerContext.require();var roles=booking.rolesOf(c.getBookingId());
  boolean allowed=admin() || (caller.role()==CallerRole.PARENT?parents.isActiveGuardian(caller.accountId(),roles.studentId()):booking.isParticipant(c.getBookingId(),caller.accountId()));
  return allowed?course(booking.supportCourseFact(c.getBookingId())):null;
 }
 private CaseView view(SupportCase c){return new CaseView(c.getId(),name(c.getOwnerId()),c.getCategory().name(),c.getTitle(),c.getDescription(),c.getBookingId(),c.getStatus().name(),c.getRevision(),c.getCreatedAt(),c.getUpdatedAt(),linkedCourse(c));}
 private static String text(String value,int max){if(value==null || value.isBlank() || value.length()>max)throw new IllegalArgumentException("内容不能为空或超过长度限制");return value.trim();}
 private static Pageable page(int page,int size,boolean history){if(page<0 || page>10000 || size<1 || size>50)throw new IllegalArgumentException("分页参数超出范围");return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,history?"createdAt":"updatedAt","id"));}
 @Transactional(readOnly=true) public Page<CaseView> list(String status,int page,int size){
  member();var state=status==null || status.isBlank()?null:SupportCase.Status.valueOf(status);var owner=admin()?null:CallerContext.require().accountId();
  return cases.findAll((root,query,cb)->{List<jakarta.persistence.criteria.Predicate> p=new ArrayList<>();if(owner!=null)p.add(cb.equal(root.get("ownerId"),owner));if(state!=null)p.add(cb.equal(root.get("status"),state));return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));},page(page,size,false)).map(this::view);
 }
 @Transactional(readOnly=true) public CaseView detail(UUID id){return view(get(id,false));}
 @Transactional(readOnly=true) public Page<CourseView> courseOptions(String status,int page,int size){
  customer();var caller=CallerContext.require();var children=caller.role()==CallerRole.PARENT?parents.activeChildAccountIds(caller.accountId()):List.<UUID>of();
  return booking.supportCourseOptions(children,status,page,size).map(this::course);
 }
 @Transactional public CaseView create(SupportCase.Category category,String title,String description,UUID bookingId){
  customer();if(category==null)throw new IllegalArgumentException("请选择工单类别");var caller=CallerContext.require();
  if(bookingId!=null){var b=booking.rolesOf(bookingId);boolean allowed=caller.role()==CallerRole.PARENT?parents.isActiveGuardian(caller.accountId(),b.studentId()):booking.isParticipant(bookingId,caller.accountId());if(!allowed)throw new AccessDeniedException("关联课程不属于你");}
  var c=cases.saveAndFlush(new SupportCase(caller.accountId(),category,text(title,120),text(description,4000),bookingId));
  audit.record("SUPPORT_CREATE","SUCCESS",Map.of("caseId",c.getId()),"客服工单创建");return view(c);
 }
 @Transactional(readOnly=true) public Page<ReplyView> history(UUID id,int page,int size){get(id,false);return replies.findByCaseId(id,page(page,size,true)).map(r->new ReplyView(r.getId(),name(r.getAuthorId()),r.getAuthorRole(),r.getBody(),r.getKind(),r.getCreatedAt()));}
 @Transactional public CaseView reply(UUID id,String body){
  var c=get(id,true);if(c.closed())throw new IllegalStateException("工单已关闭，请新建工单描述后续问题");
  var caller=CallerContext.require();replies.save(new SupportReply(id,caller.accountId(),caller.role().name(),text(body,4000),"REPLY"));
  if(!admin() && c.getStatus()==SupportCase.Status.WAITING_USER)c.change(SupportCase.Status.IN_PROGRESS);else c.touch();
  audit.record("SUPPORT_REPLY","SUCCESS",Map.of("caseId",id),"工单回复");notifyOwner(c,"你的客服工单有新回复");return view(cases.saveAndFlush(c));
 }
 @Transactional public CaseView change(UUID id,SupportCase.Status next,long revision,String note){
  var c=get(id,true);if(c.getRevision()!=revision)throw new IllegalStateException("工单已更新，请刷新后再处理");if(next==null)throw new IllegalArgumentException("请选择状态");text(note,2000);
  if(c.getStatus()==next)return view(c);
  boolean allowed=admin()?switch(c.getStatus()){
   case OPEN -> next==SupportCase.Status.IN_PROGRESS || next==SupportCase.Status.WAITING_USER || next==SupportCase.Status.RESOLVED || next==SupportCase.Status.CLOSED;
   case IN_PROGRESS,WAITING_USER -> next==SupportCase.Status.IN_PROGRESS || next==SupportCase.Status.WAITING_USER || next==SupportCase.Status.RESOLVED || next==SupportCase.Status.CLOSED;
   case RESOLVED -> next==SupportCase.Status.IN_PROGRESS || next==SupportCase.Status.CLOSED;
   case CLOSED -> false;
  }:!c.closed() && (next==SupportCase.Status.CLOSED || c.getStatus()==SupportCase.Status.RESOLVED && next==SupportCase.Status.OPEN);
  if(!allowed)throw new IllegalStateException("不允许此状态流转");var prior=c.getStatus();c.change(next);var caller=CallerContext.require();
  replies.save(new SupportReply(id,caller.accountId(),caller.role().name(),prior+" → "+next+"："+note.trim(),"STATUS"));
  audit.record("SUPPORT_STATUS","SUCCESS",Map.of("caseId",id,"from",prior,"to",next,"revision",revision),"工单状态流转，不触发退款或订单变更");notifyOwner(c,"你的客服工单处理状态已更新");return view(cases.saveAndFlush(c));
 }
 private void notifyOwner(SupportCase c,String message){if(admin())messaging.notify(c.getOwnerId(),"SUPPORT",message,c.getTitle()+"；请在客服与申诉中查看详情。");}
 private EvidenceView view(SupportEvidence f,SupportCase c){return new EvidenceView(f.getId(),f.getFilename(),f.getSizeBytes(),f.getSha256(),!c.closed() && f.getOwnerId().equals(CallerContext.require().accountId()),f.getCreatedAt());}
 @Transactional(readOnly=true) public List<EvidenceView> files(UUID id){var c=get(id,false);return evidence.findByCaseIdAndDeletedFalseOrderByCreatedAtAscIdAsc(id).stream().map(f->view(f,c)).toList();}
 @Transactional(readOnly=true) public Map<String,Long> quota(){member();return Map.of("storedBytes",quotas.findById(CallerContext.require().accountId()).map(SupportStorageQuota::getStoredBytes).orElse(0L),"limitBytes",QUOTA,"maxFileBytes",MAX_FILE);}
 @Transactional public EvidenceView upload(UUID id,String filename,String type,byte[] bytes){
  var c=get(id,true);if(c.closed())throw new IllegalStateException("工单已关闭，不能补传证据");
  if(bytes.length>MAX_FILE || filename==null || !filename.toLowerCase(Locale.ROOT).matches(".*\\.(pdf|png|jpg|jpeg)$"))throw new IllegalArgumentException("证据仅支持10MB以内的PDF、PNG、JPEG");
  var mime=validator.validate(filename,type,bytes);var caller=CallerContext.require();
  quotas.ensure(caller.accountId());var q=quotas.locked(caller.accountId()).orElseThrow();
  if(q.getStoredBytes()+bytes.length>QUOTA)throw new IllegalStateException("个人客服证据保留容量100MB已满");
  if(evidence.countByCaseId(id)>=10)throw new IllegalStateException("每个工单最多10份保留证据，移除的文件仍计入");
  var stored=storage.store("support",filename,mime,bytes);
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{storage.delete(stored.storageKey());}catch(RuntimeException e){LoggerFactory.getLogger(SupportCaseService.class).error("工单证据回滚清理失败",e);}}});
  String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  var f=evidence.saveAndFlush(new SupportEvidence(id,caller.accountId(),stored.storageKey(),filename,mime,bytes.length,hash));q.add(bytes.length);c.touch();
  audit.record("SUPPORT_EVIDENCE_UPLOAD","SUCCESS",Map.of("caseId",id,"fileId",f.getId(),"sha256",hash),"私有工单证据上传");return view(f,c);
 }
 @Transactional public Download download(UUID fileId){var f=evidence.findById(fileId).orElseThrow(()->new IllegalArgumentException("证据不存在"));get(f.getCaseId(),false);if(f.isDeleted())throw new IllegalStateException("证据已移除");var bytes=storage.load(f.getStorageKey());audit.record("SUPPORT_EVIDENCE_DOWNLOAD","SUCCESS",Map.of("fileId",fileId),"受控工单证据下载");return new Download(f.getFilename(),bytes);}
 @Transactional public void remove(UUID fileId){var caseId=evidence.caseIdOf(fileId).orElseThrow(()->new IllegalArgumentException("证据不存在"));var c=get(caseId,true);var f=evidence.locked(fileId).orElseThrow();if(!f.getOwnerId().equals(CallerContext.require().accountId()))throw new AccessDeniedException("仅上传者可以移除证据");if(f.isDeleted())return;if(c.closed())throw new IllegalStateException("已关闭工单的证据已固定");f.remove();c.touch();audit.record("SUPPORT_EVIDENCE_REMOVE","SUCCESS",Map.of("fileId",fileId),"逻辑移除，文件与保留容量不清理");}
}
