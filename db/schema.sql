-- =============================================================
-- T1 / Issue #107  MVP1 数据库结构 v2.0
-- 依据：docs/architecture.md 第 3 章 8 个实体
--       + spec/module-01/02/04/06/07 的字段、枚举与约束
-- 数据库：MySQL 8.0 / InnoDB / utf8mb4
--
-- v2.0 相对 v1.0 的变更（按 PR #118 评审意见，共 13 条）：
-- 【必须改】
-- 1. reminder.node_type 五个值改为 module-06 枚举表原文：
--    D3 / D1 / H2_DEPARTURE / M30 / POST_REVIEW
-- 2. resume.skill_empty 改为 STORED 生成列，由 parsed_skill 自动维护，
--    避免学生确认环节增删技能后仍判为「技能为空」（M1 FR-6 / AC-4，M2 AC-5）
-- 3. 8 张表全部加 IF NOT EXISTS，满足 #107「重复执行不报错」
-- 4. GRANT 拆到 db/grants.sql，不混进建表脚本
-- 5. v_dashboard 的 SUM(CASE...) 改为 COUNT(DISTINCT CASE...)，避免 JOIN 扇出放大
-- 6. resume 补 parsed_project（项目经历），M1 AC-1 四类字段齐全
-- 7. resume 加 UNIQUE KEY uk_resume_student
-- 8. interview 补 location、format（architecture 4.6.1 请求体）
-- 9. interview.status 补 RESCHEDULED（4.6.2 改期返回）
-- 10. holland_profile 补 recommend_dirs / intent_cities / expected_industry / skill_gaps
-- 【走变更流程，不在本脚本】
-- 11. 问卷作答明细表（M2 FR-2/AC-1/AC-4/AC-8 需要）—— 单开 Issue，先改 spec 与 architecture 第 3 章
-- 12. 模拟面试会话 / 报告表（T9 报告归档）—— 同上
-- 【口径修正】
-- 13. #107 第 2 条验收标准改为「字段覆盖各模块 spec 中出现的字段，以 spec 为准」
-- =============================================================

SET NAMES utf8mb4;

-- 库名可按环境替换；重复执行不报错
CREATE DATABASE IF NOT EXISTS campus_career DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE campus_career;

