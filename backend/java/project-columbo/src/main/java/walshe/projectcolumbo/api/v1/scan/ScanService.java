package walshe.projectcolumbo.api.v1.scan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import walshe.projectcolumbo.api.v1.scan.dto.*;
import walshe.projectcolumbo.persistence.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
class ScanService {

    private final SignalStateRepository signalStateRepository;
    private final AssetRepository assetRepository;

    ScanService(SignalStateRepository signalStateRepository, AssetRepository assetRepository) {
        this.signalStateRepository = signalStateRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true)
    public ScanResponse execute(ScanRequest request) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Map<Long, List<SignalState>> assetMatches = new HashMap<>();
        boolean firstCondition = true;

        for (ScanCondition condition : request.conditions()) {
            List<SignalState> matches = signalStateRepository.findLatestByCondition(
                    request.timeframe(),
                    condition.indicatorType(),
                    condition.event()
            );

            Set<Long> matchAssetIds = matches.stream()
                    .map(s -> s.getAsset().getId())
                    .collect(Collectors.toSet());

            if (request.operator() == ScanOperator.AND) {
                if (firstCondition) {
                    matches.forEach(s -> {
                        List<SignalState> list = new ArrayList<>();
                        list.add(s);
                        assetMatches.put(s.getAsset().getId(), list);
                    });
                } else {
                    assetMatches.keySet().retainAll(matchAssetIds);
                    matches.forEach(s -> {
                        List<SignalState> list = assetMatches.get(s.getAsset().getId());
                        if (list != null) {
                            list.add(s);
                        }
                    });
                }
            } else { // OR
                matches.forEach(s -> assetMatches.computeIfAbsent(s.getAsset().getId(), k -> new ArrayList<>()).add(s));
            }
            firstCondition = false;
        }

        List<ScanResult> results = assetMatches.entrySet().stream()
                .map(entry -> {
                    Long assetId = entry.getKey();
                    List<SignalState> signals = entry.getValue();
                    String symbol = signals.get(0).getAsset().getSymbol();
                    
                    List<MatchedIndicator> matchedIndicators = signals.stream()
                            .map(s -> new MatchedIndicator(s.getIndicatorType(), s.getEvent(), s.getTrendState(), null, s.getCloseTime()))
                            .toList();
                    
                    return new ScanResult(symbol, matchedIndicators);
                })
                .sorted(Comparator.comparing(ScanResult::assetSymbol))
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
}
