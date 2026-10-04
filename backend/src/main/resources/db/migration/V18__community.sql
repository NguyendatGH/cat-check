-- Phase 2 community API. UUIDs and enums follow the project-wide conventions.
CREATE TABLE community_post (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    author_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    category VARCHAR(24) NOT NULL,
    title VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    tags VARCHAR(48)[] NOT NULL DEFAULT '{}',
    image_url TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_community_post_category CHECK (category IN ('ALL', 'QA', 'TIP', 'EXPERIENCE')),
    CONSTRAINT ck_community_post_status CHECK (status IN ('PUBLISHED', 'HIDDEN', 'REMOVED')),
    CONSTRAINT ck_community_post_title CHECK (length(btrim(title)) BETWEEN 1 AND 180),
    CONSTRAINT ck_community_post_body CHECK (length(btrim(body)) BETWEEN 1 AND 10000)
);

CREATE INDEX idx_community_post_feed ON community_post (created_at DESC, id DESC)
    WHERE status = 'PUBLISHED';

CREATE TABLE community_comment (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    post_id UUID NOT NULL REFERENCES community_post(id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_community_comment_status CHECK (status IN ('PUBLISHED', 'HIDDEN', 'REMOVED')),
    CONSTRAINT ck_community_comment_body CHECK (length(btrim(body)) BETWEEN 1 AND 4000)
);

CREATE INDEX idx_community_comment_post ON community_comment (post_id, created_at ASC, id ASC)
    WHERE status = 'PUBLISHED';

CREATE TABLE community_reaction (
    post_id UUID NOT NULL REFERENCES community_post(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    reaction VARCHAR(16) NOT NULL DEFAULT 'LIKE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT ck_community_reaction_kind CHECK (reaction IN ('LIKE', 'BOOKMARK'))
);

CREATE TABLE community_report (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    post_id UUID REFERENCES community_post(id) ON DELETE CASCADE,
    comment_id UUID REFERENCES community_comment(id) ON DELETE CASCADE,
    reporter_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    reason VARCHAR(32) NOT NULL,
    details TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_community_report_target CHECK ((post_id IS NOT NULL) <> (comment_id IS NOT NULL)),
    CONSTRAINT ck_community_report_status CHECK (status IN ('OPEN', 'REVIEWING', 'RESOLVED', 'DISMISSED'))
);

CREATE INDEX idx_community_report_queue ON community_report (status, created_at DESC);

CREATE TRIGGER trg_community_post_updated_at BEFORE UPDATE ON community_post
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_community_comment_updated_at BEFORE UPDATE ON community_comment
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON community_post, community_comment,
            community_reaction, community_report TO catcheck_app;
    END IF;
END $$;
