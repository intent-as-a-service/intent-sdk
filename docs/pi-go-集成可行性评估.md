# pi-go 集成到 Java 底层：可行性评估

> 问题：`pi-go` 能不能集成到 Java 的底层里？怎么集成？会出什么问题？工作量多少？
>
> 评估对象：
> - `pi-agent/pi-go`（346 个 `.go`，64010 行，Go 1.25，模块名 `pi-go`）
> - `pi-agent/pi-go-multitenant`（364 个 `.go`，65638 行，多租户分支，含 `cmd/pi-server`）
> - 目标宿主：`next-agent` 意图执行 + 业务系统（Spring Boot / JVM）

---

## 0. 结论（先给答案）

**"能不能"分两层，答案不一样：**

1. **技术上能不能嵌进 JVM 进程**：能，但**当前代码形态做不到**，而且代价很高。
   pi-go `go.mod` 只有两个直接依赖 —— `golang.org/x/sys` 和 `modernc.org/sqlite`（纯 Go SQLite），
   **不含 CGO**，所以产出 `-buildmode=c-shared` 的 `.dll/.so` 在技术上是通的。
   但**所有入口都是 `package main`**（`cmd/pi`、`cmd/pi-server`），**没有任何库形态入口**，
   要嵌 JVM 必须先做一次结构改造（把 main 拆成可 import 的 library + `export` 函数）。
2. **该不该嵌**：**不建议**。核心理由不是语言，而是**嵌了也拿不到你想要的东西**（见 §2）。
   pi-go 与 pi-java 是**同一套 TS 原版的两个平行移植**，能力集合基本重合；
   而 Java 侧已经有一个**零 CGO、零子进程、同 JVM 内存模型**的选择（pi-java）。
   引入第二个语言运行时进核心链路，换来的是**双 GC、双信号、双线程池、跨语言栈**，不划算。

**推荐路径**：如果确实要用 pi-go，走 **§3.1 独立 HTTP 服务**（`pi-go-multitenant` 已经是这个形态），
而不是内嵌。这样 1~2 人日可跑通，且不污染业务 JVM。

---

## 1. 决定性的四个事实（先看这些，再看方案）

| # | 事实 | 证据 | 为什么关键 |
|---|---|---|---|
| F1 | **pi-go 无 CGO** | `pi-go/go.mod:5-8` 直接依赖只有 `golang.org/x/sys` + `modernc.org/sqlite`（pure-Go）。`PORTING.md:105` 提到"`-race` 需 cgo（本机无 gcc），未跑"——反证常规构建无 CGO | 这是"能不能嵌"的**最大有利条件**：可以静态产出 `c-shared` 而无须 C 工具链 |
| F2 | **无库形态入口，全是 `package main`** | `cmd/pi/main.go`、`pi-go-multitenant/cmd/pi-server/main.go`（62 行，`package main`）。`PORTING.md:7` 明确"单一 Go 模块 `pi-go`，每个 npm 包对应一个子包"——是 CLI 产品，不是 SDK | 这是"当前形态做不到"的**真实原因**。Go 的 `-buildmode=c-shared` 要求 `package main` + `//export`，而这两个 main 只做 flag 解析后调 `multitenant.NewManager` |
| F3 | **pi-go 的 RPC 只有 Unix socket，没有 TCP** | 服务端只提供 `server/unix.go:11` `net.Listen("unix", path)` 与 `server/listener.go:25` `NewUnixSocketListener`；客户端只有 `client/unix.go:14` `NewUnixSocketTransportFactory`（`net.Dial("unix", path)`）。全仓 grep `net.Listen("tcp"` 只命中 `webui/server.go:76`、`traceviewer/viewer.go:192`、`ai/oauth_callback.go:54`、`ai/openrouter_oauth.go:41`——**都与 agent RPC 无关** | **Windows 部署的直接障碍**。Windows 10 1803+ 与 Go 都支持 AF_UNIX，但路径语义、权限模型、清理方式与 Linux 不同，且容器/宿主机挂载、杀软行为都更脆弱。要用必须先加一个 TCP listener |
| F4 | **RPC 协议本身是传输无关的** | `client/client.go:41` `ByteTransportFactory`、`server/unix.go:36` `NewNetConnByteConnection(conn net.Conn)`；帧格式是 `protocol/framing.go` 的 **4 字节大端长度前缀 + CBOR**（`EncodeFrame` / `FrameDecoder`，最大帧 16MB） | **这是最有利的架构条件**：加 TCP 只需写一个 `net.Listen("tcp")` 的 listener + 一个 `net.Dial("tcp")` 的 factory，**协议、编解码、握手全不用动** |

