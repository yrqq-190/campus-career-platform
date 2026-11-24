# T9：M5 文字模拟面试、四维评分与报告归档（Mock 骨架）

> 对应 Issue：#114｜分支：`feature/issue-114-m5-mock-session`
> 对应 spec/AC：`spec/module-05-mock-interview.md` AC-1、AC-2、AC-3、AC-4、AC-5、AC-6
> 契约来源：`docs/architecture.md` §4.5（冻结契约，本文件只做 Mock 落地，不改契约、不新增 spec 之外的业务错误码）
> 形态说明：本 PR 为 **docs-only 契约骨架 + 写死 Mock**（代码块仅作示意，不进入构建），不连库、不写业务规则，不调用大模型（严格使用本地题库与关键词匹配）。

## 1. 端点总览（architecture §4.5）

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | POST | `/api/v1/mock-interviews` | 开始面试，生成题库题目 | AC-1、AC-4 |
| 2 | POST | `/api/v1/mock-interviews/{sessionId}/answers` | 提交单题文字作答并评分 | AC-2、AC-5、AC-6 |
| 3 | POST | `/api/v1/mock-interviews/{sessionId}/report` | 生成并归档最终报告 | AC-3、AC-6 |

## 2. DTO / VO 字段清单

### 2.1 Request 结构
| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `jobId` | String | 否 | 优先读取 M4 投递记录（M4 为可选前置）。 |
| `jdText` | String | 否 | JD 文本。若 `jobId` 未传或无记录，则此项必填（≥50 字）。 |
| `questionId` | Integer | 是 | (提交答案时) 当前题目的 ID。 |
| `answerText`| String | 是 | (提交答案时) 用户输入的作答文本。 |

### 2.2 VO 结构
- **SessionVO (开始面试)**
  - `sessionId` (String): 会话唯一标识。
  - `status` (String): 会话状态（`IN_PROGRESS`）。
  - `totalQuestions` (Integer): 总题数（5-8道）。
  - `currentQuestion` (Object): 包含 `questionId`、`content`、`dimension`。
- **AnswerVO (提交作答)**
  - `questionId` (Integer): 题目 ID。
  - `score` (Integer): 0-100 整数。
  - `scoreStatus` (String): `COMPLETE` 或 `PARTIAL`（维度未命中关键词）。
  - `feedback` (String): 关键词命中反馈。
  - `hasNextQuestion` (Boolean): 是否还有下一题。
  - `nextQuestion` (Object): 下一题信息，若无则为 `null`。
- **ReportVO (生成报告)**
  - `sessionId` (String): 会话 ID。
  - `status` (String): `ARCHIVED`。
  - `totalScore` (Integer): 总分。
  - `dimensionScores` (Object): 四维分项分（专业知识、逻辑表达、项目经验、岗位匹配度）。
  - `summary` (String): 评价总结。
  - `archivedAt` (String): 归档时间。

## 3. Mock JSON 示例

### 3.1 成功响应 201 Created（开始面试，AC-1）
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

### 3.2 成功响应 200 OK（单题作答与评分，AC-2 / AC-5）
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
*注（AC-5）：若用户提交极短/敷衍回答，返回结构不变，但 `score` 极低（如 10 分），`feedback` 为“回答过于简短，建议展开描述”，`scoreStatus` 标为 `PARTIAL`。*

### 3.3 成功响应 201 Created（生成报告并归档，AC-3）
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

### 3.4 失败响应 400 JD_REQUIRED（AC-4）
```json
{
  "code": "JD_REQUIRED",
  "message": "请先输入岗位或粘贴 JD",
  "requestId": "req-mock-m5-err-01"
}
```

### 3.5 失败响应 504 INTERFACE_TIMEOUT（AC-6）
```json
{
  "code": "-",
  "message": "网络异常，请重试",
  "requestId": "req-mock-m5-err-02"
}
```

## 4. 错误码表

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 400 | `JD_REQUIRED` | `jobId` 未传且 `jdText` 为空或不足 50 字 | 不创建会话 | AC-4 |
| 504 | -（通用约束） | 抽题/评分/归档接口请求超时（10 秒）且重试 1 次仍失败 | 不写库，草稿保留在前端 `localStorage` | NFR-1，架构 §4.5 |

## 5. Vue 前端页面结构与状态说明

### 5.1 组件树
- `MockInterviewView.vue` (主页面)
  - `InterviewInitPanel.vue` (初始化面板：岗位选择、JD 文本域、开始按钮)
  - `InterviewSessionPanel.vue` (答题面板：进度条、题目渲染、作答输入、提交按钮)
  - `InterviewReportPanel.vue` (报告面板：总分、雷达图、评价、归档提示)

