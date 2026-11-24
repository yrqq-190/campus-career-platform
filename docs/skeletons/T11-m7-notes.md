# T11：M7 面经筛选、外链校验与失效兜底（Mock 骨架）

> 对应 Issue：#108｜分支：`feature/issue-108-m7-notes`（从 `main` 切出，一任务一分支）
> 对应 spec/AC：`spec/module-07-interview-notes.md` AC-1、AC-4（兼顾 AC-3 空态、AC-5 演示标识）
> 契约来源：`docs/architecture.md` §4.7（冻结契约，本文件只做 Mock 落地，不改契约、不新增 spec 之外的业务错误码）
> 形态说明：本 PR 为 **docs-only 契约骨架 + 写死 Mock**（代码块仅作示意，不进入构建），不连库、不写业务规则，不实现 SSRF 校验与 24h 探测定时任务。按 2026-11-24 团队口径，骨架 PR 统一走「评审 → 返修 → APPROVE → 合并」：本骨架经评审 APPROVE 后合入 `main` 并删除分支；第 10 周实现从最新 `main` 新开 `feature/issue-108-m7-notes-impl` 分支（Issue #108 保持打开、编号不变），在实现分支把示意代码替换为可编译的 Controller / Service / Mapper 与前端页面并补自动化测试。统一响应壳 `com.campus.career.common.ApiResponse`、`RequestIdFilter`、`GlobalExceptionHandler` 已在 `main`（M1 上传链路），其余 Service / Mapper、MapStruct、真实 HTTP 调用在实现分支接入。

## 1. 端点总览（architecture §4.7）

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | GET | `/api/v1/interview-notes` | 面经列表筛选、分页；按岗位 / 来源的分组展示仅由前端完成（契约无分组字段） | AC-1、AC-3、AC-5；NFR-1、NFR-2 |
| 2 | POST | `/api/v1/interview-notes` | 收录面经外链（不收正文） | AC-1；边界行 3；NFR-4 |
| 3 | GET | `/api/v1/interview-notes/{noteId}/reachability` | 点击实时校验（3 秒判失效） | AC-4；NFR-3 |

M7 只读 M2 画像方向用于排序，不调用 M1、M3–M6、M8，也不被其他模块依赖（辅助查询环节）。

## 2. GET `/api/v1/interview-notes` 面经列表

- Header：`Authorization: Bearer <JWT>`
- Query：`position`、`direction`、`source`（均可空）；`page`（默认 1）、`pageSize`（默认 10，最大 100）。分页参数由 API 层 Bean Validation 校验：`page ≥ 1`、`1 ≤ pageSize ≤ 100`，越界返回 `400`（§4.7.1）。
- **分组展示边界（评审建议）**：`GET` 响应只有分页 `items[]`，**没有任何分组字段或分组参数**；页面若按岗位 / 来源分组，由前端基于当前页 `items[]` 自行聚合，实现时不得在 VO / 表结构私加分组字段。

### 2.1 `200 OK` 成功 Mock（含可访问与已失效两条）

```json
{
  "code": "OK",
  "requestId": "a1b2c3d4",
  "data": {
    "personalizationApplied": true,
    "page": 1,
    "pageSize": 10,
    "total": 2,
    "items": [
      {
        "noteId": "n_001",
        "url": "https://example.com/interview/123",
        "source": "某公开招聘社区",
        "position": "后端开发",
        "direction": "Java",
        "linkStatus": "OK",
        "lastProbedAt": "2026-11-12T08:00:00+08:00",
        "nextProbeAt": "2026-11-13T08:00:00+08:00",
        "isDemo": false
      },
      {
        "noteId": "n_002",
        "url": "https://example.com/interview/456",
        "source": "某技术论坛",
        "position": "后端开发",
        "direction": "Java",
        "linkStatus": "INVALID",
        "lastProbedAt": "2026-11-12T08:00:00+08:00",
        "nextProbeAt": "2026-11-13T08:00:00+08:00",
        "isDemo": true
      }
    ]
  }
}
```

