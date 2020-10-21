package ipdemo;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
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
 * An MDB that simulates an Instant Payments client beneficiary confirmation interface. 
 * It receives and counts the number of confirmations it received from the IP CSM.
 *  
 * The beneficiary requests are sent by the IP CSM to the IPBeneficiaryConfiemationBean which provides a response to the IP CSM.
 * The IP CSM sends the confirmation which this bean receives and counts.
 */ 
@MessageDriven(name = "IPBeneficiaryConfirmationBean", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "instantpayments_mybank_beneficiary_payment_confirmation"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")  })

public class IPBeneficiaryConfirmationBean implements MessageDrivenBean, MessageListener {
    // MessageDrivenBean interface Only needed in this example for access to context and rollback, otherwise this example only needs to be a MessageListener
	private static final Logger logger = LoggerFactory.getLogger(IPBeneficiaryConfirmationBean.class);
    MessageDrivenContext context;

    static FileWriter writer=null;

    public static long count=0;
    public static long totalResp=0;
    public static long totalCount=0;	 // Updated here, used in StatsServlet and SessionBeanTimer    
    public static long averageResponse=0;	// Displayed in StatsServlet
    
    private int workSimulationDelay=0;

	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264

	@EJB
	IPDBSessionBean dbSessionBean;

    public void ejbCreate()
    {
        InitialContext iniCtx;
		try {
            String delayStr = System.getProperty("IPConfirmationDelay");
            if (delayStr!=null) workSimulationDelay=Integer.parseInt(delayStr);
            if (workSimulationDelay!=0) logger.info("Delay "+workSimulationDelay);

			iniCtx = new InitialContext();
            logger.info("Started");
		} catch (javax.naming.NameNotFoundException je) {
			logger.warn("Naming error "+je);
		} catch (Exception e) {
			logger.error("Log error: "+e);
            throw new EJBException(e);
		}
    }

    public void onMessage(Message inMessage) {
        TextMessage message = (TextMessage)inMessage;
        try {
            String id=inMessage.getJMSMessageID();
			if (inMessage.getJMSRedelivered()) logger.info("Redelivered "+new Date()+" "+id);
			
			id=id+"-c"; // Make unique for confirmation (may be testing by forwarding response)

            String msgText=message.getText();
            logger.debug(String.format("TestBean Message, %s", msgText));
            Document msgdoc=XMLutils.bytesToDoc(msgText.getBytes("UTF-8"));
            String txid=XMLutils.getElementValue(msgdoc,"OrgnlTxId");
            String status=XMLutils.getElementValue(msgdoc,"GrpSts");
            String reason=XMLutils.getElementValue(msgdoc,"StsRsnInf");
            if (reason==null) reason="";
            else reason=reason.trim();

            if (dbSessionBean.getTXstatus(id)==null)
            	dbSessionBean.insertTX(id,txid,"confirmation",status,reason,msgText);
            else
            	logger.warn("Duplicate message "+new Date()+" "+id);

            String origTimeStr=XMLutils.getElementValue(msgdoc,"AccptncDtTm");
            Date origTime = null;
            try {
            	dateTimeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            	origTime = dateTimeFormat.parse(origTimeStr);
            } catch (Exception e) {
            	logger.warn("IPBeneficiaryConfirmation - bad date "+origTimeStr);
            }            
         	// Simulate work
           	try {Thread.sleep(workSimulationDelay);} catch (InterruptedException ie) {};

            count++;
            totalCount++;
         	if (writer!=null) {
         		writer.write(msgText+"\n");
         	}
            if (origTime!=null) {
            	long diff=new Date().getTime()-origTime.getTime();
           		totalResp=totalResp+diff;
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

