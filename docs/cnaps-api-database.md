# CNAPS 单表 POC API 与数据库表结构文档

版本日期：2026-07-08

适用范围：当前 `cnaps-single-table-poc` Spring Boot POC 实现。

## 1. 系统说明

当前项目提供 CNAPS 往账凭证单表 POC，运行形态为 Spring Boot HTTP JSON API，默认端口 `8080`，默认使用 H2 内存数据库的 Oracle 兼容模式进行本地验证。

当前数据库业务表为单表：

```text
T_CNAPS_BILL_POC
```

当前服务清单保留 Tuxedo 风格服务名，但运行时由 Spring Boot Service 层模拟交易服务行为。

## 2. 基础约定

### 2.1 Base URL

```text
http://localhost:8080
```

### 2.2 Content-Type

有请求体的接口统一使用：

```http
Content-Type: application/json
```

### 2.3 公共请求头

凭证类接口支持以下可选请求头。CRUD 验证场景可以不传，WebFE 会使用默认上下文：

| Header | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `requestId` | 否 | `REQ-202607080001` | 请求流水号；缺省生成 `REQ-<timestamp>`。也支持 `X-Request-Id` 作为备用头。 |
| `operatorNo` | 否 | `77210021` | 操作员号；缺省为 `77210021`。 |
| `branchNo` | 否 | `772` | 机构号；缺省为 `772`。 |
| `workDate` | 否 | `2026-07-08` | 工作日期，格式 `yyyy-MM-dd`；缺省为当前日期。 |
| `channel` | 否 | `WEBFE` | 渠道，缺省为 `WEBFE`。当前响应不直接返回该字段。 |

以下参考类接口也不要求公共请求头：

- `GET /api/health`
- `GET /api/dicts/{dictType}`
- `GET /api/banks`

### 2.4 公共响应结构

