-- Keep the original profile BID as its storage key. Verified names are aliases of
-- the immutable Wiki ID, so rename does not move boards or orphan avatars.
CREATE TABLE wiki_user_aliases (bid TEXT PRIMARY KEY, wiki_user_id INTEGER NOT NULL);
CREATE INDEX idx_wiki_aliases_id ON wiki_user_aliases(wiki_user_id);
INSERT INTO wiki_user_aliases(bid,wiki_user_id)
SELECT bid,MIN(wiki_user_id) FROM (
  SELECT bid,wiki_user_id FROM user_profiles WHERE wiki_user_id IS NOT NULL
  UNION ALL SELECT author_bid,author_wiki_user_id FROM profile_comments WHERE author_wiki_user_id IS NOT NULL
) GROUP BY bid HAVING COUNT(DISTINCT wiki_user_id)=1;
CREATE TABLE wiki_user_names (wiki_user_id INTEGER PRIMARY KEY, current_bid TEXT NOT NULL);
INSERT INTO wiki_user_names(wiki_user_id,current_bid)
SELECT a.wiki_user_id,COALESCE(
  (SELECT p.bid FROM user_profiles p WHERE p.wiki_user_id=a.wiki_user_id ORDER BY p.updated_at DESC,p.bid LIMIT 1),
  (SELECT c.author_bid FROM profile_comments c WHERE c.author_wiki_user_id=a.wiki_user_id ORDER BY c.created_at DESC,c.id DESC LIMIT 1)
) FROM wiki_user_aliases a GROUP BY a.wiki_user_id;

ALTER TABLE avatar_assets ADD COLUMN owner_wiki_user_id INTEGER;
UPDATE avatar_assets SET owner_wiki_user_id=(SELECT wiki_user_id FROM wiki_user_aliases a WHERE a.bid=avatar_assets.owner_bid);
CREATE INDEX idx_avatar_owner_id ON avatar_assets(owner_wiki_user_id);
ALTER TABLE profile_likes ADD COLUMN author_wiki_user_id INTEGER;
UPDATE profile_likes SET author_wiki_user_id=(SELECT wiki_user_id FROM wiki_user_aliases a WHERE a.bid=profile_likes.author_bid);
CREATE INDEX idx_likes_author_id ON profile_likes(target_bid,author_wiki_user_id);

-- Preserve every superseded profile before enforcing one profile per Wiki ID.
CREATE TABLE user_profile_archive AS
SELECT p.*,unixepoch() AS archived_at FROM user_profiles p WHERE p.wiki_user_id IS NOT NULL AND EXISTS(
  SELECT 1 FROM user_profiles newer WHERE newer.wiki_user_id=p.wiki_user_id
  AND (newer.updated_at>p.updated_at OR (newer.updated_at=p.updated_at AND newer.bid<p.bid))
);
DELETE FROM user_profiles WHERE bid IN (SELECT bid FROM user_profile_archive);
CREATE UNIQUE INDEX idx_profiles_unique_wiki_id ON user_profiles(wiki_user_id) WHERE wiki_user_id IS NOT NULL;
UPDATE profile_comments SET target_bid=(SELECT p.bid FROM wiki_user_aliases a JOIN user_profiles p ON p.wiki_user_id=a.wiki_user_id WHERE a.bid=profile_comments.target_bid)
WHERE target_bid<>'__public__' AND EXISTS(SELECT 1 FROM wiki_user_aliases a JOIN user_profiles p ON p.wiki_user_id=a.wiki_user_id WHERE a.bid=profile_comments.target_bid AND p.bid<>profile_comments.target_bid);
INSERT OR IGNORE INTO profile_likes(target_bid,author_bid,created_at,author_wiki_user_id)
SELECT p.bid,l.author_bid,l.created_at,l.author_wiki_user_id FROM profile_likes l JOIN wiki_user_aliases a ON a.bid=l.target_bid JOIN user_profiles p ON p.wiki_user_id=a.wiki_user_id WHERE p.bid<>l.target_bid;
DELETE FROM profile_likes WHERE EXISTS(SELECT 1 FROM wiki_user_aliases a JOIN user_profiles p ON p.wiki_user_id=a.wiki_user_id WHERE a.bid=profile_likes.target_bid AND p.bid<>profile_likes.target_bid);
