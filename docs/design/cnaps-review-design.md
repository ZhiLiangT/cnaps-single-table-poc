# CNAPS 凭证审核执行级技术设计

> 设计版本：v1.0<br>
> 日期：2026-07-21<br>
> 需求基线：`docs/requirements/cnaps-review-requirements.md` v1.4（2026-07-15）<br>
> API 基线：`docs/cnaps-frontend-api.md` v0.5<br>
> 代码基线：分支 `poc-flow-test-1`，提交 `84b0d98be850d2e5f0ce2c71ebaa56303cd06d82`<br>
> 基线状态：开始设计核验时工作区无未提交修改<br>
> 设计状态：待评审；代码方案可实施，并发真实性验证环境见 `TBD-01`

## 1. 目标与范围

### 1.1 目标

在现有 Java Servlet → Jolt → Tuxedo C → Oracle 单表调用链上实现单级审核闭环：

```text
创建 -> 10_PENDING_REVIEW -> 审核通过 -> 20_REVIEW_APPROVED
创建 -> 10_PENDING_REVIEW -> 审核退回 -> 30_REVIEW_REJECTED
30_REVIEW_REJECTED -> 修改 -> 10_PENDING_REVIEW -> 再次审核
```

设计结果必须使实现人员无需再决定路由、服务名、字段来源、状态转换、SQL 更新集合、事务顺序、错误映射或配置注册位置。

### 1.2 范围内

- `POST /api/cnaps/vouchers/review-list` 待审核分页查询。
- `POST /api/cnaps/vouchers/{billId}/review-pass` 单笔审核通过。
- `POST /api/cnaps/vouchers/{billId}/review-return` 单笔审核退回。
- WebFE 对审核列表状态和服务端上下文的强制覆盖。
- 新增 Tuxedo 服务 `CNAPS5702A`、`CNAPS5702R`，两者共用一个原生审核函数。
- 审核状态、审核人、审核时间、最后动作审计及版本更新。
- 重复审核、并发状态/版本变化、记录不存在、数据库错误及 Tuxedo 调用错误处理。
- 与上述行为直接相关的 Mock、单元/契约测试、部署元数据和冒烟验收调整。

### 1.3 范围外

- JSP、JavaScript、CSS、浏览器页面或任何前端框架。
- 登录、权限、角色、录入审核分离及机构授权模型。
- 审核意见、退回原因、批量审核、多级审核、撤销、通知、外部工作流及真实 CNAPS 外发。
- 新表、新列、新索引、新框架、新依赖或新的持久化介质。
- 对既有创建、详情、删除、通用查询的业务规则重构。
- 发布、部署、提交、推送及生产数据迁移执行。

### 1.4 禁止修改和安全边界

- 不新增 `CNAPS5702Q`；待审核列表必须复用 `CNAPS4609Q`。
- 不修改 `tuxedo-server/src/services/cnaps_query.c` 及其分页读取逻辑。
- 不改变既有 HTTP 响应信封 `respCode/respMsg/data` 和 FML32 命名。
- 不写入已退役状态 `00_DRAFT`。
- 不使用审核请求 Body 中的 `billId`、`status`、审核意见或退回原因。
- 不重新格式化或整体重写 `tuxedo/jolt/cnaps_services.bulk`；只在明确锚点局部追加 A/R 服务块。
- 不改变现有重复字段元数据的顺序、访问方向和任何 `count=0`。
- 实现阶段不得从 Git 历史恢复旧审核实现，不得覆盖已有用户改动。

## 2. 输入依据、优先级与原子需求

### 2.1 输入与用途

| 输入 | 版本/基线 | 本设计用途 |
| --- | --- | --- |
| `AGENTS.md` | 当前工作树 | 范围、安全编辑、构建和验证门禁 |
| `docs/requirements/cnaps-review-requirements.md` | v1.4 | 功能、状态、服务、审计、并发及错误的首要事实依据 |
| `docs/cnaps-frontend-api.md` | v0.5 | 需求未展开的通用响应、列表记录字段和 HTTP 契约 |
| `docs/standards/requirements-to-design-spec.md` | v1.0 | 设计结构、追踪和质量门禁 |
| `docs/cnaps-api-database.md` | 当前文件 | 仅核验现有表字段；其旧审核 API 约束不作为本次设计依据 |
| 当前 Java、C、SQL、Jolt 和部署配置 | 提交 `84b0d98...` | 核验复用能力、实际差距和精确修改位置 |

采用优先级：仓库/任务约束 > 本次需求 v1.4 > 前端 API v0.5 > 当前代码和配置 > 其他历史或补充文档。

### 2.2 原子需求

| ID | 原子需求 | 来源 |
| --- | --- | --- |
| `FR-001` | 提供只查询待审核凭证的分页列表。 | 需求 §2、§3.1 |
| `FR-002` | 提供单笔审核通过并进入 `20_REVIEW_APPROVED`。 | 需求 §2、§4 |
| `FR-003` | 提供单笔审核退回并进入 `30_REVIEW_REJECTED`。 | 需求 §2、§4 |
| `API-001` | 三个审核路径只支持 POST，GET 返回 HTTP 405。 | 需求 §3 |
| `API-002` | 列表支持日期两端可选、流水号精确匹配、分页默认值及边界。 | 需求 §3.1 |
| `API-003` | 列表拒绝 `workDate`，且开始日期不得晚于结束日期。 | 需求 §3.1 |
| `API-004` | 列表忽略客户端状态意图并强制 `10_PENDING_REVIEW`，查询仍受服务端 `branchNo` 限制。 | 需求 §3.1、§6 |
| `API-005` | 列表响应为 `pageNo/pageSize/total/records`，记录严格使用 API v0.5 的九个字段。 | 需求 §3.1；API §1、§4.8 |
| `API-006` | 审核动作的 `billId` 只来自 URL，不解析或映射请求 Body。 | 需求 §3.2 |
| `API-007` | 审核动作成功 `data` 只含六个摘要字段。 | 需求 §3.2 |
| `CTX-001` | `operatorNo`、`branchNo`、`requestId` 由 WebFE 生成/配置并覆盖客户端同名值。 | 需求 §6 |
| `BR-001` | 只有 `10_PENDING_REVIEW` 可审核；其他状态审核返回 `3004`。 | 需求 §4、§8 |
| `BR-002` | 被退回凭证经既有修改接口后回到待审核，并清空审核人和审核时间。 | 需求 §4、§9 |
| `BR-003` | 新代码不得写入 `00_DRAFT`，其他既有状态规则保持不变。 | 需求 §4 |
| `SVC-001` | 列表复用 `CNAPS4609Q`，动作只新增 `CNAPS5702A/R`，不新增 `CNAPS5702Q`。 | 需求 §5 |
| `SVC-002` | A/R 位于同一 `cnaps_review.c` 并共用状态变更函数。 | 需求 §5 |
| `DATA-001` | 审核成功按需求固定集合写入状态、审核与最后动作审计字段。 | 需求 §6 |
| `DATA-002` | 审核和最后动作时间由 Oracle `SYSTIMESTAMP` 产生，版本只加 1。 | 需求 §6、§7 |
| `TX-001` | 审核按读取、状态校验、版本更新、成功重读、提交/回滚的固定事务顺序执行。 | 需求 §7 |
| `CON-001` | 并发通过/退回只有一个成功，失败方返回 `3004`，最终版本只增加一次。 | 需求 §7 |
| `ERR-001` | 缺少编号、不存在、冲突和数据库错误分别返回规定业务码与 HTTP 状态。 | 需求 §8 |
| `ERR-002` | Tuxedo 超时/不可用沿用 `4002/4003` 和 HTTP 504/503。 | 需求 §8 |
| `SCP-001` | 不增加未授权功能、字段、服务、表、依赖或前端资源，不改查询 C 服务。 | 需求 §2、§5、§9；`AGENTS.md` |