### 5.2 状态机与交互逻辑
1. **`INIT` (准备阶段)**：展示 `InterviewInitPanel`。用户填写信息点击“开始”后，调用 `POST /mock-interviews`。
2. **`IN_PROGRESS` (答题阶段)**：展示 `InterviewSessionPanel`。
   - 用户填写答案点击提交，调用 `POST /answers`。
   - 若成功且 `hasNextQuestion = true`，刷新当前题目为 `nextQuestion`。
   - 若 `hasNextQuestion = false`，自动进入 `ARCHIVING` 阶段。
3. **`ARCHIVING` (报告生成)**：调用 `POST /report`。
4. **`COMPLETED` (完成阶段)**：展示 `InterviewReportPanel`。渲染总分、四维雷达图和总结，提示“已归档至看板”。
   - **异常分支 (AC-6)**：遇到超时，提示“网络异常”，未提交成功的作答文本存入 `localStorage`，用户点击重试时从缓存恢复。

## 6. 关键约束与 AC 验证说明

- **不调大模型**：本模块使用本地题库与关键词匹配评分（FR-5 / Non-Goals）。
- **超时与重试（AC-6）**：接口请求超时定为 10 秒，失败重试 1 次。超时后前端中断 loading，提示“网络异常，请重试”，并暂存作答草稿到前端 `localStorage`。**不得直接记为 0 分**。
- **极短回答（AC-5）**：若用户提交极短/敷衍回答，系统给出低分，并提示“回答过于简短，建议展开描述”。
- **归档幂等（AC-3）**：报告写入归档记录后同步至 M8 看板，多次请求归档不会产生重复归档数据。

## 7. 后续真实代码参考

### 7.1 Controller 骨架
*(第 10 周脚手架合并后移入 `src/main/java/.../controller`，当前仅作示意)*
```java
@RestController
@RequestMapping("/api/v1/mock-interviews")
@RequiredArgsConstructor
public class MockInterviewController {

    private final MockInterviewService mockInterviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<SessionVO>> startInterview(
            @RequestBody @Valid StartInterviewRequest request) {
        SessionVO data = mockInterviewService.startInterview(request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
    
    @PostMapping("/{sessionId}/answers")
    public ResponseEntity<ApiResponse<AnswerVO>> submitAnswer(
            @PathVariable String sessionId, 
            @RequestBody @Valid SubmitAnswerRequest request) {
        AnswerVO data = mockInterviewService.submitAnswer(sessionId, request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/{sessionId}/report")
    public ResponseEntity<ApiResponse<ReportVO>> generateReport(
            @PathVariable String sessionId) {
        ReportVO data = mockInterviewService.generateReport(sessionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }
}
```

### 7.2 Vue 视图骨架（Mock 版）
*(第 10 周移入 `frontend/src/views/interview`，当前仅作示意)*
```vue
<script setup lang="ts">
import { ref } from 'vue'

type ViewState = 'INIT' | 'IN_PROGRESS' | 'ARCHIVING' | 'COMPLETED'
const currentState = ref<ViewState>('INIT')
const sessionId = ref<string | null>(null)

// Mock 开始面试
const handleStart = async () => {
  // 模拟 API 调用
  currentState.value = 'IN_PROGRESS'
  sessionId.value = 'mock_session_001'
}

// Mock 提交作答
const handleSubmitAnswer = async () => {
  // 模拟 API 调用与超时逻辑
  currentState.value = 'ARCHIVING'
}

// Mock 生成报告
const handleGenerateReport = async () => {
  currentState.value = 'COMPLETED'
}
</script>

<template>
  <div class="mock-interview-container">
    <div v-if="currentState === 'INIT'">
      <el-input type="textarea" placeholder="粘贴 JD 文本..." />
      <el-button type="primary" @click="handleStart">开始面试</el-button>
    </div>

    <div v-if="currentState === 'IN_PROGRESS'">
      <p>进度: 1 / 5</p>
      <h3>题目: 请描述一次你解决线上故障的经历。</h3>
      <el-input type="textarea" placeholder="输入你的回答..." />
      <el-button type="primary" @click="handleSubmitAnswer">提交作答</el-button>
    </div>

    <div v-if="currentState === 'ARCHIVING'">
      <el-skeleton :rows="4" animated />
      <p>报告生成中，请稍候...</p>
    </div>

    <div v-if="currentState === 'COMPLETED'">
      <h2>面试报告</h2>
      <p>总分: 68</p>
      <el-button @click="handleGenerateReport">归档至求职档案</el-button>
    </div>
  </div>
</template>
```
