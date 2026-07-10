# CNAPS POC 前端 HTTP API 文档

版本日期：2026-07-10

适用范围：当前 `cnaps-single-table-poc` WebFE/Tomcat WAR，通过 Jolt 调用 Tuxedo C 服务，再访问本机 Oracle XE。

## 1. 基础信息

### 1.1 Base URL

VM 联调地址：

```text
http://192.168.84.134:8080/ruisui-bank-sim
```

本机或 VM 内部地址：

```text
http://127.0.0.1:8080/ruisui-bank-sim
```

### 1.2 Content-Type

有请求体的接口统一使用：

```http
Content-Type: application/json
```

如果请求体不是 `application/json`，当前 WebFE 会按空请求体处理。

### 1.3 浏览器跨域

当前 WAR 未配置 CORS。前端如果不是同源部署，需要使用开发代理，或后续在 WebFE 增加 CORS Filter。

### 1.4 服务器请求上下文

业务请求上下文由 WebFE 在服务器端提供，不接受浏览器通过公共请求头覆盖：

- `requestId`：WebFE 为每次请求生成 `REQ-<timestamp>` 形式的内部请求流水号，当前响应不回传该字段。
- `operatorNo`：使用系统属性或应用配置 `webfe.poc.operatorNo`、环境变量 `POC_OPERATOR_NO`、Servlet Context 参数 `poc.operatorNo`，默认值为 `77210021`。
- `branchNo`：使用系统属性或应用配置 `webfe.poc.branchNo`、环境变量 `POC_BRANCH_NO`、Servlet Context 参数 `poc.branchNo`，默认值为 `772`。

WebFE 会向 Tuxedo 转发内部字段 `REQUEST_ID`、`OPERATOR_NO` 和 `BRANCH_NO`，用于请求跟踪、凭证创建和审计持久化。

### 1.5 公共响应结构

