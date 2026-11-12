# 契约-Spec 对照表（第 8 周 D2）

> 用途：证明 architecture.md 第 4 章每条契约都能在 spec 里找到出处，没有凭空发明的错误码。
> 填法：第 3 列填组员契约里实际写的 URL 与错误码；第 4 列填 spec 里对应的 AC 编号；第 5 列填「一致」或「已改」。

## 一、模块级总览

| 模块 | 契约主笔 | spec 文件 | 结论 |
|---|---|---|---|
| M1 简历解析 | 贾小舒 | module-01 | 已改 |
| M2 霍兰德画像 | 贾小舒 | module-02 | 已改 |
| M3 岗位推荐 | 赵蔚 | module-03 | 待核 |
| M4 投递登记 | 赵蔚 | module-04 | 待核 |
| M5 模拟面试 | 卢果 | module-05 | 待核 |
| M6 面试提醒 | 卢果 | module-06 | 待核 |
| M7 面经外链 | 熊尚华 | module-07 | 待核 |
| M8 档案看板 | 熊尚华 | module-08 | 待核 |

## 二、逐条对照

| # | 模块 | 契约条目（URL + 错误码） | spec 依据 | 结论 |
|---|---|---|---|---|
| 1 | M1 | POST /api/resumes · 413 `RESUME_TOO_LARGE` / 415 `RESUME_FORMAT_UNSUPPORTED` / 504 `PARSE_TIMEOUT` | module-01 AC-1 | 已改 |
| 2 | M2 | POST /api/holland/answers · 400 `QUIZ_INCOMPLETE` / 409 `QUIZ_DUPLICATE_SUBMIT` | module-02 AC-7 | 已改 |
| 3 | M3 | 待填 | module-03（画像缺失返回 404） | 待核 |
| 4 | M3 | 待填 | module-03（外链失效 JOB_LINK_INVALID） | 待核 |
| 5 | M4 | 待填 | module-04（重复投递拦截） | 待核 |
| 6 | M4 | 待填 | module-04 FR-2（已投递→已约面） | 待核 |
| 7 | M5 | 待填 | module-05（四维评分与缺项） | 待核 |
| 8 | M6 | 待填 | module-06 AC-1~AC-8（5 次触发口径） | 待核 |
| 9 | M7 | 待填 | module-07（24 小时探测、点击 3 秒判失效） | 待核 |
| 10 | M8 | 待填 | module-08 NFR-1、NFR-2（首屏 3 秒 / 单分区 1 秒） | 待核 |

## 三、重点查的三处高风险不一致

1. **错误码自造**：第 5 章曾有 4 个码在 spec 里搜不到（FILE_TOO_LARGE 等），M1/M2 已按 spec 修正；M3–M8 核完后在此补结论。
2. **M6 是否写成 4 个节点** → 必须 5 次触发（前 3 天 / 前 1 天 / 前 2 小时按出发时间 / 前 30 分钟 / 结束后复盘）
3. **M4 重复投递与非法状态流转是否共用一个错误码** → 必须拆成 `DUPLICATE_APPLICATION` 与 `INVALID_STATE_TRANSITION`

## 四、判定结论

- 8 个模块全部「一致」→ 契约与 spec 对齐，可合并
- 出现「已改」→ 在此登记改了什么、改在 architecture.md 哪一节
