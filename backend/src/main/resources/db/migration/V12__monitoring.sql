-- V12: Canh bao xu huong suc khoe - module `insight` (p7 §7.2.3).
--
-- Nguon dac ta: spec/parts/p4-domain-model-erd.md §4.9.2 (dong V12) - "monitoring_rule,
-- health_flag", phu thuoc V8 (cat), V11 (scan). Chi tiet bang: p4 D11-D12. Rule R1-R4 +
-- URGENT_CLINICAL_SIGN: p6 §6.9.
--
-- Luu y tu A3 (docs/handovers/A3.md): `cat_clinical_sign_report.health_flag_id` da co COT o V8
-- nhung CHUA co FK (health_flag chua ton tai luc do) - FK do duoc bo sung o day.
-- `health_flag.notification_id` (module `notification`) se duoc V13 bo sung, KHONG phai o day.
--
-- Seed `monitoring_rule` (5 rule) nam TRONG file nay (khong tao file R__ rieng): ORCHESTRATOR
-- §3.2 chi cho phep A6 tao dung 3 file V11/V12/V14, giong dung tien le A5 da lam voi V9 (xem
-- docs/handovers/A5.md muc 5.1) - seed idempotent bang ON CONFLICT DO NOTHING.

-- ===========================================================================
-- D11. monitoring_rule - cau hinh rule canh bao
-- ===========================================================================
CREATE TABLE IF NOT EXISTS monitoring_rule
(
    code                       VARCHAR(40)   NOT NULL,
    name                       TEXT          NOT NULL,
    enabled                    BOOLEAN       NOT NULL DEFAULT true,
    params                     JSONB         NOT NULL,
    severity                   VARCHAR(16)   NOT NULL DEFAULT 'ATTENTION',
    cooldown_hours             INT           NOT NULL DEFAULT 72,
    requires_calibrated_chart  BOOLEAN       NOT NULL DEFAULT true,
    message_key                VARCHAR(80)   NOT NULL,
    push_enabled                BOOLEAN      NOT NULL DEFAULT true,
    sort_order                  INT          NOT NULL DEFAULT 100,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_monitoring_rule PRIMARY KEY (code),
    CONSTRAINT ck_monitoring_rule_severity CHECK (severity IN ('INFO', 'ATTENTION', 'URGENT')),
    CONSTRAINT ck_monitoring_rule_cooldown CHECK (cooldown_hours >= 0)
);

CREATE INDEX IF NOT EXISTS idx_monitoring_rule_enabled
    ON monitoring_rule (enabled)
    WHERE enabled;

COMMENT ON TABLE monitoring_rule IS 'Cau hinh rule canh bao xu huong (p4 D11, p6 §6.9). Bang nho, nap toan bo vao cache ung dung.';
COMMENT ON COLUMN monitoring_rule.cooldown_hours IS '0 = khong cooldown, chi dung cho URGENT_CLINICAL_SIGN.';

-- p6 §6.9.2-§6.9.7: 5 rule mac dinh. ON CONFLICT DO NOTHING de idempotent khi chay lai.
INSERT INTO monitoring_rule (code, name, enabled, params, severity, cooldown_hours,
                              requires_calibrated_chart, message_key, push_enabled, sort_order)
VALUES
    ('REPEATED_OUT_OF_RANGE', 'Lap lai ngoai khoang tham chieu', true,
     '{"windowHours":48,"minCount":2,"sameDirection":true,"minConfidence":0.55}'::jsonb,
     'ATTENTION', 72, true, 'rule.REPEATED_OUT_OF_RANGE.message', true, 10),
    ('BASELINE_DEVIATION', 'Lech so voi muc nen cua chinh be', true,
     '{"baselineDays":14,"minDelta":0.5,"minBaselineSamples":5,"baselineStat":"MEDIAN","minConfidence":0.55}'::jsonb,
     'ATTENTION', 72, true, 'rule.BASELINE_DEVIATION.message', true, 20),
    ('MONOTONIC_TREND', 'Xu huong dich chuyen mot chieu', true,
     '{"streak":3,"maxSpanDays":7,"minGapHours":6,"minStepDelta":0.15,"midpoint":6.45,"minConfidence":0.55}'::jsonb,
     'ATTENTION', 72, true, 'rule.MONOTONIC_TREND.message', true, 30),
    ('LOW_QUALITY_STREAK', 'Chat luong quet thap lien tiep', true,
     '{"streak":2,"maxConfidence":0.50}'::jsonb,
     'INFO', 24, false, 'rule.LOW_QUALITY_STREAK.message', true, 40),
    ('URGENT_CLINICAL_SIGN', 'Dau hieu lam sang do chu bao', true,
     '{}'::jsonb,
     'URGENT', 0, false, 'rule.URGENT_CLINICAL_SIGN.message', true, 50)
