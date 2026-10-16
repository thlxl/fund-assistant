package cn.local.fund;

import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;
import android.content.Context;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.Collections;

public class LedgerTest extends AndroidTestCase {
    private Store store;
    private Context isolated;
    @Override protected void setUp() throws Exception {super.setUp();isolated=new RenamingDelegatingContext(getContext(),"test_");store=new Store(isolated);store.addFund("110020","测试宽基","股票","宽基","100","200","2026-09-30");}
    @Override protected void tearDown() throws Exception {store.close();isolated.deleteDatabase("fund-ledger.db");super.tearDown();}
    public void testGeneratedTradesAreUniqueAndNeverAutomaticallyConfirmed() {
        store.addPlan("110020","100","0.12","2026-10-08");store.generate();int count=store.rows("SELECT * FROM trades").size();assertTrue(count>0);store.generate();assertEquals(count,store.rows("SELECT * FROM trades").size());
        assertEquals(0,store.rows("SELECT * FROM trades WHERE status='CONFIRMED'").size());
        assertEquals(0,store.rows("SELECT * FROM trades WHERE date='2026-10-10'").size());
    }
    public void testQuoteEstimatesDoNotChangeConfirmedShares() throws Exception {
        store.addPlan("110020","100","0.12","2026-10-08");JSONObject nav=new JSONObject();nav.put("date","2026-10-08");nav.put("nav","2");nav.put("change","0");store.cacheNav("110020","测试宽基",Collections.singletonList(nav));store.generate();
        assertEquals("49.94",Store.s(store.rows("SELECT * FROM trades WHERE date='2026-10-08'").get(0),"shares"));
        assertEquals(0,store.position("110020").shares.compareTo(new BigDecimal("100")));
        assertEquals(0,store.position("110020").estimated.compareTo(new BigDecimal("49.94")));
    }
    public void testInvalidSellRollsBack() {
        try {store.saveTrade(null,"110020","2026-10-08","SELL","500","101","0","CONFIRMED","");fail("should reject");}catch(IllegalArgumentException expected){}
        assertEquals(0,store.rows("SELECT * FROM trades").size());
    }
    public void testEditingOldBuyCannotInvalidateLaterSell() {
        store.saveTrade(null,"110020","2026-10-08","BUY","100","50","0","CONFIRMED","");
        store.saveTrade(null,"110020","2026-10-09","SELL","240","120","0","CONFIRMED","");
        String id=Store.s(store.rows("SELECT * FROM trades WHERE type='BUY'").get(0),"id");
        try {store.saveTrade(id,"110020","2026-10-08","BUY","10","5","0","CONFIRMED","");fail("should reject");}catch(IllegalArgumentException expected){}
        assertEquals("50",Store.s(store.rows("SELECT * FROM trades WHERE type='BUY'").get(0),"shares"));
    }
    public void testBackupRestoresAndBadBackupPreservesExistingLedger() throws Exception {
        String before=store.backup();store.setSetting("reserve","100");store.restore(before);assertEquals("0",store.setting("reserve","0"));
        JSONObject bad=new JSONObject(before);bad.getJSONArray("funds").getJSONObject(0).put("shares","-20");
        try {store.restore(bad.toString());fail("should reject");}catch(IllegalArgumentException expected){}
        assertEquals("100",Store.s(store.fund("110020"),"shares"));
        JSONObject invalidSetting=new JSONObject();invalidSetting.put("key","asset_现金");invalidSetting.put("value","NaN");bad=new JSONObject(before);bad.getJSONArray("settings").put(invalidSetting);
        try{store.restore(bad.toString());fail("should reject");}catch(IllegalArgumentException expected){}
        assertEquals("100",Store.s(store.fund("110020"),"shares"));
    }
    public void testDividendsAndSalesAreAccountedWithoutDoubleDeductingFees() {
        store.saveTrade(null,"110020","2026-10-08","BUY","100","50","1","CONFIRMED","");
        store.saveTrade(null,"110020","2026-10-09","DIVIDEND","10","0","0","CONFIRMED","");
        store.saveTrade(null,"110020","2026-10-12","REINVEST","0","5","0","CONFIRMED","");
        Store.Position p=store.position("110020");assertEquals(0,p.shares.compareTo(new BigDecimal("155")));assertEquals(0,p.cost.compareTo(new BigDecimal("300")));assertEquals(0,p.netInvested.compareTo(new BigDecimal("290")));
    }
    public void testSnapshotDayIsRejected() {
        try{store.saveTrade(null,"110020","2026-09-30","BUY","100","50","0","CONFIRMED","");fail("should reject");}catch(IllegalArgumentException expected){}
    }
    public void testBatchReconciliationKeepsEstimatesAuditableAndIsIdempotent() throws Exception {
        store.addPlan("110020","100","0","2026-10-08");JSONObject nav=new JSONObject();nav.put("date","2026-10-08");nav.put("nav","2");nav.put("change","0");store.cacheNav("110020","测试宽基",Collections.singletonList(nav));store.generate();
        store.reconcileBatch("110020","2026-10-08","149.99","300");
        assertEquals("RECONCILED",Store.s(store.rows("SELECT * FROM trades WHERE type='BUY' AND date='2026-10-08'").get(0),"status"));
        assertEquals(0,store.positionAt("110020","2026-10-08").shares.compareTo(new BigDecimal("149.99")));
        assertTrue(store.position("110020").adjusted);int count=store.rows("SELECT * FROM trades").size();
        store.reconcileBatch("110020","2026-10-08","149.99","300");assertEquals(count,store.rows("SELECT * FROM trades").size());
    }
    public void testQuoteParserDoesNotExecuteJavascript() throws Exception {
        String text="var fS_code=\"110020\";var fS_name=\"测试基金\";var Data_netWorthTrend=[{\"x\":1791388800000,\"y\":2,\"equityReturn\":1}];throw new Error('must not execute');";
        Quotes.Data data=Quotes.parse("110020",text);assertEquals("测试基金",data.name);assertEquals(1,data.navs.size());assertEquals("2",Store.s(data.navs.get(0),"nav"));
        try{Quotes.parse("000001",text);fail("should reject");}catch(IllegalArgumentException expected){}
    }
}
