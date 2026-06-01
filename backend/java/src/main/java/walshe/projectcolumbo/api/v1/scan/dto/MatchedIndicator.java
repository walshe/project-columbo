package walshe.projectcolumbo.api.v1.scan.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.persistence.model.IndicatorType;
import walshe.projectcolumbo.persistence.model.Timeframe;

import java.time.OffsetDateTime;

@Schema(description = "Indicator event or state that matched the scan condition for an asset")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "indicatorType",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SupertrendMatch.class, name = "SUPERTREND"),
    @JsonSubTypes.Type(value = RsiMatch.class, name = "RSI"),
    @JsonSubTypes.Type(value = ElderImpulseMatch.class, name = "ELDER_IMPULSE"),
    @JsonSubTypes.Type(value = ThermometerMatch.class, name = "ELDER_THERMOMETER")
})
public sealed interface MatchedIndicator permits SupertrendMatch, RsiMatch, ElderImpulseMatch, ThermometerMatch {
    IndicatorType indicatorType();
    @Schema(description = "The timeframe this indicator was evaluated on", example = "1W")
    Timeframe timeframe();
    OffsetDateTime closeTime();
}