## 3. 现状与需求差距

### 3.1 已核验的可复用能力

| 层次 | 当前事实及位置 | 可复用结论 |
| --- | --- | --- |
| Servlet 映射 | `web.xml` 已把 `/api/cnaps/vouchers` 和通配子路径交给 `CnapsVoucherServlet`。 | 无需新增 Servlet 或部署描述符。 |
| 请求 Body | `JsonSupport.readBodyMap` 能把无 Body、空 Body 解析为空 Map。 | 普通 POST 行为保留；审核动作将绕过 Body 解析。 |
| URL 编号 | `RequestSupport.includeBillPath` 已有从 `pathInfo` 取首段编号的模式。 | 增加动作专用的严格提取，不重写既有详情/修改/删除提取。 |
| 日期校验 | `RequestSupport.validateWorkDateFilter` 已拒绝 `workDate`、清理空边界、校验 ISO 日期和先后顺序。 | 审核列表直接复用。 |
| 可信上下文 | `BaseJsonServlet` 从 `TuxedoRuntimeConfig` 取得固定 `operatorNo/branchNo`，生成 `requestId`；`TuxedoRequestMapper.from` 最后覆盖为 `REQUEST_ID/REQ_ID/OPERATOR_NO/BRANCH_NO`。 | 客户端无法覆盖三项可信上下文。 |
| HTTP 错误映射 | `BaseJsonServlet.httpStatus` 已将 `2001/2002`、`3001`、`3004`、`4002`、`4003` 映射为 400、404、409、504、503；`4001` 落入 500。 | 不修改通用 HTTP 映射。 |
| 分页查询 | `CNAPS4609Q` 已支持日期范围、机构、状态、流水号和 `pageNo/pageSize`，Oracle 查询包含机构/状态谓词、总数统计及有序分页。 | 审核列表只需 WebFE 强制状态；C 查询和分页保持不变。 |
| 分页响应 | `JoltTuxedoClient` 已把 `CNAPS4609Q` 的重复字段组装为 `pageNo/pageSize/total/records`。 | 复用组装流程，只收敛记录字段集合。 |
| 状态 | `cnaps_status.h` 已定义四个现行状态；`validation_helper.c` 的 `cnaps_status_can_review` 已严格判断待审核。 | 新审核服务直接复用，无需新增状态。 |
| 退回后修改 | `cnaps_update.c` 已允许待审核/已退回修改，成功置为待审核，清空 `checker_no/checker_time`，动作置为 `UPDATE`。 | 已满足再提交闭环，不修改更新服务。 |
| 数据模型 | `T_CNAPS_BILL_POC` 已有全部状态、审核、最后动作、时间及 `VERSION_NO` 字段。 | 无 DDL、索引或迁移新增。 |
| 乐观更新 | `db_update_voucher` 已按 `BILL_ID + VERSION_NO` 更新并由 `execute_dml` 将 0 行返回为 `1`；OCI DML 使用非自动提交模式。 | 复用绑定、行数判定和事务函数，新增只更新审核列的专用 SQL。 |
| 重读与事务 | `cnaps_delete.c`/`cnaps_update.c` 已采用读取、`db_begin`、更新、重读、提交及失败回滚模式。 | 审核服务沿用处理顺序和错误返回方式。 |
| FML32 | 所有审核请求/响应字段已存在于 `cnaps_poc.fml32` 和 `cnaps_fields.h`；`fml_helper.c` 已有标量字符串/长整型写入函数。 | 不新增字段号，不修改 FML32 表和通用凭证输出。 |
| 状态迁移 | `sql/050_enable_voucher_review.sql` 已幂等迁移 `00_DRAFT` 为待审核并由 `init-db.sh` 引用。 | 不新增迁移；新审核实现不得出现草稿写入。 |
| Jolt 装载 | `load-jolt-metadata.sh` 在装载前删除 `CNAPS5702Q/A/R` 的旧元数据，然后装载 bulk 文件。 | 脚本无需修改；bulk 新增 A/R 后会被重新装载，Q 仍不会被重新加入。 |

### 3.2 当前差距

1. `CnapsVoucherServlet` 当前对三个审核 POST 路径直接返回 405，审核列表也未进入列表校验。
2. `TuxedoRequestMapper.serviceName` 没有审核列表和 A/R 动作映射；部分审核 GET 路径若绕过 Servlet 会误落到详情映射。
3. 审核列表尚无强制状态、字段白名单及 `pageNo/pageSize` 规则校验。
4. `JoltTuxedoClient` 的凭证列表字段集合大于 API v0.5 规定的九个字段，单笔响应读取也没有 A/R 摘要专用字段集合。
5. `MockTuxedoClient` 没有 A/R 服务或审核状态变化，列表记录也多于九个字段。
6. 原生层没有 `cnaps_review.c` 和审核专用数据库更新函数。
7. `cnapspocsvr.c`、`Makefile`、`UBBCONFIG` 及 Jolt bulk 尚未声明、构建、发布 A/R 服务。
8. 现有测试只验证审核路径被禁用和审核状态常量，未验证新闭环、响应字段、乐观冲突和服务注册。

