package com.catcheck.shared.testing;

import org.testcontainers.utility.DockerImageName;

/** Image Postgres cho mọi test Testcontainers: cùng major 18 với prod, kèm pgvector (V35 cần extension vector). */
public final class TestPostgres {

    public static final DockerImageName IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg18-trixie").asCompatibleSubstituteFor("postgres");

    private TestPostgres() {
    }
}
