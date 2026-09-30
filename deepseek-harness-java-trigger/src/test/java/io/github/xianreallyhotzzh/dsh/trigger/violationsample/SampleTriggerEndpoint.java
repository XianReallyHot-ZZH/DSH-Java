package io.github.xianreallyhotzzh.dsh.trigger.violationsample;

/**
 * ArchUnit 违规样例 fixture：trigger 包段下的靶类。
 * 仅被 CaseDependingOnTrigger 引用，用于证明架构规则能抓住反向依赖。
 */
public final class SampleTriggerEndpoint {

    public String endpointName() {
        return "sample";
    }
}
