package walshe.projectcolumbo.config;

import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
class UtcTimeProvider implements TimeProvider {
    @Override
    public OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
