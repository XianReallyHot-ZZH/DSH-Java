package io.github.xianreallyhotzzh.dsh.domain.model.valobj;

/** 模型的推理过程文本（reasoning_content），对终端用户默认折叠展示。 */
public record ReasoningBlock(String text) implements ContentBlock {}
