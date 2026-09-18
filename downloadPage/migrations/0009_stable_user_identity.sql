-- New writes use immutable MediaWiki user IDs. BID is retained for display and legacy rows.
ALTER TABLE profile_comments ADD COLUMN author_wiki_user_id INTEGER;
UPDATE profile_comments SET author_wiki_user_id=(
    SELECT wiki_user_id FROM user_profiles p WHERE p.bid=profile_comments.author_bid
) WHERE author_bid<>'anon' AND author_wiki_user_id IS NULL;
CREATE INDEX idx_comments_author_wiki_id ON profile_comments(author_wiki_user_id);

ALTER TABLE reply_notifications ADD COLUMN recipient_wiki_user_id INTEGER;
UPDATE reply_notifications SET recipient_wiki_user_id=(
    SELECT parent.author_wiki_user_id
    FROM profile_comments reply
    JOIN profile_comments parent ON parent.id=reply.reply_to_id
    WHERE reply.id=reply_notifications.comment_id
) WHERE recipient_wiki_user_id IS NULL;
UPDATE reply_notifications SET recipient_wiki_user_id=(
    SELECT wiki_user_id FROM user_profiles p WHERE p.bid=reply_notifications.recipient_bid
) WHERE recipient_wiki_user_id IS NULL;
CREATE INDEX idx_notifications_recipient_wiki_id ON reply_notifications(recipient_wiki_user_id,id DESC);
CREATE INDEX idx_notifications_unread_wiki_id ON reply_notifications(recipient_wiki_user_id,id) WHERE read_at IS NULL;
