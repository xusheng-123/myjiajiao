package com.nurture.student.api;
import com.nurture.parent.app.ParentApplicationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/v1/student/me/legacy-wallet") @PreAuthorize("hasRole('STUDENT')")
public class LegacyStudentWalletController{
 private final ParentApplicationService parents;public LegacyStudentWalletController(ParentApplicationService p){parents=p;}
 @GetMapping public Object wallet(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return parents.legacyStudentWallet(page,size);}
}
