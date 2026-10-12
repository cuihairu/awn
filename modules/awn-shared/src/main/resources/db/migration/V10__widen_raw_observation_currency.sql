-- 原始观察层保留来源原样数据（质量检查在标准化阶段执行）：
-- 来源币种可能不规范（如 "RMB"），列宽必须容纳后再由质量检查拒绝
ALTER TABLE intelligence.raw_observations ALTER COLUMN currency TYPE varchar(16);
