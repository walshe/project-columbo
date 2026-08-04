package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.signal.ScanResult;

import java.util.List;
import java.util.Objects;

public record ScanResponse(List<ScanResult> results) {
    public ScanResponse {
        Objects.requireNonNull(results, "results must not be null");
    }
}