补充事实：
- **`pi-go-multitenant` 已有完整 HTTP/SSE 服务端**（`multitenant/server.go` 900+ 行），
  含管理员/租户 CRUD、API Key 哈希存储、限流、并发 turn 槽、SSE 会话事件流、
  租户路径守卫 `GuardedEnv`、配额计量 `eventHooks`、内置身份工具 `identity_tool.go`。
  即：**"给多个 Java 业务系统共用"这件事已经做完了**，不必自己造。
- **pi.exe 约 19.8MB**（本机实测）。内嵌进 JVM 后这是**常驻进程内存之外的额外 native 内存**。
- **pi-go 的 subagents 扩展也是子进程模型**（`coding-agent/extensions/subagents/runner.go`），
  与 pi-java 的 `pi-subagents` 插件同形 → 说明"独立进程"在这套设计里本来就是被接受的形态。

---

## 2. 为什么"嵌了也拿不到想要的"（最关键的一条）

内嵌（JNI/CGO）的唯一强动机是：**让 LLM 在同一个进程里直接调用 Java 的业务工具**，
从而"权限与事务随宿主调用栈自然生效"（`next-agent` 的核心设计，见 `IntentTool.java:12-14`）。

但把 Go 放进 JVM，**这条动机并不成立**：

1. Go 侧的工具仍然要**回调进 JVM** 才能碰到 Java 的业务 API（JNI 反向调用 / 共享内存 / 本地 socket）。
   于是你要付出：
   - **JNI 反向调用需要 JNIEnv**，而 Go 的 goroutine 会**换线程**，JNIEnv 与线程绑定 →
     必须用 `JavaVM::AttachCurrentThread` + `DetachCurrentThread`，并处理 Go runtime 线程生命周期；
   - 或者绕开 JNI，在进程内再开一条本地 socket 回调 Java —— 那**等于没内嵌**，还是 IPC。
2. 而 **Java 侧已经有真正的同进程方案**：`pi-java` 是 pi 原版的 1:1 Java 移植，
   与 next-agent 同一个 JVM、同一个内存模型、同一个 GC、同一个线程池，
   `IntentTool` 是纯接口、`PiToolAdapter` 是同进程方法调用，**没有序列化边界**。
3. 因此内嵌 pi-go 得到的实际净收益 ≈ 0，付出的确定性代价却包括：
   双运行时、跨语言栈、native 内存不受 JVM 管、构建产物分平台。

**一句话**：`pi-go` 与 `pi-java` 是**同源平行移植**，能力集合基本重合（见 §5 对照）；
在已经有 `pi-java` 的前提下，把 `pi-go` 嵌进 JVM 是**用第二个语言运行时换一个已经有的东西**。

---

## 3. 四种集成方案（按推荐度排序）

### 3.1 【推荐】独立 HTTP 服务 —— 复用 `pi-go-multitenant`

**形态**：`pi-server` 作为独立守护进程运行，Java 通过 HTTP/SSE 调用；每个业务系统 = 一个 tenant。

