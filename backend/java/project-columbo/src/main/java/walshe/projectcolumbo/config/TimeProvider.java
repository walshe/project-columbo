package walshe.projectcolumbo.config;

import java.time.OffsetDateTime;

public interface TimeProvider {
    OffsetDateTime now();
}
