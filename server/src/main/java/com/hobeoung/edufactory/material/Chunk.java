package com.hobeoung.edufactory.material;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public record Chunk(UUID id, UUID materialId, int seq, String chunkType, String title, String position, String itemNo,
    String questionType, String content, List<Choice> choices, String answer, String explanation, Double score,
    List<String> tags, Integer pageFrom, Integer pageTo, List<ChunkLabel> labels, boolean embedded, JsonNode stat) {}
