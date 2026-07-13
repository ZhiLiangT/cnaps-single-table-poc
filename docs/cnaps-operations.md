# CNAPS POC 自动化运维手册

## 1. 适用环境

本文适用于以下部署：

- 虚拟机：`192.168.84.134`
- SSH 用户：`tian`
- 项目目录：`/home/tian/cnaps-single-table-poc`
- Oracle XE：`127.0.0.1:1521/XEPDB1`
- Jolt JSL：`127.0.0.1:8000`
- Tomcat/API：`192.168.84.134:8080`
- 健康接口：`http://192.168.84.134:8080/ruisui-bank-sim/api/health`

运行链路为：

```text
WebFE/Tomcat -> Jolt -> Tuxedo C 服务 -> OCI -> Oracle XE
```

## 2. 安全边界

不要提交、复制或输出 conf/db.env。该文件只保留在虚拟机中，权限应为 `600`，其中的数据库密码不得出现在 Git、日志、提示词或终端回显中。

日常启动、重启和重新部署都不会运行 `scripts/init-db.sh`。数据库初始化脚本只允许在全新数据库首次建表时手工执行，不能放入开机自启或日常部署流程。

sudo 密码只在终端需要时交互输入，不写入脚本或提示词。

### 2.1 Oracle OCI 中文字符集

`conf/tuxedo.env` 默认导出 `NLS_LANG=AMERICAN_AMERICA.AL32UTF8`。Tuxedo 启动脚本和 systemd 开机服务都会在 `tmboot` 前加载该文件，使 JSL、JSH、业务 C 服务及 OCI 会话继承 UTF-8 客户端字符集。不要只在交互式终端临时设置该变量，否则服务重启后会再次丢失。

修改该配置后必须重启 Tuxedo，并确认业务服务进程环境：

```bash
p=$(pgrep -x cnapspocsvr | head -1)
tr '\0' '\n' < "/proc/$p/environ" | grep '^NLS_LANG=AMERICAN_AMERICA.AL32UTF8$'
```

历史记录中已经变成 `U+FFFD` 或 `?` 的内容无法从替换字符还原，必须依据原始凭证重新录入或人工修正。

## 3. 自动化命令

进入项目：

```bash
ssh tian@192.168.84.134
cd /home/tian/cnaps-single-table-poc
```

常用入口：

| 操作 | 命令 | 说明 |
| --- | --- | --- |
| 完整编译部署 | `./scripts/rebuild-deploy.sh` | 编译 C 和 WAR、加载 Tuxedo/Jolt 配置、部署并健康检查 |
| 日常启动 | `./scripts/up.sh` | 按 Oracle、Tuxedo、Tomcat 顺序启动，已运行的服务会跳过 |
| 日常停止 | `./scripts/down.sh` | 停止 Tomcat 和 Tuxedo，保留 Oracle |
| 全部停止 | `./scripts/down.sh --all` | 同时停止 Tomcat、Tuxedo 和 Oracle |
| 重启应用链路 | `./scripts/cnapsctl.sh restart` | 保留 Oracle，重启 Tuxedo 和 Tomcat |
| 查看状态 | `./scripts/cnapsctl.sh status` | 显示服务、端口、Tuxedo 和健康接口状态 |
| 健康检查 | `./scripts/cnapsctl.sh health` | 三个组件全部为 `UP` 才返回成功 |
| 查看日志 | `./scripts/cnapsctl.sh logs` | 输出最近的 Tuxedo ULOG 和 Tomcat journal |
| 安装开机自启 | `./scripts/cnapsctl.sh install-autostart` | 安装并启用 systemd 配置 |

查看全部命令：

```bash
./scripts/cnapsctl.sh --help
```

## 4. 首次安装开机自启

项目依赖、Oracle、Tuxedo 和 Tomcat 已经安装完成时，只需执行：

```bash
cd /home/tian/cnaps-single-table-poc
chmod 600 conf/db.env
./scripts/cnapsctl.sh install-autostart
./scripts/up.sh
./scripts/cnapsctl.sh status
```

安装脚本会生成：

```text
/etc/systemd/system/cnaps-tuxedo.service
/etc/systemd/system/tomcat.service.d/cnaps.conf
```

