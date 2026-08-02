package walshe.projectcolumbo.supertrend.signal;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        this.signalQueryService = signalQueryService;
        this.clock = clock;
    }

    public List<ScanResult> execute(ScanRequest request) {
        List<List<SignalSummary>> perConditionCandidates = request.conditions().stream()
                .map(condition -> signalQueryService.listSignals(condition.timeframe(), condition.state(), null))
                .toList();
        return combine(request, perConditionCandidates, OffsetDateTime.now(clock));
    }

    /** Pure: per-condition matching, AND/OR combination, limit truncation - testable without a database. */
    static List<ScanResult> combine(ScanRequest request, List<List<SignalSummary>> perConditionCandidates, OffsetDateTime now) {
        List<ScanCondition> conditions = request.conditions();
        Map<String, List<ScanConditionMatch>> matchesBySymbol = new LinkedHashMap<>();

        for (int i = 0; i < conditions.size(); i++) {
            Map<String, ScanConditionMatch> conditionMatches = matchesForCondition(conditions.get(i), perConditionCandidates.get(i), now);

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
                .map(entry -> new ScanResult(entry.getKey(), List.copyOf(entry.getValue())))
                .sorted(Comparator.comparing(ScanResult::symbol))
                .toList();

        return request.limit() != null ? results.stream().limit(request.limit()).toList() : results;
    }

    private static Map<String, ScanConditionMatch> matchesForCondition(ScanCondition condition, List<SignalSummary> candidates, OffsetDateTime now) {
        Map<String, ScanConditionMatch> matches = new LinkedHashMap<>();
        for (SignalSummary candidate : candidates) {
            Long daysSinceFlip = candidate.daysSinceFlip(now);
            if (condition.maxDaysSinceFlip() != null && (daysSinceFlip == null || daysSinceFlip > condition.maxDaysSinceFlip())) {
                continue;
            }
            matches.put(candidate.symbol(), new ScanConditionMatch(condition.timeframe(), candidate.trendState(), candidate.lastFlipTime(), daysSinceFlip));
        }
        return matches;
    }

    private static List<ScanConditionMatch> newMatchList(ScanConditionMatch first) {
        List<ScanConditionMatch> matches = new ArrayList<>();
        matches.add(first);
        return matches;
    }
}
