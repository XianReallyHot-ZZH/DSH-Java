package io.github.xianreallyhotzzh.dsh.api;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * L01 起步规则：api 层零内部依赖。
 *
 * <p>api 是对外契约模块，不依赖任何内部模块（types 的类实际住在 domain 包段下，
 * 见 lesson 文档「split package」教学点，故禁 domain.. 即同时覆盖 types）。</p>
 */
class ApiArchitectureTest {

    @Test
    void apiMustNotDependOnAnyInternalModule() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("io.github.xianreallyhotzzh.dsh");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("io.github.xianreallyhotzzh.dsh.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.xianreallyhotzzh.dsh.domain..",
                        "io.github.xianreallyhotzzh.dsh.cases..",
                        "io.github.xianreallyhotzzh.dsh.infrastructure..",
                        "io.github.xianreallyhotzzh.dsh.trigger..",
                        "io.github.xianreallyhotzzh.dsh.app.."
                )
                .check(classes);
    }
}
