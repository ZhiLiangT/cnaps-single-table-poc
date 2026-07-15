# CNAPS 凭证审核需求（POC 精简版）

> 版本：v1.3 ｜ 日期：2026-07-15 ｜ API 基线：`docs/cnaps-frontend-api.md` v0.5

## 1. 目标与边界

本期只验证后端单级审核闭环：

```text
创建 -> 待审核 -> 审核通过
创建 -> 待审核 -> 审核退回 -> 修改 -> 再次待审核
```

POC 原则：复用现有查询、分页、数据库表和状态字段，只新增无法复用的审核状态变更服务，保证 Mock 和真实 Jolt/Tuxedo/Oracle 链路可构建、部署和验证。

不包含：

- JSP、JavaScript、CSS 或浏览器页面。
- 登录、权限及录入审核分离。
- 审核意见、退回原因、批量或多级审核。
- 撤销、通知、外部工作流和真实 CNAPS 外发。
- 新数据库表、列、框架或依赖。

## 2. API 范围

| API | 用途 | Body |
| --- | --- | --- |
| `POST /api/cnaps/vouchers/review-list` | 查询待审核凭证 | 查询条件，可传 `{}` |
| `POST /api/cnaps/vouchers/{billId}/review-pass` | 审核通过 | 无 |
| `POST /api/cnaps/vouchers/{billId}/review-return` | 审核退回 | 无 |

三个路径只支持 POST；使用 GET 时返回 HTTP 405。

## 3. 状态规则

| 当前状态 | 动作 | 目标状态 |
| --- | --- | --- |
| `10_PENDING_REVIEW` | 审核通过 | `20_REVIEW_APPROVED` |
| `10_PENDING_REVIEW` | 审核退回 | `30_REVIEW_REJECTED` |
| `30_REVIEW_REJECTED` | 使用现有修改接口 | `10_PENDING_REVIEW` |

- 只有 `10_PENDING_REVIEW` 可以审核。
- 非待审核状态执行审核返回 `3004`。
- `00_DRAFT` 已退役，新代码不得写入。
- 现有修改、删除和查询的其他状态规则保持不变。

## 4. 接口要求

### 4.1 待审核列表

请求字段：

| 字段 | 类型 | 默认值 | 规则 |
| --- | --- | --- | --- |
| `startWorkDate` | string | 无 | `yyyy-MM-dd`，包含该日 |
| `endWorkDate` | string | 无 | `yyyy-MM-dd`，包含该日 |
| `serialNo` | string | 无 | 精确匹配 |
| `pageNo` | number | 1 | 正整数 |
| `pageSize` | number | 10 | 1～100 |

要求：

- 日期可只传一端，开始日期不能晚于结束日期。
- 不支持 `workDate` 作为列表筛选字段。
- 服务端必须覆盖客户端状态，固定查询 `10_PENDING_REVIEW`。
- 查询范围继续受 WebFE 配置的 `branchNo` 限制。
- 响应沿用通用查询的 `pageNo/pageSize/total/records` 分页结构。
- `records` 字段以 `cnaps-frontend-api.md` 的待复核列表定义为准。

### 4.2 审核动作

| 动作 | 成功状态 | `lastAction` |
| --- | --- | --- |
| 审核通过 | `20_REVIEW_APPROVED` | `REVIEW_PASS` |
| 审核退回 | `30_REVIEW_REJECTED` | `REVIEW_RETURN` |

两个动作：

- `billId` 只从 URL 路径获取。
- 不接收请求 Body、目标状态、审核意见或退回原因。
- 成功响应的 `data` 只需包含 `billId`、`status`、`checkerNo`、`checkerTime`、`lastAction`、`versionNo`。
- 完整凭证通过现有详情接口查询。

## 5. 后端复用要求

```text
review-list   -> 复用 CNAPS4609Q，WebFE 强制 STATUS=10_PENDING_REVIEW
review-pass   -> 新增 CNAPS5702A
review-return -> 新增 CNAPS5702R
```

- 不新增 `CNAPS5702Q`。
- 不修改 `cnaps_query.c` 和 Jolt 分页解析。
- A/R 两个服务放在同一个 `cnaps_review.c`，共用状态变更函数。
- 不增加审核列表 Jolt metadata，只增加 A/R 动作 metadata。

## 6. 上下文、审计与并发

`operatorNo`、`branchNo` 和 `requestId` 由 WebFE 提供并覆盖客户端同名字段。

审核成功必须更新：

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

事务要求：

1. 读取凭证并校验待审核状态。
2. 按 `BILL_ID + VERSION_NO` 乐观锁更新。
3. 更新 0 行时回滚并返回 `3004`。
4. 数据库错误回滚并返回 `4001`。
5. 成功后重新读取数据库记录，再返回动作摘要。
6. Mock 的状态检查和更新必须原子执行。

## 7. 错误码

| 场景 | 响应码 | HTTP |
| --- | --- | ---: |
| 成功 | `0000` | 200 |
| 缺少 `billId` | `2001` | 400 |
| 日期错误 | `2002` | 400 |
| 凭证不存在 | `3001` | 404 |
| 状态或版本冲突 | `3004` | 409 |
| 数据库错误 | `4001` | 500 |
| Tuxedo 超时/不可用 | `4002` / `4003` | 504 / 503 |

## 8. POC 验收标准

- `review-list` 支持空条件、日期和流水号过滤，只返回待审核凭证。
- 客户端传入其他状态时仍只查询待审核凭证。
- 待审核凭证可无 Body 审核通过或退回，审计字段和版本正确。
- 不存在的凭证返回 `3001`，重复或非待审核操作返回 `3004`。
- 同一凭证并发通过和退回时只允许一个成功，版本只增加一次。
- 退回凭证修改后重新进入待审核，审核员和审核时间被清空。
- Mock 和真实 Tuxedo 主流程一致。
- Maven 测试及 WAR 打包、Tuxedo C 编译、UBB/Jolt 注册、部署和冒烟测试全部通过。
- 未修改任何前端页面或静态资源。
