package walshe.projectcolumbo.supertrend.pipeline;

import walshe.projectcolumbo.supertrend.shared.Provider;
import walshe.projectcolumbo.supertrend.shared.Timeframe;

/** Thrown when a new pipeline run is requested while one is already RUNNING for the same provider+timeframe. */
public class IngestionAlreadyRunningException extends RuntimeException {

    public IngestionAlreadyRunningException(Provider provider, Timeframe timeframe) {
        super("An ingestion run is already RUNNING for " + provider + " " + timeframe);
    }
}
