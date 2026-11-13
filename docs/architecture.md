# MVP1 架构设计（校级学生求职发展平台）

> 第 8 周交付物 · 主笔：陈雪 · 版本 v0.1（D1 初稿）
> 上游依据：spec/mvp1-scope.md、spec/module-01~08（8 份）
> 说明：第 1–3 章、第 6 章由陈雪完成；第 4 章契约由各模块主笔按骨架补齐。

## 1. 模块边界与职责

| 模块 | 职责 | 不负责 | 对应 spec | 主笔 |
|---|---|---|---|---|
| M1 简历上传与解析 | 上传校验（类型白名单、≤10MB）、文本抽取、大模型结构化解析、结果确认后入库 | 不做岗位匹配；不生成画像；不做版式还原 | module-01 | 贾小舒 |
| M2 霍兰德问卷与画像 | 30 题问卷、计分、六维画像生成与展示 | 不推荐岗位；不算匹配度 | module-02 | 贾小舒 |
| M3 岗位推荐与外链 | 按画像返回岗位列表、匹配度排序、外链跳转与失效标记 | 不登记投递；不抓取岗位正文入库（外链只读） | module-03 | 赵蔚 |
| M4 投递状态登记 | 登记投递、状态流转（已投递→已约面→已面试→已结束）、重复提交幂等 | 不发提醒；不生成面试日程；不判断该不该投 | module-04 | 赵蔚 |
| M5 文字模拟面试 | 生成面试问题、接收回答、四维评分与反馈、归档 | 不排真实面试；不发提醒；不做语音 | module-05 | 卢果 |
| M6 面试登记与多级提醒 | 登记面试（公司/岗位/开始时间/出发时间/地点）、生成 5 次提醒、改期失效、发送失败重试 | 不记录面试结果；不算匹配度 | module-06 | 卢果 |
| M7 面经外链 | 面经链接收录、分组展示、24 小时失效探测、点击 3 秒判失效 | 不抓正文；不做全文检索；不评分 | module-07 | 熊尚华 |
| M8 求职档案看板 | 聚合展示投递漏斗、面试日程、复盘统计，只读 | 不写业务表；不判断规则；不做业务编辑 | module-08 | 熊尚华 |

## 2. 架构全景（四层）

展示层（Vue 3 + Vite + TypeScript + Pinia + Element Plus）
  ↓ HTTP / JSON
API 层（Spring Boot 3.x Controller）
  ↓ 调用 Service
服务层（Service · 业务规则唯一归属地）
  ↓ 调用 Mapper
数据层（MyBatis-Plus + MySQL 8.0 · RDS 独立部署）

| 层 | 职责 | 关键组件 |
|---|---|---|
| 展示层 | UI 交互与数据展示，只消费 API 响应 | 简历上传页、问卷页、岗位推荐页、投递看板、模拟面试页、面试登记页、面经页、档案看板 |
| API 层 | 接收请求、参数校验（Bean Validation）、JWT 鉴权、统一响应体与错误码、限流 | 8 个 Controller，按 M1–M8 一一对应 |
| 服务层 | 核心业务规则：10MB 校验、画像计分、匹配排序、投递状态机与幂等、5 节点提醒计算、四维评分、看板聚合 | 8 个 Service；事务边界只在这一层 |
| 数据层 | 持久化与增删改查，RDS 独立隔离，不与业务代码混部 | 8 个 Mapper + 只读视图 v_dashboard |

**四条层间约束（不可违反）**

1. 展示层只消费 API 响应，不判断业务规则（如不在页面判断「是否超过投递上限」）
2. API 层不写业务规则，只做校验、鉴权、编排
3. 服务层不渲染页面、不拼接 UI 文案结构
4. 数据层不处理 UI、不做业务判断

**依赖方向**：展示层 → API 层 → 服务层 → 数据层，单向，禁止反向与循环；展示层禁止直连数据层。

## 3. 核心数据实体

| 实体 | 说明 | 关键约束 |
|---|---|---|
| student | 学生账号 | 学号唯一 |
| resume | 简历与解析结果 | 每个学生一份有效简历 |
| holland_profile | 霍兰德六维画像 | 与 student 一对一 |
| job | 岗位外链元数据 | 外链失效标记 |
| application | 投递记录 | 唯一约束 (student_id, job_id)，防重复投递 |
| interview | 面试登记 | 含开始时间与出发时间 |
| reminder | 提醒任务 | 唯一约束 (interview_id, node_type)，防重复触发 |
| interview_note | 面经外链 | 失效探测时间戳 |

> M8 档案看板不建业务表，读取只读视图 `v_dashboard`。

## 4. 接口契约

> 每个模块必须写全 5 要素：URL / 请求 / 成功响应 / 失败响应 / 错误码。
> 只写成功不写失败的一律打回。
### 4.1 M1 简历上传与解析（主笔：贾小舒）

> 依据 `spec/module-01-resume-parse.md`。M1 不调用 M2–M8 任何模块；只被 M2 读取「已确认」的档案。
> 错误码全部取自该 spec 的 AC-3 / AC-5 / AC-6 / AC-7 / AC-8 与「边界与异常」表，**不新增表中没有的码**。
> 分层归属：扩展名白名单、10MB 上限、文件可读性判定属**服务层**（第 2 章「服务层」已明确含 10MB 校验）；multipart 结构合法性属 **API 层参数校验**，不承载业务规则。

**端点总览**

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | POST | `/api/v1/resumes` | 上传并解析，返回暂存区结果 | AC-1、AC-3、AC-4、AC-5～AC-8 |
| 2 | POST | `/api/v1/resumes/confirm` | 学生修改后确认入库 | AC-2、AC-9 |
| 3 | GET | `/api/v1/resumes/current` | 读取当前已确认档案 | AC-2、AC-9 |

#### 4.1.1 上传并解析

**URL**：`POST /api/v1/resumes`

**请求**

- Header：`Authorization: Bearer <JWT>`；`Content-Type: multipart/form-data`
- Body：`file` 单文件；扩展名 ∈ {`pdf`, `docx`}；实际文件类型须与扩展名一致；大小 ≤ `MAX_RESUME_SIZE = 10MB`

**成功响应**：`201 Created`

```json
{
  "code": "OK",
  "requestId": "6b2f8c1e",
  "data": {
    "resumeId": "r_20261112_001",
    "status": "PENDING_CONFIRM",
    "parse": {
      "education":   [{ "school": "XX大学", "major": "软件工程", "degree": "本科", "startDate": "2022-09", "endDate": "2026-06" }],
      "skills":      ["Java", "SQL"],
      "projects":    [{ "name": "校园二手交易", "role": "后端", "description": "..." }],
      "internships": []
    }
  }
}
```

- 四类字段**恒存在**，简历中没有的类别返回空数组而非省略（AC-4）。
- 本次只写暂存区，正式档案不变，`status = PENDING_CONFIRM`（AC-1）；未确认内容其他模块不得读取（FR-6）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 415 | `RESUME_FORMAT_UNSUPPORTED` | 非 PDF / Word；或扩展名 .pdf 但实际是图片、压缩包 | 不落盘、不建档 | AC-5；边界行 1、2 |
| 415 | `RESUME_UNREADABLE` | Word 设了打开密码；或文件 0 字节、已损坏 | 不落盘、不建档 | AC-5；边界行 3、4 |
| 413 | `RESUME_TOO_LARGE` | 文件 > 10MB（恰好 10MB 放行） | 不落盘、不建档 | AC-3；边界行 5 |
| 504 | `PARSE_TIMEOUT` | 大模型 30 秒超时、重试 2 次后仍失败 | 不落盘，原档案不变 | AC-6；边界行 6 |
| 502 | `MODEL_OUTPUT_INVALID` | 模型输出非合法 JSON，或未过 JSON Schema 校验 | 不展示半成品，原档案不变 | AC-7；边界行 7 |
| 422 | `NO_VALID_FIELD` | 解析流程完成但四类字段全空 | 不生成空档案 | AC-8；边界行 8 |

