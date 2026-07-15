# CNAPS 凭证审核功能需求文档（POC 精简版）

> 文档版本：v1.2<br>
> 编写日期：2026-07-15<br>
> 需求状态：待评审<br>
> 适用项目：CNAPS 单表 POC<br>
> 接口基线：`docs/cnaps-frontend-api.md` v0.5

## 1. 目标

本期只验证 CNAPS 凭证的单级审核闭环：

```text
创建凭证 -> 待审核 -> 审核通过
创建凭证 -> 待审核 -> 审核退回 -> 修改 -> 再次待审核
```

项目为后端 POC，不开发或修改 JSP、JavaScript、CSS 和浏览器页面。`web-fe` 模块仅作为 HTTP API 网关并打包为 WAR。

## 2. POC 原则

1. 复用现有查询、分页、数据库表和状态字段。
2. 只新增无法复用的审核状态变更服务。
3. 不为 POC 引入新的数据库表、列、框架或依赖。
4. 测试覆盖主流程、错误状态和一次并发冲突，不追求生产级全场景自动化。
5. 保持真实 Jolt/Tuxedo/Oracle 链路可编译和部署。

## 3. 本期范围

### 3.1 必须实现

| API | 用途 |
| --- | --- |
| `POST /api/cnaps/vouchers/review-list` | 查询待审核凭证 |
| `POST /api/cnaps/vouchers/{billId}/review-pass` | 审核通过 |
| `POST /api/cnaps/vouchers/{billId}/review-return` | 审核退回 |

同时实现：

- 凭证状态流转。
- 审核操作员和审核时间记录。
- `VERSION_NO` 乐观锁。
- Mock 模式。
- Tuxedo C 服务、UBBCONFIG 和 Jolt metadata 注册。
- 最小必要单元测试、契约测试和冒烟测试。
- Maven、C 服务和完整部署验证。

### 3.2 不实现

- 任何前端页面和静态资源。
- 登录、账号、角色和权限。
- 审核人与录入人分离校验。
- 审核意见、退回原因和删除原因输入。
- 批量审核、多级审核、撤销审核和反审核。
- 消息通知和外部工作流。
- 真实扣账或 CNAPS 外发。

## 4. 状态模型

| 状态码 | 含义 | 可执行操作 |
| --- | --- | --- |
| `10_PENDING_REVIEW` | 待审核 | 修改、删除、审核通过、审核退回 |
| `20_REVIEW_APPROVED` | 审核通过 | 查询、查看详情 |
| `30_REVIEW_REJECTED` | 审核退回 | 修改、删除 |
| `40_DELETED` | 已删除 | 查看详情、显式查询 |

状态流转：

```text
创建 -> 10_PENDING_REVIEW

10_PENDING_REVIEW
  -> 审核通过 -> 20_REVIEW_APPROVED
  -> 审核退回 -> 30_REVIEW_REJECTED
  -> 删除     -> 40_DELETED

30_REVIEW_REJECTED
  -> 修改 -> 10_PENDING_REVIEW
  -> 删除 -> 40_DELETED
```

`00_DRAFT` 已退役，新代码不得写入。

## 5. API 要求

### 5.1 待审核列表

```http
POST /api/cnaps/vouchers/review-list
Content-Type: application/json; charset=UTF-8
```

请求字段：

| 字段 | 类型 | 必填 | 默认值 | 规则 |
| --- | --- | :---: | --- | --- |
| `startWorkDate` | string | 否 | 无 | `yyyy-MM-dd`，包含该日 |
| `endWorkDate` | string | 否 | 无 | `yyyy-MM-dd`，包含该日 |
| `serialNo` | string | 否 | 无 | 精确匹配 |
| `pageNo` | number | 否 | 1 | 页码 |
| `pageSize` | number | 否 | 10 | 最大 100 |

规则：

- 空条件允许传 `{}`。
- 日期可只传一端。
- 开始日期不得晚于结束日期。
- 不支持 `workDate` 列表筛选字段。
- 调用方不得指定其他状态。
- 服务端固定查询 `10_PENDING_REVIEW`。
- 查询范围由服务端配置的 `branchNo` 限制。

