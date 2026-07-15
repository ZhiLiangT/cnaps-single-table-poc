# CNAPS 凭证审核功能设计文档（POC 最小实现）

> 文档版本：v1.2<br>
> 编写日期：2026-07-15<br>
> 设计状态：待评审<br>
> 对应需求：`docs/cnaps-review-requirements.md` v1.2<br>
> API 基线：`docs/cnaps-frontend-api.md` v0.5

## 1. 文档目标

本文档用于指导 OpenCode 或开发人员直接完成后端 POC 编码、测试和部署。设计重点是用最少改动验证以下闭环：

```text
待审核列表 -> 审核通过
待审核列表 -> 审核退回 -> 修改 -> 再次待审核
```

本设计保留三个 HTTP API，但不为三个 API 分别开发三套底层服务。待审核列表复用现有查询服务，只新增两个状态变更服务。

## 2. POC 技术决策

### 2.1 服务复用

| HTTP API | WebFE 映射 | Tuxedo 实现 | 说明 |
| --- | --- | --- | --- |
| `POST /api/cnaps/vouchers/review-list` | `CNAPS4609Q` | 复用现有 `cnaps_query.c` | WebFE 强制 `STATUS=10_PENDING_REVIEW` |
| `POST /api/cnaps/vouchers/{billId}/review-pass` | `CNAPS5702A` | 新增 `cnaps_review.c` | 状态改为审核通过 |
| `POST /api/cnaps/vouchers/{billId}/review-return` | `CNAPS5702R` | 新增 `cnaps_review.c` | 状态改为审核退回 |

### 2.2 明确不做的改动

以下内容不属于本次实现：

- 不新增 `CNAPS5702Q`。
- 不修改 `tuxedo-server/src/services/cnaps_query.c`。
- 不修改 `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/JoltTuxedoClient.java`。
- 不修改 `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoResponseMapper.java`。
- 不修改数据库表结构、FML 字段表和状态常量。
- 不新增审核意见、退回原因、删除原因等请求字段。
- 不修改 JSP、JavaScript、CSS 或其他页面资源。
- 不引入框架、依赖、权限模型、消息通知或工作流引擎。
- 不从历史提交整体恢复旧审核实现。

现有 `CNAPS4609Q` 已具备状态筛选和分页能力，`JoltTuxedoClient` 已将它配置为分页服务；现有响应映射也已经支持本期所需的六个动作响应字段。因此这些代码无需扩展。

## 3. 总体调用链

```text
HTTP 请求
  -> CnapsVoucherServlet
     - 解析 JSON
     - 校验列表日期
     - review-list 强制写入待审核状态
     - 动作路径提取 billId
  -> TuxedoRequestMapper
     - HTTP 路由映射服务名
     - 注入可信 requestId/operatorNo/branchNo
  -> MockTuxedoClient 或 JoltTuxedoClient
  -> CNAPS4609Q / CNAPS5702A / CNAPS5702R
  -> Oracle T_CNAPS_BILL_POC
  -> TuxedoResponseMapper
  -> HTTP JSON 响应
```

### 3.1 待审核列表链路

```text
POST /review-list + 查询条件
  -> Servlet 覆盖 status=10_PENDING_REVIEW
  -> RequestMapper 映射 CNAPS4609Q
  -> 现有分页查询
  -> 返回 pageNo/pageSize/total/records
```

即使调用方在 Body 中传入其他 `status`，Servlet 也必须在解析完成后覆盖，不能信任客户端状态。

### 3.2 审核动作链路

```text
POST /{billId}/review-pass 或 review-return
  -> 路径提取 BILL_ID
  -> 读取凭证和当前 VERSION_NO
  -> 校验 STATUS=10_PENDING_REVIEW
  -> 按 BILL_ID + VERSION_NO 更新
  -> 重新读取凭证
  -> 返回六个摘要字段
```

## 4. 数据和状态设计

### 4.1 复用数据库对象

继续使用现有表：

```text
T_CNAPS_BILL_POC
```

本期所需的字段已经存在：

