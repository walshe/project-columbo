package walshe.projectcolumbo.api.v1.scan;
import walshe.projectcolumbo.persistence.entity.AssetLiquidityView;
import walshe.projectcolumbo.persistence.entity.RsiIndicator;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.MarketProvider;
import walshe.projectcolumbo.persistence.model.SignalEvent;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.AssetLiquidityRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.RsiRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import walshe.projectcolumbo.api.v1.scan.dto.*;
import walshe.projectcolumbo.api.v1.util.TradingViewUtil;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ScanService {

    private static final Map<Timeframe, Integer> TIMEFRAME_PRIORITY = Map.of(
        Timeframe.D1, 1,
        Timeframe.W1, 2
    );

    private final SignalStateRepository signalStateRepository;
    private final CandleRepository candleRepository;
    private final ScanValidator scanValidator;
    private final RsiRepository rsiRepository;
    private final AssetLiquidityRepository assetLiquidityRepository;

    ScanService(SignalStateRepository signalStateRepository,
                CandleRepository candleRepository,
                ScanValidator scanValidator,
                RsiRepository rsiRepository,
                AssetLiquidityRepository assetLiquidityRepository) {
        this.signalStateRepository = signalStateRepository;
        this.candleRepository = candleRepository;
        this.scanValidator = scanValidator;
        this.rsiRepository = rsiRepository;
        this.assetLiquidityRepository = assetLiquidityRepository;
    }

    @Transactional(readOnly = true)
    public ScanResponse execute(ScanRequest request) {
        scanValidator.validate(request);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Set<Timeframe> uniqueTimeframes = request.conditions().stream()
            .map(ScanCondition::timeframe)
            .collect(Collectors.toSet());
        Map<Timeframe, OffsetDateTime> closeTimeByTimeframe = new EnumMap<>(Timeframe.class);
        for (Timeframe tf : uniqueTimeframes) {
            closeTimeByTimeframe.put(tf, getLatestFinalizedCloseTime(tf));
        }

        Map<Long, BigDecimal> liquidityMap = assetLiquidityRepository.findAll().stream()
                .collect(Collectors.toMap(
                        AssetLiquidityView::getAssetId,
                        v -> v.getAvgVolume7d() != null ? v.getAvgVolume7d() : BigDecimal.ZERO));

        Map<Long, AssetMatch> assetMatches = new HashMap<>();
        boolean firstCondition = true;

        for (ScanCondition condition : request.conditions()) {
            Timeframe effectiveTf = condition.timeframe();
            OffsetDateTime latestCloseTime = closeTimeByTimeframe.get(effectiveTf);
            List<SignalState> matches;
            if (condition.event() != null) {
                Integer maxDaysSinceEvent = condition.indicatorType() == IndicatorType.RSI
                        ? condition.maxDaysSinceCross()
                        : condition.maxDaysSinceFlip();
                matches = signalStateRepository.findEventMatches(
                        condition.indicatorType(),
                        condition.event(),
                        effectiveTf,
                        latestCloseTime,
                        maxDaysSinceEvent
                );
            } else {
                matches = signalStateRepository.findStateMatches(
                        condition.indicatorType(),
                        condition.state(),
                        effectiveTf,
                        condition.maxDaysSinceFlip()
                );
            }

            Set<Long> matchAssetIds = matches.stream()
                    .map(s -> s.getAsset().getId())
                    .collect(Collectors.toSet());

            if (request.operator() == ScanOperator.AND) {
                if (firstCondition) {
                    matches.forEach(s -> {
                        AssetMatch am = new AssetMatch(s.getAsset().getId(), s.getAsset().getSymbol(), s.getAsset().getProvider());
                        am.indicators.add(mapToMatchedIndicator(s));
                        assetMatches.put(s.getAsset().getId(), am);
                    });
                } else {
                    assetMatches.keySet().retainAll(matchAssetIds);
                    matches.forEach(s -> {
                        AssetMatch am = assetMatches.get(s.getAsset().getId());
                        if (am != null) {
                            addIndicatorIfNotPresent(am.indicators, mapToMatchedIndicator(s));
                        }
                    });
                }
                if (assetMatches.isEmpty()) break;
            } else { // OR
                matches.forEach(s -> {
                    AssetMatch am = assetMatches.computeIfAbsent(s.getAsset().getId(), k -> new AssetMatch(s.getAsset().getId(), s.getAsset().getSymbol(), s.getAsset().getProvider()));
                    addIndicatorIfNotPresent(am.indicators, mapToMatchedIndicator(s));
                });
            }
            firstCondition = false;
        }

        List<ScanResult> results = assetMatches.values().stream()
                .map(am -> {
                    List<MatchedIndicator> indicators = new ArrayList<>(am.indicators);
                    // Order indicators: RSI before SUPERTREND
                    indicators.sort(Comparator.comparing(mi -> mi.indicatorType() == IndicatorType.RSI ? 0 : 1));

                    return new ScanResult(
                            am.symbol,
                            indicators,
                            liquidityMap.getOrDefault(am.id, BigDecimal.ZERO),
                            TradingViewUtil.generateUrl(am.provider, am.symbol, highestTimeframe(indicators))
                    );
                })
                .sorted((r1, r2) -> {
                    // Sort by daysSinceCross ASC, daysSinceFlip ASC, then symbol ASC
                    // Get the min daysSinceCross for each result
                    Integer c1 = r1.matchedIndicators().stream()
                            .filter(mi -> mi instanceof RsiMatch)
                            .map(mi -> ((RsiMatch) mi).daysSinceCross())
                            .min(Comparator.naturalOrder())
                            .orElse(null);
                    Integer c2 = r2.matchedIndicators().stream()
                            .filter(mi -> mi instanceof RsiMatch)
                            .map(mi -> ((RsiMatch) mi).daysSinceCross())
                            .min(Comparator.naturalOrder())
                            .orElse(null);

                    int crossCmp = compareWithNullsLast(c1, c2);
                    if (crossCmp != 0) return crossCmp;

                    // Get the min daysSinceFlip for each result
                    Integer f1 = r1.matchedIndicators().stream()
                            .filter(mi -> mi instanceof SupertrendMatch)
                            .map(mi -> ((SupertrendMatch) mi).daysSinceFlip())
                            .min(Comparator.naturalOrder())
                            .orElse(null);
                    Integer f2 = r2.matchedIndicators().stream()
                            .filter(mi -> mi instanceof SupertrendMatch)
                            .map(mi -> ((SupertrendMatch) mi).daysSinceFlip())
                            .min(Comparator.naturalOrder())
                            .orElse(null);

                    int flipCmp = compareWithNullsLast(f1, f2);
                    if (flipCmp != 0) return flipCmp;

                    return r1.assetSymbol().compareTo(r2.assetSymbol());
                })
                .limit(request.limit() != null ? request.limit() : 100)
                .toList();

        stopWatch.stop();
        log.info("Scan completed in {}ms. Operator: {}, Conditions: {}, Results: {}",
                stopWatch.getTotalTimeMillis(),
                request.operator(),
                request.conditions().size(),
                results.size());

        return new ScanResponse(
                request.operator(),
                request.conditions(),
                results
        );
    }

    private int compareWithNullsLast(Integer a, Integer b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    private Timeframe highestTimeframe(List<MatchedIndicator> indicators) {
        return indicators.stream()
            .map(MatchedIndicator::timeframe)
            .filter(Objects::nonNull)
            .max(Comparator.comparingInt(tf -> {
                Integer priority = TIMEFRAME_PRIORITY.get(tf);
                if (priority == null) {
                    throw new IllegalArgumentException(
                        "No TradingView priority defined for timeframe: " + tf +
                        ". Add it to TIMEFRAME_PRIORITY.");
                }
                return priority;
            }))
            .orElse(Timeframe.D1);
    }

    private OffsetDateTime getLatestFinalizedCloseTime(Timeframe timeframe) {
        return candleRepository.findLatestCloseTimeForTimeframe(timeframe.name())
                .map(obj -> {
                    if (obj instanceof Instant instant) {
                        return instant.atOffset(ZoneOffset.UTC);
                    }
                    if (obj instanceof java.sql.Timestamp ts) {
                        return ts.toInstant().atOffset(ZoneOffset.UTC);
                    }
                    return (OffsetDateTime) obj;
                })
                .orElse(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS));
    }


    private void addIndicatorIfNotPresent(List<MatchedIndicator> indicators, MatchedIndicator newIndicator) {
        boolean present = indicators.stream()
                .anyMatch(mi -> {
                    if (mi.indicatorType() != newIndicator.indicatorType()) return false;
                    if (mi.timeframe() != newIndicator.timeframe()) return false;
                    if (mi instanceof SupertrendMatch s1 && newIndicator instanceof SupertrendMatch s2) {
                        return s1.event() == s2.event() && s1.state() == s2.state();
                    }
                    if (mi instanceof RsiMatch r1 && newIndicator instanceof RsiMatch r2) {
                        return r1.event() == r2.event();
                    }
                    return false;
                });
        if (!present) {
            indicators.add(newIndicator);
        }
    }

    /**
     * Returns the effective "signal date" for display purposes — the date a trader would
     * see the signal appear on a TradingView chart.
     *
     * For daily candles this is the close date (TradingView labels the Buy on the same day
     * the reversal candle closes).
     *
     * For weekly candles, TradingView labels the Buy at the OPEN of the reversal candle
     * (Monday), which is 6 days before the Sunday close stored in closeTime.
     */
    private LocalDate signalDate(OffsetDateTime closeTime, Timeframe timeframe) {
        LocalDate closeDate = closeTime.toLocalDate();
        return timeframe == Timeframe.W1 ? closeDate.minusDays(6) : closeDate;
    }

    private MatchedIndicator mapToMatchedIndicator(SignalState s) {
        if (s.getIndicatorType() == IndicatorType.RSI) {
            BigDecimal rsiVal = rsiRepository.findByAssetAndTimeframeAndCloseTime(s.getAsset(), s.getTimeframe(), s.getCloseTime())
                    .map(RsiIndicator::getRsiValue)
                    .orElse(BigDecimal.ZERO);

            int daysSinceCross = (int) ChronoUnit.DAYS.between(
                    signalDate(s.getCloseTime(), s.getTimeframe()), LocalDate.now(ZoneOffset.UTC));
            return new RsiMatch(
                    IndicatorType.RSI,
                    s.getTimeframe(),
                    s.getEvent(),
                    rsiVal.doubleValue(),
                    daysSinceCross,
                    s.getCloseTime()
            );
        } else {
            int daysSinceFlip;
            if (s.getEvent() == SignalEvent.NONE || s.getEvent() == null) {
                // Find when this trend state started
                OffsetDateTime flipTime = findFlipTime(s);
                daysSinceFlip = (int) ChronoUnit.DAYS.between(
                        signalDate(flipTime, s.getTimeframe()), LocalDate.now(ZoneOffset.UTC));
            } else {
                // It's an event — calculate days between when it appeared on chart and now
                daysSinceFlip = (int) ChronoUnit.DAYS.between(
                        signalDate(s.getCloseTime(), s.getTimeframe()), LocalDate.now(ZoneOffset.UTC));
            }
            return new SupertrendMatch(
                    s.getIndicatorType(),
                    s.getTimeframe(),
                    s.getTrendState(),
                    s.getEvent(),
                    daysSinceFlip,
                    s.getCloseTime()
            );
        }
    }

    private OffsetDateTime findFlipTime(SignalState latest) {
        // Find the most recent record for this asset/timeframe/indicator where the trend state was DIFFERENT
        // and occurred BEFORE the latest record.
        Optional<OffsetDateTime> lastDifferentStateTime = signalStateRepository.findLastDifferentStateTime(
                latest.getAsset().getId(),
                latest.getTimeframe(),
                latest.getIndicatorType(),
                latest.getTrendState(),
                latest.getCloseTime()
        );

        if (lastDifferentStateTime.isEmpty()) {
            // If no different state exists, we find the earliest record of the current state
            return signalStateRepository.findFirstCurrentStateTimeAfter(
                    latest.getAsset().getId(),
                    latest.getTimeframe(),
                    latest.getIndicatorType(),
                    latest.getTrendState(),
                    OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                    latest.getCloseTime()
            ).orElse(latest.getCloseTime());
        }

        // The flip happened at the first record of the CURRENT state that is AFTER lastDifferentStateTime
        return signalStateRepository.findFirstCurrentStateTimeAfter(
                latest.getAsset().getId(),
                latest.getTimeframe(),
                latest.getIndicatorType(),
                latest.getTrendState(),
                lastDifferentStateTime.get(),
                latest.getCloseTime()
        ).orElse(latest.getCloseTime());
    }

    private static class AssetMatch {
        final Long id;
        final String symbol;
        final MarketProvider provider;
        final List<MatchedIndicator> indicators = new ArrayList<>();

        AssetMatch(Long id, String symbol, MarketProvider provider) {
            this.id = id;
            this.symbol = symbol;
            this.provider = provider;
        }
    }
}