成功响应沿用通用查询的分页结构：

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
        "billId": "B202607157720002000",
        "workDate": "2026-07-15",
        "serialNo": "0002000",
        "voucherNo": "PZ202607150001",
        "payeeAccountNo": "622200000000000001",
        "payeeName": "测试收款人",
        "amount": "5600.00",
        "status": "10_PENDING_REVIEW",
        "versionNo": 1
      }
    ]
  }
}
```

### 5.2 审核通过

```http
POST /api/cnaps/vouchers/{billId}/review-pass
```

- 无请求体。
- 仅 `10_PENDING_REVIEW` 可操作。
- 成功后状态为 `20_REVIEW_APPROVED`。
- `lastAction` 为 `REVIEW_PASS`。

### 5.3 审核退回

```http
POST /api/cnaps/vouchers/{billId}/review-return
```

- 无请求体。
- 不需要退回原因或审核意见。
- 仅 `10_PENDING_REVIEW` 可操作。
- 成功后状态为 `30_REVIEW_REJECTED`。
- `lastAction` 为 `REVIEW_RETURN`。

### 5.4 审核动作响应

POC 只要求返回必要结果字段：

```json
{
  "respCode": "0000",
  "respMsg": "审核通过成功",
  "data": {
    "billId": "B202607157720002000",
    "status": "20_REVIEW_APPROVED",
    "checkerNo": "77210021",
    "checkerTime": "2026-07-15 10:30:00",
    "lastAction": "REVIEW_PASS",
    "versionNo": 2
  }
}
```

动作完成后的完整凭证信息通过现有详情接口查询。

## 6. 内部复用要求

### 6.1 待审核列表

`review-list` 在 WebFE 中强制加入：

```json
{
  "status": "10_PENDING_REVIEW"
}
```

然后复用现有 Tuxedo 查询服务：

```text
CNAPS4609Q
```

本期不新增 `CNAPS5702Q`，不修改原生查询 C 服务，不增加审核列表 Jolt metadata。

### 6.2 审核动作

只新增：

```text
CNAPS5702A：审核通过
CNAPS5702R：审核退回
```

两个服务在同一个 C 文件中复用公共状态变更函数。

## 7. 服务端上下文

- `operatorNo` 由 WebFE 配置提供。
- `branchNo` 由 WebFE 配置提供。
- `requestId` 由 WebFE 生成。
- HTTP 调用方不得覆盖这些字段。
- HTTP 调用方不得传目标状态或审核审计字段。

## 8. 审计字段

审核成功必须更新：

| 字段 | 值 |
| --- | --- |
| `STATUS` | 目标状态 |
| `CHECKER_NO` | WebFE 配置操作员 |
| `CHECKER_TIME` | Oracle `SYSTIMESTAMP` |
| `LAST_ACTION` | `REVIEW_PASS` 或 `REVIEW_RETURN` |
| `LAST_OPERATOR_NO` | WebFE 配置操作员 |
| `LAST_REQUEST_ID` | WebFE 请求流水 |
| `LAST_ACTION_TIME` | Oracle `SYSTIMESTAMP` |
| `UPDATED_AT` | Oracle `SYSTIMESTAMP` |
| `VERSION_NO` | 原值加 1 |

退回凭证修改后，继续使用现有逻辑清空审核员和审核时间并重新进入待审核。

## 9. 并发和事务

1. 审核前检查数据库状态为 `10_PENDING_REVIEW`。
2. 数据库更新条件包含 `BILL_ID` 和读取时的 `VERSION_NO`。
3. 更新 0 行表示状态或版本已变化，回滚并返回 `3004`。
4. 数据库错误回滚并返回 `4001`。
5. 审核成功后重新查询数据库记录，再返回动作摘要。
6. Mock 使用同步块保证状态检查和修改原子执行。

## 10. 错误码

| 场景 | respCode | HTTP |
| --- | --- | ---: |
| 成功 | `0000` | 200 |
| 缺少 `billId` | `2001` | 400 |
| 日期格式错误或范围倒置 | `2002` | 400 |
| 凭证不存在 | `3001` | 404 |
| 审核状态已变化 | `3004` | 409 |
| 数据库错误 | `4001` | 500 |
| Tuxedo 超时 | `4002` | 504 |
| Tuxedo 不可用 | `4003` | 503 |
| 未分类错误 | `9999` | 500 |

## 11. POC 验收标准

### 11.1 查询

- 空条件查询返回分页结果。
- 只返回 `10_PENDING_REVIEW`。
- 日期范围和流水号过滤正确。
- 调用方传入其他 `status` 时仍只查询待审核。

### 11.2 审核通过

- 待审核凭证无 Body 调用成功。
- 状态变为 `20_REVIEW_APPROVED`。
- 审核员、审核时间、动作和版本正确。
- 再次审核返回 `3004`。

### 11.3 审核退回

- 不传原因和意见也能成功。
- 状态变为 `30_REVIEW_REJECTED`。
- 修改后重新进入 `10_PENDING_REVIEW`。

### 11.4 并发

- 同一凭证并发执行通过和退回，只允许一个成功。
- 另一个返回 `3004`。
- 最终 `VERSION_NO` 只增加一次。

### 11.5 构建部署

- Maven 测试和 WAR 打包成功。
- Tuxedo C 编译成功。
- `CNAPS5702A`、`CNAPS5702R` 注册成功。
- Jolt metadata 加载成功。
- 健康检查全部为 `UP`。
- 冒烟测试完成通过和退回主流程。

## 12. 完成定义

- 三个 HTTP API 可用。
- 待审核列表复用 `CNAPS4609Q`。
- 只新增两个审核动作 Tuxedo 服务。
- Mock 与真实服务主流程一致。
- 最小必要测试通过。
- 完整部署和冒烟测试通过。
- 未修改任何 JSP、JavaScript 或 CSS。