```json
{
  "respCode": "0000",
  "respMsg": "create success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `respCode` | string | 业务响应码。成功为 `0000`。 |
| `respMsg` | string | 业务响应消息。 |
| `data` | object / null | 业务数据。失败时为 `null`。 |

### 1.6 响应码和 HTTP 状态

| respCode | HTTP 状态 | 说明 | 当前实现备注 |
| --- | --- | --- | --- |
| `0000` | 200 | 成功 | 健康检查中 Oracle 为 `DOWN` 时仍可能返回 `0000`，前端需读取 `data.oracle`。 |
| `2001` | 400 | 必填字段为空 | 已用于创建、详情、复核退回等必填校验。 |
| `2002` | 400 | 字段格式错误 | 预留。当前多数格式错误会落到 `4001`。 |
| `2003` | 400 | 字典值非法 | 预留。 |
| `3001` | 404 | 记录不存在 | 已用于详情查询找不到凭证。 |
| `3002` | 409 | 状态不允许 | 预留。 |
| `3003` | 409 | 状态冲突 | 预留。 |
| `3004` | 409 | 复核状态冲突 | 预留。 |
| `3005` | 403 | 权限/复核人限制 | 预留。 |
| `4001` | 500 | 数据库错误 | Oracle/SQL/OCI 失败。 |
| `4002` | 504 | Tuxedo/Jolt 调用失败 | 停 Tuxedo、调用异常、超时类错误。 |
| `4003` | 503 | Tuxedo/Jolt 不可用 | Jolt 类缺失或 ATMI 客户端不可用。 |
| `9999` | 500 | 未知错误 | 预留。 |

## 2. 枚举

### 2.1 凭证状态

| 值 | 说明 |
| --- | --- |
| `00_DRAFT` | 草稿，当前 HTTP API 不直接产出。 |
| `10_PENDING_REVIEW` | 待复核。创建、修改后进入该状态。 |
| `20_REVIEW_APPROVED` | 复核通过。 |
| `30_REVIEW_REJECTED` | 复核退回。 |
| `40_DELETED` | 已删除。 |

### 2.2 最后动作

| 值 | 说明 |
| --- | --- |
| `CREATE` | 创建。 |
| `UPDATE` | 修改。 |
| `DELETE` | 删除。 |
| `REVIEW_PASS` | 复核通过。 |
| `REVIEW_RETURN` | 复核退回。 |

### 2.3 POC 默认字典值

| 字段 | 值 | 说明 |
| --- | --- | --- |
| `businessType` | `02102` | 普通汇兑。 |
| `priority` | `NORM` | 普通优先级。 |
| `systemType` | `CNAPS` | CNAPS 系统。 |
| `debitMode` | `1` | 默认扣款模式。 |
| `feeChargeMode` | `1` | 默认收费方式。 |
| `sendMode` | `0` | 默认发送方式。 |
| `faxFlag` | `0` | 非传真。 |

## 3. 字段说明

### 3.1 凭证创建请求字段

`POST /api/cnaps/vouchers` 使用以下 JSON 字段。当前后端硬校验要求 `workDate`、`payeeAccountNo`、`payeeName`、`amount` 非空；前端录入页还应按“前端建议必填”列做表单校验。

| JSON 字段 | 类型 | 后端必填 | 前端建议必填 | 默认值 | 示例 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `workDate` | string | 是 | 是 | 无 | `2026-07-09` | 工作日期，格式 `yyyy-MM-dd`；创建时必填。 |
| `businessType` | string | 否 | 是 | `02102` | `02102` | 业务类型。 |
| `accountPart1` | string | 否 | 是 | 空 | `404045` | 付款账号组成部分 1。 |
| `accountPart2` | string | 否 | 是 | 空 | `00772` | 付款账号组成部分 2。 |
| `accountPart3` | string | 否 | 是 | 空 | `000000000001` | 付款账号组成部分 3。 |
| `accountName` | string | 否 | 否 | 空 | `Payer Account` | 付款账户户名。 |
| `payerName` | string | 否 | 否 | 空 | `Payer Name` | 付款人名称。 |
| `payeeAccountNo` | string | 是 | 是 | 无 | `622200000000000001` | 收款账号。 |
| `payeeName` | string | 是 | 是 | 无 | `Payee Name` | 收款人名称。 |
| `priority` | string | 否 | 是 | `NORM` | `NORM` | 优先级。 |
| `receiveBankNo` | string | 否 | 否 | 空 | `102290000002` | 接收行行号。 |
| `receiveBankName` | string | 否 | 否 | 空 | `CNAPS receiving bank` | 接收行名称。 |
| `systemType` | string | 否 | 是 | `CNAPS` | `CNAPS` | 系统类型。 |
| `amount` | string | 是 | 是 | 无 | `5600.00` | 金额。建议前端校验为大于 0 且最多两位小数。 |
| `debitMode` | string | 否 | 是 | `1` | `1` | 扣款模式。 |
| `feeAmount` | string | 否 | 否 | `0` | `0.00` | 手续费金额。 |
| `feeChargeMode` | string | 否 | 是 | `1` | `1` | 收费方式。 |
| `sendMode` | string | 否 | 是 | `0` | `0` | 发送方式。 |
| `faxFlag` | string | 否 | 是 | `0` | `0` | 传真标志。 |
| `voucherNo` | string | 否 | 否 | 空 | `PZ202607090001` | 凭证号。 |
| `remark` | string | 否 | 否 | 空 | `front-end test` | 备注。 |

### 3.2 凭证核心响应字段

不同接口返回字段不完全一致。当前 Jolt 元数据和 C 服务只返回 POC 核心字段，不保证返回数据库全字段。

| JSON 字段 | 类型 | 常见来源接口 | 说明 |
| --- | --- | --- | --- |
| `billId` | string | 创建、详情、修改、删除、复核 | 凭证编号，格式类似 `B202607097720002004`。 |
| `serialNo` | string | 创建、详情 | 机构工作日流水号。 |
| `workDate` | string | 创建、详情、修改 | 工作日期，格式 `yyyy-MM-dd`。 |
| `status` | string | 创建、详情、修改、删除、复核、列表查询回显 | 凭证状态。 |
| `businessType` | string | 创建回显 | 业务类型。 |
| `accountPart1` | string | 创建回显 | 付款账号组成部分 1。 |
| `accountPart2` | string | 创建回显 | 付款账号组成部分 2。 |
| `accountPart3` | string | 创建回显 | 付款账号组成部分 3。 |
| `accountName` | string | 创建回显 | 付款账户户名。 |
| `payerName` | string | 创建回显 | 付款人名称。 |
| `payeeAccountNo` | string | 创建、详情、修改 | 收款账号。 |
| `payeeName` | string | 创建、详情、修改 | 收款人名称。 |
| `priority` | string | 创建回显 | 优先级。 |
| `receiveBankNo` | string | 创建回显、银行查询 | 接收行行号。 |
| `receiveBankName` | string | 创建回显、银行查询 | 接收行名称。 |
| `systemType` | string | 创建回显、字典查询 | 系统类型。 |
| `amount` | string | 创建、详情、修改 | 金额。 |
| `debitMode` | string | 创建回显 | 扣款模式。 |
| `feeAmount` | string | 创建回显 | 手续费金额。 |
| `feeChargeMode` | string | 创建回显 | 收费方式。 |
| `sendMode` | string | 创建回显 | 发送方式。 |
| `faxFlag` | string | 创建回显 | 传真标志。 |
| `voucherNo` | string | 创建回显 | 凭证号。 |
| `remark` | string | 创建、修改回显 | 备注。 |
| `rejectReason` | string | 复核退回、详情有值时 | 退回原因。 |
| `reviewComment` | string | 复核通过、详情有值时 | 复核意见。 |
| `deleteReason` | string | 删除回显 | 删除原因。 |
| `lastAction` | string | 部分服务扩展时返回 | 最后动作。 |
| `totalElements` | number | 列表、复核列表 | 总条数。当前列表接口只返回计数。 |
| `totalPages` | number | 列表、复核列表 | 总页数。当前固定返回 `1`。 |

## 4. API 清单

| 方法 | 路径 | Tuxedo 服务 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/health` | `SYSHEALTH` | 健康检查。 |
| `GET` | `/api/dicts/{dictType}` | `DICTQRY` | 字典查询。当前返回简化字段。 |
| `GET` | `/api/banks` | `BANKQRY` | 银行信息查询。 |
| `GET` | `/api/cnaps/vouchers` | `CNAPS4609Q` | 凭证列表计数查询。 |
| `POST` | `/api/cnaps/vouchers` | `CNAPS5701E` | 创建凭证。 |
| `GET` | `/api/cnaps/vouchers/review-list` | `CNAPS5702Q` | 待复核列表计数查询。 |
| `GET` | `/api/cnaps/vouchers/{billId}` | `CNAPS5702I` | 凭证明细。 |
| `PUT` | `/api/cnaps/vouchers/{billId}` | `CNAPS5701U` | 修改凭证核心字段。 |
| `POST` | `/api/cnaps/vouchers/{billId}/delete` | `CNAPS5701D` | 删除凭证。 |
| `POST` | `/api/cnaps/vouchers/{billId}/review-pass` | `CNAPS5702A` | 复核通过。 |
| `POST` | `/api/cnaps/vouchers/{billId}/review-return` | `CNAPS5702R` | 复核退回。 |

