# 无业务请求头的 v0.3 POC API 对齐设计

## 目标

依据附件 `poc-api.md` 对现有 CNAPS 单表 POC 做全链路最小对齐，使 WebFE Mock 与真实 Tuxedo/Oracle 路径都能验证健康检查、参考数据查询、单据新增、查询、详情、修改、逻辑删除、复核通过和复核退回。

本次不引入登录、账号、角色、权限或真实身份切换。客户端不传业务请求头，复核操作也不校验复核人与录入人是否相同。

## 方案选择

采用“全链路最小对齐”方案：保留现有 JSP/Servlet、Tuxedo/FML32、OCI/Oracle 架构，在现有边界内修改 WebFE、Mock、原生 C 服务、契约测试和接口文档。

未采用以下方案：

- 仅修改 WebFE/Mock：真实 Tuxedo 环境会与本地验证行为不一致。
- 重构独立业务层或账号体系：超出单表 POC 的验证目标。

## HTTP 契约

### 请求上下文

公共 HTTP API 不接收以下业务请求头：

- `requestId`
- `operatorNo`
- `branchNo`
- `workDate`

`Content-Type: application/json; charset=UTF-8` 是 JSON 请求的协议头，不属于业务请求头，继续保留。

WebFE 为每次调用生成内部 `requestId`。`operatorNo` 和 `branchNo` 使用服务器端固定 POC 配置，仅用于满足现有 FML32 和单表字段，不作为身份或权限依据，也不允许请求 Body、Query 或 Header 覆盖。

`workDate` 是业务字段：

- 新增单据时由 JSON Body 必传，格式为 `yyyy-MM-dd`。
- 修改单据时可在 JSON Body 传入；缺省时保持原值。
- 通用查询和待复核查询可使用 Query 参数 `workDate`；缺省时由 WebFE 填入服务器当前日期。
- 单据详情、删除和复核操作只需要 `billId`，不额外要求工作日期。

### 响应结构

所有接口统一返回且仅返回以下顶层字段：

```json
{
  "respCode": "0000",
  "respMsg": "操作已成功",
  "data": {}
}
```

失败时 `data` 为 `null`。移除当前额外的 `success` 字段，不向客户端返回内部 `requestId`。

### 接口范围

保留附件定义的接口和 Tuxedo 服务映射：

| HTTP API | Tuxedo 服务 |
|---|---|
| `GET /api/health` | `SYSHEALTH` |
| `GET /api/dicts/{dictType}` | `DICTQRY` |
| `GET /api/banks` | `BANKQRY` |
| `POST /api/cnaps/vouchers` | `CNAPS5701E` |
| `PUT /api/cnaps/vouchers/{billId}` | `CNAPS5701U` |
| `POST /api/cnaps/vouchers/{billId}/delete` | `CNAPS5701D` |
| `GET /api/cnaps/vouchers` | `CNAPS4609Q` |
| `GET /api/cnaps/vouchers/review-list` | `CNAPS5702Q` |
| `GET /api/cnaps/vouchers/{billId}` | `CNAPS5702I` |
| `POST /api/cnaps/vouchers/{billId}/review-pass` | `CNAPS5702A` |
| `POST /api/cnaps/vouchers/{billId}/review-return` | `CNAPS5702R` |

分页请求字段统一为 `pageNo` 和 `pageSize`，响应使用 `pageNo`、`pageSize`、`total`、`records`。不再把 `page` 和 `size` 作为公开契约。

通用查询支持附件列出的 `status`、`serialNo`、`voucherNo`、`payeeName`、`payeeAccountNo`、`includeDeleted`、`pageNo` 和 `pageSize`，并补充已确认的可选 `workDate`。待复核查询支持 `serialNo`、`pageNo`、`pageSize` 和可选 `workDate`。

## 单据行为

### 新增与修改

新增按附件接收完整单据字段，至少校验 `workDate`、`businessType`、三段付款账号、`payeeAccountNo`、`payeeName`、`priority`、`systemType` 和 `amount`。金额必须大于零且最多两位小数；手续费不得小于零。未传可选字段时使用附件给出的 POC 默认值。

