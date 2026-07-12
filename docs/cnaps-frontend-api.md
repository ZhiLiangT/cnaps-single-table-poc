# CNAPS 单表 POC HTTP API 文档

版本：v0.3
版本日期：2026-07-12

本文档是 `cnaps-single-table-poc` 当前唯一的公共 HTTP API 契约。WebFE/Tomcat WAR 通过 Jolt 调用 Tuxedo 服务，Tuxedo 服务访问 Oracle 单表 `T_CNAPS_BILL_POC`。

## 1. 基础约定

### 1.1 Base URL

```text
http://localhost:8080/ruisui-bank-sim
```

### 1.2 Content-Type

所有带 JSON Body 的请求必须使用：

```http
Content-Type: application/json; charset=UTF-8
```

### 1.3 请求上下文与工作日期

公共 API 不接收业务请求头。客户端不传请求流水、操作员、机构或工作日期请求头。

- WebFE 为每次调用生成内部请求流水。
- 固定 POC 操作员来自 `webfe.poc.operatorNo` 或 `POC_OPERATOR_NO`，默认 `77210021`。
- 固定 POC 机构来自 `webfe.poc.branchNo` 或 `POC_BRANCH_NO`，默认 `772`。
- 这些服务器端值只用于兼容 FML32 和单表审计字段，不代表登录身份或权限，Body、Query 和 Header 均不能覆盖。
- `workDate` 是公开业务字段：创建时必填；修改时可选；通用查询和待复核查询中为可选 Query 参数。

创建和修改的日期格式为 `yyyy-MM-dd`。查询中的工作日期过滤；未传时默认当前日期。单据详情、删除、复核通过和复核退回只需要 Path 中的 `billId`，不要求工作日期。

### 1.4 通用响应包络

所有响应的顶层只包含 `respCode`、`respMsg` 和 `data`：

```json
{
  "respCode": "0000",
  "respMsg": "操作已成功",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `respCode` | string | 业务响应码，`0000` 表示成功 |
| `respMsg` | string | 响应说明 |
| `data` | object / array / null | 业务数据；失败时为 `null` |

### 1.5 分页约定

公开分页请求和响应只使用 `pageNo`、`pageSize`、`total` 和 `records`。

| 字段 | 类型 | 默认值 | 说明 |
|---|---:|---:|---|
| `pageNo` | number | `1` | 页码，从 1 开始 |
| `pageSize` | number | `10` | 每页条数 |
| `total` | number | - | 总记录数 |
| `records` | array | - | 当前页记录 |

## 2. 接口清单

| HTTP API | Tuxedo 服务 | 说明 |
|---|---|---|
| `GET /api/health` | `SYSHEALTH` | 健康检查 |
| `GET /api/dicts/{dictType}` | `DICTQRY` | 字典查询 |
| `GET /api/banks` | `BANKQRY` | 行号查询 |
| `POST /api/cnaps/vouchers` | `CNAPS5701E` | 单据录入 |
| `PUT /api/cnaps/vouchers/{billId}` | `CNAPS5701U` | 单据修改 |
| `POST /api/cnaps/vouchers/{billId}/delete` | `CNAPS5701D` | 逻辑删除 |
| `GET /api/cnaps/vouchers` | `CNAPS4609Q` | 通用查询 |
| `GET /api/cnaps/vouchers/review-list` | `CNAPS5702Q` | 待复核查询 |
| `GET /api/cnaps/vouchers/{billId}` | `CNAPS5702I` | 单据详情 |
| `POST /api/cnaps/vouchers/{billId}/review-pass` | `CNAPS5702A` | 复核通过 |
| `POST /api/cnaps/vouchers/{billId}/review-return` | `CNAPS5702R` | 复核退回 |

## 3. 状态模型

| 状态 | 说明 | 允许操作 |
|---|---|---|
| `10_PENDING_REVIEW` | 待复核 | 修改、删除、复核通过、复核退回 |
| `20_REVIEW_APPROVED` | 已复核通过 | 查询 |
| `30_REVIEW_REJECTED` | 已复核退回 | 修改、删除、查询 |
| `40_DELETED` | 已逻辑删除 | 查询（需 `includeDeleted=true`） |

创建成功进入 `10_PENDING_REVIEW`。修改仅允许原状态为 `10_PENDING_REVIEW` 或 `30_REVIEW_REJECTED`，成功后回到 `10_PENDING_REVIEW`。删除仅允许这两个状态。复核仅允许 `10_PENDING_REVIEW`。

## 4. API 详情

### 4.1 健康检查

```http
GET /api/health
```

请求示例：

```bash
curl "http://localhost:8080/ruisui-bank-sim/api/health"
```

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "health check success",
  "data": {
    "service": "SYSHEALTH",
    "webfe": "UP",
    "tuxedo": "UP",
    "oracle": "UP"
  }
}
```

