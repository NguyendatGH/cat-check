package com.catcheck.ai.application.privacy;

import com.catcheck.ai.application.AiKnowledgePort;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trước đây module ai không có {@code ErasureParticipant}: xoá tài khoản xong, nội dung
 * {@code ai_message} (câu hỏi lưu nguyên văn, có thể có email/SĐT) vẫn còn nguyên.
 */
class AiErasureParticipantTest {

    private final AiKnowledgePort repository = Mockito.mock(AiKnowledgePort.class);
    private final AiErasureParticipant participant = new AiErasureParticipant(repository);

    @Test
    void erasureStripsTheConversationContentOfTheSubject() {
        UUID subject = UUID.randomUUID();

        participant.erase(subject);

        Mockito.verify(repository).eraseConversationContent(subject);
        Mockito.verifyNoMoreInteractions(repository);
    }

    @Test
    void participantIsNamedAfterTheModule() {
        assertThat(participant.participantName()).isEqualTo("ai");
    }
}
