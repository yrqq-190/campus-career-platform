# T9：M5 文字模拟面试、四维评分与报告归档（Mock 骨架）

> 对应 Issue：#114｜分支：`feature/issue-114-m5-mock-session`（从 `main` 切出，一任务一分支）
> 对应 spec/AC：`spec/module-05-mock-interview.md` AC-1、AC-2、AC-3、AC-4、AC-5、AC-6
> 契约来源：`docs/architecture.md` §4.5（冻结契约，本文件只做 Mock 落地，不改契约、不新增 spec 之外的业务错误码）
> 形态说明：本 PR 为 **docs-only 契约骨架 + 写死 Mock**（代码块仅作示意，不进入构建），不连库、不写业务规则、不调用大模型（严格使用本地题库与关键词匹配）。按 2026-11-24 团队口径，骨架 PR 统一走「评审 → 返修 → APPROVE → 合并」：本骨架经评审 APPROVE 后合入 `main` 并删除分支；第 10 周实现从最新 `main` 新开 `feature/issue-114-m5-mock-session-impl` 分支（Issue #114 保持打开、编号不变），在实现分支把示意代码替换为可编译的 Controller / Service / Mapper 与前端页面并补自动化测试。统一响应壳 `com.campus.career.common.ApiResponse`、`RequestIdFilter`、`GlobalExceptionHandler` 已在 `main`（M1 上传链路），其余 Service / Mapper、MapStruct、真实 HTTP 调用在实现分支接入。

## 1. 端点总览（architecture §4.5）

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | POST | `/api/v1/mock-interviews` | 开启面试，抽取首题生成会话 | AC-1、AC-4 |
| 2 | POST | `/api/v1/mock-interviews/{sessionId}/answers` | 提交单题作答并评分，返回下一题 | AC-2、AC-5、AC-6 |
| 3 | POST | `/api/v1/mock-interviews/{sessionId}/report` | 生成并归档最终报告 | AC-3、AC-6 |

**关于「抽题」的归属（评审意见回应）**：业务口径上的四个动作——开启面试、抽题、单题评分、生成并归档报告——中，「抽题」在冻结契约 §4.5 中**不是独立 HTTP 端点**：首题由端点 1（开启面试）的 `currentQuestion` 返回，后续题由端点 2（作答评分）的 `nextQuestion` 返回。契约未定义独立抽题 URL，本骨架遵守「不改契约」口径，**不新增端点**；三个端点均已按下文逐项给出 URL / 请求 / 成功响应 / 失败响应 / 错误码五要素。若后续确需独立抽题端点，须另走架构变更（新增 ADR），不在本 PR 内夹带。

M5 严格不调用大模型，使用本地题库与关键词匹配评分（FR-5 / Non-Goals）；`jobId` 为可选前置（优先读 M4 投递记录），无记录时降级为手填 `jdText`（≥50 字，FR-1）；归档后仅 M8 只读看板可回看，不反向依赖。

## 2. POST `/api/v1/mock-interviews` 开启面试（含抽首题）

**请求**

- Header：`Authorization: Bearer <JWT>`
- Body：

```json
{
  "jobId": "j_20261112_007",
  "jdText": "负责后端服务开发，熟悉 Java / Spring Boot / MySQL ..."
}
```

- `jobId` 与 `jdText` 二选一：传 `jobId` 时优先读 M4 投递记录；不传或 M4 无记录时必须传 `jdText`（≥50 字）走降级路径（FR-1）。
- 抽题由服务层从预置题库按岗位类型抽取，固定 5-8 道（FR-3）；首题随本端点响应返回，不单独发请求。

### 2.1 `201 Created` 成功 Mock（AC-1，会话 ID + 首题）

```json
{
  "code": "OK",
  "requestId": "req-mock-m5-start-01",
  "data": {
    "sessionId": "mi_20261121_001",
    "status": "IN_PROGRESS",
    "totalQuestions": 5,
    "currentQuestion": {
      "questionId": 1,
      "content": "请描述一次你解决线上故障的经历。",
      "dimension": "项目经验"
    }
  }
}
```

- `dimension` 必须从 `专业知识 / 逻辑表达 / 项目经验 / 岗位匹配度` 四维中取一（FR-5）。