- 每条仅含外链、来源与检索条件，**不含面经正文**（FR-2、NFR-4）。
- `linkStatus = INVALID` 表示 24h 探测失效，记录在前端置灰、不可点击并带「来源已失效」标记（NFR-2）；该状态在 `200` 内返回，**不是错误**。
- `isDemo = true` 为演示数据标识，不与真实数据混用（AC-5）。

### 2.2 空态与个性化降级（均为 `200`，不是错误）

筛选无匹配（AC-3）：

```json
{
  "code": "OK",
  "requestId": "a1b2c3d5",
  "data": {
    "personalizationApplied": true,
    "page": 1,
    "pageSize": 10,
    "total": 0,
    "items": []
  }
}
```

M2 画像方向缺失时 `personalizationApplied = false`，不做个性化排序、返回默认列表，仍支持手动筛选（AC-3）：

```json
{
  "code": "OK",
  "requestId": "a1b2c3d6",
  "data": {
    "personalizationApplied": false,
    "page": 1,
    "pageSize": 10,
    "total": 0,
    "items": []
  }
}
```

### 2.3 失败响应（M7 不定义大写业务错误码）

| 状态码 | 错误码 | 触发条件 | 数据影响 | 归属 |
|---|---|---|---|---|
| 400 | —（无业务码） | `pageSize > 100`、分页参数非法，或筛选参数格式错误 | 不查询、不写库 | API 层参数校验 |
| 401 | —（无业务码） | 无 JWT 或 Token 失效 | 不返回列表数据 | API 层统一鉴权 |

> 列表筛选无匹配、画像缺失、外链失效均在 `200` 内以空数组或降级标记表达，不造 `404`。

## 3. POST `/api/v1/interview-notes` 收录面经外链

请求体仅接受外链、来源与检索条件：

```json
{
  "url": "https://example.com/interview/123",
  "source": "某公开招聘社区",
  "position": "后端开发",
  "direction": "Java"
}
```

- **禁止接收面经正文**：请求体出现 `content` / `body` 字段一律拒绝（NFR-4）。

### 3.1 `201 Created` 成功 Mock

```json
{
  "code": "OK",
  "requestId": "e5f6a7b8",
  "data": {
    "noteId": "n_003",
    "linkStatus": "PENDING",
    "lastProbedAt": null,
    "nextProbeAt": "2026-11-13T15:00:00+08:00"
  }
}
```

新记录进入 `PENDING` 待探测状态，由后台 24h 定时任务首次探测；收录过程不抓取、不缓存正文（FR-2、NFR-4）。

### 3.2 失败响应

| 状态码 | 错误码 | 触发条件 | 数据影响 | 归属 |
|---|---|---|---|---|
| 400 | —（无业务码） | `url` 非合法 http/https 链接，或请求体结构错误 | 不写库 | API 层参数校验 |
| 400 | —（无业务码） | 协议非 http/https；解析后 IP 属环回 / 私网 / 链路本地 / 保留段；重定向超过 1 次或跳转目标未过校验（SSRF） | 不发起请求、不写库、不产生探测记录 | 服务层 SSRF 统一校验 |
| 422 | —（无业务码） | `source` 来源字段缺失 | 拒绝入库并记录质量告警，不产生面经记录 | 服务层校验；边界行 3、AC-1 |
| 422 | —（无业务码） | 请求携带面经正文（`content` / `body`）字段 | 一律拒绝，不存储正文 | 服务层校验；NFR-4 |
| 401 | —（无业务码） | 无 JWT 或 Token 失效 | 不写库 | API 层统一鉴权 |

两个 `422` 的触发条件与数据影响不同（来源缺失 / 正文合规），均不使用 M7 业务错误码。因响应无业务码、前端无法按 `code` 区分这两种 `422`，骨架约定两条**固定 `message` 文案**（不改契约、不新增字段，实现与前端按逐字匹配做提示）：

| 触发条件 | HTTP | 固定 `message`（逐字） |
| --- | --- | --- |
| `source` 来源字段缺失（含空串 / 纯空白） | `422` | `来源字段缺失` |
| 请求携带面经正文字段（`content` / `body`） | `422` | `不允许提交面经正文` |

