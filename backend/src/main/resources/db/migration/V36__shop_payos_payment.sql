-- Thanh toán PayOS cho đơn shop: thêm cột liên kết thanh toán/hạn thanh toán/huỷ-hoàn tiền và cho phép
-- payment_method = 'PAYOS' (MOMO, BANK_TRANSFER giữ nguyên cho dữ liệu cũ). payos_order_code lấy từ
-- sequence riêng (START 100000).
ALTER TABLE shop_order
    ADD COLUMN payos_order_code BIGINT UNIQUE,
    ADD COLUMN payos_payment_link_id VARCHAR(64),
    ADD COLUMN payos_checkout_url TEXT,
    ADD COLUMN payment_expires_at TIMESTAMPTZ,
    ADD COLUMN paid_at TIMESTAMPTZ,
    ADD COLUMN payment_reference VARCHAR(64),
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancel_reason VARCHAR(24),
    ADD COLUMN refund_needed BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE shop_order DROP CONSTRAINT ck_shop_order_payment;
ALTER TABLE shop_order
    ADD CONSTRAINT ck_shop_order_payment CHECK (payment_method IN ('COD', 'PAYOS', 'MOMO', 'BANK_TRANSFER')),
    ADD CONSTRAINT ck_shop_order_payos_code CHECK (payment_method <> 'PAYOS' OR payos_order_code IS NOT NULL),
    ADD CONSTRAINT ck_shop_order_cancel_reason CHECK (
        cancel_reason IS NULL OR cancel_reason IN ('PAYMENT_EXPIRED', 'BUYER', 'ADMIN'));

CREATE SEQUENCE shop_order_payos_code_seq START 100000;

-- Job quét đơn PAYOS quá hạn; danh sách đơn admin lọc theo trạng thái.
CREATE INDEX idx_shop_order_payos_expiry
    ON shop_order (payment_expires_at)
    WHERE status = 'PENDING_PAYMENT' AND payment_method = 'PAYOS';
CREATE INDEX idx_shop_order_status_created ON shop_order (status, created_at DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_app') THEN
        GRANT USAGE ON SEQUENCE shop_order_payos_code_seq TO catcheck_app;
    END IF;
END $$;
