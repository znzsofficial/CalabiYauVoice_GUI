-- Migration: 0004_anonymous_tag.sql
-- 匿名留言的访客编号（如 "042"），由服务端按密钥+IP 派生后落库，展示为「访客#042」
ALTER TABLE profile_comments ADD COLUMN author_tag TEXT;
