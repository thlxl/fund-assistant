package cn.local.fund;

import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Analysis {
    public final Map<String,BigDecimal> allocation=new LinkedHashMap<>();
    public final Map<String,BigDecimal> targets=new LinkedHashMap<>();
    public final List<String> advice=new ArrayList<>();
    public BigDecimal total=BigDecimal.ZERO, fundValue=BigDecimal.ZERO,pnl=BigDecimal.ZERO,cost=BigDecimal.ZERO,estimatedValue=BigDecimal.ZERO,liability=BigDecimal.ZERO;
    public int missing=0,pending=0,stale=0;
    public boolean adjusted=false;
    public Analysis(Store store) {
        for(String c:Engine.CATEGORIES) {allocation.put(c,BigDecimal.ZERO);targets.put(c,Engine.nonNegative(store.setting("target_"+c,"0")));}
        Map<String,BigDecimal> sectors=new LinkedHashMap<>();
        for(JSONObject f:store.funds()) {
            String code=Store.s(f,"code");Store.Position p=store.position(code);JSONObject nav=store.latest(code);adjusted|=p.adjusted;
            if(nav==null) {missing++;continue;}
            if(Engine.date(Store.s(nav,"date")).isBefore(Engine.today().minusDays(4)))stale++;
            BigDecimal value=p.shares.multiply(Store.n(nav,"nav"));
            fundValue=fundValue.add(value);cost=cost.add(p.cost);pnl=pnl.add(value.subtract(p.cost));estimatedValue=estimatedValue.add(p.estimated.multiply(Store.n(nav,"nav")));
            String category=Store.s(f,"category");allocation.put(category,allocation.getOrDefault(category,BigDecimal.ZERO).add(value));
            String sector=Store.s(f,"sector");if(!sector.trim().isEmpty())sectors.put(sector,sectors.getOrDefault(sector,BigDecimal.ZERO).add(value));
        }
        for(String c:Engine.CATEGORIES)allocation.put(c,allocation.get(c).add(Engine.nonNegative(store.setting("asset_"+c,"0"))));
        for(BigDecimal v:allocation.values())total=total.add(v);
        liability=Engine.nonNegative(store.setting("liability","0"));
        pending=Integer.parseInt(Store.s(store.rows("SELECT COUNT(*) AS count FROM trades WHERE status IN ('PLANNED','ESTIMATED')").get(0),"count"));
        if(missing>0)advice.add(missing+" 只基金缺少净值，资产和比例不完整；先更新行情或手动补净值。");
        if(stale>0)advice.add(stale+" 只基金净值距今超过 4 天，请检查发布日期和数据源。");
        if(pending>0)advice.add(pending+" 笔记录待核对。配置比例仅按已确认份额计算，预计定投单独展示。");
        BigDecimal targetSum=BigDecimal.ZERO;for(BigDecimal n:targets.values())targetSum=targetSum.add(n);
        if(total.signum()>0&&targetSum.compareTo(new BigDecimal("100"))==0&&missing==0) {
            for(String c:Engine.CATEGORIES) {
                BigDecimal delta=allocation.get(c).subtract(total.multiply(targets.get(c)).movePointLeft(2));
                BigDecimal deviation=delta.abs().divide(total,8,RoundingMode.HALF_UP);
                if(deviation.compareTo(new BigDecimal("0.05"))>=0)advice.add(c+"资产"+(delta.signum()>0?"超出":"低于")+"目标约 ¥"+Engine.money(delta.abs())+"。"+(delta.signum()>0?"优先把新增资金投向配置不足的类别，再评估是否减仓。":"可评估将新增资金或定投分配到该类别。"));
            }
        } else if(targetSum.signum()==0) advice.add("尚未设置目标配置。请先按投资期限和风险承受能力填写各类目标比例，总和为 100%。");
        if(total.signum()>0)for(var entry:sectors.entrySet())if(entry.getValue().divide(total,8,RoundingMode.HALF_UP).compareTo(new BigDecimal("0.30"))>0)
            advice.add("你标记的「"+entry.getKey()+"」占资产超过 30%，触发集中度检查提醒；这不是适合所有人的卖出阈值。");
        BigDecimal reserve=Engine.nonNegative(store.setting("reserve","0"));
        if(allocation.get("现金").compareTo(reserve)<0)advice.add("现金资产低于你设置的应急资金目标，缺口 ¥"+Engine.money(reserve.subtract(allocation.get("现金")))+"。先评估是否需要降低定投金额。");
        BigDecimal monthly=Engine.nonNegative(store.setting("monthly","0")),needed=BigDecimal.ZERO;
        for(JSONObject p:store.rows("SELECT * FROM plans")) {
            java.time.LocalDate start=Engine.date(Store.s(p,"start"));if(start.isBefore(Engine.today().plusDays(1)))start=Engine.today().plusDays(1);
            String end=Store.s(p,"end");int days=Engine.scheduled(start,Engine.today(),end.isEmpty()||end.equals("null")?null:Engine.date(end),Engine.today().plusDays(30)).size();
            needed=needed.add(Store.n(p,"amount").multiply(BigDecimal.valueOf(days)));
        }
        if(monthly.signum()>0&&needed.compareTo(monthly)>0)advice.add("未来 30 天计划定投约 ¥"+Engine.money(needed)+"，超过你设置的月投入预算 ¥"+Engine.money(monthly)+"，请检查资金来源。");
        if(!Engine.calendarKnown(Engine.today()))advice.add("交易日历未覆盖今年，定投记录仅按工作日生成，需核对假期。");
        if(advice.isEmpty())advice.add("当前未触发配置规则提醒。继续核对交易和观察配置变化，不据此保证基金适合买入或卖出。");
    }
    public String percent(BigDecimal value) {return total.signum()==0?"0.0%":value.multiply(new BigDecimal("100")).divide(total,1,RoundingMode.HALF_UP)+"%";}
}
