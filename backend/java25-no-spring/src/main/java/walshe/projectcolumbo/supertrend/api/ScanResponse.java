package walshe.projectcolumbo.supertrend.api;

import walshe.projectcolumbo.supertrend.signal.ScanResult;

import java.util.List;

public record ScanResponse(List<ScanResult> results) {
}