> 两条文案仅用于前端区分提示与测试断言；统一响应体的 `code` / `requestId` 仍由统一返回壳输出，M7 不新增大写业务码。

SSRF 防护归属**服务层**（Controller 骨架不实现，仅固化口径，来源 §4.7）：仅允许 `http`/`https`；DNS 全部解析 IP 逐一校验，拒绝环回 `127.0.0.0/8`、`::1`，私网 `10/8`、`172.16/12`、`192.168/16`，链路本地 `169.254/16`（含 `169.254.169.254`）、`fe80::/10`，以及未指定 / 保留 / 多播地址；最多跟随 1 次重定向且每跳重新校验；连接与总超时 ≤3s，对校验通过的 IP 直连以防 DNS rebinding；命中即返回 `400`、不写库。

## 4. GET `/api/v1/interview-notes/{noteId}/reachability` 点击实时校验

Header 带 JWT，无 body；实时校验**不修改记录**，定时探测结果由后台任务更新（点击 3 秒不改库）。

**点击 / 重检链路（AC-4，前端必须按此顺序，实现见 §6 骨架）**：

1. 开窗前先做浏览器侧协议校验，仅允许 `http:` / `https:`，拦截 `javascript:` 等伪协议（服务端 SSRF 防护不覆盖浏览器跳转）。
2. 非 `INVALID` 记录点「查看原文」→ 先 `GET /api/v1/interview-notes/{noteId}/reachability`：`reachable=true` 才 `window.open`；`reachable=false`（如 `CONNECT_TIMEOUT_3S`）不打开原链，展示「来源已失效，可换关键词重检」兜底（`retryKeyword` / `retryAvailable`）。
3. `INVALID` 记录的「查看原文」按 NFR-2 置灰禁用，但该行保留一个**可点击的「重新检测」入口**（独立于原链按钮）：再次调用同一探测端点；恢复可达后允许开窗，仍不可达则继续给重检兜底，从而同时满足 NFR-2 置灰与 AC-4 兜底。
4. 兜底中的「按关键词重检」用响应的 `retryKeyword` 回填筛选条件并重新拉列表（示例 `retryKeyword` 为岗位词，回填 `position`；最终字段映射在实现时固定，不新增请求字段）。

### 4.1 外链可达 `200 OK`

```json
{
  "code": "OK",
  "requestId": "c7d8e9f0",
  "data": {
    "noteId": "n_001",
    "reachable": true,
    "linkStatus": "OK",
    "checkedAt": "2026-11-12T15:10:00+08:00"
  }
}
```

### 4.2 连接 3 秒无响应：降级仍为 `200`（不是错误）

```json
{
  "code": "OK",
  "requestId": "c7d8e9f1",
  "data": {
    "noteId": "n_001",
    "reachable": false,
    "linkStatus": "INVALID",
    "unreachableReason": "CONNECT_TIMEOUT_3S",
    "retryKeyword": "后端开发",
    "retryAvailable": true,
    "checkedAt": "2026-11-12T15:10:03+08:00"
  }
}
```

按 AC-4 给出同关键词重检兜底（`retryKeyword`、`retryAvailable`），不长时间无反馈（NFR-3）。

> `docs/architecture.md` §4.7.3 的冻结示例省略了 `requestId`（§4.7.1 / §4.7.2 均带）；按统一响应体约定，本端点实际响应**必须**由统一返回壳带上 `requestId`（与 `X-Request-Id` 响应头同值），上方 Mock 已补示例字段。契约原文的示例补全另走架构变更，本骨架不改冻结契约。

### 4.3 失败响应

| 状态码 | 错误码 | 触发条件 | 数据影响 |
|---|---|---|---|
| 404 | —（无业务码） | `noteId` 指向不存在的记录 | 不写库 |
| 400 | —（无业务码） | 实时校验目标命中 SSRF 规则（非 http/https、内网或保留段 IP、重定向超限） | 不发起探测请求、不写库 |
| 401 | —（无业务码） | 无 JWT 或 Token 失效 | 不返回数据 |

> `404` 针对不存在的具体资源 ID；列表筛选无匹配仍按 `200` + `items: []` 处理，二者不冲突。

## 5. Java 骨架（契约示意代码块，暂不进入构建）

