package io.github.xianreallyhotzzh.dsh.cases.violationsample;

import io.github.xianreallyhotzzh.dsh.trigger.violationsample.SampleTriggerEndpoint;

/**
 * ArchUnit 违规样例 fixture：case 层类直接依赖 trigger 层类——
 * 正确架构下不允许的依赖方向（case 只应编排 domain，见 CONTEXT.md「case 层」）。
 * 该类仅存在于 trigger 模块测试源，证明「case 不得依赖 trigger」规则命中时会红。
 */
public final class CaseDependingOnTrigger {

    private final SampleTriggerEndpoint endpoint = new SampleTriggerEndpoint();

    public String endpointName() {
        return endpoint.endpointName();
    }
}