新增后生成 `billId` 和 `serialNo`，状态为 `10_PENDING_REVIEW`。修改只允许状态为 `10_PENDING_REVIEW` 或 `30_REVIEW_REJECTED` 的单据，成功后回到 `10_PENDING_REVIEW`。修改工作日期不重新生成 `billId` 或 `serialNo`。

### 查询与详情

通用查询默认不包含 `40_DELETED`，只有 `includeDeleted=true` 时包含逻辑删除记录。查询条件、分页结果和详情字段在 Mock 与真实 Tuxedo 路径保持一致。

### 删除

删除采用 `POST /{billId}/delete`，只允许 `10_PENDING_REVIEW` 和 `30_REVIEW_REJECTED`，成功后状态为 `40_DELETED`。`deleteReason` 可选。

### 复核

复核通过和复核退回只允许 `10_PENDING_REVIEW`：

- 复核通过后状态为 `20_REVIEW_APPROVED`。
- 复核退回要求 `rejectReason`，成功后状态为 `30_REVIEW_REJECTED`。
- 不比较 `operatorNo` 与原录入员，不返回 `3005`，不实现任何账号或身份切换逻辑。
- `checkerNo` 等存量审计字段可写入固定 POC 操作员值，但该值没有身份含义。

## 数据流与组件边界

1. Servlet 从 Path、Query 和 JSON Body 提取公开参数，不读取业务请求头。
2. WebFE 生成内部请求号，并把固定 POC 操作员号、机构号与接口参数映射为 FML32 字段。
3. Mock 客户端或 Jolt 客户端调用同名 Tuxedo 服务。
4. Tuxedo 服务执行附件规定的校验、状态判断、查询或写库。
5. WebFE 将 FML32 响应映射为附件规定的 JSON 字段和三字段响应包络。

现有单表 `T_CNAPS_BILL_POC` 继续使用，不新增账号表、权限表、独立审计表或额外业务表。

## 错误处理

保留附件中的主要业务错误码及 HTTP 状态映射：

- `2001`：必填字段缺失，HTTP 400。
- `2002`：字段格式错误，HTTP 400。
- `2003`：字典值无效，HTTP 400。
- `3001`：单据不存在，HTTP 404。
- `3003`：当前状态不允许操作，HTTP 409。
- `3004`：复核时单据状态已变化，HTTP 409。
- `4001`：数据库错误，HTTP 500。
- `4002`：Tuxedo 超时，HTTP 504。
- `4003`：Tuxedo 服务不可用，HTTP 503。
- `9999`：未分类错误，HTTP 500。

`3005` 不再用于本 POC。错误响应同样只包含 `respCode`、`respMsg` 和 `data`。

## 页面与文档

修正现有录入页缺少 `workDate` 导致新增请求必然失败的问题。页面继续作为简单联调入口，不做视觉重构，也不增加账号选择、登录或权限相关控件。

`docs/cnaps-frontend-api.md` 更新为附件 v0.3 契约，删除旧的业务请求头、`success` 字段、`page`/`size` 别名和同人复核限制说明。既有历史设计文档保留，不作为当前公开接口说明。

## 测试与验收

自动化测试按先失败后实现的方式覆盖：

- 请求 Header 中的业务上下文不能覆盖服务器内部值。
- 新增 Body 必须携带合法 `workDate`，现有录入页会提交该字段。
- 响应顶层只有 `respCode`、`respMsg`、`data`。
- `pageNo`、`pageSize`、附件查询条件和 `includeDeleted` 行为正确。
- 新增、详情、修改、删除的状态与字段变化符合附件。
- 同一固定 POC 操作员可以完成复核通过或复核退回。
- 复核退回缺少 `rejectReason` 时返回 `2001`。
- 非法状态下修改、删除或复核返回对应错误。
- Mock 行为、Java/FML32 映射、Jolt 元数据和原生 C/OCI 源码契约一致。
- Maven 测试、WAR 构建以及项目现有脚本级契约测试通过。

## 非目标

本次不实现登录、账号管理、操作员切换、角色权限、复核员身份隔离、真实扣账、真实 CNAPS 发送、复杂审计、并发流水号增强或与附件无关的重构。
