# CNAPS 凭证审核手工测试用例

> 文档版本：v1.0<br>
> 生成日期：2026-07-21<br>
> 文档状态：待确认（存在未关闭 TBD，相关用例标记为阻塞）<br>
> 代码基线：Git `84b0d98`；候选构建号、部署提交和测试环境版本在执行前填写<br>
> 测试范围：WebFE -> Jolt -> Tuxedo C -> Oracle 真实调用链；不使用 Mock 结果替代事务、数据库时间、并发或中间件故障证据

## 1. 输入基线与适用优先级

### 1.1 已读取输入

| 输入 | 版本/日期 | SHA-256 | 用途 |
| --- | --- | --- | --- |
| `docs/requirements/cnaps-review-requirements.md` | v1.4 / 2026-07-15 | `0c56a8e6239275f017a738e6e1fe23539030d3e95226ecf8d4cbc59d69838000` | 本文最高优先级业务需求 |
| `docs/design/cnaps-review-design.md` | v1.0 / 2026-07-21 | `8c035e93ab367c44b74727dc04b7b83192ad92c4d7b8cf6a32c179a32531fbc2` | 执行级路由、状态、事务、并发和错误映射设计 |
| `docs/cnaps-frontend-api.md` | v0.5 | `1382ae83eb7da0d94540795481a93a5b93f06072f06538f48dcec4993f38d928` | 公共 HTTP、响应信封、列表记录及错误码契约 |
| `docs/standards/requirements-to-test-cases-spec.md` | v1.1 | `93985846f6746b1fdcfbed14e20a3c179a36379d7d48a4e1f3019a41e15aa4a6` | 手工用例格式、可测性和追踪门禁 |
| `docs/superpowers/specs/2026-07-10-server-context-and-editable-work-date-design.md` | 2026-07-10 | `d8596a4e591d374eb34cbcb65f4c0309d3a21cbdb00d8439c717c45a9d8435c1` | 服务端可信上下文及客户端同名值覆盖规则 |
| `docs/superpowers/specs/2026-07-13-post-voucher-list-api-design.md` | 2026-07-13 | `aedf0dd617613ed6fe097782d40f15ea78ad78f4b5a389a2ad53b8c5ca293810` | POST 列表路由、JSON Body、旧 GET 405 |
| `docs/superpowers/specs/2026-07-13-voucher-list-range-only-filter-design.md` | 2026-07-13 | `2f554c32895e44d33c25bffc2ddf6de603dc29f94fa472311c1aaeb9cc48a3a2` | 日期范围、严格日期、废弃 `workDate` 的明确处理 |
| `docs/superpowers/specs/2026-07-07-cnaps-single-table-poc-design.md` | 2026-07-07 | `3aa99eb41999118426484680c4c8d291fdde8476f48634247403b5a33eaaf54e` | 仅用于识别被新需求取代的旧设计及单表范围 |
| `docs/cnaps-api-database.md` | 2026-07-13 | `975107e1fb7f8c8b085e2b9fd499f041c79e2db48dff23f9b77ac6e276dd99c2` | 仅采用 `T_CNAPS_BILL_POC` 表名和既有列名，旧 HTTP 契约不作为预期 |

本文同时读取 `docs/design/cnaps-review-design.md` 执行级设计，并按“仓库约束 > v1.4 需求 > 现行设计 > v0.5 API > 旧资料”处理冲突。

### 1.2 已解决的文档冲突

| 旧资料内容 | 本文采用的结论 |
| --- | --- |
| 复核列表调用 `CNAPS5702Q` | 采用需求：复用 `CNAPS4609Q`，WebFE 强制 `STATUS=10_PENDING_REVIEW`；不得新增 `CNAPS5702Q` |
| 审核 Body 携带 `reviewComment` / `rejectReason` | 采用需求：正常请求无 Body，不实现审核意见或退回原因；非空 Body 如何处理仍为 `TBD-002` |
| 客户端可传公共业务请求头 | 采用需求和现行设计：`operatorNo`、`branchNo`、`requestId` 由 WebFE 提供并覆盖客户端同名值 |
| 经办人不得审核本人凭证、错误码 `3005` | 采用需求：本期不实现录入审核分离，不测试 `3005` |
| 响应含 `success`、`requestId`、`serverTime` | 采用 v0.5 API：顶层字段固定且仅为 `respCode`、`respMsg`、`data` |
| 旧分页结构 `content/page` | 采用 v0.5 API：`pageNo/pageSize/total/records` |

## 2. 测试目标、边界与风险

### 2.1 范围内

- `POST /api/cnaps/vouchers/review-list` 的分页、日期、流水号、机构范围和固定待审核状态。
- `POST /api/cnaps/vouchers/{billId}/review-pass` 与 `review-return` 的状态、版本、审计和响应摘要。
- 非待审核、重复审核、并发竞争、数据库故障、Tuxedo 超时与不可用的精确错误映射和回滚。
- 退回后通过现有修改接口重新进入待审核并清空审核人、审核时间。
- 服务映射、C 服务、Jolt 元数据、配置注册和禁止项的只读人工检查。

### 2.2 范围外

- 登录、权限、录入审核分离、审核意见、退回原因、批量、多级审核、撤销、通知、外部工作流和真实 CNAPS 外发。
- JSP、JavaScript、CSS、浏览器页面和任何自动化测试代码。
- 新表、新列、新框架、新依赖、部署和生产数据操作。

### 2.3 主要风险

| 风险 | 等级 | 测试策略 |
| --- | :---: | --- |
| 两个审核请求均提交或版本增加两次 | 高 | 双终端屏障并发，多轮独立数据，核对响应组合、最终版本和胜者审计 |
| 审核失败后出现部分字段提交 | 高 | 写操作前后全行快照；故障、冲突和重复请求均核对全行不变 |
| 客户端伪造机构、操作员或请求号 | 高 | 在 Header、Query 和可用 JSON 位置注入同名值，核对服务端配置、日志和数据库审计 |
| 列表泄露非待审核或其他机构凭证 | 高 | 同库准备跨状态、跨机构数据，核对 `total` 和每条 `records` |
| Oracle 时间被应用时间代替 | 高 | 使用数据库会话捕获 `T0_DB/T1_DB`，核对三个更新时间字段落入窗口 |
| 旧 `CNAPS5702Q` 或旧审核字段残留 | 中 | 对生产源、配置、脚本和元数据执行只读搜索并保留输出 |

## 3. 原子化验收要求

状态说明：`已确认` 可直接形成通过/失败判定；`部分 TBD` 表示规则有效，但失败响应或解析语义尚无唯一契约。

| 需求 ID | 原子化要求 | 来源 | 优先级 | 状态 |
| --- | --- | --- | :---: | :---: |
| `REV-API-001` | 三个审核相关入口均只以 POST 提供 | 需求 3 | P0 | 已确认 |
| `REV-API-002` | 三个入口的 GET 请求返回 HTTP 405 | 需求 3 | P1 | 部分 TBD：业务响应体未定义 |
| `REV-API-003` | 待审核列表接收查询 JSON 对象或 `{}` | 需求 3 | P1 | 部分 TBD：缺失/畸形 Body 未定义 |
| `REV-API-004` | 审核动作的 `billId` 仅从 URL 路径获取 | 需求 3.2 | P0 | 已确认 |
| `REV-API-005` | 正常审核动作不接收 Body、目标状态、意见或原因 | 需求 3.2 | P0 | 部分 TBD：非空 Body 是拒绝还是忽略未定义 |
| `REV-API-006` | 审核成功 `data` 恰含 `billId/status/checkerNo/checkerTime/lastAction/versionNo` | 需求 3.2 | P0 | 已确认 |
| `REV-API-007` | 所有 JSON 响应顶层恰含 `respCode/respMsg/data`；失败 `data=null` | API 1 | P0 | 已确认（不含未定义的 405 响应体） |
| `REV-API-008` | 列表 `data` 恰含 `pageNo/pageSize/total/records`，记录恰含九个 API 字段 | 需求 3.1、API 4.8 | P1 | 已确认 |
| `REV-LST-001` | 待审核列表固定查询 `10_PENDING_REVIEW`，客户端 `status` 不能改变该条件 | 需求 3.1 | P0 | 已确认 |
| `REV-LST-002` | 查询范围固定受 WebFE 配置 `branchNo` 限制 | 需求 3.1 | P0 | 已确认 |
| `REV-LST-003` | 两个日期均未传时不按工作日期筛选 | API 1、4.8 | P1 | 已确认 |
| `REV-LST-004` | `startWorkDate` 为严格 `yyyy-MM-dd` 的包含性下界，可单独传入 | 需求 3.1 | P1 | 已确认 |
| `REV-LST-005` | `endWorkDate` 为严格 `yyyy-MM-dd` 的包含性上界，可单独传入 | 需求 3.1 | P1 | 已确认 |
| `REV-LST-006` | 开始日期不得晚于结束日期，日期错误返回 HTTP 400 / `2002` | 需求 3.1、8 | P1 | 已确认 |
| `REV-LST-007` | `serialNo` 按字符串精确匹配 | 需求 3.1 | P1 | 已确认 |
| `REV-LST-008` | 省略分页时默认 `pageNo=1`、`pageSize=10` | 需求 3.1 | P1 | 已确认 |
| `REV-LST-009` | `pageNo` 必须为正整数 | 需求 3.1 | P1 | 部分 TBD：非法值错误映射未定义 |
| `REV-LST-010` | `pageSize` 必须为整数且范围为 1～100 | 需求 3.1 | P1 | 部分 TBD：非法值错误映射未定义 |
| `REV-LST-011` | 请求含 `workDate` 时不作为筛选条件；现行范围设计规定 HTTP 400 / `2002` 并给出废弃字段消息 | 需求 3.1、范围设计 | P1 | 已确认 |
| `REV-STA-001` | 待审核凭证通过后变为 `20_REVIEW_APPROVED`，`lastAction=REVIEW_PASS` | 需求 4 | P0 | 已确认 |
| `REV-STA-002` | 待审核凭证退回后变为 `30_REVIEW_REJECTED`，`lastAction=REVIEW_RETURN` | 需求 4 | P0 | 已确认 |
| `REV-STA-003` | 退回凭证通过现有修改接口变为 `10_PENDING_REVIEW`，`lastAction=UPDATE` | 需求 4 | P0 | 已确认 |
| `REV-STA-004` | 任何非 `10_PENDING_REVIEW` 状态执行任一审核动作均返回 HTTP 409 / `3004` | 需求 4、8 | P0 | 已确认 |
| `REV-STA-005` | 新生命周期不得写入退役状态 `00_DRAFT` | 需求 4 | P0 | 已确认 |
| `REV-DAT-001` | 审核成功的 `CHECKER_NO` 等于 WebFE `operatorNo` | 需求 6 | P0 | 已确认 |
| `REV-DAT-002` | `CHECKER_TIME` 来源为 Oracle `SYSTIMESTAMP` | 需求 6 | P0 | 已确认 |
| `REV-DAT-003` | `LAST_OPERATOR_NO` 等于 WebFE `operatorNo`，`LAST_REQUEST_ID` 等于 WebFE 本次 `requestId` | 需求 6 | P0 | 已确认 |
| `REV-DAT-004` | `LAST_ACTION_TIME`、`UPDATED_AT` 来源为 Oracle `SYSTIMESTAMP` | 需求 6 | P0 | 已确认 |
| `REV-DAT-005` | 审核成功 `VERSION_NO` 恰为原值加 1 | 需求 6 | P0 | 已确认 |
| `REV-DAT-006` | 审核成功后重新读取凭证并以提交后的值生成响应摘要 | 需求 7 | P0 | 已确认 |
| `REV-DAT-007` | 审核只改变需求列出的状态、审核、末次动作、更新时间和版本字段；其他字段及其他凭证不变 | 需求 6、7 | P0 | 已确认 |
| `REV-DAT-008` | 退回凭证修改后 `CHECKER_NO`、`CHECKER_TIME` 均清空 | 需求 9 | P0 | 已确认 |
| `REV-CTX-001` | `operatorNo` 由 WebFE 提供并覆盖客户端同名值 | 需求 6 | P0 | 已确认 |
| `REV-CTX-002` | `branchNo` 由 WebFE 提供并覆盖客户端同名值 | 需求 6 | P0 | 已确认 |
| `REV-CTX-003` | `requestId` 由 WebFE 提供并覆盖客户端同名值 | 需求 6 | P0 | 已确认 |
| `REV-CON-001` | 审核先读状态，再以 `BILL_ID + VERSION_NO` 乐观更新 | 需求 7 | P0 | 已确认 |
| `REV-CON-002` | 乐观更新 0 行时回滚并返回 HTTP 409 / `3004` | 需求 7 | P0 | 已确认 |
| `REV-CON-003` | 同一凭证并发通过和退回时恰有一个成功、一个冲突，版本总计只加 1 | 需求 7 | P0 | 已确认 |
| `REV-CON-004` | 重复或并发同动作不能产生第二次状态、版本或审计更新 | 需求 2、7、9 | P0 | 已确认 |
| `REV-ERR-001` | 缺少 `billId` 返回 HTTP 400 / `2001` | 需求 8 | P1 | 已确认 |
| `REV-ERR-002` | 凭证不存在返回 HTTP 404 / `3001` | 需求 8 | P0 | 已确认 |
| `REV-ERR-003` | 数据库错误回滚并返回 HTTP 500 / `4001` | 需求 7、8 | P0 | 已确认 |
| `REV-ERR-004` | Tuxedo 超时返回 HTTP 504 / `4002` | 需求 8 | P1 | 已确认 |
| `REV-ERR-005` | Tuxedo 不可用返回 HTTP 503 / `4003` | 需求 8 | P1 | 已确认 |
| `REV-SCP-001` | `review-list` 复用 `CNAPS4609Q`，不得新增 `CNAPS5702Q` | 需求 5 | P1 | 已确认 |
| `REV-SCP-002` | 新增且仅使用 `CNAPS5702A`、`CNAPS5702R` 两个审核服务 | 需求 5 | P1 | 已确认 |
| `REV-SCP-003` | A/R 两入口在同一 `cnaps_review.c` 中复用一个状态变更函数 | 需求 5 | P1 | 已确认 |
| `REV-SCP-004` | 不修改现有查询 C 服务和分页读取逻辑 | 需求 5 | P1 | 已确认 |
| `REV-SCP-005` | 不新增前端资源、表、列、框架或依赖，不实现范围外功能 | 需求 2、9 | P2 | 已确认 |
| `REV-SCP-006` | Java 路由、Tuxedo C、服务配置和 Jolt 元数据中的服务及字段保持一致 | 仓库约束、需求 5 | P1 | 已确认 |

