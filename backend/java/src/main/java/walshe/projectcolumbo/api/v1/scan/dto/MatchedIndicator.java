package walshe.projectcolumbo.api.v1.scan.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import walshe.projectcolumbo.persistence.model.IndicatorType;

import java.time.OffsetDateTime;

@Schema(
    description = "Indicator event or state that matched the scan condition for an asset",
    discriminatorProperty = "indicatorType",
    oneOf = {SupertrendMatch.class, RsiMatch.class}
)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "indicatorType",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SupertrendMatch.class, name = "SUPERTREND"),
    @JsonSubTypes.Type(value = RsiMatch.class, name = "RSI")
})
public sealed interface MatchedIndicator permits SupertrendMatch, RsiMatch {
    IndicatorType indicatorType();
    OffsetDateTime closeTime();
}
