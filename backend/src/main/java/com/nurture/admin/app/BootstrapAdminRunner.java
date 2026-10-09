package com.nurture.admin.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 初始运营管理员引导 — 首次启动时按环境变量创建（NURTURE_ADMIN_PHONE / NURTURE_ADMIN_PASSWORD）。
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final AdminApplicationService adminService;
    private final PasswordEncoder passwordEncoder;

    @Value("${NURTURE_ADMIN_PHONE:13800000000}")
    private String adminPhone;

    @Value("${NURTURE_ADMIN_PASSWORD:}")
    private String adminPassword;

    public BootstrapAdminRunner(AdminApplicationService adminService, PasswordEncoder passwordEncoder) {
        this.adminService = adminService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("未设置 NURTURE_ADMIN_PASSWORD，跳过初始运营管理员创建");
            return;
        }
        adminService.bootstrapAdmin(adminPhone, "运营管理员", adminPassword, passwordEncoder);
        log.info("初始运营管理员就绪（手机号 {}），请使用其登录运营治理控制台", adminPhone);
    }
}
