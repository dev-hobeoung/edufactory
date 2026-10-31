package com.hobeoung.edufactory.document;

import java.util.UUID;

public record DocumentSummary(UUID id, UUID conversationId, String title, String kind, String status,
    int currentVersion, int itemCount, Double totalScore, Double maxSimilarity, String updatedAt) {}
