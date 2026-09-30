# L07 工具矩阵扩展与守卫（S07）

- 前置：L06（tag L06）
- 大纲节：docs/lessons/README.md#L07
- 术语：工具 / 工具目录 / 钩子 / 危险命令守卫

## vendor 精读清单
- `domain/.../tool/fs/FsGrepTool/FsEditTool/FsReadImageTool/StrReplaceEditorTool.java`
- `domain/.../tool/web/WebSearchTool.java`、`WebFetchTool.java` + `infrastructure/.../adapter/web/LocalWebService.java`
- `domain/.../agent/service/ask/AskUserTool.java` + `cases/question/RuntimeQuestionCaseImpl.java` + `trigger/.../http/command/RuntimeQuestionController.java`（运行时提问-回答闭环）
- `domain/.../tool/shell/DangerousCommandGuard.java`
- `domain/.../guard/timeout/`、`guard/reminder/`

## 实现增量
- 做：四个 fs 高阶工具、web 两工具（外部 HTTP，测试用本地 stub）、AskUser 闭环（工具挂起→REST 回答→回合继续）、危险命令守卫、guard 域、工具目录扩容。
- 不做：审批包装版端口（L13）、lsp/terminal/jobs 等条件工具（L25）。

## 测试搬运
- vendor fs/web/ask 相关单测（存在的部分）。

## 验收（DoD 专项）
- [ ] 真端点完成「改一个文件的字符串」「搜索网页并总结」
- [ ] ask_user_question 挂起 → `POST /api/harness/questions/runtime/{id}/answer` → 对话继续
- [ ] `rm -rf /`、fork 炸弹类命令被拦截提醒不执行

## 教学文档与配图
- `docs/lessons/L07-tool-matrix.md`；mermaid：AskUser 闭环时序图。

## 教学点
- AskUser 与审批门的形似与神异（都要挂起等外部输入，一个问用户一个问审批人）。
