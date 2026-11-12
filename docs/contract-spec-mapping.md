# 契约-Spec 对照表（第 8 周 D2）

> 用途：证明 architecture.md 第 4 章每条契约都能在 spec 里找到出处，没有凭空发明的错误码。
> 填法：第 3 列填组员契约里实际写的 URL 与错误码；第 4 列填 spec 里对应的 AC 编号；第 5 列填「一致」「已改」或「待核」。

## 一、模块级总览

| 模块 | 契约主笔 | spec 文件 | 结论 |
|---|---|---|---|
| M1 简历解析 | 贾小舒 | module-01 | 一致 |
| M2 霍兰德画像 | 贾小舒 | module-02 | 一致 |
| M3 岗位推荐 | 赵蔚 | module-03 | 待核（4.3 待交付） |
| M4 投递登记 | 赵蔚 | module-04 | 待核（4.4 待交付） |
| M5 模拟面试 | 卢果 | module-05 | 待核（4.5 待交付） |
| M6 面试提醒 | 卢果 | module-06 | 待核（4.6 待交付） |
| M7 面经外链 | 熊尚华 | module-07 | 一致 |
| M8 档案看板 | 熊尚华 | module-08 | 一致 |

## 二、逐条对照

| # | 模块 | 契约条目（URL + 错误码） | spec 依据 | 结论 |
|---|---|---|---|---|
| 1 | M1 上传解析 | `POST /api/v1/resumes` · 415 `RESUME_FORMAT_UNSUPPORTED` / 415 `RESUME_UNREADABLE` / 413 `RESUME_TOO_LARGE` / 504 `PARSE_TIMEOUT` / 502 `MODEL_OUTPUT_INVALID` / 422 `NO_VALID_FIELD` | module-01 AC-3、AC-5～AC-8；边界行 1–8 | 一致 |
| 2 | M1 确认入库 | `POST /api/v1/resumes/confirm` · 422 `NO_VALID_FIELD` | module-01 FR-5；AC-8 | 一致 |
| 3 | M1 读取档案 | `GET /api/v1/resumes/current` · 无业务码（未建档返回 `data: null`，不返 404） | module-01 AC-2、AC-4 | 一致 |
| 4 | M2 作答与提交 | `PUT /api/v1/holland/answers`（单题保存）· `POST /api/v1/holland/submissions`（提交计分）· 422 `QUIZ_INCOMPLETE` / 409 `QUIZ_DUPLICATE_SUBMIT` / 422 `SCORE_NOT_TRACEABLE` | module-02 AC-6、AC-7 | 一致 |
| 5 | M2 画像降级 | `GET /api/v1/holland/profile` · 200 + `PROFILE_SKILL_MISSING`（降级标记，不是错误码） | module-02 AC-5；边界表 | 一致 |
| 6 | M3 岗位推荐 | 待 4.3 交付 | module-03 AC-2、AC-3：`PROFILE_SKILL_MISSING`（200）/ `JOB_LINK_INVALID`（404） | 待核 |
| 7 | M4 投递登记 | 待 4.4 交付 | module-04 AC-2、AC-3：`ALREADY_APPLIED`（409）/ `INVALID_STATUS_TRANSITION`（409） | 待核 |
| 8 | M5 模拟面试 | 待 4.5 交付 | module-05 边界表：`JD_REQUIRED`（400）；超时 10 秒 + 重试 1 次属 NFR，不设业务码 | 待核 |
| 9 | M6 面试提醒 | 待 4.6 交付 | module-06 AC-6：`REMINDER_SEND_FAILED`；FR-2 五次触发；AC-9 改期重算 | 待核 |
| 10 | M7 面经外链 | `GET /api/v1/interview-notes` · `POST /api/v1/interview-notes` · `GET /api/v1/interview-notes/{noteId}/reachability` · 无业务码（400 参数 / 422 服务层 / 404 不存在均为传输层） | module-07 AC-1～AC-5；NFR-1～NFR-4 | 一致 |
| 11 | M8 档案看板 | `GET /api/v1/dashboard` · `GET /api/v1/dashboard/sections/{section}` · 403 `DASHBOARD_FORBIDDEN`；无任何写端点 | module-08 AC-6；NFR-1、NFR-2 | 一致 |

## 三、重点查的三处高风险不一致

1. **错误码自造**：第 5 章初稿曾有 7 个码在 spec 中搜不到——`FILE_TOO_LARGE`、`UNSUPPORTED_FILE_TYPE`、`QUESTIONNAIRE_INCOMPLETE`、`PROFILE_NOT_FOUND`、`DUPLICATE_APPLICATION`、`INVALID_STATE_TRANSITION`、`LLM_TIMEOUT`、`ARCHIVE_FAILED`、`INVALID_INTERVIEW_TIME`。D2 已按 spec 原码逐条替换：M1/M2 由贾小舒契约修正，M3–M6 见 5.3–5.6。M7/M8 本就不造业务码（熊尚华契约明确声明）。
2. **M6 是否写成 4 个节点** → 必须 **5 次触发**：前 3 天 / 前 1 天 / 前 2 小时（按出发时间）/ 前 30 分钟 / 结束后复盘。5.6 已按 5 次写，赵蔚卢果契约须一致。
3. **M4 两个 409 是否共用一个码** → 必须拆成 `ALREADY_APPLIED`（重复登记，AC-2）与 `INVALID_STATUS_TRANSITION`（非法流转，AC-3）。注意 spec 用 `STATUS` 不是 `STATE`。

## 四、判定结论

- 8 个模块全部「一致」→ 契约与 spec 对齐，PR #60 可合并
- 当前 4 个一致（M1、M2、M7、M8），4 个待核（M3–M6），待 4.3–4.6 交付后补全
- 出现「已改」→ 在此登记改了什么、改在 architecture.md 哪一节
