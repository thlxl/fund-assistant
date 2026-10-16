package cn.local.fund;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class Engine {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final String[] CATEGORIES = {"股票", "债券", "现金", "黄金", "海外", "其他"};
    private Engine() {}
    public static LocalDate today() { return LocalDate.now(ZONE); }
    public static BigDecimal number(String text) {
        if (text == null || !text.trim().matches("-?\\d{1,12}(\\.\\d{1,8})?"))
            throw new IllegalArgumentException("请输入有效数字，最多 12 位整数、8 位小数");
        return new BigDecimal(text.trim());
    }
    public static BigDecimal nonNegative(String text) {
        BigDecimal n = number(text);
        if (n.signum() < 0) throw new IllegalArgumentException("金额和份额不能为负数");
        return n;
    }
    public static BigDecimal positive(String text) {
        BigDecimal n = nonNegative(text);
        if (n.signum() == 0) throw new IllegalArgumentException("数值必须大于零");
        return n;
    }
    public static String code(String value) {
        if (!value.matches("\\d{6}")) throw new IllegalArgumentException("基金代码必须是 6 位数字");
        return value;
    }
    public static LocalDate date(String text) {
        try { return LocalDate.parse(text); }
        catch (Exception ex) { throw new IllegalArgumentException("日期格式应为 YYYY-MM-DD"); }
    }
    public static String money(BigDecimal n) { return n.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    public static String shares(BigDecimal n) { return n.setScale(Math.max(2,n.stripTrailingZeros().scale()), RoundingMode.HALF_UP).toPlainString(); }
    public static BigDecimal estimate(String amount, String ratePercent, String nav) {
        BigDecimal rate = nonNegative(ratePercent);
        if (rate.compareTo(new BigDecimal("100")) >= 0) throw new IllegalArgumentException("费率应小于 100%");
        return positive(amount).divide(BigDecimal.ONE.add(rate.movePointLeft(2)), 12, RoundingMode.HALF_UP)
                .divide(positive(nav), 2, RoundingMode.DOWN);
    }
    public static boolean calendarKnown(LocalDate d) { return d.getYear() == 2026; }
    public static boolean tradingDay(LocalDate d) {
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) return false;
        // shortcut: only SSE 2026 holidays are bundled; add each published year before relying on its calendar.
        if (calendarKnown(d)) {
            String md = d.toString().substring(5);
            String[][] closed = {{"01-01","01-03"},{"02-15","02-23"},{"04-04","04-06"},
                    {"05-01","05-05"},{"06-19","06-21"},{"09-25","09-27"},{"10-01","10-07"}};
            for (String[] range : closed) if (md.compareTo(range[0]) >= 0 && md.compareTo(range[1]) <= 0) return false;
        }
        return true;
    }
    public static List<LocalDate> scheduled(LocalDate start, LocalDate snapshot, LocalDate end, LocalDate until) {
        LocalDate first = start.isAfter(snapshot) ? start : snapshot.plusDays(1);
        LocalDate last = end != null && end.isBefore(until) ? end : until;
        if (first.plusYears(10).isBefore(last)) throw new IllegalArgumentException("自动补记最多支持 10 年，请分段核对");
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) if (tradingDay(d)) result.add(d);
        return result;
    }
    public static BigDecimal costAfterSell(BigDecimal shares, BigDecimal cost, BigDecimal sold) {
        if (sold.signum() <= 0 || sold.compareTo(shares) > 0) throw new IllegalArgumentException("卖出份额超过当时已确认持仓");
        return cost.multiply(shares.subtract(sold)).divide(shares, 8, RoundingMode.HALF_UP);
    }
}
