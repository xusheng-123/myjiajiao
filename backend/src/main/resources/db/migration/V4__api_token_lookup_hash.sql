-- Agent API Token 定位哈希：以未加盐 SHA-256(raw) 建唯一索引，
-- 验证时按定位哈希直查目标行，消除验证路径的全表遍历；
-- 盐值哈希仍作二次确认（时间安全比较），不降低存储安全性。
-- 既有行 lookup_hash 为空即自然失效（明文不可逆推，需重新签发；开发期数据可接受）。
ALTER TABLE api_tokens ADD COLUMN lookup_hash VARCHAR(64);
CREATE UNIQUE INDEX ux_api_tokens_lookup_hash ON api_tokens (lookup_hash);
