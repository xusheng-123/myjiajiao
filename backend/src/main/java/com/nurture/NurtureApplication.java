package com.nurture;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 润物细无声 · Nurture — 线上学术精修与课后答疑平台。
 *
 * <p>模块化单体（Modular Monolith）：顶层按业务特性（Package-by-Feature）划分为 16 个内部业务模块，
 * 模块间只允许经由对方 api 应用服务或领域事件协作，严禁跨模块直取仓储。
 * 首版严禁拆分物理分布式微服务。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableScheduling
public class NurtureApplication {

    public static void main(String[] args) {
        SpringApplication.run(NurtureApplication.class, args);
    }
}