| 字段 | 用途 |
| --- | --- |
| `BILL_ID` | 凭证主键 |
| `STATUS` | 当前状态 |
| `CHECKER_NO` | 最近审核人 |
| `CHECKER_TIME` | 最近审核时间 |
| `LAST_ACTION` | 最近动作 |
| `LAST_OPERATOR_NO` | 最近操作人 |
| `LAST_REQUEST_ID` | 最近请求号 |
| `LAST_ACTION_TIME` | 最近动作时间 |
| `UPDATED_AT` | 最近更新时间 |
| `VERSION_NO` | 乐观锁版本 |

不执行 DDL，不增加列、索引或审核流水表。

### 4.2 状态变化

| 动作 | 前置状态 | 目标状态 | `LAST_ACTION` |
| --- | --- | --- | --- |
| 审核通过 | `10_PENDING_REVIEW` | `20_REVIEW_APPROVED` | `REVIEW_PASS` |
| 审核退回 | `10_PENDING_REVIEW` | `30_REVIEW_REJECTED` | `REVIEW_RETURN` |

状态判断复用：

```c
cnaps_status_can_review(row.status)
```

不修改 `cnaps_status.h` 和 `validation_helper.c`。

### 4.3 审计字段

审核成功时必须写入：

```text
CHECKER_NO        = OPERATOR_NO
CHECKER_TIME      = Oracle SYSTIMESTAMP
LAST_ACTION       = REVIEW_PASS / REVIEW_RETURN
LAST_OPERATOR_NO  = OPERATOR_NO
LAST_REQUEST_ID   = REQ_ID
LAST_ACTION_TIME  = Oracle SYSTIMESTAMP
UPDATED_AT        = Oracle SYSTIMESTAMP
VERSION_NO        = 原值 + 1
```

审核动作不写入 `REVIEW_COMMENT` 和 `REJECT_REASON`。已有字段保留在表中，但本 POC 不接收、不展示，也不依赖这些字段。

## 5. HTTP 契约

### 5.1 待审核列表

请求：

```http
POST /api/cnaps/vouchers/review-list
Content-Type: application/json

{
  "startWorkDate": "2026-07-01",
  "endWorkDate": "2026-07-15",
  "serialNo": "0002000",
  "pageNo": 1,
  "pageSize": 10
}
```

允许空对象 `{}`。字段和校验规则完全复用通用查询，额外规则是服务端固定状态为 `10_PENDING_REVIEW`。

响应复用现有分页结构：

```json
{
  "respCode": "0000",
  "respMsg": "查询成功",
  "data": {
    "pageNo": 1,
    "pageSize": 10,
    "total": 1,
    "records": []
  }
}
```

### 5.2 审核通过

```http
POST /api/cnaps/vouchers/{billId}/review-pass
```

请求 Body 为空，不接收目标状态和审核意见。

### 5.3 审核退回

```http
POST /api/cnaps/vouchers/{billId}/review-return
```

请求 Body 为空，不接收退回原因和审核意见。

### 5.4 动作成功响应

两个动作只返回：

| FML32 字段 | JSON 字段 | 类型 |
| --- | --- | --- |
| `BILL_ID` | `billId` | string |
| `STATUS` | `status` | string |
| `CHECKER_NO` | `checkerNo` | string |
| `CHECKER_TIME` | `checkerTime` | string |
| `LAST_ACTION` | `lastAction` | string |
| `VERSION_NO` | `versionNo` | number |

完整凭证继续通过 `GET /api/cnaps/vouchers/{billId}` 查询。

### 5.5 错误响应

| 场景 | `respCode` | HTTP 状态 |
| --- | --- | ---: |
| 缺少 `billId` | `2001` | 400 |
| 日期格式或范围错误 | `2002` | 400 |
| 凭证不存在 | `3001` | 404 |
| 当前状态不允许审核或发生并发冲突 | `3004` | 409 |
| 数据库错误 | `4001` | 500 |

`BaseJsonServlet.httpStatus` 已将 `3004` 映射为 HTTP 409，无需修改。

## 6. 代码改动总览

### 6.1 生产代码和部署配置

