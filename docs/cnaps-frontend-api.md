# 银行 Tuxedo 后端模拟系统 API 文档

> 文档版本：v0.5 单表 POC 版<br>
> 编写日期：2026-07-13<br>
> 适用项目：`ruisui-bank-sim`<br>
> 目标环境：Linux + Oracle Tuxedo + Oracle Database + WebFE<br>
> 接口风格：HTTP JSON；WebFE 内部映射为 Tuxedo ATMI / FML32 / Jolt 调用

---

## 1. 文档说明

本文档定义单表 POC 阶段 WebFE 对外暴露的完整 HTTP API，是当前项目唯一的公共接口契约。

v0.5 变更：通用查询和待复核列表查询改为 POST JSON Body；旧 GET 列表接口直接停用并返回 HTTP 405。

核心调用链路：

```text
浏览器 / JSP / Postman / curl
        ↓ HTTP JSON
WebFE / Tomcat
        ↓ Jolt / FML32
Oracle Tuxedo 服务
        ↓ OCI / SQL
Oracle Database（T_CNAPS_BILL_POC）
```

POC 只验证以下最小业务闭环：

```text
单据录入 → 待复核查询 → 详情查询 → 复核通过 / 复核退回 → 退回后修改 → 逻辑删除
```

本项目没有登录和账号系统。固定操作员与机构仅用于内部 FML32 兼容和单表审计，不代表登录身份或权限。

---

## 2. 基础约定

### 2.1 Base URL

本地或虚拟机默认地址：

```text
http://localhost:8080/ruisui-bank-sim
```

如从宿主机访问当前虚拟机，可将 `localhost` 替换为 `192.168.84.134`。

### 2.2 请求头

带 JSON Body 的请求只需协议头：

```http
Content-Type: application/json; charset=UTF-8
```

### 2.3 日期、时间、金额和编码

| 类型 | 格式 | 示例 | 说明 |
|---|---|---|---|
| 日期 | `yyyy-MM-dd` | `2026-07-13` | 录入 Body 必填；修改 Body 可选；列表 Body 可选 |
| 时间 | `yyyy-MM-dd HH:mm:ss` | `2026-07-13 09:30:25` | 响应中的操作时间 |
| 金额 | 字符串，最多两位小数 | `5600.00` | 避免 JSON 浮点精度问题 |
| 编码 | UTF-8 | 中文地址、名称 | WebFE、Tuxedo 和 Oracle 客户端需使用兼容字符集 |

### 2.4 工作日期

- 录入接口：`workDate` 在 JSON Body 中必填。
- 修改接口：`workDate` 在 JSON Body 中可选，未传时保持原值。
- 通用查询和待复核查询：`startWorkDate`、`endWorkDate` 为可选 JSON Body 字段，均未传时不按工作日期筛选；列表请求不支持 `workDate`。
- 详情、删除和复核接口：只使用 Path 中的 `billId`，不需要工作日期。

概括而言，`workDate` 创建时必填，修改时可选。列表只按起止工作日期范围过滤，未传范围参数时查询全部工作日期。修改 `workDate` 不会重新生成 `billId` 或 `serialNo`，两者保持不变。

### 2.5 分页

分页统一使用：

| 字段 | 类型 | 默认值 | 说明 |
|---|---:|---:|---|
| `pageNo` | number | `1` | 页码，从 1 开始 |
| `pageSize` | number | `10` | 每页条数 |
| `total` | number | - | 总记录数 |
| `records` | array | - | 当前页记录 |

---

## 3. 通用响应格式

### 3.1 成功响应

```json
{
  "respCode": "0000",
  "respMsg": "操作已成功",
  "data": {}
}
```

### 3.2 失败响应

```json
{
  "respCode": "2001",
  "respMsg": "必输字段为空：payeeAccountNo",
  "data": null
}
```

### 3.3 响应字段

| 字段 | 类型 | 必返 | 说明 |
|---|---:|:---:|---|
| `respCode` | string | 是 | `0000` 表示成功，其他值见错误码章节 |
| `respMsg` | string | 是 | 响应描述 |
| `data` | object / array / null | 是 | 成功时为业务数据，失败时为 `null` |

顶层响应只包含以上三个字段。

