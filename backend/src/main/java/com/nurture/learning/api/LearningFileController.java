package com.nurture.learning.api;
import com.nurture.learning.app.LearningFileService;
import com.nurture.learning.domain.LearningFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/learning/files")
@PreAuthorize("hasAnyRole('STUDENT','TEACHER','PARENT')")
public class LearningFileController {
    private final LearningFileService service;
    public LearningFileController(LearningFileService s){service=s;}
    @GetMapping public Object list(@RequestParam(required=false) UUID studentId,@RequestParam(required=false) UUID childProfileId,
            @RequestParam(required=false) UUID taskId,@RequestParam(required=false) UUID questionId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(studentId,childProfileId,taskId,questionId,page,size);}
    @GetMapping("/quota") public Object quota(){return service.quota();}
    @GetMapping("/context") public Object context(@RequestParam(required=false) UUID taskId,@RequestParam(required=false) UUID questionId){return service.context(taskId,questionId);}
    @GetMapping("/work") public Object work(@RequestParam(required=false) UUID studentId,@RequestParam(required=false) UUID childProfileId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.work(studentId,childProfileId,page,size);}
    @GetMapping("/questions") public Object questions(@RequestParam(required=false) UUID studentId,@RequestParam(required=false) UUID childProfileId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.questions(studentId,childProfileId,page,size);}
    @GetMapping("/teachers") public Object teachers(){return service.teachers();}
    @GetMapping("/eligible-lessons") public Object lessons(@RequestParam UUID studentId){return service.lessons(studentId);}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public Object upload(@RequestParam LearningFile.Category category,@RequestParam UUID contextId,@RequestPart("file") MultipartFile file) throws IOException {
        return service.upload(category,contextId,file.getOriginalFilename(),file.getContentType(),file.getBytes());
    }
    @GetMapping("/{id}/download") public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        var f=service.download(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(f.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.filename(),StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store, private").header("X-Content-Type-Options","nosniff").body(f.content());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@PathVariable UUID id){service.remove(id);}
}
