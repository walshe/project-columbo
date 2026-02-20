package walshe.projectcolumbo.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.binance")
public record BinanceProperties(
    String baseUrl
) {}
