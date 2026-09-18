ALTER TABLE profile_comments ADD COLUMN hidden_at INTEGER;
ALTER TABLE profile_comments ADD COLUMN pinned_at INTEGER;
CREATE INDEX idx_comments_pinned ON profile_comments(target_bid,pinned_at DESC) WHERE pinned_at IS NOT NULL;
CREATE TABLE admin_audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    target TEXT NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);
