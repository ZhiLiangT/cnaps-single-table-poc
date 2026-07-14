# CNAPS 单表 POC 前端 API

> 版本：v0.5<br>
> Base URL：`http://localhost:8080/ruisui-bank-sim`

本文档仅描述前端调用所需的 HTTP 契约。项目当前没有登录和权限系统，操作员及机构由服务端固定配置。

## 1. 通用约定

- JSON 请求使用 `Content-Type: application/json; charset=UTF-8`。
- 日期格式为 `yyyy-MM-dd`，时间格式为 `yyyy-MM-dd HH:mm:ss`。
- 金额使用字符串表示，如 `"5600.00"`，最多两位小数。
- `workDate` 创建时必填、修改时可选；列表查询使用 `startWorkDate` / `endWorkDate`。
- 列表日期范围均未传时不按工作日期筛选；修改 `workDate` 不会重新生成 `billId` 或 `serialNo`。
- 分页参数为 `pageNo`（默认 `1`）和 `pageSize`（默认 `10`）。

所有接口统一返回：

```json
{
  "respCode": "0000",
  "respMsg": "操作成功",
  "data": {}
}
```

顶层字段固定为 `respCode`、`respMsg`、`data`。`respCode` 为 `0000` 时成功；失败时 `data` 为 `null`。HTTP 状态与错误码的对应关系见第 6 节。

分页响应中的 `data` 结构为：

```json
{
  "pageNo": 1,
  "pageSize": 10,
  "total": 1,
  "records": []
}
```

## 2. 接口清单

| 接口 | 用途 |
|---|---|
| `GET /api/health` | 健康检查 |
| `GET /api/dicts/{dictType}` | 字典查询 |
| `GET /api/banks` | 行号查询 |
| `POST /api/cnaps/vouchers` | 单据录入 |
| `PUT /api/cnaps/vouchers/{billId}` | 单据修改 |
| `POST /api/cnaps/vouchers/{billId}/delete` | 逻辑删除 |
| `POST /api/cnaps/vouchers/query` | 通用查询 |
| `POST /api/cnaps/vouchers/review-list` | 待复核查询 |
| `GET /api/cnaps/vouchers/{billId}` | 单据详情 |
| `POST /api/cnaps/vouchers/{billId}/review-pass` | 复核通过 |
| `POST /api/cnaps/vouchers/{billId}/review-return` | 复核退回 |

旧接口 `GET /api/cnaps/vouchers` 和 `GET /api/cnaps/vouchers/review-list` 已停用，返回 HTTP 405。

## 3. 单据状态

| 状态 | 含义 | 可执行操作 |
|---|---|---|
| `10_PENDING_REVIEW` | 待复核 | 修改、删除、复核通过、复核退回 |
| `20_REVIEW_APPROVED` | 复核通过 | 查询、查看详情 |
| `30_REVIEW_REJECTED` | 复核退回 | 修改、删除 |
| `40_DELETED` | 已删除 | 查看详情、在通用查询中显式查询 |

状态流转：

```text
10_PENDING_REVIEW  -> 20_REVIEW_APPROVED
10_PENDING_REVIEW  -> 30_REVIEW_REJECTED
10_PENDING_REVIEW  -> 40_DELETED
30_REVIEW_REJECTED -> 10_PENDING_REVIEW
30_REVIEW_REJECTED -> 40_DELETED
```

## 4. API

### 4.1 健康检查

```http
GET /api/health
```

`data` 返回 `webfe`、`tuxedo`、`oracle` 的状态，以及服务端配置的 `operatorNo` 和 `branchNo`。

### 4.2 字典查询

```http
GET /api/dicts/{dictType}
```

支持的 `dictType`：

| dictType | 可用编码 |
|---|---|
| `BUSINESS_TYPE` | `02102` |
| `PRIORITY` | `NORM` |
| `SYSTEM_TYPE` | `CNAPS` |
| `DEBIT_MODE` | `1` |
| `FEE_CHARGE_MODE` | `1` |
| `SEND_MODE` | `0` |
| `FAX_FLAG` | `0`、`1` |

`data` 为数组，元素字段如下：

| 字段 | 类型 | 说明 |
|---|---|---|
| `dictType` | string | 字典类型 |
| `dictCode` | string | 字典编码 |
| `dictName` | string | 字典名称 |
| `sortNo` | number | 排序号 |

