package com.leap.tradeapi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The layering rules from SEC4-301, enforced as a build-failing test.
 *
 * <p>The Sprint 5 build refused Spring, servlet and MyBatis types because none
 * were on that classpath. Here they resolve, so the same constraint is now a
 * rule about what may appear inside a package, and this test is what holds it.
 */
@AnalyzeClasses(
        packages = { "com.leap.tradeapi", "org.leap" },
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringRulesTest {

    @ArchTest
    static final ArchRule controllers_touch_no_persistence =
            noClasses().that().resideInAPackage("..tradeapi.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.leap.tradeapi.mapper..",
                            "java.sql..",
                            "javax.sql..",
                            "org.apache.ibatis..",
                            "org.mybatis..",
                            "org.springframework.jdbc..")
                    .because("controllers hold no SQL and import nothing from the mapper package");

    @ArchTest
    static final ArchRule controllers_open_no_transaction =
            noClasses().that().resideInAPackage("..tradeapi.controller..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.transaction.annotation.Transactional")
                    .because("the transaction boundary belongs to the service");

    @ArchTest
    static final ArchRule domain_carries_no_http_or_framework_type =
            noClasses().that().resideInAPackage("org.leap..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.servlet..",
                            "org.springframework..",
                            "org.apache.ibatis..",
                            "org.mybatis..")
                    .because("the Sprint 7 Trade Executor reuses these rules with no HTTP request");

    @ArchTest
    static final ArchRule mappers_reach_back_into_no_web_or_service_type =
            noClasses().that().resideInAPackage("..tradeapi.mapper..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.leap.tradeapi.controller..",
                            "com.leap.tradeapi.service..",
                            "jakarta.servlet..")
                    .because("a mapper decides nothing and never reaches back into HTTP");
}
