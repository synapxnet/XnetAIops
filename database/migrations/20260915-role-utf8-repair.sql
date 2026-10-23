-- Copyright (C) 2026 Synapxnet. All rights reserved.
-- This file is Synapxnet Proprietary and Confidential. It is strictly
-- forbidden to copy, distribute, or use without explicit authorization.
-- 修复已核实的内置角色错误编码，并保留原始行。 Repair verified seed-role mojibake while preserving original rows.
-- Author: maoyo | Department: 研发部 | Date: 2026-09-15 | Version: 1.0.0
-- Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com

SET NAMES utf8mb4;
USE XnetAIops;

-- 保留第一次修复前快照；重跑不会覆盖原值。 Preserve the first pre-repair snapshot; reruns never replace original values.
CREATE TABLE IF NOT EXISTS xnet_aiops_usr_role_utf8_backup_20260915 LIKE xnet_aiops_usr_role;
INSERT IGNORE INTO xnet_aiops_usr_role_utf8_backup_20260915
SELECT * FROM xnet_aiops_usr_role
WHERE (id = 1 AND role_code = 'ADMIN')
   OR (id = 2 AND role_code = 'OPERATOR')
   OR (id = 3 AND role_code = 'VIEWER');

START TRANSACTION;

-- 仅匹配审计确认的旧字节；用户后来编辑的名称或描述保持原值。 Match verified bad bytes only; preserve any later user edits.
UPDATE xnet_aiops_usr_role SET role_name = CONVERT(0xE7AEA1E79086E59198 USING utf8mb4)
WHERE id = 1 AND role_code = 'ADMIN' AND HEX(role_name) = 'C3A7C2AEC2A1C3A7C290E280A0C3A5E28098CB9C';
UPDATE xnet_aiops_usr_role SET description = CONVERT(0xE7B3BBE7BB9FE7AEA1E79086E59198EFBC8CE68BA5E69C89E68980E69C89E69D83E99990 USING utf8mb4)
WHERE id = 1 AND role_code = 'ADMIN' AND HEX(description) = 'C3A7C2B3C2BBC3A7C2BBC5B8C3A7C2AEC2A1C3A7C290E280A0C3A5E28098CB9CC3AFC2BCC592C3A6E280B9C2A5C3A6C593E280B0C3A6E280B0E282ACC3A6C593E280B0C3A6C29DC692C3A9E284A2C290';

UPDATE xnet_aiops_usr_role SET role_name = CONVERT(0xE8BF90E7BBB4E4BABAE59198 USING utf8mb4)
WHERE id = 2 AND role_code = 'OPERATOR' AND HEX(role_name) = 'C3A8C2BFC290C3A7C2BBC2B4C3A4C2BAC2BAC3A5E28098CB9C';
UPDATE xnet_aiops_usr_role SET description = CONVERT(0xE8BF90E7BBB4E6938DE4BD9CE4BABAE59198EFBC8CE58FAFE7AEA1E79086E99B86E7BEA4E5928CE69C8DE58AA1 USING utf8mb4)
WHERE id = 2 AND role_code = 'OPERATOR' AND HEX(description) = 'C3A8C2BFC290C3A7C2BBC2B4C3A6E2809CC28DC3A4C2BDC593C3A4C2BAC2BAC3A5E28098CB9CC3AFC2BCC592C3A5C28FC2AFC3A7C2AEC2A1C3A7C290E280A0C3A9E280BAE280A0C3A7C2BEC2A4C3A5E28099C592C3A6C593C28DC3A5C5A0C2A1';

UPDATE xnet_aiops_usr_role SET role_name = CONVERT(0xE8A782E5AF9FE88085 USING utf8mb4)
WHERE id = 3 AND role_code = 'VIEWER' AND HEX(role_name) = 'C3A8C2A7E2809AC3A5C2AFC5B8C3A8E282ACE280A6';
UPDATE xnet_aiops_usr_role SET description = CONVERT(0xE58FAAE8AFBBE69D83E99990EFBC8CE69FA5E79C8BE99B86E7BEA4E78AB6E68081 USING utf8mb4)
WHERE id = 3 AND role_code = 'VIEWER' AND HEX(description) = 'C3A5C28FC2AAC3A8C2AFC2BBC3A6C29DC692C3A9E284A2C290C3AFC2BCC592C3A6C5B8C2A5C3A7C593E280B9C3A9E280BAE280A0C3A7C2BEC2A4C3A7C5A0C2B6C3A6E282ACC281';

COMMIT;

SELECT id, role_code, role_name, description
FROM xnet_aiops_usr_role WHERE id IN (1, 2, 3) ORDER BY id;
