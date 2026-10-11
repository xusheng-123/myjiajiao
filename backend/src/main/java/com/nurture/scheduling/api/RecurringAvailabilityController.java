package com.nurture.scheduling.api;
import com.nurture.scheduling.app.RecurringAvailabilityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/scheduling/series") @PreAuthorize("hasRole('TEACHER')")
public class RecurringAvailabilityController {
    private final RecurringAvailabilityService service;
    public RecurringAvailabilityController(RecurringAvailabilityService s){service=s;}
    @PostMapping("/preview") public Object preview(@Valid @RequestBody RecurringAvailabilityService.Rule r){return service.preview(r);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Object publish(@Valid @RequestBody RecurringAvailabilityService.Rule r){return service.publish(r);}
    @GetMapping("/{id}") public Object detail(@PathVariable UUID id){return service.detail(id);}
    @GetMapping public Object list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(page,size);}
}
