# CNAPS 凭证审核设计（POC 生产代码执行版）

> 版本：v1.5 ｜ 日期：2026-07-15 ｜ 需求：`docs/cnaps-review-requirements.md` v1.4

## 1. OpenCode 执行目标

按本文一次完成真实 WebFE → Jolt → Tuxedo C → Oracle 审核链路。只修改本文列出的生产代码和运行接口配置，不增加前端页面或其他功能。

服务映射：

```text
POST /api/cnaps/vouchers/review-list       -> CNAPS4609Q
POST /api/cnaps/vouchers/{id}/review-pass  -> CNAPS5702A
POST /api/cnaps/vouchers/{id}/review-return-> CNAPS5702R
```

关键约束：

- 待审核列表复用现有 `CNAPS4609Q`，不新增 `CNAPS5702Q`。
- 只新增 `CNAPS5702A` 和 `CNAPS5702R`。
- 不修改 `cnaps_query.c`、`JoltTuxedoClient.java`、`TuxedoResponseMapper.java`。
- 不修改数据库结构、FML 字段表、状态常量和前端资源。
- 不增加审核意见、退回原因、DTO、框架或依赖。

## 2. 修改文件清单

| 文件 | 修改内容 |
| --- | --- |
| `web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java` | 开放三个 POST，列表强制待审核状态 |
| `web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java` | 增加三个路由映射 |
| `tuxedo-server/src/services/cnaps_review.c` | 新增 A/R 服务及共享审核函数 |
| `tuxedo-server/src/common/db_helper.c` | 审核动作写入 Oracle 时间 |
| `tuxedo-server/src/cnapspocsvr.c` | 声明 A/R 服务 |
| `tuxedo-server/Makefile` | 将 A/R 加入 buildserver 服务列表 |
| `tuxedo/UBBCONFIG` | 将 A/R 加入 `*SERVICES` |
| `tuxedo/jolt/cnaps_services.bulk` | 增加 A/R 的 Jolt 接口定义 |

除以上文件外不要修改其他文件。

## 3. `CnapsVoucherServlet` 实现

文件：

```text
web-fe/src/main/java/com/ruisui/cnaps/web/servlet/CnapsVoucherServlet.java
```

参考当前类中的 `doPost`、`isListPostPath`、`validateListFilters`，以及 `RequestSupport.includeBillPath`。

### 3.1 修改 `doGet`

删除对 `isRemovedReviewPath` 的调用，并将所有不允许 GET 的审核路径合并到 `isRejectedGetPath`：

```java
@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String apiPath = RequestSupport.apiPath(request);
    if (isRejectedGetPath(apiPath)) {
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        return;
    }
    Map<String, Object> fields = new LinkedHashMap<>(RequestSupport.queryParams(request));
    RequestSupport.includeBillPath(fields, request.getPathInfo());
    callTuxedo(request, response, fields);
}
```

### 3.2 替换 `doPost`

```java
@Override
protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    Map<String, Object> fields = JsonSupport.readBodyMap(request);
    String apiPath = RequestSupport.apiPath(request);
    boolean listPost = isListPostPath(apiPath);

    if (listPost && !validateListFilters(fields, response)) {
        return;
    }
    if (isReviewListPath(apiPath)) {
        fields.put("status", "10_PENDING_REVIEW");
    }
    if (!listPost) {
        RequestSupport.includeBillPath(fields, request.getPathInfo());
    }
    callTuxedo(request, response, fields);
}
```

`JsonSupport.readBodyMap` 已在请求长度为 0 时返回空 `LinkedHashMap`，因此 A/R 不需要 DTO 或空 Body 特殊处理。

### 3.3 替换路径辅助方法

```java
private static boolean isListPostPath(String apiPath) {
    return "/api/cnaps/vouchers/query".equals(apiPath)
        || isReviewListPath(apiPath);
}

private static boolean isReviewListPath(String apiPath) {
    return "/api/cnaps/vouchers/review-list".equals(apiPath);
}

private static boolean isRejectedGetPath(String apiPath) {
    return "/api/cnaps/vouchers".equals(apiPath)
        || "/api/cnaps/vouchers/query".equals(apiPath)
        || isReviewListPath(apiPath)
        || apiPath.endsWith("/review-pass")
        || apiPath.endsWith("/review-return");
}
```

删除 `isRemovedReviewPath`。`review-list` 必须先被识别为列表，不能调用 `includeBillPath`，否则 `review-list` 会被当成 `billId`。

## 4. `TuxedoRequestMapper` 实现

文件：

```text
web-fe/src/main/java/com/ruisui/cnaps/web/tuxedo/TuxedoRequestMapper.java
```

参考现有 `/query -> CNAPS4609Q`、`/delete -> CNAPS5701D` 的判断方式。

### 4.1 精确列表路由