### 3.3 文档和代码冲突处理

| 冲突 | 采用结论 | 依据及影响 |
| --- | --- | --- |
| `docs/cnaps-api-database.md` 仍描述请求头、审核意见、退回原因、录入审核分离、`3005` 和二级审核建议。 | 本设计全部排除，不读、不写、不校验这些字段和规则。 | 需求 v1.4 和前端 API v0.5 优先级更高，且明确将这些能力列为范围外。 |
| `cnaps_error.h` 的旧 `CNAPS_RESP_STATUS_NOT_ALLOWED` 值为 `3002`，而当前 CRUD 实际使用字符串 `3003`，审核要求 `3004`。 | 新审核服务沿现有业务服务风格直接返回需求规定的 `3004`；不借本任务重构旧错误常量。 | 避免改变无关 CRUD；HTTP 层已经支持 `3004`。 |
| 当前 Jolt/Mock 列表响应字段多于 API v0.5。 | 将列表客户端读取/Mock 输出收敛为 API 的九个字段；不改查询 C 和 Jolt 重复元数据。 | 这是满足 `API-005` 的必要契约修正，且 API v0.5 同时规定两个列表使用相同记录字段。 |

## 4. 总体方案和关键决策

### 4.1 调用链

```text
POST review-list
  -> CnapsVoucherServlet：校验、只保留允许条件、强制 STATUS
  -> TuxedoRequestMapper：CNAPS4609Q + 服务端上下文覆盖
  -> 现有 CNAPS4609Q / db_query_vouchers（不修改）
  -> JoltTuxedoClient：分页组装并只读取九个列表字段
  -> 现有 TuxedoResponseMapper / ApiResponse

POST {billId}/review-pass|review-return
  -> CnapsVoucherServlet：识别动作、忽略 Body、只取 URL billId
  -> TuxedoRequestMapper：CNAPS5702A|CNAPS5702R + 服务端上下文覆盖
  -> cnaps_review.c：两个导出入口调用同一 review_voucher
  -> db_find_voucher -> db_review_voucher(版本条件) -> db_find_voucher -> commit
  -> 六字段 FML32 摘要
  -> JoltTuxedoClient：A/R 专用六字段读取
  -> 现有 TuxedoResponseMapper / ApiResponse
```

### 4.2 关键决策

#### 决策 D-01：列表复用通用查询，但在 WebFE 固化审核语义

- 依据：`FR-001`、`API-004`、`SVC-001`，以及现有 `CNAPS4609Q` 已具备所有必要查询能力。
- 做法：审核列表先执行日期与分页校验，只保留 `startWorkDate/endWorkDate/serialNo/pageNo/pageSize`，再无条件写入 `status=10_PENDING_REVIEW`；`TuxedoRequestMapper.from` 最后写入服务端 `branchNo`。
- 影响：客户端传入 `status`、`branchNo` 或其他通用查询字段不会成为审核列表条件；未知字段因需求没有定义新错误码而忽略，不新增“未知字段”错误。
- 排除：不新增查询服务，不修改查询 C/SQL/分页，不允许客户端选择状态。

#### 决策 D-02：A/R 共用原生函数，动作参数由入口固定

- `CNAPS5702A` 只把目标状态/动作传为 `20_REVIEW_APPROVED`/`REVIEW_PASS`。
- `CNAPS5702R` 只把目标状态/动作传为 `30_REVIEW_REJECTED`/`REVIEW_RETURN`。
- 客户端没有目标状态、意见或原因的 FML32 输入入口。
- 两个入口调用同一静态函数，避免两套事务和错误分支漂移。

#### 决策 D-03：新增审核专用数据库更新，禁止复用全量 CRUD 更新 SQL

- 依据：审核只允许更新固定审计集合，且 `CHECKER_TIME` 必须由 Oracle 产生。
- 做法：在 `db_helper.c` 新增 `db_review_voucher(const cnaps_voucher_row *row)`，复用 `execute_dml` 的绑定、OCI 执行和受影响行数语义，但 SQL 只更新审核需要的列。
- 排除：不调用 `db_update_voucher`，避免重写全部业务字段，也避免把 C 格式化时间写回 `CHECKER_TIME`。

#### 决策 D-04：客户端层显式收敛响应字段

- `CNAPS4609Q` 的记录读取集合改为 API v0.5 的九个字段；原生查询仍可输出原有重复字段，Jolt 元数据不删字段、不改 `count=0`。
- `CNAPS5702A/R` 使用专用响应字段集合，只读取信封和六个摘要字段，防止请求缓冲区中的可信上下文泄漏进 `data`。
- `TuxedoResponseMapper` 已具备字段命名映射，无需改动。

#### 决策 D-05：动作不解析 Body，缺少路径编号由 WebFE 直接返回 `2001`

- Servlet 在调用 `JsonSupport.readBodyMap` 前识别审核动作。
- 动作字段 Map 从空 Map 开始，只加入严格动作路径中的 `billId`；任何 Body 均不读取、不映射。
- `/api/cnaps/vouchers/review-pass`、`/review-return` 或其他缺少编号的动作形式返回 HTTP 400、`2001`、`data=null`，不调用 Tuxedo。

## 5. 接口、字段和响应设计

### 5.1 待审核列表

```http
POST /api/cnaps/vouchers/review-list
Content-Type: application/json; charset=UTF-8
```

Body 可以为 `{}` 或下列字段：

| JSON 字段 | 类型 | 缺省 | 校验 | FML32/处理 |
| --- | --- | --- | --- | --- |
| `startWorkDate` | string | 不筛选下界 | 严格 `yyyy-MM-dd`，含当日 | `START_WORK_DATE` |
| `endWorkDate` | string | 不筛选上界 | 严格 `yyyy-MM-dd`，含当日；不得早于开始日 | `END_WORK_DATE` |
| `serialNo` | string | 不筛选 | 精确匹配 | `SERIAL_NO` |
| `pageNo` | JSON 整数 | 1 | 正整数；超出当前 C `int` 可表示范围返回 `2002`，避免现有查询静默回落为 1 | `PAGE_NO` |
| `pageSize` | JSON 整数 | 10 | 1～100，越界或非整数返回 `2002` | `PAGE_SIZE` |

附加规则：

