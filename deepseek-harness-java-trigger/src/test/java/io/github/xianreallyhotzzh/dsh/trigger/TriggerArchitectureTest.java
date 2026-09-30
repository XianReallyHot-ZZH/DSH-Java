package io.github.xianreallyhotzzh.dsh.trigger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * L01 起步规则：trigger 不依赖 app。
 *
 * <p>另含一条「case 不依赖 trigger」规则及其违规样例证明：Maven 依赖图本身已使
 * case→trigger 无法编译（且加边即成环），故规则与样例寄放在唯一能同时看见
 * cases/trigger 两个包段的 trigger 模块测试里，证明 ArchUnit 规则对这类依赖真的会红。</p>
 */
class TriggerArchitectureTest {

    @Test
    void triggerMustNotDependOnApp() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("io.github.xianreallyhotzzh.dsh");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("io.github.xianreallyhotzzh.dsh.trigger..")
                .should().dependOnClassesThat().resideInAPackage(
                        "io.github.xianreallyhotzzh.dsh.app.."
                )
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void archRuleMustCatchCaseDependingOnTrigger() {
        // 违规样例：cases 包段下的类 import 了 trigger 包段的类（均为 trigger 模块的测试 fixture）
        var violatingClasses = new ClassFileImporter()
                .importPackages("io.github.xianreallyhotzzh.dsh.cases.violationsample");

        var caseMustNotDependOnTrigger = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("io.github.xianreallyhotzzh.dsh.cases..")
                .should().dependOnClassesThat().resideInAPackage(
                        "io.github.xianreallyhotzzh.dsh.trigger.."
                );

        var error = assertThrows(AssertionError.class,
                () -> caseMustNotDependOnTrigger.check(violatingClasses));
        assertTrue(error.getMessage().contains("CaseDependingOnTrigger"),
                "ArchUnit 应点名违规类，实际消息：" + error.getMessage());
    }
}
