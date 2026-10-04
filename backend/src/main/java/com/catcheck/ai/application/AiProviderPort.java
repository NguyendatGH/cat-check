package com.catcheck.ai.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AiProviderPort {
    Optional<String> complete(List<Map<String, String>> messages);
}