## 4. 环境、上下文和数据准备

### 4.1 测试环境

| 项目 | 要求/执行记录 |
| --- | --- |
| HTTP Base URL | 默认 `http://localhost:8080/ruisui-bank-sim`；执行时记录实际 URL |
| WebFE | 候选 WAR 的提交、构建号和启动参数已记录 |
| Tuxedo/Jolt | 真实域可用；服务、元数据版本与候选构建一致 |
| Oracle | 专用测试 Schema；表 `T_CNAPS_BILL_POC`；禁止使用生产客户数据 |
| 服务端上下文 | 本文数据基线使用 `operatorNo=77210088`、`branchNo=772`；若环境采用其他值，执行前统一替换并留证 |
| 工具 | curl/Postman、Oracle SQL 客户端、两个独立终端、Tuxedo/Jolt/WebFE 日志只读权限 |
| 时间 | 应用与数据库时钟已同步；审计断言仍以 Oracle 会话的 `T0_DB/T1_DB` 为准 |
| 故障注入 | 仅在隔离环境由管理员执行，变更前后记录并恢复；不得修改生产逻辑或增加测试开关 |

### 4.2 通用业务字段模板

除用例另有说明，准备凭证时使用以下非敏感值：

```json
{
  "workDate": "2026-07-15",
  "businessType": "02102",
  "accountPart1": "404045",
  "accountPart2": "00772",
  "accountPart3": "000000000001",
  "accountName": "审核POC付款账户",
  "payerName": "审核POC付款人",
  "payerAddress": "上海市测试路1号",
  "payerBankName": "测试银行上海分行",
  "payeeAccountNo": "622200000000000001",
  "payeeName": "审核POC收款人",
  "payeeAddress": "北京市测试路2号",
  "priority": "NORM",
  "receiveBankNo": "102290000002",
  "receiveBankName": "测试接收行",
  "systemType": "CNAPS",
  "amount": "5600.00",
  "debitMode": "1",
  "feeAmount": "0.00",
  "feeChargeMode": "1",
  "sendMode": "0",
  "faxFlag": "0",
  "voucherNo": "<按数据别名填写>",
  "remark": "CNAPS-REVIEW-MANUAL-20260721"
}
```

### 4.3 数据包 `DP-LIST`

在独立 Schema 中加载以下精确数据；除 `L-D001` 外均不是逻辑删除状态，所有记录 `VERSION_NO=1`。流水号和数量必须在执行前用 SQL 核对。

| 数据别名 | `BRANCH_NO` | `STATUS` | `WORK_DATE` | `SERIAL_NO` | 数量 |
| --- | --- | --- | --- | --- | ---: |
| `L-P001..L-P040` | `772` | `10_PENDING_REVIEW` | `2026-07-01` | `0090001..0090040` | 40 |
| `L-P041..L-P080` | `772` | `10_PENDING_REVIEW` | `2026-07-15` | `0090041..0090080` | 40 |
| `L-P081..L-P101` | `772` | `10_PENDING_REVIEW` | `2026-07-31` | `0090081..0090101` | 21 |
| `L-P102` | `772` | `10_PENDING_REVIEW` | `2024-02-29` | `0090102` | 1 |
| `L-A001` | `772` | `20_REVIEW_APPROVED` | `2026-07-15` | `0090201` | 1 |
| `L-R001` | `772` | `30_REVIEW_REJECTED` | `2026-07-15` | `0090202` | 1 |
| `L-D001` | `772` | `40_DELETED` | `2026-07-15` | `0090203` | 1 |
| `L-X001..L-X002` | `999` | `10_PENDING_REVIEW` | `2026-07-15` | `0090301..0090302` | 2 |

因此在服务端机构 `772` 下，无日期筛选的待审核总数必须为 102；其他状态 3 条和其他机构 2 条均不得计入。每条列表记录的 `voucherNo` 固定为数据别名，便于核对集合且不依赖未规定的排序。

### 4.4 数据包 `DP-ACTION`

每个破坏性用例使用独立凭证。`billId` 可由环境数据装载器固定，也可通过录入接口生成后按 `voucherNo` 捕获；执行记录必须写入实际 `billId`，禁止临时改用其他凭证。

| 别名 | 初始状态 | 初始版本 | 特殊初值/用途 |
| --- | --- | ---: | --- |
| `A-PASS` | `10_PENDING_REVIEW` | 3 | 审核字段为空；通过成功 |
| `A-RETURN` | `10_PENDING_REVIEW` | 7 | 审核字段为空；退回成功 |
| `A-CTX` | `10_PENDING_REVIEW` | 4 | 可信上下文覆盖 |
| `A-REPEAT-P` | `10_PENDING_REVIEW` | 2 | 重复通过 |
| `A-REPEAT-R` | `10_PENDING_REVIEW` | 5 | 重复退回 |
| `A-APR` | `20_REVIEW_APPROVED` | 8 | 非法来源状态 |
| `A-REJ` | `30_REVIEW_REJECTED` | 6 | 非法来源状态 |
| `A-DEL` | `40_DELETED` | 9 | 非法来源状态 |
| `A-DRAFT` | `00_DRAFT` | 1 | 仅由隔离环境旧数据夹具准备；不得由业务 API 生成 |
| `A-FLOW` | `10_PENDING_REVIEW` | 1 | 审核字段为空；退回、修改、再次审核完整闭环 |
| `A-CON-PR-01..10` | `10_PENDING_REVIEW` | 3 | 10 轮通过/退回并发 |
| `A-CON-P-01..05` | `10_PENDING_REVIEW` | 3 | 5 轮双通过并发 |
| `A-CON-R-01..05` | `10_PENDING_REVIEW` | 3 | 5 轮双退回并发 |
| `A-DBERR` | `10_PENDING_REVIEW` | 11 | 数据库写失败回滚 |

所有待审核动作数据的 `REVIEW_COMMENT`、`REJECT_REASON`、删除审计字段和 `CHECKER_NO/CHECKER_TIME` 初始为 `NULL`。每条数据的业务字段、身份字段、创建审计和整行快照均在动作前导出。

### 4.5 通用 SQL 证据

执行环境可按权限调整显示格式，但不得省略以下列：

```sql
SELECT CAST(SYSTIMESTAMP AS TIMESTAMP) AS DB_TIME FROM DUAL;

SELECT BILL_ID, WORK_DATE, BRANCH_NO, OPERATOR_NO, SERIAL_NO,
       STATUS, CHECKER_NO, CHECKER_TIME, REVIEW_COMMENT, REJECT_REASON,
       DELETE_REASON, DELETE_OPERATOR_NO, DELETE_TIME,
       LAST_ACTION, LAST_OPERATOR_NO, LAST_REQUEST_ID, LAST_ACTION_TIME,
       CREATED_AT, UPDATED_AT, VERSION_NO,
       BUSINESS_TYPE, ACCOUNT_PART1, ACCOUNT_PART2, ACCOUNT_PART3,
       ACCOUNT_NAME, PAYER_NAME, PAYER_ADDRESS, PAYER_BANK_NAME,
       PAYEE_ACCOUNT_NO, PAYEE_NAME, PAYEE_ADDRESS, PRIORITY,
       RECEIVE_BANK_NO, RECEIVE_BANK_NAME, SYSTEM_TYPE, AMOUNT,
       DEBIT_MODE, FEE_AMOUNT, FEE_CHARGE_MODE, SEND_MODE, FAX_FLAG,
       VOUCHER_NO, REMARK
  FROM T_CNAPS_BILL_POC
 WHERE BILL_ID = :bill_id;
```

若候选 Schema 中某个旧可选列不存在，仅从查询中删除该列并在执行偏差中记录；状态、审核、最后动作、时间和版本列不得省略。

## 5. 通用断言

1. JSON 成功响应：HTTP 200；顶层键集合严格等于 `{respCode, respMsg, data}`；`respCode="0000"`；`respMsg` 为非空字符串（需求未规定具体文案）；`data` 非空。
2. JSON 失败响应：使用用例指定 HTTP 和业务码；顶层键集合严格等于 `{respCode, respMsg, data}`；`respMsg` 为非空字符串；`data=null`；响应不得包含栈、SQL、主机、账号或中间件凭据。
3. 列表成功：`data` 键集合严格等于 `{pageNo,pageSize,total,records}`；前三项为整数；`records` 为数组。每条记录键集合严格等于 `{billId,workDate,serialNo,voucherNo,payeeAccountNo,payeeName,amount,status,versionNo}`；日期格式为 `yyyy-MM-dd`，金额为字符串，版本为整数。
4. 审核成功：`data` 键集合严格等于 `{billId,status,checkerNo,checkerTime,lastAction,versionNo}`；`checkerTime` 为 API 规定的 `yyyy-MM-dd HH:mm:ss` 字符串，且与数据库值按秒一致。
5. 写成功：响应返回后从第二个 Oracle 会话读取已提交数据；允许变化列仅为用例明确列出的字段。所有未列字段、其他凭证行数和其他凭证快照不变。
6. 写失败：目标记录全行与动作前快照相同，`VERSION_NO` 不变；没有新增记录、没有其他记录变化、没有部分提交。
7. Oracle 时间：动作前后分别查询数据库得到 `T0_DB/T1_DB`；`CHECKER_TIME`、`LAST_ACTION_TIME`、`UPDATED_AT` 各自位于闭区间 `[T0_DB,T1_DB]`。需求未保证三者完全相等，不作相等断言。
8. `LAST_REQUEST_ID`：必须非空、不同于客户端伪造值，并能在 WebFE/Jolt/Tuxedo 同一请求日志中关联；需求未规定固定格式，不猜测正则。
9. 未规定列表排序；涉及集合的用例按 `voucherNo`/`serialNo` 比较成员，不以返回顺序判定。

