package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.EmaIndicator;
import walshe.projectcolumbo.persistence.entity.MacdIndicator;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.EmaRepository;
import walshe.projectcolumbo.persistence.repository.MacdRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
/** DISABLED: Elder Impulse System is not active — not invoked by the pipeline. Retained (functional) for re-enablement. */
public class ElderImpulseStateService {

    private static final Logger log = LoggerFactory.getLogger(ElderImpulseStateService.class);

    private static final int D1_EMA_PERIOD = 13;
    private static final int W1_EMA_PERIOD = 26;

    private final AssetRepository assetRepository;
    private final EmaRepository emaRepository;
    private final MacdRepository macdRepository;
    private final SignalStateRepository signalStateRepository;

    public ElderImpulseStateService(AssetRepository assetRepository,
                                    EmaRepository emaRepository,
                                    MacdRepository macdRepository,
                                    SignalStateRepository signalStateRepository) {
        this.assetRepository = assetRepository;
        this.emaRepository = emaRepository;
        this.macdRepository = macdRepository;
        this.signalStateRepository = signalStateRepository;
    }

    @Transactional
    public synchronized void computeForAllActiveAssets(Timeframe timeframe) {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting ElderImpulse state derivation for {} assets (timeframe={})",
                activeAssets.size(), timeframe);

        int inserted = 0, updated = 0, skipped = 0;

        for (Asset asset : activeAssets) {
            try {
                ProcessingStats stats = computeForAsset(asset, timeframe);
                inserted += stats.inserted;
                updated += stats.updated;
                skipped += stats.skipped;
            } catch (Exception e) {
                log.error("Failed to compute ElderImpulse state for asset {} {}: {}",
                        asset.getSymbol(), timeframe, e.getMessage(), e);
            }
        }

