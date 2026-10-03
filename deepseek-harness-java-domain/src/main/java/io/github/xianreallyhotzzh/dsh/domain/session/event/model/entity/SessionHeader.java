package io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * SessionHeader 表示会话头：随会话创建、被持久化与恢复（L09）一起携带的元信息。
 * <p>
 * agentPreset 槽位在 Agent 对话场景存的是创建时的 agentId（见 AgentRunFactory.create），
 * L09 的 findPersistedSession 依赖它按 agentId 找回最近会话。
 */
public record SessionHeader(
        int version,           // session format version (currently 3)
        String sessionId,      // unique session identifier
        Instant createdAt,     // epoch ms, non-negative
        String cwd,            // working directory (or null)
        String parentSession,  // parent session for fork/subagent (or null)
        int seedLength,        // number of seed events inherited from parent
        String origin,         // "subagent" or null
        int delegationDepth,   // recursion depth budget (persisted across restarts)
        String agentPreset     // determines tools & prompts for this session
) {
    public static final int SESSION_FORMAT_VERSION = 3;

    public SessionHeader {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (version < 0 || version > SESSION_FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported session format version: " + version);
        }
        if (seedLength < 0) {
            throw new IllegalArgumentException("seedLength must be non-negative: " + seedLength);
        }
        if (delegationDepth < 0) {
            throw new IllegalArgumentException("delegationDepth must be non-negative: " + delegationDepth);
        }
    }

    /** 创建普通会话头（非 subagent、无种子事件）。 */
    public static SessionHeader create(
            String sessionId,
            Instant createdAt,
            String cwd,
            String agentPreset
    ) {
        return new SessionHeader(
                SESSION_FORMAT_VERSION,
                sessionId,
                createdAt,
                cwd,
                null,   // parentSession
                0,      // seedLength
                null,   // origin
                0,      // delegationDepth
                agentPreset
        );
    }

    /** 创建 subagent 会话头（L25 消费：携带父会话与递归深度预算）。 */
    public static SessionHeader forSubagent(
            String sessionId,
            Instant createdAt,
            String cwd,
            String parentSession,
            int seedLength,
            int delegationDepth,
            String agentPreset
    ) {
        return new SessionHeader(
                SESSION_FORMAT_VERSION,
                sessionId,
                createdAt,
                cwd,
                parentSession,
                seedLength,
                "subagent",
                delegationDepth,
                agentPreset
        );
    }
}
