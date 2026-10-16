package cn.local.fund;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Store extends SQLiteOpenHelper {
    private static final String[] TABLES = {"funds", "plans", "trades", "navs", "settings"};
    public Store(Context context) { super(context, "fund-ledger.db", null, 1); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE funds(code TEXT PRIMARY KEY,name TEXT NOT NULL,category TEXT NOT NULL,sector TEXT NOT NULL,shares TEXT NOT NULL,cost TEXT NOT NULL,snapshot TEXT NOT NULL)");
        db.execSQL("CREATE TABLE plans(id INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT NOT NULL REFERENCES funds(code),amount TEXT NOT NULL,rate TEXT NOT NULL,start TEXT NOT NULL,end TEXT)");
        db.execSQL("CREATE TABLE trades(id INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT NOT NULL REFERENCES funds(code),date TEXT NOT NULL,type TEXT NOT NULL,amount TEXT NOT NULL,shares TEXT NOT NULL,fee TEXT NOT NULL,status TEXT NOT NULL,plan INTEGER REFERENCES plans(id),note TEXT NOT NULL,UNIQUE(plan,date))");
        db.execSQL("CREATE TABLE navs(code TEXT NOT NULL REFERENCES funds(code),date TEXT NOT NULL,nav TEXT NOT NULL,change TEXT NOT NULL,PRIMARY KEY(code,date))");
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { throw new IllegalStateException("不支持的数据库版本，请先备份"); }
    public synchronized List<JSONObject> rows(String sql, String... args) {
        List<JSONObject> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql,args)) {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                for(int i=0;i<c.getColumnCount();i++) put(row,c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getString(i));
                result.add(row);
            }
        }
        return result;
    }
    public static void put(JSONObject o,String k,Object v) { try { o.put(k,v); } catch(Exception e) { throw new IllegalArgumentException(e); } }
    public static String s(JSONObject o,String k) { return o.optString(k,""); }
    public static BigDecimal n(JSONObject o,String k) { return Engine.number(s(o,k)); }
    public synchronized List<JSONObject> funds() { return rows("SELECT * FROM funds ORDER BY code"); }
    public synchronized JSONObject fund(String code) { return rows("SELECT * FROM funds WHERE code=?",code).get(0); }
    public synchronized JSONObject latest(String code) {
        var result=rows("SELECT * FROM navs WHERE code=? ORDER BY date DESC LIMIT 1",code);
        return result.isEmpty()?null:result.get(0);
    }
    public synchronized String setting(String key,String fallback) {
        var r=rows("SELECT value FROM settings WHERE key=?",key);
        return r.isEmpty()?fallback:s(r.get(0),"value");
    }
    public synchronized void setSetting(String key,String value) {
        ContentValues v=new ContentValues(); v.put("key",key);v.put("value",value);
        getWritableDatabase().insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized void addFund(String code,String name,String category,String sector,String shares,String cost,String date) {
        Engine.code(code); Engine.nonNegative(shares);Engine.nonNegative(cost); LocalDate d=Engine.date(date);
        if(d.isAfter(Engine.today())) throw new IllegalArgumentException("起始持仓日期不能在未来");
        if(name.trim().isEmpty()) name=code;
        ContentValues v=new ContentValues();v.put("code",code);v.put("name",name);v.put("category",category);v.put("sector",sector);
        v.put("shares",shares);v.put("cost",cost);v.put("snapshot",date);
        getWritableDatabase().insertOrThrow("funds",null,v);
    }
    public synchronized void classify(String code,String category,String sector) {
        ContentValues v=new ContentValues();v.put("category",category);v.put("sector",sector);
        getWritableDatabase().update("funds",v,"code=?",new String[]{code});
    }
    public synchronized void addPlan(String code,String amount,String rate,String start) {
        Engine.positive(amount);Engine.estimate(amount,rate,"1"); LocalDate d=Engine.date(start);
        if(d.isBefore(Engine.date(s(fund(code),"snapshot")))) throw new IllegalArgumentException("定投起点不能早于起始持仓日期");
        if(!rows("SELECT id FROM plans WHERE code=? AND (end IS NULL OR end>=?)",code,start).isEmpty())
            throw new IllegalArgumentException("该基金已有重叠的定投计划，请先结束旧计划");
        ContentValues v=new ContentValues();v.put("code",code);v.put("amount",amount);v.put("rate",rate);v.put("start",start);
        getWritableDatabase().insertOrThrow("plans",null,v); generate();
    }
    public synchronized void endPlan(String id) {
        ContentValues v=new ContentValues();v.put("end",Engine.today().toString());
        getWritableDatabase().update("plans",v,"id=?",new String[]{id});
    }
    public synchronized void generate() {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(JSONObject p:rows("SELECT p.*,f.snapshot FROM plans p JOIN funds f ON f.code=p.code")) {
                String end=s(p,"end");
                for(LocalDate d:Engine.scheduled(Engine.date(s(p,"start")),Engine.date(s(p,"snapshot")),
                        end.isEmpty()||end.equals("null")?null:Engine.date(end),Engine.today())) {
                    ContentValues v=new ContentValues();v.put("code",s(p,"code"));v.put("date",d.toString());v.put("type","BUY");
                    v.put("amount",s(p,"amount"));v.put("shares","0");v.put("fee","0");v.put("status","PLANNED");v.put("plan",s(p,"id"));
                    v.put("note",Engine.calendarKnown(d)?"定投计划；基金暂停申购或境外假期需核对":"日历未覆盖该年份，交易日需核对");
                    db.insertWithOnConflict("trades",null,v,SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            for(JSONObject t:rows("SELECT t.*,p.rate,n.nav FROM trades t JOIN plans p ON t.plan=p.id JOIN navs n ON n.code=t.code AND n.date=t.date WHERE t.status IN ('PLANNED','ESTIMATED')")) {
                BigDecimal estimate=Engine.estimate(s(t,"amount"),s(t,"rate"),s(t,"nav"));
                ContentValues v=new ContentValues();v.put("shares",estimate.toPlainString());v.put("status","ESTIMATED");
                v.put("fee",Engine.money(n(t,"amount").subtract(n(t,"amount").divide(BigDecimal.ONE.add(n(t,"rate").movePointLeft(2)),8,java.math.RoundingMode.HALF_UP))));
                db.update("trades",v,"id=?",new String[]{s(t,"id")});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public static final class Position {
        public BigDecimal shares=BigDecimal.ZERO,cost=BigDecimal.ZERO,estimated=BigDecimal.ZERO,netInvested=BigDecimal.ZERO;
        public boolean adjusted=false;
    }
    public synchronized Position position(String code) {
        return positionAt(code,null);
    }
    public synchronized Position positionAt(String code,String through) {
        JSONObject f=fund(code);Position p=new Position();p.shares=n(f,"shares");p.cost=n(f,"cost");p.netInvested=p.cost;
        List<JSONObject> trades=through==null?rows("SELECT * FROM trades WHERE code=? ORDER BY date,id",code):rows("SELECT * FROM trades WHERE code=? AND date<=? ORDER BY date,id",code,through);
        for(JSONObject t:trades) {
            String status=s(t,"status"),type=s(t,"type");BigDecimal sh=n(t,"shares"),amount=n(t,"amount");
            if(status.equals("ESTIMATED")) { p.estimated=p.estimated.add(sh);continue; }
            if(!status.equals("CONFIRMED")&&!status.equals("RECONCILED")) continue;
            if(status.equals("RECONCILED"))p.adjusted=true;
            switch(type) {
                case "BUY": p.shares=p.shares.add(sh);p.cost=p.cost.add(amount);p.netInvested=p.netInvested.add(amount);break;
                case "SELL": p.cost=Engine.costAfterSell(p.shares,p.cost,sh);p.shares=p.shares.subtract(sh);p.netInvested=p.netInvested.subtract(amount);break;
                case "DIVIDEND": p.netInvested=p.netInvested.subtract(amount);break;
                case "REINVEST": p.shares=p.shares.add(sh);break;
                case "ADJUST": p.shares=p.shares.add(sh);p.cost=p.cost.add(amount);p.adjusted=true;break;
                default: throw new IllegalArgumentException("未知交易类型");
            }
            if(p.shares.signum()<0||p.cost.signum()<0) throw new IllegalArgumentException("调整后份额或成本不能为负数");
        }
        return p;
    }
    public synchronized void reconcileBatch(String code,String through,String actualShares,String actualCost) {
        Engine.nonNegative(actualShares);Engine.nonNegative(actualCost);
        if(!Engine.date(through).isAfter(Engine.date(s(fund(code),"snapshot")))||Engine.date(through).isAfter(Engine.today()))throw new IllegalArgumentException("核对日期应在起始日期之后，且不能在未来");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(!rows("SELECT id FROM trades WHERE code=? AND date<=? AND status='PLANNED'",code,through).isEmpty())throw new IllegalArgumentException("该日期之前还有无净值的计划，请更新行情或将未发生的定投标为跳过");
            db.execSQL("UPDATE trades SET status='RECONCILED',note=note||'；汇总核对，逐笔份额仍为估算' WHERE code=? AND date<=? AND status='ESTIMATED'",new Object[]{code,through});
            Position p=positionAt(code,through);BigDecimal deltaShares=Engine.nonNegative(actualShares).subtract(p.shares),deltaCost=Engine.nonNegative(actualCost).subtract(p.cost);
            if(deltaShares.signum()!=0||deltaCost.signum()!=0)saveTrade(null,code,through,"ADJUST",deltaCost.toPlainString(),deltaShares.toPlainString(),"0","CONFIRMED","按支付宝汇总份额及成本核对；逐笔成交未核实");
            position(code);db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public synchronized void saveTrade(String id,String code,String date,String type,String amount,String shares,String fee,String status,String note) {
        if(!Arrays.asList("BUY","SELL","DIVIDEND","REINVEST","ADJUST").contains(type)||!Arrays.asList("PLANNED","ESTIMATED","CONFIRMED","SKIPPED").contains(status))throw new IllegalArgumentException("未知交易类型或状态");
        Engine.date(date);Engine.number(amount);Engine.number(shares);Engine.nonNegative(fee);
        if(!Engine.date(date).isAfter(Engine.date(s(fund(code),"snapshot")))) throw new IllegalArgumentException("交易必须在起始持仓日期之后，起始日交易已包含在快照中");
        if(Engine.date(date).isAfter(Engine.today())) throw new IllegalArgumentException("实际交易日期不能在未来");
        if(!type.equals("ADJUST")) { Engine.nonNegative(amount);Engine.nonNegative(shares); }
        if(status.equals("CONFIRMED")) {
            if(Arrays.asList("BUY","SELL","REINVEST").contains(type)) Engine.positive(shares);
            if(type.equals("BUY")||type.equals("DIVIDEND")) Engine.positive(amount);
            if(type.equals("REINVEST")&&Engine.number(amount).signum()!=0) throw new IllegalArgumentException("红利再投不增加外部投入，金额填 0");
            if(type.equals("DIVIDEND")&&Engine.number(shares).signum()!=0) throw new IllegalArgumentException("现金分红不改变份额，份额填 0");
            if(!type.equals("ADJUST")&&Engine.nonNegative(fee).compareTo(Engine.nonNegative(amount))>0) throw new IllegalArgumentException("费用不能超过交易金额");
        }
        ContentValues v=new ContentValues();v.put("code",code);v.put("date",date);v.put("type",type);v.put("amount",amount);v.put("shares",shares);v.put("fee",fee);v.put("status",status);v.put("note",note);
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            if(id==null) db.insertOrThrow("trades",null,v); else db.update("trades",v,"id=?",new String[]{id});
            position(code);db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public synchronized void cacheNav(String code,String name,List<JSONObject> navs) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(JSONObject n:navs) {
                ContentValues v=new ContentValues();v.put("code",code);v.put("date",s(n,"date"));v.put("nav",s(n,"nav"));v.put("change",s(n,"change"));
                db.insertWithOnConflict("navs",null,v,SQLiteDatabase.CONFLICT_REPLACE);
                db.delete("settings","key=?",new String[]{"manual_"+code+"_"+s(n,"date")});
            }
            ContentValues v=new ContentValues();v.put("name",name);db.update("funds",v,"code=?",new String[]{code});
            setSetting("updated_"+code,java.time.ZonedDateTime.now(Engine.ZONE).toString());setSetting("error_"+code,"");
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public synchronized String backup() {
        JSONObject root=new JSONObject();put(root,"version",1);put(root,"created",java.time.ZonedDateTime.now(Engine.ZONE).toString());
        for(String table:TABLES) put(root,table,new JSONArray(rows("SELECT * FROM "+table)));
        return root.toString();
    }
    public synchronized void restore(String text) throws Exception {
        JSONObject root=new JSONObject(text);
        if(root.getInt("version")!=1) throw new IllegalArgumentException("不支持的备份版本");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("trades",null,null);db.delete("plans",null,null);db.delete("navs",null,null);db.delete("funds",null,null);db.delete("settings",null,null);
            for(String table:TABLES) {
                JSONArray a=root.getJSONArray(table);
                if(a.length()>100000) throw new IllegalArgumentException("备份记录过多");
                Set<String> columns=new HashSet<>();try(Cursor c=db.rawQuery("SELECT * FROM "+table+" LIMIT 0",null)){columns.addAll(Arrays.asList(c.getColumnNames()));}
                for(int i=0;i<a.length();i++) {
                    JSONObject row=a.getJSONObject(i);
                    if(row.length()!=columns.size()) throw new IllegalArgumentException("备份字段不完整："+table);
                    ContentValues v=new ContentValues();
                    for(String col:columns) { if(!row.has(col)) throw new IllegalArgumentException("备份缺少字段："+col);if(row.isNull(col))v.putNull(col);else v.put(col,row.getString(col)); }
                    validateRow(table,row);
                    db.insertOrThrow(table,null,v);
                }
            }
            for(JSONObject f:funds()) position(s(f,"code"));
            for(JSONObject t:rows("SELECT t.date,f.snapshot FROM trades t JOIN funds f ON t.code=f.code"))
                if(!Engine.date(s(t,"date")).isAfter(Engine.date(s(t,"snapshot")))) throw new IllegalArgumentException("备份交易与起始持仓重复");
            for(JSONObject t:rows("SELECT t.code,p.code AS plan_code FROM trades t JOIN plans p ON t.plan=p.id"))
                if(!s(t,"code").equals(s(t,"plan_code"))) throw new IllegalArgumentException("备份定投基金不匹配");
            for(JSONObject p:rows("SELECT a.id FROM plans a JOIN plans b ON a.code=b.code AND a.id<b.id WHERE a.start<=COALESCE(a.end,'9999-12-31') AND b.start<=COALESCE(b.end,'9999-12-31') AND a.start<=COALESCE(b.end,'9999-12-31') AND b.start<=COALESCE(a.end,'9999-12-31')"))
                throw new IllegalArgumentException("备份含重叠的定投计划");
            BigDecimal targetSum=BigDecimal.ZERO;
            for(String c:Engine.CATEGORIES)targetSum=targetSum.add(Engine.nonNegative(setting("target_"+c,"0")));
            if(targetSum.signum()!=0&&targetSum.compareTo(new BigDecimal("100"))!=0)throw new IllegalArgumentException("备份目标比例总和必须为 100%");
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    private void validateRow(String table,JSONObject r) {
        if(r.has("code")) Engine.code(s(r,"code"));
        switch(table) {
            case "funds": Engine.nonNegative(s(r,"shares"));Engine.nonNegative(s(r,"cost"));Engine.date(s(r,"snapshot"));
                if(Engine.date(s(r,"snapshot")).isAfter(Engine.today()))throw new IllegalArgumentException("起始持仓日期不能在未来");
                if(!Arrays.asList(Engine.CATEGORIES).contains(s(r,"category"))) throw new IllegalArgumentException("未知资产类别");break;
            case "plans": Engine.positive(s(r,"amount"));Engine.estimate(s(r,"amount"),s(r,"rate"),"1");Engine.date(s(r,"start"));if(!r.isNull("end"))Engine.date(s(r,"end"));break;
            case "navs": Engine.date(s(r,"date"));Engine.positive(s(r,"nav"));Engine.number(s(r,"change"));if(Engine.date(s(r,"date")).isAfter(Engine.today()))throw new IllegalArgumentException("净值日期不能在未来");break;
            case "trades":
                Engine.date(s(r,"date"));Engine.number(s(r,"amount"));Engine.number(s(r,"shares"));Engine.nonNegative(s(r,"fee"));
                if(!Arrays.asList("BUY","SELL","DIVIDEND","REINVEST","ADJUST").contains(s(r,"type"))||!Arrays.asList("PLANNED","ESTIMATED","CONFIRMED","RECONCILED","SKIPPED").contains(s(r,"status")))throw new IllegalArgumentException("未知交易类型或状态");
                if(!s(r,"type").equals("ADJUST")){Engine.nonNegative(s(r,"amount"));Engine.nonNegative(s(r,"shares"));}
                if(Arrays.asList("ESTIMATED","RECONCILED").contains(s(r,"status"))&&(!s(r,"type").equals("BUY")||r.isNull("plan")))throw new IllegalArgumentException("预计或汇总成交必须关联买入定投计划");
                if(s(r,"status").equals("CONFIRMED")&&Arrays.asList("BUY","SELL","REINVEST").contains(s(r,"type")))Engine.positive(s(r,"shares"));
                if(s(r,"status").equals("CONFIRMED")) {
                    if(s(r,"type").equals("BUY")||s(r,"type").equals("DIVIDEND"))Engine.positive(s(r,"amount"));
                    if(s(r,"type").equals("REINVEST")&&n(r,"amount").signum()!=0)throw new IllegalArgumentException("红利再投金额必须为 0");
                    if(s(r,"type").equals("DIVIDEND")&&n(r,"shares").signum()!=0)throw new IllegalArgumentException("现金分红份额必须为 0");
                }
                if(Engine.date(s(r,"date")).isAfter(Engine.today()))throw new IllegalArgumentException("交易日期不能在未来");
                break;
            case "settings":
                String key=s(r,"key");
                if(key.startsWith("asset_")||key.startsWith("target_")||Arrays.asList("reserve","liability","monthly","drawdown").contains(key))Engine.nonNegative(s(r,"value"));
                if(key.equals("drawdown")&&n(r,"value").compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("最大亏损比例超过 100%");
                break;
        }
    }
}
