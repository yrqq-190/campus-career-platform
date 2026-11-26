# Agent 评审通道（第 10 周）

> 维护：颜睿卿（记录 / 看板）｜建立：2026-11-26（D1）
> 对应：PPT P12「S8 上：Agent 评审 + 人工评审分工」、P16 评审记录模板、P20 检查表
>
> **D1 结论：本周走通道 C（人工 @Agent + 截图）。** 通道 A（Copilot code review）无订阅；通道 B（自建 Action）的 workflow 已写好备查于 `docs/ci/pr-structure-check.yml`，但**尚未落 `.github/workflows/`**——当前 PAT 只有 `repo` scope，向 workflow 路径写入会被拒，需补 `workflow` scope 或用网页提交一次。

## 1. 三条通道

| 通道 | 触发方式 | 本周状态 | 说明 |
|---|---|---|---|
| A. Copilot code review | 仓库设置开启后自动评审 PR | 不可用 | 需 Copilot 订阅，仓库当前没有；入口 Settings → Copilot |
| B. 自建 Action「PR 结构自检」 | PR 打开 / 编辑 / 推送即触发 | 待启用 | 文件已备于 `docs/ci/pr-structure-check.yml`，移入 `.github/workflows/` 即生效；只查结构、分支名、改动清单，**不做内容判断** |
| C. 人工 `@Agent` 评审 | PR 评论 @ 一个可用 Agent，按五项检查出结论 | 可用（兜底） | 结论与截图存进 `docs/review/pr-XX.md` |

## 2. 通道 B 会做什么（启用后）

无密钥，只用 `GITHUB_TOKEN`：

- 读 PR 描述，检查 8 项：关联 Issue 与 AC、变更摘要、AI 使用说明、测试证据、变更范围、风险与回滚、评审清单、分支命名 `前缀/issue-N`；
- 拉改动文件清单（状态 + 行数），供人工核对范围收敛；
- 在 PR 下发**一条**带标记的评论，重复触发更新同一条，不刷屏；
- 关键项（Issue/AC、变更摘要、分支命名）缺失时 job 失败（红叉）；提示项只列 ❌，不阻塞。

## 3. PPT P12 五项检查：谁来做

| 检查项 | 通道 B | 人工 / Agent |
|---|---|---|
| 测试真正运行并通过 | 只能看出描述里有没有证据段 | 看证据是否可复现：命令、分支、提交、输出 |
| 改动范围收敛 | 列文件清单与行数 | 逐文件 `git diff --stat` 核对是否越界 |
| 未触碰白 / 黑名单 | 能看到文件路径 | 判断是否属他人模块、是否改 `spec/` 冻结内容 |
| 无幻觉 API / 依赖 | 做不到 | 核对调用是否存在、依赖是否已批准 |
| 无 spec / AC 偏离 | 做不到 | 对照 spec 逐条 AC 判定 |

## 4. D3 操作顺序（评审人照做）

1. 打开 PR，先核描述与改动清单（通道 B 启用前由人代做这一步）；
2. 在 PR 评论 @Agent，粘贴下面模板，**截图留痕**；
3. 人工逐文件 diff 与测试证据复核，结论只能由人给：通过 / 整改 / 阻塞；
4. 写 `docs/review/pr-XX.md`，Agent 评审结果与人工裁决分栏，整改项标「代码 / 测试 / spec」。

可直接复制的模板（把三处占位换成实际值）：

```
按 5 项检查评审这个 PR：① 测试是否真跑并通过（对照测试证据的命令与输出）② 改动范围是否收敛（逐个文件看 diff）③ 是否触碰白/黑名单（他人模块、spec/ 冻结契约、db/）④ 是否有幻觉 API 或未批准依赖 ⑤ 是否有 spec/AC 偏差（逐条 AC 列结论）。

输入：@spec/module-XX-xxx.md @docs/agent-rules.md（无此文件时用 CLAUDE.md）
当前分支：<分支名>
只输出「结论 + 每条证据 + 建议归类（代码/测试/spec）」，不要改代码。
```

## 5. 边界

- Agent 评审结果 ≠ 合并许可。PR 至少 1 人 APPROVED，且评审人 ≠ 提交人 ≠ 主笔（`docs/pr-gate.md` 第 5 条）。
- 通道 B 的绿勾只证明「描述结构齐全」，不能替代内容评审；它的红叉也不自动阻止合并，但要进评审记录，作为 D4 检查表「PR 模板字段齐全」的依据。
- 后续开通 Copilot（通道 A）后，B 仍作为格式兜底，两者不冲突。

## 6. 待办（D1 起）

1. 补 PAT `workflow` scope（或由有权限的账号在网页提交一次），把 `docs/ci/pr-structure-check.yml` 移入 `.github/workflows/pr-structure-check.yml`，用一个测试 PR 验证评论与红叉。
2. 看板 `Status` 字段加 **Blocked** 选项（需网页操作：Projects → Settings → Fields → Status）。加完把 `#136`（意向写入端点）等被 spec 卡住的卡片拖进去，作为 D4 检查表「Blocked 列标记 spec 卡点」的依据。
