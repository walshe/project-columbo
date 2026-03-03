package walshe.projectcolumbo.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.OffsetDateTime;
import java.util.List;

public class SignalStateRepositoryImpl implements SignalStateRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<SignalState> findEventMatches(IndicatorType indicatorType, SignalEvent event, Timeframe timeframe, OffsetDateTime latestCloseTime, Integer maxDaysSinceCross) {
        // Find latest signal_state per active asset
        String subquery = """
            SELECT MAX(s2.closeTime)
            FROM SignalState s2
            WHERE s2.asset.id = s.asset.id
              AND s2.indicatorType = :indicatorType
              AND s2.timeframe = :timeframe
            """;

        StringBuilder jpql = new StringBuilder("""
            SELECT s FROM SignalState s
            JOIN FETCH s.asset a
            WHERE a.active = true
              AND s.indicatorType = :indicatorType
              AND s.event = :event
              AND s.timeframe = :timeframe
              AND s.closeTime = (""").append(subquery).append(")");

        if (maxDaysSinceCross != null) {
            jpql.append(" AND s.closeTime >= :crossBoundary");
        }

        TypedQuery<SignalState> query = entityManager.createQuery(jpql.toString(), SignalState.class);
        query.setParameter("indicatorType", indicatorType);
        query.setParameter("event", event);
        query.setParameter("timeframe", timeframe);

        if (maxDaysSinceCross != null) {
            OffsetDateTime crossBoundary = OffsetDateTime.now().minusDays(maxDaysSinceCross);
            query.setParameter("crossBoundary", crossBoundary);
        }

        return query.getResultList();
    }

    @Override
    public List<SignalState> findStateMatches(IndicatorType indicatorType, TrendState state, Timeframe timeframe, Integer maxDaysSinceFlip) {
        // First find latest signal_state per active asset
        String subquery = """
            SELECT MAX(s2.closeTime)
            FROM SignalState s2
            WHERE s2.asset.id = s.asset.id
              AND s2.indicatorType = :indicatorType
              AND s2.timeframe = :timeframe
            """;

        StringBuilder jpql = new StringBuilder("""
            SELECT s FROM SignalState s
            JOIN FETCH s.asset a
            WHERE a.active = true
              AND s.indicatorType = :indicatorType
              AND s.timeframe = :timeframe
              AND s.trendState = :state
              AND s.closeTime = (""").append(subquery).append(")");

        if (maxDaysSinceFlip != null) {
            jpql.append(" AND s.closeTime >= :flipBoundary");
        }

        TypedQuery<SignalState> query = entityManager.createQuery(jpql.toString(), SignalState.class);
        query.setParameter("indicatorType", indicatorType);
        query.setParameter("timeframe", timeframe);
        query.setParameter("state", state);

        if (maxDaysSinceFlip != null) {
            OffsetDateTime flipBoundary = OffsetDateTime.now().minusDays(maxDaysSinceFlip);
            query.setParameter("flipBoundary", flipBoundary);
        }

        return query.getResultList();
    }
}
