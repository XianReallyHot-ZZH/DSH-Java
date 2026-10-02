package io.github.xianreallyhotzzh.dsh.domain.llm.service;

import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.FinishReason;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.StreamChunk;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.TokenUsage;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.MessageSource;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.ContentBlock;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.ReasoningBlock;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把流式分片归并为最终内容块与 assistant 消息的领域服务。
 * <p>
 * L03 子集处理 text / reasoning 两类块；tool-call 分支随 L06 加入。
 */
public class BlockAssembler {

    private static final class PartialBlock {
        String blockType;
        StringBuilder text = new StringBuilder();
        /** 由内容块结束事件设置；设置后冻结当前部分内容。 */
        ContentBlock block;

        PartialBlock(String blockType) {
            this.blockType = blockType;
        }
    }

    private final Map<Integer, PartialBlock> partials = new LinkedHashMap<>();
    private final List<Integer> order = new ArrayList<>();
    private TokenUsage usage;
    private FinishReason finish;

    public void push(StreamChunk chunk) {
        if (chunk instanceof StreamChunk.BlockStart bs) {
            if (!partials.containsKey(bs.index())) {
                order.add(bs.index());
                partials.put(bs.index(), new PartialBlock(bs.blockType()));
            }
        } else if (chunk instanceof StreamChunk.TextDelta td) {
            PartialBlock partial = ensure(td.index(), "text");
            if (partial.block != null) return; // 已关闭；忽略迟到的增量
            partial.text.append(td.text());
        } else if (chunk instanceof StreamChunk.ReasoningDelta rd) {
            PartialBlock partial = ensure(rd.index(), "reasoning");
            if (partial.block != null) return;
            partial.text.append(rd.text());
        } else if (chunk instanceof StreamChunk.BlockEnd be) {
            PartialBlock partial = ensure(be.index(), be.block() != null ? be.block().getClass().getSimpleName() : "unknown");
            if (partial.block != null) return; // 首次关闭生效
            partial.block = be.block();
        } else if (chunk instanceof StreamChunk.Usage u) {
            this.usage = u.usage();
        } else if (chunk instanceof StreamChunk.Finish f) {
            this.finish = f.reason();
        }
    }

    private PartialBlock ensure(int index, String blockType) {
        PartialBlock partial = partials.get(index);
        if (partial == null) {
            partial = new PartialBlock(blockType);
            partials.put(index, partial);
            order.add(index);
        }
        return partial;
    }

    private ContentBlock assemble(PartialBlock partial, int index) {
        if (partial.block != null) return partial.block;
        return switch (partial.blockType) {
            case "text", "TextBlock" -> new TextBlock(partial.text.toString());
            case "reasoning", "ReasoningBlock" -> new ReasoningBlock(partial.text.toString());
            default -> throw new IllegalStateException(
                    "cannot assemble incomplete block of type \"" + partial.blockType + "\"");
        };
    }

    public List<ContentBlock> blocks() {
        List<ContentBlock> all = new ArrayList<>();
        for (int index : order) {
            PartialBlock partial = partials.get(index);
            if (partial == null) throw new IllegalStateException(
                    "BlockAssembler invariant violated: no partial for index " + index);
            all.add(assemble(partial, index));
        }
        return all;
    }

    public TokenUsage usage() {
        return usage;
    }

    public FinishReason finish() {
        return finish != null ? finish : new FinishReason.Stop();
    }

    public Message message(MessageSource source) {
        return new Message(
                UUID.randomUUID().toString(),
                "assistant",
                blocks(),
                source != null ? source : new MessageSource.PluginSource("dsh-llm/assembler")
        );
    }
}