## 5. 接口详情

### 5.1 健康检查

```http
GET /api/health
```

请求参数：无。

成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "health check success",
  "data": {
    "service": "SYSHEALTH",
    "oracle": "UP",
    "tuxedo": "UP",
    "webfe": "UP"
  }
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.service` | string | Tuxedo 服务名，固定为 `SYSHEALTH`。 |
| `data.webfe` | string | WebFE 状态，`UP`。 |
| `data.tuxedo` | string | Tuxedo 状态，Jolt 调用成功时为 `UP`。 |
| `data.oracle` | string | Oracle 探测结果，`UP` 或 `DOWN`。 |

注意：Oracle 不可用时当前服务仍返回 HTTP 200 和 `respCode=0000`，但 `data.oracle` 会是 `DOWN`，`respMsg` 为 `Oracle unavailable`。停 Tuxedo/Jolt 时 WebFE 返回 `4002` 或 `4003`。

### 5.2 字典查询

```http
GET /api/dicts/{dictType}
```

路径参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `dictType` | 是 | `BUSINESS_TYPE` | 字典类型，会映射为 Tuxedo 字段 `DICT_TYPE`。 |

当前成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "query success",
  "data": {
    "systemType": "CNAPS"
  }
}
```

当前限制：C 服务内部会写入 `DICT_TYPE`、`DICT_CODE`、`DICT_NAME`、`SORT_NO`、`SYSTEM_TYPE`，但现有 Jolt metadata 只把 `SYSTEM_TYPE` 暴露给 WebFE，所以 HTTP 响应当前只有 `systemType`。如果前端需要完整字典项，需要扩展 `tuxedo/jolt/cnaps_services.bulk`。

