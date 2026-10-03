package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

import java.util.List;
import java.util.Objects;

/**
 * SurfaceIntent 表示事件对「表面投影」的意图：执行什么操作、源自哪些事件。
 */
public record SurfaceIntent(
        SurfaceOp surfaceOp,
        List<Long> sourceEventSeqs
) {
    public SurfaceIntent {
        Objects.requireNonNull(surfaceOp, "surfaceOp");
        sourceEventSeqs = sourceEventSeqs == null ? List.of() : List.copyOf(sourceEventSeqs);
    }
}
