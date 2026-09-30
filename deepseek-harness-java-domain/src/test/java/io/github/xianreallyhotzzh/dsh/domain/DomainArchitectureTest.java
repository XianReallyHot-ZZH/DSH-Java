package io.github.xianreallyhotzzh.dsh.domain;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * L01 起步规则：domain 只依赖 types。
 *
 * <p>types 与 domain 共用 ..domain.. 包段（split package），包规则无需也无法区分二者；
 * 只需禁止 domain 依赖其余全部内部模块。L01 骨架空模块，允许空选择集，
 * 随课程填充自然生效。</p>
 */
class DomainArchitectureTest {

    @Test
    void domainMustOnlyDependOnTypes() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("io.github.xianreallyhotzzh.dsh");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("io.github.xianreallyhotzzh.dsh.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.xianreallyhotzzh.dsh.api..",
                        "io.github.xianreallyhotzzh.dsh.cases..",
                        "io.github.xianreallyhotzzh.dsh.infrastructure..",
                        "io.github.xianreallyhotzzh.dsh.trigger..",
                        "io.github.xianreallyhotzzh.dsh.app.."
                )
                .allowEmptyShould(true)
                .check(classes);
    }
}
