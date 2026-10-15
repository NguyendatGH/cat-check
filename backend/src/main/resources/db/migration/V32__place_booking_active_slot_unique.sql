-- Khung giờ đã huỷ (CANCELLED) phải đặt lại được: ràng buộc UNIQUE của V19 không lọc trạng thái
-- nên một lượt huỷ giữ chỗ vĩnh viễn. Thay bằng index partial cùng tên — JdbcPlaceRepository
-- .createBooking vẫn bắt DuplicateKeyException → BOOKING_SLOT_TAKEN như cũ.
ALTER TABLE place_booking DROP CONSTRAINT uq_place_booking_slot;
CREATE UNIQUE INDEX uq_place_booking_slot
    ON place_booking (place_id, booking_date, time_slot)
    WHERE status <> 'CANCELLED';
