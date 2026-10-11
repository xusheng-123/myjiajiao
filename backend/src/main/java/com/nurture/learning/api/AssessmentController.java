package com.nurture.learning.api;
import com.nurture.learning.app.AssessmentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/learning/assessments") @PreAuthorize("hasAnyRole('STUDENT','TEACHER','PARENT')")
public class AssessmentController{
 private final AssessmentService service;public AssessmentController(AssessmentService s){service=s;}
 public record Create(@NotNull UUID bookingId,@NotBlank @Size(max=60) String subject,@NotBlank @Size(max=120) String title,@NotBlank @Size(max=1000) String scope,@NotEmpty @Size(max=20) List<AssessmentService.Question> questions){}
 public record Edit(@Min(0) long revision,@NotBlank @Size(max=120) String title,@NotBlank @Size(max=1000) String scope,@NotEmpty @Size(max=20) List<AssessmentService.Question> questions){}
 public record Revision(@Min(0) long revision){}public record Submit(@NotEmpty @Size(max=20) List<Integer> answers){}
 public record Comment(@Min(0) long revision,@NotBlank @Size(max=4000) String comment){}
 @GetMapping public Object list(@RequestParam(required=false) UUID studentId,@RequestParam(required=false) UUID childProfileId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(studentId,childProfileId,page,size);}
 @GetMapping("/report") public Object report(@RequestParam(required=false) UUID studentId,@RequestParam(required=false) UUID childProfileId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.report(studentId,childProfileId,page,size);}
 @GetMapping("/{id}") public Object detail(@PathVariable UUID id){return service.detail(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Object create(@Valid @RequestBody Create r){return service.create(r.bookingId(),r.subject(),r.title(),r.scope(),r.questions());}
 @PutMapping("/{id}") public Object edit(@PathVariable UUID id,@Valid @RequestBody Edit r){return service.edit(id,r.revision(),r.title(),r.scope(),r.questions());}
 @PostMapping("/{id}/publish") public Object publish(@PathVariable UUID id,@Valid @RequestBody Revision r){return service.publish(id,r.revision());}
 @PostMapping("/{id}/archive") public Object archive(@PathVariable UUID id,@Valid @RequestBody Revision r){return service.archive(id,r.revision());}
 @PostMapping("/{id}/submit") public Object submit(@PathVariable UUID id,@Valid @RequestBody Submit r){return service.submit(id,r.answers());}
 @PostMapping("/{id}/comment") public Object comment(@PathVariable UUID id,@Valid @RequestBody Comment r){return service.comment(id,r.revision(),r.comment());}
}
