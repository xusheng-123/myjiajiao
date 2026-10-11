package com.nurture.matching.infra;

import com.nurture.matching.app.PublicTeacherCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public-only read projection across the monolith's schema; never mutates foreign module tables. */
@Repository
public class JdbcPublicTeacherCatalog implements PublicTeacherCatalog {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcPublicTeacherCatalog(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String ELIGIBLE = """
        FROM subject_offerings o
        JOIN teacher_profiles p ON p.id=o.teacher_id
        JOIN accounts a ON a.id=p.account_id AND a.role='TEACHER' AND a.status='ACTIVE'
        JOIN LATERAL (SELECT v.status FROM verification_cases v WHERE v.teacher_id=a.id
                      ORDER BY v.submitted_at DESC,v.id DESC LIMIT 1) verification ON verification.status='APPROVED'
        """;
    private static final String EVIDENCE = """
        LEFT JOIN LATERAL (SELECT count(*) AS review_count,avg(r.rating) AS average_rating
                           FROM reviews r WHERE r.reviewee_id=a.id) stats ON true
        LEFT JOIN LATERAL (SELECT s.start_at AS next_slot FROM availability_slots s
                           WHERE s.teacher_id=a.id AND s.status='OPEN' AND s.start_at>:now
                           ORDER BY s.start_at LIMIT 1) slot ON true
        """;
    private static final Map<String,String> SORTS = Map.of(
        "price-asc", "o.price_per_hour_cents ASC",
        "price-desc", "o.price_per_hour_cents DESC",
        "rating-desc", "coalesce(round(stats.average_rating,1),0) DESC",
        "reviews-desc", "stats.review_count DESC",
        "recommended", "(2+CASE WHEN stats.review_count>0 THEN 1 ELSE 0 END+CASE WHEN slot.next_slot IS NOT NULL THEN 1 ELSE 0 END) DESC,"+
            "(1+CASE WHEN stats.review_count>0 THEN 1 ELSE 0 END+CASE WHEN o.price_per_hour_cents<=20000 THEN 1 ELSE 0 END+CASE WHEN o.grade_band IS NOT NULL THEN 1 ELSE 0 END) DESC"
    );

    @Override
    public Page<Row> page(Filter filter, int page, int size, OffsetDateTime now) {
        String order = SORTS.get(filter.sort());
        if (order == null) throw new IllegalArgumentException("排序方式无效");
        var parameters = new MapSqlParameterSource().addValue("now",now).addValue("limit",size).addValue("offset",(long)page*size);
        var where = new StringBuilder(" WHERE o.active=true");
        if (present(filter.subject())) {
            where.append(" AND (o.subject ILIKE '%' || :subject || '%' OR similarity(o.subject,:subject)>0.2)");
            parameters.addValue("subject",filter.subject());
        }
        if (present(filter.gradeBand())) { where.append(" AND o.grade_band=:grade"); parameters.addValue("grade",filter.gradeBand()); }
        if (filter.maxPrice()!=null) { where.append(" AND o.price_per_hour_cents<=:price"); parameters.addValue("price",filter.maxPrice()); }
        if (present(filter.styleTag())) {
            where.append(" AND EXISTS(SELECT 1 FROM teacher_style_tags tag WHERE tag.teacher_id=p.id AND tag.style_tag=:style)");
            parameters.addValue("style",filter.styleTag());
        }
        if (present(filter.keyword())) {
            where.append(" AND strpos(concat(o.subject,' ',o.grade_band,' ',coalesce(p.school,'null'),' ',a.display_name),:keyword)>0");
            parameters.addValue("keyword",filter.keyword().trim());
        }
        long total = jdbc.queryForObject("SELECT count(*) "+ELIGIBLE+where, parameters, Long.class);
        String select = """
            SELECT a.id AS teacher_id,o.subject,o.grade_band,o.price_per_hour_cents,
                   coalesce(stats.average_rating,0) AS average_rating,stats.review_count,slot.next_slot,
                   ARRAY(SELECT tag.style_tag FROM teacher_style_tags tag WHERE tag.teacher_id=p.id ORDER BY tag.style_tag COLLATE "C") AS style_tags,
                   a.display_name,p.school,p.highest_degree,p.years_of_teaching
            """;
        List<Row> rows = jdbc.query(select+ELIGIBLE+EVIDENCE+where+" ORDER BY "+order+
                ",a.id ASC,o.subject COLLATE \"C\" ASC,o.grade_band COLLATE \"C\" ASC LIMIT :limit OFFSET :offset", parameters,
            (rs,index) -> new Row(rs.getObject("teacher_id",UUID.class),rs.getString("subject"),rs.getString("grade_band"),
                rs.getLong("price_per_hour_cents"),rs.getDouble("average_rating"),rs.getLong("review_count"),
                rs.getObject("next_slot",OffsetDateTime.class),Arrays.asList((String[])rs.getArray("style_tags").getArray()),
                rs.getString("display_name"),rs.getString("school"),rs.getString("highest_degree"),rs.getInt("years_of_teaching")));
        return new PageImpl<>(rows,PageRequest.of(page,size),total);
    }
    private static boolean present(String value) { return value!=null && !value.isBlank(); }
}
