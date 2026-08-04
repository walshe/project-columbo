package walshe.projectcolumbo.supertrend.pipeline;

import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

import java.util.Objects;

/** Thrown when a new pipeline run is requested while one is already RUNNING for the same provider+timeframe. */
public class IngestionAlreadyRunningException extends RuntimeException {

    public IngestionAlreadyRunningException(Provider provider, Timeframe timeframe) {
        super(messageFor(provider, timeframe));
    }

    private static String messageFor(Provider provider, Timeframe timeframe) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        return "An ingestion run is already RUNNING for " + provider + " " + timeframe;
    }
}