| 序号 | 文件 | 改动 |
| ---: | --- | --- |
| 1 | `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java` | 开放三个 POST 路由，列表强制待审核状态 |
| 2 | `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java` | 增加三个 HTTP 路由映射 |
| 3 | `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java` | 模拟通过、退回和并发状态更新 |
| 4 | `tuxedo-server/src/services/cnaps_review.c` | 新增两个审核服务，共用一个内部函数 |
| 5 | `tuxedo-server/src/common/db_helper.c` | 审核动作使用 Oracle 时间写 `CHECKER_TIME` |
| 6 | `tuxedo-server/src/cnapspocsvr.c` | 声明两个审核服务 |
| 7 | `tuxedo-server/Makefile` | 注册两个审核服务 |
| 8 | `tuxedo/UBBCONFIG` | 发布两个审核服务 |
| 9 | `tuxedo/jolt/cnaps_services.bulk` | 增加两个动作服务 metadata |

### 6.2 测试和运维文档

| 文件 | 改动 |
| --- | --- |
| `web-fe/src/test/java/com/ruisui/cnaps/web/servlet/BaseJsonServletTest.java` | Servlet 路由、强制状态、空 Body 和 405 测试 |
| `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapperTest.java` | 三个路由映射测试 |
| `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClientV03ContractTest.java` | 审核状态、审计、失败和并发测试 |
| `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/TuxedoCSourceContractTest.java` | C 服务与 SQL 契约测试 |
| `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java` | Makefile、UBB 和 metadata 注册测试 |
| `scripts/smoke-test.sh` | 真实部署通过、退回和再次修改冒烟 |
| `docs/cnaps-operations.md` | 补充审核 API 验证命令和预期结果 |

测试可在现有测试类中追加，不需要为每个场景新建测试类。

## 7. WebFE Servlet 设计

文件：

```text
web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java
```

### 7.1 删除统一禁用逻辑

当前 `isRemovedReviewPath` 会让三个审核接口返回 405。实现时删除该判断，不能只修改 RequestMapper，否则请求到不了 Tuxedo。

### 7.2 GET 仍然返回 405

`isRejectedGetPath` 必须覆盖：

```text
/api/cnaps/vouchers
/api/cnaps/vouchers/query
/api/cnaps/vouchers/review-list
以 /review-pass 结尾的路径
以 /review-return 结尾的路径
```

这样可防止 `GET /{billId}/review-pass` 被详情路由误识别为 `CNAPS5702I`。

建议逻辑：

```java
private static boolean isRejectedGetPath(String apiPath) {
    return "/api/cnaps/vouchers".equals(apiPath)
        || "/api/cnaps/vouchers/query".equals(apiPath)
        || "/api/cnaps/vouchers/review-list".equals(apiPath)
        || apiPath.endsWith("/review-pass")
        || apiPath.endsWith("/review-return");
}
```

### 7.3 列表路径识别

```java
private static boolean isListPostPath(String apiPath) {
    return "/api/cnaps/vouchers/query".equals(apiPath)
        || "/api/cnaps/vouchers/review-list".equals(apiPath);
}
```

两个列表共用 `validateListFilters`。列表路径不能调用 `includeBillPath`，避免把 `review-list` 解析为凭证号。

### 7.4 强制状态

在 JSON 解析和日期校验后、`callTuxedo` 前执行：

```java
if ("/api/cnaps/vouchers/review-list".equals(apiPath)) {
    fields.put("status", "10_PENDING_REVIEW");
}
```

写入时机必须晚于请求 Body 解析，确保覆盖调用方伪造的 `status`。

### 7.5 动作路径和空 Body

两个动作属于非列表 POST，继续调用：

```java
RequestSupport.includeBillPath(fields, request.getPathInfo());
```

当前 JSON 工具支持空 Body，不需要新增 DTO。最终 `billId` 来自 URL，不从 Body 获取。

## 8. HTTP 到 Tuxedo 映射设计

文件：

```text
web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java
```

### 8.1 路由顺序

在通配路径 `cleanPath.startsWith("/api/cnaps/vouchers/")` 之前增加精确列表映射：

```java
if ("POST".equals(verb) && "/api/cnaps/vouchers/review-list".equals(cleanPath)) {
    return "CNAPS4609Q";
}
```

在凭证通配路径内部增加：

