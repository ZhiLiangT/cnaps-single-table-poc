# Oracle OCI UTF-8 运行配置设计

## 目标

将 Oracle OCI 客户端字符集固定为 `AL32UTF8`，保证凭证录入、修改和查询链路中的中文不会在 Tuxedo C 服务与 Oracle 之间转换为替换字符。该配置必须进入受版本控制的标准启动流程，日常重启、systemd 开机启动和重新部署后均自动生效。

本次不修改数据库结构，不初始化数据库，也不尝试推测或恢复已经损坏的中文内容。

## 根因与设计选择

当前 C 服务通过 `OCIEnvCreate` 创建 OCI 环境，字符集取决于客户端 NLS 环境。运行中的 Tuxedo 服务没有 `NLS_LANG`，同一 Oracle 客户端在未设置该变量时把中文输出为 `?`，设置 `.AL32UTF8` 后能输出正确 UTF-8 字节。受影响记录在数据库中已经包含 Unicode 替换字符，说明损坏发生在写入链路而非浏览器展示层。

采用在受版本控制的 `conf/tuxedo.env` 中提供默认值的方案：

```sh
export NLS_LANG=${NLS_LANG:-AMERICAN_AMERICA.AL32UTF8}
```

选择该位置的原因如下：

- `scripts/start-tuxedo.sh`、构建、预检和元数据加载流程已经统一加载 `conf/tuxedo.env`。
- systemd 的 Tuxedo 单元调用仓库内启动脚本，因此开机启动和人工重启走同一配置入口。
- `conf/db.env` 含虚拟机本地数据库凭据并由同步流程特殊保留，不适合作为可审查、可复制到新环境的唯一修复位置。
- 使用默认值写法允许运维人员在确有需要时显式覆盖，同时默认保证当前 UTF-8 数据链路。

不在本次改用 `OCIEnvNlsCreate` 或调整 Jolt/FML 字段类型；这些改动范围更大，且当前问题可由已验证的 OCI 客户端字符集配置直接解决。

## 启动与数据流

Tuxedo 启动入口先加载 `conf/env.linux.sh` 和 `conf/tuxedo.env`，再加载数据库连接配置。其他构建、预检和元数据入口也统一加载 `conf/tuxedo.env`。`NLS_LANG` 因此在执行 `tmboot` 前进入进程环境，并由 JSL、JSH、业务 C 服务及其 OCI 会话继承。

配置生效后的目标数据流为：

1. WebFE 以 UTF-8 接收中文 JSON。
2. Jolt 将文本交给 Tuxedo C 服务。
3. C 服务持有 UTF-8 字节，并以 `AL32UTF8` OCI 客户端字符集绑定到 Oracle。
4. Oracle 的 `AL32UTF8` 数据库字符集保存原中文。
5. 查询按相反方向返回，API 响应保持 UTF-8。

## 测试与部署

先在现有部署契约测试中增加断言，要求 `conf/tuxedo.env` 明确提供 `NLS_LANG` 的 `AMERICAN_AMERICA.AL32UTF8` 默认值。测试必须在修改生产配置前失败，并在配置落地后通过。

本地验证包括：

- 运行针对部署配置的 Maven 测试；
- 运行 WebFE 完整 Maven 测试；
- 检查 Git 差异只包含本次配置、测试和运维说明。

虚拟机部署沿用现有安全同步流程，保留虚拟机本地 `conf/db.env`，不执行 `scripts/init-db.sh`。同步后重启 Tuxedo，使新环境变量进入进程；Tomcat 无需因该变量单独重启，除非现有统一重启流程包含它。

运行验收包括：

- `cnapspocsvr` 进程环境包含 `NLS_LANG=AMERICAN_AMERICA.AL32UTF8`；
- Tuxedo、JSL 和 Oracle 健康状态正常；
- 同一 OCI 环境查询已知中文字符时返回正确 UTF-8 字节，而不是 `3F`；
- 通过凭证 API 新建一条带唯一标记的中文验收数据，查询结果和数据库原始 UTF-8 字节均保持一致；
- 验收完成后通过现有 API 逻辑删除该临时凭证，使其不再出现在默认业务列表中；数据库仍按现有审计规则保留这条已删除测试记录。

## 失败处理与数据边界

若 Tuxedo 重启失败，停止在失败阶段并检查 ULOG，不继续执行写入验收。若环境变量已继承但 OCI 只读字符测试仍返回 `3F`，停止新增数据并重新检查 Oracle Instant Client 的 NLS 行为。

历史记录中已经保存为 `U+FFFD` 或 `?` 的字符不可逆，不能根据替换字符还原原文。本次部署不批量更新这些记录；需要业务人员依据原始凭证重新录入或人工修正。
