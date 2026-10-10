package com.nurture.identity.app;

import com.nurture.shared.security.CallerRole;
import com.nurture.teacher.app.TeacherApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persist account and the advertised onboarding fields in one transaction. */
@Service
public class TeacherRegistrationService {
    private final IdentityApplicationService identity;
    private final TeacherApplicationService teacher;
    public TeacherRegistrationService(IdentityApplicationService identity, TeacherApplicationService teacher) {
        this.identity=identity; this.teacher=teacher;
    }
    @Transactional
    public IdentityApplicationService.Session register(String displayName, String phone, String password,
            String school, String degree, int years, String clientIp) {
        var account=identity.register(CallerRole.TEACHER,displayName,phone,password,clientIp);
        teacher.updateProfile(account.getId(),school,degree,years,null,null);
        return identity.issueSession(account);
    }
}