### 2.2 失败响应

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 400 | `JD_REQUIRED` | `jobId` 未传且 `jdText` 为空或不足 50 字 | 不创建会话 | AC-4；边界表「JD 为空」 |
| 504 | —（无业务码） | 抽题步骤（端点 1 内）请求超时（10 秒）且重试 1 次仍失败 | 不创建会话 | 边界表「接口超时与重试」；NFR-1 |

统一失败响应体（以 `JD_REQUIRED` 为例，`code` / `requestId` 由统一返回壳输出）：

```json
{
  "code": "JD_REQUIRED",
  "message": "请先输入岗位或粘贴 JD",
  "requestId": "req-mock-m5-err-01"
}
```

## 3. POST `/api/v1/mock-interviews/{sessionId}/answers` 提交单题作答并评分（含抽下一题）

**请求**

- Header：`Authorization: Bearer <JWT>`
- Path：`sessionId` 必须属于当前登录学生，且会话状态为 `IN_PROGRESS`。
- Body：

```json
{
  "questionId": 3,
  "answerText": "当时我们线上订单服务出现超时，我通过日志定位到 ..."
}
```

- `answerText` 不能为空字符串；按关键词命中规则评分，不调大模型（FR-5、Non-Goals）。
- 下一题由本端点响应的 `nextQuestion` 返回，不单独发请求。

### 3.1 `200 OK` 成功 Mock（AC-2，单题得分 + 下一题）

```json
{
  "code": "OK",
  "requestId": "req-mock-m5-answer-01",
  "data": {
    "questionId": 3,
    "score": 72,
    "scoreStatus": "COMPLETE",
    "feedback": "命中关键词：日志定位、订单服务、超时恢复。",
    "hasNextQuestion": true,
    "nextQuestion": {
      "questionId": 4,
      "content": "如果让你重新设计这套方案，你会怎么做？",
      "dimension": "逻辑表达"
    }
  }
}
```

- `score` 为 0–100 整数；`scoreStatus` 取 `COMPLETE` 或 `PARTIAL`（某维度未命中时标 `PARTIAL`，对应 AC-5 / 边界表「评分维度缺项」）。
- `hasNextQuestion = false` 时 `nextQuestion` 为 `null`，前端引导调用端点 3 生成报告。

### 3.2 `200 OK` 极短 / 敷衍回答降级 Mock（AC-5）

用户提交极短回答时结构不变，但 `score` 极低、`scoreStatus` 标 `PARTIAL`、`feedback` 给出展开提示。**这是评分降级，不是超时，不适用 localStorage 草稿口径。**

```json
{
  "code": "OK",
  "requestId": "req-mock-m5-answer-02",
  "data": {
    "questionId": 3,
    "score": 10,
    "scoreStatus": "PARTIAL",
    "feedback": "回答过于简短，建议展开描述",
    "hasNextQuestion": true,
    "nextQuestion": {
      "questionId": 4,
      "content": "如果让你重新设计这套方案，你会怎么做？",
      "dimension": "逻辑表达"
    }
  }
}
```

### 3.3 失败响应

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 404 | —（无业务码） | `sessionId` 不存在或不属于当前学生 | 不评分、不写库 | 通用鉴权约束（不暴露存在性） |
| 409 | —（无业务码） | 会话已归档或已取消，仍尝试提交作答 | 不评分 | 通用鉴权约束（不暴露存在性） |
| 504 | —（无业务码） | 评分接口请求超时（10 秒）且重试 1 次仍失败 | 作答草稿保留在前端 `localStorage`，**不记 0 分** | 边界表「接口超时与重试」；NFR-1 |

## 4. POST `/api/v1/mock-interviews/{sessionId}/report` 生成并归档报告

**请求**：Header 带 JWT，Body 无（以服务端已保存的全部作答记录为准，不重复上传答案）。

### 4.1 `201 Created` 成功 Mock（AC-3，总分 + 四维分）

```json
{
  "code": "OK",
  "requestId": "req-mock-m5-report-01",
  "data": {
    "sessionId": "mi_20261121_001",
    "status": "ARCHIVED",
    "totalScore": 68,
    "dimensionScores": {
      "专业知识": 75,
      "逻辑表达": 62,
      "项目经验": 70,
      "岗位匹配度": 65
    },
    "summary": "逻辑表达维度偏弱，建议用 STAR 结构组织回答。",
    "archivedAt": "2026-11-21T17:30:00+08:00"
  }
}
```

