-- Migration: 0001_create_user_profiles.sql
CREATE TABLE IF NOT EXISTS user_profiles (
    bid TEXT PRIMARY KEY,
    wiki_user_id INTEGER,
    custom_name TEXT,
    avatar_url TEXT,
    bio TEXT,
    badge TEXT,
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    updated_at INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_wiki_id ON user_profiles(wiki_user_id);
