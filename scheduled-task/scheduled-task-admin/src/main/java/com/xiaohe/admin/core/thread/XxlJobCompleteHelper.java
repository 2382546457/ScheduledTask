package com.xiaohe.admin.core.thread;

import com.xiaohe.admin.core.complete.XxlJobCompleter;
import com.xiaohe.admin.core.conf.XxlJobAdminConfig;
import com.xiaohe.admin.core.model.XxlJobLog;
import com.xiaohe.core.model.Result;
import com.xiaohe.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author : 小何
 * @Description :
 * 1. 对于执行器发送的执行结果回调做出具体的处理 (并没有接收，调度中心的HTTP服务器接收后调用 XxlJobCompleteHelper 做出处理)
 * 2. 对于 调度成功，但是由于执行器宕机而无法对执行状态 (handle_code) 做出更改的这些任务，将它们的执行状态改为失败。
 *     为什么呢？因为这些任务可能需要重试，但是 handle_code 不为0无法重试。所以用 XxlJobCompleteHelper 将状态改一下。
 * @date : 2023-10-13 21:52
 */
public class XxlJobCompleteHelper {
    private static Logger logger = LoggerFactory.getLogger(XxlJobCompleteHelper.class);

    private static XxlJobCompleteHelper instance = new XxlJobCompleteHelper();
    public static XxlJobCompleteHelper getInstance() {
        return instance;
    }
    /**
     * 这个线程池处理执行器回调回来的任务执行结果
     */
    private ThreadPoolExecutor callbackThreadPool = null;

    /**
     * 这个线程扫描那些调度了但是因为执行器宕机了所以无法更新 handle_code 的任务，将handle_code更新了。
     */
    private Thread monitorThread;

    /**
     * 组件是否结束
     */
    private volatile boolean toStop = false;

    public void start() {
        // 先给线程池new一下
        initCallbackThreadPool();
        // 给线程赋上任务
        monitorThread = new Thread(() -> {
            // xxl-job刚启动的时候肯定不这么急着执行任务。先睡50ms，让其他线程池初始化完毕
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                if (!toStop) {
                    logger.error(e.getMessage(), e);
                }
            }
            // 开始循环任务
            while (!toStop) {
                // 找到10分钟内 调度了 && 没执行 && 执行器宕机 的log
                // 即: xxl_job_log.trigger_code = 200 && xxl_job.handle_code = 0 && xxl_job_registry.id = null
                Date losedTime = DateUtil.addMinutes(new Date(), -10);
                List<Long> lostJobIds = XxlJobAdminConfig.getAdminConfig().getXxlJobLogMapper().findLostJobIds(losedTime);
                for (Long lostJobId : lostJobIds) {
                    XxlJobLog xxlJobLog = new XxlJobLog();
                    xxlJobLog.setId(lostJobId);
                    xxlJobLog.setHandleTime(new Date());
                    xxlJobLog.setHandlerCode(Result.FAIL_CODE);
                    xxlJobLog.setHandleMsg("joblog_lost_fail");
                    XxlJobCompleter.updateHandleInfoAndFinish(xxlJobLog);
                }


                // 一分钟扫描一次
                try {
                    TimeUnit.SECONDS.sleep(60);
                } catch (InterruptedException e) {
                    if (!toStop) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.setName("xxl-job, admin JobLosedMonitorHelper");
        monitorThread.start();
    }

    private void initCallbackThreadPool() {
        callbackThreadPool = new ThreadPoolExecutor(
                2,
                20,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(3000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "xxl-job, admin JobLosedMonitorHelper-callbackThreadPool-" + r.hashCode());
                    }
                },
                new RejectedExecutionHandler() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        r.run();
                        logger.warn(">>>>>>>>>> xxl-job, callback too fast, match threadpool rejected handler(run now)");
                    }
                }
        )
    }

}