### 4.2 字典查询

```http
GET /api/dicts/{dictType}
```

常用 `dictType`：`BUSINESS_TYPE`、`PRIORITY`、`SYSTEM_TYPE`、`DEBIT_MODE`、`FEE_CHARGE_MODE`、`SEND_MODE`、`FAX_FLAG`。

请求示例：

```bash
curl "http://localhost:8080/ruisui-bank-sim/api/dicts/BUSINESS_TYPE"
```

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "查询成功",
  "data": [
    {
      "dictType": "BUSINESS_TYPE",
      "dictCode": "02102",
      "dictName": "普通汇兑",
      "sortNo": 1
    }
  ]
}
```

### 4.3 行号查询

```http
GET /api/banks
```

Query 参数：

| 参数 | 类型 | 必输 | 示例 | 说明 |
|---|---:|:---:|---|---|
| `bankNo` | string | 否 | `102290000002` | 行号精确查询 |
| `keyword` | string | 否 | `接收行` | 行名或行号关键字 |
| `city` | string | 否 | `上海` | 城市 |
| `systemType` | string | 否 | `CNAPS` | 系统类型 |
| `pageNo` | number | 否 | `1` | 页码 |
| `pageSize` | number | 否 | `10` | 每页条数 |

请求示例：

```bash
curl "http://localhost:8080/ruisui-bank-sim/api/banks?systemType=CNAPS&keyword=%E6%8E%A5%E6%94%B6%E8%A1%8C&pageNo=1&pageSize=10"
```

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "查询成功",
  "data": {
    "pageNo": 1,
    "pageSize": 10,
    "total": 1,
    "records": [
      {
        "bankNo": "102290000002",
        "bankName": "CNAPS receiving bank",
        "city": "上海",
        "systemType": "CNAPS"
      }
    ]
  }
}
```

### 4.4 单据录入

```http
POST /api/cnaps/vouchers
```

核心 Body 字段：

| 字段 | 类型 | 必输 | 默认值 | 说明 |
|---|---:|:---:|---|---|
| `workDate` | string | 是 | - | 工作日期，创建时必填 |
| `businessType` | string | 是 | `02102` | 业务种类 |
| `accountPart1` | string | 是 | - | 付款账号一段 |
| `accountPart2` | string | 是 | - | 付款账号二段 |
| `accountPart3` | string | 是 | - | 付款账号三段 |
| `payeeAccountNo` | string | 是 | - | 收款账号 |
| `payeeName` | string | 是 | - | 收款人名称 |
| `priority` | string | 是 | `NORM` | 优先级 |
| `systemType` | string | 是 | `CNAPS` | 系统类型 |
| `amount` | string | 是 | - | 大于 0，最多两位小数 |
| `feeAmount` | string | 否 | `0.00` | 不小于 0 |
| `debitMode` | string | 否 | `1` | 扣收方式 |
| `feeChargeMode` | string | 否 | `1` | 手续费方式 |
| `sendMode` | string | 否 | `0` | 发送方式 |
| `faxFlag` | string | 否 | `0` | 传真标志 |

