package io.github.xianreallyhotzzh.dsh.domain.agent.service;

import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.InboxTarget;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 的收件箱领域模型。
 * <p>
 * L03 最小子集：两段内存队列 + append/claim/hasPending/clear。完整语义
 * （会话事件切片 AgentInboxSpliced、重放 replay、投递通知）随 L04 事件日志加入。
 */
public final class Inbox {

    private final List<Message> nextTurn = new ArrayList<>();
    private final List<Message> nextStep = new ArrayList<>();

    /** 投递一条消息到指定目标段。 */
    public synchronized void append(InboxTarget target, Message message) {
        listFor(target).add(message);
    }

    /** 是否还有待处理消息。 */
    public synchronized boolean hasPending() {
        return !nextTurn.isEmpty() || !nextStep.isEmpty();
    }

    /**
     * 抢占待处理消息：先取全部下一步消息，再从下一回合段取最早一条。
     * <p>数据示例：NEXT_TURN 段有 [m1, m2] 时 claim 返回 [m1]，m2 留给后续回合。
     */
    public synchronized List<Message> claim(InboxTarget target, int turn) {
        List<Message> claimed = new ArrayList<>(nextStep);
        nextStep.clear();
        if (target == InboxTarget.NEXT_TURN && !nextTurn.isEmpty()) {
            claimed.add(nextTurn.remove(0));
        }
        return claimed;
    }

    /** 清空全部待处理消息。 */
    public synchronized void clear() {
        nextTurn.clear();
        nextStep.clear();
    }

    private List<Message> listFor(InboxTarget target) {
        return target == InboxTarget.NEXT_STEP ? nextStep : nextTurn;
    }
}
