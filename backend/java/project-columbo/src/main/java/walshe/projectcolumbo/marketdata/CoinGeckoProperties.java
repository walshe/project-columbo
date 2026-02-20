package walshe.projectcolumbo.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration properties for CoinGecko market data provider.
 *
 * @param apiKey the optional demo/pro API key for CoinGecko
 * @param baseUrl the base URL for CoinGecko API
 */
@ConfigurationProperties(prefix = "app.coingecko")
@Validated
record CoinGeckoProperties(
    String apiKey,
    @NotBlank String baseUrl
) {}
