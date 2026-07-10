# CNAPS 运行环境自动化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Oracle Linux 8 虚拟机上的 CNAPS POC 提供一键编译部署、日常启停、状态检查、开机自启和中文提示词运维文档。

**Architecture:** 使用 `scripts/cnapsctl.sh` 统一编排已有细分脚本，便捷包装脚本只转发参数。systemd 模板和安装器负责 Oracle XE、Tuxedo、Tomcat 的启动依赖，Shell 契约测试通过替代外部命令验证顺序、幂等性和失败处理。

**Tech Stack:** POSIX Shell、systemd、Oracle XE 21c、Oracle Tuxedo 22c、Tomcat 9、curl、Maven/JUnit 5。

## 全局约束

- `conf/db.env` 不提交、不复制、不打印，自动化不得执行 `scripts/init-db.sh`。
- `1521` 和 `8000` 只供虚拟机本机链路使用，不新增防火墙规则。
- 所有 sudo 密码均由终端交互输入，脚本不得保存密码。
- 保留现有细分脚本，统一控制器只负责编排和验证。

---

### Task 1: 一键运行控制器

**Files:**
- Create: `scripts/cnapsctl.sh`
- Create: `scripts/up.sh`
- Create: `scripts/down.sh`
- Create: `scripts/rebuild-deploy.sh`
- Test: `scripts/tests/cnapsctl-test.sh`

**Interfaces:**
- Consumes: 现有 `preflight.sh`、`build-c.sh`、`load-jolt-metadata.sh`、`load-tuxconfig.sh`、`build-web.sh`、`deploy-web.sh`、`start-tuxedo.sh`、`stop-tuxedo.sh`、`status-tuxedo.sh`。
- Produces: `cnapsctl.sh up|down [--all]|restart|status|health|logs|rebuild-deploy|tuxedo-up|tuxedo-down`。

- [ ] **Step 1: 编写失败的 Shell 契约测试**

测试使用临时目录中的 `sudo`、`systemctl`、`ss`、`curl` 和项目细分脚本替身，断言：

```sh
run_ctl up
assert_order "systemctl start oracle-xe-21c" "script start-tuxedo" "systemctl start tomcat"
run_ctl up
assert_not_logged "systemctl start oracle-xe-21c"
assert_not_logged "script start-tuxedo"
run_ctl down --all
assert_logged "systemctl stop oracle-xe-21c"
```

- [ ] **Step 2: 在 VM 上运行测试并确认因控制器不存在而失败**

Run: `sh scripts/tests/cnapsctl-test.sh`

Expected: FAIL，明确指出 `scripts/cnapsctl.sh` 不存在。

- [ ] **Step 3: 实现最小控制器和便捷包装脚本**

控制器必须：从脚本路径计算 `APP_HOME`；以 Oracle -> Tuxedo -> Tomcat 顺序启动；以 Tomcat -> Tuxedo -> Oracle 顺序停止；通过限定次数轮询端口和健康接口；使用 `CURRENT_STAGE` 在失败时输出阶段和日志提示；完整部署过程中永不调用 `init-db.sh`。

包装脚本统一采用：

```sh
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "$SCRIPT_DIR/cnapsctl.sh" up "$@"
```

- [ ] **Step 4: 运行契约测试并确认通过**

Run: `sh scripts/tests/cnapsctl-test.sh`

Expected: 所有用例输出 `PASS`，退出码为 0。

- [ ] **Step 5: 提交控制器**

```bash
git add scripts/cnapsctl.sh scripts/up.sh scripts/down.sh scripts/rebuild-deploy.sh scripts/tests/cnapsctl-test.sh
git commit -m "feat: automate cnaps runtime lifecycle"
```

### Task 2: systemd 开机自启

**Files:**
- Create: `ops/systemd/cnaps-tuxedo.service.in`
- Create: `ops/systemd/tomcat-cnaps.conf`
- Create: `scripts/install-systemd.sh`
- Test: `scripts/tests/install-systemd-test.sh`
- Modify: `scripts/cnapsctl.sh`

**Interfaces:**
- Consumes: `cnapsctl.sh tuxedo-up|tuxedo-down`。
- Produces: `/etc/systemd/system/cnaps-tuxedo.service` 和 `/etc/systemd/system/tomcat.service.d/cnaps.conf`。

- [ ] **Step 1: 编写失败的 systemd 安装器测试**

测试设置 `SYSTEMD_ROOT` 到临时目录并替换 sudo/systemctl，执行：

```sh
SYSTEMD_ROOT="$TMP/systemd" RUN_USER=tian sh scripts/install-systemd.sh
grep -F "User=tian" "$TMP/systemd/cnaps-tuxedo.service"
grep -F "Requires=cnaps-tuxedo.service" "$TMP/systemd/tomcat.service.d/cnaps.conf"
assert_logged "systemctl enable oracle-xe-21c cnaps-tuxedo tomcat"
```

