# CNAPS 运行环境自动化设计

## 目标

为虚拟机上 `/home/tian/cnaps-single-table-poc` 项目提供可重复执行的统一运维入口。操作人员无需逐条执行已有脚本，即可完成 Oracle XE、Tuxedo/Jolt 和 Tomcat 整套链路的重新编译、部署、启动、停止、状态检查和验证。

自动化必须保留虚拟机本地的 `conf/db.env`，日常操作不得初始化或重建数据库对象；任何必要服务或健康检查不可用时，脚本必须明确失败并返回非零退出码。

## 方案选择

采用“仓库内统一控制脚本 + 便捷入口脚本 + systemd 开机编排”的组合方案。该方案吸收了以下几种方式的优点：

- 只使用脚本容易调试，但虚拟机重启后不能自动恢复服务。
- 只使用 systemd 可以启动服务，但不适合承担编译和部署操作。
- 组合方案把编译部署逻辑保留在项目脚本中，systemd 也调用相同的 Tuxedo 启停脚本，避免维护两套运行逻辑。

## 命令接口

新增统一入口 `scripts/cnapsctl.sh`，支持以下命令：

- `up`：按照依赖顺序启动 Oracle XE、Tuxedo 和 Tomcat；已经正常运行的服务直接跳过。
- `down`：按照相反顺序停止 Tomcat 和 Tuxedo。默认保留 Oracle 运行，避免无必要地关闭数据库；使用 `down --all` 时同时停止 Oracle XE。
- `restart`：依次执行 `down` 和 `up`。
- `status`：显示 systemd 服务状态、预期端口、Tuxedo 服务状态以及 HTTP 健康接口响应。
- `health`：调用 WebFE 健康接口；只有 HTTP 调用成功、`respCode` 为 `0000`，并且 Oracle、Tuxedo、WebFE 均为 `UP` 时才返回成功。
- `rebuild-deploy`：执行环境预检、停止应用层、编译 C 服务、加载 Jolt 元数据和 TUXCONFIG、编译并测试 WAR、部署、启动整套服务，最后执行健康检查。
- `logs`：输出最近的 Tuxedo ULOG 和 Tomcat journal 日志。
- `install-autostart`：通过专用安装脚本安装并启用 systemd 开机自启配置。

新增 `scripts/up.sh`、`scripts/down.sh` 和 `scripts/rebuild-deploy.sh` 作为便捷入口，保证常用操作容易发现。所有脚本从自身位置计算 `APP_HOME`，允许通过环境变量覆盖配置，并使用 Oracle Linux 8 兼容的 POSIX Shell 语法。

## 运行行为

统一控制器复用现有的细分脚本，不重复实现编译或 Tuxedo 配置逻辑。调用细分脚本前先判断服务状态，保证重复执行安全：

1. 确认 Oracle XE 已启动并监听 `1521`。
2. 仅在 Tuxedo bulletin board 或 JSL 不存在时启动 Tuxedo，并验证 `8000`。
3. 根据操作需要启动或重启 Tomcat，并验证 `8080`。
4. 在限定时间内轮询健康接口，成功后才宣告启动完成。

`rebuild-deploy` 永远不执行 `init-db.sh`。替换二进制或 TUXCONFIG 前，它会停止 Tomcat 以及正在运行的 Tuxedo 域，然后依次调用现有的编译、元数据加载、配置加载和 WAR 部署脚本。任一步骤失败时立即以非零状态退出，显示失败阶段，并提示检查 `logs/ULOG*` 和 Tomcat journal。

只有 Oracle/Tomcat 的 systemd 操作、WAR 安装和 systemd 配置安装需要使用 sudo。密码仅由终端交互输入，不写入仓库或脚本。

## 开机自动化

新增模板化的 `cnaps-tuxedo.service`，安装后作为 `Type=oneshot`、`RemainAfterExit=yes` 的 systemd 单元运行。该服务使用项目所有者账号启动，依赖并晚于 `oracle-xe-21c.service`，启停操作委托给仓库中的脚本。

同时安装 Tomcat systemd drop-in，使 Tomcat 依赖并晚于 `cnaps-tuxedo.service` 启动。安装脚本根据当前项目绝对路径和用户生成配置，将相关文件安装到 `/etc/systemd/system`，执行 daemon reload，并启用 Oracle XE、CNAPS Tuxedo 和 Tomcat。重复运行安装脚本会更新其管理的文件，不修改无关的 systemd 配置。

## 使用文档和提示词控制

新增运维使用文档，覆盖一次性安装、完整重新编译部署、日常启停、状态和健康检查、日志查看、故障恢复、卸载，以及预期端口和访问地址。文档必须明确标注：数据库初始化只能在首次安装时执行。

文档同时提供可直接使用的中文 Codex 提示词，覆盖以下场景：

- 完整编译部署并执行端到端验证；
- 日常启动、停止、重启和状态检查；
- 仅根据日志诊断问题，不修改代码；
- 保留 `conf/db.env` 的安全代码同步；
- 安装或修复开机自启配置。

所有提示词明确虚拟机地址和项目路径，禁止提交或输出敏感信息，并要求 Codex 报告关键命令证据和失败阶段。

## 测试和验收

新增 Shell 契约测试，通过替代 `sudo`、`systemctl`、Tuxedo 命令、端口检查和 HTTP 响应运行，不连接真实虚拟机。测试覆盖命令分发、服务启动顺序、服务已启动时的幂等性、`down --all`、健康检查失败，以及重新编译部署中途失败后立即退出。

虚拟机验收要求如下：

- Shell 契约测试和现有 Maven 测试全部通过。
- `rebuild-deploy` 能生成 C 服务和 WAR，并以 Oracle、Tuxedo、WebFE 均为 `UP` 结束。
- 重复执行 `up` 不会重复启动 Tuxedo 域。
- 虚拟机重启后，systemd 显示 Oracle XE、CNAPS Tuxedo 和 Tomcat 均处于活动状态。
- Windows 本机可以通过 `8080` 端口访问健康接口。

## 约束

- `conf/db.env` 始终保留在虚拟机本地，不提交、不复制、不输出。
- 数据库 Schema 创建不属于日常自动化范围。
- Oracle 保持当前本机连接配置，不为 `1521` 或 Jolt `8000` 新增防火墙开放规则。
- 保留现有细分脚本，供问题诊断和手工恢复使用。