- 报告写入 `mock_interviews` 归档记录后 M8 看板才可读取；多次请求归档不产生重复数据（AC-3 幂等）。
- 四维分数中任一维度缺项时该维度记 0，并在 `summary` 标注「该维度能力有待提升」（AC-5 / 边界表「评分维度缺项」），不伪造分数。

### 4.2 失败响应

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 409 | —（无业务码） | 仍有未作答题目的会话直接请求归档 | 不写归档，返回未答题目列表（字段 `unansweredQuestionIds: number[]`） | 通用鉴权约束（不暴露存在性） |
| 500 | —（无业务码） | 报告同步至 M8 看板时发生异常 | 不产生归档，前端提示「报告归档失败」 | 边界表「报告归档失败」 |
| 504 | —（无业务码） | 归档接口请求超时（10 秒）且重试 1 次仍失败 | 不写归档，会话保持 `IN_PROGRESS` | 边界表「接口超时与重试」；NFR-1 |

## 5. DTO / VO 字段清单（字段名 + 类型 + 说明）

### 5.1 Request 结构

| 字段名 | 类型 | 必填 | 说明 | 归属端点 |
|---|---|---|---|---|
| `jobId` | String | 否 | 优先读取 M4 投递记录（M4 为可选前置）；不传或无记录时走 `jdText` 降级 | 端点 1 |
| `jdText` | String | 否 | JD 文本；`jobId` 未传或无记录时必填，≥50 字（FR-1） | 端点 1 |
| `questionId` | Integer | 是 | 当前作答题目的 ID | 端点 2 |
| `answerText` | String | 是 | 用户输入的作答文本，不可为空字符串 | 端点 2 |

### 5.2 VO 结构

**SessionVO（开启面试，端点 1）**

| 字段名 | 类型 | 说明 |
|---|---|---|
| `sessionId` | String | 会话唯一标识 |
| `status` | String | 会话状态，固定 `IN_PROGRESS` |
| `totalQuestions` | Integer | 总题数，5-8 道 |
| `currentQuestion` | Object | 首题，含 `questionId` / `content` / `dimension` |

**AnswerVO（提交作答，端点 2）**

| 字段名 | 类型 | 说明 |
|---|---|---|
| `questionId` | Integer | 题目 ID |
| `score` | Integer | 0-100 整数得分 |
| `scoreStatus` | String | `COMPLETE` 或 `PARTIAL`（维度未命中 / 极短回答） |
| `feedback` | String | 关键词命中或简短回答提示 |
| `hasNextQuestion` | Boolean | 是否还有下一题 |
| `nextQuestion` | Object | 下一题（`questionId` / `content` / `dimension`），无则 `null` |

**ReportVO（生成报告，端点 3）**

| 字段名 | 类型 | 说明 |
|---|---|---|
| `sessionId` | String | 会话 ID |
| `status` | String | 固定 `ARCHIVED` |
| `totalScore` | Integer | 总分 |
| `dimensionScores` | Object | 四维分项分（专业知识 / 逻辑表达 / 项目经验 / 岗位匹配度） |
| `summary` | String | 评价总结 |
| `archivedAt` | String | 归档时间，ISO 8601 带时区 |

## 6. Java 骨架（契约示意代码块，暂不进入构建）

> 仅固化路由与字段：方法体返回写死 Mock（自包含 `Map`，与 T6 Mock 骨架一致），不注入 Mapper、不写业务规则、不调用大模型。
> **`requestId` 口径（评审阻塞 1）**：下方 Mock 方法显式 `put("requestId", …)` 仅为冻结完整响应结构、避免照抄时漏字段；正式实现中 `requestId` 由 `RequestIdFilter` 每请求生成（8 位十六进制，同值写入请求属性、`X-Request-Id` 响应头与 MDC），Controller 经 `ApiResponse.ok(requestId, data)` / `ApiResponse.error(...)` 由统一返回壳序列化，业务代码不手写固定值。
> 实现分支（骨架 PR 合并后从最新 `main` 新开的 `-impl` 分支）在现有工程上替换为 `Controller → Service → Mapper → Entity` 分层实现并补测试；统一响应壳、全局异常处理已在 `main`，Bean Validation、MapStruct、持久化与构造器注入规范在实现分支接入。