紧跟现有 `/query` 判断增加：

```java
if ("POST".equals(verb) && "/api/cnaps/vouchers/review-list".equals(cleanPath)) {
    return "CNAPS4609Q";
}
```

该判断必须位于 `cleanPath.startsWith("/api/cnaps/vouchers/")` 之前。

### 4.2 动作路由

在凭证通配块中，放在 `PUT`、`GET` 和 `/delete` 判断之前：

```java
if (cleanPath.startsWith("/api/cnaps/vouchers/")) {
    if ("POST".equals(verb) && cleanPath.endsWith("/review-pass")) {
        return "CNAPS5702A";
    }
    if ("POST".equals(verb) && cleanPath.endsWith("/review-return")) {
        return "CNAPS5702R";
    }
    // 保留现有 PUT、GET、delete 判断
}
```

不要向 `BODY_FIELD_NAMES` 增加字段。现有 `billId -> BILL_ID`、`status -> STATUS` 和日期/分页的自动转换已经够用。

`from` 方法继续在 Body 映射后写入 `REQUEST_ID`、`REQ_ID`、`OPERATOR_NO`、`BRANCH_NO`，不得改变顺序，以保证可信上下文覆盖客户端输入。

## 5. 待审核列表复用

Servlet 对 `/review-list` 强制执行：

```java
fields.put("status", "10_PENDING_REVIEW");
```

随后 RequestMapper 映射到 `CNAPS4609Q`。现有实现已经完成以下能力：

| 能力 | 现有代码 |
| --- | --- |
| 状态、机构、日期和流水号筛选 | `cnaps_query.c::CNAPS4609Q` |
| Oracle 查询条件 | `db_helper.c::CNAPS_QUERY_PREDICATES` |
| repeated FML32 分页输出 | `cnaps_put_voucher_occurrence` |
| Jolt 分页读取 | `JoltTuxedoClient.PAGE_SERVICES` |
| HTTP 分页字段转换 | `TuxedoResponseMapper` |

因此不得复制查询代码或改动分页服务集合。

## 6. 新增 `cnaps_review.c`

文件：

```text
tuxedo-server/src/services/cnaps_review.c
```

实现风格参考：

- `cnaps_delete.c`：字段读取、状态校验、事务、错误返回。
- `cnaps_update.c`：设置审计字段、乐观锁更新、重新读取。
- `cnaps_query.c::CNAPS5702I`：按 `BILL_ID` 查询和不存在处理。
- `fml_helper.c`：`cnaps_get_string`、`cnaps_put_string`、`cnaps_put_long`。

### 6.1 文件结构

```c
#include <stdio.h>
#include <string.h>
#include "cnaps_db.h"
#include "cnaps_fields.h"
#include "cnaps_service.h"
#include "cnaps_status.h"

static void get_field(FBFR32 *fbfr, const char *field, char *out, size_t out_size)
{
    if (cnaps_get_string(fbfr, field, out, out_size) != 0) {
        out[0] = '\0';
    }
}

static void review_voucher(
    TPSVCINFO *rqst,
    const char *service_name,
    const char *target_status,
    const char *last_action,
    const char *success_message
)
{
    /* 按 6.2 节顺序实现 */
}

void CNAPS5702A(TPSVCINFO *rqst)
{
    review_voucher(
        rqst, "CNAPS5702A", CNAPS_STATUS_APPROVED,
        "REVIEW_PASS", "review pass success"
    );
}

void CNAPS5702R(TPSVCINFO *rqst)
{
    review_voucher(
        rqst, "CNAPS5702R", CNAPS_STATUS_REJECTED,
        "REVIEW_RETURN", "review return success"
    );
}
```

`CNAPS_STATUS_APPROVED`、`CNAPS_STATUS_REJECTED` 和 `cnaps_status_can_review` 已存在于 `cnaps_status.h` / `validation_helper.c`，不要新增重复常量。

### 6.2 `review_voucher` 详细逻辑

声明：

```c
FBFR32 *fbfr = (FBFR32 *)rqst->data;
cnaps_voucher_row row = {0};
char bill_id[33] = {0};
char operator_no[17] = {0};
char request_id[33] = {0};
int rc;
```

按以下顺序编码：

1. `cnaps_log_service_start(service_name)`。
2. 从 `CNAPS_F_BILL_ID` 读取 `bill_id`；空值返回 `2001 / billId is required`。
3. `db_find_voucher(bill_id, &row)`：返回 1 时返回 `3001 / 单据不存在`，非 0 时返回 `4001 / database error`。
4. `cnaps_status_can_review(row.status)` 为假时返回 `3004 / 当前状态不允许审核`。
5. 读取 `CNAPS_F_OPERATOR_NO` 和 `CNAPS_F_REQ_ID`。
6. 使用 `snprintf` 设置：

