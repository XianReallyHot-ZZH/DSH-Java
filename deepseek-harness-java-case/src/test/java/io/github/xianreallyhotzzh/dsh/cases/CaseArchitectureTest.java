package io.github.xianreallyhotzzh.dsh.cases;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * L03 起步规则：case 层只依赖 domain 与 api（用例编排不触达触发层与基础设施）。
 *
 * <p>与 vendor 的 CaseArchitectureTest 对应；策略树（orchestration）与 agent
 * 用例自 L03 起填充，规则随课程自然生效。</p>
 */
class CaseArchitectureTest {

    @Test
    void casesMustNotDependOnTriggerInfrastructureOrApp() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("io.github.xianreallyhotzzh.dsh");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("io.github.xianreallyhotzzh.dsh.cases..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.xianreallyhotzzh.dsh.infrastructure..",
                        "io.github.xianreallyhotzzh.dsh.trigger..",
                        "io.github.xianreallyhotzzh.dsh.app.."
                )
                .allowEmptyShould(true)
                .check(classes);
    }
}