目标落地路径：`src/main/java/com/campus/career/controller/MockInterviewController.java`

```java
package com.campus.career.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T9 / M5 文字模拟面试契约骨架（Mock）。
 * 仅冻结路由与响应字段：方法体写死返回，不连库、不写业务规则、不调用大模型。
 * 本地题库抽题、关键词四维评分、报告归档与 M8 同步归属 Service 层，本类不实现。
 */
@RestController
@RequestMapping("/api/v1/mock-interviews")
public class MockInterviewController {

    /** POST /api/v1/mock-interviews：开启面试并抽首题；Mock 返回会话 + 首题。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> startInterview(@RequestBody Map<String, Object> body) {
        // MOCK：正式实现改为 @Valid @RequestBody StartInterviewRequest。
        // jobId 未传且 jdText 不足 50 字 -> 400 JD_REQUIRED（服务层）。
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionId", 1);
        question.put("content", "请描述一次你解决线上故障的经历。");
        question.put("dimension", "项目经验");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", "mi_20261121_001");
        data.put("status", "IN_PROGRESS");
        data.put("totalQuestions", 5);
        data.put("currentQuestion", question);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", "OK");
        resp.put("requestId", "req-mock-m5-start-01"); // MOCK 固定值仅冻结结构；正式由 ApiResponse.ok 注入
        resp.put("data", data);
        return resp;
    }

    /** POST /api/v1/mock-interviews/{sessionId}/answers：评分并抽下一题；Mock 返回得分 + nextQuestion。 */
    @PostMapping("/{sessionId}/answers")
    public Map<String, Object> submitAnswer(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        // MOCK：极短回答 -> score 极低 + scoreStatus=PARTIAL（见 §3.2）。
        // sessionId 不存在/已归档 -> 404/409；评分超时 -> 504（草稿留 localStorage，不记 0 分）。
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("questionId", 4);
        next.put("content", "如果让你重新设计这套方案，你会怎么做？");
        next.put("dimension", "逻辑表达");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("questionId", 3);
        data.put("score", 72);
        data.put("scoreStatus", "COMPLETE");
        data.put("feedback", "命中关键词：日志定位、订单服务、超时恢复。");
        data.put("hasNextQuestion", true);
        data.put("nextQuestion", next);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", "OK");
        resp.put("requestId", "req-mock-m5-answer-01");
        resp.put("data", data);
        return resp;
    }

    /** POST /api/v1/mock-interviews/{sessionId}/report：生成并归档报告；Mock 返回总分 + 四维分。 */
    @PostMapping("/{sessionId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> generateReport(@PathVariable String sessionId) {
        // MOCK：仍有未作答题 -> 409；同步 M8 异常 -> 500；归档幂等由 Service 保证。
        Map<String, Integer> dimensionScores = new LinkedHashMap<>();
        dimensionScores.put("专业知识", 75);
        dimensionScores.put("逻辑表达", 62);
        dimensionScores.put("项目经验", 70);
        dimensionScores.put("岗位匹配度", 65);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("status", "ARCHIVED");
        data.put("totalScore", 68);
        data.put("dimensionScores", dimensionScores);
        data.put("summary", "逻辑表达维度偏弱，建议用 STAR 结构组织回答。");
        data.put("archivedAt", "2026-11-21T17:30:00+08:00");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", "OK");
        resp.put("requestId", "req-mock-m5-report-01");
        resp.put("data", data);
        return resp;
    }
}
```

DTO / VO 字段契约（md 示意代码块，落地时按类拆到 `dto/interview`、`vo/interview` 包；字段逐项对应 §5 清单）：

```java
package com.campus.career.dto.interview;

/** 开启面试请求：jobId / jdText 二选一。 */
public record StartInterviewRequest(String jobId, String jdText) {
}

/** 提交作答请求。 */
public record SubmitAnswerRequest(Integer questionId, String answerText) {
}
```

