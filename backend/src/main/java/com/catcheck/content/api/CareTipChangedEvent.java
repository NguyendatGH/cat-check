package com.catcheck.content.api;

import java.util.UUID;

/** Một care tip vừa được publish hoặc archive — nội dung công khai (và kho RAG) cần cập nhật. */
public record CareTipChangedEvent(UUID careTipId) {
}