```java
if ("POST".equals(verb) && cleanPath.endsWith("/review-pass")) {
    return "CNAPS5702A";
}
if ("POST".equals(verb) && cleanPath.endsWith("/review-return")) {
    return "CNAPS5702R";
}
```

动作判断应在兜底详情判断之前。最终映射必须是：

```text
POST review-list   -> CNAPS4609Q
POST review-pass   -> CNAPS5702A
POST review-return -> CNAPS5702R
```

### 8.2 字段映射

本期不向 `BODY_FIELD_NAMES` 增加审核原因或意见。现有字段已覆盖：

```text
billId -> BILL_ID
status -> STATUS
pageNo -> PAGE_NO
pageSize -> PAGE_SIZE
```

未知的驼峰查询字段仍由 `camelToFieldName` 转换。

### 8.3 可信上下文

`from` 方法保持最后写入：

```text
REQUEST_ID
REQ_ID
OPERATOR_NO
BRANCH_NO
```

这样客户端即使构造同名 JSON 字段，也会被服务端配置覆盖。不修改该机制。

## 9. Mock 实现设计

文件：

```text
web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/MockTuxedoClient.java
```

### 9.1 服务分发

在现有 `switch` 增加：

```java
case "CNAPS5702A" -> review(
    request,
    "20_REVIEW_APPROVED",
    "REVIEW_PASS",
    "审核通过成功"
);
case "CNAPS5702R" -> review(
    request,
    "30_REVIEW_REJECTED",
    "REVIEW_RETURN",
    "审核退回成功"
);
```

不要增加 `CNAPS5702Q` 分支。待审核列表仍经过现有 `CNAPS4609Q`，Servlet 传入的 `STATUS` 会直接被 `voucherPage` 使用。

### 9.2 公共审核方法

新增一个公共内部方法，参数为请求、目标状态、动作名称和成功消息。伪代码：

```java
private TuxedoResponse review(
    TuxedoRequest request,
    String targetStatus,
    String lastAction,
    String successMessage
) {
    String billId = text(request, "BILL_ID");
    if (billId == null || billId.isBlank()) {
        return TuxedoResponse.fail("2001", "billId is required");
    }

    synchronized (vouchers) {
        Map<String, Object> voucher = vouchers.get(billId);
        if (voucher == null) {
            return TuxedoResponse.fail("3001", "单据不存在");
        }
        if (!"10_PENDING_REVIEW".equals(voucher.get("STATUS"))) {
            return TuxedoResponse.fail("3004", "当前状态不允许审核");
        }

        voucher.put("STATUS", targetStatus);
        voucher.put("CHECKER_NO", text(request, "OPERATOR_NO"));
        voucher.put("CHECKER_TIME", now());
        voucher.put("LAST_ACTION", lastAction);
        voucher.put("VERSION_NO", number(voucher, "VERSION_NO") + 1);
        touch(voucher, request);
        return ok(successMessage, actionResult(voucher));
    }
}
```

`touch` 继续负责 `LAST_OPERATOR_NO`、`LAST_REQUEST_ID`、`LAST_ACTION_TIME` 和 `UPDATED_AT`。同步范围必须包含状态检查和字段更新，防止通过与退回同时成功。

### 9.3 动作摘要

新增 `actionResult`，使用 `LinkedHashMap` 返回以下字段：

```text
BILL_ID
STATUS
CHECKER_NO
CHECKER_TIME
LAST_ACTION
VERSION_NO
```

不要直接返回可变的完整 voucher Map，确保 Mock 与真实 C 服务响应结构一致。

### 9.4 退回后修改

现有 `update` 已执行：

```text
STATUS -> 10_PENDING_REVIEW
移除 CHECKER_NO
移除 CHECKER_TIME
LAST_ACTION -> UPDATE
VERSION_NO + 1
```

保留现有逻辑即可，不为审核功能复制修改服务。

## 10. Tuxedo C 服务设计

新增文件：

```text
tuxedo-server/src/services/cnaps_review.c
```

### 10.1 头文件

```c
#include <stdio.h>
#include <string.h>
#include "cnaps_db.h"
#include "cnaps_fields.h"
#include "cnaps_service.h"
#include "cnaps_status.h"
```

