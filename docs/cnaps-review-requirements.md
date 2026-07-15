# CNAPS 凭证审核需求（POC 生产代码版）

> 版本：v1.4 ｜ 日期：2026-07-15 ｜ API 基线：`docs/cnaps-frontend-api.md` v0.5

## 1. 目标

实现 CNAPS 凭证单级审核闭环：

```text
创建 -> 待审核 -> 审核通过
创建 -> 待审核 -> 审核退回 -> 修改 -> 再次待审核
```

本期为纯后端 POC，只实现 WebFE、Jolt、Tuxedo C 和 Oracle 真实调用链。

## 2. 范围

本期实现：

- 待审核凭证分页查询。
- 单笔审核通过。
- 单笔审核退回。
- 审核状态、审核人、审核时间和版本更新。
- 重复审核和并发状态变化处理。

本期不实现：

- JSP、JavaScript、CSS 或浏览器页面。
- 登录、权限及录入审核分离。
- 审核意见、退回原因、批量或多级审核。
- 撤销、通知、外部工作流和真实 CNAPS 外发。
- 新数据库表、列、框架或依赖。

## 3. API

| API | Body | 说明 |
| --- | --- | --- |
| `POST /api/cnaps/vouchers/review-list` | 查询条件或 `{}` | 只查询待审核凭证 |
| `POST /api/cnaps/vouchers/{billId}/review-pass` | 无 | 审核通过 |
| `POST /api/cnaps/vouchers/{billId}/review-return` | 无 | 审核退回 |

三个路径只支持 POST，GET 返回 HTTP 405。

### 3.1 待审核列表

| 字段 | 类型 | 默认值 | 规则 |
| --- | --- | --- | --- |
| `startWorkDate` | string | 无 | `yyyy-MM-dd`，包含该日 |
| `endWorkDate` | string | 无 | `yyyy-MM-dd`，包含该日 |
| `serialNo` | string | 无 | 精确匹配 |
| `pageNo` | number | 1 | 正整数 |
| `pageSize` | number | 10 | 1～100 |

- 日期可只传一端，开始日期不能晚于结束日期。
- 不支持 `workDate` 作为列表筛选字段。
- 服务端必须覆盖客户端状态，固定查询 `10_PENDING_REVIEW`。
- 查询范围继续由 WebFE 配置的 `branchNo` 限制。
- 响应沿用通用查询的 `pageNo/pageSize/total/records` 结构。
- `records` 字段以 API 文档“待复核查询”章节为准。

### 3.2 审核动作

- `billId` 只从 URL 路径获取。
- 不接收请求 Body、目标状态、审核意见或退回原因。
- 成功响应的 `data` 只返回：

```text
billId, status, checkerNo, checkerTime, lastAction, versionNo
```

- 完整凭证继续使用现有详情接口查询。

## 4. 状态规则

| 当前状态 | 动作 | 目标状态 | `lastAction` |
| --- | --- | --- | --- |
| `10_PENDING_REVIEW` | 审核通过 | `20_REVIEW_APPROVED` | `REVIEW_PASS` |
| `10_PENDING_REVIEW` | 审核退回 | `30_REVIEW_REJECTED` | `REVIEW_RETURN` |
| `30_REVIEW_REJECTED` | 现有修改接口 | `10_PENDING_REVIEW` | `UPDATE` |

- 只有 `10_PENDING_REVIEW` 可以审核。
- 非待审核状态执行审核返回 `3004`。
- `00_DRAFT` 已退役，新代码不得写入。
- 其他既有状态规则保持不变。

## 5. 服务复用

```text
review-list   -> CNAPS4609Q，WebFE 强制 STATUS=10_PENDING_REVIEW
review-pass   -> 新增 CNAPS5702A
review-return -> 新增 CNAPS5702R
```

- 不新增 `CNAPS5702Q`。
- 不修改现有查询 C 服务和分页读取逻辑。
- A/R 两个服务在同一个 `cnaps_review.c` 中共用状态变更函数。

## 6. 服务端上下文与审计

`operatorNo`、`branchNo` 和 `requestId` 由 WebFE 提供，并覆盖客户端同名字段。

审核成功更新：

| 字段 | 值 |
| --- | --- |
| `STATUS` | 目标状态 |
| `CHECKER_NO` | WebFE `operatorNo` |
| `CHECKER_TIME` | Oracle `SYSTIMESTAMP` |
| `LAST_ACTION` | `REVIEW_PASS` / `REVIEW_RETURN` |
| `LAST_OPERATOR_NO` | WebFE `operatorNo` |
| `LAST_REQUEST_ID` | WebFE `requestId` |
| `LAST_ACTION_TIME`、`UPDATED_AT` | Oracle `SYSTIMESTAMP` |
| `VERSION_NO` | 原值加 1 |

## 7. 事务与并发

1. 读取凭证并校验当前状态为待审核。
2. 按 `BILL_ID + VERSION_NO` 更新。
3. 更新 0 行表示状态或版本已变化，回滚并返回 `3004`。
4. 数据库错误回滚并返回 `4001`。
5. 成功后重新读取凭证，再返回审核摘要。

同一凭证并发执行通过和退回时，只允许一个请求成功，`VERSION_NO` 只增加一次。

## 8. 错误码

| 场景 | 响应码 | HTTP |
| --- | --- | ---: |
| 成功 | `0000` | 200 |
| 缺少 `billId` | `2001` | 400 |
| 日期错误 | `2002` | 400 |
| 凭证不存在 | `3001` | 404 |
| 状态或版本冲突 | `3004` | 409 |
| 数据库错误 | `4001` | 500 |
| Tuxedo 超时/不可用 | `4002` / `4003` | 504 / 503 |

## 9. 完成标准

- 三个 POST API 均按本需求返回结果。
- 待审核列表始终只返回 `10_PENDING_REVIEW`。
- 审核通过、退回的状态、审计字段和版本正确。
- 重复审核、凭证不存在和并发冲突返回正确错误码。
- 退回凭证修改后重新进入待审核并清空审核人和审核时间。
- 未增加 `CNAPS5702Q`，未修改前端资源，未扩大 POC 范围。
