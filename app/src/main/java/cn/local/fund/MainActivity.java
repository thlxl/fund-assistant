package cn.local.fund;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int GREEN=Color.rgb(17,123,105),INK=Color.rgb(27,46,40),MUTED=Color.rgb(106,123,115),BG=Color.rgb(244,247,245),RED=Color.rgb(188,79,59);
    private Store store;
    private LinearLayout root,body;
    private int screen=0,tradeLimit=100;
    private String detailCode=null;
    private boolean showingTrades=false;
    private String tradeCode=null;
    private boolean refreshing=false,pendingOnly=true;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private String exportPayload;
    private static final String[] TABS={"首页","持仓","定投","分析","我的"};
    private static final String[] TYPES={"BUY","SELL","DIVIDEND","REINVEST","ADJUST"};
    private static final String[] TYPE_NAMES={"买入","卖出","现金分红","红利再投","持仓调整"};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new Store(this);
        if(state!=null)screen=state.getInt("screen",0);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);RefreshJob.schedule(this);render();
    }
    @Override protected void onResume(){
        super.onResume();store.generate();render();
        boolean needs=false;for(JSONObject f:store.funds()) {
            String updated=store.setting("updated_"+Store.s(f,"code"),"");
            try {if(java.time.ZonedDateTime.parse(updated).plusHours(4).isBefore(java.time.ZonedDateTime.now(Engine.ZONE)))needs=true;}
            catch(Exception e){needs=true;}
        }
        if(needs)refresh();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("screen",screen);}
    @Override protected void onDestroy(){super.onDestroy();io.shutdown();store.close();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLineSpacing(dp(3),1);return t;}
    private void label(LinearLayout parent,String value,int size,int color,boolean bold){TextView t=text(value,size,color,bold);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);parent.addView(t,p);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(14));c.setBackground(shape(Color.WHITE,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);body.addView(c,p);return c;}
    private Button button(String title,Runnable action,boolean primary){Button b=new Button(this);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:GREEN);b.setBackgroundTintList(ColorStateList.valueOf(primary?GREEN:Color.rgb(232,243,237)));b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());return b;}
    private void action(LinearLayout parent,String title,Runnable callback){parent.addView(button(title,callback,false),new LinearLayout.LayoutParams(-1,-2));}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void open(int tab){screen=tab;detailCode=null;showingTrades=false;render();}
    private void render(){
        if(isFinishing()||isDestroyed())return;
        root.removeAllViews();
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(20),dp(14),dp(20),dp(10));
        TextView brand=text("基金手账",23,INK,true);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView local=text("● 本机保存",12,GREEN,true);header.addView(local);root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(dp(20),dp(8),dp(20),dp(16));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(showingTrades)showTrades(tradeCode);
        else if(detailCode!=null)fundDetail(detailCode);
        else switch(screen){case 0:overview();break;case 1:holdings();break;case 2:plans();break;case 3:analysis();break;default:settings();}
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(6),dp(8),dp(6),dp(8));nav.setBackgroundColor(Color.WHITE);
        for(int i=0;i<TABS.length;i++){final int tab=i;TextView t=text(TABS[i],14,screen==i?GREEN:MUTED,screen==i);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));if(screen==i)t.setBackground(shape(Color.rgb(232,243,237),14));t.setOnClickListener(v->open(tab));nav.addView(t,new LinearLayout.LayoutParams(0,-2,1));}
        root.addView(nav);
    }
    private void title(String heading,String subtitle){label(body,heading,27,INK,true);label(body,subtitle,13,MUTED,false);}
    private void overview(){
        title("让每一笔投入有据可查",Engine.today()+" · 先看配置，再做决定");
        Analysis a=new Analysis(store);LinearLayout hero=card();hero.setBackground(shape(INK,22));
        label(hero,"已计价资产 · 已确认持仓",13,Color.rgb(178,204,190),false);label(hero,"¥ "+Engine.money(a.total),36,Color.WHITE,true);
        label(hero,"基金市值  ¥"+Engine.money(a.fundValue)+"    持仓盈亏  ¥"+Engine.money(a.pnl),14,Color.WHITE,false);
        label(hero,"负债 ¥"+Engine.money(a.liability)+" · 净资产 ¥"+Engine.money(a.total.subtract(a.liability)),13,Color.rgb(178,204,190),false);
        if(a.missing>0)label(hero,a.missing+" 只基金未计价，总额不完整",13,Color.rgb(255,210,155),true);
        if(a.estimatedValue.signum()>0)label(hero,"另有预计定投市值 ¥"+Engine.money(a.estimatedValue)+"，尚未计入资产",12,Color.rgb(178,204,190),false);
        LinearLayout quick=card();label(quick,"今天的账本",18,INK,true);
        label(quick,store.funds().size()+" 只基金  ·  "+a.pending+" 笔待核对",14,MUTED,false);
        action(quick,refreshing?"正在更新行情…":"更新基金净值",this::refresh);action(quick,"核对交易与定投",this::tradeList);
        if(store.funds().isEmpty()) {LinearLayout c=card();label(c,"从你的第一只基金开始",20,INK,true);label(c,"按支付宝的已确认份额和持仓成本建立起点。之后只记新的交易，定投自动补记。",14,MUTED,false);action(c,"＋ 添加基金",this::addFund);}
        LinearLayout c=card();label(c,"资产分布",18,INK,true);allocation(c,a);
        LinearLayout n=card();label(n,"值得留意",18,INK,true);for(String tip:a.advice)label(n,"• "+tip,14,MUTED,false);
        label(body,"持仓金额留在本机；行情请求仅携带基金代码。",12,MUTED,false);
    }
    private void allocation(LinearLayout c,Analysis a){
        for(String category:Engine.CATEGORIES){BigDecimal value=a.allocation.get(category);if(value.signum()==0)continue;
            label(c,category+"  "+a.percent(value)+"     ¥"+Engine.money(value),14,INK,true);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress(a.total.signum()==0?0:value.multiply(new BigDecimal("1000")).divide(a.total,0,RoundingMode.HALF_UP).intValue());bar.setProgressTintList(ColorStateList.valueOf(GREEN));c.addView(bar,new LinearLayout.LayoutParams(-1,dp(10)));
        }
        if(a.total.signum()==0)label(c,"暂无可计价资产。",14,MUTED,false);
    }
    private void holdings(){
        title("我的基金","净值日期与账户确认日期分开展示");body.addView(button("＋ 添加基金",this::addFund,true));
        for(JSONObject f:store.funds()) {
            String code=Store.s(f,"code");Store.Position p=store.position(code);JSONObject nav=store.latest(code);LinearLayout c=card();
            label(c,Store.s(f,"name"),18,INK,true);label(c,code+" · "+Store.s(f,"category")+(Store.s(f,"sector").trim().isEmpty()?"":" · "+Store.s(f,"sector")),12,MUTED,false);
            label(c,nav==null?"暂无净值":"¥ "+Engine.money(p.shares.multiply(Store.n(nav,"nav"))),26,INK,true);
            label(c,"确认份额 "+Engine.shares(p.shares)+" · 成本 ¥"+Engine.money(p.cost),13,MUTED,false);
            if(nav!=null)label(c,"净值 "+Store.s(nav,"nav")+"  /  "+Store.s(nav,"date"),12,MUTED,false);
            if(!store.setting("error_"+code,"").trim().isEmpty())label(c,"行情更新失败，保留上次数据",12,RED,false);
            action(c,"查看详情",()->{detailCode=code;render();});
        }
        if(store.funds().isEmpty())label(body,"尚未添加基金。基金代码保留前导零，例如 000001。",14,MUTED,false);
    }
    private void fundDetail(String code){
        JSONObject f=store.fund(code);Store.Position p=store.position(code);JSONObject nav=store.latest(code);
        action(body,"‹ 返回持仓",()->open(1));title(Store.s(f,"name"),code+" · 起始持仓 "+Store.s(f,"snapshot"));
        LinearLayout c=card();label(c,"确认份额  "+Engine.shares(p.shares),23,INK,true);label(c,"剩余成本 ¥"+Engine.money(p.cost)+" · 预计新增 "+Engine.shares(p.estimated)+" 份",14,MUTED,false);
        if(nav!=null) {
            BigDecimal value=p.shares.multiply(Store.n(nav,"nav"));label(c,"市值 ¥"+Engine.money(value)+" · 持仓盈亏 ¥"+Engine.money(value.subtract(p.cost)),16,GREEN,true);
            label(c,"净值 "+Store.s(nav,"nav")+" · "+Store.s(nav,"date"),13,MUTED,false);
            label(c,"来源："+(store.setting("manual_"+code+"_"+Store.s(nav,"date"),"0").equals("1")?"手动录入":"天天基金公开数据，非支付宝账户数据"),12,MUTED,false);
            if(!p.adjusted)label(c,"起始记账以来盈亏 ¥"+Engine.money(value.subtract(p.netInvested))+"（起始金额采用你填写的成本）",12,MUTED,false);
            else label(c,"存在汇总核对或持仓调整，累计收益需核实。",12,RED,false);
        }
        String error=store.setting("error_"+code,"");if(!error.trim().isEmpty())label(c,"更新提示："+error,12,RED,false);
        label(c,"最近数据更新："+store.setting("updated_"+code,"尚未获取"),11,MUTED,false);
        action(c,"记一笔交易",()->tradeForm(null,code));action(c,"设置每日定投",()->planForm(code));action(c,"批量核对定投与总持仓",()->batchReconcile(code));action(c,"单独记录持仓差异",()->reconcile(code));action(c,"修改分类与行业标签",()->classify(code));action(c,"手动补净值",()->manualNav(code));
        List<JSONObject> history=store.rows("SELECT * FROM navs WHERE code=? ORDER BY date DESC LIMIT 180",code);
        if(history.size()>1){LinearLayout chart=card();label(chart,"近 "+history.size()+" 期单位净值",18,INK,true);chart.addView(new NavChart(history),new LinearLayout.LayoutParams(-1,dp(170)));label(chart,"净值曲线不含分红复权，不作为投资总收益曲线。",11,MUTED,false);}
        LinearLayout advice=card();label(advice,"当前怎么处理",18,INK,true);
        Analysis a=new Analysis(store);BigDecimal weight=nav==null?BigDecimal.ZERO:p.shares.multiply(Store.n(nav,"nav"));
        label(advice,nav==null?"数据不足：先补齐净值。":a.missing>0?"组合数据不完整，暂缓配置买卖判断。":"该基金占已计价总资产 "+a.percent(weight)+"。结合下方组合规则判断是否需要调整。",14,INK,false);
        for(String tip:a.advice)label(advice,tip,13,MUTED,false);
        label(advice,"首版按配置规则提供参考，尚未接入指数估值、经理变更和底层持仓重合分析。",12,MUTED,false);
        action(body,"查看该基金交易",()->showTrades(code));
    }
    private void plans(){
        title("定投，交给计划","自动补记 · 预计成交单独核对");body.addView(button("＋ 新建每日定投",()->planForm(null),true));
        for(JSONObject p:store.rows("SELECT p.*,f.name FROM plans p JOIN funds f ON p.code=f.code ORDER BY p.id DESC")) {
            LinearLayout c=card();label(c,Store.s(p,"name"),18,INK,true);label(c,"每个交易日 ¥"+Store.s(p,"amount"),25,GREEN,true);
            String end=Store.s(p,"end");boolean ended=!end.isEmpty()&&!end.equals("null");
            label(c,"开始 "+Store.s(p,"start")+" · 申购费率 "+Store.s(p,"rate")+"%",13,MUTED,false);
            label(c,ended?"截至 "+end+"，之后不再生成":"进行中 · 国内休市日跳过，不顺延",13,MUTED,false);
            label(c,"基金停购、限额、境外假期和扣款失败需核对。费率只用于估算。",12,MUTED,false);
            if(!ended) {action(c,"结束计划（今天为最后一天）",()->{store.endPlan(Store.s(p,"id"));render();toast("已结束，可从明天新建计划修改金额或恢复定投");});}
        }
        LinearLayout c=card();label(c,"未来 30 天资金计划",18,INK,true);
        BigDecimal sum=BigDecimal.ZERO;int count=0;
        for(JSONObject p:store.rows("SELECT p.*,f.snapshot FROM plans p JOIN funds f ON p.code=f.code")){
            String end=Store.s(p,"end");LocalDate start=Engine.date(Store.s(p,"start"));if(start.isBefore(Engine.today().plusDays(1)))start=Engine.today().plusDays(1);
            int days=Engine.scheduled(start,Engine.today(),end.isEmpty()||end.equals("null")?null:Engine.date(end),Engine.today().plusDays(30)).size();count+=days;sum=sum.add(Store.n(p,"amount").multiply(BigDecimal.valueOf(days)));
        }
        label(c,"约 "+count+" 笔 · ¥"+Engine.money(sum),23,INK,true);label(c,"按计划与已知国内交易日历估算，不包含收益预测。",12,MUTED,false);
        action(c,"查看自动生成的交易",this::tradeList);
    }
    private void analysis(){
        title("看清组合，再调整","以已确认持仓为基础，所有规则可复核");Analysis a=new Analysis(store);
        LinearLayout c=card();label(c,"当前配置",18,INK,true);allocation(c,a);
        LinearLayout advice=card();label(advice,"配置检查",18,INK,true);for(String tip:a.advice)label(advice,"• "+tip,14,INK,false);
        label(advice,"规则：偏离你设置的目标 ≥5 个百分点时提醒；自填行业标签占比 >30% 时提醒检查。阈值用于发现问题，不直接触发交易。",12,MUTED,false);
        action(advice,"设置目标与资金计划",this::assetSettings);
        LinearLayout projection=card();label(projection,"按当前定投继续 90 天",18,INK,true);
        BigDecimal future=BigDecimal.ZERO;java.util.Map<String,BigDecimal> amounts=new java.util.LinkedHashMap<>(a.allocation);
        for(JSONObject p:store.rows("SELECT p.*,f.category,f.snapshot FROM plans p JOIN funds f ON p.code=f.code")) {
            LocalDate start=Engine.date(Store.s(p,"start"));if(start.isBefore(Engine.today().plusDays(1)))start=Engine.today().plusDays(1);
            String end=Store.s(p,"end");int days=Engine.scheduled(start,Engine.today(),end.isEmpty()||end.equals("null")?null:Engine.date(end),Engine.today().plusDays(90)).size();BigDecimal input=Store.n(p,"amount").multiply(BigDecimal.valueOf(days));future=future.add(input);String cat=Store.s(p,"category");amounts.put(cat,amounts.get(cat).add(input));
        }
        label(projection,"新增外部投入约 ¥"+Engine.money(future),21,GREEN,true);
        BigDecimal projected=a.total.add(future);
        for(String cat:Engine.CATEGORIES)if(projected.signum()>0&&amounts.get(cat).signum()>0)label(projection,cat+" 约 "+amounts.get(cat).multiply(new BigDecimal("100")).divide(projected,1,RoundingMode.HALF_UP)+"%",14,INK,false);
        label(projection,"假设投入来自组合之外、价格不变、暂不扣费用。若使用已录入现金定投，实际是现金转为基金，不会增加总资产。跨未覆盖年份的日历仅按工作日估计。",12,MUTED,false);
        label(body,"这是配置辅助分析，不预测涨跌，不保证收益。",12,MUTED,false);
    }
    private void settings(){
        title("你的账本，你来掌握","基金手账 v0.1.0 · 安卓本地版");LinearLayout c=card();label(c,"投资与资产",18,INK,true);action(c,"配置目标 / 现金资产 / 负债",this::assetSettings);action(c,"投资期限与风险偏好",this::preferences);action(c,"交易与持仓核对",this::tradeList);
        LinearLayout backup=card();label(backup,"备份与恢复",18,INK,true);label(backup,"备份包含持仓金额和交易记录，为未加密 JSON 文件，请保存在可信位置。卸载应用会删除本机账本。",13,MUTED,false);action(backup,"导出完整备份",()->export(false));action(backup,"从备份恢复",this::chooseRestore);action(backup,"导出交易 CSV",()->export(true));
        LinearLayout data=card();label(data,"数据与运行说明",18,INK,true);label(data,"净值：天天基金公开数据，接口无稳定性保证。更新失败保留已有数据，也可手动补净值。\n\n国内日历：已内置上交所 2026 年休市安排；其他年份和基金特殊开放日需核对。\n\n后台：每天尝试更新，受系统省电影响；打开 App 时检查更新并补齐定投。\n\n隐私：不连接支付宝，不收集密码，不接入云端 AI 或统计服务。",13,MUTED,false);action(data,"更新所有基金净值",this::refresh);
    }
    private void refresh(){
        if(refreshing){toast("正在更新，请稍候");return;}refreshing=true;render();
        io.execute(()->{String message;try{message=Quotes.refresh(getApplicationContext());}catch(Exception e){message="更新失败："+e.getMessage();}String result=message;runOnUiThread(()->{refreshing=false;if(!isDestroyed()){render();toast(result);}});});
    }
    private final class Form {
        final LinearLayout content=column();final ScrollView scroll=new ScrollView(MainActivity.this);
        Form(){content.setPadding(dp(22),dp(8),dp(22),dp(12));scroll.addView(content);}
        EditText field(String name,String initial,boolean numeric){label(content,name,13,MUTED,true);EditText e=new EditText(MainActivity.this);e.setId(View.generateViewId());content.getChildAt(content.getChildCount()-1).setLabelFor(e.getId());e.setText(initial);e.setSingleLine(true);e.setTextSize(16);e.setMinHeight(dp(48));e.setSelectAllOnFocus(true);if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);content.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
        Spinner select(String name,String[] options,int selected){label(content,name,13,MUTED,true);Spinner s=new Spinner(MainActivity.this);s.setAdapter(new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_spinner_dropdown_item,options));s.setSelection(selected);s.setMinimumHeight(dp(48));s.setContentDescription(name);content.addView(s);return s;}
        void note(String value){label(content,value,12,MUTED,false);}
        void show(String title,String positive,Runnable save){AlertDialog d=new AlertDialog.Builder(MainActivity.this).setTitle(title).setView(scroll).setNegativeButton("取消",null).setPositiveButton(positive,null).create();d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{save.run();d.dismiss();render();}catch(Exception e){String m=e.getMessage();toast(m==null?"操作失败，数据未保存":m.contains("UNIQUE constraint")?"记录已存在，请勿重复录入":m);}}));d.show();}
    }
    private String value(EditText e){return e.getText().toString().trim();}
    private String selected(Spinner s){return s.getSelectedItem().toString();}
    private void addFund(){
        Form f=new Form();EditText code=f.field("基金代码（6 位，保留前导零）","",false);code.setInputType(InputType.TYPE_CLASS_NUMBER);EditText name=f.field("名称（可空，更新净值时补全）","",false);
        Spinner category=f.select("资产分类（混合基金请按主要风险手动归类）",Engine.CATEGORIES,0);EditText sector=f.field("行业标签（可空，例如宽基、医药）","",false);
        EditText shares=f.field("支付宝已确认持有份额","",true),cost=f.field("剩余持仓总成本（元）","",true),date=f.field("起始持仓日期",Engine.today().toString(),false);
        f.note("起始日期当天及之前的交易已包含在这里，不能再重复添加。当天待确认交易不应混入已确认份额。货币基金建议计入现金资产。");
        f.show("添加基金","保存",()->{store.addFund(value(code),value(name),selected(category),value(sector),value(shares),value(cost),value(date));refresh();});
    }
    private void classify(String code){JSONObject fund=store.fund(code);Form f=new Form();Spinner category=f.select("资产分类",Engine.CATEGORIES,java.util.Arrays.asList(Engine.CATEGORIES).indexOf(Store.s(fund,"category")));EditText sector=f.field("行业标签",Store.s(fund,"sector"),false);f.show("分类与标签","保存",()->store.classify(code,selected(category),value(sector)));}
    private String[] fundLabels(List<JSONObject> funds){String[] labels=new String[funds.size()];for(int i=0;i<funds.size();i++)labels[i]=Store.s(funds.get(i),"code")+" "+Store.s(funds.get(i),"name");return labels;}
    private void planForm(String code){
        List<JSONObject> funds=store.funds();if(funds.isEmpty()){toast("请先添加基金");return;}Form f=new Form();int index=0;for(int i=0;i<funds.size();i++)if(Store.s(funds.get(i),"code").equals(code))index=i;
        Spinner fund=f.select("基金",fundLabels(funds),index);EditText amount=f.field("每个交易日投入（元）","100",true),rate=f.field("实际申购费率（%，用于估算）","0",true),start=f.field("开始日期",Engine.today().plusDays(1).toString(),false);
        f.note("默认国内非交易日跳过，不顺延。填写支付宝显示的实际费率；未知时 0 仅作无费用估算。特殊暂停申购、境外假期和扣款失败无法自动获知。修改金额请结束旧计划，从次日新建。");
        f.show("每日定投","建立计划",()->store.addPlan(Store.s(funds.get(fund.getSelectedItemPosition()),"code"),value(amount),value(rate),value(start)));
    }
    private String typeName(String type){int i=java.util.Arrays.asList(TYPES).indexOf(type);return i<0?type:TYPE_NAMES[i];}
    private String statusName(String status){return switch(status){case "CONFIRMED"->"已确认";case "RECONCILED"->"已汇总核对 · 逐笔为估算";case "ESTIMATED"->"预计成交";case "SKIPPED"->"已跳过";default->"计划待核对";};}
    private void tradeList(){detailCode=null;screen=4;root.removeAllViews();render();showTrades(null);}
    private void showTrades(String code){
        showingTrades=true;tradeCode=code;
        body.removeAllViews();title("交易与核对","只把实际确认记录计入正式持仓");action(body,"＋ 手动记一笔",()->tradeForm(null,code));action(body,pendingOnly?"当前仅待核对 · 点击查看全部":"当前全部记录 · 点击只看待核对",()->{pendingOnly=!pendingOnly;showTrades(code);});
        String sql="SELECT t.*,f.name FROM trades t JOIN funds f ON t.code=f.code WHERE 1=1";
        List<String> args=new ArrayList<>();if(code!=null){sql+=" AND t.code=?";args.add(code);}if(pendingOnly)sql+=" AND t.status IN ('PLANNED','ESTIMATED')";
        sql+=" ORDER BY t.date DESC,t.id DESC LIMIT "+tradeLimit;
        List<JSONObject> records=store.rows(sql,args.toArray(new String[0]));
        for(JSONObject t:records){LinearLayout c=card();label(c,Store.s(t,"name"),17,INK,true);label(c,Store.s(t,"date")+" · "+typeName(Store.s(t,"type"))+" · "+statusName(Store.s(t,"status")),13,Store.s(t,"status").equals("CONFIRMED")?GREEN:MUTED,true);
            label(c,"金额 ¥"+Store.s(t,"amount")+" · 份额 "+Store.s(t,"shares"),15,INK,false);label(c,Store.s(t,"note"),12,MUTED,false);
            action(c,"核对 / 修改",()->tradeForm(t,Store.s(t,"code")));
        }
        if(records.isEmpty())label(body,"暂无对应记录。",14,MUTED,false);
        if(records.size()==tradeLimit)action(body,"显示更多",()->{tradeLimit+=100;showTrades(code);});
    }
    private void tradeForm(JSONObject trade,String code){
        List<JSONObject> funds=store.funds();if(funds.isEmpty()){toast("请先添加基金");return;}Form f=new Form();int index=0;for(int i=0;i<funds.size();i++)if(Store.s(funds.get(i),"code").equals(code))index=i;
        Spinner fund=f.select("基金",fundLabels(funds),index);fund.setEnabled(trade==null);
        Spinner type=f.select("交易类型",TYPE_NAMES,trade==null?0:java.util.Arrays.asList(TYPES).indexOf(Store.s(trade,"type")));if(trade!=null&&!trade.isNull("plan"))type.setEnabled(false);
        EditText date=f.field("交易净值归属日期",trade==null?Engine.today().toString():Store.s(trade,"date"),false);if(trade!=null&&!trade.isNull("plan"))date.setEnabled(false);
        EditText amount=f.field("金额（买入填含费支出，卖出填净到账）",trade==null?"":Store.s(trade,"amount"),true),shares=f.field("实际确认份额（卖出填正数）",trade==null?"":Store.s(trade,"shares"),true),fee=f.field("费用（已含于金额，仅留档）",trade==null?"0":Store.s(trade,"fee"),true);
        Spinner status=f.select("处理状态",new String[]{"已确认：以支付宝实际记录为准","待确认：不计入正式持仓","已跳过：扣款失败等"},trade!=null&&Store.s(trade,"status").equals("SKIPPED")?2:0);
        EditText note=f.field("备注",trade==null?"":Store.s(trade,"note"),false);
        f.note("确认定投前请核对实际金额、份额和日期。现金分红填金额、份额 0；红利再投填新增份额、金额 0。持仓调整填写份额和成本的增减，可为负数，并说明原因。费用不会重复扣除。");
        f.show("记录实际交易","保存",()->{String st=new String[]{"CONFIRMED","PLANNED","SKIPPED"}[status.getSelectedItemPosition()];store.saveTrade(trade==null?null:Store.s(trade,"id"),Store.s(funds.get(fund.getSelectedItemPosition()),"code"),value(date),TYPES[type.getSelectedItemPosition()],value(amount).trim().isEmpty()?"0":value(amount),value(shares).trim().isEmpty()?"0":value(shares),value(fee),st,value(note));});
    }
    private void reconcile(String code){
        Store.Position p=store.position(code);Form f=new Form();f.note("当前确认份额 "+Engine.shares(p.shares)+"；另有预计份额 "+Engine.shares(p.estimated)+"。请先逐笔确认或跳过待核对记录，再处理无法还原的差异。");
        EditText actual=f.field("支付宝已确认份额",p.shares.toPlainString(),true),cost=f.field("支付宝剩余持仓总成本",Engine.money(p.cost),true),date=f.field("核对日期",Engine.today().toString(),false),reason=f.field("调整原因（必填）","",false);
        f.show("持仓差异核对","添加调整记录",()->{
            if(!store.rows("SELECT id FROM trades WHERE code=? AND status IN ('PLANNED','ESTIMATED')",code).isEmpty())throw new IllegalArgumentException("请先处理这只基金的待核对交易，避免重复计入份额");
            if(value(reason).trim().isEmpty())throw new IllegalArgumentException("请填写调整原因");
            BigDecimal sh=Engine.nonNegative(value(actual)).subtract(p.shares),delta=Engine.nonNegative(value(cost)).subtract(p.cost);
            if(sh.signum()==0&&delta.signum()==0){toast("与账本一致，无需调整");return;}
            store.saveTrade(null,code,value(date),"ADJUST",delta.toPlainString(),sh.toPlainString(),"0","CONFIRMED",value(reason));
        });
    }
    private void batchReconcile(String code){
        Form f=new Form();EditText date=f.field("核对截至日期（与支付宝已确认交易对应）",Engine.today().minusDays(1).toString(),false);
        EditText shares=f.field("截至该日的实际总份额","",true),cost=f.field("截至该日的实际剩余总成本（元）","",true);
        f.note("一次核对这只基金截至所选日期的所有预计定投。请确认扣款都已成功；失败或停购的记录先标为跳过。汇总一致不代表逐笔份额准确，差异通过调整记录留档。核对日之后的交易继续保留，不会被重复计入。");
        f.show("批量核对定投","按实际持仓核对",()->store.reconcileBatch(code,value(date),value(shares),value(cost)));
    }
    private void manualNav(String code){Form f=new Form();EditText date=f.field("净值归属日期",Engine.today().minusDays(1).toString(),false),nav=f.field("已公布的单位净值","",true);f.note("请使用基金公司或支付宝已公布净值，不能填写盘中估算值。手动数据不提供当日收益率。");f.show("手动补净值","保存",()->{LocalDate d=Engine.date(value(date));if(d.isAfter(Engine.today()))throw new IllegalArgumentException("净值日期不能在未来");Engine.positive(value(nav));JSONObject row=new JSONObject();Store.put(row,"date",value(date));Store.put(row,"nav",value(nav));Store.put(row,"change","0");store.cacheNav(code,Store.s(store.fund(code),"name"),java.util.Collections.singletonList(row));store.setSetting("manual_"+code+"_"+value(date),"1");store.generate();});}
    private void assetSettings(){
        Form f=new Form();EditText[] assets=new EditText[6],targets=new EditText[6];
        f.note("基金资产自动统计，下面只填写基金以外的资产，避免重复。目标由你确认，工具不预设适合你的比例。");
        for(int i=0;i<6;i++){String c=Engine.CATEGORIES[i];assets[i]=f.field(c+"：其他资产金额（元）",store.setting("asset_"+c,"0"),true);targets[i]=f.field(c+"：目标比例（%）",store.setting("target_"+c,"0"),true);}
        EditText liability=f.field("负债总额（元）",store.setting("liability","0"),true),reserve=f.field("应急现金目标（元）",store.setting("reserve","0"),true);
        f.show("配置目标与其他资产","保存",()->{BigDecimal sum=BigDecimal.ZERO;for(int i=0;i<6;i++){Engine.nonNegative(value(assets[i]));sum=sum.add(Engine.nonNegative(value(targets[i])));}if(sum.signum()!=0&&sum.compareTo(new BigDecimal("100"))!=0)throw new IllegalArgumentException("目标比例总和必须为 100%，或全部为 0 暂不设置");Engine.nonNegative(value(liability));Engine.nonNegative(value(reserve));store.getWritableDatabase().beginTransaction();try{for(int i=0;i<6;i++){store.setSetting("asset_"+Engine.CATEGORIES[i],value(assets[i]));store.setSetting("target_"+Engine.CATEGORIES[i],value(targets[i]));}store.setSetting("liability",value(liability));store.setSetting("reserve",value(reserve));store.getWritableDatabase().setTransactionSuccessful();}finally{store.getWritableDatabase().endTransaction();}});
    }
    private void preferences(){Form f=new Form();EditText horizon=f.field("投资期限和用钱计划",store.setting("horizon",""),false),drawdown=f.field("能接受的最大亏损（%）",store.setting("drawdown","20"),true),monthly=f.field("每月可投入金额（元）",store.setting("monthly","0"),true);f.note("首版保存这些信息作为你的决策备忘，暂不据此自动计算适当性评分。");f.show("投资偏好","保存",()->{BigDecimal n=Engine.nonNegative(value(drawdown));if(n.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("最大亏损比例不能超过 100%");Engine.nonNegative(value(monthly));store.setSetting("horizon",value(horizon));store.setSetting("drawdown",value(drawdown));store.setSetting("monthly",value(monthly));});}
    private void export(boolean csv){
        if(csv){StringBuilder b=new StringBuilder("\uFEFF基金代码,日期,类型,金额,份额,费用,状态,备注\r\n");for(JSONObject t:store.rows("SELECT * FROM trades ORDER BY date,id")){String[] keys={"code","date","type","amount","shares","fee","status","note"};for(int i=0;i<keys.length;i++){if(i>0)b.append(',');String cell=Store.s(t,keys[i]);if(cell.matches("^[=+@-].*"))cell="'"+cell;b.append('"').append(cell.replace("\"","\"\"")).append('"');}b.append("\r\n");}exportPayload=b.toString();}else exportPayload=store.backup();
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(csv?"text/csv":"application/json").putExtra(Intent.EXTRA_TITLE,"fund-ledger-"+Engine.today()+(csv?".csv":".json"));startActivityForResult(i,201);
    }
    private void chooseRestore(){if(refreshing){toast("请等待行情更新结束后再恢复备份");return;}new AlertDialog.Builder(this).setTitle("恢复会替换当前账本").setMessage("请先导出当前备份。恢复文件会经过校验；失败时保留当前数据。").setNegativeButton("取消",null).setPositiveButton("选择备份",(d,w)->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),202)).show();}
    @Override public void onBackPressed(){if(showingTrades){showingTrades=false;render();}else if(detailCode!=null){detailCode=null;render();}else if(screen!=0)open(0);else super.onBackPressed();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        try{
            if(request==201){if(exportPayload==null)throw new IllegalStateException("页面已重建，请重新导出");try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IllegalStateException("无法打开文件");out.write(exportPayload.getBytes(StandardCharsets.UTF_8));}exportPayload=null;toast("已导出");}
            if(request==202){if(refreshing)throw new IllegalStateException("行情正在更新，请稍后重试");try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IllegalArgumentException("无法读取文件");byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>32*1024*1024)throw new IllegalArgumentException("备份超过 32 MB");}store.restore(out.toString(StandardCharsets.UTF_8.name()));}store.generate();render();toast("已恢复并补齐定投计划");}
        }catch(Exception e){toast("操作失败："+e.getMessage());}
    }
    private final class NavChart extends View {
        private final List<JSONObject> history;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path line=new Path();
        NavChart(List<JSONObject> rows){super(MainActivity.this);history=rows;setContentDescription("单位净值走势，共 "+rows.size()+" 期。最新净值 "+Store.s(rows.get(0),"nav"));}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight();double min=Double.MAX_VALUE,max=0;for(JSONObject r:history){double n=Store.n(r,"nav").doubleValue();min=Math.min(min,n);max=Math.max(max,n);}double range=Math.max(max-min,0.0001);paint.setColor(Color.rgb(230,237,232));paint.setStrokeWidth(dp(1));for(int i=0;i<3;i++)canvas.drawLine(0,dp(20)+i*(h-dp(50))/2,w,dp(20)+i*(h-dp(50))/2,paint);line.reset();for(int i=0;i<history.size();i++){JSONObject row=history.get(history.size()-1-i);float x=dp(4)+i*(w-dp(8))/(history.size()-1),y=dp(15)+(float)((max-Store.n(row,"nav").doubleValue())/range)*(h-dp(50));if(i==0)line.moveTo(x,y);else line.lineTo(x,y);}paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(GREEN);canvas.drawPath(line,paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(10));paint.setColor(MUTED);canvas.drawText(Store.s(history.get(history.size()-1),"date"),0,h-dp(5),paint);String last=Store.s(history.get(0),"date");canvas.drawText(last,w-paint.measureText(last),h-dp(5),paint);}
    }
}
