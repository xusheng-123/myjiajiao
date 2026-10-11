package com.nurture.booking.api;
import com.nurture.booking.app.BookingCalendarService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/booking")
public class BookingCalendarController {
    private final BookingCalendarService service;
    public BookingCalendarController(BookingCalendarService s){service=s;}
    @GetMapping("/calendar.ics") @PreAuthorize("hasAnyRole('STUDENT','TEACHER','PARENT')")
    public ResponseEntity<String> export(@RequestParam(required=false) UUID studentId,@RequestParam(required=false) UUID childProfileId,
            @RequestParam(required=false) OffsetDateTime from,@RequestParam(required=false) OffsetDateTime to) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"nurture-calendar.ics\"").header(HttpHeaders.CACHE_CONTROL,"no-store, private")
            .header("X-Content-Type-Options","nosniff").body(service.export(studentId,childProfileId,from,to));
    }
}
