package walshe.projectcolumbo.api.v1.scan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import walshe.projectcolumbo.api.v1.scan.dto.*;
import walshe.projectcolumbo.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
class ScanService {

    private final SignalStateRepository signalStateRepository;
    private final CandleRepository candleRepository;
    private final ScanValidator scanValidator;
    private final RsiRepository rsiRepository;

    ScanService(SignalStateRepository signalStateRepository,
                CandleRepository candleRepository,
                ScanValidator scanValidator,
                RsiRepository rsiRepository) {
        this.signalStateRepository = signalStateRepository;
        this.candleRepository = candleRepository;
        this.scanValidator = scanValidator;
        this.rsiRepository = rsiRepository;
    }

    @Transactional(readOnly = true)
    public ScanResponse execute(ScanRequest request) {
        scanValidator.validate(request);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        OffsetDateTime latestCloseTime = getLatestFinalizedCloseTime(request.timeframe());

        Map<Long, AssetMatch> assetMatches = new HashMap<>();
        boolean firstCondition = true;

        for (ScanCondition condition : request.conditions()) {
            List<SignalState> matches;
            if (condition.event() != null) {
                matches = signalStateRepository.findEventMatches(
                        condition.indicatorType(),
                        condition.event(),
                        request.timeframe(),
                        latestCloseTime,
                        condition.maxDaysSinceCross()
                );
            } else {
                matches = signalStateRepository.findStateMatches(
                        condition.indicatorType(),
                        condition.state(),
                        request.timeframe(),
                        condition.maxDaysSinceFlip()
                );
            }

            Set<Long> matchAssetIds = matches.stream()
                    .map(s -> s.getAsset().getId())
                    .collect(Collectors.toSet());

            if (request.operator() == ScanOperator.AND) {
                if (firstCondition) {
                    matches.forEach(s -> {
                        AssetMatch am = new AssetMatch(s.getAsset().getSymbol());
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
                    AssetMatch am = assetMatches.computeIfAbsent(s.getAsset().getId(), k -> new AssetMatch(s.getAsset().getSymbol()));
                    addIndicatorIfNotPresent(am.indicators, mapToMatchedIndicator(s));
                });
            }
            firstCondition = false;
        }

        List<ScanResult> results = assetMatches.values().stream()
                .map(am -> new ScanResult(am.symbol, am.indicators))
                .sorted((r1, r2) -> {
                    // Sort by earliest indicator's closeTime (DESC) then symbol (ASC)
                    OffsetDateTime t1 = r1.matchedIndicators().stream()
                            .map(MatchedIndicator::closeTime)
                            .max(Comparator.naturalOrder())
                            .orElse(OffsetDateTime.MIN);
                    OffsetDateTime t2 = r2.matchedIndicators().stream()
                            .map(MatchedIndicator::closeTime)
                            .max(Comparator.naturalOrder())
                            .orElse(OffsetDateTime.MIN);
                    int timeCmp = t2.compareTo(t1);
                    return timeCmp != 0 ? timeCmp : r1.assetSymbol().compareTo(r2.assetSymbol());
                })
                .limit(request.limit() != null ? request.limit() : 100)
                .toList();

        stopWatch.stop();
        log.info("Scan completed in {}ms. Operator: {}, Timeframe: {}, Conditions: {}, Results: {}",
                stopWatch.getTotalTimeMillis(),
                request.operator(),
                request.timeframe(),
                request.conditions().size(),
                results.size());

        return new ScanResponse(
                request.timeframe(),
                request.operator(),
                request.conditions(),
                results
        );
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

    private MatchedIndicator mapToMatchedIndicator(SignalState s) {
        if (s.getIndicatorType() == IndicatorType.RSI) {
            BigDecimal rsiVal = rsiRepository.findByAssetAndTimeframeAndCloseTime(s.getAsset(), s.getTimeframe(), s.getCloseTime())
                    .map(RsiIndicator::getRsiValue)
                    .orElse(BigDecimal.ZERO);

            // For RSI, daysSinceCross is 0 if it's the current candle match,
            // we'll use 0 for now as specified in the requirement "0 = today"
            return new RsiMatch(
                    IndicatorType.RSI,
                    s.getEvent(),
                    rsiVal.doubleValue(),
                    0,
                    s.getCloseTime()
            );
        } else {
            int daysSinceFlip;
            if (s.getEvent() == SignalEvent.NONE || s.getEvent() == null) {
                // Find when this trend state started
                OffsetDateTime flipTime = findFlipTime(s);
                daysSinceFlip = (int) ChronoUnit.DAYS.between(flipTime, OffsetDateTime.now(ZoneOffset.UTC));
            } else {
                // It's an event, so it happened at s.getCloseTime()
                daysSinceFlip = 0;
            }
            return new SupertrendMatch(
                    s.getIndicatorType(),
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
        final String symbol;
        final List<MatchedIndicator> indicators = new ArrayList<>();

        AssetMatch(String symbol) {
            this.symbol = symbol;
        }
    }
}
