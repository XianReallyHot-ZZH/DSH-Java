package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

/**
 * TurnEndReason 定义回合结束原因的封闭集合。
 * <p>
 * Completed：模型不再请求工具，回合自然完成；Aborted：取消/中断；Blocked：
 * 等待外部输入（如 ask_user，L07）；Error：执行故障；MaxTokens：输出被 token
 * 上限截断（受控续写 L08）；Interrupted：线程中断。
 */
public sealed interface TurnEndReason permits
        TurnEndReason.Completed,
        TurnEndReason.Aborted,
        TurnEndReason.Blocked,
        TurnEndReason.Error,
        TurnEndReason.MaxTokens,
        TurnEndReason.Interrupted {

    /** 正常完成（模型无需更多工具调用）。 */
    record Completed() implements TurnEndReason {}

    /** 被取消或中止。 */
    record Aborted(String reason) implements TurnEndReason {
        public Aborted { reason = reason == null ? "" : reason; }
    }

    /** 被外部输入阻塞（L07 ask_user 闭环）。 */
    record Blocked() implements TurnEndReason {}

    /** 执行出错。 */
    record Error(String message, String code) implements TurnEndReason {
        public Error { message = message == null ? "" : message; code = code == null ? "UNKNOWN" : code; }
    }

    /** 输出达到 token 上限（L08 受控续写）。 */
    record MaxTokens() implements TurnEndReason {}

    /** 线程中断。 */
    record Interrupted() implements TurnEndReason {}
}
