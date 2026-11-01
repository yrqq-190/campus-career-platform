# 分支与提交规范

## 1. 分支规范 (Branching)
严禁直接向 `main` 分支推送代码，必须新建分支并提交 PR。
- 新功能: `feat/功能简述` (例如: `feat/login-page`)
- 修复Bug: `fix/bug简述` (例如: `fix/header-style`)
- 文档修改: `docs/文档简述` (例如: `docs/git-rules`)
- 杂项/配置: `chore/杂项简述` (例如: `chore/update-gitignore`)

## 2. 提交规范 (Conventional Commits)
提交信息必须使用以下格式：`<type>: <description>`
- `feat:` 新增功能
- `fix:` 修复缺陷
- `docs:` 仅修改文档
- `chore:` 构建过程或辅助工具的变动
- `refactor:` 代码重构

*示例：`docs: add git branching and commit rules`*
