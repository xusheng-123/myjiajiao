package com.nurture.scheduling.infra;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class SchedulingPublicationLock {
    private final JdbcTemplate jdbc;
    public SchedulingPublicationLock(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void lock(UUID teacher){jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},"scheduling:"+teacher);}
}
