package com.catcheck.community;

import com.catcheck.community.api.CommunityErrorCode;
import com.catcheck.community.application.CommunityService;
import com.catcheck.community.application.spi.AccountStatus;
import com.catcheck.community.domain.CommunityRateLimitRule;
import com.catcheck.community.domain.port.CommunityRateLimiter;
import com.catcheck.community.infrastructure.persistence.JdbcCommunityRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.application.ShopService;
import com.catcheck.shop.infrastructure.persistence.JdbcShopRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CommunityPersistenceTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-trixie");

    private static JdbcTemplate jdbc;
    private static CommunityService community;
    private static ShopService shop;
    private static UUID user;
    private static UUID other;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load().migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
        CommunityRateLimiter alwaysAllow = new CommunityRateLimiter() {
            @Override
            public boolean tryConsume(CommunityRateLimitRule rule, String subject) {
                return true;
            }

            @Override
            public long secondsUntilRefill(CommunityRateLimitRule rule, String subject) {
                return 0;
            }
        };
        community = new CommunityService(new JdbcCommunityRepository(jdbc), id -> AccountStatus.ACTIVE, alwaysAllow);
        shop = new ShopService(new JdbcShopRepository(jdbc));
        user = insertUser("u1@example.invalid");
        other = insertUser("u2@example.invalid");
    }

    private static UUID insertUser(String email) {
        return jdbc.queryForObject(
                "INSERT INTO app_user (email, full_name) VALUES (?, 'T') RETURNING id", UUID.class, email);
    }

    @Test
    @DisplayName("Mot nguoi vua thich vua luu cung mot bai")
    void likeAndBookmarkCoexist() {
        var post = community.createPost(other, "qa", "Tieu de", "Noi dung", List.of());
        assertThat(community.reaction(user, post.id(), "LIKE", true)).isTrue();
        assertThat(community.reaction(user, post.id(), "BOOKMARK", true)).isTrue();
        var seen = community.detail(user, post.id()).post();
        assertThat(seen.likedByCurrentUser()).isTrue();
        assertThat(seen.bookmarkedByCurrentUser()).isTrue();
        assertThat(seen.likeCount()).isEqualTo(1);

        community.reaction(user, post.id(), "LIKE", false);
        var after = community.detail(user, post.id()).post();
        assertThat(after.likedByCurrentUser()).isFalse();
        assertThat(after.bookmarkedByCurrentUser()).isTrue();
    }

    @Test
    @DisplayName("Bai khong the co category ALL")
    void categoryAllRejectedByDatabase() {
        assertThatThrownBy(() -> community.createPost(user, "ALL", "T", "B", List.of()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Bao cao trung khi con mo -> 409; sau khi xu ly xong thi bao cao lai duoc")
    void duplicateOpenReportConflicts() {
        UUID author = insertUser("author-dup@example.invalid");
        var post = community.createPost(author, "TIP", "Tieu de", "Noi dung", List.of());
        community.report(user, post.id(), null, "SPAM", null);
        assertThatThrownBy(() -> community.report(user, post.id(), null, "OTHER", null))
                .isInstanceOf(ConflictException.class);
        community.report(other, post.id(), null, "SPAM", null);
        jdbc.update("UPDATE community_report SET status = 'DISMISSED' WHERE reporter_user_id = ?", user);
        community.report(user, post.id(), null, "SPAM", null);
    }

    @Test
    @DisplayName("Tac gia khong bao cao duoc bai/binh luan cua chinh minh; nguoi khac van bao cao duoc binh luan")
    void authorCannotReportOwnContent() {
        var post = community.createPost(other, "TIP", "Tieu de", "Noi dung", List.of());
        var comment = community.createComment(other, post.id(), "Binh luan");

        assertThatThrownBy(() -> community.report(other, post.id(), null, "SPAM", null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(CommunityErrorCode.REPORT_OWN_CONTENT));
        assertThatThrownBy(() -> community.report(other, null, comment.id(), "SPAM", null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(CommunityErrorCode.REPORT_OWN_CONTENT));
        community.report(user, null, comment.id(), "SPAM", null);
    }

    @Test
    @DisplayName("Bao cao doi tuong khong ton tai -> 404, khong phai loi FK")
    void reportUnknownTargetIsNotFound() {
        assertThatThrownBy(() -> community.report(user, UUID.randomUUID(), null, "SPAM", null))
                .isInstanceOf(com.catcheck.shared.error.NotFoundException.class);
    }

    @Test
    @DisplayName("Tra loi mot cap, binh luan chi co anh, an binh luan goc an ca phan hoi")
    void repliesImagesAndModeration() {
        var post = community.createPost(other, "QA", "Tieu de", "Noi dung", List.of());
        var root = community.createComment(user, post.id(), "Goc");
        var r1 = community.createComment(other, post.id(), root.id(), "R1", null);
        var r2 = community.createComment(user, post.id(), r1.id(), "", "community-comment/2026/10/14/a.png");
        for (int i = 3; i <= 5; i++) community.createComment(other, post.id(), root.id(), "R" + i, null);

        assertThat(r2.parentCommentId()).isEqualTo(root.id());
        assertThat(r2.replyToName()).isEqualTo("T");
        assertThat(r2.body()).isEmpty();

        var detail = community.detail(user, post.id());
        assertThat(detail.post().commentCount()).isEqualTo(6);
        assertThat(detail.comments()).hasSize(1);
        var thread = detail.comments().getFirst();
        assertThat(thread.comment().replyCount()).isEqualTo(5);
        assertThat(thread.replies()).extracting(c -> c.body()).containsExactly("R1", "", "R3");

        var rest = community.replies(post.id(), root.id(), 3, 20);
        assertThat(rest.items()).extracting(c -> c.body()).containsExactly("R4", "R5");
        assertThat(rest.totalElements()).isEqualTo(5);
        assertThat(rest.hasMore()).isFalse();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO community_comment (post_id, author_user_id, body) VALUES (?, ?, '  ')", post.id(), user))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Reports on a reply still work; hiding the top-level comment hides its whole thread.
        community.report(other, null, r2.id(), "SPAM", null);
        jdbc.update("UPDATE community_comment SET status = 'HIDDEN' WHERE id = ?", root.id());
        assertThat(community.detail(user, post.id()).comments()).isEmpty();
        assertThat(community.detail(user, post.id()).post().commentCount()).isZero();
        assertThatThrownBy(() -> community.createComment(user, post.id(), r1.id(), "x", null))
                .isInstanceOf(com.catcheck.shared.error.NotFoundException.class);
    }

    @Test
    @DisplayName("PUT cart vuot ton kho -> STOCK_UNAVAILABLE; dong don tra dung mo ta/ton kho")
    void cartStockCheckAndOrderLineData() {
        UUID product = jdbc.queryForObject("""
                INSERT INTO shop_product (sku, name, description, price_vnd, stock_quantity)
                VALUES ('SKU-T1', 'SP', 'Mo ta that', 1000, 3) RETURNING id
                """, UUID.class);
        shop.setLine(user, product, 3);
        assertThatThrownBy(() -> shop.setLine(user, product, 4))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ShopErrorCode.STOCK_UNAVAILABLE));
        var order = shop.checkout(user, "COD", "A", "0900000000", "Dia chi");
        var line = shop.order(user, order.id()).lines().getFirst();
        assertThat(line.product().description()).isEqualTo("Mo ta that");
        assertThat(line.product().stockQuantity()).isZero();
    }
}
