# L23 数字人目录（S22a）

- 前置：L22（tag L22）
- 大纲节：docs/lessons/README.md#L23
- 术语：数字人 / Agent Card / A2A

## vendor 精读清单
- `infrastructure/.../digitalhuman/DigitalHumanService.java`（380 行：目录 CRUD、Agent Card 三路径探测、健康检查；**探测不落库凭据**）
- `infrastructure/.../digitalhuman/RemoteEndpointCredentialRegistry.java`
- 表：`digital_human`(schema.sql L167)、`digital_human_endpoint`(L182) + 对应 dao/po
- `trigger/.../http/DigitalHumanController.java`（CRUD + `/discover` + `/{id}/health-check`）
- 注意：`DigitalHumanController` 直接 import infrastructure 服务（trigger→infrastructure 例外之一）——照原样复刻并标注。

## 实现增量
- 做：数字人目录域、双表、card 探测（本地/远端双协议）、健康检查、凭据只在内存注册表、REST 五端点。
- 不做：协作房间（L24）；远端凭据持久化（vendor 明确不落）。

## 测试搬运
- `DigitalHumanServiceA2aDiscoveryTest`（改包名）。

## 验收（DoD 专项）
- [ ] 对一个 card 端点（vendor 实例、或复刻件自身双开、或 mock）discover 成功并登记
- [ ] 健康检查通/断两态正确；凭据不出现在任何表中
- [ ] CRUD + 列表可用

## 教学文档与配图
- `docs/lessons/L23-digital-human.md`；mermaid：discover→登记→健康检查链路 + 双表关系。

## 教学点
- 「数字人 = 人格化 Agent 端点」而非虚拟形象；凭据不落库的安全边界；分层例外（trigger 直连 infrastructure）的如实记录。
