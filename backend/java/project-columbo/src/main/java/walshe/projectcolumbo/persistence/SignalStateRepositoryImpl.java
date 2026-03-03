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
        // Find latest occurrence of the specific event per active asset
        String subquery = """
            SELECT MAX(s2.closeTime)
            FROM SignalState s2
            WHERE s2.asset.id = s.asset.id
              AND s2.indicatorType = :indicatorType
              AND s2.event = :event
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
        // 1. Find the latest signal_state for each active asset
        String latestSubquery = """
            SELECT MAX(s2.closeTime)
            FROM SignalState s2
            WHERE s2.asset.id = s.asset.id
              AND s2.indicatorType = :indicatorType
              AND s2.timeframe = :timeframe
            """;

        // 2. We only want assets whose LATEST state matches the requested state
        StringBuilder jpql = new StringBuilder("""
            SELECT s FROM SignalState s
            JOIN FETCH s.asset a
            WHERE a.active = true
              AND s.indicatorType = :indicatorType
              AND s.timeframe = :timeframe
              AND s.trendState = :state
              AND s.closeTime = (""").append(latestSubquery).append(")");

        // 3. If maxDaysSinceFlip is provided, we must ensure the flip happened recently.
        // The flip time is the MIN(closeTime) for the CURRENT state that has NO records of a DIFFERENT state between it and the latest.
        // A simpler way to express "flipped within X days" is:
        // There must NOT be any record with a DIFFERENT state between :flipBoundary and the LATEST state's closeTime.
        // Wait, that's not exactly "flipped within X days".
        // "Flipped within X days" means the state changed from something else to :state within the last X days.
        // So at :flipBoundary, the asset was either already in :state or in a different state.
        // If it was in a different state at :flipBoundary (or before), and is now in :state, the flip happened after :flipBoundary.
        // If it was already in :state at :flipBoundary, the flip happened before :flipBoundary.
        
        // Let's use a subquery to find the most recent record with a DIFFERENT state.
        // If that record's closeTime is >= :flipBoundary, it means it was in a different state recently, 
        // and since it's currently in :state, it must have flipped recently.
        // BUT what if it has ALWAYS been in :state and there is no "different" state? 
        // Then the flip is the very first record.
        
        if (maxDaysSinceFlip != null) {
            String lastDifferentStateSubquery = """
                SELECT MAX(s3.closeTime)
                FROM SignalState s3
                WHERE s3.asset.id = s.asset.id
                  AND s3.indicatorType = :indicatorType
                  AND s3.timeframe = :timeframe
                  AND s3.trendState != :state
                  AND s3.closeTime < s.closeTime
                """;
            
            // If there's a different state, its time must be >= :flipBoundary
            // OR if there's NO different state, the EARLIEST state must be >= :flipBoundary
            jpql.append("""
                 AND (
                    EXISTS (
                        SELECT 1 FROM SignalState s4 
                        WHERE s4.asset.id = s.asset.id 
                        AND s4.indicatorType = :indicatorType 
                        AND s4.timeframe = :timeframe 
                        AND s4.trendState != :state 
                        AND s4.closeTime < s.closeTime
                        AND s4.closeTime >= :flipBoundary
                    )
                    OR (
                        NOT EXISTS (
                            SELECT 1 FROM SignalState s5 
                            WHERE s5.asset.id = s.asset.id 
                            AND s5.indicatorType = :indicatorType 
                            AND s5.timeframe = :timeframe 
                            AND s5.trendState != :state
                            AND s5.closeTime < s.closeTime
                        )
                        AND (
                            SELECT MIN(s6.closeTime) 
                            FROM SignalState s6 
                            WHERE s6.asset.id = s.asset.id 
                            AND s6.indicatorType = :indicatorType 
                            AND s6.timeframe = :timeframe
                        ) >= :flipBoundary
                    )
                 )
                """);
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
