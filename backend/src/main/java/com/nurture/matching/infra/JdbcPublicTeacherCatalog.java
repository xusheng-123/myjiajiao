package com.nurture.matching.infra;

import com.nurture.matching.app.PublicTeacherCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public-only read projection across the monolith's schema; never mutates foreign module tables.
 *
 * Two-phase page query: the eligible offering set is materialized once, ordering and LIMIT/OFFSET
 * run on that set, and per-teacher evidence (review stats, next open slot) is aggregated once per
 * matched teacher instead of once per candidate row. Keyword filtering keeps the exact
 * strpos(concat(...)) semantics and adds a redundant indexable ILIKE prefilter (strict superset,
 * re-checked by strpos) so selective searches can drive from the trigram indexes. Public fields,
 * ordering rules, tie-breaks and totals are identical to the previous implementation.
 */
@Repository
public class JdbcPublicTeacherCatalog implements PublicTeacherCatalog {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcPublicTeacherCatalog(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String ELIGIBLE = """
        FROM subject_offerings o
        JOIN teacher_profiles p ON p.id=o.teacher_id
        JOIN accounts a ON a.id=p.account_id AND a.role='TEACHER' AND a.status='ACTIVE'
        JOIN (SELECT DISTINCT ON (v.teacher_id) v.teacher_id,v.status FROM verification_cases v
              ORDER BY v.teacher_id,v.submitted_at DESC,v.id DESC) verification
          ON verification.teacher_id=a.id AND verification.status='APPROVED'
        """;
    // Slim projection: display fields are re-fetched for the final page rows only.
    private static final String MATCHED = """
        SELECT a.id AS account_id,p.id AS profile_id,o.subject,o.grade_band,o.price_per_hour_cents
        """ + ELIGIBLE;
    private static final Map<String,String> SORTS = Map.of(
        "price-asc", "price_per_hour_cents ASC",
        "price-desc", "price_per_hour_cents DESC",
        "rating-desc", "coalesce(round(average_rating,1),0) DESC",
        "reviews-desc", "review_count DESC",
        "recommended", "(2+CASE WHEN review_count>0 THEN 1 ELSE 0 END+CASE WHEN next_slot IS NOT NULL THEN 1 ELSE 0 END) DESC,"+
            "(1+CASE WHEN review_count>0 THEN 1 ELSE 0 END+CASE WHEN price_per_hour_cents<=20000 THEN 1 ELSE 0 END+CASE WHEN grade_band IS NOT NULL THEN 1 ELSE 0 END) DESC"
    );
    private static final String TIE_BREAK = "account_id ASC,subject COLLATE \"C\" ASC,grade_band COLLATE \"C\" ASC";
    private static final String TIE_OUT = "paged.account_id ASC,paged.subject COLLATE \"C\" ASC,paged.grade_band COLLATE \"C\" ASC";
    private static final String STYLE_TAGS =
        "ARRAY(SELECT tag.style_tag FROM teacher_style_tags tag WHERE tag.teacher_id=paged.profile_id ORDER BY tag.style_tag COLLATE \"C\") AS style_tags";

    private static boolean needsStats(String sort) { return !sort.equals("price-asc") && !sort.equals("price-desc"); }

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
            String trimmed = filter.keyword().trim();
            String escaped = trimmed.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
            // Redundant indexable superset of strpos(concat(...)); the strpos predicate below stays
            // authoritative. A space-free keyword can never match across the concat separators, so
            // the four ILIKE branches (each trigram-indexed) are an exact superset; a keyword with
            // spaces may span columns and keeps the strpos catch-all inside the OR (seq-scan path).
            if (trimmed.indexOf(' ') >= 0) {
                where.append(" AND (p.school ILIKE :kwLike OR (p.school IS NULL AND :kwNull) OR o.subject ILIKE :kwLike")
                     .append(" OR a.display_name ILIKE :kwLike OR o.grade_band ILIKE :kwLike")
                     .append(" OR strpos(concat(o.subject,' ',o.grade_band,' ',coalesce(p.school,'null'),' ',a.display_name),:keyword)>0)")
                     .append(" AND strpos(concat(o.subject,' ',o.grade_band,' ',coalesce(p.school,'null'),' ',a.display_name),:keyword)>0");
                parameters.addValue("kwNull", trimmed.equals("null"));
            } else {
                where.append(" AND (p.school ILIKE :kwLike OR (p.school IS NULL AND :kwNull) OR o.subject ILIKE :kwLike")
                     .append(" OR a.display_name ILIKE :kwLike OR o.grade_band ILIKE :kwLike)")
                     .append(" AND strpos(concat(o.subject,' ',o.grade_band,' ',coalesce(p.school,'null'),' ',a.display_name),:keyword)>0");
                parameters.addValue("kwNull", trimmed.equals("null"));
            }
            parameters.addValue("kwLike","%"+escaped+"%");
            parameters.addValue("keyword",trimmed);
        }
        // Read-path concurrency guard: parallel gather thrashes under mixed OLTP load (measured),
        // and the bounded top-N sort needs no parallel workers. Scoped to this transaction only.
        jdbc.getJdbcOperations().execute("SET LOCAL max_parallel_workers_per_gather = 0");
        long total = jdbc.queryForObject("SELECT count(*) "+ELIGIBLE+where, parameters, Long.class);