- [ ] **Step 2: 运行测试并确认因安装器不存在而失败**

Run: `sh scripts/tests/install-systemd-test.sh`

Expected: FAIL，指出 `scripts/install-systemd.sh` 不存在。

- [ ] **Step 3: 实现模板和幂等安装器**

`cnaps-tuxedo.service` 使用 `Type=oneshot`、`RemainAfterExit=yes`、`Requires/After=oracle-xe-21c.service` 和 `Before=tomcat.service`；Tomcat drop-in 使用 `Requires/After=cnaps-tuxedo.service`。安装器替换 `@APP_HOME@`、`@RUN_USER@`，安装文件后执行：

```sh
systemctl daemon-reload
systemctl enable oracle-xe-21c cnaps-tuxedo tomcat
```

- [ ] **Step 4: 运行两个 Shell 契约测试**

Run: `sh scripts/tests/cnapsctl-test.sh && sh scripts/tests/install-systemd-test.sh`

Expected: 全部 PASS。

- [ ] **Step 5: 提交 systemd 自动化**

```bash
git add ops/systemd scripts/install-systemd.sh scripts/cnapsctl.sh scripts/tests/install-systemd-test.sh
git commit -m "feat: add cnaps boot automation"
```

### Task 3: 中文运维文档和 Codex 提示词

**Files:**
- Create: `docs/cnaps-operations.md`
- Modify: `web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java`

**Interfaces:**
- Consumes: Task 1 和 Task 2 的命令接口。
- Produces: 人工命令手册及可直接粘贴给 Codex 的中文提示词。

- [ ] **Step 1: 添加失败的文档完整性测试**

在 `DeploymentArtifactTest` 中断言文档包含：

```java
assertThat(operations).contains(
    "./scripts/rebuild-deploy.sh",
    "./scripts/up.sh",
    "./scripts/down.sh --all",
    "./scripts/cnapsctl.sh install-autostart",
    "不要提交、复制或输出 conf/db.env");
```

- [ ] **Step 2: 运行 Maven 测试并确认文档不存在导致失败**

Run: `mvn -f web-fe/pom.xml test -Dtest=DeploymentArtifactTest`

Expected: FAIL，指出 `docs/cnaps-operations.md` 不存在或缺少约定内容。

- [ ] **Step 3: 编写中文运维和提示词文档**

文档覆盖首次安装、完整部署、日常启停、健康状态、日志、故障恢复、systemd 卸载和安全边界，并提供完整部署、启停、只读诊断、安全同步、修复开机自启五类提示词。每条提示词固定 VM 地址 `192.168.84.134` 和项目路径 `/home/tian/cnaps-single-table-poc`，要求报告命令证据且不得输出凭据。

- [ ] **Step 4: 运行 Maven 和 Shell 测试**

Run: `mvn -f web-fe/pom.xml test`

Run: `sh scripts/tests/cnapsctl-test.sh && sh scripts/tests/install-systemd-test.sh`

Expected: 全部通过。

- [ ] **Step 5: 提交文档**

```bash
git add docs/cnaps-operations.md web-fe/src/test/java/com/ruisui/cnaps/web/tuxedo/DeploymentArtifactTest.java
git commit -m "docs: add cnaps automation runbook"
```

### Task 4: 虚拟机部署和端到端验收

**Files:**
- Sync: Task 1-3 的提交到 `/home/tian/cnaps-single-table-poc`
- Preserve: `/home/tian/cnaps-single-table-poc/conf/db.env`

**Interfaces:**
- Consumes: 已提交的自动化脚本、systemd 模板和运维文档。
- Produces: VM 上已安装的开机服务和通过验证的真实 Oracle/Tuxedo/Tomcat 链路。

- [ ] **Step 1: 推送当前分支并在 VM 快进同步**

Run: `git push origin feature/cnaps-single-table-poc`

Run on VM: `git pull --ff-only origin feature/cnaps-single-table-poc`

Expected: VM 的 `conf/db.env` 保持本地修改，其余代码更新到最新提交。

- [ ] **Step 2: 在 VM 运行全部测试**

Run: `sh scripts/tests/cnapsctl-test.sh && sh scripts/tests/install-systemd-test.sh && mvn -f web-fe/pom.xml test`

Expected: 全部通过。

- [ ] **Step 3: 安装开机自动化并完整部署**

Run: `./scripts/cnapsctl.sh install-autostart`

Run: `./scripts/rebuild-deploy.sh`

Expected: systemd 单元已启用，编译部署成功，健康接口三项均为 `UP`。

- [ ] **Step 4: 验证幂等性和宿主机访问**

Run on VM: `./scripts/up.sh && ./scripts/cnapsctl.sh status`

Run on Windows: `curl.exe -fsS http://192.168.84.134:8080/ruisui-bank-sim/api/health`

Expected: 不重复启动 Tuxedo，Windows 能收到 `respCode=0000` 和三项 `UP`。
