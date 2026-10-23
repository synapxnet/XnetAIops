-- Copyright (C) 2026 Synapxnet. All rights reserved.
-- This file is Synapxnet Proprietary and Confidential. It is strictly
-- forbidden to copy, distribute, or use without explicit authorization.
-- 比赛预置身份归档维护 / Archive unchanged competition seed identities.
-- Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.0.0
-- Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
-- 手动维护：先独立备份数据库，并暂停账号及组织管理写入。应用和 Compose 不运行本脚本。
-- Manual maintenance: back up the database and pause identity writes. Never run at application startup.

-- 完整身份与原始密码摘要同时匹配；真实账号、改过密码的账号均不会命中。
-- Match the complete seed identity AND its original password fingerprint; never match by phone/name alone.
SET @seed_password_sha256 = 'cc18e99ceed22931956ae5a3d078e3afe86d2d01003468fd915c7299bb60ebeb';
SELECT id, uid, username, status FROM xnet_aiops_usr_user
WHERE uid = 'USR-GOAI-OPERATOR' AND username = 'goai_operator' AND phone = '17870171303'
  AND SHA2(password, 256) = @seed_password_sha256;
SELECT id, uid, team_name, status FROM xnet_aiops_sys_team
WHERE uid = 'TEAM-GOAI-INFRA' AND team_name = 'GOAI Infrastructure 联合团队';

-- 默认0仅预览并建立空归档表，不更改业务记录；核对候选及备份后手动改成1。
-- Default zero previews and creates empty archive tables only. Set to one after reviewing candidates and backup.
SET @apply_goai_cleanup = 0;
CREATE TABLE IF NOT EXISTS archive_aiops_goai_users_20261023 LIKE xnet_aiops_usr_user;
CREATE TABLE IF NOT EXISTS archive_aiops_goai_roles_20261023 LIKE xnet_aiops_usr_user_role_cluster;
CREATE TABLE IF NOT EXISTS archive_aiops_goai_memberships_20261023 LIKE xnet_aiops_usr_organization_membership;
CREATE TABLE IF NOT EXISTS archive_aiops_goai_sessions_20261023 LIKE xnet_aiops_usr_session;
CREATE TABLE IF NOT EXISTS archive_aiops_goai_teams_20261023 LIKE xnet_aiops_sys_team;
DROP TEMPORARY TABLE IF EXISTS mainline_aiops_seed_users;
DROP TEMPORARY TABLE IF EXISTS mainline_aiops_seed_teams;
CREATE TEMPORARY TABLE mainline_aiops_seed_users (id BIGINT PRIMARY KEY);
CREATE TEMPORARY TABLE mainline_aiops_seed_teams (id BIGINT PRIMARY KEY);

START TRANSACTION;
-- 锁定本次仍精确匹配的账号，历史归档不能单独成为后续删除依据。
-- Lock this run's exact candidates; an old archive alone must never authorize later cleanup.
INSERT INTO mainline_aiops_seed_users (id)
SELECT id FROM xnet_aiops_usr_user
WHERE @apply_goai_cleanup = 1 AND uid = 'USR-GOAI-OPERATOR'
  AND username = 'goai_operator' AND phone = '17870171303'
  AND SHA2(password, 256) = @seed_password_sha256 FOR UPDATE;
INSERT INTO mainline_aiops_seed_teams (id)
SELECT t.id FROM xnet_aiops_sys_team t
WHERE @apply_goai_cleanup = 1 AND t.uid = 'TEAM-GOAI-INFRA'
  AND t.team_name = 'GOAI Infrastructure 联合团队'
  AND NOT EXISTS (
    SELECT 1 FROM xnet_aiops_usr_organization_membership m
    LEFT JOIN mainline_aiops_seed_users u ON u.id = m.user_id
    WHERE m.team_uid = t.uid AND u.id IS NULL
  ) FOR UPDATE;

INSERT IGNORE INTO archive_aiops_goai_users_20261023
SELECT u.* FROM xnet_aiops_usr_user u JOIN mainline_aiops_seed_users c ON c.id = u.id;
INSERT IGNORE INTO archive_aiops_goai_roles_20261023
SELECT r.* FROM xnet_aiops_usr_user_role_cluster r JOIN mainline_aiops_seed_users c ON c.id = r.user_id;
INSERT IGNORE INTO archive_aiops_goai_memberships_20261023
SELECT m.* FROM xnet_aiops_usr_organization_membership m JOIN mainline_aiops_seed_users c ON c.id = m.user_id;
INSERT IGNORE INTO archive_aiops_goai_sessions_20261023
SELECT s.* FROM xnet_aiops_usr_session s JOIN mainline_aiops_seed_users c ON c.id = s.user_id;
INSERT IGNORE INTO archive_aiops_goai_teams_20261023
SELECT t.* FROM xnet_aiops_sys_team t JOIN mainline_aiops_seed_teams c ON c.id = t.id;

