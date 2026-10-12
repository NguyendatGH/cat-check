package com.catcheck.ai.application.privacy;

import com.catcheck.ai.application.AiKnowledgePort;
import com.catcheck.privacy.spi.ErasureParticipant;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AiErasureParticipant implements ErasureParticipant {

    public static final String NAME = "ai";

    private final AiKnowledgePort repository;

    public AiErasureParticipant(AiKnowledgePort repository) {
        this.repository = repository;
    }

    @Override
    public String participantName() {
        return NAME;
    }

    @Override
    public void erase(UUID subjectId) {
        repository.eraseConversationContent(subjectId);
    }
}
