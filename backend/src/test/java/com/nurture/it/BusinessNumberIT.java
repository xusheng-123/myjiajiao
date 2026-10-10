package com.nurture.it;

import com.nurture.shared.numbering.BusinessNumberGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("it") @Tag("integration")
class BusinessNumberIT {
    @Autowired BusinessNumberGenerator numbers;
    @Test void 并发生成编号不重复且学生序列独立() throws Exception {
        var pool=Executors.newFixedThreadPool(8);
        try {
            var futures=new java.util.ArrayList<Future<String>>();
            for (int i=0;i<1000;i++) futures.add(pool.submit(() -> numbers.account("NUR-STU-")));
            var generated=new java.util.HashSet<String>();
            for (var f:futures) generated.add(f.get(20,TimeUnit.SECONDS));
            assertThat(generated).hasSize(1000).allMatch(s->s.matches("NUR-STU-[0-9]{6,}"));
            assertThat(numbers.student()).matches("NUR-STU-[0-9]{6,}");
        } finally { pool.shutdownNow(); }
    }
}