请求示例：

```bash
curl -X POST "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "workDate": "2026-07-07",
    "businessType": "02102",
    "accountPart1": "404045",
    "accountPart2": "00772",
    "accountPart3": "000000000001",
    "accountName": "付款账户户名",
    "payerName": "付款人名称",
    "payeeAccountNo": "622200000000000001",
    "payeeName": "收款人名称",
    "priority": "NORM",
    "receiveBankNo": "102290000002",
    "receiveBankName": "接收行名称",
    "systemType": "CNAPS",
    "amount": "5600.00",
    "debitMode": "1",
    "feeAmount": "0.00",
    "feeChargeMode": "1",
    "sendMode": "0",
    "faxFlag": "0",
    "voucherNo": "PZ202607070001",
    "remark": "验证录入"
  }'
```

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "录入成功，待复核",
  "data": {
    "billId": "B202607070000001",
    "serialNo": "0002000",
    "workDate": "2026-07-07",
    "status": "10_PENDING_REVIEW",
    "amount": "5600.00",
    "versionNo": 1
  }
}
```

### 4.5 单据修改

```http
PUT /api/cnaps/vouchers/{billId}
```

Body 可传创建字段；`workDate` 修改时可选，未传时保持原值。修改 `workDate` 不会重新生成 `billId` 或 `serialNo`，两者保持不变。

请求示例：

```bash
curl -X PUT "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607070000001" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "workDate": "2026-07-08",
    "payeeAccountNo": "622200000000000001",
    "payeeName": "收款人名称-修改后",
    "amount": "5800.00",
    "remark": "退回后修改"
  }'
```

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "修改成功，待复核",
  "data": {
    "billId": "B202607070000001",
    "serialNo": "0002000",
    "workDate": "2026-07-08",
    "status": "10_PENDING_REVIEW",
    "amount": "5800.00",
    "versionNo": 2
  }
}
```

### 4.6 单据删除

```http
POST /api/cnaps/vouchers/{billId}/delete
```

`deleteReason` 为可选 JSON Body 字段。

请求示例：

```bash
curl -X POST "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607070000001/delete" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"deleteReason":"录入错误"}'
```

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "删除成功",
  "data": {
    "billId": "B202607070000001",
    "status": "40_DELETED",
    "deleteReason": "录入错误"
  }
}
```

### 4.7 通用查询

```http
GET /api/cnaps/vouchers
```

Query 参数：

| 参数 | 类型 | 必输 | 示例 | 说明 |
|---|---:|:---:|---|---|
| `workDate` | string | 否 | `2026-07-07` | 工作日期过滤；未传时默认当前日期。 |
| `status` | string | 否 | `10_PENDING_REVIEW` | 单据状态 |
| `serialNo` | string | 否 | `0002000` | 流水号 |
| `voucherNo` | string | 否 | `PZ202607070001` | 凭证号 |
| `payeeName` | string | 否 | `收款人` | 收款人名称模糊查询 |
| `payeeAccountNo` | string | 否 | `622200000000000001` | 收款账号 |
| `includeDeleted` | boolean | 否 | `false` | 是否包含逻辑删除记录，默认 `false` |
| `pageNo` | number | 否 | `1` | 页码 |
| `pageSize` | number | 否 | `10` | 每页条数 |

请求示例：

```bash
curl "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers?workDate=2026-07-07&status=10_PENDING_REVIEW&pageNo=1&pageSize=10"
```

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "查询成功",
  "data": {
    "pageNo": 1,
    "pageSize": 10,
    "total": 1,
    "records": [
      {
        "billId": "B202607070000001",
        "workDate": "2026-07-07",
        "serialNo": "0002000",
        "voucherNo": "PZ202607070001",
        "payeeAccountNo": "622200000000000001",
        "payeeName": "收款人名称",
        "amount": "5600.00",
        "status": "10_PENDING_REVIEW"
      }
    ]
  }
}
```

