ALTER TABLE write_throttle ADD COLUMN token TEXT;
CREATE INDEX IF NOT EXISTS idx_write_throttle_last_at ON write_throttle(last_at);
CREATE TABLE IF NOT EXISTS write_requests (
    actor TEXT NOT NULL,
    request_key TEXT NOT NULL,
    fingerprint TEXT NOT NULL,
    comment_id INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (actor, request_key)
);
CREATE INDEX IF NOT EXISTS idx_write_requests_created ON write_requests(created_at);
CREATE INDEX IF NOT EXISTS idx_comments_cursor ON profile_comments(target_bid, created_at DESC, id DESC);
CREATE TABLE IF NOT EXISTS avatar_assets (
    object_key TEXT PRIMARY KEY,
    owner_bid TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    state TEXT NOT NULL DEFAULT 'active' CHECK (state IN ('active', 'deleting'))
);
CREATE INDEX IF NOT EXISTS idx_avatar_assets_created ON avatar_assets(state, created_at);
CREATE TABLE IF NOT EXISTS maintenance_state (
    key TEXT PRIMARY KEY,
    value TEXT
);