- `workDate` 键即使为空或 `null` 也按现有规则返回 HTTP 400/`2002`。
- 校验通过后只保留表中五个输入字段，再强制加入 `status=10_PENDING_REVIEW`。
- 客户端 `status` 被覆盖；客户端 `branchNo/operatorNo/requestId` 或其他查询字段不转发。服务端 `branchNo` 由现有可信上下文注入。
- 缺省分页值不在 WebFE 人工填入，继续由 `CNAPS4609Q` 返回实际采用的 `1/10`。
- 查询顺序、日期谓词、总数和页读取继续使用现有 Oracle 实现。
- `voucherNo` 在数据库为空时返回空字符串，保证每条记录仍具有固定九个键；其他八项由现有非空业务/服务端字段产生。

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "query success",
  "data": {
    "pageNo": 1,
    "pageSize": 10,
    "total": 1,
    "records": [
      {
        "billId": "B202607137720002000",
        "workDate": "2026-07-13",
        "serialNo": "0002000",
        "voucherNo": "V001",
        "payeeAccountNo": "622200000000000001",
        "payeeName": "收款人名称",
        "amount": "5600.00",
        "status": "10_PENDING_REVIEW",
        "versionNo": 1
      }
    ]
  }
}
```

`records` 不返回完整凭证或审计字段；完整内容继续通过详情接口取得。

### 5.2 审核通过

```http
POST /api/cnaps/vouchers/{billId}/review-pass
```

- Body：无；Servlet 不解析、不映射传入内容。
- `billId`：只从 `{billId}` 路径段提取。
- 服务：`CNAPS5702A`。
- 固定目标：`20_REVIEW_APPROVED`。
- 固定动作：`REVIEW_PASS`。

### 5.3 审核退回

```http
POST /api/cnaps/vouchers/{billId}/review-return
```

- Body：无；Servlet 不解析、不映射传入内容。
- `billId`：只从 `{billId}` 路径段提取。
- 服务：`CNAPS5702R`。
- 固定目标：`30_REVIEW_REJECTED`。
- 固定动作：`REVIEW_RETURN`。

### 5.4 动作成功响应

两个动作的 `data` 键集合必须完全相同且只有下列六项：

| JSON 字段 | FML32 | 类型 | 来源 |
| --- | --- | --- | --- |
| `billId` | `BILL_ID` | string | 成功更新后重读记录 |
| `status` | `STATUS` | string | 成功更新后重读记录 |
| `checkerNo` | `CHECKER_NO` | string | WebFE `operatorNo` 写库后重读 |
| `checkerTime` | `CHECKER_TIME` | string | Oracle 时间，按现有查询格式 `yyyy-MM-dd HH:mm:ss` 返回 |
| `lastAction` | `LAST_ACTION` | string | 固定动作写库后重读 |
| `versionNo` | `VERSION_NO` | number | 原版本加 1 后重读 |

示例：

```json
{
  "respCode": "0000",
  "respMsg": "review pass success",
  "data": {
    "billId": "B202607137720002000",
    "status": "20_REVIEW_APPROVED",
    "checkerNo": "77210021",
    "checkerTime": "2026-07-21 10:20:30",
    "lastAction": "REVIEW_PASS",
    "versionNo": 2
  }
}
```

### 5.5 HTTP 方法

- 三个路径的 GET 均由 Servlet 在进入 Tuxedo 前返回 HTTP 405，保持当前 405 无业务响应体行为。
- 本设计不新增 PUT、DELETE 或其他审核方法。

## 6. 状态与业务规则

| 当前状态 | 动作 | 结果 | `lastAction` | 业务结果 |
| --- | --- | --- | --- | --- |
| `10_PENDING_REVIEW` | pass | `20_REVIEW_APPROVED` | `REVIEW_PASS` | 成功 |
| `10_PENDING_REVIEW` | return | `30_REVIEW_REJECTED` | `REVIEW_RETURN` | 成功 |
| `30_REVIEW_REJECTED` | 既有 update | `10_PENDING_REVIEW` | `UPDATE` | 保持既有实现 |
| 非 `10_PENDING_REVIEW` | pass/return | 不变 | 不变 | `3004` |
| 记录不存在 | pass/return | 无写入 | 无写入 | `3001` |

规则细化：

- 状态前置条件由 `cnaps_status_can_review(row.status)` 判定，不在新服务复制字符串比较规则。
- 重复提交相同审核动作不是幂等成功；第一次成功，后续请求返回 `3004`。
- 通过和退回互斥；获胜请求决定最终状态和审核审计，失败请求不得产生任何写入。
- 既有更新服务已在待审核或已退回修改后清空 `CHECKER_NO/CHECKER_TIME`；本任务只补充回归验证，不改其 SQL/代码。
- `00_DRAFT` 只允许出现在既有历史迁移脚本和相关验证中，新 Java/C/配置不得引用或写入该状态。

## 7. 数据、审计、事务和并发设计

### 7.1 审核更新集合

审核专用 SQL 只更新：

| 数据列 | 值/表达式 |
| --- | --- |
| `STATUS` | A 为 `20_REVIEW_APPROVED`；R 为 `30_REVIEW_REJECTED` |
| `CHECKER_NO` | WebFE `operatorNo` |
| `CHECKER_TIME` | `SYSTIMESTAMP` |
| `LAST_ACTION` | A 为 `REVIEW_PASS`；R 为 `REVIEW_RETURN` |
| `LAST_OPERATOR_NO` | WebFE `operatorNo` |
| `LAST_REQUEST_ID` | WebFE `requestId`（当前 C 模式读取同值的 `REQ_ID`） |
| `LAST_ACTION_TIME` | `SYSTIMESTAMP` |
| `UPDATED_AT` | `SYSTIMESTAMP` |
| `VERSION_NO` | `NVL(VERSION_NO, 1) + 1` |

更新谓词：

```sql
WHERE BILL_ID = :bill_id
  AND NVL(VERSION_NO, 1) = :version_no