> 仅固化路由与字段：方法体返回写死 Mock（自包含 `Map`，与 T6 Mock 骨架一致），不注入 Mapper、不写业务规则。
> **`requestId` 口径（评审阻塞 1）**：下方三个 Mock 方法显式 `put("requestId", …)` 仅为冻结完整响应结构、避免照抄时漏字段；正式实现中 `requestId` 由 `RequestIdFilter` 每请求生成（8 位十六进制，同值写入请求属性、`X-Request-Id` 响应头与 MDC），Controller 经 `ApiResponse.ok(requestId, data)` / `ApiResponse.error(...)` 由统一返回壳序列化，业务代码不手写固定值。
> 实现分支（骨架 PR 合并后从最新 `main` 新开的 `-impl` 分支）在现有工程上替换为 `Controller → Service → Mapper → Entity` 分层实现并补测试；统一响应壳、全局异常处理已在 `main`，Bean Validation、MapStruct、持久化与构造器注入规范在实现分支接入。

目标落地路径：`src/main/java/com/campus/career/controller/InterviewNoteController.java`

```java
package com.campus.career.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T11 / M7 面经外链契约骨架（Mock）。
 * 仅冻结路由与响应字段：方法体写死返回，不连库、不写业务规则。
 * SSRF 校验、24h 探测、3s 超时降级、个性化排序与 MapStruct 转换归属 Service 层，本类不实现。
 */
@Validated
@RestController
@RequestMapping("/api/v1/interview-notes")
public class InterviewNoteController {

    /**
     * GET /api/v1/interview-notes：筛选 + 分页；Mock 返回固定一页。
     * 类上 {@code @Validated} 使参数注解生效；page 越界、pageSize 越界（>100 或 <1）由全局异常处理转 400。
     */
    @GetMapping
    public Map<String, Object> listNotes(
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 最小为 1") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "pageSize 最小为 1")
            @Max(value = 100, message = "pageSize 最大为 100") Integer pageSize) {

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("noteId", "n_001");
        item.put("url", "https://example.com/interview/123");
        item.put("source", "某公开招聘社区");
        item.put("position", "后端开发");
        item.put("direction", "Java");
        item.put("linkStatus", "OK");
        item.put("lastProbedAt", "2026-11-12T08:00:00+08:00");
        item.put("nextProbeAt", "2026-11-13T08:00:00+08:00");
        item.put("isDemo", false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("personalizationApplied", true);
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("total", 1);
        data.put("items", List.of(item));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", "OK");
        resp.put("requestId", "a1b2c3d4"); // MOCK 固定值仅冻结结构；正式由 ApiResponse.ok 注入
        resp.put("data", data);
        return resp;
    }

    /** POST /api/v1/interview-notes：收录外链；Mock 直接返回 201 契约样例。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createNote(@RequestBody Map<String, Object> body) {
        // MOCK：正式实现改为 @Valid @RequestBody CreateInterviewNoteRequest。
        // source 缺失 -> 422；含 content/body 正文 -> 422；url 非法或命中 SSRF -> 400（SSRF 在 Service 层）。
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noteId", "n_003");
        data.put("linkStatus", "PENDING");
        data.put("lastProbedAt", null);
        data.put("nextProbeAt", "2026-11-13T15:00:00+08:00");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", "OK");
        resp.put("requestId", "e5f6a7b8"); // MOCK 固定值仅冻结结构；正式由 ApiResponse.ok 注入
        resp.put("data", data);
        return resp;
    }

    /** GET /api/v1/interview-notes/{noteId}/reachability：点击实时校验；Mock 返回可达样例。 */
    @GetMapping("/{noteId}/reachability")
    public Map<String, Object> reachability(@PathVariable String noteId) {
        // MOCK：3s 无响应时 data 改为 reachable=false、linkStatus=INVALID、
        // unreachableReason=CONNECT_TIMEOUT_3S、retryKeyword、retryAvailable=true，HTTP 仍为 200。
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noteId", noteId);
        data.put("reachable", true);
        data.put("linkStatus", "OK");
        data.put("checkedAt", "2026-11-12T15:10:00+08:00");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", "OK");
        resp.put("requestId", "c7d8e9f0"); // MOCK 固定值仅冻结结构；正式由 ApiResponse.ok 注入
        resp.put("data", data);
        return resp;
    }
}
```

