package io.github.xianreallyhotzzh.dsh.cases.agent.node;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.cases.agent.factory.AgentMessageDynamicContext;
import io.github.xianreallyhotzzh.dsh.cases.orchestration.StrategyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 意图节点：基于规则将用户消息归类为轻量意图（CHAT、CODE_QUESTION、
 * TASK_EXECUTION、CLARIFICATION）。意图写入动态上下文，供下游节点调整行为。
 * <p>
 * 在策略树中位于 Resolve 和 Dispatch 之间。L04 最小版即 vendor 现行的规则分类；
 * 完整版（[no-tools] 标记等）随 L08 上下文工程加入。
 */
@Service("agentMessageIntentNode")
public class AgentIntentNode extends AbstractAgentMessageNode {

    private static final Logger log = LoggerFactory.getLogger(AgentIntentNode.class);

    private final AgentDispatchNode next;

    public AgentIntentNode(AgentDispatchNode next) {
        this.next = next;
    }

    private static final Pattern FILE_PATH = Pattern.compile(
            "([/~][\\w./-]+\\.[a-z]{2,8})|([A-Za-z]:[\\\\\\w./-]+)"
    );
    private static final Pattern DIRECTORY_PATH = Pattern.compile(
            "(?<![\\w])(?:/[\\w.-]+(?:/[\\w.-]+)+|[A-Za-z]:\\\\(?:[\\w.-]+\\\\?)+)"
    );
    private static final Pattern FILESYSTEM_KEYWORDS = Pattern.compile(
            "(?i)(目录|文件夹|文件|项目|仓库|列出|罗列|查看|读取|搜索|查找|分析|统计)"
    );
    private static final Pattern EXPLAINER = Pattern.compile(
            "(?i)(解释|说明|讲讲|介绍|是什么|什么是|为什么|怎么理解|how does|what is|why|explain)"
    );
    private static final Pattern SMALL_TALK = Pattern.compile(
            "(?i)(你是谁|你会什么|你好呀|在吗|谢谢|不客气|再见|拜拜|thanks|thank you|goodbye|bye)"
    );
    private static final Pattern CODE_FENCE = Pattern.compile("```");
    private static final Pattern TASK_VERBS = Pattern.compile(
            "(?i)(帮我|请|执行|运行|创建|删除|修改|安装|部署|重构|实现|修复|生成|构建|编译|测试)"
    );
    private static final Pattern GREETING = Pattern.compile(
            "(?i)^(你好|嗨|hi|hello|hey|谢谢|感谢|thx|thanks|再见|bye|早上好|下午好|晚上好)[\\s!！.。?？]*$"
    );
    private static final Pattern CODE_KEYWORDS = Pattern.compile(
            "(?i)(函数|方法|类|接口|变量|bug|报错|异常|编译|语法|api|json|sql|正则|算法|线程|并发|内存|性能|优化|重构)"
    );

    /**
     * 识别用户输入的轻量意图，并写入策略上下文。
     * <p>数据示例：“你好”识别为 CHAT；“帮我修复 /src/App.java 的报错”识别为 TASK_EXECUTION。
     */
    @Override
    protected AgentMessageResponseDTO doApply(AgentMessageRequestDTO request,
                                              AgentMessageDynamicContext ctx) {
        String msg = request.message() != null ? request.message().trim() : "";
        AgentMessageDynamicContext.MessageIntent intent = classify(msg);
        ctx.setIntent(intent);
        log.info("[Intent] Agent={} 意图识别结果={}（消息长度={}，{}）",
                request.agentId(), intent, msg.length(),
                msg.length() > 60 ? msg.substring(0, 60) + "..." : msg);
        return null;
    }

    @Override
    public StrategyHandler<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO>
    getNext(AgentMessageRequestDTO request, AgentMessageDynamicContext ctx) {
        return next;
    }

    /**
     * 按规则将文本归类为四种消息意图。
     * <p>优先级为：空文本 -> 问候闲聊 -> 代码问题 -> 文件系统任务 -> 一般任务 -> 解释请求 -> CLARIFICATION。
     * <p>数据示例：“/tmp 目录里有什么”命中目录路径和文件系统关键词，返回 TASK_EXECUTION。
     */
    AgentMessageDynamicContext.MessageIntent classify(String msg) {
        if (msg.isEmpty()) return AgentMessageDynamicContext.MessageIntent.CLARIFICATION;

        if (GREETING.matcher(msg).matches()) {
            return AgentMessageDynamicContext.MessageIntent.CHAT;
        }

        boolean hasFilePath = FILE_PATH.matcher(msg).find();
        boolean hasDirectoryPath = DIRECTORY_PATH.matcher(msg).find();
        boolean hasCodeFence = CODE_FENCE.matcher(msg).find();
        boolean hasCodeKeyword = CODE_KEYWORDS.matcher(msg).find();
        boolean hasTaskVerb = TASK_VERBS.matcher(msg).find();
        boolean hasFilesystemKeyword = FILESYSTEM_KEYWORDS.matcher(msg).find();
        boolean hasExplainer = EXPLAINER.matcher(msg).find();
        boolean hasSmallTalk = SMALL_TALK.matcher(msg).find();

        if (hasFilePath || hasDirectoryPath || hasCodeFence || (hasCodeKeyword && hasTaskVerb)) {
            return AgentMessageDynamicContext.MessageIntent.TASK_EXECUTION;
        }
        if (hasSmallTalk) {
            return AgentMessageDynamicContext.MessageIntent.CHAT;
        }
        if (hasCodeKeyword) {
            return AgentMessageDynamicContext.MessageIntent.CODE_QUESTION;
        }
        if (hasExplainer && hasTaskVerb) {
            return AgentMessageDynamicContext.MessageIntent.CODE_QUESTION;
        }
        if (hasTaskVerb) {
            return AgentMessageDynamicContext.MessageIntent.TASK_EXECUTION;
        }
        if (hasFilesystemKeyword && (msg.endsWith("？") || msg.endsWith("?"))) {
            return AgentMessageDynamicContext.MessageIntent.TASK_EXECUTION;
        }
        return AgentMessageDynamicContext.MessageIntent.CHAT;
    }
}
