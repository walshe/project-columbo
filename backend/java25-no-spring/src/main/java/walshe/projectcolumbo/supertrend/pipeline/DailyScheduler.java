package walshe.projectcolumbo.supertrend.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Triggers {@link PipelineOrchestrator#runDaily} once per day at a fixed UTC time, via the
 * identical orchestration path a manual trigger uses — no separate scheduled-vs-manual code
 * path. Re-schedules itself after each firing rather than using a fixed-rate schedule, so a
 * slow run never causes overlapping executions.
 */
public final class DailyScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DailyScheduler.class);
    private static final LocalTime SCHEDULED_TIME_UTC = LocalTime.of(0, 5);

    private final PipelineOrchestrator orchestrator;
    private final Provider provider;
    private final Timeframe timeframe;
    private final Clock clock;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("daily-scheduler").factory());

    public DailyScheduler(PipelineOrchestrator orchestrator, Provider provider, Timeframe timeframe, Clock clock) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.timeframe = Objects.requireNonNull(timeframe, "timeframe must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void start() {
        scheduleNext();
    }

    public void stop() {
        executor.shutdownNow();
    }

    private void scheduleNext() {
        executor.schedule(this::runAndRescheduleNext, millisUntilNextRun(), TimeUnit.MILLISECONDS);
    }

    private void runAndRescheduleNext() {
        try {
            LOG.info("Scheduled daily trigger firing for {} {}", provider, timeframe);
            orchestrator.runDaily(provider, timeframe);
        } catch (IngestionAlreadyRunningException e) {
            LOG.info("Scheduled daily trigger skipped - already running: {}", e.getMessage());
        } catch (Exception e) {
            LOG.error("Scheduled daily trigger failed", e);
        } finally {
            scheduleNext();
        }
    }

    private long millisUntilNextRun() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime nextRun = now.toLocalDate().atTime(SCHEDULED_TIME_UTC).atOffset(ZoneOffset.UTC);
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).toMillis();
    }
}
