package com.addf.backend.armature.agent;

import java.time.Instant;
import java.util.List;

public record BoardSnapshot(Long boardId, String boardTitle, List<BoardGadgetEntry> gadgets, Instant updatedAt) {
}
