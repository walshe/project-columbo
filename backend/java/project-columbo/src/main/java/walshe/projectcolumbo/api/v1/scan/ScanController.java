package walshe.projectcolumbo.api.v1.scan;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import walshe.projectcolumbo.api.v1.scan.dto.ScanRequest;
import walshe.projectcolumbo.api.v1.scan.dto.ScanResponse;

@RestController
@RequestMapping("/api/v1/scan")
@Slf4j
@Tag(name = "Market Scan", description = "Endpoints for scanning market for specific indicator conditions")
public class ScanController {

    private final ScanValidator validator;
    private final ScanService service;

    ScanController(ScanValidator validator, ScanService service) {
        this.validator = validator;
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Execute a market scan", description = "Scans all active assets for matches against provided conditions using the specified logical operator")
    ResponseEntity<ScanResponse> scan(@Valid @RequestBody ScanRequest request) {
        validator.validate(request);
        return ResponseEntity.ok(service.execute(request));
    }
}