开机顺序为：

```text
oracle-xe-21c.service -> cnaps-tuxedo.service -> tomcat.service
```

项目位于用户主目录且虚拟机启用了 SELinux Enforcing。安装器使用 `PAMName=login` 建立 `tian` 用户会话，并为项目普通文件持久配置 `usr_t`、为 `scripts` 和 `tuxedo-server/bin` 配置 `bin_t`。完整部署在重建 C 服务后自动执行 `restorecon`；这些设置不会关闭全局 SELinux。

检查启用状态：

```bash
systemctl is-enabled oracle-xe-21c cnaps-tuxedo tomcat
systemctl status cnaps-tuxedo --no-pager
systemctl status tomcat --no-pager
```

## 5. 代码变更后的完整部署

代码已同步到虚拟机后执行：

```bash
cd /home/tian/cnaps-single-table-poc
./scripts/rebuild-deploy.sh
```

自动执行顺序：

1. 环境预检。
2. 停止 Tomcat 和已有 Tuxedo 域。
3. 编译 `tuxedo-server/bin/cnapspocsvr`。
4. 加载 Jolt metadata 和 TUXCONFIG。
5. 运行 Maven 测试并生成 `web-fe/target/ruisui-bank-sim.war`。
6. 部署 WAR，启动 Oracle、Tuxedo 和 Tomcat。
7. 轮询健康接口，直到 Oracle、Tuxedo、WebFE 均为 `UP`。

任何阶段失败都会停止后续操作，并显示失败阶段和日志检查命令。

## 6. 日常启停和检查

虚拟机启动后，systemd 会自动启动整套链路。需要手工恢复时执行：

```bash
cd /home/tian/cnaps-single-table-poc
./scripts/up.sh
```

停止应用但保留数据库：

```bash
./scripts/down.sh
```

维护虚拟机时全部停止：

```bash
./scripts/down.sh --all
```

状态和健康检查：

```bash
./scripts/cnapsctl.sh status
./scripts/cnapsctl.sh health
```

Windows 本机验证：

```powershell
curl.exe -fsS http://192.168.84.134:8080/ruisui-bank-sim/api/health
```

成功响应必须包含：

```json
{
  "respCode": "0000",
  "data": {
    "oracle": "UP",
    "tuxedo": "UP",
    "webfe": "UP"
  }
}
```

## 7. 日志和故障恢复

统一查看日志：

```bash
./scripts/cnapsctl.sh logs
```

单独检查：

```bash
tail -n 200 logs/ULOG*
sudo journalctl -u tomcat -n 200 --no-pager
sudo journalctl -u cnaps-tuxedo -n 200 --no-pager
ss -ltn | grep -E ':(1521|8000|8080)[[:space:]]'
```

常见现象：

| 现象 | 优先检查 |
| --- | --- |
| `1521` 不监听 | `systemctl status oracle-xe-21c` |
| `8000` 不监听 | Tuxedo ULOG、`systemctl status cnaps-tuxedo` |
| `8080` 不监听 | `journalctl -u tomcat` |
| 健康接口返回 `4002` | JSL/Tuxedo 是否启动，`8000` 是否监听 |
| `oracle=DOWN` | Oracle listener、`conf/db.env` 和业务用户连接 |

恢复顺序：

```bash
./scripts/cnapsctl.sh restart
./scripts/cnapsctl.sh health
```

如果二进制、配置或 WAR 可能不一致，执行完整的：

```bash
./scripts/rebuild-deploy.sh
```

## 8. 卸载开机自启

只卸载本项目新增的 systemd 配置，不卸载 Oracle、Tuxedo 或 Tomcat：

```bash
sudo systemctl disable --now cnaps-tuxedo
sudo rm -f /etc/systemd/system/cnaps-tuxedo.service
sudo rm -f /etc/systemd/system/tomcat.service.d/cnaps.conf
sudo systemctl daemon-reload
sudo systemctl reset-failed
```

删除 Tomcat drop-in 后，如需立即恢复 Tomcat 原始依赖关系，执行：

```bash
sudo systemctl restart tomcat
```