---

## 4. 接口清单

| HTTP API | Tuxedo 服务 | 是否写库 | 说明 |
|---|---|:---:|---|
| `GET /api/health` | `SYSHEALTH` | 否 | 健康检查 |
| `GET /api/dicts/{dictType}` | `DICTQRY` | 否 | 字典查询 |
| `GET /api/banks` | `BANKQRY` | 否 | 行号查询 |
| `POST /api/cnaps/vouchers` | `CNAPS5701E` | 是 | 单据录入 |
| `PUT /api/cnaps/vouchers/{billId}` | `CNAPS5701U` | 是 | 单据修改 |
| `POST /api/cnaps/vouchers/{billId}/delete` | `CNAPS5701D` | 是 | 逻辑删除 |
| `POST /api/cnaps/vouchers/query` | `CNAPS4609Q` | 否 | 通用查询 |
| `POST /api/cnaps/vouchers/review-list` | `CNAPS5702Q` | 否 | 待复核查询 |
| `GET /api/cnaps/vouchers/{billId}` | `CNAPS5702I` | 否 | 单据详情 |
| `POST /api/cnaps/vouchers/{billId}/review-pass` | `CNAPS5702A` | 是 | 复核通过 |
| `POST /api/cnaps/vouchers/{billId}/review-return` | `CNAPS5702R` | 是 | 复核退回 |

原列表接口 `GET /api/cnaps/vouchers` 和 `GET /api/cnaps/vouchers/review-list` 已停用，调用时返回 HTTP 405。

---

## 5. 状态模型

| 状态码 | 状态名 | 说明 | 允许操作 |
|---|---|---|---|
| `10_PENDING_REVIEW` | 待复核 | 录入或修改成功后进入 | 查询、详情、修改、删除、复核通过、复核退回 |
| `20_REVIEW_APPROVED` | 复核通过 | 复核完成，本 POC 不发送外联系统 | 查询、详情 |
| `30_REVIEW_REJECTED` | 复核退回 | 等待经办修改 | 查询、详情、修改、删除 |
| `40_DELETED` | 已删除 | 逻辑删除，不再允许业务处理 | 详情、带删除标志的通用查询 |

允许状态流转：

```text
10_PENDING_REVIEW  → 20_REVIEW_APPROVED
10_PENDING_REVIEW  → 30_REVIEW_REJECTED
10_PENDING_REVIEW  → 40_DELETED
30_REVIEW_REJECTED → 10_PENDING_REVIEW
30_REVIEW_REJECTED → 40_DELETED
```

---

## 6. API 详情

## 6.1 健康检查

### 基本信息

```http
GET /api/health
```

| 项目 | 内容 |
|---|---|
| Tuxedo 服务 | `SYSHEALTH` |
| 是否写库 | 否 |
| 用途 | 验证 WebFE、Tuxedo、Oracle 连通性 |

### 请求示例

```bash
curl -X GET "http://localhost:8080/ruisui-bank-sim/api/health"
```

### 成功响应示例