### 4.3 行号查询

```http
GET /api/banks
```

Query 参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `bankNo` | string | 行号精确匹配 |
| `keyword` | string | 行名或行号模糊匹配 |
| `city` | string | 城市匹配 |
| `systemType` | string | 系统类型匹配 |
| `pageNo` | number | 页码，默认 `1` |
| `pageSize` | number | 每页条数，默认 `10` |

`records` 元素包含 `bankNo`、`bankName`、`city`、`systemType`。

### 4.4 单据录入

```http
POST /api/cnaps/vouchers
```

请求 Body 使用第 5.1 节的业务字段。创建成功后状态为 `10_PENDING_REVIEW`，服务端生成 `billId`、`serialNo`、`versionNo` 和审计字段。

成功响应示例：

```json
{
  "respCode": "0000",
  "respMsg": "录入成功，待复核",
  "data": {
    "billId": "B202607137720002000",
    "serialNo": "0002000",
    "workDate": "2026-07-13",
    "status": "10_PENDING_REVIEW",
    "amount": "5600.00",
    "lastAction": "CREATE",
    "versionNo": 1
  }
}
```

### 4.5 单据修改

```http
PUT /api/cnaps/vouchers/{billId}
```

- 仅 `10_PENDING_REVIEW`、`30_REVIEW_REJECTED` 状态可修改。
- Body 可传第 5.1 节中的任意业务字段，全部可选；字段未传时保留原值。
- `payerAddress`、`payeeAddress`、`payerBankName` 传空字符串 `""` 时清空。
- `billId`、`serialNo`、状态、版本号和审计字段不能由客户端修改。
- 修改成功后状态变为 `10_PENDING_REVIEW`，`versionNo` 加 1。

### 4.6 单据删除

```http
POST /api/cnaps/vouchers/{billId}/delete
```

可选 Body：

```json
{ "deleteReason": "录入错误" }
```

仅 `10_PENDING_REVIEW`、`30_REVIEW_REJECTED` 状态可删除。删除为逻辑删除，成功后状态为 `40_DELETED`。

### 4.7 通用查询

```http
POST /api/cnaps/vouchers/query
```

Body 参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `startWorkDate` | string | 工作日期下界，包含该日 |
| `endWorkDate` | string | 工作日期上界，包含该日 |
| `status` | string | 状态精确匹配 |
| `serialNo` | string | 流水号精确匹配 |
| `voucherNo` | string | 凭证号精确匹配 |
| `payeeName` | string | 收款人名称模糊匹配 |
| `payeeAccountNo` | string | 收款账号精确匹配 |
| `includeDeleted` | boolean | 是否包含已删除记录，默认 `false` |
| `pageNo` | number | 页码，默认 `1` |
| `pageSize` | number | 每页条数，默认 `10` |

不设置筛选条件时传 `{}`。日期范围可只传一端；开始日期不能晚于结束日期。Body 中不得出现旧字段 `workDate`，否则返回 HTTP 400 / `2002`。

### 4.8 待复核查询

```http
POST /api/cnaps/vouchers/review-list
```

Body 参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `startWorkDate` | string | 工作日期下界，包含该日 |
| `endWorkDate` | string | 工作日期上界，包含该日 |
| `serialNo` | string | 流水号精确匹配 |
| `pageNo` | number | 页码，默认 `1` |
| `pageSize` | number | 每页条数，默认 `10` |

查询规则与通用查询一致，只返回 `10_PENDING_REVIEW` 状态的记录。不设置筛选条件时传 `{}`，Body 中不得出现 `workDate`。

两个列表接口的 `records` 元素均包含：

| 字段 | 类型 |
|---|---|
| `billId` | string |
| `workDate` | string |
| `serialNo` | string |
| `voucherNo` | string |
| `payeeAccountNo` | string |
| `payeeName` | string |
| `amount` | string |
| `status` | string |
| `versionNo` | number |

待复核列表可能不返回 `voucherNo`。列表不返回 `payerAddress`、`payeeAddress`、`payerBankName`，需要时调用详情接口。

### 4.9 单据详情

```http
GET /api/cnaps/vouchers/{billId}
```

`data` 返回第 5 节定义的完整单据字段。单据不存在时返回 `3001`。

### 4.10 复核通过

