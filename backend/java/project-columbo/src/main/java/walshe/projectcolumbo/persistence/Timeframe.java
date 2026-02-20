package walshe.projectcolumbo.persistence;

public enum Timeframe {
    D1("1D");

    private final String value;

    Timeframe(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
