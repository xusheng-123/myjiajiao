package com.nurture.matching.app;

import org.springframework.data.domain.Page;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Read-only public catalog projection. No entities, contacts, file keys or write operations. */
public interface PublicTeacherCatalog {
    record Filter(String subject, String gradeBand, Long maxPrice, String styleTag, String keyword, String sort) {}
    record Row(UUID teacherId, String subject, String gradeBand, long pricePerHourCents,
               double averageRating, long reviewCount, OffsetDateTime nextOpenSlotAt, List<String> styleTags,
               String displayName, String school, String highestDegree, int yearsOfTeaching) {}
    Page<Row> page(Filter filter, int page, int size, OffsetDateTime now);
}
