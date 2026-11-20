# 分支规划表（第 9 周 · MVP1 T1–T12）

> 归属：颜睿卿（第 9 周分支规划责任人，见四天分工表第四节 / 课件 P5 第 5 项）
> 依据：`plans/mvp1-plan.md` 的 12 个任务、`docs/architecture.md` 冻结范围（§8）
> 版本：v1.1（2026-11-20，与 `plans/mvp1-plan.md` v1.2 同步，回填 T1–T5 的 Issue 编号 #103–#107）
> 范围：仅 T1–T12，不含语音面试、企业端、自建岗位库、社区（MVP2）

## 一、命名规则

```
<前缀>/issue-<Issue编号>-<动宾短语>
```

| 前缀 | 适用 | 示例 |
|---|---|---|
| `feature/` | 新功能实现（T1–T12 主体） | `feature/issue-98-m4-application` |
| `fix/` | 缺陷修复、评审返修 | `fix/issue-105-m1-413-return` |
| `spike/` | 技术探针、可行性验证 | `spike/issue-110-ics-format` |
| `test/` | 测试补充、用例工程 | `test/issue-112-m8-smoke` |
| `docs/` | 文档、计划、规格修订 | `docs/issue-115-week9-plan` |

**四条硬规则**

1. **必须带 Issue 编号**，缺编号的 PR 不予评审（不予评审 = 不占评审人时间，直接退回）；
2. 动宾短语用**小写英文 + 连字符**，不超过 4 个词，不出现中文、空格、下划线、大写；
3. **一任务一分支**，不共用、不复用、不在同一分支塞两个任务；
4. 分支名在 **PR 开出后不改名**；确需改名视同新分支，必须重开 PR 并在描述里注明旧 PR 号。

## 二、12 条分支（T1–T12）

| 任务 | 分支名 | 从哪切出 | 合并去向 | 负责人 | 本周开工 |
|---|---|---|---|---|---|
| T1 | `feature/issue-107-data-schema` | `main` | `main` | 陈雪 | **是**（11.21 起，真实实现） |
| T2 | `feature/issue-103-m1-upload-parse` | `main` | `main` | 贾小舒 | **是**（11.21 起，真实实现） |
| T3 | `feature/issue-104-m1-parse-fallback` | `main` | `main` | 贾小舒 | 否（等 T2 合并） |
| T4 | `feature/issue-105-m2-quiz` | `main` | `main` | 贾小舒 | 否（等 T2） |
| T5 | `feature/issue-106-m2-profile` | `main` | `main` | 贾小舒 | 否（等 T4） |
| T6 | `feature/issue-<T6>-m3-recommend-list` | `main` | `main` | 赵蔚 | **契约骨架 + Mock** |
| T7 | `feature/issue-<T7>-m3-link-fallback` | `main` | `main` | 赵蔚 | 否（等 T6） |
| T8 | `feature/issue-<T8>-m4-application` | `main` | `main` | 赵蔚 | 否（等 T6） |
| T9 | `feature/issue-<T9>-m5-mock-session` | `main` | `main` | 卢果 | **契约骨架 + Mock** |
| T10 | `feature/issue-<T10>-m6-reminder` | `main` | `main` | 卢果 | 否（等 T8） |
| T11 | `feature/issue-<T11>-m7-notes` | `main` | `main` | 熊尚华 | **契约骨架 + Mock** |
| T12 | `feature/issue-<T12>-m8-dashboard` | `main` | `main` | 熊尚华 | 否（等 T1–T11） |

**「契约骨架 + Mock」的边界**：按 `architecture.md` 第 4 章已冻结的 URL / 请求 / 成功响应 / 失败响应 / 错误码，实现 Controller、DTO 与前端页面骨架，返回**写死的 Mock 数据**。不连数据库、不写业务规则、不做持久化。前置任务完成后在**同一分支**替换为真实实现，不重开分支。

## 三、切出规则

1. 一律从 **`main`** 切出，不从他人分支、不从旧任务分支切出；
2. 切出前必须是最新 main：

   ```bash
   git switch main && git fetch origin && git pull --ff-only
   git switch -c feature/issue-<编号>-<短语>
   ```

3. 若本次改动确实依赖某个未合并的前置任务，**等前置合并后再切**，不在前置分支上叠加开发（避免 PR 里混入他人提交）；
4. 一个分支的生命周期 = 一个任务。前置任务合并后，后续任务另切新分支。

## 四、合并规则

1. **一律通过 PR 合回 `main`**，任何人不得直接 push `main`（含文档）；
2. 本周只要求**把 PR 开出来进 Review 列**，不要求合并。**唯一例外**：T1 建表脚本不涉业务逻辑且是其余任务的地基，评审通过即可先合；
3. 合并方式：
   - **代码任务（T1–T12 主体）用 squash merge**，一个任务在 main 上留一个提交，便于按任务回溯；
   - **文档 / 计划类 PR 用 merge commit**，保留分人提交作者（`plans/mvp1-plan.md` 的合并即按此）；
4. 合并前分支必须已 rebase 到最新 `main`，落后即不许合；
5. 合并后**立即删除远端分支**，不让已完成分支留在列表里干扰第 10 周评审；
6. 冲突处理：落在 `architecture.md` 第 4 章接口契约、第 3 章数据实体、`spec/` 目录的冲突，**不得自行解决**，回到 Spec Owner 先改 spec，再重算契约，最后解决冲突。

## 五、Issue 编号回填

1. 11.20 全员建 Issue 后，把真实编号替换本表与 `plans/mvp1-plan.md` 中的 `<T1>`–`<T12>`；
2. 替换后分支名形如 `feature/issue-98-m4-application`，**不另起流水号**、不加日期后缀；
3. 回填责任人：陈雪（复核两份文档一致后回填）；本文档回填后同步更新版本号为 v1.1；
4. 已按旧占位名开出的分支，改名前先确认无开放 PR；有 PR 的按「改名视同新分支」处理。

## 六、合规自检（评审人可直接照此判）

```bash
# 列出所有非 main 的分支
git branch -r | grep -v 'origin/main'
# 逐条校验命名（应全部命中）
git branch -r | grep -oE 'origin/[a-z]+/issue-[0-9]+-[a-z0-9-]+$'
```

判定口径：

| 现象 | 判 |
|---|---|
| 分支名无 `issue-<数字>` | ❌ 不合规，PR 退回 |
| 前缀不在 feature / fix / spike / test / docs 五类 | ❌ 不合规 |
| 一个分支同时改两个任务模块 | ❌ 不合规，要求拆分支拆 PR |
| 分支从他人分支切出（PR 里出现他人提交） | ❌ 不合规，要求 rebase 到 main 后重开 |
| 分支名含中文 / 大写 / 下划线 / 空格 | ❌ 不合规 |
| 分支落后 main 仍未 rebase 就提评审 | ⚠️ 评审人可要求先 rebase |

## 七、变更记录

| 日期 | 内容 | 人 |
|---|---|---|
| 2026-11-19 | 初版：命名规则 5 前缀 + 12 条分支 + 切出/合并规则 + 合规自检 | 颜睿卿 |
| 2026-11-20 | v1.1：T1 回填 #107、T2–T5 回填 #103–#106（与 `plans/mvp1-plan.md` v1.2 逐条一致）；T6–T12 仍为 `<Tn>` 占位，待对应 Issue 建出后回填 | 颜睿卿 |
