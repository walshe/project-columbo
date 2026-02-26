package walshe.projectcolumbo.api.v1;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.dto.IngestionRequest;
import walshe.projectcolumbo.api.v1.dto.IngestionResponse;
import walshe.projectcolumbo.ingestion.IngestionRun;
import walshe.projectcolumbo.ingestion.IngestionRunStatus;
import walshe.projectcolumbo.ingestion.MarketPipelineService;
import walshe.projectcolumbo.ingestion.RunMode;

@RestController
@RequestMapping("/api/v1/internal/ingestion")
class IngestionController {

    private final MarketPipelineService pipelineService;

    IngestionController(MarketPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/run")
    ResponseEntity<IngestionResponse> triggerRun(@RequestBody(required = false) IngestionRequest request) {
        IngestionRequest safeRequest = request != null ? request : new IngestionRequest(null, null);
        
        IngestionRun run = pipelineService.runDaily(safeRequest.provider(), safeRequest.timeframe(), RunMode.INCREMENTAL);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new IngestionResponse(run.getId(), run.getStatus()));
    }
}
