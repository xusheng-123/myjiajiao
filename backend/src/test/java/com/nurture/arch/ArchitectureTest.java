package com.nurture.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构边界自动化测试 — 机器强制模块化单体的模块间依赖规则。
 * 使用 FreezingArchRule 冻结存量违规（技术债），只对新增违规报警。
 * 规则只约束主代码（排除 *Test）；本模块 app→infra 属正常分层，不违规。
 */
class ArchitectureTest {

    private static final Pattern MODULE_PKG = Pattern.compile("com\\.nurture\\.([a-z]+)(\\..*)?");
    private static final Pattern INFRA_PKG = Pattern.compile("com\\.nurture\\.[a-z]+\\.infra(\\..*)?");

    private static String moduleOf(JavaClass clazz) {
        Matcher m = MODULE_PKG.matcher(clazz.getPackageName());
        return m.matches() ? m.group(1) : null;
    }

    private static boolean isTestClass(JavaClass clazz) {
        return clazz.getSimpleName().endsWith("Test") || clazz.getPackageName().startsWith("com.nurture.arch.");
    }

    private static final DescribedPredicate<JavaClass> 主代码 =
            new DescribedPredicate<>("主代码（排除测试类）") {
                @Override
                public boolean test(JavaClass input) {
                    return !isTestClass(input);
                }
            };

    /** 源类依赖目标为其他业务模块的 infra 层 → 违规（同模块 app→infra 允许）。 */
    private static final ArchCondition<JavaClass> 不依赖其他模块的infra层 =
            new ArchCondition<>("不依赖其他模块的 infra 层") {
                @Override
                public void check(JavaClass source, ConditionEvents events) {
                    for (Dependency dep : source.getDirectDependenciesFromSelf()) {
                        JavaClass target = dep.getTargetClass();
                        if (!INFRA_PKG.matcher(target.getPackageName()).matches()) {
                            continue;
                        }
                        String sourceModule = moduleOf(source);
                        if (sourceModule == null || sourceModule.equals(moduleOf(target))) {
                            continue;
                        }
                        events.add(SimpleConditionEvent.violated(dep, dep.getDescription()));
                    }
                }
            };

    @Test
    void 业务模块不得新增对其他模块infra层的直接依赖() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.nurture");
        ArchRule rule = FreezingArchRule.freeze(
                noClasses()
                        .that(主代码)
                        .and().resideInAPackage("com.nurture..")
                        .and().resideOutsideOfPackage("com.nurture.shared..")
                        .should(不依赖其他模块的infra层));
        rule.check(classes);
    }

    @Test
    void 控制器不得直接依赖任何模块的Repository() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.nurture");
        ArchRule rule = FreezingArchRule.freeze(
                noClasses()
                        .that(主代码)
                        .and().resideInAPackage("com.nurture..api..")
                        .should().dependOnClassesThat()
                        .resideInAPackage("com.nurture.*.infra.."));
        rule.check(classes);
    }
}
