# 2026-05-06 RuoYi 主链瘦身记录

## 目标

把当前工程从“若依全家桶 + 历史双后端并存”的状态，收口到当前量化项目真正使用的主链：

1. `ruoyi-admin`
2. `ruoyi-ui`
3. `ruoyi-quartz`
4. `python/src/quant_road`

## 本轮已完成

### 1. 主链与历史兼容入口分离

- `admin/` 默认端口调整为 `18080`
- `admin/` 启动时增加 legacy 警告
- README、集成文档、runbook 全部明确 `ruoyi-admin` 才是默认入口
- 新增：
  - `admin/LEGACY.md`
  - `ruoyi-generator/LEGACY.md`

### 2. 主链构建减负

- `ruoyi-admin` 已移除 `ruoyi-generator` 运行依赖
- 根 `pom.xml` 已移除 `ruoyi-generator` 聚合模块
- `ruoyi-generator/` 当前仅保留源码参考，不再属于默认产品能力

### 3. 菜单与页面暴露面收口

- 后端 `getRouters` 默认过滤 `/monitor` 与 `/tool` 菜单
- 新增主链菜单过滤测试 `SysLoginControllerTest`
- 前端动态路由已移除：
  - `monitor/job-log`
  - `tool/gen-edit`
- Playwright smoke 已改为只覆盖当前主链页面

### 4. 前端遗留页面清理

已删除代码生成前端残留：

- `ruoyi-ui/src/api/tool/gen.js`
- `ruoyi-ui/src/views/tool/gen/*`

已删除监控前端残留：

- `ruoyi-ui/src/api/monitor/{cache,job,jobLog,online,server}.js`
- `ruoyi-ui/src/views/monitor/cache/*`
- `ruoyi-ui/src/views/monitor/job/*`
- `ruoyi-ui/src/views/monitor/online/*`
- `ruoyi-ui/src/views/monitor/server/*`
- `ruoyi-ui/src/views/monitor/druid/index.vue`

当前刻意保留：

- `ruoyi-ui/src/views/monitor/operlog/*`
- `ruoyi-ui/src/views/monitor/logininfor/*`

保留原因：这两组更接近“系统日志”能力，可能仍被当前系统主线承接，不在本轮一起删除。

## 验证

本轮已执行并通过：

- `mvn -pl ruoyi-admin -Dtest=SysLoginControllerTest test`
- `mvn -pl ruoyi-admin -am -DskipTests compile`
- `mvn -f admin/pom.xml -DskipTests compile`
- `npm run build:prod`

说明：

- `mvn -pl ruoyi-admin -am -Dtest=SysLoginControllerTest test` 会因为聚合链上某些模块无测试而触发 surefire 的 `No tests were executed`，这不是本轮改动引入的问题。
- 因此 Java 侧测试验证以模块级测试命令为准。

## 当前状态判断

当前工程已从“框架能力大于业务能力”的状态，收口到“主链清晰、遗留明确、默认页面聚焦量化业务”的状态。

仍然存在但已被标记/隔离的历史负担：

1. `admin/`
2. `ruoyi-generator/`
3. `monitor/operlog`
4. `monitor/logininfor`

## 后续建议

下一阶段建议不要继续大面积删除，而是按下面顺序推进：

1. 合并并稳定当前瘦身结果，先让团队只走一条主链。
2. 观察一轮真实开发与回归，确认没有人再依赖已隐藏的 `monitor/*`、`tool/*` 能力。
3. 若确认无人使用，再决定是否继续清理：
   - `monitor/operlog`
   - `monitor/logininfor`
   - 更深层的后端 monitor controller
