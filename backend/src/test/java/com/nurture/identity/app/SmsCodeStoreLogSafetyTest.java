package com.nurture.identity.app;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nurture.identity.infra.SmsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Release P1-3 安全验收：验证码/密码/Token/Secret 严禁出现在任何日志中。
 * 通过 Logback ListAppender 捕获 SmsCodeStore 全部日志事件并断言无验证码明文。
 */
class SmsCodeStoreLogSafetyTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private SmsCodeStore store;
    private final String phone = "13812345678";

    @BeforeEach
    void setUp() {
        SmsProperties props = new SmsProperties(
                new SmsProperties.RateLimit(0, 100, 1000), 5);
        store = new SmsCodeStore(new SmsRateLimiter(props), props, new SmsDelivery() {
            public boolean available() { return true; }
            public void send(String phone, String code) { /* Test supplier accepting delivery. */ }
        });
        logger = (Logger) LoggerFactory.getLogger(SmsCodeStore.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private String allLogText() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("发送验证码：日志不得出现验证码明文，手机号必须脱敏")
    void sendMustNotLogCode() {
        store.send("STUDENT", phone);

        String logs = allLogText();
        assertThat(logs).doesNotContain(phone);          // 手机号脱敏
        assertThat(logs).doesNotContain("验证码：");       // 旧格式（验证码明文）已废除
        // 断言日志中不存在任何 6 位连续数字（验证码形态）
        List<String> sixDigitRuns = java.util.regex.Pattern.compile("\\d{6}")
                .matcher(logs).results().map(m -> m.group()).toList();
        assertThat(sixDigitRuns).as("日志中不得出现 6 位数字（验证码形态）: %s", logs).isEmpty();
    }

    @Test
    @DisplayName("试错作废路径：日志同样不得出现验证码明文")
    void verifyFailurePathMustNotLogCode() {
        // 发送 → 用错误验证码试错 5 次 → 作废（触发 warn 日志）
        store.send("STUDENT", phone);
        for (int i = 0; i < 5; i++) {
            store.verify("STUDENT", phone, "000000");
        }

        String logs = allLogText();
        // 000000 是我们故意提交的错误码；作废日志不得回显正确验证码，也不应回显错误码本身
        assertThat(logs).doesNotContain("验证码：");
        List<String> sixDigitRuns = java.util.regex.Pattern.compile("\\d{6}")
                .matcher(logs).results().map(m -> m.group()).toList();
        assertThat(sixDigitRuns).as("日志中不得出现 6 位数字（验证码形态）: %s", logs).isEmpty();
    }

    @Test
    @DisplayName("手机号脱敏规则：保留前 3 后 4")
    void maskPhoneKeepsPrefixAndSuffix() {
        assertThat(SmsCodeStore.maskPhone("13812345678")).isEqualTo("138****5678");
        assertThat(SmsCodeStore.maskPhone(null)).isEqualTo("***");
        assertThat(SmsCodeStore.maskPhone("123")).isEqualTo("***");
    }
}
