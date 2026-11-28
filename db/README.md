# db 目录说明

## 执行顺序

1. `schema.sql`：建 13 张表与 `v_dashboard` 只读视图（幂等，`IF NOT EXISTS`）
2. `seed.sql`：演示数据（幂等，`ON DUPLICATE KEY UPDATE`）
3. `grants.sql`：只读账号授权（仅 `SELECT ON v_dashboard`）

```bash
mysql -u root -p < db/schema.sql
mysql -u root -p < db/seed.sql
mysql -u root -p < db/grants.sql
```

## 表清单

| # | 表 | 模块 | 说明 |
|---|---|---|---|
| 1 | student | 公共 | 学生账号（spec 未涉及登录，不建密码列） |
| 2 | resume | M1 | 简历与解析结果，`skill_empty` 为生成列 |
| 3 | holland_profile | M2 | 画像，与 student 一对一，重新生成整体替换 |
| 4 | job | M3/M4 | 岗位外链元数据 |
| 5 | application | M4 | 投递记录，唯一约束防重复 |
| 6 | interview | M6 | 面试登记 |
| 7 | reminder | M6 | 提醒任务，`node_type` 五枚举 |
| 8 | interview_note | M7 | 面经外链，库内 status 与对外 linkStatus 按 architecture §4.7 映射 |
| 9 | holland_quiz_session | M2 | 问卷作答会话 |
| 10 | quiz_answer | M2 | 逐题作答 |
| 11 | mock_interview | M5 | 模拟面试会话 |
| 12 | mock_interview_answer | M5 | 单题作答与评分 |
| 13 | mock_interview_report | M5 | 报告归档，与会话一对一 |

## 约定

- 字符集 `utf8mb4`，引擎 `InnoDB`
- 所有表带 `created_at`；需要时带 `updated_at`
- 演示数据必须带 `is_demo = 1` 标识（M7 AC-5）
- 禁止在 SQL 中写入面经正文（M7 NFR-4）