DTO / VO 字段契约（md 示意代码块，落地时按类拆到 `dto/interview`、`vo/interview` 包）：

```java
package com.campus.career.dto.interview;

/** POST 收录请求：只含外链与检索条件；刻意不声明 content/body 正文字段。 */
public record CreateInterviewNoteRequest(
        String url,
        String source,
        String position,
        String direction) {
}
```

```java
package com.campus.career.vo.interview;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 列表条目 VO：字段与 §4.7.1 响应一一对应，不增加正文字段。
 * {@code @JsonProperty("isDemo")} 固定对外字段名，避免 Boolean 组件在访问器解析差异下被推导成 demo；
 * 基线 JDK 17 + Spring Boot 3.3.5（Jackson 版本由其 BOM 管理），record 组件本就保留 isDemo，注解为双保险。
 */
public record InterviewNoteVO(
        String noteId,
        String url,
        String source,
        String position,
        String direction,
        String linkStatus,
        String lastProbedAt,
        String nextProbeAt,
        @JsonProperty("isDemo") Boolean isDemo) {
}

/** 列表分页 VO。 */
public record InterviewNotePageVO(
        Boolean personalizationApplied,
        Integer page,
        Integer pageSize,
        Long total,
        List<InterviewNoteVO> items) {
}

/** 点击实时校验 VO：可达与 3s 降级共用，降级时填 unreachableReason / retry* 字段。 */
public record ReachabilityVO(
        String noteId,
        Boolean reachable,
        String linkStatus,
        String unreachableReason,
        String retryKeyword,
        Boolean retryAvailable,
        String checkedAt) {
}
```

## 6. 前端页面骨架（Vue 3 + TS + Element Plus，示意代码块，暂不进入构建）

> 红线：`<script setup lang="ts">`、禁用 `any`；正式实现的 HTTP 调用必须经 composable / store，组件不直接 `fetch`/`axios`。当前仅用组件内写死数组做展示骨架，不发真实请求。点击链路按 AC-4 固化为「浏览器侧协议校验 → 经 composable 探测 reachability → 可达才 `window.open` / 不可达给重检兜底」，并用独立的「重新检测」入口兼顾 NFR-2 置灰；下方 `probeReachability` 仅为 composable 占位，组件内不出现真实 HTTP 调用。

目标落地路径：`src/views/interview/InterviewNotesView.vue`