```
Java 业务系统 (Spring Boot)
   │  HTTP/JSON + SSE
   ▼
pi-server (pi-go-multitenant, 独立进程/容器)
   ├─ /admin/*    平台管理（租户 CRUD、配额）
   ├─ /tenant/*   会话创建、prompt、steer、setModel、SSE 事件流
   ├─ GuardedEnv  租户路径守卫（逻辑隔离）
   ├─ eventHooks  用量计量 + shell 审计
   └─ SQLite      统一会话库（会话/分支/用量/审计）
```

**Java 侧要做的事**：一个 HTTP 客户端 + SSE 消费 + 会话 ID 映射。**没有 JNI、没有 native、没有双运行时。**

**优点**
- 零 Go 侧改造即可跑（`cmd/pi-server/main.go` 已经能用）
- 多租户、限流、审计、配额**已经实现**，直接复用
- 故障隔离：pi-go 崩溃/泄漏不影响业务 JVM；可独立重启、独立限流、独立升级
- 前面提到的 12 个既有缺陷（G1~G12）与这个方案无关，不叠加风险

**缺点**
- 多一跳网络（本地回环，通常 <1ms，可忽略）
- 需要运维一个额外进程（容器化后不算负担）
- 工具回调宿主业务 API 仍然要跨进程 → **这是本方案唯一的真痛点**，见 §4.1

**工作量**：**1~2 人日**（Java HTTP 客户端 + 契约对齐 + 冒烟）。若要多租户，配置 `tenants.json` 另加 0.5 人日。

---

### 3.2 CBOR-RPC over TCP —— 想要低延迟、复用现成协议

**形态**：给 pi-go 补一个 TCP transport，Java 实现 `protocol` 的 CBOR 帧编解码，走二进制 RPC。

**Go 侧改动**（`F4` 使它很小）：
```go
// server 侧，仿 server/unix.go
func ServeTCP(server *PiServer, listener net.Listener) error { /* 同 ServeUnix，仅来源不同 */ }
// client 侧，仿 client/unix.go
func NewTCPSocketTransportFactory(addr string) ByteTransportFactory { /* net.Dial("tcp", addr) */ }
```
协议、`EncodeFrame`/`FrameDecoder`、握手、`CommandResult` 全部复用。

**Java 侧要做的事**：实现 CBOR 编解码（或引入 `jackson-dataformat-cbor`）+ 帧分割 + 握手状态机
+ `ServerSnapshot`/`SessionSnapshot`/`ServerEvent` 的 Java 模型映射。

**优点**
- 比 HTTP/JSON 更快更省（二进制、无 HTTP 头开销），事件流更自然
- 可**双向**：Java 主动查询 + Go 主动推事件，适合流式阶段反馈（对应 `功能清单.md:42` 的 M2 流式）
- 协议已经有 `protocol/schemas.go` + conformance 测试，契约相对稳固

**缺点**
- Java 侧要从零实现一套协议客户端（pi-java 的 `pi-protocol` 有 CBOR 编解码，**可参考甚至复用其设计**，
  但两个 `pi-protocol` 是两个独立移植，API 不通用）
- 仍须先解决 Windows 的 Unix socket 问题（本方案就是解法）
- 版本耦合：帧格式/消息 schema 一旦演进，两侧必须同步

**工作量**：**Go 侧 0.5 人日**（TCP listener + factory + 测试）；
**Java 侧 3~5 人日**（CBOR 编解码 + 帧 + 模型映射 + 集成测试）。

---

### 3.3 CGO 共享库内嵌（JNI）—— 本文要重点劝退的方案

**形态**：`go build -buildmode=c-shared -o libpi.so` + `//export` 函数，Java 用 JNI/JNA 加载调用。

**前置改造（当前形态不满足 `F2`）**：
```
pi-go/
├── cmd/pi/main.go            ← 现状：package main，只有 CLI 入口
├── cmd/pi-server/main.go     ← 现状：package main，62 行
└── 需要新增：
    ├── pisdk/                ← 可 import 的库包（把 main 的逻辑搬进来）
    │   ├── session.go        ← CreateSession / Prompt / Steer / Abort / Events
    │   └── ...
    └── cshared/main.go       ← package main + //export PiCreate/PiPrompt/PiFree...
                                  （c-shared 强制要求 package main）
```
注意：**`c-shared` 要求导出侧是 `package main`**，所以 `cmd/pi-server/main.go` 里的
flag 解析逻辑要拆成"库函数 + 薄 main"，这是纯结构改造，**不改业务逻辑，但是必须做**。

