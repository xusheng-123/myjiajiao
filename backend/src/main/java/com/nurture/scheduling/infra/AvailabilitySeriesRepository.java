package com.nurture.scheduling.infra;
import com.nurture.scheduling.domain.AvailabilitySeries;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface AvailabilitySeriesRepository extends JpaRepository<AvailabilitySeries,UUID> {
    Page<AvailabilitySeries> findByTeacherIdOrderByCreatedAtDesc(UUID teacher,Pageable page);
}
