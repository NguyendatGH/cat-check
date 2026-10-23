CREATE TABLE shop_promo_code (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    code VARCHAR(50) NOT NULL UNIQUE,
    label VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    min_order_vnd BIGINT NOT NULL DEFAULT 0,
    max_discount_vnd BIGINT,
    usage_limit INT,
    used_count INT NOT NULL DEFAULT 0,
    starts_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_shop_promo_type CHECK (type IN ('PERCENT', 'FIXED', 'SHIPPING')),
    CONSTRAINT ck_shop_promo_amounts CHECK (amount > 0 AND min_order_vnd >= 0 AND (max_discount_vnd IS NULL OR max_discount_vnd > 0)),
    CONSTRAINT ck_shop_promo_usage CHECK (used_count >= 0 AND (usage_limit IS NULL OR usage_limit >= used_count))
);

CREATE TRIGGER trg_shop_promo_code_updated_at BEFORE UPDATE ON shop_promo_code
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO shop_promo_code (code, label, type, amount, min_order_vnd)
VALUES
    ('CATCHECK10', 'Giảm 10%', 'PERCENT', 10, 0),
    ('FREESHIP', 'Giảm 30.000đ ship', 'SHIPPING', 30000, 0),
    ('CATFRIEND', 'Giảm 20.000đ', 'FIXED', 20000, 0)
ON CONFLICT (code) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON shop_promo_code TO catcheck_app;
    END IF;
END $$;
