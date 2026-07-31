package walshe.projectcolumbo.supertrend.persistence;

import walshe.projectcolumbo.supertrend.shared.Provider;

public record Asset(long id, String symbol, Provider provider, boolean active) {
}