**必须处理的七个问题**（按危险程度排序）：

| # | 问题 | 具体后果 | 缓解手段 |
|---|---|---|---|
| P1 | **Go panic 穿透 CGO 边界 = 整个 JVM 进程终止** | 一个空指针/越界就让 Spring Boot 死掉；**Java 的 `try/catch` 拦不住**，`OutOfMemoryError` 那套兜底全失效 | 每个 `//export` 函数入口 `defer recover()`；但**无法覆盖 runtime 级致命错误**（栈溢出、map 并发写、`runtime.throw`） |
| P2 | **GC 冲突** | JVM 堆 + Go 堆 + Go 的 native 内存并存；容器里内存超限时**两个 GC 互相抢**，OOMKilled 后难以归因；Go 1.25 的 `GOMEMLIMIT` 与 JVM `-Xmx` 必须手工配平 | 设 `GOMEMLIMIT` + `GOMAXPROCS`（容器 CPU limit 下 Go 会自动读 cgroup，但 JVM 线程池也要设），并做联合压测 |
| P3 | **线程模型冲突** | Go runtime 自建 M:N 调度（大量 OS 线程，preemption 靠信号）；JVM 有自己的线程池与 `jstack`。Go 线程**不出现在 Java 线程转储里**，出问题看不见 | 无法根治；只能靠 Go 侧自建 pprof 出口 |
| P4 | **ClassLoader 泄漏** | 共享库通过 JNI 加载后，webapp redeploy 时若 native 侧还持有 JVM 引用，ClassLoader 无法回收 → Metaspace/native 双双泄漏，**最终 `SIGSEGV` 或无法重启** | 每次 redeploy 前 `Dispose` + `FreeLibrary`/`dlclose`；**但 Go runtime 一旦启动基本无法安全卸载** → 实际上等于**禁止热部署** |
| P5 | **信号处理冲突** | Go runtime 需要 SIGSEGV/SIGBUS/SIGPROF 用于抢占与 GC；JVM 也装 SIGSEGV 做隐式空指针检测、SIGQUIT 做线程转储。JNI 下可用 `JNI_OnLoad` 的 `AllowGetSignal` 协调，但**边界条件多** | 参考 `sigqueue`/`os/signal` 的最佳实践；**必须在目标 OS 上实测**，不能推演 |
| P6 | **多平台构建** | 一份 `.dll`(Windows) / `.so`(Linux) / `.dylib`(macOS)，各带 x86_64 + aarch64。CI 要出 6 个产物，jar 从 ~2MB 涨到 ~120MB（19.8MB × 6，压缩后小些但可观） | 拆成"平台专属 jar"（classifier），按需分发 |
| P7 | **可观测性/可调试性下降** | 混合栈（Java → JNI → Go → goroutine → 回调 Java）难以阅读；APM（SkyWalking/Arthas）看不到 Go 内部；本地 IDE 无法单步跨语言；**生产排障成本显著上升** | 只能在 Go 侧补 pprof + 结构化日志，并在两侧打同一个 trace id |

**工作量**：**2~4 周**（库化改造 3~5 人日 + `c-shared` 导出与内存所有权设计 3~5 人日 +
JNI/JNA 绑定 2~3 人日 + **P1~P7 的验证与加固 5~10 人日**）。
**且这 2~4 周换来的收益，在"Java 工具回调"这个核心诉求上是 0**（见 §2）。

---

### 3.4 GraalVM Native Image / WASM —— 不建议

