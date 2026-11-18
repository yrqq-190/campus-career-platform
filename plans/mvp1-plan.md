# MVP1 任务计划（plans/mvp1-plan.md）

> 版本：v1.0（2026-11-19）
> 范围：仅 M1–M8 八个 MVP1 模块，不含语音面试、企业端、自建岗位库、社区（MVP2）。
> 颗粒度：一个任务 = 一个可在第 10 周评审完的 PR。任务数 12，落在课程要求的 8–12 区间。
> 负责人：陈雪（范围裁判与收口）　审核：贾小舒

## 一、任务表（八列）

| 任务 ID | 任务描述 | 对应 spec/AC | 负责人 | 分支名 | 依赖 | 验收标准 | 状态 |
|---|---|---|---|---|---|---|---|
| T1 | 数据层建表与 v_dashboard 只读视图 | architecture 第 3 章；module-08 AC-1 | 陈雪 | feature/issue-<T1>-data-schema | 无 | 8 张业务表 + 只读视图建表脚本可执行；字段与 architecture 第 3 章一致；M8 通过视图只读、不写业务表 | 待开始 |
| T2 | M1 简历上传校验与解析成功路径 | module-01 AC-1、AC-3、AC-5 | 贾小舒 | feature/issue-<T2>-m1-upload-parse | T1 | 10MB+1KB 返回 413 `RESUME_TOO_LARGE`；非 PDF/Word 返回 415；正常 PDF 解析出四类字段并可确认入库 | 待开始 |
| T3 | M1 解析异常降级与档案保护 | module-01 AC-6、AC-9 | 贾小舒 | feature/issue-<T3>-m1-parse-fallback | T2 | 三次 30 秒超时返回 504 `PARSE_TIMEOUT`；非法 JSON 返回 502 `MODEL_OUTPUT_INVALID`；失败后旧档案字段与时间戳不变 | 待开始 |
| T4 | M2 问卷作答、续答与提交校验 | module-02 AC-1、AC-4、AC-6 | 贾小舒 | feature/issue-<T4>-m2-quiz | T2 | 答完 30 题得六维分数；中途退出可原样续答；未答完提交返回 422 `QUIZ_INCOMPLETE` 并列出未答题号 | 待开始 |
| T5 | M2 画像生成与技能缺失降级 | module-02 AC-2、AC-5 | 贾小舒 | feature/issue-<T5>-m2-profile | T4 | 画像四项输出齐全；技能为空仍返回 200 且带 `PROFILE_SKILL_MISSING` 标记；重新生成整体替换旧画像 | 待开始 |
| T6 | M3 推荐列表与推荐理由 | module-03 AC-1、AC-5 | 赵蔚 | feature/issue-<T6>-m3-recommend-list | T5 | 命中技能时展示「命中 Java 技能」理由；无命中返回 200 空态；演示数据卡片带「演示数据」标识 | 待开始 |
| T7 | M3 外链跳转与失效兜底 | module-03 AC-3、AC-4 | 赵蔚 | feature/issue-<T7>-m3-link-fallback | T6 | 失效外链返回 404 `JOB_LINK_INVALID`，卡片标记失效来源并给同关键词重检链接 | 待开始 |
| T8 | M4 投递登记、幂等与状态机 | module-04 AC-2、AC-3；ADR-001 | 赵蔚 | feature/issue-<T8>-m4-application | T6 | 重复登记返回 409 `ALREADY_APPLIED` 且不新增记录；终态回写返回 409 `INVALID_STATUS_TRANSITION`；并发提交只成功一条（对齐 S1 实测数据） | 待开始 |
| T9 | M5 模拟面试会话与评分归档 | module-05 AC-1、AC-3 | 卢果 | feature/issue-<T9>-m5-mock-session | T5、T8 | JD 为空返回 400 `JD_REQUIRED`；单题作答出分并给维度提示；报告生成后同步 M8，归档失败时本地缓存并提示 | 待开始 |
| T10 | M6 面试登记与多级提醒 | module-06 AC-1、AC-2 | 卢果 | feature/issue-<T10>-m6-reminder | T8 | 登记后按 4 个触发节点 + 复盘共 5 次触发；前 2 小时这一节点按出发时间计算，缺失时降级并提示；改期重算且不重复推送 | 待开始 |
| T11 | M7 面经筛选与外链校验 | module-07 AC-1、AC-4 | 熊尚华 | feature/issue-<T11>-m7-notes | T5 | 筛选 1 秒内返回，记录仅含外链不含正文；点击后 3 秒内判不可达并给同关键词重检；演示数据带标识 | 待开始 |
| T12 | M8 看板聚合与越权隔离 | module-08 AC-1、AC-6 | 熊尚华 | feature/issue-<T12>-m8-dashboard | T1–T11 | 四分区聚合完整链路；无数据分区显示空态且不影响其他分区；他人看板返回 403 `DASHBOARD_FORBIDDEN`；无编辑入口；按 mvp1-scope 的一句话演示路径可完整跑通 | 待开始 |

> 说明：分支名中的 `<T1>`–`<T12>` 在 Issue 建好后替换为真实编号，不另起流水号。
> T2、T4 各绑 3 条 AC，属上限；第 10 周若评审吃力可拆成「主链路」「边界校验」两个 PR，不另立任务。
> 未列入的 AC（M4 AC-1/AC-5、M5 AC-2/AC-4/AC-5/AC-6、M6 AC-3～AC-9、M8 AC-7）作为对应任务 PR 内的检查项。

## 二、依赖顺序与关键路径

拓扑序（12 个任务全覆盖）：

```
T1 → T2 →（T3 ∥ T4）→ T5 → T6 →（T7 ∥ T8）→（T9 ∥ T10 ∥ T11）→ T12
```

- 关键路径：T1 → T2 → T4 → T5 → T6 → T8 → T9 → T12，共 7 跳。T9 卡 T12，不可延后。
- 依赖维度可并行，人力维度需串行：T3/T4 同属贾小舒，T7/T8 同属赵蔚，T9/T10 同属卢果。
- 个人串行次序建议：贾小舒 T2→T4→T5→T3；赵蔚 T6→T8→T7；卢果 T9→T10。
- 与 `docs/dependency.md` 的 14 条边一致：M1→M2→M3→M7→M4→M5→M6→M8，M8 在末端，无环。

## 三、范围声明

- 本计划只拆 MVP1 八个模块，依据 `spec/mvp1-scope.md` 与各模块 spec。
- 不含：语音实时面试、企业端、自建岗位库、社区互动（均为 MVP2）。
- 改 AC 必须先改 spec，再回来改本表；不允许只改代码。

## 四、变更记录

| 日期 | 内容 | 人 |
|---|---|---|
| 2026-11-19 | 初版：12 个任务、依赖与关键路径、范围声明 | 陈雪 |
