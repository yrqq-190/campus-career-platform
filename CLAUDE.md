# CLAUDE.md — Agent 协作规范

本文件是本地 AI Agent（Cursor / Claude Code）的入口规范，等同于 `agent-rules.md`；冲突时以 `agent-rules.md` 为准。

## 一、每次下发任务的输入三件套

1. `@spec/<对应模块>.md` 的 AC 条目
2. `@CLAUDE.md`（本文件）
3. 当前分支名

指令模板：

> 任务 T{编号}：实现 {端点/功能}，对应 {AC-x}。当前分支 {branch}。
> 约束：不得修改 spec/、db/ 与无关模块；不得新增未批准依赖；只改 {指定目录}。
> 请先生成 {AC-x} 的测试，断言：{逐条列出}。生成后暂停，等我确认再实现。

## 二、红线

- 不写未关联 Issue / spec / AC 的代码
- 不改测试断言让测试通过（测试自适配属评审拒绝项）
- 不越界修改 `spec/`、`docs/architecture.md`、`db/`、他人模块目录
- 不调用不存在的 API 或依赖（幻觉引用）
- 一个任务一个新会话，上下文只信 spec 与 plan

## 三、目录约定

- 后端代码 `src/main/java/com/campus/career/<模块>/`
- 后端测试 `src/test/java/com/campus/career/<模块>/`，类名带 AC 编号
- 前端 `frontend/`（Vue 3 + TS + Element Plus），测试放 `frontend/tests/`
- 不再向 `docs/skeletons/` 写新内容

## 四、提交规范

- 分支 `feature|fix|test|docs|spike/<issue-N>-<短描述>`
- commit `feat(<模块>): <做了什么> (AC-x) #<Issue>`
- PR 按 `.github/PULL_REQUEST_TEMPLATE.md` 填全
