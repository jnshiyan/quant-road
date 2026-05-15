# Quant Road

Quant Road 是基于 `RuoYi-Vue + Python` 的量化运营主链工程。当前仓库只保留并维护这一条默认链路：

- Java 后端：`ruoyi-admin`
- Vue 前端：`ruoyi-ui`
- Java 基础模块：`ruoyi-framework`、`ruoyi-system`、`ruoyi-common`、`ruoyi-quartz`
- Python 量化层：`python/src/quant_road`

旧的独立后台 `admin/` 和旧代码生成模块 `ruoyi-generator/` 已从仓库移除，不再作为产品能力或默认开发入口。

## 仓库结构

```text
ruoyi-admin/            Java 后端入口
ruoyi-ui/               前端控制台
ruoyi-quartz/           定时任务模块
ruoyi-framework/        RuoYi 框架核心
ruoyi-system/           系统能力模块
ruoyi-common/           通用基础模块
python/src/quant_road/  Python 量化服务
scripts/                启停、回归、初始化脚本
sql/                    PostgreSQL 初始化脚本
docs/                   运行文档、设计规约、交付记录
```

## 一次性初始化

### 1. 初始化数据库

推荐直接执行主链初始化脚本：

```bash
python scripts/init-ruoyi-fresh.py --host localhost --port 5432 --user postgres --password 123456 --database db-quant
```

如果只需要刷新量化菜单和任务：

```bash
python scripts/apply-quant-bootstrap.py --host localhost --port 5432 --user postgres --password 123456 --database db-quant
```

如需手工执行 SQL，顺序如下：

```bash
psql -h localhost -U postgres -d db-quant -f sql/ruoyi_pg_init.sql
psql -h localhost -U postgres -d db-quant -f sql/init.sql
psql -h localhost -U postgres -d db-quant -f sql/ruoyi_quant_menu.sql
psql -h localhost -U postgres -d db-quant -f sql/ruoyi_quant_jobs.sql
```

初始化完成后，重新登录系统，默认不展示 `首页`、`系统监控`、`系统工具`、`若依官网`。

### 2. 初始化 Python 环境

```bash
cd python
pip install -r requirements.txt
pip install -e .
```

并执行一次幂等建表：

```bash
cd python
PYTHONPATH=src python -m quant_road init-db
```

## 本地启动

### 推荐方式：一键重启整套服务

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/restart-all-services.ps1
```

默认入口：

- UI：`http://localhost:8081`
- API：`http://localhost:8080`

### 分模块启动

后端：

```bash
mvn -pl ruoyi-admin -am spring-boot:run
```

前端：

```bash
cd ruoyi-ui
npm install
npm run dev
```

Python CLI 示例：

```bash
cd python
PYTHONPATH=src python -m quant_road full-daily --start-date 20230101 --strategy-id 1 --notify
```

## 常用验证命令

Java 测试：

```bash
mvn -pl ruoyi-admin -am test
```

前端生产构建：

```bash
cd ruoyi-ui
npm run build:prod
```

Python 单测：

```bash
cd python
PYTHONPATH=src python -m unittest discover -s tests -p "test_*.py"
```

量化烟测：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-quant.ps1
```

全量回归：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/full-regression.ps1
```

## 推送主链

在 `main` 分支完成改动后，可用下面的脚本完成主链检查、回归和推送：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-main-for-push.ps1 -RunVerification -Push
```

该脚本会检查：

- 根工作区当前是否为 `main`
- 工作区是否干净
- 是否仍有本地分支未合入 `main`
- 全量回归是否通过

## 量化页面开发约束

`ruoyi-ui/src/views/quant` 下所有页面必须遵循以下文档：

- `docs/quant/page-design-regulations.md`
- `docs/quant/page-delivery-regulations.md`
- `docs/quant/page-review-checklist.md`

这些页面按“运营控制台”设计，不按 PPT/展示页设计。任何页面改动都不能只给静态说明，必须以真实登录后的页面结果为准。

## 补充文档

- 运行与初始化说明：`docs/runbook.md`
- Java 主链接入说明：`docs/java-integration.md`
- 页面与产品规约：`docs/quant-system/`
- 交付记录与回顾：`docs/reports/`
