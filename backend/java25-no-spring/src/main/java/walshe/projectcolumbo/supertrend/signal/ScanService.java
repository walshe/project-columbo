package walshe.projectcolumbo.supertrend.signal;

import walshe.projectcolumbo.supertrend.shared.AssetClass;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SuperTrend-only condition matching: each {@link ScanCondition} is evaluated independently
 * against its own timeframe (conditions may target different timeframes in the same request),
 * then the per-condition asset-symbol matches are combined by {@link ScanRequest#operator()} -
 * intersected for {@code AND}, unioned for {@code OR}. A standalone capability, not used
 * internally by {@code signals-api}/{@code summary-api}/{@code trend-alignment-api}.
 */
public final class ScanService {

    private final SignalQueryService signalQueryService;
    private final Clock clock;

    public ScanService(SignalQueryService signalQueryService, Clock clock) {
        this.signalQueryService = Objects.requireNonNull(signalQueryService, "signalQueryService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public List<ScanResult> execute(ScanRequest request) {
        List<List<SignalSummary>> perConditionCandidates = request.conditions().stream()
                .map(condition -> signalQueryService.listSignals(condition.timeframe(), condition.state(), null, request.assetClass()))
                .toList();
        return combine(request, perConditionCandidates, OffsetDateTime.now(clock));
    }

    /** Pure: per-condition matching, AND/OR combination, limit truncation - testable without a database. */
    static List<ScanResult> combine(ScanRequest request, List<List<SignalSummary>> perConditionCandidates, OffsetDateTime now) {
        List<ScanCondition> conditions = request.conditions();
        Map<String, List<ScanConditionMatch>> matchesBySymbol = new LinkedHashMap<>();
        Map<String, AssetClass> assetClassBySymbol = new LinkedHashMap<>();
        Map<String, BigDecimal> avgVolumeBySymbol = new LinkedHashMap<>();

        for (int i = 0; i < conditions.size(); i++) {
            Map<String, ScanConditionMatch> conditionMatches = matchesForCondition(
                    conditions.get(i), perConditionCandidates.get(i), now, assetClassBySymbol, avgVolumeBySymbol);

            if (i == 0) {
                conditionMatches.forEach((symbol, match) -> matchesBySymbol.put(symbol, newMatchList(match)));
            } else if (request.operator() == ScanOperator.AND) {
                matchesBySymbol.keySet().retainAll(conditionMatches.keySet());
                matchesBySymbol.forEach((symbol, matches) -> matches.add(conditionMatches.get(symbol)));
            } else {
                conditionMatches.forEach((symbol, match) ->
                        matchesBySymbol.computeIfAbsent(symbol, ignored -> new ArrayList<>()).add(match));
            }
        }

        List<ScanResult> results = matchesBySymbol.entrySet().stream()
                .map(entry -> new ScanResult(
                        entry.getKey(),
                        assetClassBySymbol.get(entry.getKey()),
                        List.copyOf(entry.getValue()),
                        avgVolumeBySymbol.get(entry.getKey())))
                .sorted(comparatorFor(request.sort()))
                .toList();

        return request.limit() != null ? results.stream().limit(request.limit()).toList() : results;
    }

    private static Comparator<ScanResult> comparatorFor(ScanSort sort) {
        ScanSort effective = sort != null ? sort : ScanSort.SYMBOL_ASC;
        return switch (effective) {
            case SYMBOL_ASC -> Comparator.comparing(ScanResult::symbol);
            case LIQUIDITY_DESC -> Comparator.comparing(ScanResult::avgVolume7d).reversed();
        };
    }

    private static Map<String, ScanConditionMatch> matchesForCondition(
            ScanCondition condition, List<SignalSummary> candidates, OffsetDateTime now,
            Map<String, AssetClass> assetClassBySymbol, Map<String, BigDecimal> avgVolumeBySymbol) {
        Map<String, ScanConditionMatch> matches = new LinkedHashMap<>();
        for (SignalSummary candidate : candidates) {
            Long daysSinceFlip = candidate.daysSinceFlip(now);
            if (condition.maxDaysSinceFlip() != null && (daysSinceFlip == null || daysSinceFlip > condition.maxDaysSinceFlip())) {
                continue;
            }
            matches.put(candidate.symbol(), new ScanConditionMatch(
                    condition.timeframe(), candidate.trendState(), candidate.lastFlipTime(), daysSinceFlip, candidate.tradingviewUrl()));
            assetClassBySymbol.put(candidate.symbol(), candidate.assetClass());
            avgVolumeBySymbol.put(candidate.symbol(), candidate.avgVolume7d());
        }
        return matches;
    }

    private static List<ScanConditionMatch> newMatchList(ScanConditionMatch first) {
        List<ScanConditionMatch> matches = new ArrayList<>();
        matches.add(first);
        return matches;
    }
}
