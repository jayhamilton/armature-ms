package com.addf.backend.armature.agent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// The backend has no independent view of board/gadget state (see AgentToolRegistry's
// class javadoc) - boards live in the browser's localStorage. This store caches the
// latest boardContext + boardGadgets from each /api/agent/chat request, in memory
// only, keyed by boardId, purely so an MCP App (present_board_summary) has something
// real to render for an external MCP client that has no localStorage access of its
// own. It reflects "as of the last chat message sent from armature-ui's panel", not
// live board state - there is no push channel the other direction.
@Component
public class BoardSnapshotStore {

    private final Map<Long, BoardSnapshot> snapshots = new ConcurrentHashMap<>();

    public void update(AgentBoardContext boardContext, List<BoardGadgetEntry> boardGadgets) {
        if (boardContext == null || boardContext.boardId() == null) {
            return;
        }
        snapshots.put(boardContext.boardId(), new BoardSnapshot(
                boardContext.boardId(),
                boardContext.boardTitle(),
                boardGadgets != null ? boardGadgets : List.of(),
                Instant.now()
        ));
    }

    // Single-board demo assumption: an external MCP client has no boardId of its own
    // to ask for, so present_board_summary shows whichever board was synced most
    // recently rather than requiring a boardId argument the caller can't supply.
    public Optional<BoardSnapshot> latest() {
        return snapshots.values().stream().max(Comparator.comparing(BoardSnapshot::updatedAt));
    }
}
