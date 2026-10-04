package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.application.export.DsarExportSnapshotPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JdbcDsarExportSnapshotAdapter implements DsarExportSnapshotPort {
    private final JdbcTemplate jdbc;
    public JdbcDsarExportSnapshotAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public String profile(UUID userId) {
        return jdbc.queryForObject("SELECT row_to_json(x)::text FROM (SELECT id, email, full_name, locale, timezone, status, onboarding_status, notification_prefs, created_at FROM app_user WHERE id=?) x", String.class, userId);
    }
    @Override public List<String> cats(UUID userId) {
        return rows("SELECT row_to_json(x)::text FROM (SELECT id, name, birth_date, approx_age_months, breed_code, breed_other, coat_color, sex, neutered, weight_kg, public_code, status, is_primary, notes, created_at FROM cat WHERE owner_id=? AND deleted_at IS NULL ORDER BY created_at) x", userId);
    }
    @Override public List<String> scans(UUID userId) {
        return rows("SELECT row_to_json(x)::text FROM (SELECT s.id, s.cat_id, s.assignment, s.captured_at, s.capture_source, s.status, s.disputed_at, s.disputed_note, a.ph_value, a.ph_low, a.ph_high, a.classification, a.confidence, a.near_boundary, a.quality_flags, a.computed_at FROM scan s LEFT JOIN scan_analysis a ON a.id=s.current_analysis_id WHERE s.user_id=? AND s.deleted_at IS NULL ORDER BY s.captured_at) x", userId);
    }
    @Override public List<String> credits(UUID userId) {
        return rows("SELECT row_to_json(x)::text FROM (SELECT id, type, amount, balance_after, ref_type, ref_id, note, created_at FROM credit_ledger WHERE user_id=? ORDER BY created_at) x", userId);
    }
    @Override public List<String> consents(UUID userId) {
        return rows("SELECT row_to_json(x)::text FROM (SELECT purpose_code, status, method, locale, occurred_at FROM consent_record WHERE user_id=? ORDER BY occurred_at) x", userId);
    }
    @Override public List<String> notifications(UUID userId) {
        return rows("SELECT row_to_json(x)::text FROM (SELECT id, channel, template_code, payload, title_snapshot, body_snapshot, status, created_at, sent_at, read_at FROM notification WHERE user_id=? ORDER BY created_at) x", userId);
    }
    @Override public List<String> orders(UUID userId) {
        return rows("SELECT row_to_json(x)::text FROM (SELECT o.id, o.order_code, o.status, o.payment_method, o.receiver_name, o.receiver_phone, o.shipping_address, o.subtotal_vnd, o.discount_vnd, o.shipping_fee_vnd, o.total_vnd, o.created_at, (SELECT coalesce(jsonb_agg(jsonb_build_object('name', l.product_name, 'unitPriceVnd', l.unit_price_vnd, 'quantity', l.quantity)), '[]'::jsonb) FROM shop_order_line l WHERE l.order_id=o.id) AS lines FROM shop_order o WHERE o.user_id=? ORDER BY o.created_at) x", userId);
    }

    private List<String> rows(String query, UUID userId) {
        return jdbc.query(query, (rs, row) -> rs.getString(1), userId);
    }
}