-- 归档冲突或内容不一致时停止全部变更，避免覆盖旧备份或丢失关联。
-- Stop all mutations on archive conflicts; never overwrite an earlier backup or lose current bindings.
SET @archive_complete =
  NOT EXISTS (
    SELECT 1 FROM xnet_aiops_usr_user u JOIN mainline_aiops_seed_users c ON c.id = u.id
    LEFT JOIN archive_aiops_goai_users_20261023 a ON a.id = u.id
      AND a.uid = u.uid AND a.username = u.username AND a.password = u.password
      AND a.phone <=> u.phone AND a.email <=> u.email AND a.user_type <=> u.user_type
      AND a.status <=> u.status AND a.last_login_at <=> u.last_login_at
      AND a.created_at <=> u.created_at AND a.updated_at <=> u.updated_at
    WHERE a.id IS NULL
  );
SET @archive_complete = @archive_complete AND NOT EXISTS (
    SELECT 1 FROM xnet_aiops_usr_user_role_cluster r JOIN mainline_aiops_seed_users c ON c.id = r.user_id
    LEFT JOIN archive_aiops_goai_roles_20261023 a ON a.id = r.id AND a.user_id = r.user_id
      AND a.role_id = r.role_id AND a.cluster_id <=> r.cluster_id WHERE a.id IS NULL
  );
SET @archive_complete = @archive_complete AND NOT EXISTS (
    SELECT 1 FROM xnet_aiops_usr_organization_membership m JOIN mainline_aiops_seed_users c ON c.id = m.user_id
    LEFT JOIN archive_aiops_goai_memberships_20261023 a ON a.id = m.id AND a.user_id = m.user_id
      AND a.tenant_uid = m.tenant_uid AND a.dept_uid <=> m.dept_uid AND a.team_uid <=> m.team_uid
      AND a.status = m.status AND a.data_access_enabled = m.data_access_enabled
      AND a.created_at <=> m.created_at WHERE a.id IS NULL
  );
SET @archive_complete = @archive_complete AND NOT EXISTS (
    SELECT 1 FROM xnet_aiops_usr_session s JOIN mainline_aiops_seed_users c ON c.id = s.user_id
    LEFT JOIN archive_aiops_goai_sessions_20261023 a ON a.id = s.id AND a.user_id = s.user_id
      AND a.token = s.token AND a.ip <=> s.ip AND a.expire_at = s.expire_at
      AND a.created_at <=> s.created_at WHERE a.id IS NULL
  );
SET @archive_complete = @archive_complete AND NOT EXISTS (
    SELECT 1 FROM xnet_aiops_sys_team t JOIN mainline_aiops_seed_teams c ON c.id = t.id
    LEFT JOIN archive_aiops_goai_teams_20261023 a ON a.id = t.id AND a.uid = t.uid
      AND a.dept_uid = t.dept_uid AND a.team_name = t.team_name AND a.status = t.status
      AND a.created_at <=> t.created_at WHERE a.id IS NULL
  );
SELECT @apply_goai_cleanup AS apply_requested, @archive_complete AS archive_verified;

-- 保留停用身份与历史审计；只撤销该比赛身份的会话、角色和组织绑定。
-- Retain the disabled audit principal; revoke only its sessions, roles and organization memberships.
DELETE s FROM xnet_aiops_usr_session s JOIN mainline_aiops_seed_users c ON c.id = s.user_id WHERE @archive_complete = 1;
DELETE r FROM xnet_aiops_usr_user_role_cluster r JOIN mainline_aiops_seed_users c ON c.id = r.user_id WHERE @archive_complete = 1;
DELETE m FROM xnet_aiops_usr_organization_membership m JOIN mainline_aiops_seed_users c ON c.id = m.user_id WHERE @archive_complete = 1;
UPDATE xnet_aiops_usr_user u JOIN mainline_aiops_seed_users c ON c.id = u.id SET u.status = 'disabled' WHERE @archive_complete = 1;
UPDATE xnet_aiops_sys_team t JOIN mainline_aiops_seed_teams c ON c.id = t.id SET t.status = 0
WHERE @archive_complete = 1 AND NOT EXISTS (SELECT 1 FROM xnet_aiops_usr_organization_membership m WHERE m.team_uid = t.uid);
COMMIT;

-- 通用角色、租户/部门、业务资源、运行记录和审批审计保持原样。
-- Keep shared roles, tenants/departments, resources, runs and approval/audit records unchanged.
SELECT u.id, u.uid, u.status FROM xnet_aiops_usr_user u JOIN mainline_aiops_seed_users c ON c.id = u.id;
DROP TEMPORARY TABLE mainline_aiops_seed_users;
DROP TEMPORARY TABLE mainline_aiops_seed_teams;
