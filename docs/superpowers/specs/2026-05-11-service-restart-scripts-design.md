# 量化全服务重启脚本 Design

## 目标

提供一套终端脚本，统一重启量化开发环境的三类服务：

1. `ruoyi-admin` 后端
2. `ruoyi-ui` 前端 dev 服务
3. Python 异步 worker

用户执行一个总控脚本即可完成“停止旧进程、启动新进程、验证健康状态”，不再手工猜哪个进程该杀、哪个服务是否真的起来了。

## 设计原则

- 只管理当前仓库对应的服务进程，不按裸 `java/node/python` 粗暴全杀。
- 每层服务保留单独脚本，便于单独重启与排障。
- 总控脚本负责统一编排顺序：先停消费端，再停 UI，再停后端；启动时反向进行。
- 每个脚本都输出明确结果：发现了什么、停掉了什么、启动了什么、是否健康。
- 所有启动日志统一落到 `runtime-logs`。

## 脚本清单

- `scripts/restart-ruoyi-admin.ps1`
- `scripts/restart-ruoyi-ui.ps1`
- `scripts/restart-quant-worker.ps1`
- `scripts/restart-all-services.ps1`
- `scripts/check-all-services-runtime.ps1`
- `scripts/lib/service-runtime.ps1`

## 服务识别规则

### ruoyi-admin

- 匹配 `java.exe`
- 命令行包含当前仓库的 `ruoyi-admin/target/ruoyi-admin.jar`
- 兼容相对路径和绝对路径两种启动方式

### ruoyi-ui

- 匹配 `node.exe`
- 命令行包含当前仓库 `ruoyi-ui`
- 命令行包含 `vue-cli-service.js serve`
- 同时联动清理其父 `npm run dev` 进程

### quant worker

- 匹配两类进程：
- `powershell.exe` 监督脚本，命令行包含 `run-quant-worker-loop.ps1`
- `python.exe` 实际执行子进程，命令行包含 `quant_road run-async-worker`
- 识别逻辑仍限定在当前仓库路径下，避免误伤其他 Python 任务

## 启动方式

### ruoyi-admin

- `java -jar ruoyi-admin/target/ruoyi-admin.jar`

### ruoyi-ui

- 在 `ruoyi-ui` 目录执行 `npm run dev`

### quant worker

- 以监督脚本方式常驻：
- `powershell -File scripts/run-quant-worker-loop.ps1`
- 监督脚本会进入 `python` 目录，注入 `PYTHONPATH=src`
- 然后循环执行：
- `python -m quant_road run-async-worker --worker-id ruoyi-web-worker`
- 原因：当前 worker 在队列为空时会返回 `{"status":"IDLE"}` 并退出，不适合作为常驻服务直接拉起

## 健康检查

### ruoyi-admin

- `http://127.0.0.1:8080`
- 再补一条关键接口：`/quant/data/taskCenterSummary`

### ruoyi-ui

- `http://127.0.0.1`
- 返回前端首页 HTML 即视为启动完成

### quant worker

- 必须存在监督脚本进程
- 实际 `python.exe` 子进程允许空闲时退出，下一轮由监督脚本再次拉起

## 总控脚本验收

`scripts/restart-all-services.ps1` 在完成启动后，会自动调用：

- `scripts/check-all-services-runtime.ps1`

并校验：

- `ruoyi-admin` 首页可达且登录成功
- `ruoyi-ui` 首页可达
- `quant worker` 监督进程存在

任何一项失败都直接报错退出，避免出现“脚本显示成功，但环境其实半坏”的假阳性。

## 用户体验

最常用命令只有两个：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/restart-all-services.ps1
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-all-services-runtime.ps1
```

## 风险控制

- 找不到旧进程时只给 warning，不视为失败。
- 某层启动失败时立即停止后续启动，避免半套环境假可用。
- 进程识别全部基于仓库路径与命令行关键字，避免误伤别的开发任务。
