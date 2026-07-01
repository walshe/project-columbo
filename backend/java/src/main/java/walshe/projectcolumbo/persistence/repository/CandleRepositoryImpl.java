package walshe.projectcolumbo.persistence.repository;
import walshe.projectcolumbo.persistence.model.Timeframe;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CandleRepositoryImpl implements CandleRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<AssetCloseAtTime> findClosePricesAtFlipTimes(Timeframe timeframe, Map<Long, OffsetDateTime> flipCloseTimeByAssetId) {
        if (flipCloseTimeByAssetId.isEmpty()) {
            return List.of();
        }

        // Exact (assetId, closeTime) pair matching, built as a single query rather than
        // independent IN clauses — avoids matching one asset's flip time against a
        // different asset's candle row.
        List<String> clauses = new ArrayList<>();
        List<Long> assetIds = new ArrayList<>(flipCloseTimeByAssetId.keySet());
        for (int i = 0; i < assetIds.size(); i++) {
            clauses.add(String.format("(c.asset.id = :assetId%d AND c.closeTime = :closeTime%d)", i, i));
        }

        String jpql = "SELECT c.asset.id, c.closeTime, c.close FROM Candle c " +
                "WHERE c.timeframe = :timeframe AND (" + String.join(" OR ", clauses) + ")";

        Query query = entityManager.createQuery(jpql);
        query.setParameter("timeframe", timeframe);
        for (int i = 0; i < assetIds.size(); i++) {
            Long assetId = assetIds.get(i);
            query.setParameter("assetId" + i, assetId);
            query.setParameter("closeTime" + i, flipCloseTimeByAssetId.get(assetId));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> new AssetCloseAtTime((Long) row[0], (OffsetDateTime) row[1], (BigDecimal) row[2]))
                .toList();
    }
}
