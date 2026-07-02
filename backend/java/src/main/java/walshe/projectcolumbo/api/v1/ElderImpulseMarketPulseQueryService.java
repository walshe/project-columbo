package walshe.projectcolumbo.api.v1;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import walshe.projectcolumbo.api.v1.dto.MarketPulseDto;
import walshe.projectcolumbo.api.v1.mapper.MarketPulseMapper;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.Timeframe;
import walshe.projectcolumbo.persistence.repository.MarketBreadthSnapshotRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
/** DISABLED: Elder Impulse System is not active. Retained (functional) for re-enablement. */
public class ElderImpulseMarketPulseQueryService {

    private final MarketBreadthSnapshotRepository repository;

    public ElderImpulseMarketPulseQueryService(MarketBreadthSnapshotRepository repository) {
        this.repository = repository;
    }

    public Optional<MarketPulseDto> getLatestPulse(Timeframe timeframe) {
        return repository.findTopByTimeframeAndIndicatorTypeOrderBySnapshotCloseTimeDesc(
                        timeframe, IndicatorType.ELDER_IMPULSE)
                .map(MarketPulseMapper::toDto);
    }

    public List<MarketPulseDto> getPulseHistory(Timeframe timeframe, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            return repository.findByTimeframeAndIndicatorTypeOrderBySnapshotCloseTimeAsc(
                            timeframe, IndicatorType.ELDER_IMPULSE)
                    .stream()
                    .map(MarketPulseMapper::toDto)
                    .collect(Collectors.toList());
        }

        OffsetDateTime start = (from != null) ? from : OffsetDateTime.parse("1970-01-01T00:00:00Z");
        OffsetDateTime end = (to != null) ? to : OffsetDateTime.parse("9999-12-31T23:59:59Z");

        return repository.findByTimeframeAndIndicatorTypeAndSnapshotCloseTimeBetweenOrderBySnapshotCloseTimeAsc(
                        timeframe, IndicatorType.ELDER_IMPULSE, start, end)
                .stream()
                .map(MarketPulseMapper::toDto)
                .collect(Collectors.toList());
    }
}
