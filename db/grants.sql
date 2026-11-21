-- =============================================================
-- T1 / Issue #107  看板只读账号授权
-- 与 db/schema.sql 分开执行：CREATE USER / GRANT 需要更高权限，
-- 混进建表脚本会让本地普通账号执行失败。
-- 用法：mysql -u root -p < db/grants.sql
-- =============================================================

-- 库名与 schema.sql 保持一致，按环境替换
-- CREATE DATABASE IF NOT EXISTS campus_career DEFAULT CHARSET utf8mb4;

-- 看板只读账号：仅允许 SELECT v_dashboard（M8 越权隔离，403 DASHBOARD_FORBIDDEN 的前置）
CREATE USER IF NOT EXISTS 'career_dashboard'@'%' IDENTIFIED BY 'CHANGE_ME_IN_ENV';

GRANT SELECT ON campus_career.v_dashboard TO 'career_dashboard'@'%';

FLUSH PRIVILEGES;

-- 校验：应只看到 v_dashboard 一项授权
-- SHOW GRANTS FOR 'career_dashboard'@'%';
