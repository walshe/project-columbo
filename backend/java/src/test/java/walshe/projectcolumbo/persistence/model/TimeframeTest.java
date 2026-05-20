package walshe.projectcolumbo.persistence.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimeframeTest {

    @Test
    void w1_isValidEnumConstant() {
        assertNotNull(Timeframe.W1);
    }

    @Test
    void w1_valueIs1W() {
        assertEquals("1W", Timeframe.W1.getValue());
    }

    @Test
    void fromValue_1W_returnsW1() {
        assertEquals(Timeframe.W1, Timeframe.fromValue("1W"));
    }

    @Test
    void fromValue_W1ByName_returnsW1() {
        assertEquals(Timeframe.W1, Timeframe.fromValue("W1"));
    }

    @Test
    void fromValue_1D_returnsD1_noRegression() {
        assertEquals(Timeframe.D1, Timeframe.fromValue("1D"));
    }

    @Test
    void fromValue_D1ByName_returnsD1_noRegression() {
        assertEquals(Timeframe.D1, Timeframe.fromValue("D1"));
    }

    @Test
    void values_hasTwoConstants_D1AndW1() {
        Timeframe[] values = Timeframe.values();
        assertEquals(2, values.length);
        assertSame(Timeframe.D1, values[0]);
        assertSame(Timeframe.W1, values[1]);
    }
}
