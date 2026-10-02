package io.github.xianreallyhotzzh.dsh.trigger.service;

import io.github.xianreallyhotzzh.dsh.api.IAgentApi;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;
import io.github.xianreallyhotzzh.dsh.cases.agent.IAgentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 智能体应用适配器（api 契约的 trigger 层实现）。
 * <p>
 * 执行流程：接收触发层传入的 API DTO → 调用 case 层用例 → 异常收敛为统一信封
 * （00000 成功 / 40000 参数错 / 50000 内部错）后返回 API DTO。
 * <p>
 * 注意：LLM 上游失败不会走到这里——它在领域层已被软化为助手消息内容；
 * 信封折叠只针对 harness 自身故障（如用例非法参数、装配缺陷）。
 */
@Service
public class AgentApi implements IAgentApi {

    private static final Logger log = LoggerFactory.getLogger(AgentApi.class);

    private final IAgentUseCase agentUseCase;

    public AgentApi(IAgentUseCase agentUseCase) {
        this.agentUseCase = agentUseCase;
    }

    @Override
    public Response<AgentMessageResponseDTO> sendMessage(AgentMessageRequestDTO request) {
        return execute(() -> agentUseCase.sendMessage(request));
    }

    private <T> Response<T> execute(java.util.function.Supplier<T> action) {
        try {
            return Response.success(action.get());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("[AgentApi] 用例执行失败，收敛为 50000：{}", exception.getMessage(), exception);
            return Response.internalError(exception.getMessage());
        }
    }
}
