package ipdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.MessageDriven;
import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
 
/** 
 * An MDB that simulates an Instant Payments client originator response interface. 
 * It receives and counts the number of responses from the IP CSM.
 *  
 * The originator requests are sent by the IPTestServlet to the IP CSM which creates the response.
 */
@MessageDriven(name = "IPOriginatorResponseBean", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "instantpayments_mybank_originator_payment_response"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })

public class IPOriginatorResponseBean implements MessageDrivenBean, MessageListener {
	private static final Logger logger = LoggerFactory.getLogger(IPOriginatorResponseBean.class);
    // MessageDrivenBean interface Only needed in this example for access to context and rollback, otherwise this example only needs to be a MessageListener
    MessageDrivenContext context;

    static FileWriter writer=null;
    static final int FIVE_SECS=5000;

    public static long count=0;
    public static long totalResp=0;
    public static long totalCount=0; // Updated here, used in StatsServlet and SessionBeanResponseLogger timer task
    public static long minResponse=0;
    public static long maxResponse=0;
    public static long errorCount=0;
    public static long lateCount=0;
    public static long totalErrorCount=0;
    public static long totalLateCount=0;
    public static long averageResponse=0;	// Displayed in StatsServlet, displayed and updated in SessionBeanResponseLogger timer task

    private int workSimulationDelay=0;
    
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264

	@EJB
	IPDBSessionBean dbSessionBean;
	
    public void ejbCreate()
    {
        InitialContext iniCtx;
		try {
            String delayStr = System.getProperty("IPResponseDelay");
            if (delayStr!=null) workSimulationDelay=Integer.parseInt(delayStr);
            if (workSimulationDelay!=0) logger.info("Delay "+workSimulationDelay);

			iniCtx = new InitialContext();
	       	logger.info("Started");
			String outfileStr=System.getProperty("IPresponseLog");
            if (outfileStr!=null) {
	            logger.info("Log: "+outfileStr);
	       	    if (outfileStr!=null&&outfileStr.trim().length()>0) writer=new FileWriter(outfileStr, true);    // Append mode
	       	}
		} catch (javax.naming.NameNotFoundException je) {
			logger.warn("Naming error "+je);
		} catch (Exception e) {
			logger.error("Log error: "+e);
		}
    }

    public void onMessage(Message inMessage) {
        TextMessage message = (TextMessage)inMessage;
        try {
        	String id=inMessage.getJMSMessageID();
        	if (inMessage.getJMSRedelivered()) logger.warn("Redelivered "+new Date()+" "+id);

            String msgText=message.getText();
            //logger.info(String.format("TestBean Message, %s", msgText));
            Document msgdoc=XMLutils.bytesToDoc(msgText.getBytes("UTF-8"));
            String txid=XMLutils.getElementValue(msgdoc,"OrgnlTxId");
            String status=XMLutils.getElementValue(msgdoc,"GrpSts");
            String reason=XMLutils.getElementValue(msgdoc,"StsRsnInf");
            if (reason==null) reason="";

            if (status==null || txid==null) {
            	logger.warn("Illegal message "+msgText);
            	return;
            }
            //logger.info(String.format("TestBean Message, %s", msgText));
            if (dbSessionBean.getTXinfo(id)==null)
            	dbSessionBean.insertTX(id,txid,"response",status.trim(),reason.trim(),msgText);
            else
            	logger.warn("Duplicate message "+new Date()+" "+id);

            String origTimeStr=XMLutils.getElementValue(msgdoc,"AccptncDtTm");
            Date origTime = null;
            try {
            	dateTimeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            	origTime = dateTimeFormat.parse(origTimeStr);
            } catch (Exception e) {
            	logger.warn("Bad date "+origTimeStr);
            }
            String sts=XMLutils.getElementValue(msgdoc,"GrpSts");
 
            // Simulate work
           	try {Thread.sleep(workSimulationDelay);} catch (InterruptedException ie) {logger.error("Sleep "+ie);};
           	logger.debug("Work simulation {}",workSimulationDelay);

           	synchronized (this) {	// Avoid counter update errors multiple bean instances
           		count++;
           		totalCount++;
                if (sts==null || !sts.equals("ACCP")) {
                	errorCount++;	// Anything apart from acceptance is a reject error...
                	totalErrorCount++;
                }	
	            if (origTime!=null) {
	            	long diff=new Date().getTime()-origTime.getTime();
	            	if (diff>FIVE_SECS) {
	            		lateCount++;	// SLA 5s
	            		totalLateCount++;
	            	} else if (diff<0) {
	            		logger.warn("Time now earlier than acceptance time "+origTimeStr);
	            	}
	           		totalResp=totalResp+diff;
	           		if (diff<minResponse||minResponse==0) minResponse=diff;
	           		if (diff>maxResponse) maxResponse=diff;
	           	}
           	}
           
         	if (writer!=null) {
         		writer.write(msgText+"\n");
         	}
         	
        } catch (JMSException | IOException e) {
            throw new EJBException(e);
        }
    }

    public void setMessageDrivenContext(MessageDrivenContext mdc) 
    {
     /* As with all EJBs, you must set the context in order to be 
        able to use it at another time within the MDB methods. */
     this.context = mdc;
    }

    public void ejbRemove()
    {
    	logger.info("Total count "+totalCount);
    	try {if (writer!=null) writer.close();} catch (Exception e) {};
    }

}

/* Using Default resource adapter -activationConfig list
@ResourceAdapter("activemq-rar-4.1.1.rar") 
*/

