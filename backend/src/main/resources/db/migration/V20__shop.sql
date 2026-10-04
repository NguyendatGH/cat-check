-- Phase 3 catalogue, cart and order API. Payment capture remains outside this module.
CREATE TABLE shop_product (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    image_url TEXT,
    price_vnd BIGINT NOT NULL,
    compare_at_price_vnd BIGINT,
    stock_quantity INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_shop_product_price CHECK (price_vnd >= 0),
    CONSTRAINT ck_shop_product_stock CHECK (stock_quantity >= 0),
    CONSTRAINT ck_shop_product_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE shop_cart_line (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES shop_product(id) ON DELETE RESTRICT,
    quantity INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, product_id),
    CONSTRAINT ck_shop_cart_quantity CHECK (quantity BETWEEN 1 AND 99)
);

CREATE TABLE shop_order (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    order_code VARCHAR(24) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_method VARCHAR(24) NOT NULL,
    receiver_name VARCHAR(120) NOT NULL,
    receiver_phone VARCHAR(32) NOT NULL,
    shipping_address TEXT NOT NULL,
    subtotal_vnd BIGINT NOT NULL,
    discount_vnd BIGINT NOT NULL DEFAULT 0,
    shipping_fee_vnd BIGINT NOT NULL DEFAULT 0,
    total_vnd BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_shop_order_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'PACKING', 'SHIPPING', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_shop_order_payment CHECK (payment_method IN ('COD', 'MOMO', 'BANK_TRANSFER')),
    CONSTRAINT ck_shop_order_amounts CHECK (subtotal_vnd >= 0 AND discount_vnd >= 0 AND shipping_fee_vnd >= 0 AND total_vnd >= 0)
);

CREATE TABLE shop_order_line (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    order_id UUID NOT NULL REFERENCES shop_order(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES shop_product(id) ON DELETE RESTRICT,
    product_name VARCHAR(180) NOT NULL,
    unit_price_vnd BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT ck_shop_order_line_amounts CHECK (unit_price_vnd >= 0 AND quantity BETWEEN 1 AND 99)
);

CREATE INDEX idx_shop_order_user_created ON shop_order (user_id, created_at DESC);

CREATE TRIGGER trg_shop_product_updated_at BEFORE UPDATE ON shop_product
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_shop_cart_line_updated_at BEFORE UPDATE ON shop_cart_line
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_shop_order_updated_at BEFORE UPDATE ON shop_order
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO shop_product (sku, name, description, price_vnd, compare_at_price_vnd, stock_quantity, metadata)
VALUES
    ('SMARTSAND-BIO-6L', 'Cát thông minh CATCHECK SmartSand Bio 6L', 'Hạt chỉ thị màu để theo dõi xu hướng pH.', 245000, 290000, 100, '{"badge":"Bán chạy"}'),
    ('SUBSCRIPTION-3M', 'Gói định kỳ chăm sóc 3 tháng', 'Combo ba túi SmartSand và bảng đối chiếu.', 650000, 780000, 50, '{"badge":"Tiết kiệm"}'),
    ('CLEANBOX-TRAY', 'Khay cát CleanBox', 'Khay cát thành cao, dễ vệ sinh.', 480000, 550000, 30, '{"badge":"Khay thông minh"}')
ON CONFLICT (sku) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON shop_product, shop_cart_line, shop_order, shop_order_line TO catcheck_app;
    END IF;
END $$;
