package com.nurture.messaging.app;
import com.nurture.messaging.domain.*;
import com.nurture.messaging.infra.*;
import com.nurture.shared.security.*;
import com.nurture.shared.audit.AuditService;
import com.nurture.shared.storage.FileStorageService;
import com.nurture.learning.app.LearningFileValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.security.MessageDigest;
@Service
public class ConsultationFileService{
 public static final long QUOTA=104857600L,MAX=10485760L;
 private final ConversationRepository conversations;private final ConsultationFileRepository files;private final ConsultationStorageQuotaRepository quotas;
 private final MessagingApplicationService messaging;private final LearningFileValidator validator;private final FileStorageService storage;private final AuditService audit;
 public ConsultationFileService(ConversationRepository c,ConsultationFileRepository f,ConsultationStorageQuotaRepository q,MessagingApplicationService m,LearningFileValidator v,FileStorageService s,AuditService a){conversations=c;files=f;quotas=q;messaging=m;validator=v;storage=s;audit=a;}
 public record View(UUID fileId,String filename,long sizeBytes,String sha256){}public record Download(String filename,byte[] content){}
 private void participant(Conversation c){var p=CallerContext.require();if(p.role()!=CallerRole.STUDENT && p.role()!=CallerRole.TEACHER || !p.accountId().equals(c.getStudentId()) && !p.accountId().equals(c.getTeacherId()))throw new AccessDeniedException("咨询附件仅会话双方可以读取与上传");}
 @Transactional(readOnly=true) public com.nurture.shared.storage.StorageQuotaView quota(){var c=CallerContext.require();if(c.role()!=CallerRole.STUDENT && c.role()!=CallerRole.TEACHER)throw new AccessDeniedException("咨询附件由会话师生上传");return com.nurture.shared.storage.StorageQuotaView.of(quotas.findById(c.accountId()).map(ConsultationStorageQuota::getStoredBytes).orElse(0L),QUOTA,MAX,0);}
 @Transactional public View upload(UUID id,String filename,String type,byte[] bytes){
  var conversation=conversations.locked(id).orElseThrow(()->new IllegalArgumentException("会话不存在"));participant(conversation);if(bytes.length>MAX)throw new IllegalArgumentException("咨询附件不能超过10MB");var mime=validator.validate(filename,type,bytes);var caller=CallerContext.require();
  quotas.ensure(caller.accountId());var q=quotas.locked(caller.accountId()).orElseThrow();if(q.getStoredBytes()+bytes.length>QUOTA)throw new IllegalStateException("咨询附件个人保留容量100MB已满");if(files.countByConversationId(id)>=100)throw new IllegalStateException("每个会话最多100份保留附件");
  var saved=storage.store("consultation",filename,mime,bytes);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{storage.delete(saved.storageKey());}catch(RuntimeException e){LoggerFactory.getLogger(ConsultationFileService.class).error("咨询附件回滚清理失败",e);}}});
  String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  var f=files.saveAndFlush(new ConsultationFile(id,caller.accountId(),saved.storageKey(),filename,mime,bytes.length,hash));q.add(bytes.length);
  var message=messaging.send(id,"附件："+filename);message.attach(f.getId());
  audit.record("CONSULTATION_FILE_UPLOAD","SUCCESS",Map.of("conversationId",id,"fileId",f.getId(),"sha256",hash),"附件与消息同事务发布，保留已发内容");return new View(f.getId(),filename,bytes.length,hash);
 }
 @Transactional public Download download(UUID id){var f=files.findById(id).orElseThrow(()->new IllegalArgumentException("附件不存在"));participant(conversations.findById(f.getConversationId()).orElseThrow());var bytes=storage.load(f.getStorageKey());audit.record("CONSULTATION_FILE_DOWNLOAD","SUCCESS",Map.of("fileId",id),"私有咨询附件受控下载");return new Download(f.getFilename(),bytes);}
}