-- ---------- 1. student 学生账号 ----------
CREATE TABLE IF NOT EXISTS student (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_no    VARCHAR(32)  NOT NULL                COMMENT '学号，唯一',
  name          VARCHAR(64)  NOT NULL                COMMENT '姓名',
  college       VARCHAR(64)  NULL                    COMMENT '院系',
  major         VARCHAR(64)  NULL                    COMMENT '专业',
  grad_year     SMALLINT     NULL                    COMMENT '毕业年份',
  role          VARCHAR(16)  NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT（spec 未涉及登录，不建密码列）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_no (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生账号';

-- ---------- 2. resume 简历与解析结果 ----------
-- 每个学生一份有效简历；解析结果停在待确认态，确认后才对下游生效（M1 AC-2）
CREATE TABLE IF NOT EXISTS resume (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  student_id     BIGINT       NOT NULL COMMENT '所属学生',
  file_name      VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_path      VARCHAR(512) NOT NULL COMMENT '存储路径',
  file_size      INT          NOT NULL COMMENT '字节数，超限由应用层拦 413 RESUME_TOO_LARGE',
  parsed_basic   JSON         NULL     COMMENT '基本信息（姓名/联系方式等）',
  parsed_edu     JSON         NULL     COMMENT '教育经历',
  parsed_skill   JSON         NULL     COMMENT '专业技能，可为空数组；确认环节可增删',
  parsed_project JSON         NULL     COMMENT '项目经历（M1 AC-1 四类之一）',
  parsed_exp     JSON         NULL     COMMENT '实习/工作经历',
  skill_empty    TINYINT(1)   GENERATED ALWAYS AS
                 (CASE WHEN parsed_skill IS NULL OR JSON_LENGTH(parsed_skill) = 0 THEN 1 ELSE 0 END) STORED
                 COMMENT '1=技能为空，由 parsed_skill 自动维护，供 M2 AC-5 降级判定',
  confirm_status VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待确认 / CONFIRMED 已确认',
  parsed_at      DATETIME     NULL     COMMENT '解析完成时间',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_resume_student (student_id),
  CONSTRAINT fk_resume_student FOREIGN KEY (student_id) REFERENCES student (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历与解析结果';

-- ---------- 3. holland_profile 霍兰德画像 ----------
-- 与 student 一对一；重新生成时整体替换（M2 AC-3），不保留历史行
CREATE TABLE IF NOT EXISTS holland_profile (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  student_id        BIGINT      NOT NULL,
  score_r           TINYINT     NOT NULL DEFAULT 0 COMMENT '现实型',
  score_i           TINYINT     NOT NULL DEFAULT 0 COMMENT '研究型',
  score_a           TINYINT     NOT NULL DEFAULT 0 COMMENT '艺术型',
  score_s           TINYINT     NOT NULL DEFAULT 0 COMMENT '社会型',
  score_e           TINYINT     NOT NULL DEFAULT 0 COMMENT '企业型',
  score_c           TINYINT     NOT NULL DEFAULT 0 COMMENT '常规型',
  top_dimension     CHAR(1)     NOT NULL COMMENT 'R/I/A/S/E/C 中最高维',
  recommend_dirs    JSON        NULL     COMMENT '推荐方向（M2 画像四项之一）',
  intent_cities     JSON        NULL     COMMENT '意向城市（学生问卷填写）',
  expected_industry VARCHAR(128) NULL    COMMENT '期望行业',
  skill_gaps        JSON        NULL     COMMENT '技能短板（M2 FR-6 四项之一）',
  skill_missing     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '1=简历技能为空，画像降级（M2 AC-5）',
  revision          INT         NOT NULL DEFAULT 1 COMMENT '第几次生成，重新生成时 +1 并整体替换',
  generated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_holland_student (student_id),
  CONSTRAINT fk_holland_student FOREIGN KEY (student_id) REFERENCES student (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='霍兰德画像';

-- ---------- 4. job 岗位外链元数据 ----------
CREATE TABLE IF NOT EXISTS job (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  title           VARCHAR(128) NOT NULL COMMENT '岗位名称',
  company         VARCHAR(128) NULL,
  location        VARCHAR(64)  NULL,
  salary_range    VARCHAR(64)  NULL,
  jd_text         TEXT         NULL     COMMENT '岗位描述，M4 状态流转时要求非空',
  external_url    VARCHAR(512) NOT NULL COMMENT '第三方岗位链接',
  source          VARCHAR(32)  NULL     COMMENT '来源站点标识',
  link_status     VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'VALID/INVALID/UNKNOWN，外链失效标记',
  last_checked_at DATETIME     NULL     COMMENT '最近一次可达性探测时间（M7 3 秒判定）',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_external_url (external_url),
  KEY idx_job_link_status (link_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位外链元数据';

-- ---------- 5. application 投递记录 ----------
-- 唯一约束防重复投递（M4 AC-2/AC-3；ADR-001：唯一约束 + 乐观锁）
CREATE TABLE IF NOT EXISTS application (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  student_id  BIGINT      NOT NULL,
  job_id      BIGINT      NOT NULL,
  status      VARCHAR(24) NOT NULL DEFAULT 'APPLIED' COMMENT '投递状态；非法流转返 409 INVALID_STATUS_TRANSITION；取值集合待 T8 确认',
  applied_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version     INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（ADR-001）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_student_job (student_id, job_id),
  KEY idx_app_job (job_id),
  CONSTRAINT fk_app_student FOREIGN KEY (student_id) REFERENCES student (id),
  CONSTRAINT fk_app_job FOREIGN KEY (job_id) REFERENCES job (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投递记录';

-- ---------- 6. interview 面试登记 ----------
CREATE TABLE IF NOT EXISTS interview (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  student_id     BIGINT       NOT NULL,
  company        VARCHAR(128) NOT NULL,
  position       VARCHAR(128) NULL,
  location       VARCHAR(255) NULL     COMMENT '面试地点；边界表有「未填面试地点」',
  format         VARCHAR(32)  NULL     COMMENT '面试形式，如 ONSITE/ONLINE/PHONE',
  start_time     DATETIME     NOT NULL COMMENT '面试开始时间',
  departure_time DATETIME     NULL     COMMENT '出发时间；为空时按开始时间前推计算（M6 NFR-1）',
  status         VARCHAR(16)  NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED/RESCHEDULED/CANCELED/DONE（4.6.2 改期返回 RESCHEDULED）',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_interview_student (student_id),
  KEY idx_interview_start (start_time),
  CONSTRAINT fk_interview_student FOREIGN KEY (student_id) REFERENCES student (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试登记';

-- ---------- 7. reminder 提醒任务 ----------
-- 5 次触发 = 4 个节点 + 复盘；唯一约束防重复触发（M6）
CREATE TABLE IF NOT EXISTS reminder (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  interview_id BIGINT       NOT NULL,
  node_type    VARCHAR(24)  NOT NULL COMMENT 'module-06 枚举：D3 前3天 / D1 前1天 / H2_DEPARTURE 前2小时按出发时间倒推 / M30 前30分钟 / POST_REVIEW 结束后复盘',
  remind_at    DATETIME     NOT NULL COMMENT '计划触发时间',
  sent_at      DATETIME     NULL     COMMENT '实际发送时间',
  status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
  retry_count  TINYINT      NOT NULL DEFAULT 0 COMMENT '已重试次数，上限 3，间隔 5 分钟',
  fail_reason  VARCHAR(255) NULL     COMMENT '失败原因，如 REMINDER_SEND_FAILED',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reminder_interview_node (interview_id, node_type),
  CONSTRAINT fk_reminder_interview FOREIGN KEY (interview_id) REFERENCES interview (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提醒任务';

-- ---------- 8. interview_note 面经外链 ----------
-- 只存外链与元数据，不存面经正文（M7 范围）
CREATE TABLE IF NOT EXISTS interview_note (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  student_id      BIGINT       NULL     COMMENT '收录人，外部抓取时可为空',
  source_url      VARCHAR(512) NOT NULL COMMENT '面经来源链接',
  title           VARCHAR(255) NULL,
  company         VARCHAR(128) NULL,
  position        VARCHAR(128) NULL,
  tags            VARCHAR(255) NULL    COMMENT '标签，逗号分隔，用于筛选',
  status          VARCHAR(16)  NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'VALID/INVALID/UNVERIFIED',
  last_checked_at DATETIME     NULL    COMMENT '最近一次失效探测时间',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_note_source_url (source_url),
  KEY idx_note_status (status),
  KEY idx_note_company (company)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面经外链';
-- ---------- 9. holland_quiz_session 问卷作答会话 ----------
-- 一个学生一份问卷；提交后作答只读，重复提交返回 409 QUIZ_DUPLICATE_SUBMIT（M2 AC-2/AC-6）
CREATE TABLE IF NOT EXISTS holland_quiz_session (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  student_id   BIGINT      NOT NULL,
  round_no     INT         NOT NULL DEFAULT 1 COMMENT '作答轮次；重新作答新建一轮，历史轮次只读（M2 FR-7）。落库必须显式取 MAX(round_no)+1，不依赖默认值',
  status       VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS 作答中 / SUBMITTED 已提交',
  started_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次作答时间，用于续答（M2 AC-1）',
  submitted_at DATETIME    NULL     COMMENT '提交时间；非空即作答只读',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_quiz_session_student_round (student_id, round_no),
  KEY idx_quiz_session_status (status),
  CONSTRAINT fk_quiz_session_student FOREIGN KEY (student_id) REFERENCES student (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='霍兰德问卷作答会话';

-- ---------- 10. quiz_answer 问卷逐题作答 ----------
-- 每题一条；分值口径由题库选项决定，用于六维汇总（M2 AC-2）
CREATE TABLE IF NOT EXISTS quiz_answer (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  session_id  BIGINT   NOT NULL,
  question_id INT      NOT NULL COMMENT '题库题号，题库常量在代码中维护',
  dimension   CHAR(1)  NOT NULL COMMENT 'R/I/A/S/E/C',
  score       TINYINT  NOT NULL COMMENT '该题所选选项对应分值，口径见题库选项定义',
  answered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_quiz_answer_session_question (session_id, question_id),
  CONSTRAINT fk_quiz_answer_session FOREIGN KEY (session_id) REFERENCES holland_quiz_session (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷逐题作答';

-- ---------- 11. mock_interview 模拟面试会话 ----------
-- M5 不调用大模型；题面与评分来自本地题库与关键词匹配
CREATE TABLE IF NOT EXISTS mock_interview (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  student_id      BIGINT       NOT NULL,
  job_id          BIGINT       NULL     COMMENT '关联 job(id)，可为空；为空时降级手填 jd_text（M5 AC-4）',
  jd_text         TEXT         NULL     COMMENT '手填 JD，≥50 字；job_id 为空时必填（M5 AC-4）',
  status          VARCHAR(16)  NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS / ARCHIVED / CANCELLED',
  total_questions TINYINT      NOT NULL COMMENT '本次抽题数，5-8 道',
  total_score     TINYINT      NULL     COMMENT '归档后写入总分 0-100',
  started_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  archived_at     DATETIME     NULL     COMMENT '归档时间；归档幂等（M5 AC-3）',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_mock_interview_student (student_id),
  KEY idx_mock_interview_status (status),
  CONSTRAINT fk_mock_interview_student FOREIGN KEY (student_id) REFERENCES student (id),
  CONSTRAINT fk_mock_interview_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟面试会话';

-- ---------- 12. mock_interview_answer 模拟面试单题作答 ----------
CREATE TABLE IF NOT EXISTS mock_interview_answer (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  session_id       BIGINT       NOT NULL,
  question_id      INT          NOT NULL,
  question_content VARCHAR(512) NOT NULL COMMENT '题面冗余存储，避免题库变更导致历史报告失真',
  dimension        VARCHAR(16)  NOT NULL COMMENT '专业知识 / 逻辑表达 / 项目经验 / 岗位匹配度',
  answer_text      TEXT         NOT NULL COMMENT '学生作答原文',
  score            TINYINT      NULL     COMMENT '单题得分 0-100；未评分时为空',
  score_status     VARCHAR(16)  NULL     COMMENT 'COMPLETE / PARTIAL（维度未命中或极短回答，M5 AC-5）',
  feedback         VARCHAR(512) NULL     COMMENT '关键词命中或展开提示',
  answered_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mock_answer_session_question (session_id, question_id),
  CONSTRAINT fk_mock_answer_session FOREIGN KEY (session_id) REFERENCES mock_interview (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟面试单题作答';

-- ---------- 13. mock_interview_report 模拟面试报告（归档） ----------
-- 与 mock_interview 一对一；重复归档不新增行（M5 AC-3 幂等）
CREATE TABLE IF NOT EXISTS mock_interview_report (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  session_id         BIGINT       NOT NULL,
  total_score        TINYINT      NOT NULL COMMENT '总分 0-100',
  score_professional TINYINT      NOT NULL DEFAULT 0 COMMENT '专业知识',
  score_logic        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑表达',
  score_experience   TINYINT      NOT NULL DEFAULT 0 COMMENT '项目经验',
  score_match        TINYINT      NOT NULL DEFAULT 0 COMMENT '岗位匹配度',
  summary            VARCHAR(512) NULL     COMMENT '评价总结；维度缺项在此标注',
  archived_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mock_report_session (session_id),
  CONSTRAINT fk_mock_report_session FOREIGN KEY (session_id) REFERENCES mock_interview (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟面试报告归档';

-- ---------- 14. v_dashboard 只读看板视图（M8 不建业务表） ----------
-- 全部用 COUNT(DISTINCT ...) 聚合，避免多表 LEFT JOIN 扇出放大
CREATE OR REPLACE VIEW v_dashboard AS
SELECT
  s.id                                                                AS student_id,
  s.student_no,
  s.name,
  r.confirm_status                                                    AS resume_status,
  r.parsed_at                                                         AS resume_parsed_at,
  h.top_dimension                                                     AS holland_top,
  h.skill_missing                                                     AS holland_skill_missing,
  h.generated_at                                                      AS profile_generated_at,
  COUNT(DISTINCT a.id)                                                AS application_count,
  COUNT(DISTINCT CASE WHEN a.status = 'APPLIED' THEN a.id END)        AS application_applied_count,
  COUNT(DISTINCT i.id)                                                AS interview_count,
  COUNT(DISTINCT CASE WHEN i.status = 'SCHEDULED' THEN i.id END)      AS interview_upcoming_count,
  COUNT(DISTINCT n.id)                                                AS note_count
FROM student s
LEFT JOIN resume          r ON r.student_id = s.id
LEFT JOIN holland_profile h ON h.student_id = s.id
LEFT JOIN application     a ON a.student_id = s.id
LEFT JOIN interview       i ON i.student_id = s.id
LEFT JOIN interview_note  n ON n.student_id = s.id
GROUP BY s.id, s.student_no, s.name,
         r.confirm_status, r.parsed_at,
         h.top_dimension, h.skill_missing, h.generated_at;
