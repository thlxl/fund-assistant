package cn.local.fund;

import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class AppSmokeTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public AppSmokeTest(){super(MainActivity.class);}
    private View find(View v,String text){
        if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    public void testAllFiveScreensOpen() {
        MainActivity activity=getActivity();
        for(String tab:new String[]{"首页","持仓","定投","分析","我的"}){
            getInstrumentation().runOnMainSync(()->{View v=find(activity.getWindow().getDecorView(),tab);assertNotNull(v);v.performClick();});
            getInstrumentation().waitForIdleSync();
        }
        assertNotNull(find(activity.getWindow().getDecorView(),"备份与恢复"));
    }
}