- **GraalVM Native Image**：主要面向 *JVM 语言编译成 native*，不是"在 JVM 里跑 Go"。
  要用 Truffle 系（Sulong for LLVM bitcode）跑 Go —— Go runtime 重度依赖抢占式信号与栈复制，
  Sulong 对 Go 的支持**长期不完整**，且 Sulong 自身生态已边缘化。**风险极高、收益不明。**
- **WASM（TinyGo / GOOS=js）**：Go 官方 wasm 目标**不支持 goroutine 的真实并行**（单线程），
  也**不支持 net/sqlite 这类能力**；TinyGo 对 `net/http`、`database/sql`、反射的支持都不完整。
  pi-go 重度依赖 SSE 流、SQLite、并发 → **不可行**。

**结论：这两条路不要走。**

---

## 4. 会出现的问题（分级）

### 4.1 无论哪种方案都会遇到的问题：**Java 工具回调**

这是**唯一的结构性难题**，且它**与集成方式无关**：

- next-agent 的核心设计是"工具直调宿主内部 SDK/API，权限与事务随调用栈自然生效"（`IntentTool.java:12-14`）。
- 若推理内核在 Go 侧，**工具实现在 Java 侧**，那么每次工具调用都要跨语言边界：
  - **方案 3.1（HTTP）**：Go → HTTP → Java。需要 Java 暴露"内部工具端点"，
    但**登录态、租户、事务上下文怎么带过去？** next-agent 现在靠 `toolDecorator` 在
    同一调用栈里恢复宿主上下文（`ExecutionRequest.toolDecorator`），跨进程后这条链断了，
    退化成"内部 API + 服务间鉴权"——**正是 next-agent 当初想避免的东西**（`架构设计方案.md:14-15`）。
  - **方案 3.3（JNI）**：Go → JNI → Java。要处理 `AttachCurrentThread`、局部引用泄漏、
    异常挂起（`ExceptionCheck`/`ExceptionClear`）、goroutine 换线程。
- **两种都要自己造一套上下文传播机制**（token 透传 / 线程局部桥 / 显式 context 对象），
  而 `pi-java` 方案下这**一行都不用写**。

> 结论：如果意图执行必须调 Java 业务工具，**推理内核放 Java 侧是架构上更省的选择**。
> pi-go 更适合放在**不依赖宿主上下文**的位置（如通用编码任务、文档处理、批量分析）。

### 4.2 仅 3.3（内嵌）才有的问题

见上表 P1~P7。补充两条实操性的：

- **调试体验倒退**：`jstack`/`jmap`/`Arthas` 对 Go 侧无效；Go 的 `pprof` 需要额外端口或信号触发。
  团队要同时具备两套排障能力。
- **不可热部署**：P4 决定了共享库加载后基本不能安全卸载 → 业务系统失去"改 jar 重启即可"的便利，
  或必须接受 native 内存缓慢泄漏。

### 4.3 仅 3.1/3.2（跨进程）才有的问题

- **工具回调跨进程**（见 4.1）——最痛
- **协议版本耦合**：pi-go 的 schema 演进要与 Java 客户端同步发版
- **会话状态双写**：pi-go 有 SQLite 会话库，next-agent 有 `TraceStore`（JSONL/内存/DB）。
  两套留痕需要决定谁是权威，否则审计对不上
- **部署复杂度**：多一个进程/容器要管（健康检查、日志聚合、优雅停机、升级顺序）

### 4.4 安全边界问题（**必须单独提醒**）

`pi-go-multitenant` 的 `GuardedEnv` 注释自己写得很清楚（`multitenant/guard.go:11-13`）：

> "GuardedEnv wraps an ExecutionEnv and keeps every path-oriented operation inside the tenant root.
> **This is logical isolation: it prevents accidental path traversal from the built-in file tools.
> It is not a process sandbox.**"

含义：
- pi-go 带 **`bash` 工具**（`agent/harness/bash.go`）。放在**业务系统**里，等于给业务 JVM
  提供了一个可执行任意 shell 的通道（哪怕是独立进程，也在同一台机器/同一网络命名空间）。
