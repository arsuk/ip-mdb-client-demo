package ipdemo;
 
import java.io.IOException;

import javax.ejb.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
@Singleton
public class IPTimedSessionBeanResponseLogger {
	private static final Logger logger = LoggerFactory.getLogger(IPTimedSessionBeanResponseLogger.class);
	
    static final long TENSECS=10000;
    static final long ONESEC=1000;
	static private long lastTime=0;

    @Schedule(hour="*", minute="*", second="*/10", persistent=false)
    public void logCountsJob() throws IOException {
        // Do your job here which should run every 10 secs.

    	long now=System.currentTimeMillis();
    	long interval=now-lastTime;
    	lastTime=now;
        if (interval<TENSECS) interval=TENSECS; // sometimes 1ms to early

		// Display and reset stats if transactions have been sent back to the originator bean
    	if (IPOriginatorResponseBean.count>0) {
    		long sendCount=IPTestServlet.sendCount;
    		long count=IPOriginatorResponseBean.count;
    		long errorCount=IPOriginatorResponseBean.errorCount;
    		long totalResp=IPOriginatorResponseBean.totalResp;
    		IPTestServlet.sendCount=0;
    		IPOriginatorResponseBean.count=0;
    		IPOriginatorResponseBean.errorCount=0;
    		IPOriginatorResponseBean.totalResp=0;
    		IPOriginatorResponseBean.averageResponse=totalResp/count;
        	logger.info("Response. Interval "+interval+
        			"ms, Servlet Original count "+sendCount+", Response count "+count+", Error count "+errorCount);
        	logger.info("Min Response "+(long)IPOriginatorResponseBean.minResponse+"ms"+
        			", Avg Response "+(long)IPOriginatorResponseBean.averageResponse+"ms"+
        			", Max Response "+(long)IPOriginatorResponseBean.maxResponse+"ms, "+
        			String.format("%.1f", (float)count/(interval/ONESEC))+" TPS");
        	IPOriginatorResponseBean.minResponse=0;
        	IPOriginatorResponseBean.maxResponse=0;
            // Flush bean logger if present
          	if (IPOriginatorResponseBean.writer!=null) 
          		IPOriginatorResponseBean.writer.flush();
        }
    	if (IPBeneficiaryConfirmationBean.count>0) {
    		long count=IPBeneficiaryConfirmationBean.count;
    		IPBeneficiaryConfirmationBean.count=0;
        	logger.info("Confirmation. Interval "+interval+
        			"ms, count "+count+
        			", "+String.format("%.1f", (float)count/(interval/ONESEC))+" TPS");
    		// Beneficiary confirmation stats updated here for consistency but not logged (showed in stats servlet)
			IPBeneficiaryConfirmationBean.averageResponse=IPBeneficiaryConfirmationBean.totalResp/count;
			IPBeneficiaryConfirmationBean.totalResp=0;
            // Flush bean logger if present
          	if (IPBeneficiaryConfirmationBean.writer!=null) 
          		IPBeneficiaryConfirmationBean.writer.flush();
		}
    }
}
