package walshe.projectcolumbo.api.v1;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.api.v1.dto.SignalSort;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.mapper.SignalStateMapper;
import walshe.projectcolumbo.config.TimeProvider;
import walshe.projectcolumbo.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SignalQueryService {

    private final SignalStateRepository signalStateRepository;
    private final AssetLiquidityRepository assetLiquidityRepository;
    private final TimeProvider timeProvider;

    public SignalQueryService(SignalStateRepository signalStateRepository, 
                            AssetLiquidityRepository assetLiquidityRepository,
                            TimeProvider timeProvider) {
        this.signalStateRepository = signalStateRepository;
        this.assetLiquidityRepository = assetLiquidityRepository;
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

        // Create a map of the latest actual flips
        Map<Long, SignalState> flipsByAssetId = latestFlips.stream()
                .collect(Collectors.toMap(s -> s.getAsset().getId(), s -> s));

        Map<Long, BigDecimal> liquidityMap = assetLiquidityRepository.findAll().stream()
                .collect(Collectors.toMap(AssetLiquidityView::getAssetId, AssetLiquidityView::getAvgVolume7d));

        List<SignalStateDto> dtos = latestSignals.stream()
                .filter(s -> stateFilter == null || s.getTrendState() == stateFilter)
                .map(s -> SignalStateMapper.toDto(s, flipsByAssetId.get(s.getAsset().getId()), now, liquidityMap.getOrDefault(s.getAsset().getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        if (sort != null) {
            Comparator<SignalStateDto> comparator = switch (sort) {
                case ASSET_ASC -> Comparator.comparing(SignalStateDto::symbol);
                case LAST_FLIP_ASC -> Comparator.comparing(SignalStateDto::lastFlipTime, Comparator.nullsLast(Comparator.naturalOrder()));
                case LAST_FLIP_DESC -> Comparator.comparing(SignalStateDto::lastFlipTime, Comparator.nullsLast(Comparator.reverseOrder()));
                case TREND_STATE_ASC -> Comparator.comparing(SignalStateDto::trendState);
                case LIQUIDITY_DESC -> Comparator.comparing(
                        dto -> dto.avgVolume7d() != null ? dto.avgVolume7d() : BigDecimal.ZERO,
                        Comparator.reverseOrder()
                );
            };
            dtos.sort(comparator);
        } else {
            // Default sort by symbol
            dtos.sort(Comparator.comparing(SignalStateDto::symbol));
        }

        return dtos;
    }
}
