# libs — vendored pi artifacts

**English** · [中文](#中文)

These files are **not part of intent-sdk**. They are the upstream build artifacts of
[pi-java](https://gitee.com/harvey_danny/pi-agent-java), redistributed here so that a fresh clone
builds without depending on any public repository having pi published.

| Artifact | Version | Size |
|---|---|---|
| `dev.pi:pi-java-parent` (pom only) | 0.1.0-SNAPSHOT | 3 KB |
| `dev.pi:pi-ai` (jar + pom) | 0.1.0-SNAPSHOT | 390 KB |
| `dev.pi:pi-agent` (jar + pom) | 0.1.0-SNAPSHOT | 311 KB |
| `dev.pi:pi-telemetry` (jar + pom) | 0.1.0-SNAPSHOT | 33 KB |

## Why they are here

`intent-sdk-pi` — the module implementing the executors (`builtin-agent` / `skill` / `flow`) — depends
on `dev.pi:pi-ai` and `dev.pi:pi-agent`. Those coordinates are **not published to Maven Central or any
other public repository**, so without these files `mvn install` fails immediately after a clone with
`Could not find artifact dev.pi:pi-ai:jar:0.1.0-SNAPSHOT`.

Keeping the required artifacts in the repository makes the build reproducible everywhere, at the cost
of ~740 KB of binaries. Everything else in the SDK depends on Maven Central only.

## How they are consumed

The `vendor-pi` profile in [`../pom.xml`](../pom.xml) — active **only in the repository root**, where
`libs/dev/pi` exists — installs these artifacts into your local Maven repository during the
`initialize` phase, before any module that needs them is built. Nothing is written outside your local
Maven repository, and the profile does not run in downstream builds.

The directory layout mirrors a Maven repository, so the same tree could also be served as a
`file://` repository if you prefer that over installing.

## Upgrading pi

1. Build [pi-java](https://gitee.com/harvey_danny/pi-agent-java): `mvn install -DskipTests`.
2. Replace the jar/pom pairs here, keeping the `<groupId>/<artifactId>/<version>/` layout and the
   file names version-consistent.
3. If pi's dependency set or version changes, update the `install-file` executions in `../pom.xml`
   and the `dev.pi:*` entries in its `<dependencyManagement>`.

## License

These artifacts are the work of the pi-java project, distributed under the **MIT License**
(Copyright (c) 2026 AWCP). The full text ships alongside them as [`dev/pi/LICENSE`](./dev/pi/LICENSE).

intent-sdk itself is Apache-2.0. The two licenses are compatible; this directory exists purely to
make the build reproducible, and the pi sources remain the canonical upstream.

---

<a name="中文"></a>
# libs — 随仓库分发的 pi 制品（中文）

这里的文件**不属于 intent-sdk**，而是 [pi-java](https://gitee.com/harvey_danny/pi-agent-java) 的
构建产物，随仓库分发，目的是让任何人 clone 之后都能直接构建，而不依赖某个公共仓里恰好有 pi。

## 为什么要放进来

`intent-sdk-pi`（执行器模块：`builtin-agent` / `skill` / `flow`）依赖 `dev.pi:pi-ai` 与
`dev.pi:pi-agent`，而这两个坐标**没有发布到 Maven Central 或任何其它公共仓**。
不放进来的话，clone 之后 `mvn install` 会立刻失败：

```
Could not find artifact dev.pi:pi-ai:jar:0.1.0-SNAPSHOT
```

代价是仓库里多约 740 KB 二进制，换来的是"拿到代码即可构建"。SDK 其余部分只依赖中央仓。

## 怎么被使用

[`../pom.xml`](../pom.xml) 里的 `vendor-pi` profile —— **只在根模块激活**（只有根模块存在
`libs/dev/pi`）—— 会在 `initialize` 阶段把这些制品安装进你的本地 Maven 仓，
早于任何需要它们的模块开始构建。除了本地 Maven 仓，不写任何其它位置；
该 profile 也不会在下游构建里触发。

目录布局与 Maven 仓一致，因此如果你更倾向用 `file://` 仓而不是安装，也可以直接把本目录当仓库用。

## 升级 pi

1. 构建 [pi-java](https://gitee.com/harvey_danny/pi-agent-java)：`mvn install -DskipTests`
2. 替换此处的 jar/pom，保持 `<groupId>/<artifactId>/<version>/` 布局与文件名版本一致
3. 若 pi 的依赖集合或版本变了，同步修改 `../pom.xml` 里的 `install-file` 执行与
   `<dependencyManagement>` 中的 `dev.pi:*` 条目

## 许可证

这些制品是 pi-java 项目的成果，以 **MIT 许可证**分发（Copyright (c) 2026 AWCP），
完整文本随附于 [`dev/pi/LICENSE`](./dev/pi/LICENSE)。intent-sdk 本身是 Apache-2.0，
两者兼容；本目录的存在只为让构建可复现，pi 的源码工程始终是权威上游。
