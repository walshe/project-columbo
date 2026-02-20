package walshe.projectcolumbo.persistence;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Timeframe {
    D1("1D");

    private final String value;

    Timeframe(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
