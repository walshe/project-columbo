package walshe.projectcolumbo.supertrend.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Runs one function per item in parallel, one virtual thread per item, and waits for all to
 * finish before returning. Callers only ever use this for per-asset work <em>within</em> one
 * pipeline phase — cross-phase ordering always stays sequential; only work within a phase is
 * parallelized. Error isolation (one item's failure not affecting others) is the caller's
 * responsibility (catch inside the supplied function) — this utility only parallelizes.
 */
public final class ParallelAssetExecutor {

    private ParallelAssetExecutor() {
    }

    public static <T, R> List<R> runForEachItem(List<T> items, Function<T, R> work) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> futures = items.stream()
                    .map(item -> executor.submit(() -> work.apply(item)))
                    .toList();

            List<R> results = new ArrayList<>(futures.size());
            for (Future<R> future : futures) {
                results.add(awaitResult(future));
            }
            return results;
        }
    }

    private static <R> R awaitResult(Future<R> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while awaiting parallel asset work", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Parallel asset work failed", e.getCause());
        }
    }
}
