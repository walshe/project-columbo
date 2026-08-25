package walshe.projectcolumbo.supertrend.pipeline;

import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.util.Objects;

/** Thrown when a new pipeline run is requested while one is already RUNNING for the same timeframe. */
public class IngestionAlreadyRunningException extends RuntimeException {

    public IngestionAlreadyRunningException(Timeframe timeframe) {
        super(messageFor(timeframe));
    }

    private static String messageFor(Timeframe timeframe) {
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        return "An ingestion run is already RUNNING for " + timeframe;
    }
}
