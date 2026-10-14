-- Community comments: one-level replies and an optional attached image.
-- parent_comment_id always points at a top-level comment (replies to a reply are stored under
-- the same root); reply_to_user_id only drives the "trả lời <tên>" label.
ALTER TABLE community_comment
    ADD COLUMN parent_comment_id UUID REFERENCES community_comment(id) ON DELETE CASCADE,
    ADD COLUMN reply_to_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    ADD COLUMN image_url TEXT;

ALTER TABLE community_comment DROP CONSTRAINT ck_community_comment_body;
ALTER TABLE community_comment ADD CONSTRAINT ck_community_comment_body CHECK (
    length(body) <= 4000 AND (length(btrim(body)) >= 1 OR image_url IS NOT NULL)
);

CREATE INDEX idx_community_comment_replies ON community_comment (parent_comment_id, created_at ASC, id ASC)
    WHERE status = 'PUBLISHED' AND parent_comment_id IS NOT NULL;
