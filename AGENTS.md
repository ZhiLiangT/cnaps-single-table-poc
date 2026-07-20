# CNAPS 单表 POC 通用规则

## 项目范围

- 本仓库是仅包含后端的 Java Servlet、Jolt/Tuxedo C 和 Oracle POC。
- 不得添加 JSP、JavaScript、CSS、前端框架或浏览器页面。
- 保留现有的 HTTP 响应封装和 FML32 命名约定。

## 通用开发原则

- 以当前任务指定的需求和设计文档为事实依据；仅在其中未定义 API 细节时参考 `docs/cnaps-frontend-api.md`。
- 只修改当前任务涉及的文件和行为，不得重构无关的 CRUD、查询、Jolt 客户端或响应映射代码。
- 未经明确要求，不得从 Git 历史恢复旧实现，也不得覆盖工作树中已有的用户改动。
- 接口、字段、服务和状态的新增或变更必须在 Java、Tuxedo C、配置及 Jolt 元数据之间保持一致。

## 应复用的现有模式

- Servlet 路由和列表校验：`CnapsVoucherServlet` 和 `RequestSupport`。
- HTTP 到服务的映射：`TuxedoRequestMapper` 中现有的查询和删除分支。
- 原生字段读取、错误处理和事务：`cnaps_delete.c`。
- 审计字段和乐观更新：`cnaps_update.c` 和 `db_helper.c`。
- 状态校验：`cnaps_status.h` 和 `validation_helper.c`。
- FML32 输出：`fml_helper.c`。

## 安全编辑规则

- 使用带明确锚点的小补丁修改现有文件；绝不得重新生成或完整重写现有文件。
- 绝不得使用大范围字符串替换来插入 C 声明。必须保留锚点和所有原始行。
- 除非设计明确要求更改，否则必须保留行顺序、重复字段元数据以及每个现有的 `count=0`。
- 每次编辑现有文件后立即检查差异。如果无关行发生变化，应停止并纠正编辑。
- 编辑 Jolt 元数据时不得解析后重新序列化、重新格式化或重写现有内容；只进行任务所需的局部修改。

## 环境与验证

- Linux POC 虚拟机已在 `/opt` 下安装 Tuxedo `buildserver`、Tuxedo 头文件和 Oracle SDK 头文件。
- 只能在仓库根目录通过 `./scripts/build-c.sh` 编译原生 C 代码。
- 绝不得直接调用 `make` 或 `buildserver`。构建脚本会先加载 `conf/env.linux.sh`、`conf/tuxedo.env` 和 `conf/db.env`，然后再调用 Make。
- 不得根据登录 Shell 的 `PATH` 或直接执行 `command -v buildserver` 的结果判定 Tuxedo 缺失；`conf/tuxedo.env` 会添加 `/opt/tuxedo/bin`。
- 除非 `./scripts/build-c.sh` 产生相同错误，否则 C LSP 关于缺少外部头文件的诊断不具权威性。
- 如果 `./scripts/build-c.sh` 失败，报告其确切错误。不得仅为消除编辑器诊断而修改头文件包含路径或业务代码。
- 完成代码修改后，先运行成本较低的结构检查：
  - `git diff --check`
  - 按改动范围验证服务声明、配置注册和 Jolt 元数据相互一致
  - 确认重复字段元数据和现有 `count=0` 未被意外更改
- 涉及 Java 代码时，在结构检查后只运行一次完整 Maven 构建：`mvn -f web-fe/pom.xml clean package`。
- 不得先运行带 `-DskipTests` 的构建，再运行另一次完整 Maven 构建。
- 涉及原生 C 代码时，在其他适用检查成功后恰好运行一次 `./scripts/build-c.sh`。

## 完成与交付

- 完成任务范围内的全部修改并执行适用验证后，方可报告任务完成。
- 除非得到明确要求，否则不得提交、推送、同步或部署。
- 报告实际修改的文件、已执行的验证，以及任何尚未解决的编译或测试问题。
