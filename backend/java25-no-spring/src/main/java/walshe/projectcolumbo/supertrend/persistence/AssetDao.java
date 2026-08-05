package walshe.projectcolumbo.supertrend.persistence;

import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Provider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AssetDao {

    private final DataSource dataSource;

    public AssetDao(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    public List<Asset> findAllActive() {
        return findAllActive(null);
    }

    /** @param assetClassFilter restricts results to this class only; every active asset is returned when null. */
    public List<Asset> findAllActive(AssetClass assetClassFilter) {
        String sql = assetClassFilter == null
                ? "SELECT id, symbol, provider, active, asset_class FROM asset WHERE active = true ORDER BY id"
                : "SELECT id, symbol, provider, active, asset_class FROM asset WHERE active = true AND asset_class = ?::asset_class ORDER BY id";
        List<Asset> assets = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (assetClassFilter != null) {
                statement.setString(1, assetClassFilter.name());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    assets.add(mapRow(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load active assets", e);
        }
        return assets;
    }

    /** Marks an asset inactive so future ingestion runs skip it (e.g. after a provider invalid-symbol error). */
    public void deactivate(long assetId) {
        String sql = "UPDATE asset SET active = false WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assetId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to deactivate asset " + assetId, e);
        }
    }

    private static Asset mapRow(ResultSet resultSet) throws SQLException {
        return new Asset(
                resultSet.getLong("id"),
                resultSet.getString("symbol"),
                Provider.valueOf(resultSet.getString("provider")),
                resultSet.getBoolean("active"),
                AssetClass.valueOf(resultSet.getString("asset_class"))
        );
    }
}
