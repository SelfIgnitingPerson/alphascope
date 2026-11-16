# 本机 MySQL 运行与已有数据迁移

项目默认连接本机 MySQL 8，日常启动不需要 Docker。后端通过 TCP 连接，不使用 root 作为运行账号。已有数据通过迁移保留，不重新执行种子覆盖原库。

## 首次安装空库

启动本机已安装的 MySQL 8，用数据库管理员进入客户端：

```bash
mysql -u root -p
```

创建空库和专用账号。以下密码仅是占位符，实际值应与本机 `.env` 的 `MYSQL_PASSWORD` 一致：

```sql
CREATE DATABASE alphascope CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'alphascope'@'127.0.0.1' IDENTIFIED BY 'replace_with_random_password';
CREATE USER 'alphascope'@'localhost' IDENTIFIED BY 'replace_with_random_password';
GRANT ALL PRIVILEGES ON alphascope.* TO 'alphascope'@'127.0.0.1';
GRANT ALL PRIVILEGES ON alphascope.* TO 'alphascope'@'localhost';
```

已有同名库或账号时先检查归属，不重复创建、改密码或清空。这里仅创建数据库及授权，业务表仍由[唯一V1基线](../../backend/src/main/resources/db/migration/V1__baseline.sql)经Flyway初始化，不另存一份建表SQL。

复制根目录 `.env.example` 为 `.env`，设置：

```dotenv
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=alphascope
MYSQL_USER=alphascope
MYSQL_PASSWORD=replace_with_random_password
```

已有 `.env` 保留，只修改数据库连接项。正常运行不需要管理员密码或 `MYSQL_ROOT_PASSWORD`。如使用 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 完整配置，它们分别优先于上述别名；维护CLI使用相同优先级。

从项目根目录启动：

```bash
bash scripts/run-backend.sh
npm --prefix frontend run dev
```

第二条在另一个终端运行。打开[工作台](http://127.0.0.1:5173/)，并检查[后端健康](http://127.0.0.1:8080/actuator/health)。启动脚本不自动启动容器，也不静默切换其他数据库。

## 从已有 Docker 数据库切换

先确认本机独立实例的版本、socket/端口、管理员权限和目标库为空。原库与目标实例必须具有不同的 server_uuid；不能把“3306能连接”当作已经连到本机。已有同名库或账号不得自动覆盖。

切换顺序：

1. 确认没有活动研究或验证任务，暂停应用及其他写入者。
2. 整库复制结构与数据，包括Flyway历史、账号空间、已冻结报告和完整输入。迁移本身不重算报告、升级结构或重新抓取行情。
3. 使用同一时区和稳定主键顺序逐表核对列、结构、行数及完整内容指纹。JSON保留数组顺序，按精确十进制数值和对象键比较，不能用普通浮点误差容忍来跳过差异。
4. 数值序列化有差异时，先与原库逐字段比对，并在目标副本中保持原始冻结精度；未通过核验前不得切换。不能修改原库、旧证据哈希或关闭Flyway校验来迁就新库。
5. 配置仅拥有本项目库权限的本地专用账号；使用独立凭据，避免旧容器意外启动后应用静默写回旧库。
6. 停止原MySQL容器，保留原数据卷；核对应用实际TCP连接到的实例身份后恢复后端，检查健康、自选、原报告和数据日期。

回退也必须先暂停应用并核对新库有无新增数据；不能直接开启旧容器后继续写入已落后的副本。原库保留不等于切换后的持续备份。

## 当前本机切换记录

2026-11-13已由 Docker MySQL 8.0.40 切换到本机 MySQL 8.0.42，地址 `127.0.0.1:3306`，数据库及运行账号均为 `alphascope`。42张表、69,488条记录的完整结构及精确语义内容通过核验；冻结输入的数值表示差异已按源库值恢复。已有39份报告、5只自选保留，报告#39的接口内容与切换前一致。

迁移通过管道传输，没有生成额外SQL备份文件；临时本机管理员密码已从 `.env` 清除。原 `alphascope-mysql-1` 停止且数据卷保留。证据只在本机 `.runtime/implementation/local-mysql/`，不进入源码包。后续新增数据以本机库为准，不维护两套并行业务库。

## Docker 的保留用途

根目录 `docker-compose.yml` 是可选开发数据库，默认发布到 `127.0.0.1:3307`，避免与本机3306冲突。选择容器时才配置 `MYSQL_ROOT_PASSWORD` 并显式将应用 `MYSQL_PORT` 改为3307；不要同时把两个不同数据实例当作同一业务库使用。

普通 `bash scripts/verify.sh` 不要求 Docker。完整隔离数据库/浏览器回归、可选备份的隔离恢复核验及生产容器部署仍按各自手册使用 Docker；它们不意味着日常业务数据库仍在容器内。用户当前关闭自动备份，不会因此在正常运行中启动验证容器。
