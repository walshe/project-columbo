package walshe.projectcolumbo.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.Timeframe;

import java.util.Optional;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

    Optional<IngestionRun> findFirstByProviderAndTimeframeAndStatusOrderByStartedAtDesc(
            MarketProvider provider, Timeframe timeframe, IngestionRunStatus status);

    Optional<IngestionRun> findTopByProviderAndTimeframeOrderByStartedAtDesc(
            MarketProvider provider, Timeframe timeframe);
}
