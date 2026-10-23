-- Copyright (C) 2026 Synapxnet. All rights reserved.
-- Synapxnet Proprietary and Confidential. No copying, distribution or use without authorization.
-- 组织访问字段迁移 / Organization-access column migration.
-- Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.3.0
-- Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
-- 在已选择的数据库内执行，不自动授予任何成员数据访问权。 Run in the selected database without granting access to any member.
SET @organization_access_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'xnet_aiops_usr_organization_membership' AND column_name = 'data_access_enabled'),
  'SELECT 1',
  'ALTER TABLE xnet_aiops_usr_organization_membership ADD COLUMN data_access_enabled TINYINT NOT NULL DEFAULT 0 COMMENT ''必须显式授予团队数据访问权'' AFTER status'
);
PREPARE organization_access_statement FROM @organization_access_ddl;
EXECUTE organization_access_statement;
DEALLOCATE PREPARE organization_access_statement;
