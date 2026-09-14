-- Migration: 0003_anonymous_comments.sql
ALTER TABLE profile_comments ADD COLUMN author_name TEXT;
ALTER TABLE profile_comments ADD COLUMN author_ip_hash TEXT;

-- 简易写入限流（按用户 BID 或匿名 IP 哈希）
CREATE TABLE IF NOT EXISTS write_throttle (
    key TEXT PRIMARY KEY,
    last_at INTEGER NOT NULL
);