## 6. 详细测试用例

### 6.1 路由与公共契约

#### `REV-TC-API-001`：空条件 POST 返回默认待审核分页

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-API-001` |
| 标题 | 空 JSON 条件查询仅返回当前机构待审核凭证并应用默认分页 |
| 需求 ID | `REV-API-001`、`REV-API-003`、`REV-API-007`、`REV-API-008`、`REV-LST-001`、`REV-LST-002`、`REV-LST-003`、`REV-LST-008` |
| 优先级 | P0 |
| 执行工具 / 状态 | Postman 或 curl、Oracle SQL、服务日志 / 未执行 |
| 前置条件 | 部署候选版本；WebFE `branchNo=772`；重新加载 `DP-LIST` 并确认 102 条本机构待审核、3 条本机构非待审核、2 条其他机构待审核 |
| 测试数据 | `POST /api/cnaps/vouchers/review-list`；`Content-Type: application/json; charset=UTF-8`；Body `{}` |
| 操作步骤 | 1. 导出 `DP-LIST` 全表快照及上述分类计数。<br>2. 发送请求并保存完整 HTTP 响应。<br>3. 按响应中的每个 `billId` 查询数据库记录。<br>4. 再次导出全表快照。 |
| 预期结果 | 1. 基线计数分别为 102、3、2。<br>2. HTTP 200、`respCode="0000"`，满足通用成功信封；`data.pageNo=1`、`pageSize=10`、`total=102`、`records` 长度为 10。<br>3. 每条记录满足九字段契约；数据库中的 `BRANCH_NO` 均为 `772`、`STATUS` 均为 `10_PENDING_REVIEW`；不出现 `L-A001/L-R001/L-D001/L-X001/L-X002`。<br>4. 请求前后全表快照一致，查询不写库。 |
| 清理 | 无写操作；保留 `DP-LIST` 供后续列表用例，若有偏差则重载数据包 |
| 证据 | 完整请求/响应、分类 SQL、10 条记录回查、前后快照、同一请求服务日志 |

#### `REV-TC-API-002`：三个 GET 路径均返回 405

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-API-002` |
| 标题 | 对三个仅支持 POST 的路径发送 GET 均被方法级拒绝且不调用 Tuxedo |
| 需求 ID | `REV-API-001`、`REV-API-002` |
| 优先级 | P1 |
| 执行工具 / 状态 | curl `--path-as-is`、Oracle SQL、WebFE/Jolt 日志 / 阻塞：`TBD-001` |
| 前置条件 | `DP-LIST` 已加载；`A-PASS` 存在且已保存全行快照；可查询 WebFE/Jolt 调用日志 |
| 测试数据 | 依次 GET：`/api/cnaps/vouchers/review-list`、`/api/cnaps/vouchers/{A-PASS.billId}/review-pass`、`/api/cnaps/vouchers/{A-PASS.billId}/review-return` |
| 操作步骤 | 1. 记录三个请求的开始时间和 `A-PASS` 快照。<br>2. 逐一发送 GET 并保存状态、响应头和原始 Body。<br>3. 在时间窗口内查询 Tuxedo 调用日志。<br>4. 回查 `A-PASS` 和 `DP-LIST`。 |
| 预期结果 | 三个请求的 HTTP 状态均为 405；按 POST 列表设计，请求不进入 Tuxedo。`respCode`、`respMsg` 和 `data` 的 405 Body 契约为 TBD，关闭前不得把空 Body、HTML 或某业务码判为通过。数据库所有记录不变。 |
| 清理 | 无 |
| 证据 | 三份原始 HTTP 响应、WebFE 访问日志、无 Tuxedo 调用的日志查询、前后 SQL 快照 |

#### `REV-TC-API-003`：审核路径缺少 `billId`

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-API-003` |
| 标题 | 审核通过和退回路径缺少 billId 均返回必填错误且不写库 |
| 需求 ID | `REV-API-004`、`REV-ERR-001` |
| 优先级 | P1 |
| 执行工具 / 状态 | 能保留双斜杠的原始 HTTP 客户端或 curl `--path-as-is`、Oracle SQL / 未执行 |
| 前置条件 | 客户端、代理和容器测试入口不会在发送前合并 `//`；已保存测试 Schema 全表快照 |
| 测试数据 | 无 Body；分别 POST `/api/cnaps/vouchers/review-pass`、`/api/cnaps/vouchers/review-return`，以及保留双斜杠的 `/api/cnaps/vouchers//review-pass`、`/api/cnaps/vouchers//review-return` |
| 操作步骤 | 1. 记录四条原始请求行，确认无编号路径和双斜杠路径均按原样发送。<br>2. 逐一发送四个请求。<br>3. 保存完整响应并比较全表快照。 |
| 预期结果 | 四个请求均为 HTTP 400、`respCode="2001"`、`data=null`，满足通用失败信封；错误语义指向缺少 `billId`，不得被路由为其他凭证或详情操作；数据库全表不变。若基础设施强制归一化路径，记录环境阻塞而非产品失败。 |
| 清理 | 无 |
| 证据 | 原始 HTTP 请求行、两份响应、前后全表快照、WebFE 路由日志 |

#### `REV-TC-API-004`：审核动作携带非空 Body

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-API-004` |
| 标题 | 审核动作携带目标状态、意见或原因时不得在规则未确认前假定拒绝或忽略 |
| 需求 ID | `REV-API-005` |
| 优先级 | P0 |
| 执行工具 / 状态 | Postman、Oracle SQL / 阻塞：`TBD-002` |
| 前置条件 | 为表中每个变体准备一条独立待审核凭证，保存全行快照和初始版本 |
| 测试数据 | 通过接口分别发送 `{"targetStatus":"30_REVIEW_REJECTED"}`、`{"reviewComment":"should-not-be-used"}`；退回接口发送 `{"targetStatus":"20_REVIEW_APPROVED"}`、`{"rejectReason":"should-not-be-used"}`；均为 JSON Body |
| 操作步骤 | 1. 对每个变体记录独立 `billId` 和快照。<br>2. 逐一发送请求并保存响应。<br>3. 回查目标行和其他行。 |
| 预期结果 | 需求只规定正常请求无 Body，未规定非空 Body 是 HTTP 4xx 拒绝还是忽略后执行；因此 HTTP、`respCode`、响应字段和目标行是否发生合法审核均为 TBD。无论最终选择哪种规则，客户端提供的 `targetStatus`、意见和原因不得直接决定持久化值；不得出现需求状态之外的状态或部分字段更新。 |
| 清理 | TBD 关闭后按选定语义重置或废弃各独立凭证 |
| 证据 | 每个变体的请求/响应、前后全行快照、服务日志 |

### 6.2 列表筛选、边界和数据校验

#### `REV-TC-LST-001`：单边日期范围

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-001` |
| 标题 | 只传开始或结束日期时按包含性单边界筛选 |
| 需求 ID | `REV-LST-004`、`REV-LST-005`、`REV-API-008` |
| 优先级 | P1 |
| 执行工具 / 状态 | Postman、Oracle SQL / 未执行 |
| 前置条件 | 重载 `DP-LIST`；服务端机构为 `772` |
| 测试数据 | A：`{"startWorkDate":"2026-07-15","pageNo":1,"pageSize":100}`；B：`{"endWorkDate":"2026-07-01","pageNo":1,"pageSize":100}` |
| 操作步骤 | 1. 发送 A 并保存响应。<br>2. 发送 B 并保存响应。<br>3. 按 `billId` 回查所有返回记录。 |
| 预期结果 | A：HTTP 200 / `0000`，`total=61`、`records` 长度 61，每条 `WORK_DATE>=2026-07-15`，包含 7 月 15 日的 40 条。B：HTTP 200 / `0000`，`total=41`、`records` 长度 41，每条 `WORK_DATE<=2026-07-01`，包含 7 月 1 日的 40 条及 `L-P102`。两次 `pageNo=1/pageSize=100`，所有记录均为机构 `772` 的待审核状态，数据库不变。 |
| 清理 | 无 |
| 证据 | 两份响应、成员集合与日期 SQL、前后计数 |

#### `REV-TC-LST-002`：双边日期和闰日包含性

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-002` |
| 标题 | 相同上下界、跨日范围和合法闰日均包含边界日期 |
| 需求 ID | `REV-LST-004`、`REV-LST-005`、`REV-LST-006` |
| 优先级 | P1 |
| 执行工具 / 状态 | Postman、Oracle SQL / 未执行 |
| 前置条件 | 重载 `DP-LIST` |
| 测试数据 | A：`2026-07-15..2026-07-15`；B：`2026-07-01..2026-07-15`；C：`2024-02-29..2024-02-29`；每个请求 `pageSize=100` |
| 操作步骤 | 1. 分别发送 A、B、C。<br>2. 保存响应并按成员集合与数据库日期比较。 |
| 预期结果 | 三个请求均 HTTP 200 / `0000` 且满足列表契约。A `total=40`、40 条均为 7 月 15 日；B `total=80`，恰为 `L-P001..L-P080`；C `total=1`，唯一记录为 `L-P102` 且 `workDate="2024-02-29"`。数据库不变。 |
| 清理 | 无 |
| 证据 | 三份响应、成员集合 SQL、全表前后快照 |

#### `REV-TC-LST-003`：空白日期按未传处理

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-003` |
| 标题 | 空字符串和纯空格日期边界按范围设计移除并视为未传 |
| 需求 ID | `REV-LST-003`、`REV-LST-004`、`REV-LST-005` |
| 优先级 | P2 |
| 执行工具 / 状态 | Postman、Oracle SQL / 未执行 |
| 前置条件 | 重载 `DP-LIST` |
| 测试数据 | `{"startWorkDate":"","endWorkDate":"   ","pageNo":1,"pageSize":100}` |
| 操作步骤 | 1. 发送请求。<br>2. 保存响应并核对成员。 |
| 预期结果 | HTTP 200 / `0000`；两个空白边界均不参与筛选；`pageNo=1`、`pageSize=100`、`total=102`、`records` 长度 100；所有记录仍为机构 `772` 的待审核凭证；数据库不变。 |
| 清理 | 无 |
| 证据 | 请求/响应、计数 SQL、成员回查 |

#### `REV-TC-LST-004`：日期格式和日历值非法

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-004` |
| 标题 | 非严格格式、不存在日期和日期时间值均返回日期格式错误 |
| 需求 ID | `REV-LST-004`、`REV-LST-005`、`REV-LST-006` |
| 优先级 | P1 |
| 执行工具 / 状态 | Postman、WebFE/Jolt 日志、Oracle SQL / 未执行 |
| 前置条件 | 重载 `DP-LIST` 并保存快照 |
| 测试数据 | 分别请求：`startWorkDate="2026-7-01"`、`endWorkDate="2026/07/01"`、`startWorkDate="2026-02-30"`、`endWorkDate="2026-07-01T00:00:00"`；其他字段省略 |
| 操作步骤 | 1. 为四个变体逐一发送请求。<br>2. 保存每份响应。<br>3. 查询相应窗口的服务调用日志并比较数据库快照。 |
| 预期结果 | 每个变体均 HTTP 400、`respCode="2002"`、`data=null`，满足失败信封；错误语义指向日期格式/日历值；WebFE 校验失败后不调用 Tuxedo；数据库快照不变。 |
| 清理 | 无 |
| 证据 | 四份请求/响应、无下游调用日志、前后快照 |

#### `REV-TC-LST-005`：开始日期晚于结束日期

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-005` |
| 标题 | 反向日期范围在 WebFE 被拒绝且不扩大查询 |
| 需求 ID | `REV-LST-006` |
| 优先级 | P1 |
| 执行工具 / 状态 | Postman、WebFE/Jolt 日志、Oracle SQL / 未执行 |
| 前置条件 | 重载 `DP-LIST` |
| 测试数据 | `{"startWorkDate":"2026-07-31","endWorkDate":"2026-07-01"}` |
| 操作步骤 | 1. 发送请求并保存响应。<br>2. 查询 Tuxedo 调用日志和数据库快照。 |
| 预期结果 | HTTP 400、`respCode="2002"`、`data=null`；错误语义表示开始日期晚于结束日期；不调用 Tuxedo；不返回任何列表数据；数据库不变。 |
| 清理 | 无 |
| 证据 | 请求/响应、无下游调用日志、SQL 快照 |

