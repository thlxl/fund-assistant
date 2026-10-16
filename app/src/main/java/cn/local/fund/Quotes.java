package cn.local.fund;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Quotes {
    private Quotes() {}
    public static final class Data {
        public String name;
        public List<JSONObject> navs=new ArrayList<>();
    }
    public static Data parse(String code,String text) throws Exception {
        Engine.code(code);
        String found=match(text,"\\bfS_code\\s*=\\s*\"(\\d{6})\"");
        if(!code.equals(found)) throw new IllegalArgumentException("返回的基金代码不匹配");
        Data result=new Data();result.name=new JSONArray("["+match(text,"\\bfS_name\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\")")+"]").getString(0);
        JSONArray trend=new JSONArray(match(text,"\\bData_netWorthTrend\\s*=\\s*(\\[.*?\\])\\s*;"));
        for(int i=0;i<trend.length();i++) {
            JSONObject raw=trend.getJSONObject(i);JSONObject row=new JSONObject();
            String day=Instant.ofEpochMilli(raw.getLong("x")).atZone(Engine.ZONE).toLocalDate().toString();
            if(Engine.date(day).isAfter(Engine.today())) continue;
            String nav=raw.get("y").toString();Engine.positive(nav);
            String change=raw.optString("equityReturn","0");Engine.number(change);
            row.put("date",day);row.put("nav",nav);row.put("change",change);result.navs.add(row);
        }
        if(result.navs.isEmpty()) throw new IllegalArgumentException("暂无支持的单位净值数据，货币基金请作为现金录入");
        return result;
    }
    private static String match(String text,String regex) {
        Matcher m=Pattern.compile(regex,Pattern.DOTALL).matcher(text);
        if(!m.find()) throw new IllegalArgumentException("行情数据格式已变化或基金不支持");
        return m.group(1);
    }
    public static Data fetch(String code) throws Exception {
        Engine.code(code);
        HttpURLConnection connection=(HttpURLConnection)new URL("https://fund.eastmoney.com/pingzhongdata/"+code+".js").openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent","Mozilla/5.0 FundLedger/0.1");
        connection.setRequestProperty("Referer","https://fund.eastmoney.com/"+code+".html");
        try {
            if(connection.getResponseCode()!=200)throw new IllegalStateException("行情服务返回 "+connection.getResponseCode());
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int size;
                while((size=in.read(buffer))!=-1){out.write(buffer,0,size);if(out.size()>8*1024*1024)throw new IllegalArgumentException("行情响应过大");}
                return parse(code,out.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {connection.disconnect();}
    }
    public static String refresh(Context context) {
        int ok=0,fail=0;
        try(Store store=new Store(context)) {
            for(JSONObject f:store.funds()) {
                if(Thread.currentThread().isInterrupted()) break;
                String code=Store.s(f,"code");
                try {Data data=fetch(code);store.cacheNav(code,data.name,data.navs);ok++;}
                catch(Exception e){store.setSetting("error_"+code,e.getMessage()==null?"获取失败":e.getMessage());fail++;}
            }
            store.generate();
        }
        return "更新完成："+ok+" 只成功，"+fail+" 只失败";
    }
}