```

其中 `:version_no` 取自本次服务初次读取的数据库记录，不接受客户端版本号。SQL 不更新业务字段、`BRANCH_NO`、录入人、删除字段、`REVIEW_COMMENT` 或 `REJECT_REASON`。

### 7.2 数据库函数语义

新增 `db_review_voucher(const cnaps_voucher_row *row)`：

- 通过既有 `execute_dml(sql, row, 1)` 绑定字段、执行 OCI DML 并读取 `OCI_ATTR_ROW_COUNT`。
- 返回 `0`：恰有记录满足版本条件并已更新。
- 返回 `1`：影响 0 行，解释为审核状态/版本并发变化，服务回滚并返回 `3004`。
- 返回负值：OCI/数据库错误，服务回滚并返回 `4001`。
- 不改变 `db_update_voucher` 的现有语义，避免影响修改和删除链路。

### 7.3 原生服务处理顺序

`review_voucher(rqst, serviceName, targetStatus, lastAction, successMessage)` 执行：

1. 从 FML32 读取 `BILL_ID`；为空返回 `2001`。
2. `db_find_voucher` 初读：返回 1 映射 `3001`，数据库失败映射 `4001`。
3. 调用 `cnaps_status_can_review`；非待审核返回 `3004`，尚未开始 DML。
4. 从 FML32 读取 WebFE 注入的 `OPERATOR_NO` 和 `REQ_ID`，填入 `checker_no/last_operator_no/last_request_id`，并设置目标状态和动作；保留初读的 `version_no`。
5. 调用 `db_begin`；失败返回 `4001`。
6. 调用 `db_review_voucher`：0 行则回滚并返回 `3004`；其他错误回滚并返回 `4001`。
7. 在同一事务中 `db_find_voucher` 重读；任何非 0 结果均回滚并返回 `4001`。
8. 调用 `db_commit`；失败时调用回滚并返回 `4001`。
9. 用既有 `cnaps_put_string/cnaps_put_long` 只写六个摘要字段，再用 `cnaps_return_response` 返回 `0000`。

### 7.4 并发语义

假设两个请求都先读到版本 `V`：

- Oracle 只允许第一个 `BILL_ID + VERSION_NO=V` 更新成功并把版本置为 `V+1`。
- 第二个更新在竞争结束后影响 0 行，回滚并返回 `3004`。
- 失败请求不再次判断目标状态，不把冲突降级为 `3001` 或 `4001`。
- 最终状态由唯一成功请求决定；`CHECKER_*`、`LAST_*` 及版本来自同一条成功 SQL。

当前 `UBBCONFIG` 将 `cnapspocsvr` 配置为 `MAX=1`，仓库默认运行形态会串行处理原生服务；SQL 的版本谓词仍是扩容或多实例情况下的并发正确性保障。真实性验证限制见 `TBD-01`。

### 7.5 机构和权限边界

- 列表继续用服务端 `BRANCH_NO` 作为查询谓词。
- 审核动作会收到服务端 `branchNo`，但需求未授权机构权限校验，审核 SQL 也不增加机构谓词。
- 不校验审核人与录入人是否相同，不新增 `3005`。

## 8. 分层调用链和逐文件修改方案

### 8.1 Java 生产代码

| 文件 | 修改锚点 | 执行级修改 | 保持不变项 |
| --- | --- | --- | --- |
| `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java` | `doGet`、`doPost`、列表路径/校验私有方法 | GET 继续拦截审核三路径；POST 在读 Body 前分流 A/R；动作只构造 URL 编号；把 `review-list` 纳入列表路径，复用日期校验，增加审核分页校验、字段收敛和固定状态；缺少动作编号直接写 `2001`。 | 创建、通用查询、详情、修改、删除路由和调用方式不重构。 |
| `web-fe/src/main/java/com/ruisui/cnaps/web/support/RequestSupport.java` | `includeBillPath`、日期校验附近 | 新增动作路径编号的严格提取/包含辅助方法；新增仅供审核列表使用的分页整数和范围校验，返回错误文本，由 Servlet 统一映射 `2002`。 | 既有 `includeBillPath` 和通用查询日期行为不变。 |
| `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java` | `serviceName` | 增加 `POST review-list -> CNAPS4609Q`、`POST */review-pass -> CNAPS5702A`、`POST */review-return -> CNAPS5702R`；在通用 GET 详情分支排除 review-list 和动作路径。 | `from` 的服务端上下文最终覆盖顺序、既有 CRUD 映射和字段命名不变。 |
| `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java` | 服务集合、`VOUCHER_FIELDS`、`readResponseFields` | 将分页凭证记录读取字段收敛为九项并把缺失的可选 `VOUCHER_NO` 规范为空字符串；增加 A/R 服务集合及 `RESP_CODE/RESP_MSG + 六字段` 的专用读取分支。 | 分页读取算法、最大 occurrence、数值转换、ApplicationException 和 4002/4003 处理不变。 |
| `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java` | 服务 switch、`voucherPage/listRecord`、生命周期方法 | 新增 A/R 分支和共用审核方法；审核仅待审核可执行，更新固定审计、版本并返回六字段摘要；对同一凭证的审核状态检查/更新采用同一同步临界区；列表记录显式复制九字段并保留空 `VOUCHER_NO`。 | 其他 Mock CRUD、字典、银行和查询筛选逻辑不重构。 |

无需修改：`BaseJsonServlet`、`JsonSupport`、`TuxedoResponseMapper`、`ApiResponse`、`TuxedoRuntimeConfig`、`web.xml`。现有能力已覆盖响应信封、字段转小驼峰、HTTP 错误和可信上下文。

### 8.2 Tuxedo C 和数据库访问

| 文件 | 修改锚点 | 执行级修改 | 保持不变项 |
| --- | --- | --- | --- |
| `tuxedo-server/include/cnaps_db.h` | `db_update_voucher` 声明附近 | 增加 `db_review_voucher(const cnaps_voucher_row *row)` 声明。 | `cnaps_voucher_row` 不增加成员；查询函数签名不变。 |
| `tuxedo-server/src/common/db_helper.c` | `db_update_voucher` 附近 | 局部新增审核专用 SQL 函数，使用 §7.1 的列、Oracle 时间和版本谓词，并复用 `execute_dml(..., 1)`。 | 不改通用更新、查询、选择列、绑定顺序和事务函数。 |
| `tuxedo-server/src/services/cnaps_review.c` | 新文件 | 定义 `CNAPS5702A`、`CNAPS5702R` 和一个静态共用审核函数；严格按 §7.3 执行；只用现有 FML32 标量写入函数输出六字段。 | 不接收意见、原因、目标状态或客户端版本。 |
| `tuxedo-server/src/cnapspocsvr.c` | 现有服务原型列表 | 增加 `CNAPS5702A/R` 原型。 | 初始化、Oracle 连接和其他服务原型不变。 |
| `tuxedo-server/Makefile` | `SERVICES` | 在现有服务列表局部追加 `CNAPS5702A CNAPS5702R`；新 C 文件由现有 `wildcard src/services/*.c` 自动纳入源码。 | 不直接改构建命令、库路径或头文件路径。 |

无需修改：`cnaps_status.h`、`validation_helper.c`、`cnaps_fields.h`、`cnaps_poc.fml32`、`cnaps_service.h`、`fml_helper.c`、`cnaps_query.c`、`cnaps_update.c`、`cnaps_delete.c`。它们分别已提供状态规则、字段和可复用的读写/事务模式。

### 8.3 服务和 Jolt 配置

| 文件 | 修改锚点 | 执行级修改 | 保持不变项 |
| --- | --- | --- | --- |
| `tuxedo/UBBCONFIG` | `*SERVICES` 末尾 | 追加 `CNAPS5702A`、`CNAPS5702R`。 | 不改变服务器数量、JSL、TMMETADATA 或其他服务。 |
| `tuxedo/jolt/cnaps_services.bulk` | `CNAPS5702I` 服务块之后 | 局部追加两个标量服务块：请求 `REQUEST_ID/REQ_ID/OPERATOR_NO/BRANCH_NO` 为 `in`，`BILL_ID` 为 `inout`；`STATUS/CHECKER_NO/CHECKER_TIME/LAST_ACTION/VERSION_NO` 为 `out`；信封为 `outerr`。 | 不修改 `CNAPS4609Q` 块，不改变任何现有重复字段和 `count=0`，不加入 `CNAPS5702Q`。 |

无需修改：`scripts/load-jolt-metadata.sh`。它已先删除 Q/A/R 旧定义再从 bulk 装载；新增 bulk 块后 A/R 会恢复，Q 不会恢复。

### 8.4 测试和验收资产

| 文件 | 计划修改 | 主要覆盖 |
| --- | --- | --- |
| `web-fe/src/test/java/com/ruisui/cnaps/web/support/RequestSupportTest.java` | 增加动作路径编号、缺失编号、审核分页合法/非法边界测试。 | `API-002/006` |
| `web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java` | 将审核 POST 从“禁用”改为实际路由测试；覆盖 GET 405、固定状态、服务端机构、Body 不映射、缺编号、日期/分页错误且不调用 Tuxedo。 | `API-001/003/004/006` |
| `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java` | 增加三个 POST 服务映射和审核 GET 不映射测试。 | `SVC-001` |
| `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/JoltPagedResponseTest.java` | 对记录键集合断言九字段，而非只断言其中三项。 | `API-005` |
| `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClientTest.java` | 增加 A/R 只读取六字段且仍发送服务端操作员的测试。 | `API-007`、`CTX-001` |
| `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientReviewTest.java` | 新增 Mock 生命周期测试：列表、退回、修改重入、通过、重复审核、两动作并发与版本。 | `FR-001/002/003`、`BR-001/002`、`CON-001` |
| `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java` | 静态断言新 C 服务共用函数、状态校验、事务顺序、专用 SQL、Oracle 时间、版本谓词、0 行到 3004、六字段输出和无草稿写入。 | `SVC-002`、`DATA-001/002`、`TX-001` |
| `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java` | 把 A/R 加入导出服务集合；断言 Makefile、UBBCONFIG、Jolt 块一致、动作参数为标量、Q 未注册且现有 `count=0` 保持。 | `SVC-001`、`SCP-001` |
| `scripts/smoke-test.sh` | 在保留现有 CRUD 冒烟的前提下，用独立凭证追加 review-list、return、update、pass、重复 pass 的真实链路断言。 | 完成标准主路径和重复审核 |

## 9. 错误码、异常和响应映射

| 场景 | 产生层 | `respCode` | HTTP | 数据/事务 |
| --- | --- | --- | ---: | --- |
| 成功 | C 服务 | `0000` | 200 | 列表页或六字段摘要 |
| 动作 URL 缺少 `billId` | WebFE Servlet | `2001` | 400 | `data=null`，不调用 Tuxedo |
| 日期格式、日期倒序、分页非整数/越界 | WebFE Servlet；C 保留二次日期防线 | `2002` | 400 | `data=null`，校验失败时不查库 |
| 凭证不存在 | C 初次读取 | `3001` | 404 | 无 DML |
| 初读状态非待审核 | C 状态校验 | `3004` | 409 | 无 DML |
| 乐观更新 0 行 | C + DB helper | `3004` | 409 | 回滚 |
| 数据库连接、读取、DML、重读或提交失败 | C | `4001` | 500 | DML 后异常均回滚 |
| Jolt 调用超时/反射调用失败 | `JoltTuxedoClient` | `4002` | 504 | `data=null` |
| Jolt 运行时缺失/服务不可用 | `JoltTuxedoClient` | `4003` | 503 | `data=null` |
| 未分类 WebFE 错误 | 现有通用路径 | `9999`/500 语义保持 | 500 | 不在本任务扩展 |

异常传递保持现状：C 使用 `cnaps_return_error(..., TPFAIL)` 写 `RESP_CODE/RESP_MSG`；Jolt 的 `ApplicationException` 分支读取应用错误对象；`TuxedoResponseMapper` 失败时输出 `data=null`；`BaseJsonServlet` 完成 HTTP 状态映射。

## 10. 幂等、安全和兼容性

- 重复审核：明确返回冲突，不做“已是目标状态则成功”的幂等放宽。
- 可信字段：动作 Body 完全不参与映射；列表只保留允许查询字段；Mapper 最后覆盖服务端上下文。
- 数据最小化：动作响应只暴露六字段，列表只暴露九字段；完整凭证仍走详情接口。
- 向后兼容：现有创建、修改、删除、详情、通用查询路径和服务名不变；列表字段收敛到已发布 API v0.5 契约。
- 部署兼容：不新增 FML32 字段号、表结构、依赖和环境变量；现有 metadata 装载脚本可处理新增服务块。
- POC 安全限制保持：无登录权限和录入审核分离；不把该限制包装成额外错误码。

## 11. 配置、构建和发布影响

### 11.1 配置一致性

实现后以下位置必须同时且仅出现 A/R：

```text
Java HTTP 映射：TuxedoRequestMapper
C 导出原型：cnapspocsvr.c
buildserver 服务：tuxedo-server/Makefile
运行注册：tuxedo/UBBCONFIG
Jolt 接口：tuxedo/jolt/cnaps_services.bulk
```

`CNAPS5702Q` 只能保留在元数据清理命令/禁止性测试语境中，不得出现在服务声明、Makefile、UBBCONFIG 或 Jolt service 块。

### 11.2 构建顺序

实现阶段必须按低成本到高成本执行：

1. 每次局部编辑后立即查看 `git diff -- <文件>`，发现无关行变化立即纠正。
2. `git diff --check`。
3. 用 `rg`/人工差异核对 A/R 在 Java、C、Makefile、UBBCONFIG、Jolt 中一致；确认无 `service=CNAPS5702Q`；确认现有 `count=0` 和 `CNAPS4609Q` 块未变化。
4. Java 涉及修改时只运行一次：`mvn -f web-fe/pom.xml clean package`。不得先执行跳过测试的构建。
5. 前述适用检查成功后，原生 C 恰好运行一次：`./scripts/build-c.sh`。不得直接调用 `make` 或 `buildserver`。

若 C 构建失败，只报告 `./scripts/build-c.sh` 的精确错误，不因编辑器缺少 Tuxedo/Oracle 头文件而修改 include 路径或业务代码。

### 11.3 发布影响

- 本设计不执行部署。
- 实际部署时沿用现有 Jolt metadata、TUXCONFIG 和 WebFE 发布流程；不增加脚本或环境变量。
- `sql/050_enable_voucher_review.sql` 已存在且已纳入初始化；本功能不要求再次新增数据库变更。

## 12. 测试、验证和验收方案

### 12.1 自动化验证

Maven 测试至少覆盖：

1. 三个 POST 路由和三个 GET 405。
2. 审核列表空条件、单端/双端日期、同日边界、错误日期、倒序日期、`workDate`、分页默认/最小/最大/非法值。
3. 客户端传 approved/rejected 状态、机构和上下文时，发给 Tuxedo 的值仍为待审核及服务端配置。
4. 列表 `records` 每项键集合严格为九项。
5. 动作只从路径取编号，Body 中伪造编号、状态、审核意见、退回原因不会进入 Tuxedo 请求。
6. 动作成功 `data` 键集合严格为六项。
7. Mock 审核通过、退回、退回后修改再审、重复审核、并发两动作及版本只加一。
8. C/SQL 契约和服务注册的静态一致性。
9. Jolt 应用错误、`4002` 和 `4003` 既有测试继续通过。

### 12.2 真实链路验收

在 WebFE 使用 Jolt、Tuxedo 连接 Oracle 的 POC 环境执行：

| 编号 | 场景 | 关键断言 |
| --- | --- | --- |
| `AT-01` | 创建后 review-list | 包含新凭证；所有记录状态均待审核；机构为服务端机构；键集合九项 |
| `AT-02` | review-list 强制状态 | Body 传其他状态仍只返回待审核；传 `workDate` 返回 400/2002 |
| `AT-03` | 分页与日期 | 默认 1/10，pageSize 1 和 100 成功，0/101/小数失败；日期两端包含 |
| `AT-04` | 审核通过 | HTTP 200/0000；状态、审核人/时间、动作、版本及数据库审计正确；响应六项 |
| `AT-05` | 审核退回 | HTTP 200/0000；状态和动作正确；不写意见/原因 |
| `AT-06` | 退回后修改 | 状态恢复待审核，审核人/时间清空，动作 UPDATE，版本再加一，可再次审核 |
| `AT-07` | 重复审核 | 第二次 HTTP 409/3004；数据库字段和版本不再变化 |
| `AT-08` | 不存在/缺编号 | 分别 HTTP 404/3001、HTTP 400/2001，`data=null` |
| `AT-09` | 同凭证并发 pass/return | 恰好一个 200/0000、一个 409/3004；最终状态与胜者一致；版本为初始值 +1 |
| `AT-10` | 数据库不可用 | Tuxedo 服务可达但 Oracle 操作失败时返回 500/4001，无部分更新 |
| `AT-11` | Tuxedo 超时/不可用 | 分别返回 504/4002 和 503/4003 |
| `AT-12` | 方法限制和范围 | 三个 GET 均 405；无 Q 服务、无前端资源、无 DDL/依赖新增 |

数据库核对需查询同一 `BILL_ID` 的：`STATUS`、`CHECKER_NO`、`CHECKER_TIME`、`LAST_ACTION`、`LAST_OPERATOR_NO`、`LAST_REQUEST_ID`、`LAST_ACTION_TIME`、`UPDATED_AT`、`VERSION_NO`；同时确认未授权业务列及意见/原因未被审核动作改变。

### 12.3 完成判定

- 所有自动化测试和规定构建成功。
- `AT-01`～`AT-12` 达到预期；受 `TBD-01` 限制时必须如实记录并发验证层级。
- Java/C/配置/Jolt 服务名和字段方向一致。
- `git diff --check` 通过，Jolt 既有重复字段和 `count=0` 无变化。
- 差异中没有 `CNAPS5702Q` 服务、前端资源、DDL、新依赖、审核意见/原因或其他范围外内容。

## 13. 需求追踪矩阵

| 需求 | 设计章节 | 修改位置 | 验证方式 | 状态 |
| --- | --- | --- | --- | --- |
| `FR-001` 待审核分页列表 | §4.1、§5.1 | Servlet、RequestMapper、JoltClient、Mock | 单元测试；`AT-01`～`AT-03` | 已覆盖 |
| `FR-002` 单笔审核通过 | §5.2、§6、§7 | Servlet、RequestMapper、`cnaps_review.c`、DB helper | Mock/C 契约；`AT-04` | 已覆盖 |
| `FR-003` 单笔审核退回 | §5.3、§6、§7 | Servlet、RequestMapper、`cnaps_review.c`、DB helper | Mock/C 契约；`AT-05` | 已覆盖 |
| `API-001` 仅 POST/GET 405 | §5.5 | `CnapsVoucherServlet` | Servlet 测试；`AT-12` | 已覆盖 |
| `API-002` 日期/流水/分页 | §5.1 | Servlet、RequestSupport、现有 CNAPS4609Q | 边界单测；`AT-03` | 已覆盖 |
| `API-003` 拒绝 workDate/日期倒序 | §5.1、§9 | RequestSupport、Servlet | RequestSupport/Servlet 测试；`AT-02` | 已覆盖 |
| `API-004` 固定状态与机构 | §4.2 D-01、§5.1、§7.5 | Servlet、现有 Mapper/查询 SQL | 捕获 Tuxedo 请求；`AT-01/02` | 已覆盖 |
| `API-005` 分页结构与九字段 | §4.2 D-04、§5.1 | JoltClient、Mock | 精确键集合测试；`AT-01` | 已覆盖 |
| `API-006` URL billId/无 Body | §4.2 D-05、§5.2/5.3 | Servlet、RequestSupport | Body 伪造/缺编号单测；`AT-08` | 已覆盖 |
| `API-007` 六字段动作摘要 | §5.4 | `cnaps_review.c`、JoltClient、Mock | 精确键集合测试；`AT-04/05` | 已覆盖 |
| `CTX-001` 服务端上下文覆盖 | §3.1、§5.1、§7.1 | 现有 BaseServlet/Mapper；Servlet 收敛 | Mapper/Servlet 测试；审计数据库核对 | 已覆盖 |
| `BR-001` 仅待审核/3004 | §6、§7.3 | 现有状态 helper、`cnaps_review.c` | 状态决策表测试；`AT-07/09` | 已覆盖 |
| `BR-002` 退回修改重入并清审核 | §6 | 既有 `cnaps_update.c`；回归测试 | Mock 生命周期；`AT-06` | 已覆盖，无生产修改 |
| `BR-003` 无草稿写入/既有状态不变 | §1.4、§6 | 新 C/Java；既有状态文件不改 | `rg`/C 契约/差异检查 | 已覆盖 |
| `SVC-001` Q 复用、只新增 A/R | §4.2、§8.3、§11.1 | Mapper、server、Makefile、UBB、Jolt | DeploymentArtifactTest；服务名结构检查 | 已覆盖 |
| `SVC-002` A/R 共用 C 函数 | §4.2 D-02、§7.3 | 新 `cnaps_review.c` | C 源码契约；C 构建 | 已覆盖 |
| `DATA-001` 固定审核/审计更新集合 | §7.1 | DB helper、`cnaps_review.c` | SQL 静态断言；数据库核对 | 已覆盖 |
| `DATA-002` Oracle 时间/版本 +1 | §7.1/7.2 | DB helper | `SYSTIMESTAMP`/版本 SQL 断言；`AT-04/05/09` | 已覆盖 |
| `TX-001` 事务、回滚和重读 | §7.2/7.3 | DB helper、`cnaps_review.c` | 调用顺序契约；错误注入；`AT-10` | 已覆盖 |
| `CON-001` 并发唯一成功 | §7.4、§12.2 | DB helper 版本谓词、C 冲突映射、Mock 同步 | 并发单测；`AT-09`；`TBD-01` | 设计已覆盖，环境待确认 |
| `ERR-001` 2001/3001/3004/4001 | §9 | Servlet、C 服务、现有 HTTP 映射 | 错误路径单测；`AT-07/08/10` | 已覆盖 |
| `ERR-002` 4002/4003 | §9 | 现有 JoltClient/BaseServlet，无生产修改 | 既有 Jolt 测试；`AT-11` | 已覆盖 |
| `SCP-001` 范围和禁止项 | §1.3/1.4、§8、§12.3、§15 | 全部差异 | `git diff`、`rg`、部署契约测试 | 已覆盖 |

正向追踪：每个原子需求均有设计、修改位置和验证方式。<br>
反向追踪：§8 中每个计划修改均对应上述需求；未引入无来源服务、字段、依赖或状态。

## 14. 风险、假设、冲突与 TBD

### 14.1 风险

| 风险 | 控制 |
| --- | --- |
| 审核动作复用全量更新 SQL导致业务字段被并发覆盖 | 使用只更新审核列的 `db_review_voucher`。 |
| C 已输出或请求缓冲区残留字段导致动作响应超出六项 | C 只写摘要，Jolt 再用动作专用读取字段集合双重收敛。 |
| 审核列表被客户端状态或其他通用查询字段缩小/扩大 | Servlet 校验后字段白名单化并最后固定状态；Mapper 最后覆盖机构。 |
| Jolt bulk 局部修改破坏重复字段 | 不改 CNAPS4609Q 块；差异和测试逐项确认 `count=0`。 |
| 并发失败被误报不存在或数据库错误 | 初读不存在才 3001；版本更新 0 行固定 3004；OCI 异常才 4001。 |
| 当前单实例配置掩盖真实数据库并发等待行为 | SQL 版本条件、Mock 并发测试和并发 HTTP 验收分层验证；见 `TBD-01`。 |

### 14.2 假设

- `TuxedoRuntimeConfig` 的现有非空默认值/部署配置继续提供 `operatorNo` 和 `branchNo`；本任务不新增配置校验规则。
- 所有写生命周期继续遵守版本递增约定。当前创建、修改、删除和草稿迁移均已核验满足该约定。
- API 的 `respMsg` 不是前端分支判断字段；业务判断以 HTTP 状态和 `respCode` 为准。成功文案按现有 C 英文动作风格使用 `review pass success`/`review return success`。

### 14.3 TBD

- `TBD-01`：当前已提交的 `UBBCONFIG` 对业务服务器设置 `MAX=1`，同时发送两个 HTTP 请求能验证“一个成功、一个 3004”的外部结果，但不能证明两个原生进程在 Oracle 行更新上的真实竞争。是否允许在独立验收环境临时以多实例配置执行并发测试需由环境负责人确认；未经授权不修改仓库 `UBBCONFIG`。该项不阻塞代码实现和乐观锁设计，但必须在验收报告中注明实际验证层级。

## 15. 明确不修改项

实现本设计时以下文件/行为保持不变，除非后续需求另行授权：

- `docs/cnaps-frontend-api.md`、`docs/cnaps-api-database.md` 及本次需求文档。
- `web-fe/pom.xml`、`web.xml`、所有 JSP/JS/CSS/前端资源（仓库当前也不存在这些资源）。
- `BaseJsonServlet` 的响应信封和 HTTP 错误映射。
- `TuxedoResponseMapper` 的现有 FML32 到 JSON 命名规则。
- `tuxedo-server/src/services/cnaps_query.c` 和 `db_query_vouchers` 的查询/分页算法。
- 创建、修改、删除、详情服务的既有业务规则；仅对更新后的“退回再修改”行为做回归验证。
- `cnaps_status.h`、`validation_helper.c` 的现有状态常量和校验函数。
- `cnaps_fields.h`、`cnaps_poc.fml32`、`fml_helper.c` 的字段定义和通用输出函数。
- 所有 SQL DDL、索引、历史迁移及数据库列。
- `scripts/load-jolt-metadata.sh` 的先删后装流程和所有现有 `count=0`。
- Tuxedo/Oracle include 路径、构建工具、运行用户、环境变量和依赖版本。
