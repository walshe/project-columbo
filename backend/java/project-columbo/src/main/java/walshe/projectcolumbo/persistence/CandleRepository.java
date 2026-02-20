package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CandleRepository extends JpaRepository<Candle, Long> {
    List<Candle> findByAssetAndTimeframe(Asset asset, Timeframe timeframe);

    List<Candle> findByAssetAndTimeframeOrderByCloseTimeAsc(Asset asset, Timeframe timeframe);

    List<Candle> findByAssetAndTimeframeAndCloseTimeGreaterThanEqualOrderByCloseTimeAsc(Asset asset, Timeframe timeframe, OffsetDateTime closeTime);

    Optional<Candle> findByAssetAndTimeframeAndCloseTime(Asset asset, Timeframe timeframe, OffsetDateTime closeTime);
}
