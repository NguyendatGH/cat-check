package com.catcheck.ai.application.privacy;

import com.catcheck.ai.application.AiKnowledgePort;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