### 10.2 公共函数

文件内只实现一个公共状态变更函数，例如：

```c
static void review_voucher(
    TPSVCINFO *rqst,
    const char *service_name,
    const char *target_status,
    const char *last_action,
    const char *success_message
)
```

`CNAPS5702A` 和 `CNAPS5702R` 只负责传入不同参数，不能复制两份数据库逻辑。

### 10.3 处理顺序

公共函数严格按以下顺序处理：

1. 将 `rqst->data` 转为 `FBFR32 *`。
2. 记录 `cnaps_log_service_start(service_name)`。
3. 读取 `BILL_ID`；为空返回 `2001`。
4. 调用 `db_find_voucher`。
5. 未找到返回 `3001`，其他查询错误返回 `4001`。
6. 调用 `cnaps_status_can_review(row.status)`；不允许时返回 `3004`。
7. 从 FML32 读取 `OPERATOR_NO` 和 `REQ_ID`。
8. 设置目标状态、审核人、最后动作、最后操作人和请求号。
9. 调用 `db_begin()`。
10. 调用 `db_update_voucher(&row)`。
11. 更新 0 行时回滚并返回 `3004`。
12. 其他更新错误时回滚并返回 `4001`。
13. 调用 `db_find_voucher` 重新读取数据库生成的时间和新版本。
14. 调用 `db_commit()`；失败则回滚并返回 `4001`。
15. 写入六个动作摘要字段。
16. 调用 `cnaps_return_response(rqst, 1, "0000", success_message)`。

### 10.4 行对象赋值

```c
snprintf(row.status, sizeof(row.status), "%s", target_status);
snprintf(row.checker_no, sizeof(row.checker_no), "%s", operator_no);
snprintf(row.last_action, sizeof(row.last_action), "%s", last_action);
snprintf(row.last_operator_no, sizeof(row.last_operator_no), "%s", operator_no);
snprintf(row.last_request_id, sizeof(row.last_request_id), "%s", request_id);
```

不要由 C 进程计算 `checker_time`；数据库 SQL 根据审核动作写入 `SYSTIMESTAMP`。

### 10.5 乐观锁结果

`db_update_voucher` 使用读取到的 `row.version_no` 作为更新条件：

```sql
WHERE BILL_ID=:bill_id
  AND NVL(VERSION_NO, 1)=:version_no
```

两个请求同时读取同一版本时，只能有一个更新成功。第二个更新影响 0 行，必须映射为 `3004`，不能映射为 `3001`。

### 10.6 响应字段写入

不要调用 `cnaps_put_voucher` 返回完整凭证，改用现有 helper：

```c
cnaps_put_string(fbfr, CNAPS_F_BILL_ID, row.bill_id);
cnaps_put_string(fbfr, CNAPS_F_STATUS, row.status);
cnaps_put_string(fbfr, CNAPS_F_CHECKER_NO, row.checker_no);
cnaps_put_string(fbfr, CNAPS_F_CHECKER_TIME, row.checker_time);
cnaps_put_string(fbfr, CNAPS_F_LAST_ACTION, row.last_action);
cnaps_put_long(fbfr, CNAPS_F_VERSION_NO, row.version_no);
```

### 10.7 两个导出函数

```c
void CNAPS5702A(TPSVCINFO *rqst)
{
    review_voucher(
        rqst,
        "CNAPS5702A",
        CNAPS_STATUS_APPROVED,
        "REVIEW_PASS",
        "审核通过成功"
    );
}

void CNAPS5702R(TPSVCINFO *rqst)
{
    review_voucher(
        rqst,
        "CNAPS5702R",
        CNAPS_STATUS_REJECTED,
        "REVIEW_RETURN",
        "审核退回成功"
    );
}
```

状态宏名称以现有 `cnaps_status.h` 为准；实现前先确认实际宏名，不得另建重复常量。

## 11. 数据库更新时间设计

文件：

```text
tuxedo-server/src/common/db_helper.c
```

在 `db_update_voucher` 的 SQL 中，将现有 `CHECKER_TIME` 表达式改为：

