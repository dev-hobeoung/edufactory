package com.hobeoung.edufactory.label;

import java.util.UUID;

public record Label(UUID id, String group, String name, String color, String description, int materialCount,
    int chunkCount) {}