#### `REV-TC-LST-006`：废弃 `workDate` 字段

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-006` |
| 标题 | 请求只要出现 workDate 键即明确拒绝而不静默扩大查询 |
| 需求 ID | `REV-LST-011` |
| 优先级 | P1 |
| 执行工具 / 状态 | Postman、WebFE/Jolt 日志 / 未执行 |
| 前置条件 | 重载 `DP-LIST` |
| 测试数据 | 依次发送 `{"workDate":"2026-07-15"}`、`{"workDate":null}`、`{"workDate":""}`、`{"workDate":"   "}` |
| 操作步骤 | 1. 逐一发送四个请求。<br>2. 保存响应并检查下游调用日志。 |
| 预期结果 | 每个请求均 HTTP 400、`respCode="2002"`、`data=null`；`respMsg` 精确为 `列表查询不支持 workDate，请使用 startWorkDate/endWorkDate`；不调用 Tuxedo，不返回扩大后的列表，数据库不变。 |
| 清理 | 无 |
| 证据 | 四份请求/响应、WebFE 校验日志、无 Tuxedo 调用证据 |

#### `REV-TC-LST-007`：流水号精确匹配

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-007` |
| 标题 | 完整流水号命中唯一记录而前缀不作模糊匹配 |
| 需求 ID | `REV-LST-007` |
| 优先级 | P1 |
| 执行工具 / 状态 | Postman、Oracle SQL / 未执行 |
| 前置条件 | 重载 `DP-LIST`；确认 `L-P041.SERIAL_NO=0090041`，且不存在带空格值 |
| 测试数据 | A：`{"serialNo":"0090041"}`；B：`{"serialNo":"009004"}` |
| 操作步骤 | 1. 逐一发送 A、B。<br>2. 保存响应并按 `billId` 回查。 |
| 预期结果 | 两个请求均 HTTP 200 / `0000`。A `total=1` 且唯一记录为 `L-P041`；B `total=0`、`records=[]`。每个响应使用默认 `pageNo=1/pageSize=10`；数据库不变。空白处理未定义，另见 `REV-TC-LST-013`。 |
| 清理 | 无 |
| 证据 | 两份响应、流水号 SQL、前后快照 |

#### `REV-TC-LST-008`：日期与流水号组合取交集

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-008` |
| 标题 | 日期范围和流水号同时传入时按交集过滤 |
| 需求 ID | `REV-LST-004`、`REV-LST-005`、`REV-LST-007` |
| 优先级 | P1 |
| 执行工具 / 状态 | Postman、Oracle SQL / 未执行 |
| 前置条件 | 重载 `DP-LIST` |
| 测试数据 | A：7 月 15 日范围 + `serialNo=0090041`；B：7 月 1 日范围 + 同一流水号 |
| 操作步骤 | 1. 发送 A、B。<br>2. 保存响应并回查成员。 |
| 预期结果 | A HTTP 200 / `0000`、`total=1`，唯一记录为 `L-P041`；B HTTP 200 / `0000`、`total=0`、`records=[]`；不得把不同条件按并集处理；数据库不变。 |
| 清理 | 无 |
| 证据 | 两份响应、交集 SQL、成员回查 |

#### `REV-TC-LST-009`：分页最小值、最大值和末页外

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-009` |
| 标题 | pageSize 边界 1 和 100 可用且超过末页返回空 records |
| 需求 ID | `REV-LST-009`、`REV-LST-010`、`REV-API-008` |
| 优先级 | P1 |
| 执行工具 / 状态 | Postman、Oracle SQL / 未执行 |
| 前置条件 | 重载 `DP-LIST`，本机构待审核总数为 102 |
| 测试数据 | A：`pageNo=1,pageSize=1`；B：`pageNo=2,pageSize=100`；C：`pageNo=12,pageSize=10` |
| 操作步骤 | 1. 逐一发送 A、B、C。<br>2. 保存响应并核对成员不重复、不越界。 |
| 预期结果 | 三个请求均 HTTP 200 / `0000` 且 `total=102`。A 回显 `1/1`、`records` 长度 1；B 回显 `2/100`、`records` 长度 2；C 回显 `12/10`、`records=[]`。所有返回成员均为机构 `772` 待审核数据，数据库不变。 |
| 清理 | 无 |
| 证据 | 三份响应、总数 SQL、成员集合比较 |

#### `REV-TC-LST-010`：非法分页参数

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-010` |
| 标题 | pageNo/pageSize 的零值、负数、越界、非整数和错误类型需明确错误契约 |
| 需求 ID | `REV-LST-009`、`REV-LST-010` |
| 优先级 | P1 |
| 执行工具 / 状态 | Postman、服务日志 / 阻塞：`TBD-003` |
| 前置条件 | 重载 `DP-LIST` |
| 测试数据 | `pageNo`: `0`、`-1`、`1.5`、`"1"`、`null`；`pageSize`: `0`、`-1`、`101`、`1.5`、`"10"`、`null`；每次只改变一个字段 |
| 操作步骤 | 1. 对每个变体发送独立请求。<br>2. 保存原始 HTTP 响应和下游调用日志。 |
| 预期结果 | 已确认规则要求这些值不能作为有效分页执行；但 HTTP 状态、业务码、失败 `data` 以及 `null` 是否回退默认值未定义，均为 TBD。关闭前不得统一猜测为 `2002`，也不得把截断、钳制或默认化判为通过。查询不得写库。 |
| 清理 | 无 |
| 证据 | 每个变体的请求/响应、服务日志、TBD 结论链接 |

#### `REV-TC-LST-011`：状态、机构和可信上下文覆盖

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-011` |
| 标题 | 客户端伪造状态和上下文仍只得到服务端机构的待审核凭证 |
| 需求 ID | `REV-LST-001`、`REV-LST-002`、`REV-CTX-001`、`REV-CTX-002`、`REV-CTX-003` |
| 优先级 | P0 |
| 执行工具 / 状态 | Postman、Oracle SQL、WebFE/Jolt/Tuxedo 日志 / 未执行 |
| 前置条件 | 重载 `DP-LIST`；WebFE 配置 `operatorNo=77210088`、`branchNo=772` |
| 测试数据 | Header、Query 和 JSON 中均注入 `operatorNo=CLIENT_OP`、`branchNo=999`、`requestId=CLIENT_REQ`；JSON 另含 `status=20_REVIEW_APPROVED`、`pageSize=100`；分别查 `pageNo=1/2` |
| 操作步骤 | 1. 记录服务端配置证据。<br>2. 发送两页请求并保存响应。<br>3. 汇总两页 102 个成员，逐条回查机构和状态。<br>4. 在日志中定位 WebFE 生成的请求号及内部上下文。 |
| 预期结果 | 两次均 HTTP 200 / `0000`；总数均 102，第一页 100 条、第二页 2 条；102 条均为 `BRANCH_NO=772`、`STATUS=10_PENDING_REVIEW`，不返回机构 999 或已通过记录。客户端三个上下文值不进入内部可信字段，日志请求号不等于 `CLIENT_REQ`；响应仍只有三字段顶层信封；数据库不变。 |
| 清理 | 无 |
| 证据 | 配置快照、两份请求/响应、102 条成员 SQL、三层日志关联 |

