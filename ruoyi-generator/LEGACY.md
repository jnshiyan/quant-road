# Legacy Generator Module

`ruoyi-generator/` 是若依自带代码生成模块源码，当前仅保留作历史参考。

当前状态：

- 已从根 `pom.xml` 聚合模块中移除
- 已从 `ruoyi-admin` 运行依赖中移除
- 当前量化主链不再暴露 `tool/gen` 前端入口

使用约定：

1. 不要把它视为当前产品功能的一部分。
2. 不要在主链开发中新增对 `ruoyi-generator` 的运行时依赖。
3. 如果未来需要恢复代码生成能力，应作为单独决策重新接入，而不是隐式带回主链。