默认不返回 `40_DELETED`；只有 `includeDeleted=true` 时才包含逻辑删除记录。

### 4.8 待复核查询

```http
GET /api/cnaps/vouchers/review-list
```

Query 参数：

| 参数 | 类型 | 必输 | 示例 | 说明 |
|---|---:|:---:|---|---|
| `workDate` | string | 否 | `2026-07-07` | 工作日期过滤；未传时默认当前日期。 |
| `serialNo` | string | 否 | `0002000` | 流水号 |
| `pageNo` | number | 否 | `1` | 页码 |
| `pageSize` | number | 否 | `10` | 每页条数 |

请求示例：

```bash
curl "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/review-list?workDate=2026-07-07&pageNo=1&pageSize=10"
```

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "查询成功",
  "data": {
    "pageNo": 1,
    "pageSize": 10,
    "total": 1,
    "records": [
      {
        "billId": "B202607070000001",
        "serialNo": "0002000",
        "workDate": "2026-07-07",
        "payeeName": "收款人名称",
        "payeeAccountNo": "622200000000000001",
        "amount": "5600.00",
        "status": "10_PENDING_REVIEW"
      }
    ]
  }
}
```

### 4.9 单据详情

```http
GET /api/cnaps/vouchers/{billId}
```

请求示例：

```bash
curl "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607070000001"
```

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "查询成功",
  "data": {
    "billId": "B202607070000001",
    "workDate": "2026-07-07",
    "serialNo": "0002000",
    "businessType": "02102",
    "accountPart1": "404045",
    "accountPart2": "00772",
    "accountPart3": "000000000001",
    "payeeAccountNo": "622200000000000001",
    "payeeName": "收款人名称",
    "priority": "NORM",
    "systemType": "CNAPS",
    "amount": "5600.00",
    "status": "10_PENDING_REVIEW",
    "versionNo": 1
  }
}
```

不存在时返回 `3001`。

### 4.10 复核通过

```http
POST /api/cnaps/vouchers/{billId}/review-pass
```

`reviewComment` 为可选 JSON Body 字段。

请求示例：

```bash
curl -X POST "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607070000001/review-pass" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"reviewComment":"复核通过"}'
```

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "操作已成功",
  "data": {
    "billId": "B202607070000001",
    "serialNo": "0002000",
    "amount": "5600.00",
    "status": "20_REVIEW_APPROVED"
  }
}
```

本 POC 使用服务器端固定操作员，不校验复核人与录入人是否相同。同一固定 POC 操作员可以录入并复核同一单据，审计字段中的操作员值不代表登录身份。

### 4.11 复核退回

```http
POST /api/cnaps/vouchers/{billId}/review-return
```

`rejectReason` 必填，`reviewComment` 可选。

请求示例：

```bash
curl -X POST "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607070000002/review-return" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "rejectReason": "收款人户名不完整",
    "reviewComment": "请修改后重新提交"
  }'
```

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "复核退回成功",
  "data": {
    "billId": "B202607070000002",
    "serialNo": "0002001",
    "amount": "5600.00",
    "status": "30_REVIEW_REJECTED",
    "rejectReason": "收款人户名不完整"
  }
}
```

退回原因缺失时返回 `2001`。身份简化规则与复核通过一致。

## 5. 单据字段字典

