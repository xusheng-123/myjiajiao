package com.nurture.messaging.api;
import com.nurture.messaging.app.ConsultationFileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/messaging") @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
public class ConsultationFileController{
 private final ConsultationFileService service;public ConsultationFileController(ConsultationFileService s){service=s;}
 @GetMapping("/files/quota") public Object quota(){return service.quota();}
 @PostMapping(value="/conversations/{id}/files",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
 public Object upload(@PathVariable UUID id,@RequestPart("file") MultipartFile f)throws IOException{return service.upload(id,f.getOriginalFilename(),f.getContentType(),f.getBytes());}
 @GetMapping("/files/{id}/download") public ResponseEntity<byte[]> download(@PathVariable UUID id){var f=service.download(id);return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(f.content().length).header("Content-Disposition",ContentDisposition.attachment().filename(f.filename(),StandardCharsets.UTF_8).build().toString()).header("Cache-Control","no-store, private").header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'").body(f.content());}
}
