package io.github.xianreallyhotzzh.dsh.cases.agent.node;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.cases.agent.factory.AgentMessageDynamicContext;
import io.github.xianreallyhotzzh.dsh.cases.orchestration.StrategyHandler;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.InboxTarget;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.MessageSource;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 派发节点：构造用户消息并投递到 Agent 收件箱。
 * <p>
 * L03 最小实现：原样包装请求文本。vendor 版还按意图改写消息文本（目录任务生成
 * ls 指令提示等，随 L04 Intent 节点接入）与解析图片附件为 ImageBlock（L07）。
 */
@Service("agentMessageDispatchNode")
public class AgentDispatchNode extends AbstractAgentMessageNode {

    private static final Logger log = LoggerFactory.getLogger(AgentDispatchNode.class);

    private final AgentCollectNode next;

    public AgentDispatchNode(AgentCollectNode next) {
        this.next = next;
    }

    /**
     * 构造用户消息并投递到收件箱、唤醒 Agent。
     * <p>数据示例：message="你好" → TextBlock("你好") → send(NEXT_TURN, wakeup=true)。
     */
    @Override
    protected AgentMessageResponseDTO doApply(AgentMessageRequestDTO request,
                                              AgentMessageDynamicContext ctx) {
        Message userMessage = Message.createUser(
                List.of(new TextBlock(request.message())),
                new MessageSource.UserSource()
        );
        ctx.setUserMessage(userMessage);
        log.info("[Dispatch] Agent={} 构造用户消息并发送到收件箱（文本长度={}）",
                request.agentId(), request.message().length());
        ctx.getAgent().send(userMessage, InboxTarget.NEXT_TURN, true);
        log.info("[Dispatch] Agent={} 消息已投递收件箱并唤醒 Agent，等待执行...", request.agentId());
        return null;
    }

    @Override
    public StrategyHandler<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO>
    getNext(AgentMessageRequestDTO request, AgentMessageDynamicContext ctx) {
        return next;
    }
}