```java
package com.campus.career.vo.interview;

import java.util.Map;

/** 开启面试 VO。 */
public record SessionVO(
        String sessionId,
        String status,
        Integer totalQuestions,
        Map<String, Object> currentQuestion) {
}

/** 单题作答 VO。scoreStatus 取 COMPLETE / PARTIAL。 */
public record AnswerVO(
        Integer questionId,
        Integer score,
        String scoreStatus,
        String feedback,
        Boolean hasNextQuestion,
        Map<String, Object> nextQuestion) {
}

/** 报告 VO。四维分项总分值域 0-100。 */
public record ReportVO(
        String sessionId,
        String status,
        Integer totalScore,
        Map<String, Integer> dimensionScores,
        String summary,
        String archivedAt) {
}
```

## 7. 前端页面骨架（Vue 3 + TS + Element Plus，示意代码块，暂不进入构建）

> 红线：`<script setup lang="ts">`、禁用 `any`；正式实现的 HTTP 调用必须经 composable / store，组件不直接 `fetch`/`axios`。当前仅用组件内写死数据做展示骨架，不发真实请求。状态机按 `INIT → IN_PROGRESS → ARCHIVING → COMPLETED` 固化；超时（10 秒 + 重试 1 次）分支把未提交作答暂存 `localStorage`、提示「网络异常，请重试」，重试时恢复，**不记 0 分**；下方 `startMock` / `submitAnswerApi` / `archiveReport` 仅为 composable 占位。

**组件树**

- `MockInterviewView.vue`（主页面，承载状态机）
  - `InterviewInitPanel.vue`（初始化面板：岗位选择、JD 文本域、[开始面试] 按钮）— `INIT`
  - `InterviewSessionPanel.vue`（面试会话页：进度条、题目渲染、**作答区**、`[提交作答]` 按钮）— `IN_PROGRESS`
  - `ReportArchivingPanel.vue`（报告生成中骨架屏）— `ARCHIVING`
  - `InterviewReportPanel.vue`（**报告页**：总分、四维雷达图、评价、`[归档至求职档案]`）— `COMPLETED`
  - `ArchiveFailureAlert.vue`（**归档失败提示**：`500` / `504` 时提示「报告归档失败，请稍后重试」并保留本地报告数据）

**状态机与交互逻辑**

1. `INIT`：展示 `InterviewInitPanel`；用户填写岗位 / JD 点击「开始面试」→ 调端点 1（`POST /mock-interviews`）。
2. `IN_PROGRESS`：展示 `InterviewSessionPanel`；用户在作答区输入并点「提交作答」→ 调端点 2（`POST /answers`）。
   - 成功且 `hasNextQuestion = true`：当前题刷新为 `nextQuestion`，进度 +1。
   - 成功且 `hasNextQuestion = false`：自动进入 `ARCHIVING`。
   - 超时（10 秒、重试 1 次仍失败）：中断 loading，提示「网络异常，请重试」，把 `answerText` 存入 `localStorage`；重试时恢复草稿，**不得直接记为 0 分**（AC-6）。
3. `ARCHIVING`：展示 `ReportArchivingPanel` 骨架屏；调端点 3（`POST /report`）。
4. `COMPLETED`：展示 `InterviewReportPanel`，渲染总分、四维雷达图与总结，提示「已归档至看板」。
   - 归档失败（`500` 同步异常 / `504` 超时）：展示 `ArchiveFailureAlert`，本地缓存报告数据，可重试，不写半条归档。

目标落地路径：`src/views/interview/MockInterviewView.vue`

