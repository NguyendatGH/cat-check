package com.catcheck.community.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hành vi không cần DB thật: trạng thái reaction trả về và INSERT bình luận không ghi được dòng nào. */
class JdbcCommunityRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JdbcCommunityRepository repository = new JdbcCommunityRepository(jdbc);

    @Test
    void removingAReactionReportsItInactive() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertThat(repository.toggleReaction(UUID.randomUUID(), UUID.randomUUID(), "LIKE", false)).isFalse();
    }

    @Test
    void addingAReactionReportsItActiveEvenWhenItAlreadyExisted() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        assertThat(repository.toggleReaction(UUID.randomUUID(), UUID.randomUUID(), "LIKE", true)).isTrue();
    }

    @Test
    void commentInsertOnANoLongerPublishedPostIsEmptyInsteadOfThrowing() {
        when(jdbc.queryForList(anyString(), eq(UUID.class), any(Object[].class))).thenReturn(List.of());

        assertThat(repository.insertComment(UUID.randomUUID(), UUID.randomUUID(), null, null, "Hi", null)).isEmpty();
    }
}