```json
{
  "respCode": "0000",
  "respMsg": "health check success",
  "data": {
    "service": "SYSHEALTH",
    "webfe": "UP",
    "tuxedo": "UP",
    "oracle": "UP",
    "operatorNo": "77210021",
    "branchNo": "772"
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---:|---|
| `data.service` | string | 实际调用的服务名 |
| `data.webfe` | string | WebFE 状态 |
| `data.tuxedo` | string | Tuxedo 状态 |
| `data.oracle` | string | Oracle 状态 |
| `data.operatorNo` | string | 服务器配置的固定 POC 操作员 |
| `data.branchNo` | string | 服务器配置的固定 POC 机构 |

## 6.2 字典查询

### 基本信息

```http
GET /api/dicts/{dictType}
```

| 项目 | 内容 |
|---|---|
| Tuxedo 服务 | `DICTQRY` |
| 是否写库 | 否 |
| 用途 | 查询录入页面使用的固定 POC 字典 |

### Path 参数

| 参数 | 类型 | 必输 | 示例 | 说明 |
|---|---:|:---:|---|---|
| `dictType` | string | 是 | `BUSINESS_TYPE` | 字典类型 |

支持的字典：

| dictType | 支持值 | 说明 |
|---|---|---|
| `BUSINESS_TYPE` | `02102` | 普通汇兑 |
| `PRIORITY` | `NORM` | 普通优先级 |
| `SYSTEM_TYPE` | `CNAPS` | CNAPS 系统 |
| `DEBIT_MODE` | `1` | 扣收 |
| `FEE_CHARGE_MODE` | `1` | 同城收费 |
| `SEND_MODE` | `0` | 柜面 |
| `FAX_FLAG` | `0`、`1` | 否、是 |

### 请求示例

```bash
curl -X GET "http://localhost:8080/ruisui-bank-sim/api/dicts/BUSINESS_TYPE"
```

### 成功响应示例

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

### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---:|---|
| `data[].dictType` | string | 字典类型 |
| `data[].dictCode` | string | 字典编码 |
| `data[].dictName` | string | 字典名称 |
| `data[].sortNo` | number | 排序号 |

不存在的字典类型返回 `2003`。

## 6.3 行号查询

### 基本信息

```http
GET /api/banks
```

| 项目 | 内容 |
|---|---|
| Tuxedo 服务 | `BANKQRY` |
| 是否写库 | 否 |
| 用途 | 查询接收行号和行名 |

### Query 参数

| 参数 | 类型 | 必输 | 默认值 | 说明 |
|---|---:|:---:|---:|---|
| `bankNo` | string | 否 | - | 行号精确匹配 |
| `keyword` | string | 否 | - | 行名或行号模糊匹配 |
| `city` | string | 否 | - | 城市匹配 |
| `systemType` | string | 否 | - | 系统类型匹配 |
| `pageNo` | number | 否 | `1` | 页码 |
| `pageSize` | number | 否 | `10` | 每页条数 |

### 请求示例

```bash
curl -X GET "http://localhost:8080/ruisui-bank-sim/api/banks?keyword=290000&systemType=CNAPS&pageNo=1&pageSize=10"
```

### 成功响应示例

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
        "bankName": "接收行名称",
        "city": "上海",
        "systemType": "CNAPS"
      }
    ]
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---:|---|
| `data.pageNo` | number | 当前页码 |
| `data.pageSize` | number | 每页条数 |
| `data.total` | number | 总记录数 |
| `data.records[].bankNo` | string | 行号 |
| `data.records[].bankName` | string | 行名 |
| `data.records[].city` | string | 城市 |
| `data.records[].systemType` | string | 系统类型 |

## 6.4 单据录入

### 基本信息

```http
POST /api/cnaps/vouchers
```

| 项目 | 内容 |
|---|---|
| Tuxedo 服务 | `CNAPS5701E` |
| 是否写库 | 是 |
| 初始状态 | `10_PENDING_REVIEW` |
| 系统生成 | `billId`、`serialNo`、审计字段、版本号 |

### Body 参数

| 字段 | 类型 | 必输 | 默认值 | 说明 |
|---|---:|:---:|---|---|
| `workDate` | string | 是 | - | 工作日期，`yyyy-MM-dd` |
| `businessType` | string | 是 | - | 当前支持 `02102` |
| `accountPart1` | string | 是 | - | 付款账号一段 |
| `accountPart2` | string | 是 | - | 付款账号二段 |
| `accountPart3` | string | 是 | - | 付款账号三段 |
| `accountName` | string | 否 | - | 付款账户户名 |
| `payerName` | string | 否 | - | 付款人名称 |
| `payerAddress` | string | 否 | - | 付款人地址，最长 256 个字符 |
| `payerBankName` | string | 否 | - | 付款人开户行，最长 128 个字符 |
| `payeeAccountNo` | string | 是 | - | 收款账号 |
| `payeeName` | string | 是 | - | 收款人名称 |
| `payeeAddress` | string | 否 | - | 收款人地址，最长 256 个字符 |
| `priority` | string | 是 | - | 当前支持 `NORM` |
| `receiveBankNo` | string | 否 | - | 接收行号 |
| `receiveBankName` | string | 否 | - | 接收行名称 |
| `systemType` | string | 是 | - | 当前支持 `CNAPS` |
| `amount` | string | 是 | - | 大于 0，最多两位小数 |
| `debitMode` | string | 否 | `1` | 扣收方式 |
| `feeAmount` | string | 否 | `0.00` | 不小于 0，最多两位小数 |
| `feeChargeMode` | string | 否 | `1` | 手续费方式 |
| `sendMode` | string | 否 | `0` | 发送方式 |
| `faxFlag` | string | 否 | `0` | 传真标志 |
| `voucherNo` | string | 否 | - | 凭证号 |
| `remark` | string | 否 | - | 备注 |

### 请求示例

```bash
curl -X POST "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "workDate": "2026-07-13",
    "businessType": "02102",
    "accountPart1": "404045",
    "accountPart2": "00772",
    "accountPart3": "000000000001",
    "accountName": "付款账户户名",
    "payerName": "付款人名称",
    "payerAddress": "上海市浦东新区示例路 1 号",
    "payerBankName": "中国示例银行上海分行",
    "payeeAccountNo": "622200000000000001",
    "payeeName": "收款人名称",
    "payeeAddress": "北京市朝阳区示例路 2 号",
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
    "voucherNo": "PZ202607130001",
    "remark": "验证录入"
  }'
