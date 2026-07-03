package walshe.projectcolumbo.persistence.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Candle timeframe. Serialized as its display value: D1 = \"1D\" (daily), W1 = \"1W\" (weekly). "
        + "Both the display value and the enum name are accepted on input.")
public enum Timeframe {
    D1("1D"), W1("1W");

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