| JSON 字段 | 类型 | 创建必输 | 说明 |
|---|---:|:---:|---|
| `billId` | string | 响应生成 | 单据编号 |
| `workDate` | string | 是 | 业务日期，创建时必填，修改时可选 |
| `serialNo` | string | 响应生成 | 流水号 |
| `businessType` | string | 是 | 业务种类 |
| `accountPart1` | string | 是 | 付款账号一段 |
| `accountPart2` | string | 是 | 付款账号二段 |
| `accountPart3` | string | 是 | 付款账号三段 |
| `accountName` | string | 否 | 付款账户户名 |
| `payerName` | string | 否 | 付款人名称 |
| `payeeAccountNo` | string | 是 | 收款账号 |
| `payeeName` | string | 是 | 收款人名称 |
| `priority` | string | 是 | 优先级 |
| `receiveBankNo` | string | 否 | 接收行号 |
| `receiveBankName` | string | 否 | 接收行名称 |
| `systemType` | string | 是 | 系统类型 |
| `amount` | string | 是 | 金额，大于 0 且最多两位小数 |
| `debitMode` | string | 否 | 扣收方式 |
| `feeAmount` | string | 否 | 手续费，不小于 0 |
| `feeChargeMode` | string | 否 | 手续费方式 |
| `sendMode` | string | 否 | 发送方式 |
| `faxFlag` | string | 否 | 传真标志 |
| `voucherNo` | string | 否 | 凭证号 |
| `remark` | string | 否 | 备注 |
| `status` | string | 响应返回 | 单据状态 |
| `reviewComment` | string | 否 | 复核意见 |
| `rejectReason` | string | 复核退回必输 | 退回原因 |
| `deleteReason` | string | 否 | 删除原因 |
| `versionNo` | number | 响应返回 | 版本号 |

## 6. 错误码

错误响应也只包含三字段包络，`data` 为 `null`。

| 错误码 | HTTP 状态 | 含义 | 典型场景 |
|---|---:|---|---|
| `0000` | 200 | 成功 | 交易成功 |
| `2001` | 400 | 必填字段缺失 | 创建缺少必填项、复核退回缺少原因 |
| `2002` | 400 | 字段格式错误 | 日期或金额格式错误 |
| `2003` | 400 | 字典值无效 | 业务种类、优先级等无效 |
| `3001` | 404 | 单据不存在 | 详情、修改、删除或复核找不到单据 |
| `3003` | 409 | 当前状态不允许操作 | 已复核单据删除、已删除单据修改 |
| `3004` | 409 | 复核时单据状态已变化 | 重复复核或并发复核 |
| `4001` | 500 | 数据库错误 | Oracle 连接或 SQL/OCI 执行失败 |
| `4002` | 504 | Tuxedo 服务超时 | WebFE 调用超时 |
| `4003` | 503 | Tuxedo 服务不可用 | 服务未启动或路由失败 |
| `9999` | 500 | 未分类错误 | 未分类异常 |

失败示例：

```json
{
  "respCode": "4003",
  "respMsg": "Tuxedo 服务不可用：CNAPS5701E",
  "data": null
}
```

## 7. 典型联调流程

### 7.1 录入后复核通过

```text
POST /api/cnaps/vouchers
GET  /api/cnaps/vouchers/review-list
GET  /api/cnaps/vouchers/{billId}
POST /api/cnaps/vouchers/{billId}/review-pass
GET  /api/cnaps/vouchers/{billId}
```

预期状态：`10_PENDING_REVIEW` → `20_REVIEW_APPROVED`。

### 7.2 录入后复核退回并修改

```text
POST /api/cnaps/vouchers
POST /api/cnaps/vouchers/{billId}/review-return
PUT  /api/cnaps/vouchers/{billId}
GET  /api/cnaps/vouchers/{billId}
```

预期状态：`10_PENDING_REVIEW` → `30_REVIEW_REJECTED` → `10_PENDING_REVIEW`。

### 7.3 逻辑删除

```text
POST /api/cnaps/vouchers
POST /api/cnaps/vouchers/{billId}/delete
GET  /api/cnaps/vouchers?includeDeleted=true&pageNo=1&pageSize=10
```

预期状态：`10_PENDING_REVIEW` → `40_DELETED`。

## 8. POC 边界

- 不提供登录、账号、角色、权限或操作员切换。
- 固定操作员和机构只用于兼容现有 FML32 与审计字段。
- 不做真实扣账、真实 CNAPS 发送或复杂审计。
- Native Tuxedo/OCI 的编译和部署需要配置好的 Linux/Tuxedo/Oracle 环境，不属于 HTTP 文档契约。