统一失败响应体：

```json
{ "code": "RESUME_TOO_LARGE", "message": "单份简历不超过 10MB", "requestId": "6b2f8c1e" }
```

> 上表 6 个码即 M1 的全部业务错误码，与 spec「边界与异常」表逐行一一对应，无新增码。任务要求的 415 / 413 / 504 / 502 / 422 五个状态码均在本端点覆盖。

#### 4.1.2 确认入库

**URL**：`POST /api/v1/resumes/confirm`

**请求**

```json
{
  "resumeId": "r_20261112_001",
  "education":   [ ... ],
  "skills":      ["Java", "Spring Boot"],
  "projects":    [ ... ],
  "internships": []
}
```

学生可在确认页逐项修改、删除或新增条目，以提交内容为准写入（AC-2）。

**成功响应**：`200 OK`

```json
{
  "code": "OK",
  "requestId": "9a71d0f2",
  "data": { "status": "CONFIRMED", "confirmedAt": "2026-11-12T15:00:00+08:00", "skillCount": 2 }
}
```

新内容整体替换旧档案，不保存、不展示、不比对历史版本（FR-7）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 422 | `NO_VALID_FIELD` | 学生把四类字段全部删空后提交 | 不写库、不生成空档案，原档案不变 | FR-5；AC-8；边界行 8 |

> 本端点不调用大模型，故不出现 504 / 502。入库失败时原档案保持上传前状态（AC-9）。

#### 4.1.3 读取当前档案

**URL**：`GET /api/v1/resumes/current`

**请求**：Header 带 JWT，无 body。

**成功响应**：`200 OK`

已建档返回四类字段与 `status = CONFIRMED`；未建档返回 `"data": null`。**空态不是错误**，不返回 404。

**失败响应**：仅 401 鉴权失败（API 层统一拦截，非业务错误码），无业务失败分支。

> **越界声明**：本节不定义画像生成（M2）、岗位匹配（M3）、面试与提醒（M6）接口。M1 不向下游推送数据，由 M2 主动读取已确认档案。

### 4.2 M2 霍兰德问卷与画像（主笔：贾小舒）

> 依据 `spec/module-02-holland-profile.md`。M2 读取 M1 已确认档案，不调用 M3–M8；被 M3、M5 读取画像。
> 业务错误码仅 3 个：`QUIZ_INCOMPLETE`、`QUIZ_DUPLICATE_SUBMIT`、`SCORE_NOT_TRACEABLE`；另有 `PROFILE_SKILL_MISSING`，它是 200 响应中的**降级标记，不是错误**（AC-5）。
> 分层归属：计分、追溯校验、画像合成全部在**服务层**（第 2 章已明确「画像计分属服务层」）；API 层只做参数与鉴权。

**端点总览**

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | GET | `/api/v1/holland/questions` | 取 30 题与已存作答（续答） | AC-4、FR-1、FR-2 |
| 2 | PUT | `/api/v1/holland/answers` | 单题即时保存 | FR-2、AC-4 |
| 3 | POST | `/api/v1/holland/submissions` | 提交、计分、生成画像 | AC-1、AC-2、AC-5～AC-8 |
| 4 | GET | `/api/v1/holland/profile` | 读取当前画像 | AC-2、AC-3、AC-5 |
| 5 | POST | `/api/v1/holland/profile/regenerate` | 重新生成并整体替换 | AC-3 |

#### 4.2.1 获取题目与已保存作答

**URL**：`GET /api/v1/holland/questions`

**请求**：Header 带 JWT，无 body。

**成功响应**：`200 OK`

```json
{
  "code": "OK",
  "data": {
    "questions": [
      { "id": 1, "text": "...", "dimension": "R", "options": ["非常不符合", "不太符合", "比较符合", "非常符合"] }
    ],
    "answers": { "1": 2, "2": 4 },
    "answeredCount": 2,
    "submitted": false
  }
}
```

题目内容与顺序固定，不随机抽题（FR-1）；`answers` 回填已保存选项，支持中途退出后从断点续答（AC-4）。

**失败响应**：仅 401 鉴权失败，无业务失败分支。

#### 4.2.2 单题即时保存

**URL**：`PUT /api/v1/holland/answers`

**请求**

```json
{ "questionId": 18, "option": 3 }
```

`questionId` ∈ 1–30，`option` ∈ 1–4。

**成功响应**：`200 OK`

```json
{ "code": "OK", "data": { "saved": true, "answeredCount": 17 } }
```

每次选择即时保存，无需显式「保存草稿」按钮（FR-2）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 说明 |
|---|---|---|---|---|
| 400 | —（无业务码） | `questionId` 不在 1–30，或 `option` 不在 1–4 | 不写库 | API 层 Bean Validation 统一返回，不新增业务错误码 |
| 409 | `QUIZ_DUPLICATE_SUBMIT` | 问卷已提交后再次修改作答 | 不覆盖已提交版本 | AC-7 |

#### 4.2.3 提交问卷并生成画像

**URL**：`POST /api/v1/holland/submissions`

**请求**：Header 带 JWT，无 body（以服务端已保存作答为准，避免前端重复上传答案造成不一致）。

**成功响应**：`201 Created`

```json
{
  "code": "OK",
  "requestId": "c31e77a0",
  "data": {
    "scores": { "R": 72, "I": 85, "A": 40, "S": 63, "E": 58, "C": 49 },
    "profile": {
      "directions": [{ "name": "后端开发", "reason": "I 维度 85，命中 Java/SQL 技能" }],
      "cities": ["杭州", "苏州"],
      "industries": ["互联网"],
      "skillGaps": [{ "skill": "分布式缓存", "reason": "目标方向要求 Redis" }]
    }
  }
}
```

- 六维分数 0–100，每个维度均可回溯到具体作答（AC-1、FR-4）。
- 画像四项齐全，方向 3–5 个且各带支撑理由，短板 ≤ 5 项（AC-2、FR-6）。
- 当正式档案缺技能字段时，`skillGaps` 返回 `{ "status": "PROFILE_SKILL_MISSING" }`，**不是空数组，也不报错**，兴趣维度、城市、行业照常返回（AC-5、FR-8）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 422 | `QUIZ_INCOMPLETE` | 30 题未全部作答 | 不生成任何画像数据，已答题目保留 | AC-6；边界行 1 |
| 409 | `QUIZ_DUPLICATE_SUBMIT` | 已成功提交后再次提交 | 画像仍为一份，不产生第二份 | AC-7；边界行 2 |
| 500 | `SCORE_NOT_TRACEABLE` | 某维度得分无法回溯到任何作答 | 本次画像不对外提供，不展示部分错误分数 | AC-8；边界行 6 |

422 的响应体需带未答题号：

```json
{ "code": "QUIZ_INCOMPLETE", "message": "还有 20 题未作答", "requestId": "c31e77a0", "missing": [11,12,"...",30] }
```

#### 4.2.4 读取当前画像

**URL**：`GET /api/v1/holland/profile`

