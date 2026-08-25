package walshe.projectcolumbo.supertrend.persistence;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.AssetVenue;
import walshe.projectcolumbo.supertrend.shared.Provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetTest {

    @Test
    void rejectsNullSymbol() {
        assertThatThrownBy(() -> new Asset(1, null, Provider.BINANCE, true, AssetClass.CRYPTO, AssetVenue.SPOT, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatThrownBy(() -> new Asset(1, "  ", Provider.BINANCE, true, AssetClass.CRYPTO, AssetVenue.SPOT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsNullProvider() {
        assertThatThrownBy(() -> new Asset(1, "BTCUSDT", null, true, AssetClass.CRYPTO, AssetVenue.SPOT, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullAssetClass() {
        assertThatThrownBy(() -> new Asset(1, "BTCUSDT", Provider.BINANCE, true, null, AssetVenue.SPOT, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullVenue() {
        assertThatThrownBy(() -> new Asset(1, "BTCUSDT", Provider.BINANCE, true, AssetClass.CRYPTO, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nameIsOptional() {
        Asset withoutName = new Asset(1, "BTCUSDT", Provider.BINANCE, true, AssetClass.CRYPTO, AssetVenue.SPOT, null, null);
        assertThat(withoutName.name()).isNull();

        Asset withName = new Asset(1, "BTCUSDT", Provider.BINANCE, true, AssetClass.CRYPTO, AssetVenue.SPOT, "Bitcoin", null);
        assertThat(withName.name()).isEqualTo("Bitcoin");
    }

    @Test
    void tradingviewRefIsOptional() {
        Asset withoutRef = new Asset(1, "AAPL", Provider.TIINGO, true, AssetClass.STOCK, AssetVenue.EXCHANGE, "Apple Inc", null);
        assertThat(withoutRef.tradingviewRef()).isNull();

        Asset withRef = new Asset(1, "AAPL", Provider.TIINGO, true, AssetClass.STOCK, AssetVenue.EXCHANGE, "Apple Inc", "NASDAQ:AAPL");
        assertThat(withRef.tradingviewRef()).isEqualTo("NASDAQ:AAPL");
    }
}
