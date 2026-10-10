package com.nurture.teacher.infra;

import com.nurture.teacher.domain.SubjectOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SubjectOfferingRepository extends JpaRepository<SubjectOffering, UUID> {

    List<SubjectOffering> findByTeacherIdAndActiveTrue(UUID teacherId);

    /** 含已下架条目（供 order 侧区分「从未定价」与「明确存在但已停止销售」）。 */
    List<SubjectOffering> findByTeacherIdAndSubject(UUID teacherId, String subject);

    /** D9：同导师+科目+学段的在售条目唯一（服务层幂等 + 部分唯一索引双重保障）。 */
    java.util.Optional<SubjectOffering> findByTeacherIdAndSubjectAndGradeBandAndActiveTrue(
            UUID teacherId, String subject, String gradeBand);

    List<SubjectOffering> findBySubjectAndGradeBandAndActiveTrue(String subject, String gradeBand);

    /** pg_trgm 模糊匹配：科目/教学风格可被近似检索（规范禁止外部搜索引擎）。
     * 注：pg_trgm 对 1~2 字中文无 trigram，故叠加 ILIKE 精确包含匹配兜底。 */
    @Query(value = """
            SELECT o.* FROM subject_offerings o
            WHERE o.active = true
              AND (:subject IS NULL
                   OR o.subject ILIKE '%' || :subject || '%'
                   OR similarity(o.subject, :subject) > 0.2)
            ORDER BY similarity(o.subject, :subject) DESC NULLS LAST, o.subject
            LIMIT 200
            """, nativeQuery = true)
    List<SubjectOffering> searchBySimilarSubject(@Param("subject") String subject);
}
