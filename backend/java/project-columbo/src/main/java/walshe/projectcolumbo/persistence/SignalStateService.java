package walshe.projectcolumbo.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
class SignalStateService {
    private static final Logger log = LoggerFactory.getLogger(SignalStateService.class);

    private final AssetRepository assetRepository;
    private final SuperTrendRepository superTrendRepository;
    private final SignalStateRepository signalStateRepository;
    private final SignalStateCalculator calculator;

    public SignalStateService(AssetRepository assetRepository,
                              SuperTrendRepository superTrendRepository,
                              SignalStateRepository signalStateRepository,
                              SignalStateCalculator calculator) {
        this.assetRepository = assetRepository;
        this.superTrendRepository = superTrendRepository;
        this.signalStateRepository = signalStateRepository;
        this.calculator = calculator;
    }

    @Transactional
    public void detectDaily() {
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Starting SignalState detection for {} active assets", activeAssets.size());

        for (Asset asset : activeAssets) {
            for (Timeframe timeframe : Timeframe.values()) {
                try {
                    this.processAsset(asset, timeframe, false);
                } catch (Exception e) {
                    log.error("Failed to detect SignalState for asset {} {}: {}", 
                            asset.getSymbol(), timeframe, e.getMessage(), e);
                }
            }
        }
    }

    @Transactional
    public void processAsset(Asset asset, Timeframe timeframe, boolean fullRecalc) {
        OffsetDateTime boundary = CandleFilters.utcMidnightToday(OffsetDateTime.now());

        Optional<SignalState> latestStored = signalStateRepository.findFirstByAssetIdAndTimeframeAndIndicatorTypeOrderByCloseTimeDesc(
                asset.getId(), timeframe, IndicatorType.SUPERTREND);

        List<SuperTrendIndicator> indicatorsToProcess;
        SuperTrendDirection previousDirection = null;

        if (fullRecalc || latestStored.isEmpty()) {
            indicatorsToProcess = superTrendRepository.findByAssetAndTimeframeOrderByCloseTimeAsc(asset, timeframe);
        } else {
            OffsetDateTime lastCloseTime = latestStored.get().getCloseTime();
            indicatorsToProcess = superTrendRepository.findByAssetAndTimeframeAndCloseTimeAfterOrderByCloseTimeAsc(
                    asset, timeframe, lastCloseTime);
            
            // To determine reversal for the first new row, we need the direction of the row that produced the last stored state
            Optional<SuperTrendIndicator> lastStoredIndicator = superTrendRepository.findByAssetAndTimeframeAndCloseTime(
                    asset, timeframe, lastCloseTime);
            if (lastStoredIndicator.isPresent()) {
                previousDirection = lastStoredIndicator.get().getDirection();
            }
        }

        // Phase 4: Filter out non-finalized rows
        List<SuperTrendIndicator> finalizedIndicators = indicatorsToProcess.stream()
                .filter(i -> i.getCloseTime().isBefore(boundary))
                .collect(Collectors.toList());

        if (finalizedIndicators.isEmpty()) {
            return;
        }

        List<SignalStateResult> results = calculator.calculate(finalizedIndicators, previousDirection);
        
        // Phase 6 & 7 will implement persistence, for now Phase 5 is about logic
        log.debug("Calculated {} SignalState results for {} {}", results.size(), asset.getSymbol(), timeframe);
    }
}
