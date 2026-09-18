-- Existing rows remain top-level comments. Replies always point to a top-level root.
ALTER TABLE profile_comments ADD COLUMN root_id INTEGER;
ALTER TABLE profile_comments ADD COLUMN reply_to_id INTEGER;
ALTER TABLE profile_comments ADD COLUMN deleted_at INTEGER;
CREATE INDEX IF NOT EXISTS idx_comments_thread ON profile_comments(root_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_comments_roots ON profile_comments(target_bid, created_at DESC, id DESC) WHERE root_id IS NULL;
