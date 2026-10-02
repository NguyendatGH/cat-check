package com.catcheck.identity.infrastructure.crypto;

import com.catcheck.identity.domain.port.CryptoCanaryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link CryptoCanaryRepository} tren {@code JdbcTemplate}. */
@Repository
public class JdbcCryptoCanaryRepository implements CryptoCanaryRepository {

    private final JdbcTemplate jdbc;

    public JdbcCryptoCanaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(UUID id, String purpose, int keyVersion, byte[] cipherBlob, byte[] plaintextTag) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO crypto_canary (id, purpose, key_version, cipher_blob, plaintext_tag)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setObject(i++, id);
            ps.setString(i++, purpose);
            ps.setInt(i++, keyVersion);
            ps.setBytes(i++, cipherBlob);
            ps.setBytes(i, plaintextTag);
            return ps;
        });
    }

    @Override
    public boolean exists(String purpose, int keyVersion) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM crypto_canary WHERE purpose = ? AND key_version = ?",
                Integer.class, purpose, keyVersion);
        return count != null && count > 0;
    }

    @Override
    public void markVerified(UUID id) {
        jdbc.update("UPDATE crypto_canary SET verified_at = now() WHERE id = ?", id);
    }

    @Override
    public Optional<Canary> find(String purpose, int keyVersion) {
        List<Canary> rows = jdbc.query(
                "SELECT id, cipher_blob, plaintext_tag, verified_at FROM crypto_canary"
                        + " WHERE purpose = ? AND key_version = ?",
                (rs, rowNum) -> new Canary(
                        rs.getObject("id", UUID.class),
                        rs.getBytes("cipher_blob"),
                        rs.getBytes("plaintext_tag"),
                        rs.getObject("verified_at", java.time.OffsetDateTime.class) != null),
                purpose, keyVersion);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /** Giu lai vi {@link Types} can khi ghi UUID null o cac adapter khac cua module. */
    static void setNullUuid(PreparedStatement ps, int index) throws java.sql.SQLException {
        ps.setNull(index, Types.OTHER);
    }
}
