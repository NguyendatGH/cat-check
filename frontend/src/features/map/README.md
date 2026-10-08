# features/map

- Làm gì: chỗ dành cho logic bản đồ dùng chung giữa nhiều trang (hiện chưa có).
- Route dùng: /map, /map/clinics/:clinicId — UI nằm ở `pages/map/**`, gọi API qua `features/place`.
- Trạng thái: rỗng có chủ đích. Bản đồ MapLibre (`pages/map/PlaceMap.tsx`) chỉ phục vụ hai trang
  trên nên để cạnh trang; chuyển vào đây khi có trang thứ ba cần dùng.
