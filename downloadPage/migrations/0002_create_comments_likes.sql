-- Migration: 0002_create_comments_likes.sql
CREATE TABLE IF NOT EXISTS profile_comments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    target_bid TEXT NOT NULL,
    author_bid TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_profile_comments_target ON profile_comments(target_bid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_profile_comments_author ON profile_comments(author_bid);

CREATE TABLE IF NOT EXISTS profile_likes (
    target_bid TEXT NOT NULL,
    author_bid TEXT NOT NULL,
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    PRIMARY KEY (target_bid, author_bid)
);

CREATE INDEX IF NOT EXISTS idx_profile_likes_target ON profile_likes(target_bid);
