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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 派发节点：构造用户消息并投递到 Agent 收件箱。
 * <p>
 * L04 面：按意图增强消息文本（任务执行注入「先用工具」约束、目录任务生成 ls 指令提示）。
 * vendor 版还解析图片附件为 ImageBlock（L07）与 @插件 提及指令（L14）。
 */
@Service("agentMessageDispatchNode")
public class AgentDispatchNode extends AbstractAgentMessageNode {

    private static final Logger log = LoggerFactory.getLogger(AgentDispatchNode.class);

    // 提取消息中的绝对路径：遇到空白和常见中英文标点即停止，避免把后续说明文字吞进路径。
    private static final Pattern ABSOLUTE_PATH = Pattern.compile("(/[^\s，。,；;：:\"'“”‘’（）()！？!?、<>]+)");
    private static final Pattern DIRECTORY_QUESTION = Pattern.compile("(?i)(目录|项目|文件夹|仓库|里面有什么|有哪些|包含什么|列表|list|what's in|what is in)");

    private final AgentCollectNode next;

    public AgentDispatchNode(AgentCollectNode next) {
        this.next = next;
    }

    /**
     * 构造用户消息并投递到收件箱、唤醒 Agent。
     * <p>数据示例：message="你好"（CHAT）原样投递；"/tmp 目录里有什么"（TASK_EXECUTION）
     * 被改写为「推荐用 shell_execute 执行 ls -la /tmp …」的任务指令。
     */
    @Override
    protected AgentMessageResponseDTO doApply(AgentMessageRequestDTO request,
                                              AgentMessageDynamicContext ctx) {
        var intent = ctx.getIntent();
        String messageText = prepareMessageText(request.message(), intent);
        Message userMessage = Message.createUser(
                List.of(new TextBlock(messageText)),
                new MessageSource.UserSource()
        );
        ctx.setUserMessage(userMessage);
        log.info("[Dispatch] Agent={} 构造用户消息并发送到收件箱（intent={} 文本长度={}）",
                request.agentId(), intent, messageText.length());
        ctx.getAgent().send(userMessage, InboxTarget.NEXT_TURN, true);
        log.info("[Dispatch] Agent={} 消息已投递收件箱并唤醒 Agent，等待执行...", request.agentId());
        return null;
    }

    @Override
    public StrategyHandler<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO>
    getNext(AgentMessageRequestDTO request, AgentMessageDynamicContext ctx) {
        return next;
    }

    /**
     * 根据意图增强用户消息。
     * <p>任务执行消息会加入「先使用工具」的约束；目录问题会进一步生成明确的 shell 命令提示；
     * 闲聊和代码问答保持原文，避免影响模型表达。
     */
    static String prepareMessageText(String messageText, AgentMessageDynamicContext.MessageIntent intent) {
        if (intent == AgentMessageDynamicContext.MessageIntent.TASK_EXECUTION) {
            String directoryInstruction = buildDirectoryInstruction(messageText);
            if (directoryInstruction != null) {
                return directoryInstruction;
            }
            return "请先使用可用工具完成下面的任务，不要在未使用工具前直接给出结论。"
                    + "如果任务涉及本地目录、文件、代码或仓库状态，优先读取/搜索后再回答。\n\n"
                    + "用户原始请求：" + messageText;
        }
        return messageText;
    }

    /**
     * 为目录类任务生成精确的工具指令。
     * <p>只有文本同时命中目录关键词和绝对路径、且路径真实存在为目录时才返回提示；否则返回 null，
     * 交由通用的任务执行指令处理。
     * <p>数据示例：输入“/tmp 目录里有什么”生成建议执行 `ls -la "/tmp"` 的提示。
     */
    private static String buildDirectoryInstruction(String messageText) {
        if (messageText == null || !DIRECTORY_QUESTION.matcher(messageText).find()) {
            return null;
        }
        Matcher matcher = ABSOLUTE_PATH.matcher(messageText);
        if (!matcher.find()) {
            return null;
        }
        String directory = matcher.group(1);
        // 路径必须真实存在且是目录才改写为 ls 指令——否则像 "SELECT/SHOW/DESC"
        // 这类文本片段会被误当成目录路径，把用户消息改写得面目全非。
        try {
            if (!java.nio.file.Files.isDirectory(java.nio.file.Path.of(directory))) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        return "你需要列出这个目录的一级子目录和文件。推荐用 `shell_execute` 工具执行 `ls -la \"" + directory + "\"` 获取完整列表，\n"
                + "或者用 `find \"" + directory + "\" -maxdepth 1 -type d` 只列目录。\n"
                + "拿到真实结果后，用 Markdown 列表展示所有一级项目，不要遗漏、不要模糊化。\n\n"
                + "用户原始请求：" + messageText;
    }
}
