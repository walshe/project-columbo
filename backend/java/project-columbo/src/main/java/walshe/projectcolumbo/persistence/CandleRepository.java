package walshe.projectcolumbo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CandleRepository extends JpaRepository<Candle, Long> {
    List<Candle> findByAssetAndTimeframe(Asset asset, Timeframe timeframe);

    List<Candle> findByAssetAndTimeframeOrderByCloseTimeAsc(Asset asset, Timeframe timeframe);

    List<Candle> findByAssetAndTimeframeAndCloseTimeGreaterThanEqualOrderByCloseTimeAsc(Asset asset, Timeframe timeframe, OffsetDateTime closeTime);

    Optional<Candle> findByAssetAndTimeframeAndCloseTime(Asset asset, Timeframe timeframe, OffsetDateTime closeTime);

    Optional<Candle> findFirstByAssetAndTimeframeAndCloseTimeBeforeOrderByCloseTimeDesc(Asset asset, Timeframe timeframe, OffsetDateTime boundary);

    @Query(value = "SELECT * FROM candle WHERE asset_id = :assetId AND timeframe = CAST(:timeframe AS timeframe) ORDER BY close_time DESC LIMIT :limit", nativeQuery = true)
    List<Candle> findRecentByAssetAndTimeframe(@Param("assetId") Long assetId, @Param("timeframe") String timeframe, @Param("limit") int limit);

    @Query(value = "SELECT close_time FROM candle WHERE asset_id = :assetId AND timeframe = CAST(:timeframe AS timeframe) ORDER BY close_time DESC LIMIT 1", nativeQuery = true)
    Optional<Object> findLatestCloseTime(@Param("assetId") Long assetId, @Param("timeframe") String timeframe);
}