```sql
CHECKER_TIME=CASE
  WHEN :last_action IN ('REVIEW_PASS', 'REVIEW_RETURN') THEN SYSTIMESTAMP
  WHEN :checker_time IS NULL THEN NULL
  ELSE TO_TIMESTAMP(:checker_time, 'YYYY-MM-DD HH24:MI:SS')
END
```

作用：

- 审核动作由 Oracle 生成审核时间。
- 退回凭证再次修改时，现有更新逻辑仍可把审核时间清空。
- 其他已有操作保持原行为。

`UPDATED_AT`、`LAST_ACTION_TIME` 和 `VERSION_NO` 的现有 SQL 已满足要求，不修改。

## 12. 服务注册设计

### 12.1 C 服务声明

文件 `tuxedo-server/src/cnapspocsvr.c` 增加：

```c
void CNAPS5702A(TPSVCINFO *rqst);
void CNAPS5702R(TPSVCINFO *rqst);
```

不要声明 `CNAPS5702Q`。

### 12.2 Makefile

文件 `tuxedo-server/Makefile` 的 `SERVICES` 末尾增加：

```text
CNAPS5702A CNAPS5702R
```

`SOURCES` 已使用 `$(wildcard src/services/*.c)`，新增 `cnaps_review.c` 会自动参与编译，不需要额外添加源文件行。

### 12.3 UBBCONFIG

文件 `tuxedo/UBBCONFIG` 的 `*SERVICES` 增加：

```text
CNAPS5702A
CNAPS5702R
```

不要增加 `CNAPS5702Q`。

## 13. Jolt metadata 设计

文件：

```text
tuxedo/jolt/cnaps_services.bulk
```

只增加 `CNAPS5702A` 和 `CNAPS5702R` 两个 service 块。两个块字段完全相同，只有服务名不同。

| 参数 | 类型 | access | 说明 |
| --- | --- | --- | --- |
| `REQUEST_ID` | string | in | WebFE 请求号 |
| `REQ_ID` | string | in | C 服务审计请求号 |
| `OPERATOR_NO` | string | in | 审核员 |
| `BRANCH_NO` | string | in | WebFE 可信机构上下文 |
| `BILL_ID` | string | inout | 路径凭证号及响应凭证号 |
| `STATUS` | string | out | 目标状态 |
| `CHECKER_NO` | string | out | 审核员 |
| `CHECKER_TIME` | string | out | 审核时间 |
| `LAST_ACTION` | string | out | 审核动作 |
| `VERSION_NO` | long | out | 新版本号 |
| `RESP_CODE` | string | outerr | 业务响应码 |
| `RESP_MSG` | string | outerr | 业务响应消息 |

每个 service 块头部：

```text
service=CNAPS5702A
export=true
inbuf=FML32
outbuf=FML32
```

另一个块将服务名改为 `CNAPS5702R`。不要把完整凭证字段复制到动作服务 metadata，不要增加 reason/comment 字段。

现有 `scripts/load-jolt-metadata.sh` 已在加载前删除 `CNAPS5702Q,CNAPS5702A,CNAPS5702R` 的旧定义。删除不存在的 `CNAPS5702Q` 不影响加载，因此脚本无需修改。

## 14. 测试设计

### 14.1 Servlet 测试

在 `BaseJsonServletTest` 调整原“审核接口全部 405”的断言，并增加：

1. `POST /review-list` 调用 Tuxedo。
2. `{}` 请求会生成 `STATUS=10_PENDING_REVIEW`。
3. Body 传 `status=20_REVIEW_APPROVED` 时仍被覆盖为待审核。
4. `review-list` 不把路径文本写入 `BILL_ID`。
5. `POST /{billId}/review-pass` 空 Body 可用，服务名为 `CNAPS5702A`。
6. `POST /{billId}/review-return` 空 Body 可用，服务名为 `CNAPS5702R`。
7. 两个动作均正确写入路径 `BILL_ID`。
8. 三个审核路径使用 GET 时返回 405，且不调用 Tuxedo。
9. `review-list` 的日期格式和倒序范围仍返回 `2002`。

### 14.2 RequestMapper 测试

在 `TuxedoRequestMapperTest` 将当前“不支持审核路径”的断言改为：