```vue
<script setup lang="ts">
import { ref } from 'vue'

type ViewState = 'INIT' | 'IN_PROGRESS' | 'ARCHIVING' | 'COMPLETED'
type Dimension = '专业知识' | '逻辑表达' | '项目经验' | '岗位匹配度'

interface Question {
  questionId: number
  content: string
  dimension: Dimension
}

interface AnswerResult {
  questionId: number
  score: number
  scoreStatus: 'COMPLETE' | 'PARTIAL'
  feedback: string
  hasNextQuestion: boolean
  nextQuestion: Question | null
}

interface Report {
  sessionId: string
  status: string
  totalScore: number
  dimensionScores: Record<Dimension, number>
  summary: string
  archivedAt: string
}

const currentState = ref<ViewState>('INIT')
const sessionId = ref<string | null>(null)
const jdText = ref('')
const answerText = ref('')
const currentQuestion = ref<Question | null>(null)
const progress = ref({ current: 1, total: 5 })
const report = ref<Report | null>(null)
const archiveFailed = ref(false)

// MOCK：写死首题；正式实现经 composable 调用 POST /api/v1/mock-interviews。
async function startMock(): Promise<void> {
  currentQuestion.value = {
    questionId: 1,
    content: '请描述一次你解决线上故障的经历。',
    dimension: '项目经验'
  }
  sessionId.value = 'mi_20261121_001'
  currentState.value = 'IN_PROGRESS'
}

// MOCK 占位：正式实现由 composable 调用 POST /{sessionId}/answers。
// 10 秒超时 + 重试 1 次仍失败时抛错，调用方把 answerText 存 localStorage、提示「网络异常，请重试」，不记 0 分。
function submitAnswerApi(session: string, questionId: number, text: string): Promise<AnswerResult> {
  return Promise.resolve({
    questionId,
    score: 72,
    scoreStatus: 'COMPLETE',
    feedback: '命中关键词：日志定位、订单服务、超时恢复。',
    hasNextQuestion: true,
    nextQuestion: {
      questionId: 4,
      content: '如果让你重新设计这套方案，你会怎么做？',
      dimension: '逻辑表达'
    }
  })
}

async function submitAnswer(): Promise<void> {
  if (!sessionId.value || !currentQuestion.value) return
  const draft = answerText.value
  try {
    const result = await submitAnswerApi(sessionId.value, currentQuestion.value.questionId, draft)
    localStorage.removeItem('mock-answer-draft')
    if (result.hasNextQuestion && result.nextQuestion) {
      currentQuestion.value = result.nextQuestion
      progress.value.current += 1
    } else {
      currentState.value = 'ARCHIVING'
      await archiveReport()
    }
  } catch (error: any) {
    // 评审建议：按状态码分流
    if (error?.response?.status === 504 || error?.message === 'timeout') {
      localStorage.setItem('mock-answer-draft', draft) // AC-6：504超时草稿保留
      // ElMessage.error('网络异常，请重试') // 示意
    } else {
      // 404 / 409 等：提示会话失效，不再存草稿
      // ElMessage.error('会话已失效，请重新开始') // 示意
      currentState.value = 'INIT'
    }
  }
}

// MOCK 占位：正式实现由 composable 调用 POST /{sessionId}/report。
async function archiveReport(): Promise<void> {
  try {
    report.value = {
      sessionId: sessionId.value ?? '',
      status: 'ARCHIVED',
      totalScore: 68,
      dimensionScores: { 专业知识: 75, 逻辑表达: 62, 项目经验: 70, 岗位匹配度: 65 },
      summary: '逻辑表达维度偏弱，建议用 STAR 结构组织回答。',
      archivedAt: '2026-11-21T17:30:00+08:00'
    }
    archiveFailed.value = false
    currentState.value = 'COMPLETED'
  } catch {
    archiveFailed.value = true // 500 / 504：归档失败提示，本地保留报告数据
  }
}

function retryArchive(): void {
  void archiveReport()
}
</script>

<template>
  <section class="mock-interview">
    <div v-if="currentState === 'INIT'">
      <el-input v-model="jdText" type="textarea" :rows="4" placeholder="粘贴 JD 文本（≥50 字）..." />
      <el-button type="primary" @click="startMock">开始面试</el-button>
    </div>

    <div v-else-if="currentState === 'IN_PROGRESS'">
      <el-progress :percentage="Math.round(progress.current / progress.total * 100)" />
      <h3>题目 {{ progress.current }} / {{ progress.total }}：{{ currentQuestion?.content }}</h3>
      <el-input v-model="answerText" type="textarea" :rows="6" placeholder="输入你的回答..." />
      <el-button type="primary" @click="submitAnswer">提交作答</el-button>
    </div>

    <div v-else-if="currentState === 'ARCHIVING'">
      <el-skeleton :rows="4" animated />
      <p>报告生成中，请稍候...</p>
    </div>

    <div v-else-if="currentState === 'COMPLETED' && report">
      <h2>面试报告</h2>
      <p>总分：{{ report.totalScore }}</p>
      <ul>
        <li v-for="(score, dim) in report.dimensionScores" :key="dim">{{ dim }}：{{ score }}</li>
      </ul>
      <p>{{ report.summary }}</p>
      <el-alert v-if="archiveFailed" type="error" show-icon title="报告归档失败，请稍后重试">
        <el-button size="small" @click="retryArchive">重试归档</el-button>
      </el-alert>
      <el-alert v-else type="success" :closable="false" title="已归档至求职档案（M8 看板只读）" />
    </div>
  </section>
</template>
```