```json
{
  "respCode": "0000",
  "respMsg": "success",
  "requestId": "REQ-202607080001",
  "serverTime": "2026-07-08T09:30:00+08:00",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `respCode` | string | 响应码。成功为 `0000`。 |
| `respMsg` | string | 响应消息。 |
| `requestId` | string | 请求流水号。参考类接口可能为空。 |
| `serverTime` | string | 服务端响应时间，ISO Offset DateTime。 |
| `data` | object / array / null | 业务数据。失败时为 `null`。 |

### 2.5 响应码

| 响应码 | HTTP 状态 | 含义 |
| --- | --- | --- |
| `0000` | 200 | 成功。 |
| `2001` | 400 | 必填字段为空。 |
| `2002` | 400 | 字段格式错误。 |
| `2003` | 400 | 字典值非法。 |
| `3001` | 404 | 数据不存在。 |
| `3003` | 409 | 当前状态不允许该操作。 |
| `3004` | 409 | 复核时状态已变化或非待复核。 |
| `3005` | 403 | 操作员不能复核本人经办凭证。 |
| `4001` | 500 | 数据库或持久化异常。 |
| `4002` | 预留 | Tuxedo 超时。当前 POC 仅保留错误码枚举。 |
| `4003` | 预留 | Tuxedo 不可用。当前 POC 仅保留错误码枚举。 |
| `9999` | 500 | 未知异常。 |

## 3. 字典与枚举

### 3.1 业务字典

| 字典类型 | code | name |
| --- | --- | --- |
| `BUSINESS_TYPE` | `02102` | 普通汇兑 |
| `PRIORITY` | `NORM` | 普通 |
| `FEE_CHARGE_MODE` | `1` | 同城收费 |
| `SEND_MODE` | `0` | 柜面 |
| `DEBIT_MODE` | `1` | 扣收 |
| `FAX_FLAG` | `0` | 否 |
| `FAX_FLAG` | `1` | 是 |
| `SYSTEM_TYPE` | `CNAPS` | CNAPS |

### 3.2 银行信息

| bankNo | bankName |
| --- | --- |
| `102290000002` | 接收行名称 |

### 3.3 凭证状态

| 状态码 | 说明 |
| --- | --- |
| `10_PENDING_REVIEW` | 待复核 |
| `20_REVIEW_APPROVED` | 复核通过 |
| `30_REVIEW_REJECTED` | 复核退回 |
| `40_DELETED` | 已删除 |

### 3.4 最后动作

| 动作码 | 说明 |
| --- | --- |
| `CREATE` | 录入 |
| `UPDATE` | 修改 |
| `DELETE` | 删除 |
| `REVIEW_PASS` | 复核通过 |
| `REVIEW_RETURN` | 复核退回 |

## 4. 公共数据结构

### 4.1 VoucherCreateRequest

录入和修改接口共用该请求体。

| 字段 | 类型 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| `businessType` | string | 是 | `02102` | 业务类型。 |
| `accountPart1` | string | 是 | `404045` | 付款账号组成部分 1。 |
| `accountPart2` | string | 是 | `00772` | 付款账号组成部分 2。 |
| `accountPart3` | string | 是 | `000000000001` | 付款账号组成部分 3。 |
| `accountName` | string | 否 | `付款账户户名` | 付款账户户名。 |
| `payerName` | string | 否 | `付款人名称` | 付款人名称。 |
| `payeeAccountNo` | string | 是 | `622200000000000001` | 收款账号。 |
| `payeeName` | string | 是 | `收款人名称` | 收款人名称。 |
| `priority` | string | 是 | `NORM` | 优先级。 |
| `receiveBankNo` | string | 否 | `102290000002` | 接收行行号。 |
| `receiveBankName` | string | 否 | `接收行名称` | 接收行名称。 |
| `systemType` | string | 是 | `CNAPS` | 系统类型。 |
| `amount` | string | 是 | `5600.00` | 金额，必须大于 0，最多两位小数。 |
| `debitMode` | string | 是 | `1` | 扣款模式。 |
| `feeAmount` | string | 否 | `0.00` | 手续费金额，空值按 `0` 处理。 |
| `feeChargeMode` | string | 是 | `1` | 收费方式。 |
| `sendMode` | string | 是 | `0` | 发送方式。 |
| `faxFlag` | string | 是 | `0` | 传真标志。 |
| `voucherNo` | string | 否 | `PZ202607080001` | 凭证号。 |
| `remark` | string | 否 | `验证录入` | 备注。 |

必填字段缺失或空字符串返回 `2001`。字典值不在支持范围内返回 `2003`。金额格式错误、金额小于等于 0、金额超过两位小数返回 `2002`。

### 4.2 VoucherResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `billId` | string | 凭证主键，格式为 `B` + `yyyyMMdd` + `branchNo` + `serialNo`。 |
| `serialNo` | string | 机构工作日内流水号，当前起始参考值为 `0002000`。 |
| `status` | string | 凭证状态。 |
| `lastAction` | string | 最后动作。 |
| `businessType` | string | 业务类型。 |
| `accountPart1` | string | 付款账号组成部分 1。 |
| `accountPart2` | string | 付款账号组成部分 2。 |
| `accountPart3` | string | 付款账号组成部分 3。 |
| `accountName` | string | 付款账户户名。 |
| `payerName` | string | 付款人名称。 |
| `payeeAccountNo` | string | 收款账号。 |
| `payeeName` | string | 收款人名称。 |
| `priority` | string | 优先级。 |
| `receiveBankNo` | string | 接收行行号。 |
| `receiveBankName` | string | 接收行名称。 |
| `systemType` | string | 系统类型。 |
| `amount` | string | 金额。 |
| `debitMode` | string | 扣款模式。 |
| `feeAmount` | string | 手续费金额。 |
| `feeChargeMode` | string | 收费方式。 |
| `sendMode` | string | 发送方式。 |
| `faxFlag` | string | 传真标志。 |
| `voucherNo` | string | 凭证号。 |
| `remark` | string | 备注。 |
| `operatorNo` | string | 经办操作员。 |
| `branchNo` | string | 机构号。 |
| `lastOperatorNo` | string | 最后操作员。 |
| `lastRequestId` | string | 最后请求流水号。 |
| `workDate` | string | 工作日期，格式 `yyyy-MM-dd`。 |
| `versionNo` | number | 版本号，创建为 `1`，后续生命周期动作递增。 |
| `rejectReason` | string | 退回原因。为空时不返回。 |
| `deleteOperatorNo` | string | 删除操作员。为空时不返回。 |
| `deleteTime` | string | 删除时间。为空时不返回。 |
| `checkerNo` | string | 复核员。为空时不返回。 |
| `checkerTime` | string | 复核时间。为空时不返回。 |
| `reviewComment` | string | 复核意见。为空时不返回。 |

## 5. API 接口

### 5.1 健康检查

```http
GET /api/health
```

说明：返回系统状态和模拟 Tuxedo 服务名列表。

成功响应示例：

```json
{
  "respCode": "0000",
  "respMsg": "success",
  "requestId": null,
  "serverTime": "2026-07-08T09:30:00+08:00",
  "data": {
    "status": "UP",
    "services": [
      "SYSHEALTH",
      "DICTQRY",
      "BANKQRY",
      "CNAPS5701E",
      "CNAPS5701U",
      "CNAPS5701D",
      "CNAPS4609Q",
      "CNAPS5702Q",
      "CNAPS5702I",
      "CNAPS5702A",
      "CNAPS5702R"
    ]
  }
}
```

### 5.2 字典查询

```http
GET /api/dicts/{dictType}
```

路径参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `dictType` | 是 | `BUSINESS_TYPE` | 字典类型。 |

成功响应示例：

```json
{
  "respCode": "0000",
  "respMsg": "success",
  "requestId": null,
  "serverTime": "2026-07-08T09:30:00+08:00",
  "data": [
    {
      "code": "02102",
      "name": "普通汇兑"
    }
  ]
}
```

异常：

| 场景 | HTTP 状态 | respCode |
| --- | --- | --- |
| 字典类型不存在 | 404 | `3001` |

### 5.3 银行查询

```http
GET /api/banks?bankNo={bankNo}&keyword={keyword}
```

查询参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `bankNo` | 否 | `102290000002` | 按行号精确匹配。 |
| `keyword` | 否 | `接收` | 按银行名称包含匹配。 |

成功响应示例：

```json
{
  "respCode": "0000",
  "respMsg": "success",
  "requestId": null,
  "serverTime": "2026-07-08T09:30:00+08:00",
  "data": [
    {
      "bankNo": "102290000002",
      "bankName": "接收行名称"
    }
  ]
}
```

### 5.4 凭证录入

```http
POST /api/cnaps/vouchers
```

请求头：见“公共请求头”。

请求体示例：

```json
{
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
  "voucherNo": "PZ202607080001",
  "remark": "验证录入"
}
```

成功行为：

- 生成 `serialNo`。
- 生成 `billId`。
- 状态置为 `10_PENDING_REVIEW`。
- `lastAction` 置为 `CREATE`。
- `versionNo` 置为 `1`。

成功响应示例：

```json
{
  "respCode": "0000",
  "respMsg": "success",
  "requestId": "REQ-CREATE-001",
  "serverTime": "2026-07-08T09:30:00+08:00",
  "data": {
    "billId": "B202607087720002000",
    "serialNo": "0002000",
    "status": "10_PENDING_REVIEW",
    "lastAction": "CREATE",
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
    "voucherNo": "PZ202607080001",
    "remark": "验证录入",
    "operatorNo": "77210021",
    "branchNo": "772",
    "lastOperatorNo": "77210021",
    "lastRequestId": "REQ-CREATE-001",
    "workDate": "2026-07-08",
    "versionNo": 1
  }
}
```

### 5.5 经办凭证查询

```http
GET /api/cnaps/vouchers?status={status}&operatorNo={operatorNo}&serialNo={serialNo}&page={page}&size={size}
```

请求头：见“公共请求头”。

查询参数：

| 参数 | 必填 | 默认值 | 示例 | 说明 |
| --- | --- | --- | --- | --- |
| `status` | 否 | 无 | `10_PENDING_REVIEW` | 按状态过滤。 |
| `operatorNo` | 否 | 无 | `77210021` | 按经办操作员过滤。 |
| `serialNo` | 否 | 无 | `0002000` | 按流水号过滤。 |
| `page` | 否 | `0` | `0` | 页码，小于 0 时按 0 处理。 |
| `size` | 否 | `10` | `10` | 每页条数，小于等于 0 时按 10，最大 50。 |

查询范围默认使用 WebFE 内部上下文中的 `workDate` 和 `branchNo`；需要指定时可通过请求头覆盖。

成功响应 `data` 为分页对象，核心结构如下：

```json
{
  "respCode": "0000",
  "respMsg": "success",
  "requestId": "REQ-QRY-001",
  "serverTime": "2026-07-08T09:30:00+08:00",
  "data": {
    "content": [
      {
        "billId": "B202607087720002000",
        "serialNo": "0002000",
        "status": "10_PENDING_REVIEW"
      }
    ],
    "page": {
      "size": 10,
      "number": 0,
      "totalElements": 1,
      "totalPages": 1
    }
  }
}
```

### 5.6 复核列表查询

```http
GET /api/cnaps/vouchers/review-list?page={page}&size={size}
```

请求头：见“公共请求头”。

说明：等价于按 `status=10_PENDING_REVIEW` 查询当前机构、当前工作日的待复核列表。

查询参数：

| 参数 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | 否 | `0` | 页码。 |
| `size` | 否 | `10` | 每页条数，最大 50。 |

响应结构同“经办凭证查询”。

### 5.7 凭证详情

```http
GET /api/cnaps/vouchers/{billId}
```

请求头：见“公共请求头”。

路径参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `billId` | 是 | `B202607087720002000` | 凭证编号。 |

说明：默认只查询 WebFE 内部上下文 `branchNo` 和 `workDate` 范围内的凭证；需要指定时可通过请求头覆盖。

异常：

| 场景 | HTTP 状态 | respCode |
| --- | --- | --- |
| 凭证不存在 | 404 | `3001` |
| 凭证不属于请求机构或工作日 | 404 | `3001` |

### 5.8 凭证修改

```http
PUT /api/cnaps/vouchers/{billId}
```

请求头：见“公共请求头”。

请求体：同 `VoucherCreateRequest`。

允许状态：

- `10_PENDING_REVIEW`
- `30_REVIEW_REJECTED`

成功行为：

- 更新提交字段。
- 状态置为 `10_PENDING_REVIEW`。
- `lastAction` 置为 `UPDATE`。
- `versionNo` 加 1。
- 清空退回原因、复核意见、复核员、复核时间、删除信息。

异常：

| 场景 | HTTP 状态 | respCode |
| --- | --- | --- |
| 凭证不存在 | 404 | `3001` |
| 当前状态不可修改 | 409 | `3003` |
| 请求体字段校验失败 | 400 | `2001` / `2002` / `2003` |

### 5.9 凭证删除

```http
POST /api/cnaps/vouchers/{billId}/delete
```

请求头：见“公共请求头”。

请求体：

```json
{
  "deleteReason": "录入有误"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `deleteReason` | string | 否 | 删除原因。当前实现不做必填校验。 |

允许状态：

- `10_PENDING_REVIEW`
- `30_REVIEW_REJECTED`

成功行为：

- 状态置为 `40_DELETED`。
- `lastAction` 置为 `DELETE`。
- 记录 `deleteReason`、`deleteOperatorNo`、`deleteTime`。
- `versionNo` 加 1。

异常：

| 场景 | HTTP 状态 | respCode |
| --- | --- | --- |
| 凭证不存在 | 404 | `3001` |
| 当前状态不可删除 | 409 | `3003` |

### 5.10 复核通过

```http
POST /api/cnaps/vouchers/{billId}/review-pass
```

请求头：见“公共请求头”。

请求体：

```json
{
  "reviewComment": "复核通过"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `reviewComment` | string | 否 | 复核意见。 |

允许状态：

- `10_PENDING_REVIEW`

成功行为：

- 状态置为 `20_REVIEW_APPROVED`。
- `lastAction` 置为 `REVIEW_PASS`。
- 记录 `checkerNo`、`checkerTime`、`reviewComment`。
- 清空 `rejectReason`。
- `versionNo` 加 1。

异常：

| 场景 | HTTP 状态 | respCode |
| --- | --- | --- |
| 凭证不存在 | 404 | `3001` |
| 经办操作员复核本人凭证 | 403 | `3005` |
| 非待复核状态 | 409 | `3004` |

### 5.11 复核退回

```http
POST /api/cnaps/vouchers/{billId}/review-return
```

请求头：见“公共请求头”。

请求体：

```json
{
  "rejectReason": "收款人信息需修正"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `rejectReason` | string | 是 | 退回原因，长度不超过 200。 |

允许状态：

- `10_PENDING_REVIEW`

成功行为：

- 状态置为 `30_REVIEW_REJECTED`。
- `lastAction` 置为 `REVIEW_RETURN`。
- 记录 `checkerNo`、`checkerTime`、`rejectReason`。
- 清空 `reviewComment`。
- `versionNo` 加 1。

异常：

| 场景 | HTTP 状态 | respCode |
| --- | --- | --- |
| 凭证不存在 | 404 | `3001` |
| `rejectReason` 为空 | 400 | `2001` |
| `rejectReason` 超过 200 字符 | 400 | `2002` |
| 经办操作员复核本人凭证 | 403 | `3005` |
| 非待复核状态 | 409 | `3004` |

### 5.12 二次复核步骤

说明：本节为二次复核目标流程说明。当前 Spring Boot POC 仅实现单级复核接口 `review-pass` 和 `review-return`，尚未实现独立的一次复核、二次复核接口和数据库字段。

建议二次复核采用以下处理步骤：

1. 经办录入凭证。
   - 调用 `POST /api/cnaps/vouchers`。
   - 初始状态为 `10_PENDING_REVIEW`，表示待一次复核。

2. 一次复核通过。
   - 建议新增接口：`POST /api/cnaps/vouchers/{billId}/first-review-pass`。
   - 允许状态：`10_PENDING_REVIEW`。
   - 一次复核员不能等于经办操作员。
   - 成功后状态变为 `15_PENDING_SECOND_REVIEW`，表示待二次复核。
   - 记录一次复核员、一次复核时间、一次复核意见。

3. 一次复核退回。
   - 建议新增接口：`POST /api/cnaps/vouchers/{billId}/first-review-return`。
   - 允许状态：`10_PENDING_REVIEW`。
   - 一次复核员不能等于经办操作员。
   - 成功后状态变为 `30_REVIEW_REJECTED`。
   - 记录退回原因。

4. 二次复核通过。
   - 建议新增接口：`POST /api/cnaps/vouchers/{billId}/second-review-pass`。
   - 允许状态：`15_PENDING_SECOND_REVIEW`。
   - 二次复核员不能等于经办操作员。
   - 二次复核员不能等于一次复核员。
   - 成功后状态变为 `20_REVIEW_APPROVED`。
   - 记录二次复核员、二次复核时间、二次复核意见。

5. 二次复核退回。
   - 建议新增接口：`POST /api/cnaps/vouchers/{billId}/second-review-return`。
   - 允许状态：`15_PENDING_SECOND_REVIEW`。
   - 二次复核员不能等于经办操作员。
   - 二次复核员不能等于一次复核员。
   - 成功后状态变为 `30_REVIEW_REJECTED`。
   - 记录退回原因。

建议新增状态码：

| 状态码 | 说明 |
| --- | --- |
| `15_PENDING_SECOND_REVIEW` | 待二次复核 |

建议新增动作码：

| 动作码 | 说明 |
| --- | --- |
| `FIRST_REVIEW_PASS` | 一次复核通过 |
| `FIRST_REVIEW_RETURN` | 一次复核退回 |
| `SECOND_REVIEW_PASS` | 二次复核通过 |
| `SECOND_REVIEW_RETURN` | 二次复核退回 |

建议新增业务错误：

| 场景 | HTTP 状态 | respCode |
| --- | --- | --- |
| 一次复核员等于经办操作员 | 403 | `3005` |
| 二次复核员等于经办操作员 | 403 | `3005` |
| 二次复核员等于一次复核员 | 403 | 建议新增 `3006` |
| 非待一次复核状态执行一次复核 | 409 | `3004` |
| 非待二次复核状态执行二次复核 | 409 | `3004` |

## 6. 生命周期状态流转

### 6.1 当前 POC 单级复核流程

```text
POST /api/cnaps/vouchers
  -> 10_PENDING_REVIEW

PUT /api/cnaps/vouchers/{billId}
  10_PENDING_REVIEW 或 30_REVIEW_REJECTED
  -> 10_PENDING_REVIEW

POST /api/cnaps/vouchers/{billId}/review-pass
  10_PENDING_REVIEW
  -> 20_REVIEW_APPROVED

POST /api/cnaps/vouchers/{billId}/review-return
  10_PENDING_REVIEW
  -> 30_REVIEW_REJECTED

POST /api/cnaps/vouchers/{billId}/delete
  10_PENDING_REVIEW 或 30_REVIEW_REJECTED
  -> 40_DELETED
```

复核通过和复核退回均禁止经办操作员本人执行。

### 6.2 二次复核目标流程

```text
POST /api/cnaps/vouchers
  -> 10_PENDING_REVIEW

POST /api/cnaps/vouchers/{billId}/first-review-pass
  10_PENDING_REVIEW
  -> 15_PENDING_SECOND_REVIEW

POST /api/cnaps/vouchers/{billId}/first-review-return
  10_PENDING_REVIEW
  -> 30_REVIEW_REJECTED

POST /api/cnaps/vouchers/{billId}/second-review-pass
  15_PENDING_SECOND_REVIEW
  -> 20_REVIEW_APPROVED

POST /api/cnaps/vouchers/{billId}/second-review-return
  15_PENDING_SECOND_REVIEW
  -> 30_REVIEW_REJECTED

PUT /api/cnaps/vouchers/{billId}
  30_REVIEW_REJECTED
  -> 10_PENDING_REVIEW

POST /api/cnaps/vouchers/{billId}/delete
  10_PENDING_REVIEW、15_PENDING_SECOND_REVIEW 或 30_REVIEW_REJECTED
  -> 40_DELETED
```

二次复核人员约束：

- 一次复核员不能等于经办操作员。
- 二次复核员不能等于经办操作员。
- 二次复核员不能等于一次复核员。

## 7. 数据库表结构

### 7.1 表名

```text
T_CNAPS_BILL_POC
```

### 7.2 字段说明

| 字段名 | 类型 | 是否必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `BILL_ID` | `VARCHAR2(32)` | 是 | 无 | 主键，凭证编号。 |
| `WORK_DATE` | `DATE` | 是 | 无 | 工作日期。 |
| `BRANCH_NO` | `VARCHAR2(12)` | 是 | 无 | 机构号。 |
| `OPERATOR_NO` | `VARCHAR2(16)` | 是 | 无 | 经办操作员。 |
| `SERIAL_NO` | `VARCHAR2(16)` | 是 | 无 | 机构工作日内流水号。 |
| `BUSINESS_TYPE` | `VARCHAR2(12)` | 是 | 无 | 业务类型。 |
| `ACCOUNT_PART1` | `VARCHAR2(32)` | 否 | 无 | 付款账号组成部分 1。 |
| `ACCOUNT_PART2` | `VARCHAR2(32)` | 否 | 无 | 付款账号组成部分 2。 |
| `ACCOUNT_PART3` | `VARCHAR2(64)` | 否 | 无 | 付款账号组成部分 3。 |
| `ACCOUNT_NAME` | `VARCHAR2(128)` | 否 | 无 | 付款账户户名。 |
| `PAYER_NAME` | `VARCHAR2(128)` | 否 | 无 | 付款人名称。 |
| `PAYEE_ACCOUNT_NO` | `VARCHAR2(64)` | 是 | 无 | 收款账号。 |
| `PAYEE_NAME` | `VARCHAR2(128)` | 是 | 无 | 收款人名称。 |
| `PRIORITY` | `VARCHAR2(12)` | 否 | 无 | 优先级。 |
| `RECEIVE_BANK_NO` | `VARCHAR2(32)` | 否 | 无 | 接收行行号。 |
| `RECEIVE_BANK_NAME` | `VARCHAR2(128)` | 否 | 无 | 接收行名称。 |
| `SYSTEM_TYPE` | `VARCHAR2(16)` | 否 | 无 | 系统类型。 |
| `AMOUNT` | `NUMBER(18,2)` | 是 | 无 | 交易金额。 |
| `DEBIT_MODE` | `VARCHAR2(8)` | 否 | 无 | 扣款模式。 |
| `FEE_AMOUNT` | `NUMBER(18,2)` | 否 | `0` | 手续费金额。 |
| `FEE_CHARGE_MODE` | `VARCHAR2(8)` | 否 | 无 | 收费方式。 |
| `SEND_MODE` | `VARCHAR2(8)` | 否 | 无 | 发送方式。 |
| `FAX_FLAG` | `VARCHAR2(1)` | 否 | 无 | 传真标志。 |
| `VOUCHER_NO` | `VARCHAR2(64)` | 否 | 无 | 凭证号。 |
| `REMARK` | `VARCHAR2(512)` | 否 | 无 | 备注。 |
| `STATUS` | `VARCHAR2(32)` | 是 | 无 | 凭证状态。 |
| `CHECKER_NO` | `VARCHAR2(16)` | 否 | 无 | 复核员。 |
| `CHECKER_TIME` | `TIMESTAMP` | 否 | 无 | 复核时间。 |
| `REVIEW_COMMENT` | `VARCHAR2(512)` | 否 | 无 | 复核意见。 |
| `REJECT_REASON` | `VARCHAR2(200)` | 否 | 无 | 退回原因。 |
| `DELETE_REASON` | `VARCHAR2(200)` | 否 | 无 | 删除原因。 |
| `DELETE_OPERATOR_NO` | `VARCHAR2(16)` | 否 | 无 | 删除操作员。 |
| `DELETE_TIME` | `TIMESTAMP` | 否 | 无 | 删除时间。 |
| `LAST_ACTION` | `VARCHAR2(32)` | 否 | 无 | 最后动作。 |
| `LAST_OPERATOR_NO` | `VARCHAR2(16)` | 否 | 无 | 最后操作员。 |
| `LAST_REQUEST_ID` | `VARCHAR2(32)` | 否 | 无 | 最后请求流水号。 |
| `LAST_ACTION_TIME` | `TIMESTAMP` | 否 | 无 | 最后动作时间。 |
| `CREATED_AT` | `TIMESTAMP` | 否 | `SYSTIMESTAMP` | 创建时间。 |
| `UPDATED_AT` | `TIMESTAMP` | 否 | `SYSTIMESTAMP` | 更新时间。 |
| `VERSION_NO` | `NUMBER(10)` | 否 | `1` | 版本号。 |

注意：应用层对部分字段的必填校验比 DDL 更严格。例如 `ACCOUNT_PART1`、`ACCOUNT_PART2`、`ACCOUNT_PART3`、`PRIORITY`、`SYSTEM_TYPE`、`DEBIT_MODE`、`FEE_CHARGE_MODE`、`SEND_MODE`、`FAX_FLAG` 在 DDL 中允许为空，但录入和修改接口要求必填。

### 7.3 索引

| 索引名 | 类型 | 字段 | 说明 |
| --- | --- | --- | --- |
| `PK` | 主键 | `BILL_ID` | 凭证唯一主键。 |
| `UK_CNAPS_BILL_POC_SERIAL` | 唯一索引 | `WORK_DATE, BRANCH_NO, SERIAL_NO` | 保证同一工作日同一机构流水号唯一。 |
| `IDX_CNAPS_BILL_POC_QRY` | 普通索引 | `WORK_DATE, BRANCH_NO, STATUS, OPERATOR_NO, SERIAL_NO` | 支持经办和复核列表查询。 |

### 7.4 Oracle DDL

```sql
CREATE TABLE T_CNAPS_BILL_POC (
  BILL_ID VARCHAR2(32) PRIMARY KEY,
  WORK_DATE DATE NOT NULL,
  BRANCH_NO VARCHAR2(12) NOT NULL,
  OPERATOR_NO VARCHAR2(16) NOT NULL,
  SERIAL_NO VARCHAR2(16) NOT NULL,
  BUSINESS_TYPE VARCHAR2(12) NOT NULL,
  ACCOUNT_PART1 VARCHAR2(32),
  ACCOUNT_PART2 VARCHAR2(32),
  ACCOUNT_PART3 VARCHAR2(64),
  ACCOUNT_NAME VARCHAR2(128),
  PAYER_NAME VARCHAR2(128),
  PAYEE_ACCOUNT_NO VARCHAR2(64) NOT NULL,
  PAYEE_NAME VARCHAR2(128) NOT NULL,
  PRIORITY VARCHAR2(12),
  RECEIVE_BANK_NO VARCHAR2(32),
  RECEIVE_BANK_NAME VARCHAR2(128),
  SYSTEM_TYPE VARCHAR2(16),
  AMOUNT NUMBER(18,2) NOT NULL,
  DEBIT_MODE VARCHAR2(8),
  FEE_AMOUNT NUMBER(18,2) DEFAULT 0,
  FEE_CHARGE_MODE VARCHAR2(8),
  SEND_MODE VARCHAR2(8),
  FAX_FLAG VARCHAR2(1),
  VOUCHER_NO VARCHAR2(64),
  REMARK VARCHAR2(512),
  STATUS VARCHAR2(32) NOT NULL,
  CHECKER_NO VARCHAR2(16),
  CHECKER_TIME TIMESTAMP,
  REVIEW_COMMENT VARCHAR2(512),
  REJECT_REASON VARCHAR2(200),
  DELETE_REASON VARCHAR2(200),
  DELETE_OPERATOR_NO VARCHAR2(16),
  DELETE_TIME TIMESTAMP,
  LAST_ACTION VARCHAR2(32),
  LAST_OPERATOR_NO VARCHAR2(16),
  LAST_REQUEST_ID VARCHAR2(32),
  LAST_ACTION_TIME TIMESTAMP,
  CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP,
  UPDATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP,
  VERSION_NO NUMBER(10) DEFAULT 1
);

CREATE UNIQUE INDEX UK_CNAPS_BILL_POC_SERIAL
  ON T_CNAPS_BILL_POC(WORK_DATE, BRANCH_NO, SERIAL_NO);

CREATE INDEX IDX_CNAPS_BILL_POC_QRY
  ON T_CNAPS_BILL_POC(WORK_DATE, BRANCH_NO, STATUS, OPERATOR_NO, SERIAL_NO);
```

### 7.5 二次复核字段扩展建议

当前 `T_CNAPS_BILL_POC` 只有一组复核字段：

```text
CHECKER_NO
CHECKER_TIME
REVIEW_COMMENT
```

如果要实现二次复核，建议将复核字段拆成一次复核和二次复核两组：

| 字段名 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- |
| `FIRST_CHECKER_NO` | `VARCHAR2(16)` | 否 | 一次复核员。 |
| `FIRST_CHECKER_TIME` | `TIMESTAMP` | 否 | 一次复核时间。 |
| `FIRST_REVIEW_COMMENT` | `VARCHAR2(512)` | 否 | 一次复核意见。 |
| `SECOND_CHECKER_NO` | `VARCHAR2(16)` | 否 | 二次复核员。 |
| `SECOND_CHECKER_TIME` | `TIMESTAMP` | 否 | 二次复核时间。 |
| `SECOND_REVIEW_COMMENT` | `VARCHAR2(512)` | 否 | 二次复核意见。 |

建议 DDL：

```sql
ALTER TABLE T_CNAPS_BILL_POC ADD (
  FIRST_CHECKER_NO VARCHAR2(16),
  FIRST_CHECKER_TIME TIMESTAMP,
  FIRST_REVIEW_COMMENT VARCHAR2(512),
  SECOND_CHECKER_NO VARCHAR2(16),
  SECOND_CHECKER_TIME TIMESTAMP,
  SECOND_REVIEW_COMMENT VARCHAR2(512)
);
```

兼容策略：

- 若仅保留单级复核，继续使用 `CHECKER_NO`、`CHECKER_TIME`、`REVIEW_COMMENT`。
- 若启用二次复核，新增字段保存分级复核信息，原字段可作为最终复核摘要字段保留。
- 二次复核上线前，需要同步更新实体类、响应 DTO、复核接口和状态流转测试。

## 8. 调用示例

### 8.1 创建凭证

```bash
curl -X POST http://localhost:8080/api/cnaps/vouchers \
  -H "Content-Type: application/json" \
  -d '{
    "businessType":"02102",
    "accountPart1":"404045",
    "accountPart2":"00772",
    "accountPart3":"000000000001",
    "accountName":"付款账户户名",
    "payerName":"付款人名称",
    "payeeAccountNo":"622200000000000001",
    "payeeName":"收款人名称",
    "priority":"NORM",
    "receiveBankNo":"102290000002",
    "receiveBankName":"接收行名称",
    "systemType":"CNAPS",
    "amount":"5600.00",
    "debitMode":"1",
    "feeAmount":"0.00",
    "feeChargeMode":"1",
    "sendMode":"0",
    "faxFlag":"0",
    "voucherNo":"PZ202607080001",
    "remark":"验证录入"
  }'
```

### 8.2 查询凭证

```bash
curl "http://localhost:8080/api/cnaps/vouchers?status=10_PENDING_REVIEW&page=0&size=10"
```

### 8.3 复核通过

```bash
curl -X POST http://localhost:8080/api/cnaps/vouchers/{billId}/review-pass \
  -H "Content-Type: application/json" \
  -d '{"reviewComment":"复核通过"}'
```
