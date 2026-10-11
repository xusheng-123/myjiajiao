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

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * 架构边界自动化测试 — 机器强制模块化单体的模块间依赖规则。
 * 全量严格校验，不冻结或豁免存量违规；反例验证规则确实拒绝非法依赖。
 * 生产扫描排除测试目录；本模块 app→infra 属正常分层，不违规。
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
        JavaClasses classes = new ClassFileImporter().withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.nurture");
        ArchRule rule = classes()
                        .that(主代码)
                        .and().resideInAPackage("com.nurture..")
                        .and().resideOutsideOfPackage("com.nurture.shared..")
                        .should(不依赖其他模块的infra层);
        rule.check(classes);
    }

    @Test
    void 控制器不得直接依赖任何模块的Repository() {
        JavaClasses classes = new ClassFileImporter().withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.nurture");
        ArchRule rule = noClasses()
                        .that(主代码)
                        .and().resideInAPackage("com.nurture..api..")
                        .should().dependOnClassesThat()
                        .resideInAPackage("com.nurture.*.infra..");
        rule.check(classes);
    }
    static ArchRule crossModuleRule(){
        return classes().that().resideInAPackage("com.nurture..")
                .and().resideOutsideOfPackage("com.nurture.shared..")
                .should(new ArchCondition<JavaClass>("跨模块只能依赖应用层"){
                    @Override public void check(JavaClass source,ConditionEvents events){
                        for(Dependency dep:source.getDirectDependenciesFromSelf()){
                            var target=dep.getTargetClass();String own=moduleOf(source),other=moduleOf(target);
                            if(other==null || other.equals("shared") || other.equals(own))continue;
                            String appPackage="com.nurture."+other+".app";
                            if(!target.getPackageName().equals(appPackage) && !target.getPackageName().startsWith(appPackage+"."))events.add(SimpleConditionEvent.violated(dep,dep.getDescription()));
                        }
                    }
                });
    }
    @Test void 所有业务跨模块访问只通过应用端口(){
        crossModuleRule().check(new ClassFileImporter().withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.nurture"));
    }
    @Test void 领域层不依赖应用接口或仓储(){
        noClasses().that().resideInAPackage("com.nurture..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("com.nurture..app..","com.nurture..api..","com.nurture..infra..")
                .check(new ClassFileImporter().withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.nurture"));
    }
    @Test void 跨模块反例确实被拒绝而应用端口被允许(){
        var bad=new ClassFileImporter().importClasses(com.nurture.fixturea.app.BadDependency.class,com.nurture.fixtureb.infra.ForeignRepository.class);
        org.assertj.core.api.Assertions.assertThat(crossModuleRule().evaluate(bad).hasViolation()).isTrue();
        var good=new ClassFileImporter().importClasses(com.nurture.fixturea.app.GoodDependency.class,com.nurture.fixtureb.app.ForeignPort.class);
        org.assertj.core.api.Assertions.assertThat(crossModuleRule().evaluate(good).hasViolation()).isFalse();
    }
}
