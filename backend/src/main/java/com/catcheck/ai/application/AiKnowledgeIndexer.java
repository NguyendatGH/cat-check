package com.catcheck.ai.application;

import com.catcheck.content.api.CareTipChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Index kho tri thức NGOÀI luồng request (docs/AI_HYBRID_RAG_PLAN.md mục 3): publish/archive care tip,
 * lúc khởi động, hoặc admin reindex. Chỉ tài liệu/chunk đổi hash mới được ghi và embed lại.
 */
@Service
public class AiKnowledgeIndexer {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeIndexer.class);

    static final int EMBED_BATCH = 16;
    static final int MAX_EMBEDDED_PER_RUN = 2000;

    private final AiKnowledgePort knowledge;
    private final AiEmbeddingPort embedding;
    private final HybridRetrievalService retrieval;
    private final boolean indexOnStartup;

    public AiKnowledgeIndexer(AiKnowledgePort knowledge, AiEmbeddingPort embedding, HybridRetrievalService retrieval,
                              @Value("${catcheck.ai.index-on-startup:true}") boolean indexOnStartup) {
        this.knowledge = knowledge;
        this.embedding = embedding;
        this.retrieval = retrieval;
        this.indexOnStartup = indexOnStartup;
    }

    /** @return số tài liệu đang được index */
    public synchronized int reindex() {
        AiKnowledgePort.SyncResult sync = knowledge.syncPublishedCareTips();
        int embedded = embedPending();
        if (sync.changed() || embedded > 0) {
            retrieval.corpusChanged();
        }
        log.info("AI knowledge index: documents={}, changed={}, removed={}, embeddedChunks={}",
                sync.documents(), sync.changedDocuments(), sync.removedDocuments(), embedded);
        return sync.documents();
    }

    // Toàn kho (đã bỏ qua theo hash) thay vì theo sourceId: rẻ với kho nhỏ, tự sửa mọi lệch.
    // shortcut: đọc lại mọi care tip mỗi lần; chuyển sang sync theo careTipId khi kho lên hàng nghìn bài.
    @TransactionalEventListener(fallbackExecution = true)
    void on(CareTipChangedEvent event) {
        reindexInBackground("care tip change");
    }

    @EventListener(ApplicationReadyEvent.class)
    void indexOnStartup() {
        if (indexOnStartup) {
            reindexInBackground("startup");
        }
    }

    // Embedding tốn CPU: không bắt request publish của admin chờ; lỗi chỉ để kho cũ dùng tới lần reindex sau.
    private void reindexInBackground(String trigger) {
        Thread.ofVirtual().name("ai-knowledge-index").start(() -> {
            try {
                reindex();
            } catch (RuntimeException ex) {
                log.warn("AI knowledge index after {} failed ({}); the previous index stays in use",
                        trigger, ex.getClass().getSimpleName());
            }
        });
    }

    private int embedPending() {
        if (!embedding.available()) {
            return 0;
        }
        int total = 0;
        while (total < MAX_EMBEDDED_PER_RUN) {
            List<AiKnowledgePort.PendingChunk> batch = knowledge.chunksNeedingEmbedding(embedding.modelId(), EMBED_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            List<float[]> vectors = embedding.embedPassages(batch.stream().map(AiKnowledgePort.PendingChunk::content).toList());
            Map<java.util.UUID, float[]> byChunk = new HashMap<>();
            for (int i = 0; i < batch.size(); i++) {
                byChunk.put(batch.get(i).id(), vectors.get(i));
            }
            knowledge.saveEmbeddings(embedding.modelId(), byChunk);
            total += batch.size();
        }
        return total;
    }
}
