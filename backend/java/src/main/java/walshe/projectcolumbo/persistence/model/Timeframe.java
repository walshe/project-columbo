package walshe.projectcolumbo.persistence.model;

import com.fasterxml.jackson.annotation.JsonCreator;
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

    @JsonCreator
    public static Timeframe fromValue(String value) {
        for (Timeframe timeframe : Timeframe.values()) {
            if (timeframe.value.equalsIgnoreCase(value) || timeframe.name().equalsIgnoreCase(value)) {
                return timeframe;
            }
        }
        throw new IllegalArgumentException("Unknown timeframe: " + value);
    }
}
