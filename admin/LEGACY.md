# Legacy Admin Module

`admin/` 是历史兼容的独立 Spring Boot 后端，不属于当前推荐主链。

当前状态：

- 默认主链后端：`ruoyi-admin`
- 当前模块默认端口：`18080`
- 当前模块用途：历史接口/数据对照、兼容性排查

使用约定：

1. 不要把 `admin/` 当作日常开发启动入口。
2. 新功能默认不要继续加在这里。
3. 回归、联调、交付默认只验证 `ruoyi-admin + ruoyi-ui + python` 主链。

如非明确需要，请从仓库根目录启动：

```bash
mvn -pl ruoyi-admin -am spring-boot:run
```