#### `REV-TC-LST-012`：列表 Body 解析异常

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-012` |
| 标题 | 缺失、畸形或非对象 JSON Body 的失败契约需先确认 |
| 需求 ID | `REV-API-003` |
| 优先级 | P1 |
| 执行工具 / 状态 | 原始 HTTP 客户端、服务日志 / 阻塞：`TBD-004` |
| 前置条件 | 重载 `DP-LIST` |
| 测试数据 | 无 Body、空白 Body、`null`、`[]`、`{"pageNo":1`（缺右括号）、正确 JSON 但 `Content-Type:text/plain`；每次独立请求 |
| 操作步骤 | 1. 逐一发送六个变体，保留原始请求字节。<br>2. 保存状态、响应头、Body 和服务日志。 |
| 预期结果 | 需求仅明确“查询条件或 `{}`”，未定义上述解析失败的 HTTP、业务码和响应字段；全部为 TBD。任何变体均不得导致数据库变化；若请求被接受，也必须有经批准的默认化规则后才能判定。 |
| 清理 | 无 |
| 证据 | 六份原始请求/响应、服务日志、数据库前后计数、TBD 结论链接 |

#### `REV-TC-LST-013`：列表可选字段的 null、类型和未知键

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-LST-013` |
| 标题 | 日期、流水号的 null/错误类型及未知 JSON 键需要统一解析规则 |
| 需求 ID | `REV-API-003`、`REV-LST-004`、`REV-LST-005`、`REV-LST-007` |
| 优先级 | P1 |
| 执行工具 / 状态 | Postman、服务日志 / 阻塞：`TBD-006` |
| 前置条件 | 重载 `DP-LIST` |
| 测试数据 | 单变量变体：`startWorkDate:null`、`startWorkDate:20260701`、`endWorkDate:true`、`serialNo:null`、`serialNo:""`、`serialNo:"0090041 "`、`serialNo:900041`、`unknownField:"x"` |
| 操作步骤 | 1. 每次仅携带一个变体字段并发送请求。<br>2. 保存原始响应和 Tuxedo 调用日志。 |
| 预期结果 | 字段声明了类型和筛选语义，但未定义 `null`、空串、JSON 类型转换和未知键的拒绝/忽略规则，也未定义对应 HTTP 与业务码；全部为 TBD。关闭前不得把 null 当省略、把数字转字符串或把未知键静默忽略当作既定预期。请求不得写库。 |
| 清理 | 无 |
| 证据 | 八份请求/响应、服务日志、TBD 结论链接 |

### 6.3 审核状态、响应、审计和重复操作

#### `REV-TC-ACT-001`：待审核凭证审核通过

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ACT-001` |
| 标题 | 待审核凭证无 Body 审核通过后提交准确状态、审计和版本摘要 |
| 需求 ID | `REV-API-004`、`REV-API-005`、`REV-API-006`、`REV-API-007`、`REV-STA-001`、`REV-DAT-001`、`REV-DAT-002`、`REV-DAT-003`、`REV-DAT-004`、`REV-DAT-005`、`REV-DAT-006`、`REV-DAT-007` |
| 优先级 | P0 |
| 执行工具 / 状态 | Postman 或 curl、两个 Oracle 会话、三层服务日志 / 未执行 |
| 前置条件 | 重载 `A-PASS`：`STATUS=10_PENDING_REVIEW`、`VERSION_NO=3`、审核字段为空；WebFE 上下文为 `77210088/772`；已保存目标行及其他行动数据快照 |
| 测试数据 | `POST /api/cnaps/vouchers/{A-PASS.billId}/review-pass`；请求无 Body（`Content-Length: 0`） |
| 操作步骤 | 1. 查询并保存目标全行与其他记录快照。<br>2. 从 Oracle 查询并记录 `T0_DB`。<br>3. 发送无 Body 请求，保存原始响应。<br>4. 从 Oracle 查询并记录 `T1_DB`。<br>5. 使用第二数据库会话回查目标行。<br>6. GET 现有详情接口并关联本次 WebFE/Jolt/Tuxedo 日志。 |
| 预期结果 | HTTP 200、`respCode="0000"`，满足成功信封。审核 `data` 恰有六键：`billId=A-PASS.billId`、`status="20_REVIEW_APPROVED"`、`checkerNo="77210088"`、`checkerTime` 符合格式且与数据库按秒一致、`lastAction="REVIEW_PASS"`、`versionNo=4`。数据库恰更新：`STATUS=20_REVIEW_APPROVED`、`CHECKER_NO=77210088`、`CHECKER_TIME/LAST_ACTION_TIME/UPDATED_AT` 位于 `[T0_DB,T1_DB]`、`LAST_ACTION=REVIEW_PASS`、`LAST_OPERATOR_NO=77210088`、`LAST_REQUEST_ID` 等于日志中 WebFE 本次请求号、`VERSION_NO=4`。详情 HTTP 200 / `0000` 且完整凭证反映同一提交值。`REVIEW_COMMENT/REJECT_REASON` 仍为 NULL；业务、身份、创建和删除字段不变；其他记录不变。 |
| 清理 | `A-PASS` 不复用；按数据包重载机制恢复，不对已审核记录做逆向业务操作 |
| 证据 | 请求/响应、`T0_DB/T1_DB`、前后全行 SQL、详情响应、三层日志、其他记录快照 |

#### `REV-TC-ACT-002`：待审核凭证审核退回

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ACT-002` |
| 标题 | 待审核凭证无 Body 退回后提交准确状态、审计和版本摘要且不写退回原因 |
| 需求 ID | `REV-API-004`、`REV-API-005`、`REV-API-006`、`REV-API-007`、`REV-STA-002`、`REV-DAT-001`、`REV-DAT-002`、`REV-DAT-003`、`REV-DAT-004`、`REV-DAT-005`、`REV-DAT-006`、`REV-DAT-007` |
| 优先级 | P0 |
| 执行工具 / 状态 | Postman 或 curl、两个 Oracle 会话、三层服务日志 / 未执行 |
| 前置条件 | 重载 `A-RETURN`：待审核、版本 7、审核字段及 `REJECT_REASON` 为空；服务端上下文为 `77210088/772` |
| 测试数据 | `POST /api/cnaps/vouchers/{A-RETURN.billId}/review-return`；无 Body |
| 操作步骤 | 与 `REV-TC-ACT-001` 相同，但调用退回接口并回查 `A-RETURN`。 |
| 预期结果 | HTTP 200 / `0000`；六键 `data` 中 `billId=A-RETURN.billId`、`status="30_REVIEW_REJECTED"`、`checkerNo="77210088"`、`checkerTime` 与数据库按秒一致并位于数据库时间窗、`lastAction="REVIEW_RETURN"`、`versionNo=8`。数据库的状态、审核人/时间、最后操作员/请求号/动作时间、更新时间和版本恰按需求更新；`LAST_ACTION=REVIEW_RETURN`。`REJECT_REASON/REVIEW_COMMENT` 仍为 NULL，证明无 Body 不生成原因或意见；其他字段和其他记录不变。详情接口显示相同提交值。 |
| 清理 | `A-RETURN` 不复用；重载数据包恢复 |
| 证据 | 请求/响应、数据库时间窗、前后 SQL、详情响应、三层日志、其他记录快照 |

#### `REV-TC-ACT-003`：不存在的凭证

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ACT-003` |
| 标题 | 对不存在 billId 执行通过或退回均返回不存在且无数据变化 |
| 需求 ID | `REV-ERR-002`、`REV-API-007` |
| 优先级 | P0 |
| 执行工具 / 状态 | Postman、Oracle SQL、服务日志 / 未执行 |
| 前置条件 | SQL 确认 `B209912317729999999` 在 `T_CNAPS_BILL_POC` 中计数为 0；保存全表快照 |
| 测试数据 | 无 Body；对该 ID 分别 POST `review-pass` 和 `review-return` |
| 操作步骤 | 1. 查询不存在计数并保存全表快照。<br>2. 依次调用两个接口。<br>3. 保存响应并再次比较全表。 |
| 预期结果 | 两个请求均 HTTP 404、`respCode="3001"`、`data=null` 且满足失败信封；`respMsg` 表示凭证不存在；不插入该 ID，不改变任何现有记录，不增加版本或审计。 |
| 清理 | 无 |
| 证据 | 不存在 SQL、两份响应、前后全表快照、服务日志 |

#### `REV-TC-ACT-004`：全部非待审核来源状态

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ACT-004` |
| 标题 | 已通过、已退回、已删除和旧草稿状态执行两种审核动作均冲突 |
| 需求 ID | `REV-STA-004`、`REV-STA-005`、`REV-CON-002`、`REV-DAT-007` |
| 优先级 | P0 |
| 执行工具 / 状态 | Postman、Oracle SQL / 未执行 |
| 前置条件 | 重载 `A-APR/A-REJ/A-DEL/A-DRAFT` 并保存每条全行快照；旧草稿仅为测试夹具，未通过业务 API 创建 |
| 测试数据 | 对四条记录各发送一次无 Body `review-pass` 和一次无 Body `review-return`，共 8 个请求 |
| 操作步骤 | 1. 确认四条初始状态和版本。<br>2. 每个请求前保存目标快照，发送请求后立即回查。<br>3. 汇总 8 份响应。 |
| 预期结果 | 8 个请求均 HTTP 409、`respCode="3004"`、`data=null`，满足失败信封；不得返回旧的 `3003` 或 `3005`。每个请求后目标全行与请求前完全一致，版本、审核和时间字段不变；其他记录不变。`A-DRAFT` 仍仅是既有夹具，没有任何新记录被写为 `00_DRAFT`。 |
| 清理 | 删除或恢复隔离环境的 `A-DRAFT` 测试夹具；其他记录按数据包重载 |
| 证据 | 8 份请求/响应、每次前后 SQL、旧草稿夹具来源记录 |

#### `REV-TC-ACT-005`：重复审核通过

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ACT-005` |
| 标题 | 同一凭证连续两次审核通过仅第一次提交 |
| 需求 ID | `REV-STA-001`、`REV-STA-004`、`REV-CON-004`、`REV-DAT-005`、`REV-DAT-007` |
| 优先级 | P0 |
| 执行工具 / 状态 | Postman、Oracle SQL / 未执行 |
| 前置条件 | 重载 `A-REPEAT-P`：待审核、版本 2；保存全行快照 |
| 测试数据 | 连续两次无 Body POST `review-pass`，第二次仅在第一次响应及提交回查完成后发送 |
| 操作步骤 | 1. 发送第一次请求并保存响应、数据库提交快照 `S1`。<br>2. 发送完全相同的第二次请求并保存响应、快照 `S2`。 |
| 预期结果 | 第一次 HTTP 200 / `0000`，状态已通过、`lastAction=REVIEW_PASS`、`versionNo=3`。第二次 HTTP 409 / `3004`、`data=null`。`S2` 与 `S1` 全行相同：最终版本仍为 3，审核时间、最后请求号、最后动作时间和更新时间均未被第二次请求覆盖；其他记录不变。 |
| 清理 | 不复用该凭证；重载数据包恢复 |
| 证据 | 两份响应、初始/S1/S2 SQL、两次请求日志 |

#### `REV-TC-ACT-006`：重复审核退回

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ACT-006` |
| 标题 | 同一凭证连续两次审核退回仅第一次提交 |
| 需求 ID | `REV-STA-002`、`REV-STA-004`、`REV-CON-004`、`REV-DAT-005`、`REV-DAT-007` |
| 优先级 | P0 |
| 执行工具 / 状态 | Postman、Oracle SQL / 未执行 |
| 前置条件 | 重载 `A-REPEAT-R`：待审核、版本 5；保存全行快照 |
| 测试数据 | 连续两次无 Body POST `review-return` |
| 操作步骤 | 同 `REV-TC-ACT-005`，保存第一次提交快照 `S1` 和第二次后快照 `S2`。 |
| 预期结果 | 第一次 HTTP 200 / `0000`，状态已退回、`lastAction=REVIEW_RETURN`、`versionNo=6`；第二次 HTTP 409 / `3004`、`data=null`。`S2=S1`，最终版本仍为 6，第一次审核与审计字段不被覆盖；`REJECT_REASON` 仍为 NULL；其他记录不变。 |
| 清理 | 不复用该凭证；重载数据包恢复 |
| 证据 | 两份响应、初始/S1/S2 SQL、两次请求日志 |

#### `REV-TC-ACT-007`：退回、修改、再次审核闭环

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ACT-007` |
| 标题 | 待审核凭证退回后修改重回待审核并清空审核字段，随后可再次通过 |
| 需求 ID | `REV-STA-002`、`REV-STA-003`、`REV-DAT-005`、`REV-DAT-008`、`REV-STA-005` |
| 优先级 | P0 |
| 执行工具 / 状态 | Postman、Oracle SQL、服务日志 / 部分阻塞：`TBD-005` 仅影响 PUT `data` 字段集合判定 |
| 前置条件 | 重载 `A-FLOW`：待审核、版本 1、审核字段为空；保存身份、业务和审计快照 |
| 测试数据 | 1. 无 Body POST `review-return`。2. PUT `/api/cnaps/vouchers/{billId}`，Body `{"remark":"A-FLOW-UPDATED"}`。3. review-list 用该 `serialNo` 查询。4. 无 Body POST `review-pass` |
| 操作步骤 | 1. 执行退回并回查 `S1`。<br>2. 执行 PUT 修改并回查 `S2`。<br>3. 用 review-list 精确查询该流水号。<br>4. 执行再次审核通过并回查 `S3`。 |
| 预期结果 | 步骤 1：HTTP 200 / `0000`，`S1.STATUS=30_REVIEW_REJECTED`、`LAST_ACTION=REVIEW_RETURN`、`VERSION_NO=2`、审核人和时间非空。步骤 2：HTTP 200 / `0000` 且三字段顶层信封；PUT `data` 的精确键集合为 TBD；数据库 `S2.STATUS=10_PENDING_REVIEW`、`LAST_ACTION=UPDATE`、`VERSION_NO=3`、`CHECKER_NO=NULL`、`CHECKER_TIME=NULL`、`REMARK=A-FLOW-UPDATED`，`BILL_ID/SERIAL_NO/WORK_DATE/BRANCH_NO/OPERATOR_NO/CREATED_AT` 不变，除修改与既有更新审计字段外其他业务字段不变。步骤 3：HTTP 200 / `0000`、`total=1`，记录状态待审核且版本 3。步骤 4：HTTP 200 / `0000`，六键摘要为已通过、`REVIEW_PASS`、版本 4；`S3` 审计按本次通过更新。整个流程不出现 `00_DRAFT`，其他凭证不变。 |
| 清理 | `A-FLOW` 不复用；重载数据包恢复 |
| 证据 | 四次请求/响应、S0/S1/S2/S3 全行 SQL、列表成员、三层日志 |

#### `REV-TC-ACT-008`：URL billId 和可信上下文优先

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ACT-008` |
| 标题 | URL 中的 billId 是唯一目标且客户端伪造上下文不进入审核审计 |
| 需求 ID | `REV-API-004`、`REV-CTX-001`、`REV-CTX-002`、`REV-CTX-003`、`REV-DAT-001`、`REV-DAT-003`、`REV-DAT-007` |
| 优先级 | P0 |
| 执行工具 / 状态 | Postman、Oracle SQL、三层日志 / 未执行 |
| 前置条件 | 重载 `A-CTX`（待审核、版本 4）和 `A-RETURN`；WebFE 配置 `77210088/772`；保存两条全行快照 |
| 测试数据 | URL 路径使用 `A-CTX.billId`，Query 另传 `billId={A-RETURN.billId}&operatorNo=CLIENT_OP&branchNo=999&requestId=CLIENT_REQ`；同名上下文 Header 也传伪造值；请求无 Body |
| 操作步骤 | 1. 保存服务端配置及两条快照。<br>2. POST 路径中的 `review-pass`。<br>3. 保存响应、回查两条记录并关联日志。 |
| 预期结果 | HTTP 200 / `0000`；响应 `data.billId` 只能是 `A-CTX.billId`、状态已通过、版本 5。`A-CTX.CHECKER_NO/LAST_OPERATOR_NO=77210088`、`BRANCH_NO` 仍为 772、`LAST_REQUEST_ID` 等于 WebFE 日志生成值且不等于 `CLIENT_REQ`；URL 外的 `billId` 和三个伪造上下文均无效。`A-RETURN` 全行不变。 |
| 清理 | 不复用 `A-CTX`；重载数据包恢复 |
| 证据 | 配置快照、原始请求/响应、两条前后 SQL、三层日志关联 |

#### `REV-TC-ACT-009`：billId 空白和格式边界

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ACT-009` |
| 标题 | 空白、超长和含路径分隔符的 billId 需要明确校验与路由规则 |
| 需求 ID | `REV-API-004`、`REV-ERR-001`、`REV-ERR-002` |
| 优先级 | P1 |
| 执行工具 / 状态 | curl `--path-as-is` 或原始 HTTP 客户端、服务日志、Oracle SQL / 阻塞：`TBD-007` |
| 前置条件 | 保存测试 Schema 全表快照；客户端与代理不预先规范化路径 |
| 测试数据 | URL 段分别为 `%20`、`null`、33 个 `A`、`A%2FB`；每个分别用于 pass/return 且无 Body |
| 操作步骤 | 1. 保留每个原始请求行。<br>2. 逐一发送并保存路由及业务响应。<br>3. 比较全表快照。 |
| 预期结果 | 需求只定义“缺少”与“不存在”两类，没有定义空白是否等于缺少、是否校验格式/长度、编码斜杠由路由层还是业务层处理；HTTP、业务码和响应字段为 TBD。任何变体都不得定位或修改一条非预期凭证，数据库全表必须不变。 |
| 清理 | 无 |
| 证据 | 原始请求行、每份响应、路由日志、前后全表快照、TBD 结论链接 |

### 6.4 并发、事务和故障恢复

#### `REV-TC-CON-001`：审核通过与退回并发竞争

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-CON-001` |
| 标题 | 同一待审核凭证并发通过和退回时仅一个请求提交并决定最终审计 |
| 需求 ID | `REV-CON-001`、`REV-CON-002`、`REV-CON-003`、`REV-DAT-001`、`REV-DAT-003`、`REV-DAT-005`、`REV-DAT-007` |
| 优先级 | P0 |
| 执行工具 / 状态 | 两个独立终端或 Postman Runner、Oracle SQL、三层日志 / 未执行 |
| 前置条件 | 重载 `A-CON-PR-01..10`，每条待审核、版本 3；两个客户端均经过同一 WebFE；为每轮保存全行快照和数据库 `T0_DB` |
| 测试数据 | 每轮对同一 `billId`：终端 A 无 Body POST `review-pass`，终端 B 无 Body POST `review-return`；共 10 轮，每轮使用下一条独立凭证 |
| 操作步骤 | 1. 两终端预置请求但不发送。<br>2. 使用统一屏障/倒计时同时释放；记录客户端开始时间和请求日志时间，若两请求未重叠则该轮作废重跑。<br>3. 收集两个响应。<br>4. 查询 `T1_DB`、最终全行及两条请求日志。<br>5. 对 10 轮重复以上步骤。 |
| 预期结果 | 每轮响应组合恰为：1 个 HTTP 200 / `0000` 和 1 个 HTTP 409 / `3004`；冲突响应 `data=null`。最终 `VERSION_NO=4`，仅比初始增加 1。若通过请求成功，最终 `STATUS=20_REVIEW_APPROVED`、`LAST_ACTION=REVIEW_PASS`，成功摘要和数据库一致；若退回请求成功，则为 `30_REVIEW_REJECTED/REVIEW_RETURN`。`CHECKER_NO/LAST_OPERATOR_NO/LAST_REQUEST_ID/三个时间字段` 只反映成功请求，失败请求不得覆盖；所有其他列和其他凭证不变。10 轮均不得出现两个成功、两个冲突或版本 5。 |
| 清理 | 每轮数据不复用；失败或非重叠轮次重载对应别名后重跑 |
| 证据 | 20 个请求/响应、屏障时间、每轮 S0/S1 SQL、三层日志、10 轮汇总表 |

#### `REV-TC-CON-002`：相同审核动作并发竞争

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-CON-002` |
| 标题 | 双通过或双退回并发时均只有一个请求成功且版本只增加一次 |
| 需求 ID | `REV-CON-001`、`REV-CON-002`、`REV-CON-004`、`REV-DAT-005`、`REV-DAT-007` |
| 优先级 | P0 |
| 执行工具 / 状态 | 两个独立终端、Oracle SQL、三层日志 / 未执行 |
| 前置条件 | 重载 `A-CON-P-01..05` 和 `A-CON-R-01..05`，每条待审核、版本 3 |
| 测试数据 | 5 轮双 `review-pass`；5 轮双 `review-return`；每轮同一 ID、不同终端、无 Body |
| 操作步骤 | 按 `REV-TC-CON-001` 的屏障方式执行 10 轮并保存每轮两个响应及最终快照。 |
| 预期结果 | 每轮恰有 1 个 HTTP 200 / `0000`、1 个 HTTP 409 / `3004`；最终版本均为 4。双通过最终只能是 `20_REVIEW_APPROVED/REVIEW_PASS`，双退回只能是 `30_REVIEW_REJECTED/REVIEW_RETURN`；审计字段只写一次且对应胜者请求；失败响应 `data=null`；其他列和其他记录不变。 |
| 清理 | 每轮独立数据不复用；非重叠轮次重载后重跑 |
| 证据 | 20 个请求/响应、屏障证据、10 份前后 SQL、日志与汇总表 |

#### `REV-TC-ERR-001`：数据库更新失败回滚

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ERR-001` |
| 标题 | 读取成功但 Oracle 更新报错时返回数据库错误且整笔回滚 |
| 需求 ID | `REV-ERR-003`、`REV-DAT-007` |
| 优先级 | P0 |
| 执行工具 / 状态 | Oracle DBA 工具、Postman、Oracle SQL、服务日志 / 未执行（需批准的隔离环境故障窗口） |
| 前置条件 | 重载 `A-DBERR`：待审核、版本 11；DBA 已批准可逆方案，使应用仍可 SELECT 该行但 UPDATE 必然报 Oracle 错误，例如测试专用触发器抛错或对非表属主运行用户临时撤销 UPDATE；已记录原权限/对象并准备恢复命令 |
| 测试数据 | 无 Body POST `review-pass`；目标 `A-DBERR.billId` |
| 操作步骤 | 1. 导出目标全行、其他记录摘要和故障前数据库健康证据。<br>2. DBA 启用仅影响本测试更新的故障并记录时间。<br>3. 发送请求并保存响应、C/Oracle 错误日志。<br>4. 从独立会话回查全行及其他记录。<br>5. DBA 恢复原状态，验证健康检查和普通只读查询。 |
| 预期结果 | 请求 HTTP 500、`respCode="4001"`、`data=null`，满足失败信封且不泄露 Oracle SQL、错误堆栈或凭据。事务回滚：`A-DBERR` 全行与步骤 1 一致，仍待审核、版本仍 11、所有审核/最后动作/时间字段未部分更新；其他记录不变。恢复后环境健康，故障对象或权限无残留。 |
| 清理 | 恢复权限/删除测试故障对象；复核恢复结果；`A-DBERR` 可在确认全行未变后保留作重试 |
| 证据 | 变更审批、故障前后权限/对象、请求/响应、前后 SQL、C/Oracle 日志、恢复健康结果 |

#### `REV-TC-ERR-002`：Tuxedo 调用超时

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ERR-002` |
| 标题 | 待审核列表调用超过 WebFE 超时阈值时映射为 504/4002 |
| 需求 ID | `REV-ERR-004`、`REV-API-007` |
| 优先级 | P1 |
| 执行工具 / 状态 | 环境管理员工具、Postman、WebFE/Jolt/Tuxedo 日志、Oracle SQL / 未执行 |
| 前置条件 | 重载 `DP-LIST`；批准可逆故障方案能让 `CNAPS4609Q` 调用超过 WebFE 超时阈值但不造成服务不可用分类；记录超时配置和恢复步骤 |
| 测试数据 | Body `{}` POST `/api/cnaps/vouchers/review-list` |
| 操作步骤 | 1. 保存全表计数、超时阈值和健康状态。<br>2. 启用延迟/网络超时故障。<br>3. 发送请求，测量客户端耗时并保存响应和三层日志。<br>4. 解除故障，等待在途调用结束，再比较数据库并执行一次健康列表请求。 |
| 预期结果 | 故障请求 HTTP 504、`respCode="4002"`、`data=null`，满足失败信封；日志能区分超时而非未注册/不可用。列表是只读操作，故障前后 `T_CNAPS_BILL_POC` 不变。恢复后同一 `{}` 请求 HTTP 200 / `0000`、`total=102`，环境无残留故障。 |
| 清理 | 解除延迟/网络故障并恢复原超时配置 |
| 证据 | 故障审批与时间、超时配置、请求耗时/响应、三层日志、前后 SQL、恢复请求 |

#### `REV-TC-ERR-003`：Tuxedo 服务不可用

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-ERR-003` |
| 标题 | Jolt/Tuxedo 服务不可用时待审核列表映射为 503/4003 |
| 需求 ID | `REV-ERR-005`、`REV-API-007` |
| 优先级 | P1 |
| 执行工具 / 状态 | 环境启停工具、Postman、服务日志、Oracle SQL / 未执行 |
| 前置条件 | 重载 `DP-LIST`；批准只影响隔离环境的停服或注销服务窗口；已记录原服务状态和恢复命令 |
| 测试数据 | 在 `CNAPS4609Q` 不可用期间 Body `{}` POST review-list |
| 操作步骤 | 1. 保存数据库和服务状态。<br>2. 停止/隔离所需 Tuxedo/Jolt 服务并确认不可用。<br>3. 发送请求并保存响应、日志。<br>4. 恢复服务，确认注册和健康，再重发请求。 |
| 预期结果 | 故障请求 HTTP 503、`respCode="4003"`、`data=null`；日志明确为服务不可用而非业务错误、超时或数据库错误；数据库不变。恢复后请求 HTTP 200 / `0000`、`total=102`，服务注册回到故障前状态。 |
| 清理 | 恢复 Tuxedo/Jolt 并确认无遗留停服 |
| 证据 | 停服/恢复记录、故障响应、三层日志、前后 SQL、恢复响应 |

### 6.5 人工结构与范围约束检查

#### `REV-TC-STR-001`：路由、服务注册和 Jolt 元数据一致

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-STR-001` |
| 标题 | 三条 HTTP 路由在 Java、Tuxedo、配置和 Jolt 元数据中使用一致服务与字段 |
| 需求 ID | `REV-SCP-001`、`REV-SCP-002`、`REV-SCP-006` |
| 优先级 | P1 |
| 执行工具 / 状态 | `rg`、人工代码评审、Jolt 仓库只读查询 / 未执行 |
| 前置条件 | 候选提交已检出；记录候选 SHA；只读检查生产源与部署产物，不修改文件 |
| 测试数据 | 搜索范围：`web-fe/src/main`、`tuxedo-server`、`tuxedo`、`scripts`、`conf`；关键词 `CNAPS4609Q`、`CNAPS5702A`、`CNAPS5702R`、`CNAPS5702Q` |
| 操作步骤 | 1. 用 `rg -n` 导出全部命中。<br>2. 核对 `TuxedoRequestMapper`：review-list -> `CNAPS4609Q`、pass -> `CNAPS5702A`、return -> `CNAPS5702R`。<br>3. 核对 `tuxedo-server/Makefile`、服务入口声明、`tuxedo/UBBCONFIG` 和加载脚本。<br>4. 核对 `tuxedo/jolt/cnaps_services.bulk` 及实际 Jolt 仓库中 A/R 服务块。<br>5. 对照 Java 映射、FML32、C 读取/输出和 Jolt 参数，逐项核对 `BILL_ID`、可信上下文、响应码/消息及六个审核摘要字段。 |
| 预期结果 | 三个路由只映射到需求规定服务。构建、配置、入口和 Jolt 仓库均注册 `CNAPS5702A/R`，继续注册 `CNAPS4609Q`；字段方向、类型、重复次数和命名在各层一致，审核成功六字段均可从 C 经 FML/Jolt 到 HTTP。生产路径无 `CNAPS5702Q` 命中。此用例无 HTTP 调用，HTTP 状态/业务码/响应字段为不适用；无数据库变化。 |
| 清理 | 无 |
| 证据 | 搜索命令与完整输出、逐文件评审记录、Jolt 仓库查询、候选 SHA |

#### `REV-TC-STR-002`：共用审核函数且查询/分页实现不变

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-STR-002` |
| 标题 | A/R 在同一 C 文件复用状态变更函数且既有查询和分页读取未被改动 |
| 需求 ID | `REV-SCP-003`、`REV-SCP-004`、`REV-CON-001`、`REV-CON-002` |
| 优先级 | P1 |
| 执行工具 / 状态 | `git diff`、`rg`、人工代码评审 / 未执行 |
| 前置条件 | 基线 `84b0d98` 可读；候选 SHA 已记录 |
| 测试数据 | `tuxedo-server/src/services/cnaps_review.c`、`tuxedo-server/src/services/cnaps_query.c`、`web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java` 及实际分页读取文件 |
| 操作步骤 | 1. 检查 `cnaps_review.c` 是否同时定义 A/R 入口并调用同一个内部状态变更函数。<br>2. 人工核对该函数顺序：读取、待审核校验、按 `BILL_ID+VERSION_NO` 更新、0 行映射 `3004`、错误回滚、成功重读。<br>3. 执行 `git diff 84b0d98 --` 对比查询 C 和分页读取相关文件。<br>4. 导出差异并由评审人签字。 |
| 预期结果 | A/R 位于同一 `cnaps_review.c` 且业务状态变更逻辑只有一个共享实现；传入目标状态和 `lastAction` 决定 A/R 差异。事务顺序与需求一致。`cnaps_query.c` 和既有分页读取逻辑相对基线无业务差异；若候选包含格式或必要注册变化，必须逐行证明不改变查询/分页行为，否则失败。此人工检查无 HTTP 状态、业务码或响应字段，数据库不变。 |
| 清理 | 无 |
| 证据 | 文件路径/行号、共享函数调用图、基线到候选 diff、评审签字 |

#### `REV-TC-STR-003`：无旧服务、旧状态或范围扩张

| 字段 | 内容 |
| --- | --- |
| 用例 ID | `REV-TC-STR-003` |
| 标题 | 候选变更未引入 CNAPS5702Q、前端资源、新数据结构或范围外审核能力 |
| 需求 ID | `REV-STA-005`、`REV-SCP-001`、`REV-SCP-005` |
| 优先级 | P2 |
| 执行工具 / 状态 | `git diff --name-status`、`rg`、人工评审 / 未执行 |
| 前置条件 | 基线 `84b0d98` 与候选 SHA 已记录 |
| 测试数据 | 候选变更文件清单；生产 Java/C/配置/Jolt/SQL/Maven 文件；关键词 `CNAPS5702Q`、`00_DRAFT`、`reviewComment`、`rejectReason`、`REVIEW_COMMENT`、`REJECT_REASON` |
| 操作步骤 | 1. 导出 `git diff --name-status 84b0d98..<candidate>` 和完整差异。<br>2. 搜索生产路径中的旧服务、旧状态写入和旧审核输入字段。<br>3. 检查是否新增/修改 JSP、JavaScript、CSS 或前端框架文件。<br>4. 检查 SQL 是否新增表/列，POM 是否新增依赖。<br>5. 检查是否新增批量、多级、撤销、通知、权限或外发接口。 |
| 预期结果 | 生产源、部署配置和加载脚本均无 `CNAPS5702Q`；不存在把任何记录赋值为 `00_DRAFT` 的新代码。审核接口不读取或持久化意见、退回原因或客户端目标状态。候选没有 JSP/JS/CSS/浏览器页面、没有新表/列、框架/依赖，也没有范围外接口或行为。旧列名可因既有修改逻辑或结构定义出现，但不得出现在新审核请求读取或赋值路径。此人工检查无 HTTP 状态、业务码或响应字段，数据库不变。 |
| 清理 | 无 |
| 证据 | 文件清单、搜索命令与完整输出、SQL/POM 差异、范围评审结论、候选 SHA |

## 7. 待确认项

以下 TBD 不得以当前实现行为关闭；必须由需求/API 负责人给出书面契约，并同步更新需求或 API 文档。

| TBD ID | 待确认问题与冲突证据 | 可选决策（不代替确认） | 影响用例 | 负责人 | 关闭条件 |
| --- | --- | --- | --- | --- | --- |
| `TBD-001` | 需求只规定 GET 返回 HTTP 405；v0.5 错误码表没有 405 对应业务码，也未说明是否返回三字段 JSON | A. 405 无 Body；B. 405 使用三字段信封并新增/复用明确业务码 | `REV-TC-API-002` | API 负责人 | 在 API 文档中给出 405 的 Content-Type、顶层字段、`respCode/respMsg/data` |
| `TBD-002` | “审核动作不接收 Body”未说明非空 Body 是拒绝还是忽略；旧资料中的意见/原因又被本需求明确移出范围 | A. 以指定 4xx/业务码拒绝且不写库；B. 忽略全部 Body 后只按 URL 动作 | `REV-TC-API-004` | 需求负责人、API 负责人 | 明确处理策略、HTTP、业务码、响应字段及数据库不变/合法变化 |
| `TBD-003` | 已定义 `pageNo`/`pageSize` 有效范围，但未定义 0、负数、101、非整数、字符串和 null 的错误映射 | A. 全部 HTTP 400 / `2002`；B. 为 null/类型转换分别定义默认或拒绝规则 | `REV-TC-LST-010` | API 负责人 | 为测试数据表中的每一等价类给出 HTTP、业务码、`data` 及是否调用下游 |
| `TBD-004` | review-list 要求 JSON 对象或 `{}`，但缺失/空白/畸形/数组/null Body 及错误 Content-Type 未定义 | A. 统一解析错误；B. 部分值等同 `{}`；C. Content-Type 单独返回 415 | `REV-TC-LST-012` | API 负责人 | 给出每个变体的状态、业务码、响应信封和下游调用规则 |
| `TBD-005` | v0.5 API 未定义现有 PUT 修改成功时 `data` 的精确字段集合；闭环仍需调用该接口 | A. 返回完整详情字段；B. 返回固定摘要字段 | `REV-TC-ACT-007` | API 负责人 | 更新 API 4.5，列出 PUT 成功 `data` 的键、类型和值 |
| `TBD-006` | 可选日期/流水号字段的 null、空白、错误 JSON 类型、尾随空格及未知键处理未定义 | A. 严格拒绝；B. 指定值视为未传；C. 未知键拒绝或忽略 | `REV-TC-LST-013` | 需求负责人、API 负责人 | 逐项定义解析、trim/大小写、HTTP、业务码和是否调用下游 |
| `TBD-007` | 只定义缺少和不存在 `billId`，未定义空白、字面 `null`、超长、编码分隔符及路径规范化边界 | A. 统一按 `2001`；B. 格式错误按 `2002`；C. 合法路由后按 `3001` | `REV-TC-ACT-009` | API 负责人、WebFE 负责人 | 定义 billId 格式/长度/trim、路由规范化及各类错误映射 |

当前文档状态为“待确认”。TBD 关闭前，受影响用例保持阻塞或部分阻塞，不计入用例就绪率。

设计文档另列环境验证项 `TBD-01`：当前 `UBBCONFIG` 的业务服务器 `MAX=1` 可能使两个 HTTP 请求在原生层串行；即使外部结果呈现一胜一冲突，也不能单凭该配置证明多原生实例在 Oracle 行更新上的真实竞争。未经环境负责人批准，不修改仓库配置；执行记录必须注明实际并发验证层级。

## 8. 需求 ID → 用例 ID 追踪矩阵

| 需求 ID | 主要测试点 | 用例 ID | 手工执行方式 | 覆盖状态 |
| --- | --- | --- | --- | --- |
| `REV-API-001` | 三个 POST 入口、GET 不兼容 | `REV-TC-API-001`、`REV-TC-API-002` | HTTP + 日志 | 已覆盖；405 Body 待确认 |
| `REV-API-002` | 三个 GET 均 405 | `REV-TC-API-002` | 原始 HTTP + 无下游调用日志 | 阻塞 `TBD-001` |
| `REV-API-003` | JSON 对象/空对象及解析异常 | `REV-TC-API-001`、`REV-TC-LST-012`、`REV-TC-LST-013` | HTTP + 日志 | 正常已覆盖；异常阻塞 `TBD-004/006` |
| `REV-API-004` | billId 只取 URL、缺失和边界 | `REV-TC-API-003`、`REV-TC-ACT-001`、`REV-TC-ACT-002`、`REV-TC-ACT-008`、`REV-TC-ACT-009` | HTTP + SQL | 核心已覆盖；格式边界阻塞 `TBD-007` |
| `REV-API-005` | 正常无 Body、非空 Body | `REV-TC-ACT-001`、`REV-TC-ACT-002`、`REV-TC-API-004` | HTTP + SQL | 正常已覆盖；异常阻塞 `TBD-002` |
| `REV-API-006` | 审核成功六字段摘要 | `REV-TC-ACT-001`、`REV-TC-ACT-002` | HTTP + SQL | 已覆盖 |
| `REV-API-007` | 三字段信封、失败 data=null | `REV-TC-API-001`、`REV-TC-ACT-001`、`REV-TC-ACT-002`、`REV-TC-ACT-003`、`REV-TC-ERR-001`、`REV-TC-ERR-002`、`REV-TC-ERR-003` | HTTP | 已覆盖；405 除外 |
| `REV-API-008` | 分页及九字段 records | `REV-TC-API-001`、`REV-TC-LST-001`、`REV-TC-LST-009` | HTTP + SQL | 已覆盖 |
| `REV-LST-001` | 强制待审核、覆盖客户端状态 | `REV-TC-API-001`、`REV-TC-LST-011` | HTTP + SQL + 日志 | 已覆盖 |
| `REV-LST-002` | 固定服务端机构范围 | `REV-TC-API-001`、`REV-TC-LST-011` | HTTP + 跨机构 SQL | 已覆盖 |
| `REV-LST-003` | 无日期过滤、空白边界 | `REV-TC-API-001`、`REV-TC-LST-003` | HTTP + SQL | 已覆盖 |
| `REV-LST-004` | 开始日期严格格式、单边、包含性 | `REV-TC-LST-001`、`REV-TC-LST-002`、`REV-TC-LST-004`、`REV-TC-LST-008`、`REV-TC-LST-013` | HTTP + SQL | 核心已覆盖；null/类型阻塞 `TBD-006` |
| `REV-LST-005` | 结束日期严格格式、单边、包含性 | `REV-TC-LST-001`、`REV-TC-LST-002`、`REV-TC-LST-004`、`REV-TC-LST-008`、`REV-TC-LST-013` | HTTP + SQL | 核心已覆盖；null/类型阻塞 `TBD-006` |
| `REV-LST-006` | 合法/非法日期、反向范围、2002 | `REV-TC-LST-002`、`REV-TC-LST-004`、`REV-TC-LST-005` | HTTP + 无下游日志 | 已覆盖 |
| `REV-LST-007` | 流水号精确匹配 | `REV-TC-LST-007`、`REV-TC-LST-008`、`REV-TC-LST-013` | HTTP + SQL | 核心已覆盖；空白/类型阻塞 `TBD-006` |
| `REV-LST-008` | 默认 1/10 | `REV-TC-API-001` | HTTP | 已覆盖 |
| `REV-LST-009` | pageNo 正整数和末页外 | `REV-TC-LST-009`、`REV-TC-LST-010` | HTTP | 有效边界已覆盖；非法值阻塞 `TBD-003` |
| `REV-LST-010` | pageSize 1～100 | `REV-TC-LST-009`、`REV-TC-LST-010` | HTTP | 有效边界已覆盖；非法值阻塞 `TBD-003` |
| `REV-LST-011` | 禁止 workDate | `REV-TC-LST-006` | HTTP + 无下游日志 | 已覆盖 |
| `REV-STA-001` | 待审核 -> 已通过 | `REV-TC-ACT-001`、`REV-TC-ACT-005` | HTTP + SQL | 已覆盖 |
| `REV-STA-002` | 待审核 -> 已退回 | `REV-TC-ACT-002`、`REV-TC-ACT-006`、`REV-TC-ACT-007` | HTTP + SQL | 已覆盖 |
| `REV-STA-003` | 已退回 -> 修改 -> 待审核 | `REV-TC-ACT-007` | HTTP + SQL | 数据状态已覆盖；PUT data 阻塞 `TBD-005` |
| `REV-STA-004` | 非待审核状态 3004、重复动作 | `REV-TC-ACT-004`、`REV-TC-ACT-005`、`REV-TC-ACT-006` | HTTP + SQL | 已覆盖 |
| `REV-STA-005` | 不写 00_DRAFT | `REV-TC-ACT-004`、`REV-TC-ACT-007`、`REV-TC-STR-003` | 生命周期 + 代码检查 | 已覆盖 |
| `REV-DAT-001` | 审核人来源 | `REV-TC-ACT-001`、`REV-TC-ACT-002`、`REV-TC-ACT-008`、`REV-TC-CON-001` | SQL + 配置/日志 | 已覆盖 |
| `REV-DAT-002` | 审核时间为 Oracle 时间 | `REV-TC-ACT-001`、`REV-TC-ACT-002` | 双 DB 时间窗 | 已覆盖 |
| `REV-DAT-003` | 最后操作员和请求号 | `REV-TC-ACT-001`、`REV-TC-ACT-002`、`REV-TC-ACT-008`、`REV-TC-CON-001` | SQL + 三层日志 | 已覆盖 |
| `REV-DAT-004` | 动作时间、更新时间为 Oracle 时间 | `REV-TC-ACT-001`、`REV-TC-ACT-002` | 双 DB 时间窗 | 已覆盖 |
| `REV-DAT-005` | 版本恰加 1 | `REV-TC-ACT-001`、`REV-TC-ACT-002`、`REV-TC-ACT-005`、`REV-TC-ACT-006`、`REV-TC-ACT-007`、`REV-TC-CON-001`、`REV-TC-CON-002` | SQL | 已覆盖 |
| `REV-DAT-006` | 成功重读后返回摘要 | `REV-TC-ACT-001`、`REV-TC-ACT-002` | 响应与提交后 SQL 逐字段比对 | 已覆盖 |
| `REV-DAT-007` | 允许变化列和不变项 | `REV-TC-ACT-001`、`REV-TC-ACT-002`、`REV-TC-ACT-003`、`REV-TC-ACT-004`、`REV-TC-ACT-005`、`REV-TC-ACT-006`、`REV-TC-ACT-008`、`REV-TC-CON-001`、`REV-TC-CON-002`、`REV-TC-ERR-001` | 全行/其他行快照 | 已覆盖 |
| `REV-DAT-008` | 退回修改后清空审核人/时间 | `REV-TC-ACT-007` | HTTP + SQL | 已覆盖 |
| `REV-CTX-001` | 服务端 operatorNo 覆盖 | `REV-TC-LST-011`、`REV-TC-ACT-008` | 配置 + HTTP + SQL/日志 | 已覆盖 |
| `REV-CTX-002` | 服务端 branchNo 覆盖 | `REV-TC-LST-011`、`REV-TC-ACT-008` | 跨机构 SQL + 配置 | 已覆盖 |
| `REV-CTX-003` | 服务端 requestId 覆盖 | `REV-TC-LST-011`、`REV-TC-ACT-008` | 三层日志 + SQL | 已覆盖 |
| `REV-CON-001` | 读取后按 ID+版本更新 | `REV-TC-CON-001`、`REV-TC-CON-002`、`REV-TC-STR-002` | 并发 + 代码评审 | 已覆盖 |
| `REV-CON-002` | 0 行更新回滚 3004 | `REV-TC-ACT-004`、`REV-TC-CON-001`、`REV-TC-CON-002`、`REV-TC-STR-002` | HTTP + SQL + 代码评审 | 已覆盖 |
| `REV-CON-003` | 通过/退回唯一胜者 | `REV-TC-CON-001` | 10 轮双终端并发 | 已覆盖 |
| `REV-CON-004` | 重复/同动作并发只提交一次 | `REV-TC-ACT-005`、`REV-TC-ACT-006`、`REV-TC-CON-002` | 顺序重复 + 10 轮并发 | 已覆盖 |
| `REV-ERR-001` | 缺 billId 400/2001 | `REV-TC-API-003`、`REV-TC-ACT-009` | 原始 HTTP + SQL | 缺失已覆盖；格式边界阻塞 `TBD-007` |
| `REV-ERR-002` | 不存在 404/3001 | `REV-TC-ACT-003`、`REV-TC-ACT-009` | HTTP + SQL | 不存在已覆盖；格式边界阻塞 `TBD-007` |
| `REV-ERR-003` | 数据库错误 500/4001 并回滚 | `REV-TC-ERR-001` | 故障注入 + SQL | 已覆盖 |
| `REV-ERR-004` | Tuxedo 超时 504/4002 | `REV-TC-ERR-002` | 超时注入 + 日志 | 已覆盖 |
| `REV-ERR-005` | Tuxedo 不可用 503/4003 | `REV-TC-ERR-003` | 停服注入 + 日志 | 已覆盖 |
| `REV-SCP-001` | review-list 复用 4609Q、无 5702Q | `REV-TC-STR-001`、`REV-TC-STR-003` | 代码/配置/Jolt 检查 | 已覆盖 |
| `REV-SCP-002` | 仅新增 A/R | `REV-TC-STR-001` | 全路径注册检查 | 已覆盖 |
| `REV-SCP-003` | 同文件共享函数 | `REV-TC-STR-002` | C 代码评审 | 已覆盖 |
| `REV-SCP-004` | 查询 C/分页读取不变 | `REV-TC-STR-002` | 基线 diff | 已覆盖 |
| `REV-SCP-005` | 无前端、新结构、依赖或范围外能力 | `REV-TC-STR-003` | 文件清单 + diff + 搜索 | 已覆盖 |
| `REV-SCP-006` | Java/C/配置/Jolt 一致 | `REV-TC-STR-001` | 跨层字段矩阵检查 | 已覆盖 |

追踪结果：50 条原子需求均至少关联 1 条用例；其中 7 个 TBD 影响 7 条用例（6 条阻塞、1 条部分阻塞），不得从覆盖统计中静默删除。

## 9. 执行记录与证据模板

每条用例每次执行复制以下记录；“实际结果”不得只填“同预期”。

```text
用例 ID：
需求 ID：
候选提交 / 构建号：
WebFE / Jolt / Tuxedo / Oracle 环境版本：
Base URL：
服务端 operatorNo / branchNo 配置证据：
执行人 / 执行时间：
数据包 / 数据别名 / 实际 billId：
执行前状态 / VERSION_NO：
实际请求（Method、URL、Header、原始 Body）：
实际 HTTP 状态：
实际 respCode / respMsg / data 字段和值：
T0_DB / T1_DB：
执行前 SQL 快照：
执行后 SQL 快照：
WebFE / Jolt / Tuxedo / Oracle 日志位置：
步骤偏差：
实际结果（逐项值）：
结论：通过 / 失败 / 阻塞 / 不适用
缺陷 ID / 阻塞单号：
清理或恢复结果：
证据链接：
```

并发用例另附每轮汇总：

| 轮次 | 数据别名 | 初始版本 | A 请求/响应 | B 请求/响应 | 是否时间重叠 | 胜者 | 最终状态 | 最终版本 | 胜者 requestId | 审计一致 | 结论 |
| ---: | --- | ---: | --- | --- | :---: | --- | --- | ---: | --- | :---: | --- |
| 1 |  |  |  |  |  |  |  |  |  |  |  |

## 10. 执行顺序、覆盖指标与退出准则

### 10.1 建议执行顺序

1. 由需求/API 负责人关闭 `TBD-001..007`，更新本文受影响预期和状态。
2. 执行 `REV-TC-STR-001..003`，确认候选包跨层一致且没有范围扩张。
3. 加载 `DP-LIST`，执行路由、列表正常、异常、边界和上下文用例。
4. 重载 `DP-ACTION`，依次执行成功动作、非法状态、重复动作和完整闭环；每个破坏性用例使用独立数据。
5. 重载并发数据，执行 `REV-TC-CON-001/002`；无有效时间重叠的轮次不计结果，并在执行记录中注明设计 `TBD-01` 的并发验证层级。
6. 在管理员批准的维护窗口执行数据库、超时和不可用故障用例，完成恢复检查。
7. 汇总响应、SQL、日志和清理结果，执行追踪矩阵正向/反向复核。

### 10.2 当前设计覆盖

| 指标 | 结果 | 说明 |
| --- | ---: | --- |
| 原子需求追踪覆盖 | 50 / 50（100%） | 每条需求至少关联一个用例；部分异常契约仍为 TBD |
| 手工用例数 | 34 | 仅文档用例，不含自动化测试 |
| 当前可完全执行用例 | 27 / 34（79.4%） | 6 条阻塞、1 条部分阻塞 |
| 已定义错误映射覆盖 | 8 / 8 | `0000/200`、`2001/400`、`2002/400`、`3001/404`、`3004/409`、`4001/500`、`4002/504`、`4003/503` |
| 需求合法状态边覆盖 | 3 / 3 | 通过、退回、退回后修改 |
| 明确非法审核组合 | 8 / 8 | 4 个非待审核来源状态 × 2 个审核动作，另含重复和并发 |

### 10.3 正式执行退出准则

- `TBD-001..007` 已由责任方书面关闭，需求/API 与本文同步；否则文档和受影响用例保持“待确认/阻塞”。
- 所有 P0 用例通过，且不存在未关闭的状态、版本、审计、事务、并发或数据泄露缺陷。
- 所有非阻塞 P1 用例通过；P2 未执行项有批准的不适用或延期说明。
- 10 轮相反动作并发和 10 轮同动作并发均取得有效重叠证据，且没有双成功、双冲突或版本增加两次。
- 数据库、超时和不可用故障均完成恢复，未遗留权限、触发器、网络、服务或配置变更。
- 每条写用例均有请求/响应、操作前后 SQL 和必要日志；每条失败用例均证明全行与其他记录不变。
- 需求追踪矩阵完成正向审查；所有用例均有需求/风险来源，未把范围外功能加入通过标准。

## 11. 评审签字

| 角色 | 姓名 | 结论 | 日期 | 备注/单号 |
| --- | --- | --- | --- | --- |
| 需求负责人 |  | 通过 / 带条件通过 / 待确认 / 拒绝 |  |  |
| API / WebFE 负责人 |  | 通过 / 带条件通过 / 待确认 / 拒绝 |  |  |
| Tuxedo / Oracle 负责人 |  | 通过 / 带条件通过 / 待确认 / 拒绝 |  |  |
| 测试负责人 |  | 通过 / 带条件通过 / 待确认 / 拒绝 |  |  |