ON CONFLICT (code) DO NOTHING;

-- ===========================================================================
-- D12. health_flag - dau hieu duoc danh dau
-- ===========================================================================
CREATE TABLE IF NOT EXISTS health_flag
(
    id                UUID          NOT NULL DEFAULT uuidv7(),
    cat_id            UUID          NOT NULL,
    rule_code         VARCHAR(40)   NOT NULL,
    trigger_scan_id   UUID,
    severity          VARCHAR(16)   NOT NULL,
    triggered_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    window_from       TIMESTAMPTZ   NOT NULL,
    window_to         TIMESTAMPTZ   NOT NULL,
    message_key       VARCHAR(80)   NOT NULL,
    message_params    JSONB         NOT NULL DEFAULT '{}',
    explanation_vi    TEXT          NOT NULL,
    acknowledged_at   TIMESTAMPTZ,
    acknowledged_by   UUID,
    notification_id   UUID,
    dedupe_key        VARCHAR(120)  NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_health_flag PRIMARY KEY (id),
    CONSTRAINT uq_health_flag_dedupe UNIQUE (dedupe_key),
    CONSTRAINT ck_health_flag_severity CHECK (severity IN ('INFO', 'ATTENTION', 'URGENT')),
    CONSTRAINT ck_health_flag_window CHECK (window_to > window_from)
);

CREATE INDEX IF NOT EXISTS idx_health_flag_cat_triggered
    ON health_flag (cat_id, triggered_at DESC);
CREATE INDEX IF NOT EXISTS idx_health_flag_unacknowledged
    ON health_flag (cat_id)
    WHERE acknowledged_at IS NULL;

COMMENT ON TABLE health_flag IS 'Dau hieu duoc rule danh dau (p4 D12). dedupe_key UNIQUE ep cooldown o DB.';
COMMENT ON COLUMN health_flag.explanation_vi IS 'Snapshot da render - giu nguyen cau chu du message_key doi sau nay (PDF/thong bao da gui).';
COMMENT ON COLUMN health_flag.notification_id IS 'FK -> notification(id) se duoc V13 (module notification) bo sung.';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_health_flag_cat') THEN
        ALTER TABLE health_flag ADD CONSTRAINT fk_health_flag_cat
            FOREIGN KEY (cat_id) REFERENCES cat (id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_health_flag_rule') THEN
        ALTER TABLE health_flag ADD CONSTRAINT fk_health_flag_rule
            FOREIGN KEY (rule_code) REFERENCES monitoring_rule (code);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_health_flag_trigger_scan') THEN
        ALTER TABLE health_flag ADD CONSTRAINT fk_health_flag_trigger_scan
            FOREIGN KEY (trigger_scan_id) REFERENCES scan (id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_health_flag_acknowledged_by') THEN
        ALTER TABLE health_flag ADD CONSTRAINT fk_health_flag_acknowledged_by
            FOREIGN KEY (acknowledged_by) REFERENCES app_user (id);
    END IF;
END
$$;

-- ===========================================================================
-- Bo sung FK con thieu tu V8 (module `cat`): cat_clinical_sign_report.health_flag_id.
-- Cot da ton tai o V8; health_flag chua ton tai luc do nen chua co FK - xem docs/handovers/A3.md.
-- ===========================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cat_clinical_sign_report_health_flag') THEN
        ALTER TABLE cat_clinical_sign_report ADD CONSTRAINT fk_cat_clinical_sign_report_health_flag
            FOREIGN KEY (health_flag_id) REFERENCES health_flag (id) ON DELETE SET NULL;
    END IF;
END
$$;
