# 贡献指南

感谢你愿意参与。这个项目最需要的贡献其实不是代码，而是**意图设计**（见下）。

## 项目边界（先读这个，避免做无用功）

```
intent-protocol / intent-sdk-core / intent-sdk-host / intent-sdk-gateway
    ↑ 核心。**不允许**依赖 Spring，也不允许依赖任何推理引擎（pi-ai / pi-agent）。
      这是"可脱离 Spring 独立使用""可替换推理引擎"两个能力的基础，
      CI 里有守卫任务（guard-core-purity）会拦住违规 PR。
intent-sdk-pi
    ↑ 唯一允许依赖 pi-ai / pi-agent 的模块。换引擎只换这里。
intent-spring-boot-starter
    ↑ Spring 装配层。宿主引入即用。
```

## 最能被接受的贡献：意图设计

一个意图 = **宿主工具（Java）+ 意图规范（YAML）+ 可选的事实规则（YAML）**。
如果你在某个业务系统里跑通了一个好意图，欢迎以 `examples/` 的形式提交：

```
examples/<行业或系统>/
├── README.md          这个意图解决什么场景、效果如何（附实测耗时/token）
├── intent/*.yaml      意图规范
├── rules/*.yaml       事实规则（可选）
└── tools/*.java       宿主工具（可选，脱敏后）
```

> 提交示例请务必**脱敏**：去掉真实客户名、内部域名、密钥、绝对路径。

## 代码贡献

### 提交前自检

```bash
mvn -B verify                       # 全部单测
mvn -B -pl intent-protocol,intent-sdk-core,intent-sdk-host -am verify   # 仅核心模块（无需 pi）
```

### 约定

- **Java 21**；4 空格缩进；UTF-8；文件末尾留一个换行。
- 类与公开方法**写 Javadoc**：说清"为什么这么设计"，而不是复述方法名。
- 新增/修改行为时**同时补测试**。规范校验、规则引擎、目录装配这三块是重点。
- **不要引入新的重量级依赖**。核心模块的依赖清单应当长期保持只有 Jackson。
- 提交信息用中文或英文都行，但要说清"改了什么 + 为什么"。

### 关于 YAML 规范的一处硬约束

意图规范与事实规则是**启动强校验**的：不合规会让服务起不来。这是刻意的设计（静默失效最难排查），
所以提交 YAML 前请本地起一次服务，或在 `intent-for-ruoyi-vue-plus` 仓里用离线校验器跑一遍。

## 报告问题

请附上：

1. 宿主框架与版本（如 RuoYi-Vue-Plus 6.0.0 / Spring Boot 4.1.0）
2. 相关意图的 YAML（脱敏）
3. `GET /intent/trace/{traceId}` 的执行留痕（**最有价值**，能看到工具的真实入参与返回）
4. 期望行为 vs 实际行为

## 许可证

贡献即表示你同意以 [Apache License 2.0](./LICENSE) 授权你的贡献。
