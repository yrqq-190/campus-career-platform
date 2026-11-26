-- draft-m5.sql (M5 模拟面试模块建表草案)

CREATE TABLE `mock_interviews` (
  `id` VARCHAR(36) PRIMARY KEY COMMENT '会话ID',
  `student_id` VARCHAR(36) NOT NULL COMMENT '学生ID',
  `job_id` VARCHAR(36) COMMENT '岗位ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态: IN_PROGRESS, ARCHIVED',
  `total_score` INT DEFAULT NULL COMMENT '总分',
  `dimension_scores` JSON DEFAULT NULL COMMENT '四维分项分JSON',
  `summary` TEXT DEFAULT NULL COMMENT '评价总结',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `archived_at` DATETIME DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `mock_interview_answers` (
  `id` VARCHAR(36) PRIMARY KEY,
  `session_id` VARCHAR(36) NOT NULL,
  `question_id` INT NOT NULL,
  `answer_text` TEXT NOT NULL,
  `score` INT NOT NULL,
  `score_status` VARCHAR(20) NOT NULL COMMENT 'COMPLETE/PARTIAL',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
