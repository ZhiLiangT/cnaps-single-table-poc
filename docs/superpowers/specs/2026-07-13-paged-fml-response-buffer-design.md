# 分页 FML32 响应缓冲区修复设计

## 背景与根因

`BANKQRY`、`CNAPS4609Q` 和 `CNAPS5702Q` 都会在收到 Jolt 请求后，直接复用请求携带的 FML32 缓冲区写分页响应。该缓冲区的初始容量只适合请求字段，无法容纳单据列表的多条重复字段。

真实虚拟机的 Tuxedo ULOG 已确认服务端连续报错：

```text
LIBFML_CAT:3: ERROR: No space in fielded buffer
```

字段写入失败后，服务仍返回了残缺缓冲区。WebFE 随后通过 Jolt 读取响应时，以第一个缺失字段名形成 `PAGE_NO`、`PAGE_SIZE` 或 `BILL_ID` 错误，并最终映射为 HTTP 504。因此这些字段不是非法参数，根因是 C 服务响应容量不足且未阻止残缺响应返回。

## 修复范围

修复以下三个只读分页服务：

- `BANKQRY`
- `CNAPS4609Q`
- `CNAPS5702Q`

保持 HTTP API、请求和响应字段、默认分页值以及最大每页 100 条记录不变。不修改数据库结构、数据、Jolt 服务契约或 `conf/db.env`。

## 设计

在 `tuxedo-server/src/common/fml_helper.c` 增加一个公共响应缓冲区扩容函数，并在 `tuxedo-server/include/cnaps_service.h` 暴露声明。函数接收 `TPSVCINFO *` 和目标容量，通过 `Fsizeof32` 判断当前容量；容量不足时调用 `tprealloc`，成功后同时更新 `rqst->data`，并向调用方返回新的 `FBFR32 *`。

两个单据分页服务在完成请求字段读取和数据库查询、开始写分页字段之前，将响应缓冲区扩容到 1 MiB。该容量覆盖最多 100 条单据记录及其所有重复字段。银行查询最多返回一条静态记录，使用较小的 16 KiB 容量。

如果 `tprealloc` 失败，公共函数记录 Tuxedo 日志并返回失败。服务不再继续写分页数据，也不返回伪成功；它使用仍然有效的原始请求缓冲区返回明确的 `4002` 响应。

## 数据流

1. 服务从原始 FML32 请求缓冲区读取筛选和分页参数。
2. 服务执行查询并得到实际返回行数。
3. 服务调用公共辅助函数扩容响应缓冲区。
4. 扩容成功后，服务写入分页元数据、重复记录和成功响应字段。
5. 扩容失败时，服务停止正常响应构造并返回 `4002`。
6. Jolt 从完整 FML32 响应读取分页字段和记录，WebFE 返回 HTTP 200。

## 测试与验收

按 TDD 顺序先扩展 `TuxedoCSourceContractTest`，验证：

- 公共辅助函数使用 `Fsizeof32` 和 `tprealloc`，并更新 `rqst->data`。
- 三个分页服务在写分页字段和记录前调用扩容函数。
- 扩容失败时三个服务均停止正常响应并返回错误。

随后实现最小 C 代码使测试通过，并运行完整 Maven 测试。部署到 `192.168.84.134` 后执行真实集成验证：

- `/api/banks?pageNo=1&pageSize=1`
- `/api/cnaps/vouchers?pageNo=1&pageSize=1`
- `/api/cnaps/vouchers/review-list?pageNo=1&pageSize=1`
- 健康接口和 `./scripts/cnapsctl.sh status`

三个分页接口必须返回 HTTP 200 和 `respCode=0000`；ULOG 中新的请求时段不得再出现 `No space in fielded buffer`。完整构建和测试必须通过，Oracle、Tuxedo/JSL 和 Tomcat 必须保持 `UP`。