```java
assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers/review-list"))
    .isEqualTo("CNAPS4609Q");
assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers/BILL-1/review-pass"))
    .isEqualTo("CNAPS5702A");
assertThat(mapper.serviceName("POST", "/api/cnaps/vouchers/BILL-1/review-return"))
    .isEqualTo("CNAPS5702R");
```

保留不支持的 HTTP 方法和未知路径测试。

### 14.3 Mock 契约测试

在现有 Mock 契约测试中覆盖：

| 场景 | 预期 |
| --- | --- |
| 创建后用 `CNAPS4609Q + STATUS=10_PENDING_REVIEW` 查询 | 列表包含新凭证 |
| 审核通过 | 状态为 `20_REVIEW_APPROVED`，动作和审核字段正确，版本加 1 |
| 审核退回 | 状态为 `30_REVIEW_REJECTED`，动作和审核字段正确，版本加 1 |
| 动作成功响应 | 只包含六个摘要字段 |
| 不存在的凭证 | `3001` |
| 已通过凭证再次审核 | `3004` |
| 已退回凭证再次审核 | `3004` |
| 退回后修改 | 状态回到待审核，审核人和时间清空 |
| 同一凭证并发通过和退回 | 一个 `0000`、一个 `3004`，版本只增加一次 |

并发测试可使用两个线程和同一起跑门闩，不需要压力测试框架。

### 14.4 C 源码契约测试

更新 `TuxedoCSourceContractTest`：

- 原先断言 `cnaps_review.c` 不存在，改为断言存在。
- 文件包含 `CNAPS5702A`、`CNAPS5702R`、`cnaps_status_can_review`。
- 文件包含错误码 `2001`、`3001`、`3004`、`4001`。
- 文件调用 `db_begin`、`db_update_voucher`、`db_commit` 和 `db_rollback`。
- 文件只输出六个摘要字段。
- 文件不包含 `CNAPS5702Q`、`REVIEW_COMMENT` 或 `REJECT_REASON`。
- `db_helper.c` 包含审核动作对应的 `SYSTIMESTAMP` 表达式。

### 14.5 部署制品测试

更新 `DeploymentArtifactTest`：

- `cnapspocsvr.c` 声明 A/R。
- Makefile 的服务列表包含 A/R。
- UBBCONFIG 发布 A/R。
- 三处都不要求 `CNAPS5702Q`。
- metadata 只新增 A/R 块。
- A/R metadata 包含六个输出字段和 `RESP_CODE/RESP_MSG`。
- A/R metadata 不包含 reason/comment 字段。
- 现有 `CNAPS4609Q` metadata 保持不变。

不需要修改 `JoltTuxedoClientTest` 的分页服务集合，因为审核列表复用已存在的 `CNAPS4609Q`。如现有测试桩对未知动作服务报错，只补充 A/R 的最小通用动作响应，不增加新的分页读取逻辑。

## 15. 冒烟测试设计

文件：

```text
scripts/smoke-test.sh
```

保留现有健康检查和基础 CRUD，追加两个相互独立的凭证流程。

### 15.1 审核通过流程

1. 创建凭证 A。
2. 调用 `review-list`，断言包含 A 且状态为待审核。
3. 空 Body 调用 A 的 `review-pass`。
4. 断言响应为 `0000`、状态为 `20_REVIEW_APPROVED`、版本为 2。
5. 再次调用 `review-pass`，断言 `3004`。

### 15.2 审核退回流程

1. 创建凭证 B。
2. 空 Body 调用 B 的 `review-return`。
3. 断言状态为 `30_REVIEW_REJECTED`。
4. 调用现有修改接口修改 B。
5. 断言状态回到 `10_PENDING_REVIEW`，版本继续增加。
6. 再次查询 `review-list`，断言包含 B。

动作请求不要传 `{}`、`reason` 或 `comment`；使用无 Body 的 POST，验证真实 API 契约。

## 16. 实现顺序

OpenCode 按以下顺序执行，可减少重复修改：

