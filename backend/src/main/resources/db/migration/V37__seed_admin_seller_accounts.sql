-- V37: Seed persistent admin and seller accounts so credentials never expire and are available across all profiles.
-- Password for both accounts: Admin@123
-- Hash: {bcrypt}$2a$12$gZamX3Q/atXXT9SKz6U3JOhHvltsCWUXthA862a2oXjezK.SrUBoi

DO $$
DECLARE
    admin_id UUID;
    seller_id UUID;
    pwd_hash TEXT := '{bcrypt}$2a$12$gZamX3Q/atXXT9SKz6U3JOhHvltsCWUXthA862a2oXjezK.SrUBoi';
BEGIN
    -- 1. admin@catcheck.vn (ADMIN_SUPER)
    SELECT id INTO admin_id FROM app_user WHERE email = 'admin@catcheck.vn';
    IF admin_id IS NULL THEN
        INSERT INTO app_user (email, full_name, status, onboarding_status, email_verified_at)
        VALUES ('admin@catcheck.vn', 'Admin CATCHECK', 'ACTIVE', 'COMPLETED', now())
        RETURNING id INTO admin_id;
    ELSE
        UPDATE app_user
        SET status = 'ACTIVE', onboarding_status = 'COMPLETED', email_verified_at = COALESCE(email_verified_at, now())
        WHERE id = admin_id;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM user_identity WHERE user_id = admin_id AND provider = 'LOCAL') THEN
        INSERT INTO user_identity (user_id, provider, provider_user_id, provider_email, email_verified, password_hash)
        VALUES (admin_id, 'LOCAL', 'admin@catcheck.vn', 'admin@catcheck.vn', true, pwd_hash);
    ELSE
        UPDATE user_identity
        SET password_hash = pwd_hash, email_verified = true, updated_at = now()
        WHERE user_id = admin_id AND provider = 'LOCAL';
    END IF;

    INSERT INTO user_role (user_id, role)
    VALUES (admin_id, 'USER')
    ON CONFLICT (user_id, role) DO NOTHING;

    INSERT INTO user_role (user_id, role)
    VALUES (admin_id, 'ADMIN_SUPER')
    ON CONFLICT (user_id, role) DO NOTHING;

    -- 2. seller@catcheck.vn (ADMIN_CATALOG)
    SELECT id INTO seller_id FROM app_user WHERE email = 'seller@catcheck.vn';
    IF seller_id IS NULL THEN
        INSERT INTO app_user (email, full_name, status, onboarding_status, email_verified_at)
        VALUES ('seller@catcheck.vn', 'Seller CATCHECK', 'ACTIVE', 'COMPLETED', now())
        RETURNING id INTO seller_id;
    ELSE
        UPDATE app_user
        SET status = 'ACTIVE', onboarding_status = 'COMPLETED', email_verified_at = COALESCE(email_verified_at, now())
        WHERE id = seller_id;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM user_identity WHERE user_id = seller_id AND provider = 'LOCAL') THEN
        INSERT INTO user_identity (user_id, provider, provider_user_id, provider_email, email_verified, password_hash)
        VALUES (seller_id, 'LOCAL', 'seller@catcheck.vn', 'seller@catcheck.vn', true, pwd_hash);
    ELSE
        UPDATE user_identity
        SET password_hash = pwd_hash, email_verified = true, updated_at = now()
        WHERE user_id = seller_id AND provider = 'LOCAL';
    END IF;

    INSERT INTO user_role (user_id, role)
    VALUES (seller_id, 'USER')
    ON CONFLICT (user_id, role) DO NOTHING;

    INSERT INTO user_role (user_id, role)
    VALUES (seller_id, 'ADMIN_CATALOG')
    ON CONFLICT (user_id, role) DO NOTHING;
END $$;
