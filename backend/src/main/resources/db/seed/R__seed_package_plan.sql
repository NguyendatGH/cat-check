-- Initial proposal from p5 section 5.3. Owner approval is still required before go-live.
INSERT INTO package_plan (
    code, name, weight_kg, credit_amount, credit_validity_days,
    max_cat_profiles, features, active, version
) VALUES
    ('MINI', 'CATCHECK Mini - Trial', 1.00, 3, 7, 1,
     '{"history":"NONE","trend":false,"reminder":false,"export":false,"storeImage":false}'::jsonb, true, 1),
    ('DAILY', 'CATCHECK Daily', 2.50, 8, 7, 1,
     '{"history":"BASIC","trend":false,"reminder":false,"export":false,"storeImage":true}'::jsonb, true, 1),
    ('PLUS', 'CATCHECK Plus', 3.00, 10, 7, 1,
     '{"history":"ADVANCED","trend":true,"reminder":true,"export":true,"storeImage":true}'::jsonb, true, 1),
    ('MULTI', 'CATCHECK Multi', 5.00, 16, 7, NULL,
     '{"history":"ADVANCED","trend":true,"reminder":true,"export":true,"storeImage":true}'::jsonb, true, 1),
    ('CARE_BOX', 'CATCHECK Care Box', 5.00, 16, 7, NULL,
     '{"history":"ADVANCED","trend":true,"reminder":true,"export":true,"storeImage":true}'::jsonb, true, 1)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    weight_kg = EXCLUDED.weight_kg,
    credit_amount = EXCLUDED.credit_amount,
    credit_validity_days = EXCLUDED.credit_validity_days,
    max_cat_profiles = EXCLUDED.max_cat_profiles,
    features = EXCLUDED.features,
    active = EXCLUDED.active,
    version = EXCLUDED.version;