### 5.3 银行查询

```http
GET /api/banks
```

查询参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `receiveBankNo` | 否 | `102290000002` | 接收行行号。当前 POC 不按参数过滤，固定返回一条测试银行。 |
| `pageNo` / `page` | 否 | `1` | 页码字段会传给 Tuxedo，当前 POC 不实际分页。 |
| `pageSize` / `size` | 否 | `10` | 页大小字段会传给 Tuxedo，当前 POC 不实际分页。 |

当前成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "query success",
  "data": {
    "receiveBankNo": "102290000002",
    "receiveBankName": "CNAPS receiving bank"
  }
}
```

### 5.4 凭证列表查询

```http
GET /api/cnaps/vouchers?status=10_PENDING_REVIEW&pageNo=1&pageSize=10
```

查询参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `workDate` | 否 | `2026-07-09` | 工作日期过滤；未传时默认当前日期。 |
| `status` | 否 | `10_PENDING_REVIEW` | 按状态计数。 |
| `pageNo` / `page` | 否 | `1` | 会传入 Tuxedo，但当前 C 服务不实际分页。 |
| `pageSize` / `size` | 否 | `10` | 会传入 Tuxedo，但当前 C 服务不实际分页。 |
| `payeeName` | 否 | `Payee` | Jolt metadata 支持传入，但当前 C 服务不使用。 |
| `serialNo` | 否 | `0002004` | WebFE 可映射字段，但当前 C 服务不使用。 |
| `includeDeleted` | 否 | `false` | WebFE 可映射字段，但当前 C 服务不使用。 |

实际过滤条件还使用服务器配置的 `branchNo`（默认 `772`）；`status` 来自查询参数，可为空。

当前成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "query success",
  "data": {
    "status": "10_PENDING_REVIEW",
    "totalElements": 1,
    "totalPages": 1
  }
}
```

当前限制：该接口现在只返回计数，不返回 `records`、`content` 或具体凭证明细列表。

### 5.5 创建凭证

```http
POST /api/cnaps/vouchers
Content-Type: application/json
```

请求体：

```json
{
  "workDate": "2026-07-09",
  "payeeAccountNo": "622200000000000001",
  "payeeName": "Payee Name",
  "amount": "5600.00",
  "businessType": "02102",
  "priority": "NORM",
  "systemType": "CNAPS",
  "accountPart1": "404045",
  "accountPart2": "00772",
  "accountPart3": "000000000001",
  "debitMode": "1",
  "feeChargeMode": "1",
  "sendMode": "0",
  "faxFlag": "0",
  "remark": "front-end create"
}
```

成功行为：

- 生成 `billId`。
- 生成 `serialNo`。
- 状态置为 `10_PENDING_REVIEW`。
- `lastAction` 在数据库中记录为 `CREATE`。

当前成功响应示例：

```json
{
  "respCode": "0000",
  "respMsg": "create success",
  "data": {
    "billId": "B202607097720002004",
    "serialNo": "0002004",
    "workDate": "2026-07-09",
    "status": "10_PENDING_REVIEW",
    "payeeAccountNo": "622200000000000001",
    "payeeName": "Payee Name",
    "amount": "5600.00",
    "businessType": "02102",
    "priority": "NORM",
    "systemType": "CNAPS",
    "accountPart1": "404045",
    "accountPart2": "00772",
    "accountPart3": "000000000001",
    "debitMode": "1",
    "feeChargeMode": "1",
    "sendMode": "0",
    "faxFlag": "0",
    "remark": "front-end create"
  }
}
```

错误场景：

| 场景 | HTTP 状态 | respCode | 说明 |
| --- | --- | --- | --- |
| `workDate`、`payeeAccountNo`、`payeeName` 或 `amount` 为空 | 400 | `2001` | 返回 `required field missing`。 |
| Oracle DML 失败 | 500 | `4001` | 金额格式无法转数字等也可能进入该错误。 |

### 5.6 待复核列表查询

```http
GET /api/cnaps/vouchers/review-list?pageNo=1&pageSize=10
```

