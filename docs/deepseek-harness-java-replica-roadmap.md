# 以 Lesson 课程形式复刻 deepseek-harness-java 的路线图

> 目标：在 DSH-Java 项目内，以循序渐进的 lesson 形式复刻 `vendors/deepseek-harness-java`。
> 每个 lesson 有明确目标和教学文档；走完全部课程即完成复刻，学习者也在过程中深入掌握该项目。
>
> **课程基准**：vendor submodule 固定在 `ff2d0a5`（main）。上游更新前不追新，避免课程失配。

## 为什么走主流程（而不是 wayfinder）

wayfinder 适用于「看不到路在哪」的迷雾型工程。本项目终点完全确定——`vendors/deepseek-harness-java` 的代码就是验收标准。真正的未知不是方向，是 **课程的切法**，这属于磨尖想法（grilling）能解决的问题。

## Phase 0 — 侦察（不占主窗口）

并行发起 `/research` 后台 agent 阅读/vendor 源码，产出带引用的 markdown 落在仓库中：

| # | 研究任务 | 产出 |
|---|---------|------|
| 1 | 模块依赖图与请求生命周期：6 个模块（api / app / case / domain / infrastructure / trigger / types）谁依赖谁；一条对话请求从 trigger 进来到出结果的完整链路 | `docs/research/` 下带引用报告 |
| 2 | 特性清单：把项目拆成可增量交付的功能切片——lesson 切分的原材料 | 同上 |
| 3 | 插件 / skills 子系统机制：项目灵魂与最难点 | 同上 |

注意：报告落在文件里，不要把 vendor 源码直接灌进后续会话，保持磨尖阶段窗口干净。

## Phase 1 — 定课程设计（一个不间断的上下文窗口内完成）

### 1.1 磨尖想法

```
/grill-with-docs 我想以循序渐进 lesson 的形式在 DSH-Java 下复刻 vendors/deepseek-harness-java，
每课有目标和教学文档，走完所有课程即完成复刻
```

需要被逼出答案的关键问题：

- **受众是谁？** 自己 / 未来学员 / 公开课程——决定文档深度与语言
- **lesson 按什么切？** 按模块（先 domain 后 infrastructure）、按 tracer-bullet 增量（每课一条端到端竖切）、还是混合——直接影响「走完自然复刻」能否成立
- **每课的完成定义？** 编译通过 / 测试绿 / 与 vendor 行为对齐
- **复现代码放哪？** 如 `lessons/` 下逐课累积
- **课程基准固定在哪个版本？** 建议 `ff2d0a5`

期间用 `/domain-modeling` 把 harness / plugin / skill / trigger / case 等术语钉进 `CONTEXT.md`——它同时就是课程的教学词汇表。

### 1.2 产出大纲与工单（同一窗口内，勿中途 clear/compact）

```
/to-spec      → 课程大纲：lesson 列表、每课目标、验收标准
/to-tickets   → 一课一票（或一课数票），blocking edges 即课序
```

## Phase 2 — 逐课实现（每课一个全新会话）

每张票开新会话：

```
/implement    → 教学文档 + 该课代码增量（内部走 /tdd）
/code-review  → 两轴收尾审查：代码标准 + 是否达成该课 spec
```

课与课之间 `/clear`。每课自包含，上下文用完即弃。

## Phase 3 — 作为学习者走课

课程建完后用 `/teach` 逐课走一遍：多会话、有状态，是「走课」的天然载体，同时验证教学文档质量；发现的坑回流为课程修订。

构建路径（Phase 0–2）与学习路径（Phase 3）在这里合流。

## 上下文卫生备忘

- Phase 1 的 grill → spec → tickets 必须在**同一个不间断窗口**内完成
- vendor 的阅读量全部由 `/research` 的后台窗口承担，主窗口只消费结论
- Phase 2 每课新开会话，不携带上一课的残余上下文