```vue
<script setup lang="ts">
import { reactive, ref } from 'vue'

type LinkStatus = 'OK' | 'INVALID' | 'PENDING'

interface InterviewNote {
  noteId: string
  url: string
  source: string
  position: string | null
  direction: string | null
  linkStatus: LinkStatus
  lastProbedAt: string | null
  nextProbeAt: string | null
  isDemo: boolean
}

/** GET /{noteId}/reachability 的 data；可达与 3s 降级共用，降级时填 retry* 字段。 */
interface ReachabilityResult {
  noteId: string
  reachable: boolean
  linkStatus?: LinkStatus
  unreachableReason?: string
  retryKeyword?: string
  retryAvailable?: boolean
  checkedAt: string
}

/** AC-4 失效兜底条：keyword 供「按关键词重检」回填筛选。 */
interface FallbackTip {
  noteId: string
  keyword: string
  message: string
}

const filters = reactive({ position: '', direction: '', source: '' })
const loading = ref(false)
const probingId = ref<string | null>(null)
const fallbackTip = ref<FallbackTip | null>(null)

// MOCK：写死列表，字段与 GET /api/v1/interview-notes 的 items[] 对齐；正式实现经 composable/store 获取。
const items = ref<InterviewNote[]>([
  {
    noteId: 'n_001',
    url: 'https://example.com/interview/123',
    source: '某公开招聘社区',
    position: '后端开发',
    direction: 'Java',
    linkStatus: 'OK',
    lastProbedAt: '2026-11-12T08:00:00+08:00',
    nextProbeAt: '2026-11-13T08:00:00+08:00',
    isDemo: false
  },
  {
    noteId: 'n_002',
    url: 'https://example.com/interview/456',
    source: '某技术论坛',
    position: '后端开发',
    direction: 'Java',
    linkStatus: 'INVALID',
    lastProbedAt: '2026-11-12T08:00:00+08:00',
    nextProbeAt: '2026-11-13T08:00:00+08:00',
    isDemo: true
  }
])

// MOCK：正式实现改为经 composable 调用列表接口，携带 filters 与分页参数。
async function fetchList(): Promise<void> {
  loading.value = false
}

// MOCK 占位：正式实现由 composable / store 调用 GET /api/v1/interview-notes/{noteId}/reachability，
// 组件内不直接 fetch/axios；此处默认返回可达样例，降级形态见 §4.2（reachable=false + retry* 字段）。
function probeReachability(noteId: string): Promise<ReachabilityResult> {
  return Promise.resolve({ noteId, reachable: true, linkStatus: 'OK', checkedAt: '' })
}

function isHttpUrl(url: string): boolean {
  // 浏览器侧协议白名单：服务端 SSRF 校验不覆盖 window.open 跳转，必须在开窗前拦截 javascript: 等伪协议。
  return /^https?:\/\//i.test(url)
}

function syncStatus(note: InterviewNote, status: LinkStatus | undefined): void {
  if (status === 'OK' || status === 'INVALID' || status === 'PENDING') {
    note.linkStatus = status
  }
}

// 探测 → 可达才开窗；不可达置 INVALID 并给 AC-4 兜底。checkLink（查看原文）与 recheck（重新检测）共用。
async function probeAndOpen(note: InterviewNote): Promise<void> {
  if (!isHttpUrl(note.url)) {
    fallbackTip.value = {
      noteId: note.noteId,
      keyword: note.position ?? note.direction ?? '',
      message: '链接协议不合法，已阻止打开'
    }
    return
  }
  probingId.value = note.noteId
  try {
    const probe = await probeReachability(note.noteId)
    syncStatus(note, probe.linkStatus)
    if (probe.reachable) {
      fallbackTip.value = null
      window.open(note.url, '_blank', 'noopener,noreferrer')
      return
    }
    note.linkStatus = 'INVALID'
    fallbackTip.value = {
      noteId: note.noteId,
      keyword: probe.retryKeyword ?? note.position ?? note.direction ?? '',
      message: '来源已失效，可换关键词重检'
    }
  } finally {
    probingId.value = null
  }
}

// 「查看原文」：NFR-2 下 INVALID 记录按钮已禁用，函数内再兜底拦截一次，不打开原链。
async function checkLink(note: InterviewNote): Promise<void> {
  if (note.linkStatus === 'INVALID') return
  await probeAndOpen(note)
}

// INVALID 行的独立「重新检测」入口：不受 INVALID 拦截，再次探测同一链接（点击校验不改库）。
async function recheck(note: InterviewNote): Promise<void> {
  await probeAndOpen(note)
}

// 兜底条「按关键词重检」：用 retryKeyword（示例为岗位词）回填筛选并重新拉列表。
function applyRetryKeyword(): void {
  if (!fallbackTip.value || !fallbackTip.value.keyword) return
  filters.position = fallbackTip.value.keyword
  void fetchList()
}
</script>

<template>
  <section class="interview-notes">
    <el-form inline>
      <el-form-item label="岗位">
        <el-input v-model="filters.position" placeholder="岗位" clearable />
      </el-form-item>
      <el-form-item label="方向">
        <el-input v-model="filters.direction" placeholder="方向" clearable />
      </el-form-item>
      <el-form-item label="来源">
        <el-input v-model="filters.source" placeholder="来源" clearable />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="fetchList">筛选</el-button>
    </el-form>

    <el-empty v-if="items.length === 0" description="暂无符合条件的面经，可调整筛选条件后重试" />

    <el-table v-else :data="items">
      <el-table-column prop="source" label="来源" min-width="140" />
      <el-table-column prop="position" label="岗位" min-width="110" />
      <el-table-column prop="direction" label="方向" min-width="100" />
      <el-table-column label="状态" min-width="170">
        <template #default="{ row }: { row: InterviewNote }">
          <el-tag :type="row.linkStatus === 'OK' ? 'success' : 'info'">
            {{ row.linkStatus === 'OK' ? '可访问' : '来源已失效' }}
          </el-tag>
          <el-tag v-if="row.isDemo" type="warning" style="margin-left: 8px">演示数据</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }: { row: InterviewNote }">
          <el-button
            size="small"
            :loading="probingId === row.noteId && row.linkStatus !== 'INVALID'"
            :disabled="row.linkStatus === 'INVALID' || probingId === row.noteId"
            @click="checkLink(row)"
          >
            查看原文
          </el-button>
          <el-button
            v-if="row.linkStatus === 'INVALID'"
            size="small"
            link
            type="primary"
            :loading="probingId === row.noteId"
            :disabled="probingId === row.noteId"
            @click="recheck(row)"
          >
            重新检测
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-alert
      v-if="fallbackTip"
      class="retry-tip"
      type="warning"
      show-icon
      :closable="true"
      :title="fallbackTip.message"
      @close="fallbackTip = null"
    >
      <template #default>
        <span>同一来源可换关键词重新检索；也可在该行点「重新检测」再次探测。</span>
        <el-button
          v-if="fallbackTip.keyword"
          size="small"
          type="warning"
          plain
          style="margin-left: 8px"
          @click="applyRetryKeyword"
        >
          按关键词「{{ fallbackTip.keyword }}」重检
        </el-button>
      </template>
    </el-alert>
  </section>
</template>
```

