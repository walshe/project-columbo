package walshe.projectcolumbo.persistence.service;

import walshe.projectcolumbo.persistence.entity.Asset;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.entity.ThermometerIndicator;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.repository.AssetRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;
import walshe.projectcolumbo.persistence.repository.ThermometerRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
/** DISABLED: Market Thermometer is not active — not invoked by the pipeline. Retained (functional) for re-enablement. */
public class ThermometerStateService {

    private static final Logger log = LoggerFactory.getLogger(ThermometerStateService.class);

    private final AssetRepository assetRepository;
    private final ThermometerRepository thermometerRepository;
    private final SignalStateRepository signalStateRepository;

    public ThermometerStateService(AssetRepository assetRepository,
                                    ThermometerRepository thermometerRepository,
                                    SignalStateRepository signalStateRepository) {
        this.assetRepository = assetRepository;
        this.thermometerRepository = thermometerRepository;
        this.signalStateRepository = signalStateRepository;
    }

    @Transactional
    public synchronized void computeForAllActiveAssets() {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting ThermometerState derivation for {} assets", activeAssets.size());

        int inserted = 0, updated = 0, skipped = 0;

        for (Asset asset : activeAssets) {
            try {
                ProcessingStats stats = computeForAsset(asset);
                inserted += stats.inserted();
                updated += stats.updated();
                skipped += stats.skipped();
            } catch (Exception e) {
                log.error("Failed to compute ThermometerState for asset {}: {}",
                        asset.getSymbol(), e.getMessage(), e);
            }
        }

        log.info("ThermometerState computation complete. inserted={}, updated={}, skipped={}",
                inserted, updated, skipped);
    }

    private ProcessingStats computeForAsset(Asset asset) {
        Optional<ThermometerIndicator> latest = thermometerRepository.findFirstByAssetOrderByCloseTimeDesc(asset);

        if (latest.isEmpty() || latest.get().getTemperatureEma() == null) {
            log.debug("Skipping ThermometerState for {}: no EMA data yet", asset.getSymbol());
            return new ProcessingStats(0, 0, 1);
        }

        ThermometerIndicator row = latest.get();
        BigDecimal temperature = row.getTemperature();
        BigDecimal ema = row.getTemperatureEma();
        OffsetDateTime closeTime = row.getCloseTime();

        // Derive state — SPIKE check takes priority
        BigDecimal tripleEma = ema.multiply(BigDecimal.valueOf(3));
        TrendState newState;
        if (temperature.compareTo(tripleEma) > 0) {
            newState = TrendState.ELDER_THERMOMETER_SPIKE;
        } else if (temperature.compareTo(ema) > 0) {
            newState = TrendState.ELDER_THERMOMETER_HOT;
        } else {
            newState = TrendState.ELDER_THERMOMETER_QUIET;
        }

        // Event detection via previous signal_state
        Optional<SignalState> previous = signalStateRepository
                .findFirstByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeDesc(
                        asset.getId(), Timeframe.D1, IndicatorType.ELDER_THERMOMETER);

        TrendState prevState = previous.map(SignalState::getTrendState).orElse(null);

        SignalEvent event = SignalEvent.NONE;
        if (newState == TrendState.ELDER_THERMOMETER_SPIKE) {
            event = SignalEvent.ELDER_THERMOMETER_TRIPLE_SPIKE;
        } else if (newState == TrendState.ELDER_THERMOMETER_HOT
                && prevState == TrendState.ELDER_THERMOMETER_QUIET) {
            event = SignalEvent.ELDER_THERMOMETER_CROSSED_ABOVE_EMA;
        } else if (newState == TrendState.ELDER_THERMOMETER_QUIET
                && (prevState == TrendState.ELDER_THERMOMETER_HOT
                    || prevState == TrendState.ELDER_THERMOMETER_SPIKE)) {
            event = SignalEvent.ELDER_THERMOMETER_CROSSED_BELOW_EMA;
        }

        // Upsert into signal_state
        Optional<SignalState> existing = signalStateRepository
                .findByAssetAndTimeframeAndIndicatorTypeAndCloseTime(
                        asset, Timeframe.D1, IndicatorType.ELDER_THERMOMETER, closeTime);

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
            signalStateRepository.save(new SignalState(
                    asset, Timeframe.D1, IndicatorType.ELDER_THERMOMETER, closeTime, newState, event));
            return new ProcessingStats(1, 0, 0);
        }
    }

    private record ProcessingStats(int inserted, int updated, int skipped) {}
}
