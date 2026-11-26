-- MVP1 演示数据（幂等：重复执行不新增重复行）
-- 执行顺序：schema.sql → seed.sql → grants.sql
USE campus_career;

-- 演示学生
INSERT INTO student (student_no, name, college, major, grad_year)
VALUES ('2023001', '张演示', '计算机学院', '软件工程', 2027),
       ('2023002', '李演示', '计算机学院', '软件工程', 2027)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 演示岗位
INSERT INTO job (title, company, location, salary_range, jd_text, external_url, source, link_status)
VALUES
('Java 后端开发', '演示科技', '杭州', '15-25K',
 '负责后端服务开发，熟悉 Java、Spring Boot、MySQL，参与高并发系统设计与优化。',
 'https://example.com/jobs/1', 'demo', 'VALID'),
('前端开发工程师', '演示网络', '苏州', '12-20K',
 '负责 Vue 3 + TypeScript 前端开发，熟悉 Element Plus 与工程化构建。',
 'https://example.com/jobs/2', 'demo', 'VALID'),
('数据分析师', '演示数据', '上海', '13-22K',
 '负责业务数据建模与报表开发，熟悉 SQL 与 Python 数据分析。',
 'https://example.com/jobs/3', 'demo', 'VALID')
ON DUPLICATE KEY UPDATE title = VALUES(title);

-- 面经演示数据（含 is_demo 标识）待 #147 合并后补入，避免在该四列不存在时执行失败
