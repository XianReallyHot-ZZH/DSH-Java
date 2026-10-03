package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

/**
 * SurfaceOp 表示表面投影操作：追加一条消息，或用一个区间替换既有消息。
 * <p>
 * 替换区间的语义：派生表面时，[start, end] 内既有表面消息被本事件遮蔽（shadow），
 * 本事件自身投影成为新消息——计划模式提交、ask_user 回填都靠它改写历史（L07/L20）。
 */
public sealed interface SurfaceOp permits SurfaceOp.Append, SurfaceOp.Replace {

    /** 追加操作。 */
    record Append() implements SurfaceOp {}

    /** 区间替换操作：遮蔽 [start, end] 序号上的表面消息。 */
    record Replace(long start, long end) implements SurfaceOp {
        public Replace {
            if (start < 0 || end < 0 || end < start) {
                throw new IllegalArgumentException("Invalid replace range: start=" + start + " end=" + end);
            }
        }
    }

    static SurfaceOp append() {
        return new Append();
    }

    static SurfaceOp replace(long start, long end) {
        return new Replace(start, end);
    }
}
