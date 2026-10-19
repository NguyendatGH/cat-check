package com.catcheck.scan.tooling;

import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.DeltaE2000Params;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.color.PhChartPoint;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class LocalDbChartCatalog implements ChartCatalog {

    private final String jdbcUrl;
    private final String user;
    private final String password;

    LocalDbChartCatalog(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    static LocalDbChartCatalog fromSystemPropertiesOrLocalDefaults() {
        return new LocalDbChartCatalog(
                System.getProperty("harness.db.url", "jdbc:postgresql://localhost:5432/catcheck"),
                System.getProperty("harness.db.user", "catcheck"),
                System.getProperty("harness.db.password", "catcheck_local_only"));
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    @Override
    public Optional<PhChart> findActiveChart(String productLine, String productionBatch) {
        String sql = "SELECT id, version, status, illuminant, observer, is_placeholder "
                + "FROM color_chart WHERE product_line = ? AND status = 'ACTIVE' "
                + "AND production_batch IS NOT DISTINCT FROM ? ORDER BY version DESC LIMIT 1";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productLine);
            ps.setString(2, productionBatch);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                UUID chartId = (UUID) rs.getObject("id");
                PhChart chart = new PhChart(
                        chartId.toString(),
                        rs.getInt("version"),
                        PhChart.Status.valueOf(rs.getString("status")),
                        rs.getString("illuminant"),
                        Integer.parseInt(rs.getString("observer")),
                        rs.getBoolean("is_placeholder"),
                        loadPoints(c, chartId),
                        DeltaE2000Params.CAT_CHECK);
                return Optional.of(chart);
            }
        } catch (SQLException e) {
            throw dbUnavailable(e);
        }
    }

    @Override
    public Optional<PhChart> findChartById(UUID chartId) {
        throw new UnsupportedOperationException(
                "LocalDbChartCatalog chi phuc vu harness doc-active-chart - khong can backfill findChartById");
    }

    @Override
    public List<PhBandClassifier.Band> findGlobalBands() {
        String sql = "SELECT code, min_ph, max_ph, min_inclusive, max_inclusive, severity, "
                + "color_token, icon_name, sort_order FROM ph_classification_band "
                + "WHERE chart_id IS NULL AND active = true ORDER BY sort_order";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PhBandClassifier.Band> bands = new ArrayList<>();
            while (rs.next()) {
                BigDecimal minPh = (BigDecimal) rs.getObject("min_ph");
                BigDecimal maxPh = (BigDecimal) rs.getObject("max_ph");
                String code = rs.getString("code");
                bands.add(new PhBandClassifier.Band(
                        PhBandClassifier.Code.valueOf(code),
                        minPh == null ? null : minPh.doubleValue(),
                        maxPh == null ? null : maxPh.doubleValue(),
                        rs.getBoolean("min_inclusive"),
                        rs.getBoolean("max_inclusive"),
                        code,
                        PhBandClassifier.Severity.valueOf(rs.getString("severity")),
                        rs.getString("color_token"),
                        rs.getString("icon_name"),
                        rs.getInt("sort_order")));
            }
            return bands;
        } catch (SQLException e) {
            throw dbUnavailable(e);
        }
    }

    private List<PhChartPoint> loadPoints(Connection c, UUID chartId) throws SQLException {
        String sql = "SELECT id, ph_value, lab_l, lab_a, lab_b, tolerance_delta_e, hex_srgb, "
                + "display_hex, display_name_vi, display_name_en, sample_count, sample_spread_de00 "
                + "FROM color_chart_point WHERE chart_id = ? ORDER BY sort_order";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, chartId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PhChartPoint> points = new ArrayList<>();
                while (rs.next()) {
                    BigDecimal spread = (BigDecimal) rs.getObject("sample_spread_de00");
                    Object sampleCountObj = rs.getObject("sample_count");
                    points.add(new PhChartPoint(
                            ((UUID) rs.getObject("id")).toString(),
                            rs.getBigDecimal("ph_value").doubleValue(),
                            new Lab(rs.getBigDecimal("lab_l").doubleValue(),
                                    rs.getBigDecimal("lab_a").doubleValue(),
                                    rs.getBigDecimal("lab_b").doubleValue()),
                            rs.getBigDecimal("tolerance_delta_e").doubleValue(),
                            rs.getString("hex_srgb"),
                            rs.getString("display_hex"),
                            rs.getString("display_name_vi"),
                            rs.getString("display_name_en"),
                            sampleCountObj == null ? 0 : ((Number) sampleCountObj).intValue(),
                            spread == null ? 0.0 : spread.doubleValue()));
                }
                return points;
            }
        }
    }

    private IllegalStateException dbUnavailable(SQLException e) {
        return new IllegalStateException(
                "Harness khong doc duoc Postgres local (" + jdbcUrl + ") - can DB native dang chay voi "
                        + "schema da migrate va bang mau CHART-PLACEHOLDER-v1 (xem backend/README.md, "
                        + "KHONG dung Testcontainers/Docker o day)", e);
    }
}
