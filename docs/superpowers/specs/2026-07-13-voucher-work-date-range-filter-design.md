# 凭证列表工作日期范围筛选设计

## 目标

为以下两个凭证列表接口增加基于 `workDate` 字段的日期范围筛选，同时保留现有精确日期筛选能力：

- `GET /api/cnaps/vouchers`
- `GET /api/cnaps/vouchers/review-list`

当请求未提供任何日期筛选参数时，查询全部符合其他条件的凭证，不再默认筛选当天数据。

## API 约定

列表接口支持以下可选 Query 参数：

- `workDate`：精确工作日期，格式为 `yyyy-MM-dd`。
- `startWorkDate`：工作日期下界，格式为 `yyyy-MM-dd`，筛选包含该日期。
- `endWorkDate`：工作日期上界，格式为 `yyyy-MM-dd`，筛选包含该日期。

允许只传 `startWorkDate` 或只传 `endWorkDate`，分别表示仅设置下界或上界。

为避免冲突，`workDate` 不得与 `startWorkDate`、`endWorkDate` 同时使用。若起止日期同时存在，必须满足 `startWorkDate <= endWorkDate`。日期格式非法、日期不存在、参数混用或区间倒置时，接口返回 HTTP 400 和业务错误码 `2002`。

## 请求处理与数据流

WebFE 在调用 Tuxedo 前完成参数组合与日期合法性校验，并停止为列表查询注入当天 `workDate`。现有 `workDate` 继续映射为 `WORK_DATE`；新增参数分别映射为 `START_WORK_DATE` 和 `END_WORK_DATE`。

两个 Tuxedo 查询服务读取并再次校验三个日期字段，以保护绕过 WebFE 的直接调用。服务把精确日期和范围边界传入统一的 `db_query_vouchers` 查询函数。

数据库查询条件采用以下包含边界的语义：

```sql
(:work_date IS NULL OR WORK_DATE = TO_DATE(:work_date, 'YYYY-MM-DD'))
AND (:start_work_date IS NULL OR WORK_DATE >= TO_DATE(:start_work_date, 'YYYY-MM-DD'))
AND (:end_work_date IS NULL OR WORK_DATE < TO_DATE(:end_work_date, 'YYYY-MM-DD') + 1)
```

结束边界使用次日开区间，避免数据库列含非零时间部分时漏掉结束日期当天的数据。所有日期参数为空时，这三项条件均不限制结果。

Mock Tuxedo 客户端实现相同的筛选与校验语义，确保本地演示行为与真实服务一致。

## 兼容性与范围

现有只传 `workDate` 的调用保持精确匹配行为。唯一有意变更是：没有任何日期参数的列表请求从“默认当天”调整为“全部日期”。其他筛选、分页、排序、删除数据可见性和待复核状态约束保持不变。

本次不增加新的响应字段，不修改凭证录入或修改接口，也不改变数据库表结构。

## 测试策略

测试先行覆盖以下行为：

- WebFE 正确转发三个日期参数，且无日期参数时不注入 `WORK_DATE`。
- 两个列表接口都支持精确日期、双边范围、仅下界和仅上界。
- 起止边界均包含，未传日期时返回全部符合其他条件的数据。
- 非法格式、不存在日期、精确日期与范围混用、起始日期晚于结束日期均被拒绝。
- Tuxedo C 服务字段读取、校验、数据库函数签名和 SQL 绑定保持一致。
- 现有分页、分支筛选、待复核状态筛选和精确 `workDate` 回归测试继续通过。

## 文档更新

更新前端 API 文档中的两个列表接口参数表、请求示例和默认行为说明，明确范围参数、包含边界、参数互斥与全量默认语义。
