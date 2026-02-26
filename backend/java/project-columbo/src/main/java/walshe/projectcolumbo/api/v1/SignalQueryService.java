package walshe.projectcolumbo.api.v1;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.api.v1.dto.SignalSort;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.mapper.SignalStateMapper;
import walshe.projectcolumbo.config.TimeProvider;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.SignalState;
import walshe.projectcolumbo.persistence.Timeframe;
import walshe.projectcolumbo.persistence.TrendState;
import walshe.projectcolumbo.persistence.SignalStateRepository;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SignalQueryService {

    private final SignalStateRepository signalStateRepository;
    private final TimeProvider timeProvider;

    public SignalQueryService(SignalStateRepository signalStateRepository, TimeProvider timeProvider) {
        this.signalStateRepository = signalStateRepository;
        this.timeProvider = timeProvider;
    }

    public List<SignalStateDto> listSignals(Timeframe timeframe, 
                                          IndicatorType indicatorType, 
                                          TrendState stateFilter, 
                                          SignalSort sort) {
        OffsetDateTime now = timeProvider.now();
        OffsetDateTime boundary = now.truncatedTo(ChronoUnit.DAYS);

        List<SignalState> latestSignals = signalStateRepository.findLatestFinalizedForActiveAssets(timeframe, indicatorType, boundary);
        List<SignalState> latestFlips = signalStateRepository.findLatestFinalizedFlipsForActiveAssets(timeframe, indicatorType, boundary);
        List<SignalState> earliestStates = signalStateRepository.findEarliestFinalizedForActiveAssets(timeframe, indicatorType, boundary);

        Map<Long, SignalState> flipsByAssetId = latestFlips.stream()
                .collect(Collectors.toMap(s -> s.getAsset().getId(), s -> s));
        
        // Add fallbacks for assets that never flipped
        earliestStates.forEach(s -> flipsByAssetId.putIfAbsent(s.getAsset().getId(), s));

        List<SignalStateDto> dtos = latestSignals.stream()
                .filter(s -> stateFilter == null || s.getTrendState() == stateFilter)
                .map(s -> SignalStateMapper.toDto(s, flipsByAssetId.get(s.getAsset().getId()), now))
                .collect(Collectors.toList());

        if (sort != null) {
            Comparator<SignalStateDto> comparator = switch (sort) {
                case ASSET_ASC -> Comparator.comparing(SignalStateDto::symbol);
                case LAST_FLIP_ASC -> Comparator.comparing(SignalStateDto::lastFlipTime, Comparator.nullsLast(Comparator.naturalOrder()));
                case LAST_FLIP_DESC -> Comparator.comparing(SignalStateDto::lastFlipTime, Comparator.nullsFirst(Comparator.reverseOrder()));
                case TREND_STATE_ASC -> Comparator.comparing(SignalStateDto::trendState);
            };
            dtos.sort(comparator);
        } else {
            // Default sort by symbol
            dtos.sort(Comparator.comparing(SignalStateDto::symbol));
        }

        return dtos;
    }
}