        RowMapper<Row> mapper = (rs,index) -> rowOf(rs);
        List<Row> rows;
        if (needsStats(filter.sort())) {
            // Evidence aggregated once per teacher over the whole table (small: reviews/slots are
            // index-covered); the page itself is a single top-N scan over the matched set.
            String sql = """
                WITH stats AS (
                  SELECT r.reviewee_id AS account_id,count(*) AS review_count,avg(r.rating) AS average_rating
                  FROM reviews r GROUP BY r.reviewee_id),
                nextslots AS (
                  SELECT s.teacher_id AS account_id,min(s.start_at) AS next_slot
                  FROM availability_slots s WHERE s.status='OPEN' AND s.start_at>:now GROUP BY s.teacher_id),
                paged AS (
                  SELECT m.account_id,m.profile_id,m.subject,m.grade_band,m.price_per_hour_cents,
                         coalesce(st.average_rating,0) AS average_rating,coalesce(st.review_count,0) AS review_count,ns.next_slot
                  FROM (%MATCHED%%WHERE%) m
                  LEFT JOIN stats st ON st.account_id=m.account_id
                  LEFT JOIN nextslots ns ON ns.account_id=m.account_id
                  ORDER BY %ORDER%,%TIE%
                  LIMIT :limit OFFSET :offset)
                SELECT paged.account_id AS teacher_id,paged.subject,paged.grade_band,paged.price_per_hour_cents,
                       paged.average_rating,paged.review_count,paged.next_slot,
                       %STYLE_TAGS%,a.display_name,p.school,p.highest_degree,p.years_of_teaching
                FROM paged
                JOIN accounts a ON a.id=paged.account_id
                JOIN teacher_profiles p ON p.id=paged.profile_id
                ORDER BY %ORDER%,%TIE_OUT%
                """.replace("%TIE%",TIE_BREAK).replace("%TIE_OUT%",TIE_OUT).replace("%STYLE_TAGS%",STYLE_TAGS);
            rows = jdbc.query(replace(sql, where, order), parameters, mapper);
        } else {
            String sql = """
                WITH paged AS (
                  SELECT m.* FROM (%MATCHED%%WHERE%) m
                  ORDER BY %ORDER%,%TIE%
                  LIMIT :limit OFFSET :offset)
                SELECT paged.account_id AS teacher_id,paged.subject,paged.grade_band,paged.price_per_hour_cents,
                       coalesce(stats.average_rating,0) AS average_rating,stats.review_count,slot.next_slot,
                       %STYLE_TAGS%,a.display_name,p.school,p.highest_degree,p.years_of_teaching
                FROM paged
                JOIN accounts a ON a.id=paged.account_id
                JOIN teacher_profiles p ON p.id=paged.profile_id
                LEFT JOIN LATERAL (SELECT count(*) AS review_count,avg(r.rating) AS average_rating
                                   FROM reviews r WHERE r.reviewee_id=paged.account_id) stats ON true
                LEFT JOIN LATERAL (SELECT s.start_at AS next_slot FROM availability_slots s
                                   WHERE s.teacher_id=paged.account_id AND s.status='OPEN' AND s.start_at>:now
                                   ORDER BY s.start_at LIMIT 1) slot ON true
                ORDER BY %ORDER%,%TIE_OUT%
                """.replace("%TIE%",TIE_BREAK).replace("%TIE_OUT%",TIE_OUT).replace("%STYLE_TAGS%",STYLE_TAGS);
            rows = jdbc.query(replace(sql, where, order), parameters, mapper);
        }
        return new PageImpl<>(rows,PageRequest.of(page,size),total);
    }

    private static String replace(String sql, StringBuilder where, String order) {
        return sql.replace("%MATCHED%",MATCHED).replace("%WHERE%",where.toString()).replace("%ORDER%",order);
    }

    private static Row rowOf(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("teacher_id",UUID.class),rs.getString("subject"),rs.getString("grade_band"),
            rs.getLong("price_per_hour_cents"),rs.getDouble("average_rating"),rs.getLong("review_count"),
            rs.getObject("next_slot",OffsetDateTime.class),Arrays.asList((String[])rs.getArray("style_tags").getArray()),
            rs.getString("display_name"),rs.getString("school"),rs.getString("highest_degree"),rs.getInt("years_of_teaching"));
    }
    private static boolean present(String value) { return value!=null && !value.isBlank(); }
}