```

### 成功响应示例

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

### 响应字段说明

写接口返回单据处理结果，完整公共字段见第 7 章。三个新增字段由录入接口保存，并由详情接口返回。

必填字段缺失返回 `2001`；日期、金额或长度不合法返回 `2002`；字典值无效返回 `2003`。

## 6.5 单据修改

### 基本信息

```http
PUT /api/cnaps/vouchers/{billId}
```

| 项目 | 内容 |
|---|---|
| Tuxedo 服务 | `CNAPS5701U` |
| 是否写库 | 是 |
| 允许状态 | `10_PENDING_REVIEW`、`30_REVIEW_REJECTED` |
| 修改后状态 | `10_PENDING_REVIEW` |

### Path 参数

| 参数 | 类型 | 必输 | 说明 |
|---|---:|:---:|---|
| `billId` | string | 是 | 单据编号 |

### Body 参数

Body 可传第 6.4 节中的业务字段，全部为可选。`billId`、`serialNo`、操作员、机构、状态、版本和审计时间不能由客户端覆盖。

三个新增字段的修改语义：

- 字段未传时保留原值；
- 传非空字符串时覆盖原值；
- 传空字符串 `""` 时清空字段。

### 请求示例

```bash
curl -X PUT "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607137720002000" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "workDate": "2026-07-13",
    "payerAddress": "上海市浦东新区示例路 3 号",
    "payerBankName": "中国示例银行上海分行营业部",
    "payeeName": "收款人名称-修改后",
    "payeeAddress": "",
    "amount": "5800.00",
    "remark": "退回后修改"
  }'