查询参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `workDate` | 否 | `2026-07-09` | 工作日期过滤；未传时默认当前日期。 |
| `pageNo` / `page` | 否 | `1` | 会传入 Tuxedo，当前 C 服务不实际分页。 |
| `pageSize` / `size` | 否 | `10` | 会传入 Tuxedo，当前 C 服务不实际分页。 |

实际过滤条件还使用服务器配置的 `branchNo`（默认 `772`），`status` 固定为 `10_PENDING_REVIEW`。

当前成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "query success",
  "data": {
    "totalElements": 1,
    "totalPages": 1
  }
}
```

当前限制：该接口现在只返回计数，不返回待复核凭证列表。

### 5.7 凭证明细

```http
GET /api/cnaps/vouchers/{billId}
```

路径参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `billId` | 是 | `B202607097720002004` | 凭证编号。 |

当前成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "detail query success",
  "data": {
    "billId": "B202607097720002004",
    "serialNo": "0002004",
    "status": "10_PENDING_REVIEW",
    "payeeAccountNo": "622200000000000001",
    "payeeName": "Payee Name",
    "amount": "5600.00"
  }
}
```

错误场景：

| 场景 | HTTP 状态 | respCode | 说明 |
| --- | --- | --- | --- |
| `billId` 为空 | 400 | `2001` | 正常路径下不应出现。 |
| 凭证不存在 | 404 | `3001` | 返回 `voucher not found`。 |

### 5.8 修改凭证

```http
PUT /api/cnaps/vouchers/{billId}
Content-Type: application/json
```

路径参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `billId` | 是 | `B202607097720002004` | 凭证编号。 |

请求体：

```json
{
  "workDate": "2026-07-10",
  "payeeAccountNo": "622200000000000456",
  "payeeName": "Updated Payee",
  "amount": "56.78",
  "remark": "front-end update"
}
```

请求字段：

| JSON 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `workDate` | string | 否 | 工作日期，格式 `yyyy-MM-dd`；修改时可选。传入时更新工作日期，不传时保持原值。 |
| `payeeAccountNo` | string | 否 | 收款账号。 |
| `payeeName` | string | 否 | 收款人名称。 |
| `amount` | string | 否 | 金额。 |
| `remark` | string | 否 | 备注。 |

成功行为：

- 状态置为 `10_PENDING_REVIEW`。
- 数据库 `lastAction` 置为 `UPDATE`。
- 数据库版本号加 1。
- 修改 `workDate` 不会重新生成 `billId` 或 `serialNo`，两者保持不变。

当前成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "update success",
  "data": {
    "billId": "B202607097720002004",
    "workDate": "2026-07-10",
    "status": "10_PENDING_REVIEW",
    "payeeAccountNo": "622200000000000456",
    "payeeName": "Updated Payee",
    "amount": "56.78",
    "remark": "front-end update"
  }
}
```

当前限制：后端当前不检查凭证原状态是否允许修改。

### 5.9 删除凭证

```http
POST /api/cnaps/vouchers/{billId}/delete
Content-Type: application/json
```

路径参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `billId` | 是 | `B202607097720002004` | 凭证编号。 |

请求体：

```json
{
  "deleteReason": "input error"
}
```

请求字段：

| JSON 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `deleteReason` | string | 否 | 删除原因。 |

成功行为：

- 状态置为 `40_DELETED`。
- 数据库 `lastAction` 置为 `DELETE`。
- 数据库版本号加 1。

当前成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "delete success",
  "data": {
    "billId": "B202607097720002004",
    "status": "40_DELETED",
    "deleteReason": "input error"
  }
}
```

当前限制：后端当前不检查凭证原状态是否允许删除。

### 5.10 复核通过

```http
POST /api/cnaps/vouchers/{billId}/review-pass
Content-Type: application/json
```

路径参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `billId` | 是 | `B202607097720002005` | 凭证编号。 |

请求体：

```json
{
  "reviewComment": "ok"
}
```

请求字段：

| JSON 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `reviewComment` | string | 否 | 复核意见。 |

成功行为：

- 状态置为 `20_REVIEW_APPROVED`。
- 数据库 `lastAction` 置为 `REVIEW_PASS`。
- 数据库版本号加 1。