```http
POST /api/cnaps/vouchers/{billId}/review-pass
```

可选 Body：

```json
{ "reviewComment": "复核通过" }
```

仅 `10_PENDING_REVIEW` 状态可操作，成功后状态为 `20_REVIEW_APPROVED`。

### 4.11 复核退回

```http
POST /api/cnaps/vouchers/{billId}/review-return
```

Body：

```json
{
  "rejectReason": "收款人户名不完整",
  "reviewComment": "请修改后重新提交"
}
```

`rejectReason` 必填，`reviewComment` 可选。仅 `10_PENDING_REVIEW` 状态可操作，成功后状态为 `30_REVIEW_REJECTED`。

## 5. 单据字段

### 5.1 前端业务字段

| 字段 | 类型 | 创建必填 | 默认值 / 说明 |
|---|---|:---:|---|
| `workDate` | string | 是 | 工作日期；修改时可选 |
| `businessType` | string | 是 | 当前仅支持 `02102` |
| `accountPart1` | string | 是 | 付款账号一段 |
| `accountPart2` | string | 是 | 付款账号二段 |
| `accountPart3` | string | 是 | 付款账号三段 |
| `accountName` | string | 否 | 付款账户户名 |
| `payerName` | string | 否 | 付款人名称 |
| `payerAddress` | string | 否 | 付款人地址，最长 256 字符 |
| `payerBankName` | string | 否 | 付款人开户行，最长 128 字符 |
| `payeeAccountNo` | string | 是 | 收款账号 |
| `payeeName` | string | 是 | 收款人名称 |
| `payeeAddress` | string | 否 | 收款人地址，最长 256 字符 |
| `priority` | string | 是 | 当前仅支持 `NORM` |
| `receiveBankNo` | string | 否 | 接收行号 |
| `receiveBankName` | string | 否 | 接收行名称 |
| `systemType` | string | 是 | 当前仅支持 `CNAPS` |
| `amount` | string | 是 | 大于 0，最多两位小数 |
| `debitMode` | string | 否 | 默认 `1` |
| `feeAmount` | string | 否 | 默认 `0.00`，不能小于 0 |
| `feeChargeMode` | string | 否 | 默认 `1` |
| `sendMode` | string | 否 | 默认 `0` |
| `faxFlag` | string | 否 | 默认 `0` |
| `voucherNo` | string | 否 | 凭证号 |
| `remark` | string | 否 | 备注 |

### 5.2 服务端字段

以下字段由服务端生成或维护，客户端不能直接修改：

| 字段 | 类型 | 说明 |
|---|---|---|
| `billId` | string | 单据编号 |
| `branchNo` | string | 机构号 |
| `operatorNo` | string | 录入操作员 |
| `serialNo` | string | 流水号 |
| `status` | string | 单据状态 |
| `checkerNo` / `checkerTime` | string | 最近复核人 / 时间 |
| `reviewComment` | string | 复核意见 |
| `rejectReason` | string | 退回原因 |
| `deleteReason` | string | 删除原因 |
| `deleteOperatorNo` / `deleteTime` | string | 删除人 / 时间 |
| `lastAction` | string | 最后动作 |
| `lastOperatorNo` | string | 最后操作员 |
| `lastRequestId` | string | 内部请求流水 |
| `lastActionTime` | string | 最后动作时间 |
| `createdAt` / `updatedAt` | string | 创建 / 更新时间 |
| `versionNo` | number | 版本号 |

## 6. 错误码

| 错误码 | HTTP 状态 | 含义 |
|---|---:|---|
| `0000` | 200 | 成功 |
| `2001` | 400 | 必填字段缺失 |
| `2002` | 400 | 字段格式错误 |
| `2003` | 400 | 字典值无效 |
| `3001` | 404 | 单据不存在 |
| `3003` | 409 | 当前状态不允许操作 |
| `3004` | 409 | 复核时状态已变化 |
| `4001` | 500 | 数据库错误 |
| `4002` | 504 | 后端调用超时 |
| `4003` | 503 | 后端服务不可用 |
| `9999` | 500 | 未分类错误 |

## 7. POC 限制

- 不提供登录、账号、角色或权限功能。
- 不校验复核人与录入人是否相同。
- 不执行真实扣账、CNAPS 发送或外联系统调用。
- 删除均为逻辑删除。