- **独立进程（3.1/3.2）反而是安全优势**：可以单独做只读挂载、无网络、非 root、
  独立 seccomp/AppArmor 或容器沙箱。
- **内嵌（3.3）丧失这个优势**：shell 由业务 JVM 直接发起，与业务进程同权限、同网络、
  同文件系统视图，出事就是整站。
- 而 next-agent 的意图场景**根本不需要 bash/文件工具**——只需要宿主业务工具。
  这进一步说明：**为了不用的能力承担沙箱风险，不值得**。

---

## 5. pi-go / pi-java / pi(TS) 三方对照（帮你判断该用哪个）

| 维度 | pi (TS 原版) | pi-java | pi-go | pi-go-multitenant |
|---|---|---|---|---|
| 规模 | ~23 万行 TS | 10 模块 / ~340 文件 | 346 `.go` / 64010 行 | 364 `.go` / 65638 行 |
| 运行形态 | Node/Bun CLI | 纯 JVM 库 + CLI | 静态二进制（19.8MB） | HTTP/SSE 守护进程 |
| 与 Java 的边界 | 子进程 / RPC | **同进程方法调用** | 子进程 / RPC | **HTTP** |
| CGO | n/a | n/a | **无**（`modernc.org/sqlite`） | **无** |
| 多租户 | 无 | 无 | 无 | **有**（限流/配额/审计/路径守卫） |
| 会话存储 | 文件/SQLite | JSONL / SQLite | JSONL / SQLite | 统一 SQLite |
| 工具回调宿主 Java | 跨进程 | **同进程** | 跨进程 | HTTP |
| RPC 传输 | — | — | **仅 Unix socket** | HTTP/SSE |
| 移植完整度 | 基准 | PORTING-PLAN 记 P0~P5 基本完成 | PORTING.md 记"coding-agent 仍是核心子集（88 个 .go），大量 modes/utils 未逐文件移植" | 同 pi-go + 多租户层 |

> 注：pi-go 的 `PORTING.md` 自己列了未完成项（`ai` 的部分 provider/API、`coding-agent` 的
> `modes/interactive/*`、`core/export-html` 完整模板、`rpc-entry/main/migrations` 入口等）。
> 若选 pi-go，**要先确认你要的能力在它的"已完成"集合里**。

---

## 6. 工作量预估汇总

| 方案 | Go 侧 | Java 侧 | 联调/加固 | 合计 | 风险 |
|---|---|---|---|---|---|
| **3.1 独立 HTTP 服务** | **0**（已就绪） | 1~2 人日 | 0.5 人日 | **~2 人日** | 低 |
| **3.2 CBOR-RPC over TCP** | 0.5 人日（TCP listener+factory） | 3~5 人日（CBOR+帧+模型） | 1~2 人日 | **5~8 人日** | 中 |
| **3.3 CGO 共享库内嵌** | 3~5 人日（库化改造）+ 3~5 人日（c-shared 导出/内存所有权） | 2~3 人日（JNI/JNA） | **5~10 人日（P1~P7 验证）** | **2~4 周** | **高** |
| 3.4 GraalVM/WASM | — | — | — | 不建议 | 极高 |

另需计入（3.1/3.2/3.3 都要）：

| 项 | 工作量 | 说明 |
|---|---|---|
| **Java 工具回调通道** | **3~8 人日** | 设计上下文传播（登录态/租户/事务）+ 鉴权 + 超时 + 错误映射。**这是真正的成本大头，且各方案都要付** |
| 会话/留痕权威归口 | 1~2 人日 | pi-go SQLite ↔ next-agent `TraceStore` 谁说了算 |
| 部署与运维 | 1~3 人日 | 容器化、健康检查、日志、优雅停机、升级顺序 |
| 安全加固（3.1/3.2） | 2~3 人日 | 非 root、只读挂载、无网络出站白名单、seccomp |
| **合计（推荐路径 3.1）** | | **~10 人日** |
| **合计（3.3 内嵌）** | | **~5~7 周**，且含不可消除的运行时风险 |

