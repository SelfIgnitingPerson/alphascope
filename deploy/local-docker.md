# 本机 Docker 应用 + 本机 MySQL

适用于这台 Mac 的 Docker Desktop。Vue构建结果由Java JAR直接提供，应用和轻量Nginx入口在Docker运行；MySQL继续使用本机现有服务、账号和数据库。无需域名、证书或Vite，不启动任何MySQL容器，也不复制业务库。

## 启动与停止

先打开Docker Desktop、启动本机MySQL。根目录`.env`沿用已经验证的`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD`；不要用模板覆盖已有密码。

```bash
# 在项目根目录运行；首次构建会下载Node、Maven、Java和Nginx镜像及依赖。
bash scripts/run-docker.sh up
```

打开 <http://127.0.0.1:5173/>。同一个地址提供前端和API，健康检查为 <http://127.0.0.1:5173/actuator/health>。

| 操作 | 命令 |
| --- | --- |
| 首次启动或更新代码后重新构建并启动 | `bash scripts/run-docker.sh up` |
| 使用已有镜像重新启动并加载配置 | `bash scripts/run-docker.sh start` |
| 查看容器状态 | `bash scripts/run-docker.sh status` |
| 跟踪应用与入口日志 | `bash scripts/run-docker.sh logs` |
| 停止并移除应用容器 | `bash scripts/run-docker.sh stop` |
| 仅校验Compose配置 | `bash scripts/run-docker.sh check` |

`stop`不停止本机MySQL，不删除数据库或`.runtime`。修改代码使用`up`；修改`.env`后使用`start`或`up`重新创建容器，单独`docker restart`不会重新加载环境变量。日常运行不需要在Mac安装Node或Java，源码镜像构建在容器内完成。

切换前先正常停止旧的本机Java后端和Vite，避免5173端口冲突或两个后端共同消费任务。脚本发现8080上的本项目Java进程会停止操作，不会自动杀进程。回到开发方式时先执行`stop`，再运行原来的后端和Vite启动命令。

## 连接与数据保留

- 容器中的`127.0.0.1`指向容器本身；本机MySQL通过`host.docker.internal`连接，端口默认3306。容器只获得应用专用账号，不传入MySQL管理员密码。
- 保留LOCAL访问方式与原有用户1的数据，不改成PUBLIC、不重建账号、不清空自选或报告。
- 项目`.runtime`挂载到容器`/app/.runtime`，脚本传入当前Mac用户UID/GID。已有BaoStock访问限制、请求预算与研究资料继续保留，不因容器重建清零。
- Nginx与Java共用网络命名空间，Java仅监听容器回环8080，入口转发至回环；宿主机仅发布`127.0.0.1:5173`。不增加后端可信远端IP，不放宽转发头检查。
- 仅供本机访问，不能把5173改成`0.0.0.0`用于多人或公网。公网身份、来源许可和HTTPS使用[生产部署](README.md)的独立方案。
- 自动备份继续关闭；启动会执行正常的Flyway校验与队列恢复，是否启用行情定时任务沿用根目录`SCHEDULER_ENABLED`。

## 常见故障

| 现象 | 核对方法 |
| --- | --- |
| `Cannot connect to the Docker daemon` | 先打开Docker Desktop，等待引擎就绪 |
| 5173端口被占用 | 停止原Vite，再启动容器；不要同时运行两套页面服务 |
| 镜像拉取EOF、超时或解析失败 | 查看日志中的镜像仓库地址，检查Docker Desktop网络、代理和镜像加速器；不是数据库或行情权限问题 |
| MySQL连接拒绝或超时 | 确认本机MySQL启动、`.env`端口正确，MySQL可接受Docker Desktop转发连接 |
| MySQL `Access denied` | 确认专用账号密码和允许的来源；不要因此把运行账号换成root或盲目授权所有主机 |
| 应用不健康、入口未启动 | `logs`先看app的Hikari、Flyway与启动异常；入口等待app健康后才启动 |
| 行情仍提示访问受限 | 容器化不恢复供应商权限；既有错误码与冷却时间继续有效 |

官方原理：[Docker Desktop访问宿主机](https://docs.docker.com/desktop/features/networking/networking-how-tos/)、[Compose网络命名空间](https://docs.docker.com/compose/how-tos/networking/)。
