package walshe.projectcolumbo.api.v1;
import walshe.projectcolumbo.persistence.entity.AssetLiquidityView;
import walshe.projectcolumbo.persistence.entity.SignalState;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.model.TrendState;
import walshe.projectcolumbo.persistence.repository.AssetCloseAtTime;
import walshe.projectcolumbo.persistence.repository.AssetClosePrice;
import walshe.projectcolumbo.persistence.repository.AssetLiquidityRepository;
import walshe.projectcolumbo.persistence.repository.CandleRepository;
import walshe.projectcolumbo.persistence.repository.SignalStateRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.api.v1.dto.SignalSort;
import walshe.projectcolumbo.api.v1.dto.SignalStateDto;
import walshe.projectcolumbo.api.v1.mapper.SignalStateMapper;
import walshe.projectcolumbo.config.TimeProvider;


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
    private final CandleRepository candleRepository;
    private final TimeProvider timeProvider;

    public SignalQueryService(SignalStateRepository signalStateRepository,
                            AssetLiquidityRepository assetLiquidityRepository,
                            CandleRepository candleRepository,
                            TimeProvider timeProvider) {
        this.signalStateRepository = signalStateRepository;
        this.assetLiquidityRepository = assetLiquidityRepository;
        this.candleRepository = candleRepository;
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
                .collect(Collectors.toMap(
                        AssetLiquidityView::getAssetId,
                        v -> v.getAvgVolume7d() != null ? v.getAvgVolume7d() : BigDecimal.ZERO));

        Map<Long, BigDecimal> latestCloseByAssetId = candleRepository.findLatestClosePricesByTimeframe(timeframe.name()).stream()
                .collect(Collectors.toMap(AssetClosePrice::getAssetId, AssetClosePrice::getClose));

        Map<Long, OffsetDateTime> flipCloseTimeByAssetId = flipsByAssetId.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getCloseTime()));
        Map<Long, BigDecimal> flipCloseByAssetId = candleRepository.findClosePricesAtFlipTimes(timeframe, flipCloseTimeByAssetId).stream()
                .collect(Collectors.toMap(AssetCloseAtTime::assetId, AssetCloseAtTime::close));

        List<SignalStateDto> dtos = latestSignals.stream()
                .filter(s -> stateFilter == null || s.getTrendState() == stateFilter)
                .map(s -> SignalStateMapper.toDto(s, flipsByAssetId.get(s.getAsset().getId()), now,
                        liquidityMap.getOrDefault(s.getAsset().getId(), BigDecimal.ZERO),
                        flipCloseByAssetId.get(s.getAsset().getId()),
                        latestCloseByAssetId.get(s.getAsset().getId())))
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
                case PCT_CHANGE_ASC -> Comparator.comparing(SignalStateDto::pctChangeSinceFlip, Comparator.nullsLast(Comparator.naturalOrder()));
                case PCT_CHANGE_DESC -> Comparator.comparing(SignalStateDto::pctChangeSinceFlip, Comparator.nullsLast(Comparator.reverseOrder()));
            };
            dtos.sort(comparator);
        } else {
            // Default sort by symbol
            dtos.sort(Comparator.comparing(SignalStateDto::symbol));
        }

        return dtos;
    }
}