## 7. 边界与待接续清单（不越界）

- 本 PR 只交付 docs 内 Mock 契约与骨架代码块：**不连库、不写业务规则、不改动 `spec/` 与 `docs/architecture.md` 冻结契约、不改 `db/`**，不存储面经正文（NFR-4）。
- 骨架 PR 按 2026-11-24 口径经 APPROVE 后合入 `main`；第 10 周在从最新 `main` 新开的 `feature/issue-108-m7-notes-impl` 分支补：可编译的 Controller/DTO/VO、Bean Validation（含 `source` 必填、未知正文拒绝、分页 `@Min` / `@Max`）、Service 层 SSRF 与 3s 超时降级、MyBatis-Plus 实体/Mapper、MapStruct、构造器注入、前端工程内页面与 composable/store。统一返回壳（`ApiResponse` / `RequestIdFilter` / `GlobalExceptionHandler`）已在 `main`，实现分支直接使用。
- **实现前置·库表缺口（属 T1 / `db/schema.sql` 范围，另开 Issue 与 schema PR；本 PR 只留痕、不夹带 `db/` 改动）**：当前 `interview_note` 表仅有 `source_url / title / company / position / tags / status / last_checked_at / created_at`，尚缺 §4.7.1 所需的 `source`（来源标注，AC-1）、`direction`（方向筛选与个性化排序）、`next_probe_at`（24h 探测排期）、`is_demo`（AC-5 演示标识）四列；库内 `status` 注释枚举（`VALID / INVALID / UNVERIFIED`）与 §4.7 对外 `linkStatus`（`OK / INVALID / PENDING`）需在该 T1 变更中对齐字面取值，或明确分层映射（库内状态值与 API DTO 枚举允许映射，但要在 T1 PR 写清）。
- 依赖 T5（M2 画像方向）就绪后再接 `personalizationApplied` 个性化排序；M7 不向其他模块推送数据（§4.7 越界声明）。
- 实现分支（`-impl`）计划补测（接口层单测 / 前端页面测试）：列表筛选与分页（`pageSize>100` → 400、空结果 `200` + `items:[]`、画像缺失 `personalizationApplied=false`）；POST 拒绝链（400 非法 URL/SSRF、422 来源缺失、422 携带正文）；reachability 可达 `200` 与 3s 降级 `200`（`CONNECT_TIMEOUT_3S`）、不存在 `404`、SSRF `400`、未鉴权 `401`；SSRF 用例覆盖环回 / 私网 / 链路本地（含云元数据地址）/ 重定向超限。