## 9. 使用 Codex 提示词控制操作

以下提示词可直接发送给 Codex。提示词不包含密码；遇到 sudo 需要交互输入时，Codex 应明确提示，不得把密码写入命令、文件或日志。

### 9.1 完整编译部署

```text
通过 SSH 连接 tian@192.168.84.134，在 /home/tian/cnaps-single-table-poc 执行当前项目的完整自动化编译部署。使用 ./scripts/rebuild-deploy.sh，不要手工重复脚本内部步骤，不要运行 scripts/init-db.sh。不要提交、复制或输出 conf/db.env 以及任何密码。完成后执行 ./scripts/cnapsctl.sh status，并从本机访问 http://192.168.84.134:8080/ruisui-bank-sim/api/health。请报告 C 编译、Maven 测试、Tuxedo/JSL、Tomcat、Oracle 和健康接口的实际结果；遇到失败时停在失败阶段并给出日志证据。
```

### 9.2 日常启动并检查

```text
连接虚拟机 tian@192.168.84.134，进入 /home/tian/cnaps-single-table-poc，执行 ./scripts/up.sh，然后执行 ./scripts/cnapsctl.sh status。不要修改代码、数据库数据或 conf/db.env。确认 1521、8000、8080 的监听状态，并报告健康接口中的 oracle、tuxedo、webfe 状态。
```

### 9.3 停止或重启

停止应用层但保留 Oracle：

```text
连接 tian@192.168.84.134，在 /home/tian/cnaps-single-table-poc 执行 ./scripts/down.sh。不要使用 --all。完成后确认 Tomcat 和 Tuxedo 已停止、Oracle XE 仍在运行，不要修改任何配置。
```

重启应用链路：

```text
连接 tian@192.168.84.134，在 /home/tian/cnaps-single-table-poc 执行 ./scripts/cnapsctl.sh restart，然后执行 ./scripts/cnapsctl.sh health。不要运行数据库初始化，不要输出 conf/db.env。报告实际服务状态和健康响应。
```

### 9.4 只读诊断

```text
通过 SSH 只读诊断 tian@192.168.84.134 上 /home/tian/cnaps-single-table-poc 的运行故障。先执行 ./scripts/cnapsctl.sh status 和 ./scripts/cnapsctl.sh logs，再检查 1521、8000、8080 端口及 oracle-xe-21c、cnaps-tuxedo、tomcat 的 systemd 状态。不要重启服务，不要修改代码、配置或数据库，不要读取或输出 conf/db.env 的内容。请按证据说明故障位于 Oracle、Tuxedo/Jolt、Tomcat 还是 WebFE。
```

### 9.5 安全同步代码

```text
将当前分支代码安全同步到虚拟机 tian@192.168.84.134 的 /home/tian/cnaps-single-table-poc。同步前检查本机、远端仓库和虚拟机 Git 状态；必须保留虚拟机本地修改的 conf/db.env，不得覆盖、暂存、提交、复制或输出该文件。只允许 fast-forward 同步。同步后报告本机、远端和虚拟机提交号是否一致，但暂时不要编译部署。
```

### 9.6 安装或修复开机自启

```text
连接 tian@192.168.84.134，进入 /home/tian/cnaps-single-table-poc，使用 ./scripts/cnapsctl.sh install-autostart 安装或修复 CNAPS 开机自启。不要把 sudo 密码写入命令或文件。随后执行 ./scripts/up.sh，检查 oracle-xe-21c、cnaps-tuxedo、tomcat 的 enable/active 状态和启动依赖，最后验证健康接口。不得修改 conf/db.env 或运行 scripts/init-db.sh。
```

## 10. 自动化测试

Shell 契约测试不连接真实服务，可安全重复执行：

```bash
sh scripts/tests/cnapsctl-test.sh
sh scripts/tests/install-systemd-test.sh
```

WebFE 测试：

```bash
mvn -f web-fe/pom.xml test
```

完整 CRUD 冒烟测试会写入并删除测试数据，仅在明确需要验证业务闭环时执行：

```bash
BASE_URL=http://127.0.0.1:8080/ruisui-bank-sim ./scripts/smoke-test.sh
```
