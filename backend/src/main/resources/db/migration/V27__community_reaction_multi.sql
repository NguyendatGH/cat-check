-- Cho phép một người dùng vừa thích (LIKE) vừa lưu (BOOKMARK) cùng một bài:
-- khoá chính cũ (post_id, user_id) chỉ cho một phản ứng mỗi người mỗi bài.
ALTER TABLE community_reaction DROP CONSTRAINT community_reaction_pkey;
ALTER TABLE community_reaction ADD PRIMARY KEY (post_id, user_id, reaction);

-- Báo cáo vi phạm: mỗi người chỉ có một báo cáo còn mở cho cùng một đối tượng.
CREATE UNIQUE INDEX uq_community_report_open_post
    ON community_report (reporter_user_id, post_id)
    WHERE post_id IS NOT NULL AND status IN ('OPEN', 'REVIEWING');
CREATE UNIQUE INDEX uq_community_report_open_comment
    ON community_report (reporter_user_id, comment_id)
    WHERE comment_id IS NOT NULL AND status IN ('OPEN', 'REVIEWING');

-- Bài viết không thể có category ALL (ALL chỉ là bộ lọc của danh sách).
ALTER TABLE community_post DROP CONSTRAINT ck_community_post_category;
ALTER TABLE community_post ADD CONSTRAINT ck_community_post_category
    CHECK (category IN ('QA', 'TIP', 'EXPERIENCE')) NOT VALID;