当前成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "review pass success",
  "data": {
    "billId": "B202607097720002005",
    "status": "20_REVIEW_APPROVED",
    "reviewComment": "ok"
  }
}
```

当前限制：后端当前不检查凭证原状态，也不禁止经办人复核本人凭证。前端如需规避误操作，建议只对 `10_PENDING_REVIEW` 状态展示该操作。

### 5.11 复核退回

```http
POST /api/cnaps/vouchers/{billId}/review-return
Content-Type: application/json
```

路径参数：

| 参数 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `billId` | 是 | `B202607097720002004` | 凭证编号。 |

请求体：

```json
{
  "rejectReason": "payee info incorrect"
}
```

请求字段：

| JSON 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `rejectReason` | string | 是 | 退回原因。 |
| `reviewComment` | string | 否 | 复核意见。当前接口可透传，但主要使用 `rejectReason`。 |

成功行为：

- 状态置为 `30_REVIEW_REJECTED`。
- 数据库 `lastAction` 置为 `REVIEW_RETURN`。
- 数据库版本号加 1。

当前成功响应：

```json
{
  "respCode": "0000",
  "respMsg": "review return success",
  "data": {
    "billId": "B202607097720002004",
    "status": "30_REVIEW_REJECTED",
    "rejectReason": "payee info incorrect"
  }
}
```

错误场景：

| 场景 | HTTP 状态 | respCode | 说明 |
| --- | --- | --- | --- |
| `billId` 或 `rejectReason` 为空 | 400 | `2001` | 返回 `billId and rejectReason are required`。 |

当前限制：后端当前不检查凭证原状态，也不禁止经办人复核本人凭证。前端如需规避误操作，建议只对 `10_PENDING_REVIEW` 状态展示该操作。

## 6. 前端联调 curl 示例

### 6.1 健康检查

```bash
curl "http://192.168.84.134:8080/ruisui-bank-sim/api/health"
```

### 6.2 创建凭证

```bash
curl -X POST "http://192.168.84.134:8080/ruisui-bank-sim/api/cnaps/vouchers" \
  -H "Content-Type: application/json" \
  -d '{
    "workDate": "2026-07-09",
    "payeeAccountNo": "622200000000000001",
    "payeeName": "Payee Name",
    "amount": "5600.00",
    "businessType": "02102",
    "priority": "NORM",
    "systemType": "CNAPS",
    "accountPart1": "404045",
    "accountPart2": "00772",
    "accountPart3": "000000000001",
    "debitMode": "1",
    "feeChargeMode": "1",
    "sendMode": "0",
    "faxFlag": "0",
    "remark": "front-end create"
  }'
```

### 6.3 查询列表计数

```bash
curl "http://192.168.84.134:8080/ruisui-bank-sim/api/cnaps/vouchers?status=10_PENDING_REVIEW&pageNo=1&pageSize=10"
```

### 6.4 查询详情

```bash
curl "http://192.168.84.134:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607097720002004"
```

### 6.5 修改凭证

```bash
curl -X PUT "http://192.168.84.134:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607097720002004" \
  -H "Content-Type: application/json" \
  -d '{
    "workDate": "2026-07-10",
    "payeeAccountNo": "622200000000000456",
    "payeeName": "Updated Payee",
    "amount": "56.78",
    "remark": "front-end update"
  }'
```

### 6.6 复核通过

```bash
curl -X POST "http://192.168.84.134:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607097720002005/review-pass" \
  -H "Content-Type: application/json" \
  -d '{"reviewComment":"ok"}'
```

### 6.7 复核退回

```bash
curl -X POST "http://192.168.84.134:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607097720002004/review-return" \
  -H "Content-Type: application/json" \
  -d '{"rejectReason":"payee info incorrect"}'
```

### 6.8 删除凭证

```bash
curl -X POST "http://192.168.84.134:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607097720002004/delete" \
  -H "Content-Type: application/json" \
  -d '{"deleteReason":"input error"}'
```

## 7. 当前 POC 限制

1. 列表和复核列表当前只返回 `totalElements` / `totalPages`，不返回明细数组。
2. 字典接口当前只暴露 `systemType`，完整 `dictCode` / `dictName` 需要扩展 Jolt metadata。
3. 银行接口当前固定返回一条测试银行，不按参数过滤。
4. 修改、删除、复核当前未做完整状态流转校验，建议前端先按状态控制按钮展示。
5. 当前 VM 上中文从 Oracle 读回时可能出现编码问题，联调示例建议先使用 ASCII；后续需要统一配置 Oracle/Tomcat/Tuxedo 的字符集。
