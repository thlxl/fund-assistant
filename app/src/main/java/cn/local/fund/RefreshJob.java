package cn.local.fund;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.job.JobParameters;
import android.content.ComponentName;
import android.content.Context;

public class RefreshJob extends JobService {
    private Thread worker;
    public static void schedule(Context context) {
        JobScheduler jobs=context.getSystemService(JobScheduler.class);
        if(jobs.getPendingJob(101)==null)jobs.schedule(new JobInfo.Builder(101,new ComponentName(context,RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(24*60*60*1000L).build());
    }
    @Override public boolean onStartJob(JobParameters params) {
        worker=new Thread(()->{Quotes.refresh(this);if(!Thread.currentThread().isInterrupted())jobFinished(params,false);},"fund-daily-refresh");
        worker.start();return true;
    }
    @Override public boolean onStopJob(JobParameters params) {if(worker!=null)worker.interrupt();return true;}
}