**请求**：Header 带 JWT，无 body。

**成功响应**：`200 OK`

一张画像整体返回（AC-3 保证同一时刻只有一份）；`skillGaps` 可能为 `PROFILE_SKILL_MISSING` 标记（AC-5）。尚未生成画像时返回 `"data": null`，空态不报错。

**失败响应**：仅 401 鉴权失败，无业务失败分支。

#### 4.2.5 重新生成画像

**URL**：`POST /api/v1/holland/profile/regenerate`

**请求**：Header 带 JWT，无 body。用于简历更新并确认后重新生成（US-3）。

**成功响应**：`200 OK`

返回新画像，旧画像整体替换、不再存在，不提供新旧对比视图（AC-3、FR-7）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 422 | `QUIZ_INCOMPLETE` | 问卷尚未提交就请求生成画像 | 不生成画像 | AC-6；边界行 8 |
| 500 | `SCORE_NOT_TRACEABLE` | 某维度得分无法回溯到任何作答 | 本次画像不对外提供 | AC-8；边界行 6 |

> **越界声明**：M2 不返回岗位卡片、匹配度与推荐排序（属 M3），不生成面试问题与评分（属 M5）。画像以固定字段结构对外提供，其他模块按字段名取值，不依赖自然语言描述做二次解析（FR-5）。

### 4.3 M3 岗位推荐与外链（主笔：赵蔚）

> 依据 `spec/module-03-job-match.md`。M3 读取 M2 已生成的画像，不调用 M4、M5；不抓取岗位正文入库，仅做外链跳转与失效标记。
> 业务错误码仅 1 个：`JOB_LINK_INVALID` (404)；`PROFILE_SKILL_MISSING` 为 200 降级标记，不是错误码。
> 分层归属：画像读取与推荐排序在**服务层**；API 层只做参数校验与鉴权。

**端点总览**

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | GET | `/api/v1/jobs/recommendations` | 获取岗位推荐列表 | AC-1、AC-2、AC-3 |

#### 4.3.1 获取岗位推荐列表

**URL**: `GET /api/v1/jobs/recommendations`

**请求**: Header 带 JWT，无 body。

**成功响应**: `200 OK`

```json
{
  "code": "OK",
  "data": [
    {
      "jobId": "J001",
      "title": "前端开发实习生",
      "reason": "命中技能：Vue、JavaScript",
      "sourceUrl": "https://...",
      "isDemo": false
    }
  ]
}
```

无画像无技能标签时，`data` 返回空数组 `[]`，同时以 200 状态返回降级标记 `PROFILE_SKILL_MISSING`（AC-2），不报错。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 401 | -（无业务码） | 未登录或 Token 失效 | 不返回数据 | 通用鉴权 |
| 404 | `JOB_LINK_INVALID` | 岗位外链失效 | 不返回数据 | AC-3 |

> **越界声明**：M3 不负责投递状态登记（属 M4），不负责抓取外链正文和全文检索（仅做跳转）。
> 
### 4.4 M4 投递状态登记（主笔：赵蔚）

> 依据 `spec/module-04-application-log.md`。M4 独立维护投递记录，不调用 M3、M5；状态机流转与幂等校验都在服务层。
> 业务错误码仅 2 个：`ALREADY_APPLIED`、`INVALID_STATUS_TRANSITION`，且不可合并共用。
> 分层归属：状态流转校验、幂等判断在**服务层**；API 层只做参数校验与鉴权。

**端点总览**

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | POST | `/api/v1/applications` | 登记投递状态 | AC-1、AC-3、AC-4 |
| 2 | GET | `/api/v1/applications` | 查询投递记录列表 | AC-2、AC-5 |

#### 4.4.1 登记投递状态

**URL**: `POST /api/v1/applications`

**请求**

```json
{
  "jobId": "J001",
  "status": "已投递"
}
**成功响应**: `201 Created`
{
  "code": "OK",
  "data": {
    "applicationId": "A001",
    "jobId": "J001",
    "status": "已投递",
    "createdAt": "2026-11-12T10:00:00"
  }
}
```

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 400 | -（无业务码） | `jobId` 缺失或状态值非法 | 不写库 | API 层 Bean Validation |
| 409 | `ALREADY_APPLIED` | 同一学生同一岗位重复登记 | 拦截，不产生新记录 | AC-2 |
| 409 | `INVALID_STATUS_TRANSITION` | 非法状态流转 | 拦截，原记录状态不变 | AC-3 |

#### 4.4.2 查询投递记录列表

**URL**: `GET /api/v1/applications`

**请求**: Header 带 JWT，无 body。

**成功响应**: `200 OK`

```json
{
  "code": "OK",
  "data": [
    {
      "applicationId": "A001",
      "jobId": "J001",
      "title": "前端开发实习生",
      "status": "已投递",
      "updatedAt": "2026-11-12T10:00:00"
    }
  ]
}
```

若无投递记录，`data` 返回空数组 `[]`，不报错（AC-5）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 401 | -（无业务码） | 未登录或 Token 失效 | 不返回数据 | 通用鉴权 |

> **越界声明**：M4 不负责判断该不该投、不生成面试日程（属 M6）、不发送提醒。

### 4.5 M5 文字模拟面试（主笔：卢果）

> 依据 `spec/module-05-mock-interview.md`。错误码全部取自该 spec 的 AC-4 / AC-5 / AC-6 与「边界与异常」表，**不新增表中没有的码**。
> 分层归属：接口超时、四维评分缺项判定、归档写入，均属**服务层**；JD 空值校验、路径参数格式属 **API 层参数校验**，不承载业务规则。

**端点总览**

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | POST | `/api/v1/mock-interviews` | 开始面试，生成题目 | AC-1、AC-4 |
| 2 | POST | `/api/v1/mock-interviews/{sessionId}/answers` | 提交单题作答并评分 | AC-2、AC-5 |
| 3 | POST | `/api/v1/mock-interviews/{sessionId}/report` | 生成并归档最终报告 | AC-3、AC-6 |

#### 4.5.1 开始面试

**URL**：`POST /api/v1/mock-interviews`

**请求**

- Header：`Authorization: Bearer <JWT>`
- Body：

```json
{
  "jobId": "j_20261112_007",
  "jdText": "负责后端服务开发，熟悉 Java / Spring Boot / MySQL ..."
}
```

- `jobId` 与 `jdText` 二选一：传 `jobId` 时优先读 M4 的投递记录；不传或 M4 无记录时，必须传 `jdText`（≥50 字）走降级路径（FR-1）。

**成功响应**：`201 Created`

