CREATE TABLE reply_notifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recipient_bid TEXT NOT NULL,
    comment_id INTEGER NOT NULL,
    root_id INTEGER NOT NULL,
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    read_at INTEGER,
    UNIQUE(recipient_bid, comment_id)
);
CREATE INDEX idx_notifications_recipient ON reply_notifications(recipient_bid, id DESC);
CREATE INDEX idx_notifications_unread ON reply_notifications(recipient_bid, id) WHERE read_at IS NULL;
