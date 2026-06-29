package walshe.projectcolumbo.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AsyncExecutionHandler {
    private static final Logger log = LoggerFactory.getLogger(AsyncExecutionHandler.class);

    private final List<ExecutionFailure> failures = new ArrayList<>();

    public static class ExecutionFailure {
        public final String assetId;
        public final String operationName;
        public final Exception exception;

        public ExecutionFailure(String assetId, String operationName, Exception exception) {
            this.assetId = assetId;
            this.operationName = operationName;
            this.exception = exception;
        }

        @Override
        public String toString() {
            return String.format("%s for asset %s: %s", operationName, assetId, exception.getMessage());
        }
    }

    public void handleFailure(String assetId, String operationName, Exception e) {
        log.error("Async operation {} failed for asset {}: {}", operationName, assetId, e.getMessage(), e);
        failures.add(new ExecutionFailure(assetId, operationName, e));
    }

    public List<ExecutionFailure> getFailures() {
        return failures;
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public int getFailureCount() {
        return failures.size();
    }

    public String getFailureSummary() {
        if (failures.isEmpty()) {
            return "No failures";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(failures.size()).append(" failures: ");
        failures.forEach(f -> sb.append("\n  - ").append(f));
        return sb.toString();
    }

    public <T> CompletableFuture<T> wrapExecution(String assetId, String operationName, CompletableFuture<T> future) {
        return future.exceptionally(e -> {
            handleFailure(assetId, operationName, new Exception(e));
            throw new RuntimeException(e);
        });
    }
}