```json
{
  "code": "OK",
  "requestId": "a4b2c1d0",
  "data": {
    "sessionId": "mi_20261112_001",
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

- 题目从预置题库按岗位类型抽取，固定 5-8 道（FR-3）。
- `dimension` 必须从 `专业知识 / 逻辑表达 / 项目经验 / 岗位匹配度` 四维中取一（FR-5）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 400 | `JD_REQUIRED` | `jobId` 未传且 `jdText` 为空或不足 50 字 | 不创建会话 | module-05 边界表「JD 为空」 |
| 504 | - | 抽题接口请求超时（10 秒）且重试 1 次仍失败 | 不创建会话 | NFR-1（非错误码） |

#### 4.5.2 提交单题作答并评分

**URL**：`POST /api/v1/mock-interviews/{sessionId}/answers`

**请求**

- Header：`Authorization: Bearer <JWT>`
- Body：

```json
{
  "questionId": 3,
  "answerText": "当时我们线上订单服务出现超时，我通过日志定位到 ..."
}
```

- `sessionId` 必须属于当前登录学生，且会话状态为 `IN_PROGRESS`。
- `answerText` 不能为空字符串；系统按关键词命中规则评分，不调大模型（FR-5、Non-Goals）。

**成功响应**：`200 OK`

```json
{
  "code": "OK",
  "requestId": "b5c3d2e1",
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
- `hasNextQuestion = false` 时，`nextQuestion` 为 `null`，前端可引导用户调用 4.5.3 生成报告。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 404 | - | `sessionId` 不存在或不属于当前学生 | 不评分、不写库 | 通用鉴权约束 |
| 409 | - | 会话已归档或已取消，仍尝试提交作答 | 不评分 | 通用状态机约束 |
| 504 | - | 评分接口请求超时（10 秒）且重试 1 次仍失败 | 作答草稿保留在前端 `localStorage` | NFR-1（非错误码） |


#### 4.5.3 生成并归档最终报告

**URL**：`POST /api/v1/mock-interviews/{sessionId}/report`

**请求**

- Header：`Authorization: Bearer <JWT>`
- Body：无。以服务端已保存的全部作答记录为准，不重复上传答案。

**成功响应**：`201 Created`

```json
{
  "code": "OK",
  "requestId": "c6d4e3f2",
  "data": {
    "sessionId": "mi_20261112_001",
    "status": "ARCHIVED",
    "totalScore": 68,
    "dimensionScores": {
      "专业知识": 75,
      "逻辑表达": 62,
      "项目经验": 70,
      "岗位匹配度": 65
    },
    "summary": "逻辑表达维度偏弱，建议用 STAR 结构组织回答。",
    "archivedAt": "2026-11-12T17:30:00+08:00"
  }
}
```

- 报告写入 `mock_interviews` 归档记录后，M8 档案看板才可读取（M5 只归档，M8 只读，依赖方向不反向）。
- 四维分数中任一维度缺项时，该维度记为 0 并在 `summary` 中标注「该维度能力有待提升」（AC-5 / 边界表「评分维度缺项」）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 409 | - | 仍有未作答题目的会话直接请求归档 | 不写归档，返回未答题目列表 | 通用状态机约束 |
| 500 | - | 报告同步至 M8 看板时发生异常 | 不产生归档，前端提示“报告归档失败” | 边界表「报告归档失败」 |
| 504 | - | 归档接口请求超时（10 秒）且重试 1 次仍失败 | 不写归档，会话保持 `IN_PROGRESS` | NFR-1（非错误码） |

> **越界声明**：本节不定义岗位匹配（M3）、投递登记（M4）、面试日程提醒（M6）及看板展示（M8）接口。

---

### 4.6 M6 面试登记与多级提醒（主笔：卢果）

> 依据 `spec/module-06-interview-reminder.md`。错误码全部取自该 spec 的 AC-5 / AC-6 / AC-7 / AC-8 / AC-9 与「边界与异常」表，**不新增表中没有的码**。
> 分层归属：5 节点计算、改期重算、发送失败重试，均属**服务层**；日期格式、字段必填属 **API 层参数校验**。
> **错误码补充说明**：由于本模块严格遵守白名单，下文失败响应表中的 `409` 均**无专属业务码**，属于**通用状态机约束**。前端请通过 HTTP 状态码 `409` 结合具体的 `触发条件` 提示语来区分是「面试已取消」还是「重复触发」。

**端点总览**

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | POST | `/api/v1/interviews` | 登记面试，生成 5 次提醒 | AC-1、AC-2、AC-8 |
| 2 | PATCH | `/api/v1/interviews/{interviewId}` | 改期或取消，旧提醒失效并重算 | AC-5、AC-9 |
| 3 | GET | `/api/v1/interviews/{interviewId}/reminders` | 查询该面试的提醒任务状态 | AC-2、AC-6、AC-7 |
| 4 | GET | `/api/v1/interviews/{interviewId}/calendar` | 导出 `.ics` 日历文件 | AC-4 |

#### 4.6.1 登记面试

**URL**：`POST /api/v1/interviews`

**请求**

- Header：`Authorization: Bearer <JWT>`
- Body：

```json
{
  "company": "某科技有限公司",
  "position": "后端开发实习生",
  "startTime": "2026-11-20T14:00:00+08:00",
  "departureTime": "2026-11-20T12:30:00+08:00",
  "location": "杭州市余杭区某大厦",
  "format": "OFFLINE"
}
```

- `departureTime` 可为空；为空时前 2 小时提醒按 `startTime` 倒推，并在响应中标记 `baselineFallback = true`（AC-8 / 边界表「基准缺失」）。
- `format` 取 `ONLINE / OFFLINE`；`ONLINE` 时 `location` 填会议链接，`OFFLINE` 时填详细地址（边界表「未填面试地点」）。

**成功响应**：`201 Created`

```json
{
  "code": "OK",
  "requestId": "d7e5f4a3",
  "data": {
    "interviewId": "itv_20261112_001",
    "status": "SCHEDULED",
    "reminders": [
      { "nodeType": "D3", "triggerAt": "2026-11-17T14:00:00+08:00" },
      { "nodeType": "D1", "triggerAt": "2026-11-19T14:00:00+08:00" },
      { "nodeType": "H2_DEPARTURE", "triggerAt": "2026-11-20T10:30:00+08:00" },
      { "nodeType": "M30", "triggerAt": "2026-11-20T13:30:00+08:00" },
      { "nodeType": "POST_REVIEW", "triggerAt": "2026-11-20T16:00:00+08:00" }
    ],
    "baselineFallback": false
  }
}
```

- 提醒任务由 `@Scheduled` 扫描生成，登记时不立即推送（FR-2）。
- 5 条提醒写入 `reminder` 表，唯一约束 `(interview_id, node_type)` 防重复触发（第 3 章核心实体）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 400 | - | `startTime` 早于当前时间 | 不写库，不生成提醒 | 边界表「时间冲突」 |
| 400 | - | `ONLINE` 未填会议链接，或 `OFFLINE` 未填详细地址 | 不写库 | 边界表「未填面试地点」 |

#### 4.6.2 改期或取消

**URL**：`PATCH /api/v1/interviews/{interviewId}`

**请求**

- Header：`Authorization: Bearer <JWT>`
- Body（改期）：

```json
{
  "startTime": "2026-11-21T10:00:00+08:00",
  "departureTime": "2026-11-21T08:30:00+08:00"
}
```

- Body（取消）：

```json
{
  "status": "CANCELLED"
}
```

**成功响应**：`200 OK`

```json
{
  "code": "OK",
  "requestId": "e8f6a5b4",
  "data": {
    "interviewId": "itv_20261112_001",
    "status": "RESCHEDULED",
    "oldRemindersInvalidated": true,
    "newReminders": [
      { "nodeType": "H2_DEPARTURE", "triggerAt": "2026-11-21T06:30:00+08:00" },
      { "nodeType": "M30", "triggerAt": "2026-11-21T09:30:00+08:00" },
      { "nodeType": "POST_REVIEW", "triggerAt": "2026-11-21T12:00:00+08:00" }
    ],
    "skippedExpiredNodes": ["D3", "D1"]
  }
}
```

- 改期时旧 5 条提醒全部置为失效（`oldRemindersInvalidated = true`），按新时间重新计算并生成未过期节点（AC-9 / 边界表「改期或取消后旧提醒失效与重建」）。
- 取消时 `status = CANCELLED`，旧提醒全部失效，后续 `@Scheduled` 扫描直接跳过（AC-5）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 404 | - | `interviewId` 不存在或不属于当前学生 | 不修改任何数据 | 通用鉴权约束 |
| 400 | - | 改期后的 `startTime` 早于当前时间 | 旧提醒保持不变，不重算 | 边界表「时间冲突」 |
| 409 | - | 对已取消的面试再次执行 PATCH（触发条件：状态已取消） | 不修改状态 | 通用状态机约束 |
| 409 | - | 改期成功后，调度器仍尝试触发旧时间的任务（触发条件：旧任务失效拦截） | 强制拦截，丢弃推送，确保“改期后旧提醒失效” | module-06 AC-9 |

#### 4.6.3 查询提醒任务状态

**URL**：`GET /api/v1/interviews/{interviewId}/reminders`

**请求**

- Header：`Authorization: Bearer <JWT>`
- Body：无。

**成功响应**：`200 OK`

```json
{
  "code": "OK",
  "requestId": "f9a7b6c5",
  "data": {
    "interviewId": "itv_20261112_001",
    "reminders": [
      {
        "nodeType": "D3",
        "triggerAt": "2026-11-17T14:00:00+08:00",
        "sendStatus": "SENT",
        "channel": ["IN_APP", "EMAIL"]
      },
      {
        "nodeType": "H2_DEPARTURE",
        "triggerAt": "2026-11-20T10:30:00+08:00",
        "sendStatus": "FAILED",
        "retryCount": 3,
        "lastError": "REMINDER_SEND_FAILED",
        "channel": ["IN_APP", "EMAIL"]
      }
    ]
  }
}
```

- `sendStatus` 取 `PENDING / SENT / FAILED`；`FAILED` 时带 `retryCount` 和 `lastError`（AC-6）。
- 同一 `(interview_id, node_type)` 只存在一条记录，`@Scheduled` 通过唯一约束拦截重复触发（AC-7 / 边界表「重复触发」）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 404 | - | `interviewId` 不存在或不属于当前学生 | 不返回任何提醒数据 | 通用鉴权约束 |
| 409 | - | 调度器扫描周期重叠，尝试对已发送的节点发起重复推送（触发条件：幂等拦截） | 执行幂等拦截，不产生重复推送 | module-06 AC-7 |

#### 4.6.4 导出 `.ics` 日历文件

**URL**：`GET /api/v1/interviews/{interviewId}/calendar`

**请求**

- Header：`Authorization: Bearer <JWT>`
- Body：无。

**成功响应**：`200 OK`

`Content-Type: text/calendar; charset=utf-8`，Body 为 `.ics` 文件流：

```text
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Campus Career//Interview Calendar//CN
BEGIN:VEVENT
UID:itv_20261112_001@campus-career
DTSTART:20261120T060000Z
DTEND:20261120T070000Z
SUMMARY:面试 - 某科技有限公司 后端开发实习生
LOCATION:杭州市余杭区某大厦
END:VEVENT
END:VCALENDAR
```

- `.ics` 仅做导出，不参与站内提醒（FR-4 / Non-Goals）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 404 | - | `interviewId` 不存在或不属于当前学生 | 不生成文件 | 通用鉴权约束 |
| 409 | - | 面试已取消（触发条件：状态已取消无法导出） | 不生成文件 | 通用状态机约束 |

> **越界声明**：本节不定义投递状态流转（M4）、模拟面试（M5）、面经收录（M7）及看板聚合（M8）接口。

### 4.7 M7 面经外链（主笔：熊尚华）

> 依据 `spec/module-07-interview-notes.md`。M7 只读 M2 画像方向用于排序，不调用 M1、M3–M6、M8，也不被其他模块依赖（辅助查询环节）。
> 错误码说明：M7 spec 未定义大写业务错误码。外链失效、无匹配、画像缺失均为 200 空态或降级标记，**不是错误**；请求类拒绝由 API / 服务层校验返回，不新增 spec 之外的业务码（处理方式同 4.2.2）。
> 分层归属：列表首屏 / 筛选 / 分页 ≤1 秒由 **API 层**响应时效保证（NFR-1）；24 小时探测、点击 3 秒判定、不抓正文合规在 **服务层**；API 层只做参数校验与鉴权，不写业务规则。

**SSRF 防护（服务端对所有外链发起请求前的统一校验，服务层执行）**

- 协议白名单：仅允许 `http` / `https`；拒绝 `file`、`ftp`、`gopher`、`dict` 等非网页协议及无协议输入。
- 目标地址校验：先解析域名得到 IP，对全部解析结果逐一校验，拒绝指向以下地址的目标：
  - 环回地址 `127.0.0.0/8`、`::1`
  - 私有网段 `10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16`
  - 链路本地 `169.254.0.0/16`（含云元数据端点 `169.254.169.254`）、`fe80::/10`
  - 未指定 / 保留 / 多播地址 `0.0.0.0`、`224.0.0.0/4`、`240.0.0.0/4` 及对应 IPv6 保留地址
- 重定向限制：最多跟随 1 次重定向；每个跳转目标（含 Location 解析出的 IP）重新执行上述协议与地址校验，任一不通过即拒绝，不再继续跟随。
- 连接约束：连接与总超时 ≤3 秒（与 NFR-3 一致）；服务层对解析后通过校验的 IP 直接建立连接，避免解析与连接之间的地址被替换（DNS rebinding）。
- 命中任一规则即拒绝发起请求，返回 400（无业务码），不写库、不产生探测记录。

**端点总览**

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | GET | `/api/v1/interview-notes` | 面经列表筛选、分页、分组展示 | AC-1、AC-3、AC-5；NFR-1、NFR-2 |
| 2 | POST | `/api/v1/interview-notes` | 收录面经外链 | AC-1；边界行 3；NFR-4 |
| 3 | GET | `/api/v1/interview-notes/{noteId}/reachability` | 点击实时校验（3 秒判失效） | AC-4；NFR-3 |

#### 4.7.1 面经列表

**URL**：`GET /api/v1/interview-notes`

**请求**

- Header：`Authorization: Bearer <JWT>`
- Query：`position`、`direction`、`source`（筛选条件，均可空）；`page`（默认 1）、`pageSize`（默认 10，最大 100）

**成功响应**：`200 OK`

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

- 每条仅含外链与来源、检索条件，**不含正文**（FR-2）。
- `linkStatus = INVALID` 表示 24 小时探测失效，记录置灰、不可点击，并带「来源已失效」标记（NFR-2）；该状态在 200 内返回，不是错误。
- `isDemo = true` 为演示数据标识，不与真实数据混用（AC-5）。
- 筛选无匹配时 `items = []`，空态不是错误（AC-3）；画像方向缺失时 `personalizationApplied = false`，不做个性化排序、返回默认列表，仍支持手动筛选（AC-3）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 400 | —（无业务码） | `pageSize > 100`、分页参数非法，或筛选参数格式错误 | 不查询、不写库 | API 层 Bean Validation；NFR-1 分页口径 |
| 401 | —（无业务码） | 无 JWT 或 Token 失效 | 不返回列表数据 | API 层统一鉴权 |

> 本端点无 spec 业务错误码；空态与降级均在 200 内表达，不造 404。

#### 4.7.2 收录面经外链

**URL**：`POST /api/v1/interview-notes`

**请求**

```json
{
  "url": "https://example.com/interview/123",
  "source": "某公开招聘社区",
  "position": "后端开发",
  "direction": "Java"
}
```

- 仅接受外链、来源与检索条件；不接受面经正文（`content`）字段（NFR-4）。

**成功响应**：`201 Created`

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

- 新记录进入待探测状态，由后台 24 小时定时任务首次探测；收录过程不抓取、不缓存正文（FR-2、NFR-4）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 400 | —（无业务码） | `url` 非合法 http/https 链接，或请求体结构错误 | 不写库 | API 层参数校验；5.7「URL 非法」 |
| 400 | —（无业务码） | 协议非 http/https，或解析后 IP 属环回 / 私有 / 链路本地 / 保留段，或重定向超过 1 次、跳转目标未过校验（SSRF） | 不发起请求、不写库、不产生记录 | 服务层 SSRF 统一校验；本节 SSRF 防护规则 |
| 422 | —（无业务码） | `source` 来源字段缺失 | 拒绝入库并记录质量告警，不产生面经记录 | 边界行 3；AC-1 |
| 422 | —（无业务码） | 请求携带面经正文（`content` / `body`）字段 | 一律拒绝，不存储正文 | NFR-4 |
| 401 | —（无业务码） | 无 JWT 或 Token 失效 | 不写库 | API 层统一鉴权 |

> 两个 422 触发条件与数据影响不同，分别对应来源缺失与正文合规，不共用业务码（spec 未定义业务码，故均由服务层校验拒绝）。

#### 4.7.3 点击实时校验

**URL**：`GET /api/v1/interview-notes/{noteId}/reachability`

**请求**：Header 带 JWT，无 body。

**成功响应**：`200 OK`

外链可达：

```json
{
  "code": "OK",
  "data": { "noteId": "n_001", "reachable": true, "linkStatus": "OK", "checkedAt": "2026-11-12T15:10:00+08:00" }
}
```

连接 3 秒无响应（降级，仍返回 200，不是错误）：

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

- 连接无响应超过 3 秒判定不可达，按 AC-4 提供同关键词重检兜底（`retryKeyword`、`retryAvailable`），不长时间无反馈（NFR-3）。
- 实时校验不修改记录，定时探测结果由后台任务更新（5.7「点击 3 秒不改库」）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 404 | —（无业务码） | `noteId` 指向不存在的记录 | 不写库 | API 层路径参数校验 |
| 400 | —（无业务码） | 实时校验目标命中 SSRF 规则（非 http/https、内网或保留段 IP、重定向超限） | 不发起探测请求、不写库 | 服务层 SSRF 统一校验；本节 SSRF 防护规则 |
| 401 | —（无业务码） | 无 JWT 或 Token 失效 | 不返回数据 | API 层统一鉴权 |

> 此处 404 针对「不存在的具体资源 ID」；列表筛选无匹配仍按 200 空数组处理，二者不冲突。

> **越界声明**：M7 不定义岗位推荐与岗位外链（M3）、投递登记（M4）、模拟面试（M5）接口；不做全文检索、不评分。M7 只读取 M2 画像方向，不向其他模块推送数据。

### 4.8 M8 求职档案看板（主笔：熊尚华）

> 依据 `spec/module-08-dashboard.md`。M8 只读聚合 M1–M7（读只读视图 `v_dashboard` 与各源只读接口），不发起任何写操作，也不被其他模块依赖（输出出口）。
> 业务错误码仅 1 个：`DASHBOARD_FORBIDDEN`（403，AC-6）。分区失败、空数据均为 200 降级或空态标记，**不是错误**；不新增 spec 之外的码。
> 分层归属：分区聚合、首屏 3 秒、单分区 1 秒、失败重试在 **服务层**（M8 聚合属服务层，NFR-1、NFR-2）；API 层只做参数校验与鉴权，不写业务规则。

**端点总览**

| # | 方法 | URL | 用途 | 对应 spec |
|---|---|---|---|---|
| 1 | GET | `/api/v1/dashboard` | 看板全量分区聚合 | AC-1、AC-3、AC-4、AC-5；NFR-1、NFR-2 |
| 2 | GET | `/api/v1/dashboard/sections/{section}` | 单分区查询与重试 | AC-7；NFR-2 |

> 本模块不提供任何写端点。

#### 4.8.1 看板全量

**URL**：`GET /api/v1/dashboard`

**请求**：Header 带 JWT，无 body。

**成功响应**：`200 OK`

```json
{
  "code": "OK",
  "requestId": "c9d0e1f2",
  "data": {
    "studentId": "s_20260001",
    "sections": [
      { "section": "profile", "title": "求职画像", "status": "OK", "data": { "directions": ["后端开发"] } },
      { "section": "applications", "title": "意向清单", "status": "OK",
        "data": { "funnel": { "applied": 5, "interviewed": 2, "closed": 1 }, "highlights": ["已投递未约面：2"] } },
      { "section": "interviews", "title": "面试安排", "status": "EMPTY", "data": null },
      { "section": "reviews", "title": "复盘记录", "status": "FAILED", "retryable": true, "data": null }
    ],
    "renderedAt": "2026-11-12T15:20:00+08:00"
  }
}
```

- 四个固定分区：`profile` / `applications` / `interviews` / `reviews`。
- 无数据分区 `status = EMPTY`、`data = null`（AC-3 空态，不是错误）；源数据加载失败分区 `status = FAILED`、`retryable = true` 并带重试入口（AC-7），与 EMPTY 明确区分，其余分区照常展示。
- 突出「已投递未约面」「约面未复盘」等待办与关键状态（AC-4）。
- 就业中心账号仅只读浏览，不出现编辑、审核与导出入口（AC-5）。
- 首屏 3 秒内、单分区 1 秒内完成（NFR-1）；失败不返回编造的兜底数据（5.8）。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 403 | `DASHBOARD_FORBIDDEN` | 访问不属于自己的看板地址 | 不返回任何他人数据 | AC-6；NFR-4；边界行 5 |
| 401 | —（无业务码） | 无 JWT 或 Token 失效 | 不返回数据 | API 层统一鉴权 |

> 分区失败在 200 内以 `status = FAILED` 降级，不返回错误码；本端点唯一业务错误码为 `DASHBOARD_FORBIDDEN`。

#### 4.8.2 单分区查询与重试

**URL**：`GET /api/v1/dashboard/sections/{section}`

**请求**

- Header：`Authorization: Bearer <JWT>`
- 路径参数：`section` ∈ {`profile`, `applications`, `interviews`, `reviews`}

**成功响应**：`200 OK`

```json
{
  "code": "OK",
  "data": { "section": "reviews", "status": "OK", "data": [ { "interviewId": "i_001", "summary": "..." } ] }
}
```

- 分区无数据：`status = EMPTY`、`data = null`（AC-3）。
- 分区源数据失败：`status = FAILED`、`retryable = true`，点击重试后 10 秒内返回结果或再次失败提示（AC-7、NFR-2），不阻塞其他分区。

**失败响应**

| 状态码 | 错误码 | 触发条件 | 数据影响 | 出处 |
|---|---|---|---|---|
| 400 | —（无业务码） | `section` 不在四个合法分区内 | 不查询、不写库 | API 层参数校验 |
| 403 | `DASHBOARD_FORBIDDEN` | 越权访问他人分区 | 不返回他人数据 | AC-6；NFR-4；边界行 5 |
| 401 | —（无业务码） | 无 JWT 或 Token 失效 | 不返回数据 | API 层统一鉴权 |

#### 4.8.3 写请求拒绝（只读约束）

- 对本模块所有路径发起的写方法（`POST` / `PUT` / `PATCH` / `DELETE`）：返回 `405 Method Not Allowed`。
- 错误码：—（无业务码，API 层统一拒绝）。
- 数据影响：不产生任何数据变更；业务库对看板服务仅授予读权限（NFR-3）。
- 出处：NFR-3；AC-2（编辑跳回源模块）；边界行 2。

> **越界声明**：M8 不定义任何数据新增、修改、删除接口，编辑操作跳回对应源模块（M1–M7）完成；不提供数据导出、审核（后置 P1）与企业端管理，不引入新的数据采集。




## 5. 数据流

> 每个模块写两条路径。失败路径必须交代三件事：写不写库、已有数据动不动、能否重试且不产生重复记录。

### 5.1 M1 简历上传与解析

M1 分两步：上传进暂存区 → 学生确认后写正式档案。

**成功路径**
1. 展示层选择文件 → `POST /api/v1/resumes`
2. API 层仅做传输层防护：文件字段存在性、请求体大小硬上限（防超大请求打爆内存），不做业务判定
3. 服务层执行业务校验（扩展名白名单、≤10MB、可读性）→ 抽取纯文本 → 调大模型结构化输出 → JSON Schema 校验
4. 写暂存区，`status = PENDING_CONFIRM`，正式档案不变（AC-1）
5. 返回解析结果 → 展示层进确认页，学生逐项修改
6. 学生保存 → `POST /api/v1/resumes/confirm` → 写正式档案，`status = CONFIRMED`（AC-2）

**失败路径**

| 情形 | 返回 | 数据影响 | 可否重试 |
|---|---|---|---|
| 文件 > 10MB（恰好 10MB 放行） | 413 `RESUME_TOO_LARGE` | 不落盘、不建档 | 换文件重试 |
| 非 PDF / Word，或伪装扩展名 | 415 `RESUME_FORMAT_UNSUPPORTED` | 不落盘、不建档 | 换文件重试 |
| 文件有打开密码 / 0 字节 / 已损坏 | 415 `RESUME_UNREADABLE` | 不落盘、不建档 | 换文件重试 |
| 大模型 30 秒超时，重试 2 次仍失败 | 504 `PARSE_TIMEOUT` | 不落盘，原档案不变 | 可重试 |
| 模型输出非法 JSON 或未过 Schema | 502 `MODEL_OUTPUT_INVALID` | 不展示半成品，原档案不变 | 可重试 |
| 解析完成但四类字段全空 | 422 `NO_VALID_FIELD` | 不生成空档案 | 换简历重试 |

> 6 个码即 M1 全部业务错误码，与 spec「边界与异常」表逐行对应，无自造码。

### 5.2 M2 霍兰德问卷与画像

M2 有 5 个端点：取题续答、单题保存、提交计分、读画像、重新生成。

**成功路径**
1. `GET /api/v1/holland/questions` 取 30 题与已存作答，支持断点续答（AC-4）
2. `PUT /api/v1/holland/answers` 单题即时保存，无需「保存草稿」按钮（FR-2）
3. `POST /api/v1/holland/submissions` 提交 → 服务层计分、追溯校验、合成画像 → `201 Created`
4. `GET /api/v1/holland/profile` 读取当前画像
5. 重测走 `POST /api/v1/holland/profile/regenerate`，整体替换旧画像（AC-3）

**失败路径**

| 情形 | 返回 | 数据影响 | 可否重试 |
|---|---|---|---|
| 30 题未全部作答 | 422 `QUIZ_INCOMPLETE` | 不生成画像，已答题目保留 | 补齐后重试 |
| 已提交后再次修改作答 | 409 `QUIZ_DUPLICATE_SUBMIT` | 不覆盖已提交版本 | 不可重试，提示改走重测 |
| 评分无法追溯到作答 | 422 `SCORE_NOT_TRACEABLE` | 不落库 | 可重试 |
| 画像专业与技能缺失 | 200 + `PROFILE_SKILL_MISSING` | 正常返回，标降级标记 | 不算错误，无需重试 |

> `PROFILE_SKILL_MISSING` 是 200 响应中的降级标记，不是错误码（AC-5）。

### 5.3 M3 岗位推荐与外链

**成功路径**
1. 展示层请求推荐 → `GET /api/v1/jobs/recommendations`（已与 4.3 契约一致）
2. 服务层读 `holland_profile` → 按六维匹配度排序，生成可解释推荐理由（命中的技能 / 方向标签）
3. 返回岗位列表（含外链 + 「演示数据」标识）→ 展示层渲染卡片
4. 点击卡片 → 跳转外部招聘链接，仅记录检索条件，不落岗位详情、不改投递状态

**失败路径**

| 情形 | 返回 | 数据影响 | 可否重试 |
|---|---|---|---|
| 画像字段完整但无技能标签 | 200 + `PROFILE_SKILL_MISSING` | 不渲染卡片，展示【暂无匹配岗位，请完善技能标签】空态 | 不是错误，补技能后重试 |
| 无匹配岗位 | 200 + 空数组 | 不写库 | 空结果是正常态，不是错误 |
| 外链已失效 | 404 `JOB_LINK_INVALID` | 卡片标「失效来源」，不改任何状态 | 按关键词重检 |

> 三个码均取自 module-03 spec（AC-2 / AC-5 / AC-3），不新增 spec 之外的码。

### 5.4 M4 投递状态登记

**成功路径**
1. 展示层提交投递 → `POST /api/v1/applications`（端点名以 4.4 契约为准）
2. API 层校验岗位存在
3. 服务层按唯一约束 `(student_id, job_id)` 判重 → 写 `application`，状态 = 已投递
4. 状态变更按四态流转规则校验：已投递 → 已查看 / 已约面 / 已结束；已查看 → 已约面 / 已结束；已约面 → 已结束；已结束为终态
5. 返回投递记录，同步 M8 看板投递记录区

**失败路径**

| 情形 | 返回 | 数据影响 | 可否重试 |
|---|---|---|---|
| 重复登记同一岗位 | 409 `ALREADY_APPLIED` | 不新增记录，原记录不变 | 不可重试，提示「已登记」 |
| 非法状态流转（如 已结束 → 已投递） | 409 `INVALID_STATUS_TRANSITION` | 状态字段不动 | 不可重试 |
| 并发两次登记 | 409 `ALREADY_APPLIED`，仅一条写入成功 | 唯一约束兜底，不会产生两条 | 幂等 |
| 点外链后返回平台 | 不改任何状态 | 提示【请手动确认是否已投递】 | — |

> 两个码取自 module-04 spec（AC-2 / AC-3）。M4 是本项目并发最敏感的一处，由 ADR-001 决策覆盖。

### 5.5 M5 文字模拟面试

**成功路径**
1. 展示层开始面试 → `POST /api/v1/mock-interviews`（端点名以 4.5 契约为准）
2. 岗位信息优先读 M4 投递记录；无记录则手动粘贴 JD 或输入岗位名称（FR-1 降级）
3. 按岗位类型从预置题库抽 5–8 道文字题（**本模块不调用大模型**，不接入实时题生成）
4. 学生逐题文字作答 → 关键词规则评分，四维：专业知识 / 逻辑表达 / 项目经验 / 岗位匹配度
5. 全部答完 → 生成总分与评价报告 → 归档，同步 M8 看板供回看

**失败路径**

| 情形 | 返回 | 数据影响 | 可否重试 |
|---|---|---|---|
| JD 为空 | 400 `JD_REQUIRED` | 阻断流程，不进会话 | 补 JD 后重试 |
| 粘贴 JD 少于 50 字 | 400 `JD_REQUIRED`，提示「请粘贴更详细的岗位要求」 | 阻断流程 | 补全后重试 |
| 抽题 / 评分接口超时 | 10 秒超时，自动重试 1 次；仍失败则前端中断 loading，提示「网络异常，请重试」 | 草稿存前端 localStorage，**不记 0 分** | 可重试 |
| 某维度未作答或关键词全未命中 | 200，该维度记 0 分 + 报告提示「该维度能力有待提升」 | 归档标该维度 0 分，不伪造分数 | 不重试 |
| 报告归档失败 | 提示「报告归档失败，请稍后重试」 | 不留半条归档，本地缓存报告数据 | 可重试 |

> `JD_REQUIRED` 取自 module-05 spec 边界表。超时 10 秒 + 重试 1 次是 spec NFR-1 的独立口径，不是错误码。**spec 明确「本模块不涉及大模型调用」，不得出现 `LLM_TIMEOUT`。**

### 5.6 M6 面试登记与多级提醒

**成功路径**
1. 展示层登记面试（公司 / 岗位 / 面试时间 / 形式 / 地点或会议链接）→ `POST /api/v1/interviews`（端点名以 4.6 契约为准）
2. 服务层按开始时间与出发时间计算 5 个触发点：前 3 天、前 1 天、前 2 小时（按出发时间）、前 30 分钟、结束后 2 小时复盘
3. 写 `interview` + 5 条 `reminder`（唯一约束 `(interview_id, node_type)`）
4. `@Scheduled` 定时任务扫描到点 → 站内信 + 邮件
5. 导出日历 → 生成 `.ics` 文件供外部导入（仅导出，不参与站内提醒）

**失败路径**

| 情形 | 返回 | 数据影响 | 可否重试 |
|---|---|---|---|
| 提醒时间早于当前 | 提示「设定的提醒时间已过，是否仅保留复盘提醒？」由用户确认 | 确认前不写库 | 改时间后重试 |
| 未填出发时间 | 前 2 小时提醒按面试开始时间倒推 + 提示补出发时间 | 正常生成提醒 | 不阻断 |
| 改期 | 200，清除旧定时任务，按新时间 + 出发时间重新生成 4 节点 + 复盘，已过期节点不触发 | 幂等 | — |
| 取消面试 | 200，阻断后续全部提醒推送 | 状态置「已取消」 | — |
| 同一节点重复触发 | 唯一约束拦截，同节点只发一次 | 幂等 | — |
| 发送失败 | 重试 3 次（间隔 5 分钟），仍失败返回 `REMINDER_SEND_FAILED` | 只更新发送状态，不新增 reminder | 可重试 |

> 5 次触发口径与 `REMINDER_SEND_FAILED` 均取自 module-06 spec（FR-2 / AC-6），不得退回 4 节点。改期后重算是 AC-9 的硬要求。

### 5.7 M7 面经外链

**成功路径**
1. 展示层请求列表 → `GET /api/v1/interview-notes`（筛选、分页、分组，1 秒内响应）
2. 展示层提交链接 → `POST /api/v1/interview-notes`（仅接受外链与来源，不收正文）
3. 写 `interview_note`，`linkStatus = PENDING`，由后台 24 小时定时任务首次探测
4. 用户点击链接 → `GET /api/v1/interview-notes/{noteId}/reachability` 实时校验，3 秒内返回可达性

**失败路径**

| 情形 | 返回 | 数据影响 | 可否重试 |
|---|---|---|---|
| url 非 http/https 或请求体结构错 | 400（无业务码，API 层参数校验） | 不写库 | 改链接后重试 |
| source 来源缺失 | 422（无业务码，服务层校验） | 拒绝入库并记录质量告警 | 补来源后重试 |
| 请求携带面经正文 | 422（无业务码，服务层校验） | 一律拒绝，不存储正文 | 不可重试 |
| 列表筛选无匹配 | 200 + `items = []` | 不写库 | 空态不是错误 |
| 24 小时探测失败 | 200 + `linkStatus = INVALID` | 只更新状态与探测时间，不删记录 | 次日再探测 |
| 点击 3 秒无响应 | 200 + `reachable = false`，带 `retryKeyword` | 不改库 | 可按同关键词重检 |
| noteId 不存在 | 404（无业务码） | 不写库 | 不可重试 |

> M7 spec 未定义业务错误码；失效、空态、降级一律在 200 内表达，不造业务码。

### 5.8 M8 求职档案看板

**成功路径**
1. 展示层请求 → `GET /api/v1/dashboard`
2. 服务层并行读 M1–M7 只读视图 `v_dashboard`，聚合四个固定分区：profile / applications / interviews / reviews
3. 单分区重试 → `GET /api/v1/dashboard/sections/{section}`
4. 首屏 3 秒内渲染完成

**失败路径**

| 情形 | 返回 | 数据影响 | 可否重试 |
|---|---|---|---|
| 越权访问他人看板 | 403 `DASHBOARD_FORBIDDEN` | 不返回任何他人数据 | 不可重试 |
| section 不在四个合法值内 | 400（无业务码） | 不查询、不写库 | 改参数后重试 |
| 对看板路径发写请求 | 405 Method Not Allowed | 不产生任何数据变更 | 不可重试 |
| 单分区查询超 1 秒 | 200 + 该分区 `status = FAILED`、`retryable = true` | 不写库 | 可点重试，10 秒内出结果 |
| 分区无数据 | 200 + `status = EMPTY`、`data = null` | 不写库 | 空态不是错误 |
| 首屏 3 秒未完成 | 200 + 已完成分区 + 失败分区标记 | 不写库 | 可刷新重试 |

> 全模块只读，零写库；分区失败不返回编造数据。

## 6. 冻结范围

| 冻结（本周内不得擅自改） | 不冻结（第 10 周迭代） |
|---|---|
| 模块边界与职责 | 内部函数、类名、变量命名 |
| 接口契约（URL/请求/响应/错误码） | UI 样式、页面布局 |
| 依赖方向（单向/禁循环） | 具体框架版本细节 |
| 关键 ADR（ADR-001 投递幂等、ADR-002 大模型输出） | 索引优化细节 |
| 核心数据实体与唯一约束 | 非关键实现细节 |

**变更规则**：接口 / 依赖 / 关键 ADR 变更 → 新增 ADR 留痕。
**冻结不是不能改，是改要留证据；实现细节留给第 10 周迭代。**
**本周冻结点（D2 23:00 生效）**

| 冻结项 | 谁提变更 | 怎么改 |
|---|---|---|
| 模块边界与职责 | 任何人 | 群里说明理由，由陈雪裁定，并记入变更记录 |
| 接口契约（URL / 请求 / 响应 / 错误码） | 契约主笔 | 新增 ADR 留痕，或在变更记录登记 |
| 依赖方向 | 熊尚华 | 出现反向或循环一律打回，不接受例外 |
| 关键 ADR（ADR-001、ADR-002） | 决策作者 | 只能追加，不能静默改写结论 |
| 核心数据实体与唯一约束 | 陈雪 | 需同步改 spec，否则不准改 |

**变更记录**

| 日期 | 变更项 | 原因 | 裁定人 |
|---|---|---|---|
| 11.13 | 初版冻结 | D2 收口 | 陈雪 |