```c
row.status          = target_status;
row.checker_no      = operator_no;
row.last_action     = last_action;
row.last_operator_no= operator_no;
row.last_request_id = request_id;
```

实际 C 代码按 `cnaps_delete.c` 的方式对每个数组字段调用 `snprintf(row.xxx, sizeof(row.xxx), "%s", value)`，不能直接赋值。

7. `db_begin()` 失败返回 `4001`。
8. `rc = db_update_voucher(&row)`。
9. `rc == 1` 表示 `BILL_ID + VERSION_NO` 未更新到记录；执行 `db_rollback()` 并返回 `3004`。
10. `rc != 0` 时回滚并返回 `4001`。
11. 再次执行 `db_find_voucher(bill_id, &row)`，失败时回滚并返回 `4001`。
12. `db_commit()` 失败时回滚并返回 `4001`。
13. 只写入以下响应字段：

```c
cnaps_put_string(fbfr, CNAPS_F_BILL_ID, row.bill_id);
cnaps_put_string(fbfr, CNAPS_F_STATUS, row.status);
cnaps_put_string(fbfr, CNAPS_F_CHECKER_NO, row.checker_no);
cnaps_put_string(fbfr, CNAPS_F_CHECKER_TIME, row.checker_time);
cnaps_put_string(fbfr, CNAPS_F_LAST_ACTION, row.last_action);
cnaps_put_long(fbfr, CNAPS_F_VERSION_NO, row.version_no);
```

14. `cnaps_return_response(rqst, 1, "0000", success_message)`。

不要调用 `cnaps_put_voucher`，否则动作 API 会返回完整凭证。不要写 `REVIEW_COMMENT` 或 `REJECT_REASON`。

## 7. `db_helper.c` 修改

文件：

```text
tuxedo-server/src/common/db_helper.c
```

参考同一 SQL 中 `DELETE_TIME` 根据 `LAST_ACTION='DELETE'` 写 `SYSTIMESTAMP` 的做法。

在 `db_update_voucher` 的 SQL 中，仅替换 `CHECKER_TIME` 表达式：

```c
"CHECKER_TIME=CASE "
"WHEN :last_action IN ('REVIEW_PASS', 'REVIEW_RETURN') THEN SYSTIMESTAMP "
"WHEN :checker_time IS NULL THEN NULL "
"ELSE TO_TIMESTAMP(:checker_time, 'YYYY-MM-DD HH24:MI:SS') END, "
```

不要在 C 服务中生成审核时间。审核成功后第二次 `db_find_voucher` 会读取 Oracle 生成的 `CHECKER_TIME`。

保留现有乐观锁：

```sql
WHERE BILL_ID=:bill_id AND NVL(VERSION_NO, 1)=:version_no
```

`execute_dml(..., require_affected_row=1)` 已将影响 0 行转换为返回值 1；审核服务必须将其映射为 `3004`，而不是 `3001`。

## 8. Java 响应链路无需修改

以下现有实现已覆盖动作响应：

- `JoltTuxedoClient.DATA_FIELDS` 已包含 `BILL_ID`、`STATUS`、`CHECKER_NO`、`CHECKER_TIME`、`LAST_ACTION`、`VERSION_NO`。
- 非分页服务默认按单值字段读取，A/R 不需要加入 `PAGE_SERVICES`。
- `TuxedoResponseMapper.FIELD_NAMES` 已将六个 FML32 字段映射为目标 JSON 字段。
- `BaseJsonServlet.httpStatus` 已将 `3004` 映射为 HTTP 409。

不要修改这些类。

## 9. 服务导出和接口配置

这些配置属于真实调用链的一部分，缺少任何一项都会导致 WebFE 无法调用新服务。

### 9.1 `cnapspocsvr.c`

必须使用锚点局部插入，不得用字符串替换覆盖原声明。预期补丁为：

```diff
 void CNAPS5702I(TPSVCINFO *rqst);
+void CNAPS5702A(TPSVCINFO *rqst);
+void CNAPS5702R(TPSVCINFO *rqst);
```

修改后 `CNAPS5702I`、`CNAPS5702A`、`CNAPS5702R` 必须各出现一次。该文件预期仅增加 2 行，不删除任何行。

### 9.2 `tuxedo-server/Makefile`

在 `SERVICES` 末尾增加：

```text
CNAPS5702A CNAPS5702R
```

`SOURCES` 已使用 `$(wildcard src/services/*.c)`，不要再单独添加 `cnaps_review.c`。

### 9.3 `tuxedo/UBBCONFIG`

在 `*SERVICES` 末尾增加：

```text
CNAPS5702A
CNAPS5702R
```

### 9.4 `tuxedo/jolt/cnaps_services.bulk`

