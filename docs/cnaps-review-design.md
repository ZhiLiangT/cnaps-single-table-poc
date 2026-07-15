# CNAPS 凭证审核设计（POC 执行版）

> 版本：v1.3 ｜ 日期：2026-07-15 ｜ 需求：`docs/cnaps-review-requirements.md` v1.3

## 1. 实现结论

三个 HTTP API 保持不变，但底层只增加两个状态变更服务：

| HTTP API | Tuxedo 服务 | 实现方式 |
| --- | --- | --- |
| `POST /api/cnaps/vouchers/review-list` | `CNAPS4609Q` | Servlet 强制待审核状态，复用现有分页查询 |
| `POST /api/cnaps/vouchers/{billId}/review-pass` | `CNAPS5702A` | 新增审核通过服务 |
| `POST /api/cnaps/vouchers/{billId}/review-return` | `CNAPS5702R` | 新增审核退回服务 |

明确不改：

- 不新增 `CNAPS5702Q`。
- 不修改 `cnaps_query.c`、`JoltTuxedoClient.java` 和 `TuxedoResponseMapper.java`。
- 不修改数据库结构、FML 字段表、状态常量和分页协议。
- 不修改 JSP、JavaScript、CSS，不增加依赖或框架。
- 不从历史提交整体恢复旧审核代码。

API 字段、通用响应和错误码以 `cnaps-frontend-api.md` 为准；业务边界和验收标准以需求文档为准。本文只定义代码实现。

## 2. 调用链与状态

```text
HTTP -> CnapsVoucherServlet -> TuxedoRequestMapper
     -> MockTuxedoClient 或 JoltTuxedoClient
     -> CNAPS4609Q / CNAPS5702A / CNAPS5702R
     -> T_CNAPS_BILL_POC
```

| 动作 | 前置状态 | 目标状态 | 动作值 |
| --- | --- | --- | --- |
| 通过 | `10_PENDING_REVIEW` | `20_REVIEW_APPROVED` | `REVIEW_PASS` |
| 退回 | `10_PENDING_REVIEW` | `30_REVIEW_REJECTED` | `REVIEW_RETURN` |

动作成功只返回：`billId`、`status`、`checkerNo`、`checkerTime`、`lastAction`、`versionNo`。完整凭证使用现有详情接口查询。

## 3. 修改文件

### 3.1 生产代码与配置

| 文件 | 修改 |
| --- | --- |
| `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java` | 开放三个 POST；列表强制待审核状态 |
| `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java` | 增加三个路由映射 |
| `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java` | 增加 A/R 状态变更 |
| `tuxedo-server/src/services/cnaps_review.c` | 新增两个审核服务和共享函数 |
| `tuxedo-server/src/common/db_helper.c` | 审核时间使用 Oracle 时间 |
| `tuxedo-server/src/cnapspocsvr.c` | 声明 A/R 服务 |
| `tuxedo-server/Makefile` | 注册 A/R 服务 |
| `tuxedo/UBBCONFIG` | 发布 A/R 服务 |
| `tuxedo/jolt/cnaps_services.bulk` | 增加 A/R metadata |

### 3.2 测试与运维

| 文件 | 修改 |
| --- | --- |
| `BaseJsonServletTest.java` | POST 路由、强制状态、空 Body、GET 405 |
| `TuxedoRequestMapperTest.java` | 三个服务映射 |
| `MockTuxedoClientV03ContractTest.java` | 状态、审计、冲突和并发 |
| `TuxedoCSourceContractTest.java` | C 服务与 SQL 契约 |
| `DeploymentArtifactTest.java` | Makefile、UBB、metadata 契约 |
| `scripts/smoke-test.sh` | 通过和退回主流程 |
| `docs/cnaps-operations.md` | 部署验证命令和预期结果 |

测试应追加到现有测试类，不为每个场景新建文件。

## 4. WebFE 实现

### 4.1 `CnapsVoucherServlet`

1. 删除 `doPost` 中 `isRemovedReviewPath` 的 405 判断。
2. `isListPostPath` 同时识别 `/query` 和 `/review-list`。
3. 两个列表继续共用 `validateListFilters`，且不调用 `includeBillPath`。
4. JSON 解析和校验完成后，对 `/review-list` 执行：

```java
fields.put("status", "10_PENDING_REVIEW");
```

