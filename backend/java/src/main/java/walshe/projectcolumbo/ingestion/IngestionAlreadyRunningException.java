package walshe.projectcolumbo.ingestion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class IngestionAlreadyRunningException extends RuntimeException {
    public IngestionAlreadyRunningException(String message) {
        super(message);
    }
}
