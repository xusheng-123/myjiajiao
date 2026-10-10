package com.nurture.shared.numbering;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Set;

/** Numbers identify records, never authorize access. Sequence gaps on rollback are intentional. */
@Component
public class BusinessNumberGenerator {
    private final JdbcTemplate db;
    public BusinessNumberGenerator(JdbcTemplate db) { this.db=db; }
    public String account(String prefix) {
        if (!Set.of("NUR-STU-","NUR-PAR-","NUR-TEA-","NUR-ADM-","NUR-AGT-").contains(prefix))
            throw new IllegalArgumentException("Unknown account prefix");
        return prefix+String.format("%06d",db.queryForObject("select nextval('account_business_number_seq')",Long.class));
    }
    public String student() {
        return "NUR-STU-"+String.format("%06d",db.queryForObject("select nextval('student_business_number_seq')",Long.class));
    }
}
