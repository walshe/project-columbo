package walshe.projectcolumbo.supertrend.persistence;

/** Unchecked wrapper for {@link java.sql.SQLException}, so DAO method signatures stay clean. */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