1. 阅读 API、需求、设计文档和当前相关源码。
2. 修改 Servlet 与 RequestMapper，先打通三个 HTTP 路由。
3. 修改 Mock 并完成 Java 主流程测试。
4. 新增 `cnaps_review.c` 和数据库时间 SQL。
5. 注册 C 服务、UBB 和 Jolt metadata。
6. 补充源码契约和部署制品测试。
7. 更新冒烟脚本与运维文档。
8. 运行完整 Maven 构建。
9. 在 Linux/Tuxedo 环境编译 C、部署并运行冒烟。

不要先批量恢复历史文件；每完成一层就运行对应测试。

## 17. 验证命令

### 17.1 本地 Java 测试

```bash
mvn -f web-fe/pom.xml -Dtest=BaseJsonServletTest,TuxedoRequestMapperTest,MockTuxedoClientV03ContractTest,TuxedoCSourceContractTest,DeploymentArtifactTest test
mvn -f web-fe/pom.xml clean package
```

验收：

- 测试全部通过。
- 生成 WAR。
- 无编译错误和新增告警。

### 17.2 Linux C 编译和部署

在配置好 Tuxedo、Oracle Client 和环境变量的 Linux 虚拟机执行项目现有脚本：

```bash
./scripts/rebuild-deploy.sh
./scripts/cnapsctl.sh status
BASE_URL=http://127.0.0.1:8080/ruisui-bank-sim ./scripts/smoke-test.sh
```

若项目脚本实际入口有变化，以 `docs/cnaps-operations.md` 的当前命令为准，不另写一套部署脚本。

### 17.3 服务注册检查

部署后确认：

```text
CNAPS5702A 可调用
CNAPS5702R 可调用
CNAPS4609Q 继续可调用
Jolt metadata 加载无错误
SYSHEALTH 返回 WebFE/Tuxedo/Oracle 全部 UP
```

## 18. OpenCode 完成检查表

- [ ] 三个 POST API 均可访问。
- [ ] 三个审核路径使用 GET 均返回 405。
- [ ] `review-list` 映射到现有 `CNAPS4609Q`。
- [ ] 调用方传入的状态会被覆盖为待审核。
- [ ] 没有新增 `CNAPS5702Q`。
- [ ] 只新增 `CNAPS5702A` 和 `CNAPS5702R` 两个 Tuxedo 服务。
- [ ] 动作 API 无请求 Body。
- [ ] 动作响应只有六个必要字段。
- [ ] 审核人来自 WebFE 可信配置。
- [ ] 审核时间来自 Oracle `SYSTIMESTAMP`。
- [ ] 非待审核状态返回 `3004`。
- [ ] 并发审核只有一个成功。
- [ ] 退回后修改重新进入待审核。
- [ ] Mock 与真实服务主流程一致。
- [ ] Maven 测试和 WAR 打包成功。
- [ ] Tuxedo C 编译和注册成功。
- [ ] Jolt metadata 加载成功。
- [ ] 冒烟测试通过。
- [ ] 未修改任何 JSP、JavaScript 或 CSS。

## 19. 工作量评估

按本设计实现，预计：

| 类别 | 数量或代码量 |
| --- | ---: |
| 生产代码和部署配置文件 | 约 9 个 |
| 测试文件 | 约 5 个 |
| 脚本和运维文档 | 约 2 个 |
| 生产代码及配置新增/修改 | 约 300～500 行 |
| 测试、脚本和文档新增/修改 | 约 200～300 行 |
| 总改动 | 约 500～800 行 |
| 开发、联调和部署 | 约 2～4 人日 |

行数包含 Tuxedo/Jolt 注册、测试和冒烟脚本，不代表业务逻辑复杂。核心审核状态变更逻辑预计只有约 100～180 行；其余改动用于保证 HTTP、Mock、Tuxedo、Oracle 和部署链路能够完整验证。

## 20. POC 升级边界

如果后续从 POC 进入生产，应另行评审以下能力，不在本次代码中预埋：

- 审核权限和录入审核分离。
- 审核意见及退回原因。
- 审核流水表和完整历史追踪。
- 幂等请求和重放保护。
- 批量审核、多级审核、撤销审核。
- 监控指标、告警、容量和性能压测。

POC 阶段不为这些未来能力增加抽象层或占位代码。
