package cn.local.fund;

import org.junit.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class EngineTest {
    @Test public void holidayAndWeekendAreSkipped() {
        assertFalse(Engine.tradingDay(LocalDate.parse("2026-10-01")));
        assertFalse(Engine.tradingDay(LocalDate.parse("2026-10-10")));
        assertTrue(Engine.tradingDay(LocalDate.parse("2026-10-08")));
        assertFalse(Engine.tradingDay(LocalDate.parse("2026-02-23")));
    }
    @Test public void snapshotIsNotCountedTwiceAndEndIsInclusive() {
        var days = Engine.scheduled(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"),
                LocalDate.parse("2026-10-09"), LocalDate.parse("2026-10-16"));
        assertEquals(2, days.size());
        assertEquals(LocalDate.parse("2026-10-08"), days.get(0));
        assertEquals(LocalDate.parse("2026-10-09"), days.get(1));
    }
    @Test public void feeIsInsideInvestmentAndSharesRoundDown() {
        assertEquals("49.94", Engine.estimate("100", "0.12", "2").toPlainString());
        assertEquals("50.00", Engine.estimate("100", "0", "2").toPlainString());
    }
    @Test public void sellingReducesRemainingCostProportionally() {
        assertEquals(0, new BigDecimal("600").compareTo(Engine.costAfterSell(new BigDecimal("100"),new BigDecimal("1000"),new BigDecimal("40"))));
    }
    @Test(expected=IllegalArgumentException.class) public void oversellingIsRejected() {
        Engine.costAfterSell(new BigDecimal("10"),new BigDecimal("100"),new BigDecimal("11"));
    }
    @Test(expected=IllegalArgumentException.class) public void invalidMoneyIsRejected() { Engine.positive("NaN"); }
    @Test(expected=IllegalArgumentException.class) public void negativeSharesAreRejected() { Engine.nonNegative("-1"); }
    @Test public void unknownCalendarIsExplicit() { assertFalse(Engine.calendarKnown(LocalDate.parse("2027-01-01"))); }
    @Test public void confirmedSharePrecisionIsPreserved() { assertEquals("10.1234",Engine.shares(new BigDecimal("10.1234")));assertEquals("10.00",Engine.shares(new BigDecimal("10"))); }
}
