package walshe.projectcolumbo.supertrend.persistence;

import org.junit.jupiter.api.Test;
import walshe.projectcolumbo.supertrend.shared.AssetClass;
import walshe.projectcolumbo.supertrend.shared.Provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetTest {

    @Test
    void rejectsNullSymbol() {
        assertThatThrownBy(() -> new Asset(1, null, Provider.BINANCE, true, AssetClass.CRYPTO))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatThrownBy(() -> new Asset(1, "  ", Provider.BINANCE, true, AssetClass.CRYPTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsNullProvider() {
        assertThatThrownBy(() -> new Asset(1, "BTCUSDT", null, true, AssetClass.CRYPTO))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullAssetClass() {
        assertThatThrownBy(() -> new Asset(1, "BTCUSDT", Provider.BINANCE, true, null))
                .isInstanceOf(NullPointerException.class);
    }
}