## 8. 非功能约束与 AC 覆盖说明

**NFR-1 时间口径（用户确认与 module-05 NFR-1 原文一致，不改动）**

- 抽题与评分**正常响应时间不超过 1 秒**（NFR-1）。
- 接口**超时阈值 10 秒，失败重试 1 次**；仍失败按各端点失败响应处理（§2.2 / §3.3 / §4.2）。
- 两个数字含义不同：1 秒是正常态响应目标，10 秒是超时阈值，不可混用。

**错误码全量口径**

- M5 唯一大写业务错误码为 `JD_REQUIRED`（400，AC-4）。
- 其余 `404 / 409 / 500 / 504` 均为**通用状态机与参数校验约束，不设独立错误码**；超时与重试按 NFR-1 走 504 通用约束，前端通过 HTTP 状态码结合 `message` 提示区分（§2.2 / §3.3 / §4.2）。
- spec 明确本模块不调用大模型，不得出现 `LLM_TIMEOUT`。

**AC 覆盖对照**

| AC | 要求 | 本文件落点 |
|---|---|---|
| AC-1 | 成功开启面试 | §2.1 `201` 会话 ID + 首题 |
| AC-2 | 单题评分 | §3.1 `200` 得分 + 下一题 |
| AC-3 | 报告归档 | §4.1 `201` 总分 + 四维分；§4.1 幂等说明 |
| AC-4 | JD 为空拦截 | §2.2 `400 JD_REQUIRED` |
| AC-5 | 回答极短 → 提示并给低分 | §3.2 极短回答降级 Mock（`score=10`、`PARTIAL`、「回答过于简短，建议展开描述」）；§4.1 四维缺项记 0 |
| AC-6 | 超时重试 1 次 → 暂存 localStorage → 不记 0 分 | §3.3 `504` 数据影响、§7 状态机超时分支、§7 Vue `catch` 中 `localStorage.setItem('mock-answer-draft', draft)` |

## 9. 边界与待接续清单（不越界）

- 本 PR 只交付 docs 内 Mock 契约与骨架代码块：**不连库、不写业务规则、不调用大模型、不改动 `spec/` 与 `docs/architecture.md` 冻结契约、不改 `db/`**（FR-5 / Non-Goals）。
- 骨架 PR 按 2026-11-24 口径经 APPROVE 后合入 `main`；第 10 周在从最新 `main` 新开的 `feature/issue-114-m5-mock-session-impl` 分支补：可编译的 Controller/DTO/VO、Bean Validation（`jdText` ≥50 字）、Service 层本地题库抽题与关键词四维评分、归档幂等、MyBatis-Plus 实体/Mapper、MapStruct、构造器注入、前端工程内页面与 composable/store。统一返回壳（`ApiResponse` / `RequestIdFilter` / `GlobalExceptionHandler`）已在 `main`，实现分支直接使用。
- 依赖 M4（投递记录）为可选前置：`jobId` 无记录时降级手填 `jdText`，不阻断流程（FR-1）。
- 归档只写 M5 归档记录、同步 M8 只读看板，不反向依赖、不在看板内编辑（§4.5.3 越界声明）。
- **待架构变更项（本 PR 不夹带）**：评审提出将「抽题」列为独立端点；冻结契约 §4.5 当前无独立抽题 URL，本骨架按「不改契约」口径未新增。若确认需要，须在架构变更中新增 ADR 后，再于实现分支补端点与契约文档。
- 实现分支（`-impl`）计划补测（接口层单测 / 前端页面测试）：`JD_REQUIRED`（400）；极短回答 `scoreStatus=PARTIAL`、低分与提示文案（AC-5）；四维缺项归档记 0 分并标注；404/409 鉴权与状态机约束；10 秒超时 + 重试 1 次、草稿恢复且**不记 0 分**（AC-6）；归档幂等（多次请求不重复写入）；归档失败 `500` / `504` 的本地缓存与重试提示。
