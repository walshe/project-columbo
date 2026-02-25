package walshe.projectcolumbo.api.v1;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.api.v1.mapper.MarketPulseMapper;
import walshe.projectcolumbo.persistence.IndicatorType;
import walshe.projectcolumbo.persistence.MarketBreadthSnapshotRepository;
import walshe.projectcolumbo.persistence.Timeframe;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MarketPulseQueryService {

    private final MarketBreadthSnapshotRepository repository;

    public MarketPulseQueryService(MarketBreadthSnapshotRepository repository) {
        this.repository = repository;
    }

    public Optional<MarketPulseDto> getLatestPulse(Timeframe timeframe, IndicatorType indicatorType) {
        return repository.findTopByTimeframeAndIndicatorTypeOrderBySnapshotCloseTimeDesc(timeframe, indicatorType)
                .map(MarketPulseMapper::toDto);
    }

    public List<MarketPulseDto> getPulseHistory(Timeframe timeframe, IndicatorType indicatorType, OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime start = (from != null) ? from : OffsetDateTime.MIN;
        OffsetDateTime end = (to != null) ? to : OffsetDateTime.MAX;

        return repository.findByTimeframeAndIndicatorTypeAndSnapshotCloseTimeBetweenOrderBySnapshotCloseTimeAsc(
                        timeframe, indicatorType, start, end)
                .stream()
                .map(MarketPulseMapper::toDto)
                .collect(Collectors.toList());
    }
}
