package walshe.projectcolumbo.api.v1;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.dto.IngestionRequest;
import walshe.projectcolumbo.api.v1.dto.IngestionResponse;
import walshe.projectcolumbo.ingestion.IngestionOrchestrator;
import walshe.projectcolumbo.ingestion.IngestionRun;

@RestController
@RequestMapping("/api/v1/internal/ingestion")
class IngestionController {

    private final IngestionOrchestrator orchestrator;

    IngestionController(IngestionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/run")
    ResponseEntity<IngestionResponse> triggerRun(@RequestBody(required = false) IngestionRequest request) {
        IngestionRequest safeRequest = request != null ? request : new IngestionRequest(null, null);
        
        IngestionRun run = orchestrator.runInternal(safeRequest.provider(), safeRequest.timeframe());
        
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new IngestionResponse(run.getId(), run.getStatus()));
    }
}