---

## 7. 建议

### 如果你的目标是"给 next-agent 的意图执行找推理内核"

**不要换内核。** 用 `pi-java`。理由：
1. 同进程 → `IntentTool` 是普通方法调用，宿主权限/事务随调用栈，**零改造**；
2. 同 JVM → 无双 GC、双信号、双线程池、跨语言栈；
3. pi-java 已经有 `AgentLoop` 的六个钩子、`Skills`、`PromptTemplates`、会话树、压缩 ——
   本系列前一份分析（`pi-java-能力差距分析.md`）已确认**缺的是接线，不是能力**；
4. 换 pi-go 会**同时**引入"内核跨语言"和"工具跨进程"两个新问题，
   而它们要解决的问题（意图执行不完善）**根因是接线缺失，不是内核不行**。

### 如果你确实需要 pi-go 的能力（例如它的多租户、traceviewer、workspace memory）

- 走 **§3.1 独立 HTTP 服务**，把它当**独立组件**用，不要内嵌；
- 用在**不依赖宿主上下文**的场景：文档处理、批量分析、代码审查、平台级 agent 服务；
- 让 pi-go 的 tenant 边界 ≈ 你的业务系统边界，直接复用它的限流/配额/审计；
- 明确接受"工具回调跨进程"，并**优先把不依赖登录态的工具先跑起来**验证通道。

### 如果一定要内嵌（3.3）

先做一个**两天的可行性验证（spike）**，只验证四件事，全部要在**目标 OS（Windows）**上做：
1. 造一个最小 `c-shared` 库（`//export` 一个空函数 + 一个 `panic` 路径），JNI 加载；
2. **故意触发 Go panic**，确认 JVM 是否存活（验 P1）；
3. webapp 热部署一次，确认 ClassLoader 与 native 内存是否泄漏（验 P4）；
4. 容器内存压到 limit，确认 `GOMEMLIMIT` + `-Xmx` 能否配平（验 P2）。

**这四条任一不过，就放弃内嵌。** 不要在 spike 通过前投入库化改造。

---

## 附录：本次评估的关键证据

| 结论 | 证据位置 |
|---|---|
| pi-go 无 CGO | `pi-go/go.mod:5-8`（仅 `golang.org/x/sys`、`modernc.org/sqlite`）；`PORTING.md:105` |
| 入口全是 `package main` | `pi-go/cmd/pi/main.go`；`pi-go-multitenant/cmd/pi-server/main.go:1-2` |
| RPC 仅 Unix socket（server） | `pi-go/server/unix.go:10-12`、`pi-go/server/listener.go:23-29` |
| RPC 仅 Unix socket（client） | `pi-go/client/unix.go:14-16` |
| 传输无关（可换 TCP） | `pi-go/client/client.go:27-41`、`pi-go/server/unix.go:36` |
| 帧格式 4 字节大端 + CBOR | `pi-go/protocol/framing.go:8-11,39-51,82-181` |
| multitenant 有完整 HTTP/SSE 服务 | `pi-go-multitenant/multitenant/server.go:21-63,474-495,620-660` |
| 多租户能力（限流/配额/审计/守卫） | `multitenant/ratelimit.go`、`hooks.go:12-16,77-175`、`guard.go:11-20`、`identity_tool.go` |
| `GuardedEnv` 不是进程沙箱 | `multitenant/guard.go:11-13`（注释原文） |
| 二进制 ~19.8MB | 本机 `pi-go/pi.exe` 实测 |
| 无 TCP 用于 agent RPC | 全仓 grep `net.Listen("tcp"` → 仅 `webui/server.go:76`、`traceviewer/viewer.go:192`、`ai/oauth_callback.go:54`、`ai/openrouter_oauth.go:41` |
| pi-go subagents 也是子进程 | `pi-go/coding-agent/extensions/subagents/runner.go` |
