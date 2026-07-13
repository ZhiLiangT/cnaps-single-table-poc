# Complete POC API Document Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the single public HTTP API contract in the detailed format of the original `poc-api.md`, while keeping every field and example aligned with deployed commit `420d671`.

**Architecture:** Keep `docs/cnaps-frontend-api.md` as the only public API contract. Use an executable Java source-contract test to enforce the required detailed structure, all 11 endpoints, headerless examples, current pagination names, and the three new create/update/detail fields.

**Tech Stack:** Markdown, Java 17, JUnit 5, AssertJ, Maven.

## Global Constraints

- Output file is exactly `docs/cnaps-frontend-api.md`; do not create another public API document.
- Title style follows `C:/Users/19141/Desktop/poc-api.md`.
- Version is `v0.4 单表 POC 版`; date is `2026-07-13`.
- Cover exactly the current 11 HTTP endpoints and their Tuxedo services.
- Public callers send no business request headers; JSON examples use only `Content-Type`.
- Top-level responses contain only `respCode`, `respMsg`, and `data`.
- Pagination uses `pageNo`, `pageSize`, `total`, and `records`.
- Create/update accept `payerAddress`, `payeeAddress`, and `payerBankName`; detail returns them; list records do not.
- Update semantics are omitted = preserve, non-empty = overwrite, empty string = clear.
- Do not add account, login, permission, database deployment, or unrelated business logic.
- Preserve the pre-existing untracked `.idea/` directory.

---

### Task 1: Detailed Public API Contract

**Files:**
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`
- Modify: `docs/cnaps-frontend-api.md`

**Interfaces:**
- Consumes: the deployed service contract represented by `TuxedoRequestMapper`, `MockTuxedoClient`, `JoltTuxedoClient`, `tuxedo/jolt/cnaps_services.bulk`, and the approved design spec.
- Produces: one detailed Markdown API contract for frontend, Postman, and curl integration.

- [ ] **Step 1: Write the failing detailed-document contract test**

Add this test to `DeploymentArtifactTest`:

```java
@Test
void frontendApiUsesTheDetailedOriginalFormatForAllCurrentEndpoints() throws Exception {
    String api = Files.readString(root.resolve("docs/cnaps-frontend-api.md"));

    assertThat(api).contains(
        "# 老式银行 Tuxedo 后端模拟系统 API 文档",
        "文档版本：v0.4 单表 POC 版",
        "编写日期：2026-07-13",
        "## 1. 文档说明",
        "## 2. 基础约定",
        "## 3. 通用响应格式",
        "## 4. 接口清单",
        "## 5. 状态模型",
        "## 6. API 详情",
        "## 7. 单据完整字段说明",
        "## 8. 错误码",
        "## 9. 典型联调流程",
        "## 10. POC 边界",
        "### 基本信息",
        "### 请求示例",
        "### 成功响应示例",
        "### 响应字段说明"
    );

    for (String endpoint : List.of(
        "GET /api/health",
        "GET /api/dicts/{dictType}",
        "GET /api/banks",
        "POST /api/cnaps/vouchers",
        "PUT /api/cnaps/vouchers/{billId}",
        "POST /api/cnaps/vouchers/{billId}/delete",
        "GET /api/cnaps/vouchers",
        "GET /api/cnaps/vouchers/review-list",
        "GET /api/cnaps/vouchers/{billId}",
        "POST /api/cnaps/vouchers/{billId}/review-pass",
        "POST /api/cnaps/vouchers/{billId}/review-return"
    )) {
        assertThat(api).contains(endpoint);
    }

    assertThat(api).contains(
        "`payerAddress`", "`payeeAddress`", "`payerBankName`",
        "未传时保留原值", "空字符串", "pageNo", "pageSize", "records"
    );
    assertThat(api).doesNotContain(
        "\"success\"",
        "-H \"requestId:",
        "-H \"operatorNo:",
        "-H \"branchNo:",
        "-H \"workDate:",
        "?page=", "&size="
    );
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn -f web-fe/pom.xml "-Dtest=DeploymentArtifactTest#frontendApiUsesTheDetailedOriginalFormatForAllCurrentEndpoints" test
```

Expected: FAIL because the existing compact document does not contain the v0.4 header and detailed original-format sections.

- [ ] **Step 3: Rewrite the public API document**

Replace `docs/cnaps-frontend-api.md` with a single Markdown document using this exact top-level outline:

```markdown
# 老式银行 Tuxedo 后端模拟系统 API 文档

> 文档版本：v0.4 单表 POC 版<br>
> 编写日期：2026-07-13<br>
> 适用项目：`ruisui-bank-sim`<br>
> 目标环境：Linux + Oracle Tuxedo + Oracle Database + WebFE<br>
> 接口风格：HTTP JSON；WebFE 内部映射为 Tuxedo ATMI / FML32 / Jolt 调用

## 1. 文档说明
## 2. 基础约定
## 3. 通用响应格式
## 4. 接口清单
## 5. 状态模型
## 6. API 详情
## 7. 单据完整字段说明
## 8. 错误码
## 9. 典型联调流程
## 10. POC 边界
```

Under API details, create sections 6.1 through 6.11 in the interface-list order. Each section must contain basic information, relevant parameter tables, a runnable curl command, a success response, response-field descriptions, and applicable business rules.

Use one consistent example lifecycle with `workDate=2026-07-13`, `branchNo=772`, `serialNo=0002000`, and a generated bill ID shaped as `B202607137720002000`. Create/update/detail examples must include:

```json
{
  "payerAddress": "上海市浦东新区示例路 1 号",
  "payeeAddress": "北京市朝阳区示例路 2 号",
  "payerBankName": "中国示例银行上海分行"
}
```

List and review-list record examples must omit those three keys. All JSON response examples use the three-field envelope and all JSON request curl examples use only:

```bash
-H "Content-Type: application/json; charset=UTF-8"
```

- [ ] **Step 4: Run focused and full verification**

Run:

```powershell
mvn -f web-fe/pom.xml "-Dtest=DeploymentArtifactTest" test
mvn -f web-fe/pom.xml test
git diff --check
```

Expected: `DeploymentArtifactTest` passes, the full Maven suite reports zero failures, and `git diff --check` prints nothing.

- [ ] **Step 5: Inspect forbidden legacy content and endpoint coverage**

Run:

```powershell
rg -n '"success"|-H "(requestId|operatorNo|branchNo|workDate):|\?page=|&size=' docs/cnaps-frontend-api.md
rg -n '^## 6\.|^### 6\.' docs/cnaps-frontend-api.md
```

Expected: the first command has no matches; the second shows one API detail parent heading and exactly 11 numbered endpoint headings.

- [ ] **Step 6: Commit the completed document**

```powershell
git add docs/cnaps-frontend-api.md web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java
git commit -m "docs(api): publish complete poc api guide"
```
