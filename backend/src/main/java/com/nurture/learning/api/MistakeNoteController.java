package com.nurture.learning.api;
import com.nurture.learning.app.MistakeNoteService;
import com.nurture.learning.domain.MistakeNote;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/learning/mistakes")
@PreAuthorize("hasAnyRole('STUDENT','TEACHER','PARENT')")
public class MistakeNoteController {
    private final MistakeNoteService service;
    public MistakeNoteController(MistakeNoteService s){service=s;}
    public record Create(UUID studentId,@NotBlank @Size(max=32) String subject,@NotBlank @Size(max=100) String title,
            @NotBlank @Size(max=4000) String questionText,@NotBlank @Size(max=2000) String errorReason,
            @NotNull @Size(max=300) String knowledgeTags,LocalDate reviewDate) {}
    public record Update(@NotNull @Min(0) Long revision,@NotBlank @Size(max=32) String subject,@NotBlank @Size(max=100) String title,
            @NotBlank @Size(max=4000) String questionText,@NotBlank @Size(max=2000) String errorReason,
            @NotNull @Size(max=300) String knowledgeTags,LocalDate reviewDate,@NotNull MistakeNote.Status status) {}
    public record Convert(@NotNull @Min(0) Long revision,@NotNull UUID bookingId) {}
    @GetMapping public Object list(@RequestParam(required=false) UUID studentId,@RequestParam(required=false) UUID childProfileId,
            @RequestParam(required=false) String subject,@RequestParam(required=false) MistakeNote.Status status,@RequestParam(required=false) String tag,
            @RequestParam(required=false) LocalDate dueBefore,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(studentId,childProfileId,subject,status,tag,dueBefore,page,size);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Object create(@Valid @RequestBody Create b){return service.create(b.studentId(),b.subject(),b.title(),b.questionText(),b.errorReason(),b.knowledgeTags(),b.reviewDate());}
    @PutMapping("/{id}") public Object update(@PathVariable UUID id,@Valid @RequestBody Update b){return service.update(id,b.revision(),b.subject(),b.title(),b.questionText(),b.errorReason(),b.knowledgeTags(),b.reviewDate(),b.status());}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@PathVariable UUID id,@RequestParam long revision){service.remove(id,revision);}
    @GetMapping("/{id}/eligible-lessons") public Object lessons(@PathVariable UUID id){return service.lessons(id);}
    @PostMapping("/{id}/task") @ResponseStatus(HttpStatus.CREATED) public Object convert(@PathVariable UUID id,@Valid @RequestBody Convert b){return service.convert(id,b.revision(),b.bookingId());}
}
