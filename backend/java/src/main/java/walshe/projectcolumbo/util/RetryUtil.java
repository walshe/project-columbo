package walshe.projectcolumbo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.concurrent.Callable;

public class RetryUtil {
    private static final Logger log = LoggerFactory.getLogger(RetryUtil.class);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 10;

    public static <T> T retryOnOptimisticLock(Callable<T> operation, String operationName) throws Exception {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return operation.call();
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt < MAX_RETRIES - 1) {
                    long backoffMs = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt);
                    log.warn("Optimistic lock conflict on {} (attempt {}), retrying in {}ms", operationName, attempt + 1, backoffMs);
                    Thread.sleep(backoffMs);
                } else {
                    log.error("Optimistic lock conflict on {} after {} attempts, giving up", operationName, MAX_RETRIES);
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Retry loop exited unexpectedly");
    }

    public static void retryOnOptimisticLock(Runnable operation, String operationName) throws Exception {
        retryOnOptimisticLock(() -> {
            operation.run();
            return null;
        }, operationName);
    }
}