        log.info("ElderImpulse state derivation completed for timeframe={}. inserted={}, updated={}, skipped={}",
                timeframe, inserted, updated, skipped);
    }

    private ProcessingStats computeForAsset(Asset asset, Timeframe timeframe) {
        return timeframe == Timeframe.W1
                ? computeW1ForAsset(asset)
                : computeD1ForAsset(asset);
    }

    private ProcessingStats computeD1ForAsset(Asset asset) {
        // Early-exit guard: if the latest stored D1 Elder Impulse signal_state already reflects
        // the latest D1 EMA bar, the underlying EMA and MACD indicators have not changed since
        // the last run — nothing new to derive.  Two cheap single-row lookups avoid loading the
        // full EMA and MACD history for assets where no new candle has arrived.
        Optional<SignalState> latestStoredState = signalStateRepository
                .findFirstByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeDesc(
                        asset.getId(), Timeframe.D1, IndicatorType.ELDER_IMPULSE);
        Optional<EmaIndicator> latestEmaCheck = emaRepository
                .findFirstByAssetAndTimeframeAndPeriodOrderByCloseTimeDesc(asset, Timeframe.D1, D1_EMA_PERIOD);
        if (latestStoredState.isPresent() && latestEmaCheck.isPresent()
                && latestStoredState.get().getCloseTime().equals(latestEmaCheck.get().getCloseTime())) {
            log.debug("D1 ElderImpulse already up-to-date for {} — skipping", asset.getSymbol());
            return new ProcessingStats(0, 0, 1);
        }

        List<EmaIndicator> emaRows = emaRepository.findByAssetAndTimeframeAndPeriodOrderByCloseTimeAsc(
                asset, Timeframe.D1, D1_EMA_PERIOD);
        List<MacdIndicator> macdRows = macdRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(
                asset, Timeframe.D1);

        if (emaRows.size() < 2 || macdRows.size() < 2) {
            log.debug("Insufficient indicator data for D1 ElderImpulse: asset={}, emaRows={}, macdRows={}",
                    asset.getSymbol(), emaRows.size(), macdRows.size());
            return new ProcessingStats(0, 0, 1);
        }

        EmaIndicator latestEma = emaRows.get(emaRows.size() - 1);
        EmaIndicator prevEma = emaRows.get(emaRows.size() - 2);
        MacdIndicator latestMacd = macdRows.get(macdRows.size() - 1);
        MacdIndicator prevMacd = macdRows.get(macdRows.size() - 2);

        BigDecimal emaSlope = latestEma.getEmaValue().subtract(prevEma.getEmaValue());
        BigDecimal macdSlope = latestMacd.getHistogram().subtract(prevMacd.getHistogram());

        TrendState newState;
        if (emaSlope.compareTo(BigDecimal.ZERO) > 0 && macdSlope.compareTo(BigDecimal.ZERO) > 0) {
            newState = TrendState.ELDER_IMPULSE_GREEN;
        } else if (emaSlope.compareTo(BigDecimal.ZERO) < 0 && macdSlope.compareTo(BigDecimal.ZERO) < 0) {
            newState = TrendState.ELDER_IMPULSE_RED;
        } else {
            newState = TrendState.ELDER_IMPULSE_NEUTRAL;
        }

        return persistState(asset, Timeframe.D1, latestEma.getCloseTime(), newState);
    }

    private ProcessingStats computeW1ForAsset(Asset asset) {
        // Early-exit guard: same logic as D1 but for the weekly timeframe.
        // If the latest stored W1 Elder Impulse state already reflects the latest W1 EMA bar,
        // no new weekly candle has been rolled up since the last run — skip the full-history load.
        Optional<SignalState> latestStoredState = signalStateRepository
                .findFirstByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeDesc(
                        asset.getId(), Timeframe.W1, IndicatorType.ELDER_IMPULSE);
        Optional<EmaIndicator> latestEmaCheck = emaRepository
                .findFirstByAssetAndTimeframeAndPeriodOrderByCloseTimeDesc(asset, Timeframe.W1, W1_EMA_PERIOD);
        if (latestStoredState.isPresent() && latestEmaCheck.isPresent()
                && latestStoredState.get().getCloseTime().equals(latestEmaCheck.get().getCloseTime())) {
            log.debug("W1 ElderImpulse already up-to-date for {} — skipping", asset.getSymbol());
            return new ProcessingStats(0, 0, 1);
        }

        List<EmaIndicator> emaRows = emaRepository.findByAssetAndTimeframeAndPeriodOrderByCloseTimeAsc(
                asset, Timeframe.W1, W1_EMA_PERIOD);

        if (emaRows.size() < 2) {
            log.debug("Insufficient EMA data for W1 ElderImpulse: asset={}, emaRows={}",
                    asset.getSymbol(), emaRows.size());
            return new ProcessingStats(0, 0, 1);
        }

        EmaIndicator latestEma = emaRows.get(emaRows.size() - 1);
        EmaIndicator prevEma = emaRows.get(emaRows.size() - 2);

        BigDecimal emaSlope = latestEma.getEmaValue().subtract(prevEma.getEmaValue());

        TrendState newState;
        if (emaSlope.compareTo(BigDecimal.ZERO) > 0) {
            newState = TrendState.ELDER_IMPULSE_GREEN;
        } else if (emaSlope.compareTo(BigDecimal.ZERO) < 0) {
            newState = TrendState.ELDER_IMPULSE_RED;
        } else {
            newState = TrendState.ELDER_IMPULSE_NEUTRAL;
        }

        return persistState(asset, Timeframe.W1, latestEma.getCloseTime(), newState);
    }

    private ProcessingStats persistState(Asset asset, Timeframe timeframe,
                                         OffsetDateTime closeTime, TrendState newState) {
        Optional<SignalState> previous = signalStateRepository
                .findFirstByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeDesc(
                        asset.getId(), timeframe, IndicatorType.ELDER_IMPULSE);

        SignalEvent event = SignalEvent.NONE;
        if (previous.isEmpty() || previous.get().getTrendState() != newState) {
            event = switch (newState) {
                case ELDER_IMPULSE_GREEN -> SignalEvent.ELDER_IMPULSE_TURNED_GREEN;
                case ELDER_IMPULSE_RED -> SignalEvent.ELDER_IMPULSE_TURNED_RED;
                case ELDER_IMPULSE_NEUTRAL -> SignalEvent.ELDER_IMPULSE_TURNED_NEUTRAL;
                default -> SignalEvent.NONE;
            };
        }

        Optional<SignalState> existing = signalStateRepository
                .findByAssetAndTimeframeAndIndicatorTypeAndCloseTime(
                        asset, timeframe, IndicatorType.ELDER_IMPULSE, closeTime);

        if (existing.isPresent()) {
            SignalState stored = existing.get();
            if (stored.getTrendState() == newState && stored.getEvent() == event) {
                return new ProcessingStats(0, 0, 1);
            }
            stored.setTrendState(newState);
            stored.setEvent(event);
            signalStateRepository.save(stored);
            return new ProcessingStats(0, 1, 0);
        } else {
            SignalState newSignalState = new SignalState(
                    asset, timeframe, IndicatorType.ELDER_IMPULSE, closeTime, newState, event);
            signalStateRepository.save(newSignalState);
            return new ProcessingStats(1, 0, 0);
        }
    }

    private record ProcessingStats(int inserted, int updated, int skipped) {}
}
