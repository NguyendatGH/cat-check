-- Lượt thích cho bình luận: mỗi người tối đa một lượt thích trên mỗi bình luận.
CREATE TABLE community_comment_reaction (
    comment_id UUID NOT NULL REFERENCES community_comment(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id)
);

CREATE INDEX idx_community_comment_reaction_user ON community_comment_reaction (user_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_app') THEN
        GRANT SELECT, INSERT, DELETE ON community_comment_reaction TO catcheck_app;
    END IF;
END $$;