该写入必须晚于 Body 解析，以覆盖客户端伪造的状态。

5. A/R 属于非列表 POST，继续调用 `includeBillPath`，从 URL 注入 `billId`；允许空 Body。
6. `isRejectedGetPath` 必须拒绝：

```text
/api/cnaps/vouchers
/api/cnaps/vouchers/query
/api/cnaps/vouchers/review-list
*/review-pass
*/review-return
```

最后两项用于防止 GET 动作路径被详情路由误识别。

### 4.2 `TuxedoRequestMapper`

在凭证通配路由前处理精确列表路径，在通配路由内优先处理动作路径：

```text
POST /api/cnaps/vouchers/review-list       -> CNAPS4609Q
POST */review-pass                         -> CNAPS5702A
POST */review-return                       -> CNAPS5702R
```

不增加 reason/comment 字段。现有映射已覆盖 `billId/status/pageNo/pageSize`。

`REQUEST_ID`、`REQ_ID`、`OPERATOR_NO`、`BRANCH_NO` 继续由 `from` 方法最后写入，保证服务端上下文覆盖客户端同名字段。

### 4.3 无需修改的 Java 类

- `JoltTuxedoClient` 已将 `CNAPS4609Q` 识别为分页服务，并能读取动作所需字段。
- `TuxedoResponseMapper` 已支持六个动作响应字段。
- `BaseJsonServlet` 已将 `3004` 映射为 HTTP 409。

## 5. Mock 实现

`MockTuxedoClient.call` 增加：

```text
CNAPS5702A -> review(target=20_REVIEW_APPROVED, action=REVIEW_PASS)
CNAPS5702R -> review(target=30_REVIEW_REJECTED, action=REVIEW_RETURN)
```

不要增加 `CNAPS5702Q`；`CNAPS4609Q` 直接使用 Servlet 传入的 `STATUS`。

共享 `review` 方法按以下顺序执行：

1. `BILL_ID` 为空返回 `2001`。
2. 在同一同步块内读取、检查和更新 `vouchers`。
3. 不存在返回 `3001`。
4. 状态不是 `10_PENDING_REVIEW` 返回 `3004`。
5. 更新目标状态、`CHECKER_NO`、`CHECKER_TIME`、`LAST_ACTION`。
6. `VERSION_NO` 加 1，并复用 `touch` 更新最后操作审计字段。
7. 返回六个摘要字段，不返回可变的完整 voucher Map。

现有 `update` 已能让退回凭证重新进入待审核并清空审核字段，不复制修改逻辑。

## 6. Tuxedo C 实现

新增 `tuxedo-server/src/services/cnaps_review.c`，包含：

```text
一个 static review_voucher(...) 共享函数
CNAPS5702A：传 APPROVED + REVIEW_PASS
CNAPS5702R：传 REJECTED + REVIEW_RETURN
```

复用现有头文件和 helper：`cnaps_db.h`、`cnaps_fields.h`、`cnaps_service.h`、`cnaps_status.h`。

### 6.1 共享事务流程

1. 读取 `BILL_ID`，为空返回 `2001`。
2. `db_find_voucher`：未找到返回 `3001`，数据库错误返回 `4001`。
3. `cnaps_status_can_review(row.status)` 失败返回 `3004`。
4. 读取 `OPERATOR_NO`、`REQ_ID`。
5. 设置 `status/checker_no/last_action/last_operator_no/last_request_id`。
6. `db_begin()`。
7. `db_update_voucher(&row)`。
8. 更新 0 行表示版本冲突：回滚并返回 `3004`。
9. 其他更新错误：回滚并返回 `4001`。
10. 重新 `db_find_voucher`，读取数据库时间和新版本。
11. `db_commit()`；失败时回滚并返回 `4001`。
12. 用 `cnaps_put_string/long` 输出六个摘要字段。
13. `cnaps_return_response(..., "0000", success_message)`。

不要调用 `cnaps_put_voucher`，不要处理审核意见或退回原因。

### 6.2 乐观锁

继续复用 `db_update_voucher` 的条件：

```sql
WHERE BILL_ID=:bill_id AND NVL(VERSION_NO, 1)=:version_no
```

因此并发审核只有一个更新成功，另一个影响 0 行并返回 `3004`。

### 6.3 审核时间

在 `db_helper.c` 的 `db_update_voucher` SQL 中将 `CHECKER_TIME` 改为：

