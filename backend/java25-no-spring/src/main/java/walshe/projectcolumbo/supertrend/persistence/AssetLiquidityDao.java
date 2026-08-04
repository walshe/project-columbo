package walshe.projectcolumbo.supertrend.persistence;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Reads the {@code v_asset_liquidity} view (rolling 7-day average D1 volume per asset). */
public final class AssetLiquidityDao {

    private final DataSource dataSource;

    public AssetLiquidityDao(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public Map<Long, BigDecimal> findAvgVolume7d() {
        String sql = "SELECT asset_id, avg_volume_7d FROM v_asset_liquidity";
        Map<Long, BigDecimal> avgVolumeByAssetId = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                BigDecimal avgVolume = resultSet.getBigDecimal("avg_volume_7d");
                avgVolumeByAssetId.put(resultSet.getLong("asset_id"), avgVolume != null ? avgVolume : BigDecimal.ZERO);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load asset liquidity", e);
        }
        return avgVolumeByAssetId;
    }
}
