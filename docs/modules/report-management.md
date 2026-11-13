# 举报与申诉管理

入口为“管理 → 举报与申诉”。页面按服务器能力清单显示举报处理与申诉复核；当前默认授权仍由后端策略决定，本次不调整角色权限。

## 分层与接口

- `ReportManagementRepository` 是独立管理端口，由屏幕层调用；`FoodtimeRepository` 只聚合该接口。管理内部备注使用独立 `ReportManagement` 模型，不混入普通用户回执模型。
- `RemoteRepository` 负责 Cookie／CSRF、地址和会话归属；`ReportManagementJson` 负责分页、时间、图片引用和字段转换。请求前后校验同一账号、角色和会话，旧响应不得交给新账号。
- `ReportManagementPolicy` 只解释服务器能力字符串，不判断角色名。读写权限最终由后端独立校验；管理 Tab 本身仍遵守现有全局管理员入口规则。

| 操作 | API（省略 `/api/v1`） |
|---|---|
| 待处理举报／提交决定 | `GET /admin/reports`、`POST /admin/reports/{id}/decision` |
| 已处理决定 | `GET /admin/reports/decisions` |
| 追问分页／管理员回复 | `GET/POST /admin/reports/{id}/follow-ups` |
| 实际下架、停用、恢复 | `POST /admin/reports/{id}/actions` |
| 申诉分页／详情 | `GET /admin/appeals`、`GET /admin/appeals/{id}` |
| 开始复核 | `POST /admin/appeals/{id}/start?expectedRevision=…` |
| 保存复核结论 | `POST /admin/appeals/{id}/resolve` |

举报及追问使用 `report.review`，申诉使用 `appeal.review`。实际内容动作还需 `moderation.review`，用户动作还需 `user.manage`。客户端隐藏未获授权入口，不代替服务端授权。

## 操作规则

待处理举报必须填写公开理由，可另填内部备注；移除旧的“标为已处理”快捷操作。保存决定不直接处罚。已处理区域独立分页，返回列表保留当前区域与页码。列表异步加载用独立编号隔离迟到响应，失败可刷新并保留已展示记录。

待处理、已处理举报均能打开同一管理追问页。历史和证据可分页查看，公开回复、内部备注分别保存。回复失败保留文字、图片和请求编号，成功后清理本次输入并读取最新页。`EvidenceSlots` 成功上传后在当前账号的原选择槽内保留服务器引用，后续上传或关联失败重试复用它；更新槽位前再次校验账号与原选择，不覆盖用户后来选择的文件。

实际处罚／恢复须再次确认，显示对象和公开理由；服务器生成真实动作记录。申诉复核须先开始，再填写公开结论；提交 `expectedRevision`，冲突时保留输入、提示刷新，不强行覆盖。复核成立不自动执行恢复，需要回到已处理举报核对决定编号并单独确认。

## 验证边界

`ReportManagementPolicyTest`、`ReportManagementJsonTest` 覆盖能力组合、私有字段、分页和异常响应。`ReportManagementRunner` 仅允许 `.reportmanagementcheck` 且 API 为回环 `18097` 的 Debug 包运行，使用真实窗口、系统触摸及独立 HTTP 夹具，覆盖决定、动作确认、分页返回、证据复用、追问、申诉版本冲突和能力撤销。普通尺寸与窄屏大字体各完成 24 项检查。

夹具不等于真实后端的完整业务联调；正式服务器授权及真实账号的处罚恢复、双方回执和通知仍需按发布前验收清单继续验证。正式 APK 仍只能由 main 成功检查后的发布流程生成。