参考现有 `CNAPS5701D` 的公共输入和错误字段，但只声明审核动作所需字段。只能在文件末尾追加两个 service 块；禁止解析后重新生成、全量写回、格式化或修改任何已有 service。现有 repeated 字段的 `count=0` 必须全部保留。

追加的两个块服务名分别为 `CNAPS5702A` 和 `CNAPS5702R`：

```text
service=CNAPS5702A
export=true
inbuf=FML32
outbuf=FML32
param=REQUEST_ID
type=string
access=in
param=REQ_ID
type=string
access=in
param=OPERATOR_NO
type=string
access=in
param=BRANCH_NO
type=string
access=in
param=BILL_ID
type=string
access=inout
param=STATUS
type=string
access=out
param=CHECKER_NO
type=string
access=out
param=CHECKER_TIME
type=string
access=out
param=LAST_ACTION
type=string
access=out
param=VERSION_NO
type=long
access=out
param=RESP_CODE
type=string
access=outerr
param=RESP_MSG
type=string
access=outerr
```

复制该块并只把 `service` 改为 `CNAPS5702R`。不要增加完整凭证字段、reason/comment 字段或 `CNAPS5702Q` 块。

该文件预期变更为 82 行新增、0 行删除。修改后必须满足：

```text
service=CNAPS5702A 恰好 1 个
service=CNAPS5702R 恰好 1 个
DICT_TYPE 后仍包含 count=0
所有原有 service 块内容保持不变
```

## 10. 最终代码行为

### 10.1 待审核列表

```text
请求 Body
 -> Servlet 校验日期并覆盖 STATUS=PENDING_REVIEW
 -> CNAPS4609Q 按 STATUS + BRANCH_NO 查询
 -> 返回现有分页结构
```

客户端即使传 `status=20_REVIEW_APPROVED`，最终 Tuxedo 请求也必须是 `STATUS=10_PENDING_REVIEW`。

### 10.2 审核动作

```text
URL billId
 -> RequestSupport 写 billId
 -> RequestMapper 转 BILL_ID 并注入 OPERATOR_NO/REQ_ID
 -> CNAPS5702A/R 读取当前记录
 -> 校验 PENDING_REVIEW
 -> BILL_ID + VERSION_NO 乐观锁更新
 -> Oracle 写审核时间并递增版本
 -> 返回六个摘要字段
```

错误结果：不存在返回 `3001`；状态不允许或并发版本变化返回 `3004`；数据库错误返回 `4001`。

## 11. OpenCode 改动与验证边界

完成第 2 节八个文件后停止，不进行以下扩展：

- 不新增查询服务、数据表、字段、DTO 或公共抽象层。
- 不重构现有 CRUD、查询、Jolt 客户端和响应映射。
- 不增加前端、权限、审核原因、批量审核或历史流水。
- 不修改与本功能无关的文件。

### 11.1 低成本结构检查

完成编辑后先检查 diff，不要立即启动 Maven：

```bash
git diff --check
git diff --numstat -- tuxedo-server/src/cnapspocsvr.c
git diff --numstat -- tuxedo/jolt/cnaps_services.bulk
grep -c '^void CNAPS5702I' tuxedo-server/src/cnapspocsvr.c
grep -c '^void CNAPS5702A' tuxedo-server/src/cnapspocsvr.c
grep -c '^void CNAPS5702R' tuxedo-server/src/cnapspocsvr.c
grep -c '^service=CNAPS5702A$' tuxedo/jolt/cnaps_services.bulk
grep -c '^service=CNAPS5702R$' tuxedo/jolt/cnaps_services.bulk
grep -A3 '^param=DICT_TYPE$' tuxedo/jolt/cnaps_services.bulk
```

预期：三个声明计数均为 1，两个 service 计数均为 1，`DICT_TYPE` 输出包含 `count=0`；`cnapspocsvr.c` 为 2 行新增、0 行删除，bulk 为 82 行新增、0 行删除。任何一个结果不符时，只修复对应文件，不进入 Maven。

### 11.2 唯一 Maven 命令

结构检查通过后只执行一次：

```bash
mvn -f web-fe/pom.xml clean package
```

不要先执行 `-DskipTests package` 再执行完整 Maven。现有测试用于发现 metadata 等既有内容被误改，不要求新增或修改审核测试。

### 11.3 C 工具链能力判断

只有当前机器同时具备 `buildserver`、Tuxedo `atmi.h` 和 Oracle SDK `oci.h` 时才执行 C 编译。缺少任一项时：

- 不运行 `make`。
- 不追查 C LSP 对外部头文件的报错。
- 不修改 include 或业务代码来规避环境缺失。
- 在最终反馈中记录“当前环境缺少 Tuxedo/Oracle C 工具链，未执行原生编译”。

最终反馈只需列出实际修改文件和三个 API 的实现结果。