```sql
CASE
  WHEN :last_action IN ('REVIEW_PASS', 'REVIEW_RETURN') THEN SYSTIMESTAMP
  WHEN :checker_time IS NULL THEN NULL
  ELSE TO_TIMESTAMP(:checker_time, 'YYYY-MM-DD HH24:MI:SS')
END
```

其他 `UPDATED_AT`、`LAST_ACTION_TIME` 和 `VERSION_NO` SQL 保持不变。

## 7. 服务注册与 Jolt

### 7.1 注册

- `cnapspocsvr.c` 声明 `CNAPS5702A/R`。
- Makefile 的 `SERVICES` 增加 `CNAPS5702A CNAPS5702R`。
- UBBCONFIG 的 `*SERVICES` 增加两行 A/R。
- `SOURCES` 已使用 `src/services/*.c`，无需单独添加 `cnaps_review.c`。

任何位置都不注册 `CNAPS5702Q`。

### 7.2 Metadata

`cnaps_services.bulk` 只增加两个结构相同的服务块：

| 字段 | 类型 | access |
| --- | --- | --- |
| `REQUEST_ID`、`REQ_ID`、`OPERATOR_NO`、`BRANCH_NO` | string | in |
| `BILL_ID` | string | inout |
| `STATUS`、`CHECKER_NO`、`CHECKER_TIME`、`LAST_ACTION` | string | out |
| `VERSION_NO` | long | out |
| `RESP_CODE`、`RESP_MSG` | string | outerr |

每个块使用 `export=true`、`inbuf=FML32`、`outbuf=FML32`。不复制完整凭证字段，不增加 reason/comment。

现有 `load-jolt-metadata.sh` 已删除 A/R 的旧定义后重新加载，无需修改。

## 8. 最小测试集

### 8.1 Servlet 与映射

- `review-list` 映射 `CNAPS4609Q`，强制待审核状态且不生成 `BILL_ID`。
- 客户端传其他状态时仍被覆盖。
- A/R 空 Body 可用，路径 `billId` 和服务名正确。
- 三个审核路径 GET 返回 405，且不调用 Tuxedo。
- 列表日期错误返回 `2002`。

### 8.2 Mock

- 创建后能由待审核列表查到。
- 通过/退回的状态、审核字段、动作和版本正确。
- 成功响应只有六个摘要字段。
- 不存在返回 `3001`；重复审核返回 `3004`。
- 退回后修改重新进入待审核并清空审核字段。
- 并发通过和退回时一个 `0000`、一个 `3004`，版本只增加一次。

### 8.3 C 与部署制品

- `cnaps_review.c` 存在并包含 A/R、状态校验、事务和四类错误码。
- C 文件不包含 `CNAPS5702Q`、`REVIEW_COMMENT`、`REJECT_REASON`。
- SQL 包含审核动作的 `SYSTIMESTAMP`。
- Makefile、UBB、metadata 只增加 A/R。
- A/R metadata 字段和 access 与第 7.2 节一致。
- 现有 `CNAPS4609Q` metadata 和分页客户端保持不变。

## 9. 冒烟流程

在 `scripts/smoke-test.sh` 追加两条独立流程：

```text
创建 A -> review-list 可见 -> review-pass -> APPROVED -> 再次审核返回 3004
创建 B -> review-return -> REJECTED -> 修改 -> PENDING_REVIEW -> review-list 可见
```

动作请求使用无 Body POST。保留现有健康检查和基础 CRUD。

## 10. 执行与验证

实现顺序：WebFE 路由 → Mock → C/SQL → 注册与 metadata → 测试 → 冒烟与运维文档。

Java 验证：

```bash
mvn -f web-fe/pom.xml -Dtest=BaseJsonServletTest,TuxedoRequestMapperTest,MockTuxedoClientV03ContractTest,TuxedoCSourceContractTest,DeploymentArtifactTest test
mvn -f web-fe/pom.xml clean package
```

Linux 部署验证：

```bash
./scripts/rebuild-deploy.sh
./scripts/cnapsctl.sh status
BASE_URL=http://127.0.0.1:8080/ruisui-bank-sim ./scripts/smoke-test.sh
```

完成条件：三条 API、Mock、真实 Tuxedo、Maven、C 编译、Jolt/UBB、健康检查和冒烟全部通过，且未修改前端资源或扩大 POC 范围。
