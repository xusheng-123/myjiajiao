package com.nurture.admin.api;
import com.nurture.admin.app.SupportCaseService;
import com.nurture.admin.domain.SupportCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.util.UUID;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
@RestController @RequestMapping("/api/v1/support/cases")
@PreAuthorize("hasAnyRole('STUDENT','TEACHER','PARENT','ADMIN')")
public class SupportCaseController {
 private final SupportCaseService service;public SupportCaseController(SupportCaseService s){service=s;}
 public record Create(@NotNull SupportCase.Category category,@NotBlank @Size(max=120) String title,@NotBlank @Size(max=4000) String description,UUID bookingId){}
 public record Reply(@NotBlank @Size(max=4000) String body){}
 public record Change(@NotNull SupportCase.Status status,@Min(0) long revision,@NotBlank @Size(max=2000) String note){}
 @GetMapping public Object list(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(status,page,size);}
 @GetMapping("/quota") public Object quota(){return service.quota();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Object create(@Valid @RequestBody Create r){return service.create(r.category(),r.title(),r.description(),r.bookingId());}
 @GetMapping("/{id}") public Object detail(@PathVariable UUID id){return service.detail(id);}
 @GetMapping("/{id}/replies") public Object history(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.history(id,page,size);}
 @PostMapping("/{id}/replies") @ResponseStatus(HttpStatus.CREATED) public Object reply(@PathVariable UUID id,@Valid @RequestBody Reply r){return service.reply(id,r.body());}
 @PostMapping("/{id}/status") public Object status(@PathVariable UUID id,@Valid @RequestBody Change r){return service.change(id,r.status(),r.revision(),r.note());}
 @GetMapping("/{id}/evidence") public Object files(@PathVariable UUID id){return service.files(id);}
 @PostMapping(value="/{id}/evidence",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
 public Object upload(@PathVariable UUID id,@RequestPart("file") MultipartFile f)throws IOException{return service.upload(id,f.getOriginalFilename(),f.getContentType(),f.getBytes());}
 @GetMapping("/evidence/{id}/download") public ResponseEntity<byte[]> download(@PathVariable UUID id){var f=service.download(id);return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(f.content().length).header("Content-Disposition",ContentDisposition.attachment().filename(f.filename(),StandardCharsets.UTF_8).build().toString()).header("Cache-Control","no-store, private").header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'").body(f.content());}
 @DeleteMapping("/evidence/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@PathVariable UUID id){service.remove(id);}
}