```

### 成功响应示例

```json
{
  "respCode": "0000",
  "respMsg": "修改成功，待复核",
  "data": {
    "billId": "B202607137720002000",
    "serialNo": "0002000",
    "workDate": "2026-07-13",
    "status": "10_PENDING_REVIEW",
    "amount": "5800.00",
    "lastAction": "UPDATE",
    "versionNo": 2
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---:|---|
| `data.billId` | string | 单据编号，不因修改工作日期而变化 |
| `data.serialNo` | string | 流水号，不因修改而变化 |
| `data.status` | string | 修改成功后为待复核 |
| `data.versionNo` | number | 在原版本上加 1 |

单据不存在返回 `3001`；当前状态不可修改返回 `3003`。

## 6.6 单据删除

### 基本信息

```http
POST /api/cnaps/vouchers/{billId}/delete
```

| 项目 | 内容 |
|---|---|
| Tuxedo 服务 | `CNAPS5701D` |
| 是否写库 | 是，逻辑删除 |
| 允许状态 | `10_PENDING_REVIEW`、`30_REVIEW_REJECTED` |
| 删除后状态 | `40_DELETED` |

### Path 和 Body 参数

| 参数 | 位置 | 类型 | 必输 | 说明 |
|---|---|---:|:---:|---|
| `billId` | Path | string | 是 | 单据编号 |
| `deleteReason` | Body | string | 否 | 删除原因 |

### 请求示例

```bash
curl -X POST "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607137720002000/delete" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"deleteReason":"录入错误"}'
```

### 成功响应示例

```json
{
  "respCode": "0000",
  "respMsg": "删除成功",
  "data": {
    "billId": "B202607137720002000",
    "status": "40_DELETED",
    "deleteReason": "录入错误",
    "lastAction": "DELETE",
    "versionNo": 2
  }
}
```

### 响应字段说明

删除不会物理移除数据库记录。单据不存在返回 `3001`，当前状态不可删除返回 `3003`。

## 6.7 通用查询

### 基本信息

```http
POST /api/cnaps/vouchers/query
```

| 项目 | 内容 |
|---|---|
| Tuxedo 服务 | `CNAPS4609Q` |
| 是否写库 | 否 |
| 用途 | 按工作日期、状态和单据信息分页查询 |

### Body 参数

| 参数 | 类型 | 必输 | 默认值 | 说明 |
|---|---:|:---:|---:|---|
| `startWorkDate` | string | 否 | - | 工作日期下界，格式 `yyyy-MM-dd`，包含该日期 |
| `endWorkDate` | string | 否 | - | 工作日期上界，格式 `yyyy-MM-dd`，包含该日期 |
| `status` | string | 否 | - | 状态精确匹配 |
| `serialNo` | string | 否 | - | 流水号精确匹配 |
| `voucherNo` | string | 否 | - | 凭证号精确匹配 |
| `payeeName` | string | 否 | - | 收款人名称模糊匹配 |
| `payeeAccountNo` | string | 否 | - | 收款账号精确匹配 |
| `includeDeleted` | boolean | 否 | `false` | 是否包含逻辑删除记录 |
| `pageNo` | number | 否 | `1` | 页码 |
| `pageSize` | number | 否 | `10` | 每页条数 |

### 请求示例

```bash
curl -X POST "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/query" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "startWorkDate": "2026-07-10",
    "endWorkDate": "2026-07-13",
    "status": "10_PENDING_REVIEW",
    "pageNo": 1,
    "pageSize": 10
  }'
```

`startWorkDate` 和 `endWorkDate` 均包含边界，可单独使用；两者都不传或仅传空白值时，不按工作日期筛选。日期格式非法、日期不存在或开始日期晚于结束日期时返回 `2002`。请求 Body 只要包含旧字段 `workDate`（包括 `null`、空字符串或纯空白值），即返回 HTTP 400 / `2002`：`列表查询不支持 workDate，请使用 startWorkDate/endWorkDate`。

### 成功响应示例

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
        "billId": "B202607137720002000",
        "workDate": "2026-07-13",
        "serialNo": "0002000",
        "voucherNo": "PZ202607130001",
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

### 响应字段说明

| 字段 | 类型 | 说明 |
|---|---:|---|
| `data.pageNo` | number | 当前页码 |
| `data.pageSize` | number | 每页条数 |
| `data.total` | number | 符合条件的总记录数 |
| `data.records` | array | 单据列表记录 |

列表记录不返回 `payerAddress`、`payeeAddress`、`payerBankName`。默认排除 `40_DELETED`。

## 6.8 待复核查询

### 基本信息

```http
POST /api/cnaps/vouchers/review-list
```

| 项目 | 内容 |
|---|---|
| Tuxedo 服务 | `CNAPS5702Q` |
| 是否写库 | 否 |
| 固定状态 | `10_PENDING_REVIEW` |

### Body 参数

| 参数 | 类型 | 必输 | 默认值 | 说明 |
|---|---:|:---:|---:|---|
| `startWorkDate` | string | 否 | - | 工作日期下界，格式 `yyyy-MM-dd`，包含该日期 |
| `endWorkDate` | string | 否 | - | 工作日期上界，格式 `yyyy-MM-dd`，包含该日期 |
| `serialNo` | string | 否 | - | 流水号精确匹配 |
| `pageNo` | number | 否 | `1` | 页码 |
| `pageSize` | number | 否 | `10` | 每页条数 |

### 请求示例

```bash
curl -X POST "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/review-list" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "startWorkDate": "2026-07-10",
    "pageNo": 1,
    "pageSize": 10
  }'
```

日期筛选规则与通用查询一致：范围边界包含，可只传一端，两端都不传或仅传空白值时查询全部工作日期。非法日期或倒序范围返回 `2002`。请求 Body 只要包含旧字段 `workDate`（包括 `null`、空字符串或纯空白值），即返回 HTTP 400 / `2002`：`列表查询不支持 workDate，请使用 startWorkDate/endWorkDate`。

### 成功响应示例

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
        "billId": "B202607137720002000",
        "workDate": "2026-07-13",
        "serialNo": "0002000",
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

### 响应字段说明

分页字段与通用查询一致，只返回待复核记录。列表记录不返回三个地址/开户行新增字段。

## 6.9 单据详情

### 基本信息

```http
GET /api/cnaps/vouchers/{billId}
```

| 项目 | 内容 |
|---|---|
| Tuxedo 服务 | `CNAPS5702I` |
| 是否写库 | 否 |
| 用途 | 返回单据完整字段和最后操作信息 |

### Path 参数

| 参数 | 类型 | 必输 | 说明 |
|---|---:|:---:|---|
| `billId` | string | 是 | 单据编号 |

### 请求示例

```bash
curl -X GET "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607137720002000"
```

### 成功响应示例

```json
{
  "respCode": "0000",
  "respMsg": "查询成功",
  "data": {
    "billId": "B202607137720002000",
    "workDate": "2026-07-13",
    "branchNo": "772",
    "operatorNo": "77210021",
    "serialNo": "0002000",
    "businessType": "02102",
    "accountPart1": "404045",
    "accountPart2": "00772",
    "accountPart3": "000000000001",
    "accountName": "付款账户户名",
    "payerName": "付款人名称",
    "payerAddress": "上海市浦东新区示例路 1 号",
    "payerBankName": "中国示例银行上海分行",
    "payeeAccountNo": "622200000000000001",
    "payeeName": "收款人名称",
    "payeeAddress": "北京市朝阳区示例路 2 号",
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
    "voucherNo": "PZ202607130001",
    "remark": "验证录入",
    "status": "10_PENDING_REVIEW",
    "lastAction": "CREATE",
    "lastOperatorNo": "77210021",
    "versionNo": 1
  }
}
```

### 响应字段说明

详情返回第 7 章定义的完整单据字段，包括三个新增字段。单据不存在返回 `3001`。

## 6.10 复核通过

### 基本信息

```http
POST /api/cnaps/vouchers/{billId}/review-pass
```

| 项目 | 内容 |
|---|---|
| Tuxedo 服务 | `CNAPS5702A` |
| 是否写库 | 是 |
| 允许状态 | `10_PENDING_REVIEW` |
| 复核后状态 | `20_REVIEW_APPROVED` |

### Path 和 Body 参数

| 参数 | 位置 | 类型 | 必输 | 说明 |
|---|---|---:|:---:|---|
| `billId` | Path | string | 是 | 单据编号 |
| `reviewComment` | Body | string | 否 | 复核意见 |

### 请求示例

```bash
curl -X POST "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607137720002000/review-pass" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"reviewComment":"复核通过"}'
```

### 成功响应示例

```json
{
  "respCode": "0000",
  "respMsg": "操作已成功",
  "data": {
    "billId": "B202607137720002000",
    "status": "20_REVIEW_APPROVED",
    "checkerNo": "77210021",
    "reviewComment": "复核通过",
    "lastAction": "REVIEW_PASS",
    "versionNo": 2
  }
}
```

### 响应字段说明

本 POC 使用服务器端固定操作员，不校验复核人与录入人是否相同。单据不存在返回 `3001`，状态已变化返回 `3004`。

## 6.11 复核退回

### 基本信息

```http
POST /api/cnaps/vouchers/{billId}/review-return
```

| 项目 | 内容 |
|---|---|
| Tuxedo 服务 | `CNAPS5702R` |
| 是否写库 | 是 |
| 允许状态 | `10_PENDING_REVIEW` |
| 退回后状态 | `30_REVIEW_REJECTED` |

### Path 和 Body 参数

| 参数 | 位置 | 类型 | 必输 | 说明 |
|---|---|---:|:---:|---|
| `billId` | Path | string | 是 | 单据编号 |
| `rejectReason` | Body | string | 是 | 退回原因，不能为空 |
| `reviewComment` | Body | string | 否 | 复核意见 |

### 请求示例

```bash
curl -X POST "http://localhost:8080/ruisui-bank-sim/api/cnaps/vouchers/B202607137720002000/review-return" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{
    "rejectReason": "收款人户名不完整",
    "reviewComment": "请修改后重新提交"
  }'
```

### 成功响应示例

```json
{
  "respCode": "0000",
  "respMsg": "复核退回成功",
  "data": {
    "billId": "B202607137720002000",
    "status": "30_REVIEW_REJECTED",
    "rejectReason": "收款人户名不完整",
    "reviewComment": "请修改后重新提交",
    "lastAction": "REVIEW_RETURN",
    "versionNo": 2
  }
}
```

### 响应字段说明

`rejectReason` 缺失或为空返回 `2001`；单据不存在返回 `3001`；状态已变化返回 `3004`。

---

## 7. 单据完整字段说明

| JSON 字段 | 类型 | 录入必输 | 修改 | 详情返回 | 说明 |
|---|---:|:---:|:---:|:---:|---|
| `billId` | string | 系统生成 | 不可改 | 是 | 单据编号 |
| `workDate` | string | 是 | 可选 | 是 | 工作日期 |
| `branchNo` | string | 服务器生成 | 不可改 | 是 | 固定 POC 机构 |
| `operatorNo` | string | 服务器生成 | 不可改 | 是 | 固定 POC 操作员 |
| `serialNo` | string | 系统生成 | 不可改 | 是 | 工作日机构流水号 |
| `businessType` | string | 是 | 可选 | 是 | 业务种类 |
| `accountPart1` | string | 是 | 可选 | 是 | 付款账号一段 |
| `accountPart2` | string | 是 | 可选 | 是 | 付款账号二段 |
| `accountPart3` | string | 是 | 可选 | 是 | 付款账号三段 |
| `accountName` | string | 否 | 可选 | 是 | 付款账户户名 |
| `payerName` | string | 否 | 可选 | 是 | 付款人名称 |
| `payerAddress` | string | 否 | 可选/可清空 | 是 | 付款人地址，最长 256 个字符 |
| `payerBankName` | string | 否 | 可选/可清空 | 是 | 付款人开户行，最长 128 个字符 |
| `payeeAccountNo` | string | 是 | 可选 | 是 | 收款账号 |
| `payeeName` | string | 是 | 可选 | 是 | 收款人名称 |
| `payeeAddress` | string | 否 | 可选/可清空 | 是 | 收款人地址，最长 256 个字符 |
| `priority` | string | 是 | 可选 | 是 | 优先级 |
| `receiveBankNo` | string | 否 | 可选 | 是 | 接收行号 |
| `receiveBankName` | string | 否 | 可选 | 是 | 接收行名称 |
| `systemType` | string | 是 | 可选 | 是 | 系统类型 |
| `amount` | string | 是 | 可选 | 是 | 金额，大于 0 |
| `debitMode` | string | 否 | 可选 | 是 | 扣收方式，默认 `1` |
| `feeAmount` | string | 否 | 可选 | 是 | 手续费，默认 `0.00` |
| `feeChargeMode` | string | 否 | 可选 | 是 | 手续费方式，默认 `1` |
| `sendMode` | string | 否 | 可选 | 是 | 发送方式，默认 `0` |
| `faxFlag` | string | 否 | 可选 | 是 | 传真标志，默认 `0` |
| `voucherNo` | string | 否 | 可选 | 是 | 凭证号 |
| `remark` | string | 否 | 可选 | 是 | 备注 |
| `status` | string | 系统生成 | 不可直接改 | 是 | 单据状态 |
| `checkerNo` | string | - | 系统维护 | 是 | 最近复核员 |
| `checkerTime` | string | - | 系统维护 | 是 | 最近复核时间 |
| `reviewComment` | string | - | 复核接口维护 | 是 | 复核意见 |
| `rejectReason` | string | - | 退回接口维护 | 是 | 退回原因 |
| `deleteReason` | string | - | 删除接口维护 | 是 | 删除原因 |
| `deleteOperatorNo` | string | - | 系统维护 | 是 | 删除操作员 |
| `deleteTime` | string | - | 系统维护 | 是 | 删除时间 |
| `lastAction` | string | 系统生成 | 系统维护 | 是 | 最后动作 |
| `lastOperatorNo` | string | 系统生成 | 系统维护 | 是 | 最后操作员 |
| `lastRequestId` | string | 系统生成 | 系统维护 | 是 | 内部请求流水 |
| `lastActionTime` | string | 系统生成 | 系统维护 | 是 | 最后动作时间 |
| `createdAt` | string | 系统生成 | 不可改 | 是 | 创建时间 |
| `updatedAt` | string | 系统生成 | 系统维护 | 是 | 更新时间 |
| `versionNo` | number | 系统生成 | 系统维护 | 是 | 乐观版本号 |

通用查询和待复核查询使用既有列表投影，不返回 `payerAddress`、`payeeAddress`、`payerBankName`；详情接口返回完整字段。

---

## 8. 错误码

| 错误码 | HTTP 状态 | 含义 | 典型场景 |
|---|---:|---|---|
| `0000` | 200 | 成功 | 交易成功 |
| `2001` | 400 | 必填字段缺失 | 录入缺字段、退回原因为空 |
| `2002` | 400 | 字段格式错误 | 日期、金额或字段长度错误 |
| `2003` | 400 | 字典值无效 | 业务种类、优先级或系统类型不支持 |
| `3001` | 404 | 单据不存在 | 详情、修改、删除或复核找不到单据 |
| `3003` | 409 | 当前状态不允许操作 | 已复核或已删除单据被修改/删除 |
| `3004` | 409 | 复核时状态已变化 | 重复复核或并发复核 |
| `4001` | 500 | 数据库错误 | Oracle 连接或 OCI/SQL 执行失败 |
| `4002` | 504 | Tuxedo 调用超时 | Jolt 调用超时 |
| `4003` | 503 | Tuxedo 服务不可用 | 服务未启动、路由失败或运行时缺失 |
| `9999` | 500 | 未分类错误 | 未分类异常 |

错误响应示例：

```json
{
  "respCode": "3003",
  "respMsg": "当前状态不允许操作",
  "data": null
}
```

---

## 9. 典型联调流程

### 9.1 录入后复核通过

```text
1. GET  /api/health
2. POST /api/cnaps/vouchers
3. POST /api/cnaps/vouchers/review-list
4. GET  /api/cnaps/vouchers/{billId}
5. POST /api/cnaps/vouchers/{billId}/review-pass
6. GET  /api/cnaps/vouchers/{billId}
```

预期状态：`10_PENDING_REVIEW` → `20_REVIEW_APPROVED`。

### 9.2 录入后复核退回，再修改提交

```text
1. POST /api/cnaps/vouchers
2. POST /api/cnaps/vouchers/{billId}/review-return
3. GET  /api/cnaps/vouchers/{billId}
4. PUT  /api/cnaps/vouchers/{billId}
5. GET  /api/cnaps/vouchers/{billId}
```

预期状态：`10_PENDING_REVIEW` → `30_REVIEW_REJECTED` → `10_PENDING_REVIEW`。

### 9.3 删除待复核或退回单据

```text
1. POST /api/cnaps/vouchers
2. POST /api/cnaps/vouchers/{billId}/delete
3. POST /api/cnaps/vouchers/query（Body: startWorkDate、endWorkDate、includeDeleted、pageNo、pageSize）
4. GET  /api/cnaps/vouchers/{billId}
```

预期状态：`10_PENDING_REVIEW` → `40_DELETED`。

---

## 10. POC 边界

- 不提供登录、登出、账号、角色、菜单或权限系统。
- 不校验复核人与录入人是否为不同用户。
- 固定操作员和机构只用于兼容内部 FML32 与审计字段。
- 不做真实核心扣账、真实 CNAPS 发送、真实外联系统或生产级审计。
- 只使用 Oracle 单表 `T_CNAPS_BILL_POC` 验证 CRUD 和状态流转。
- 本文档只描述公共 HTTP API，不扩展数据库部署或运维流程。
