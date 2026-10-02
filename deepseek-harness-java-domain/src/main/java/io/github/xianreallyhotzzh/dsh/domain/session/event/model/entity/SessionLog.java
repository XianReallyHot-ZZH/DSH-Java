package io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity;

import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 单个会话的内存消息日志。
 * <p>
 * L03 最小子集：按追加顺序保存 Message，供 Collect 节点折叠响应与下一步请求
 * 读取历史。L04 升级为事件日志（SessionEvent 有序 WAL + deriveMessages 折叠），
 * 类名与路径保持与 vendor 一致以便平滑演进。
 */
public class SessionLog {

    private final String sessionId;
    private final List<Message> messages = new ArrayList<>();

    public SessionLog(String sessionId) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    }

    public String sessionId() {
        return sessionId;
    }

    /** 追加一条消息（用户或助手）。 */
    public synchronized void append(Message message) {
        messages.add(message);
    }

    /** 当前已累积的消息快照。 */
    public synchronized List<Message> messages() {
        return List.copyOf(messages);
    }

    /** 折叠出下一次模型请求的历史消息（L03 与 messages() 等价；L04 起从事件折叠）。 */
    public List<Message> deriveMessages() {
        return messages();
    }
}
