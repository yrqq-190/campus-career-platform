# T11：M7 面经筛选、外链校验与失效兜底（Mock 骨架）

> 对应 Issue：#108｜分支：`feature/issue-108-m7-notes`（从 `main` 切出，一任务一分支）
> 对应 spec/AC：`spec/module-07-interview-notes.md` AC-1、AC-4（兼顾 AC-3 空态、AC-5 演示标识）
> 契约来源：`docs/architecture.md` §4.7（冻结契约，本文件只做 Mock 落地，不改契约、不新增 spec 之外的业务错误码）
> 形态说明：本 PR 为**契约骨架 + 写死 Mock**，不连库、不写业务规则，不实现 SSRF 校验与 24h 探测定时任务；统一返回壳、Service/Mapper、MapStruct、真实 HTTP 调用与自动化测试，在 #11 脚手架与 T5 前置完成后于**同一分支**替换补齐，不另开分支。

## 1. 端点总览（architecture §4.7）

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | GET | `/api/v1/interview-notes` | 面经列表筛选、分页、分组展示 | AC-1、AC-3、AC-5；NFR-1、NFR-2 |
| 2 | POST | `/api/v1/interview-notes` | 收录面经外链（不收正文） | AC-1；边界行 3；NFR-4 |
| 3 | GET | `/api/v1/interview-notes/{noteId}/reachability` | 点击实时校验（3 秒判失效） | AC-4；NFR-3 |

M7 只读 M2 画像方向用于排序，不调用 M1、M3–M6、M8，也不被其他模块依赖（辅助查询环节）。

## 2. GET `/api/v1/interview-notes` 面经列表

- Header：`Authorization: Bearer <JWT>`
- Query：`position`、`direction`、`source`（均可空）；`page`（默认 1）、`pageSize`（默认 10，最大 100）

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

两个 `422` 的触发条件与数据影响不同（来源缺失 / 正文合规），均不使用业务码。

SSRF 防护归属**服务层**（Controller 骨架不实现，仅固化口径，来源 §4.7）：仅允许 `http`/`https`；DNS 全部解析 IP 逐一校验，拒绝环回 `127.0.0.0/8`、`::1`，私网 `10/8`、`172.16/12`、`192.168/16`，链路本地 `169.254/16`（含 `169.254.169.254`）、`fe80::/10`，以及未指定 / 保留 / 多播地址；最多跟随 1 次重定向且每跳重新校验；连接与总超时 ≤3s，对校验通过的 IP 直连以防 DNS rebinding；命中即返回 `400`、不写库。

## 4. GET `/api/v1/interview-notes/{noteId}/reachability` 点击实时校验

Header 带 JWT，无 body；实时校验**不修改记录**，定时探测结果由后台任务更新（点击 3 秒不改库）。

### 4.1 外链可达 `200 OK`

```json
{
  "code": "OK",
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

### 4.3 失败响应

| 状态码 | 错误码 | 触发条件 | 数据影响 |
|---|---|---|---|
| 404 | —（无业务码） | `noteId` 指向不存在的记录 | 不写库 |
| 400 | —（无业务码） | 实时校验目标命中 SSRF 规则（非 http/https、内网或保留段 IP、重定向超限） | 不发起探测请求、不写库 |
| 401 | —（无业务码） | 无 JWT 或 Token 失效 | 不返回数据 |

> `404` 针对不存在的具体资源 ID；列表筛选无匹配仍按 `200` + `items: []` 处理，二者不冲突。

## 5. Java 骨架（契约示意代码块，暂不进入构建）

> 仅固化路由与字段：方法体返回写死 Mock（自包含 `Map`，与 T6 Mock 骨架一致），不注入 Mapper、不写业务规则。
> 落地待 #11 脚手架提供统一返回壳、全局异常到状态码映射、Bean Validation、MapStruct 与构造器注入规范后，在同一分支替换为 `Controller → Service → Mapper → Entity` 分层实现并补测试。

目标落地路径：`src/main/java/com/campus/career/controller/InterviewNoteController.java`

```java
package com.campus.career.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T11 / M7 面经外链契约骨架（Mock）。
 * 仅冻结路由与响应字段：方法体写死返回，不连库、不写业务规则。
 * SSRF 校验、24h 探测、3s 超时降级、个性化排序与 MapStruct 转换归属 Service 层，本类不实现。
 */
@RestController
@RequestMapping("/api/v1/interview-notes")
public class InterviewNoteController {

    /** GET /api/v1/interview-notes：筛选 + 分页；Mock 返回固定一页。 */
    @GetMapping
    public Map<String, Object> listNotes(
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

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

import java.util.List;

/** 列表条目 VO：字段与 §4.7.1 响应一一对应，不增加正文字段。 */
public record InterviewNoteVO(
        String noteId,
        String url,
        String source,
        String position,
        String direction,
        String linkStatus,
        String lastProbedAt,
        String nextProbeAt,
        Boolean isDemo) {
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

> 红线：`<script setup lang="ts">`、禁用 `any`；正式实现的 HTTP 调用必须经 composable / store，组件不直接 `fetch`/`axios`。当前仅用组件内写死数组做展示骨架，不发请求。

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

const filters = reactive({ position: '', direction: '', source: '' })
const loading = ref(false)

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

// MOCK：正式实现先请求 reachability；可达再打开原文，3s 降级则提示“同关键词重检”。
// linkStatus=INVALID 的记录已被 24h 探测置灰，按钮禁用、不可点击（NFR-2）；点击校验不改库。
async function checkLink(note: InterviewNote): Promise<void> {
  if (note.linkStatus === 'INVALID') return
  window.open(note.url, '_blank', 'noopener,noreferrer')
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
      <el-table-column label="操作" width="120">
        <template #default="{ row }: { row: InterviewNote }">
          <el-button
            size="small"
            :disabled="row.linkStatus === 'INVALID'"
            @click="checkLink(row)"
          >
            查看原文
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
```

## 7. 边界与待接续清单（不越界）

- 本 PR 只交付 docs 内 Mock 契约与骨架代码块：**不连库、不写业务规则、不改动 `spec/` 与 `docs/architecture.md` 冻结契约**，不存储面经正文（NFR-4）。
- 待 #11 脚手架落地后在同一分支补：可编译的 Controller/DTO/VO、统一返回壳与全局错误处理、Bean Validation（含 `source` 必填、未知正文拒绝）、Service 层 SSRF 与 3s 超时降级、MyBatis-Plus 实体/Mapper、MapStruct、构造器注入、前端工程内页面与 composable/store。
- 依赖 T5（M2 画像方向）就绪后再接 `personalizationApplied` 个性化排序；M7 不向其他模块推送数据（§4.7 越界声明）。
- 计划补测（11.22 起，接口层单测 / 前端骨架）：列表筛选与分页（`pageSize>100` → 400、空结果 `200` + `items:[]`、画像缺失 `personalizationApplied=false`）；POST 拒绝链（400 非法 URL/SSRF、422 来源缺失、422 携带正文）；reachability 可达 `200` 与 3s 降级 `200`（`CONNECT_TIMEOUT_3S`）、不存在 `404`、SSRF `400`、未鉴权 `401`；SSRF 用例覆盖环回 / 私网 / 链路本地（含云元数据地址）/ 重定向超限。
