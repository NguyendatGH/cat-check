# features/community

- Làm gì: client + hook TanStack Query cho Community API — bảng tin (lọc chuyên mục ở server,
  phân trang offset), chi tiết bài, bình luận, thích/lưu (cập nhật lạc quan), báo cáo vi phạm.
- Route dùng: /community, /community/new, /community/posts/:postId
- Endpoint: `GET|POST /api/v1/community/posts`, `GET /api/v1/community/posts/{id}`,
  `POST /api/v1/community/posts/{id}/comments`, `POST /api/v1/community/posts/{id}/reactions`,
  `POST /api/v1/community/reports`
- Entitlement: feature flag `community`
- Trạng thái: UI render 100% từ API thật; không còn dữ liệu dựng tay trong `pages/community`.
