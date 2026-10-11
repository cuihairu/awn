# ADR-0008：构建工具采用 Maven

- 状态：已接受
- 日期：2026-10-11

## 背景

后端为 16 个 Maven 模块（14 业务模块 + 2 可运行应用），继承 `spring-boot-starter-parent:3.5.6`，通过 BOM 管理 Spring Modulith 版本。备选方案是 Gradle（Kotlin DSL），其增量构建与并行能力更强。

## 决策

保持 **Maven** 作为唯一后端构建工具，与 ADR-0001 的“单个 Maven 多模块工程”保持一致。

## 后果

- 正面：Spring Boot / Spring Modulith 官方示例与生态默认；依赖版本由 BOM 统一管理；CI 命令简单（`mvn -B -ntp verify`）。
- 负面：全量构建速度不及 Gradle 的增量构建。
- 约束：**禁止同时引入 Gradle 构建脚本**，避免出现双构建体系与依赖版本分叉；如后续因构建速度实际成为瓶颈，需另立 ADR 记录整体迁移。
