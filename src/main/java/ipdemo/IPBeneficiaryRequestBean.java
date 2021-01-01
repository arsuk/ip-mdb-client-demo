package ipdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.MessageDriven;
import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/** 
 * An MDB that simulates an Instant Payments client beneficiary. 
 * It receives creditor requests from the IP CSM and replies with a response - accepted or rejected (if amount > 100). 
 * 
 */
@MessageDriven(name = "IPBeneficiaryRequestBean", activationConfig = {
		//@ActivationConfigProperty(propertyName = "transaction-type", propertyValue = "Bean"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "instantpayments_mybank_beneficiary_payment_request")  })

public class IPBeneficiaryRequestBean implements MessageDrivenBean, MessageListener
{
	private static final Logger logger = LoggerFactory.getLogger(IPBeneficiaryRequestBean.class);

    private MessageDrivenContext ctx = null;
    private QueueConnectionFactory qcf;	// To get outgoing connections for sending messages
    
    private int rejectLimit=100;
    
	static final long SEVENSECS=7000;
    
	private String defaultTemplate="pacs.002.xml";
    
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
    
	private byte[] docText;
	
    String delayStr = System.getProperty("IPRequestDelay");
	
	@EJB
	IPDBSessionBean dbSessionBean;
    
	public IPBeneficiaryRequestBean() {

		// Try to open a private pool (not-managed by Wildfly/JBoss). If null when not defined, will allocate a container managed pool later
     	try {
			InitialContext iniCtx = new InitialContext(); 				

			qcf = PrivatePool.createPrivatePool(iniCtx,logger);	// Null if not configured in standalone.xml
		} catch (javax.naming.NameNotFoundException je) {
			logger.debug("Factory naming error "+je);
		} catch (Exception e) {
			logger.error("Activemq factory "+e);
		};
	}

    public void setMessageDrivenContext(MessageDrivenContext ctx)
    {
        this.ctx = ctx;
        //logger.info("BeneficiaryRequest.setMessageDrivenContext, this=" + hashCode());
    }
    
    public void ejbCreate()
    {
    	// Check for pooled connection factory created directly with ActiveMQ above or
    	// get a pool defined in the standalone.xml.
        try {
            // Create connection for replies and forwards
            InitialContext iniCtx = new InitialContext();

            if (qcf==null) {
                qcf = ManagedPool.getPool(iniCtx,logger);
            }

            // Get XML template for creating messages from a file - real application code would have a better way of doing this
            docText=XMLutils.getTemplate(defaultTemplate);
            
	       	logger.info("Started");
        } catch (Exception e) {
            throw new EJBException("Init Exception ", e);
        }
    }

    public void ejbRemove()
    {
        //logger.info("BeneficiaryRequest.ejbRemove, this="+hashCode());

        ctx = null;

    }
                
    public void onMessage(Message msg)
    {
        logger.trace("BeneficiaryRequest.onMessage, this="+hashCode());
        
    	if (docText==null) {
    		logger.error("IPBeneficiaryRequest not initialised for onMessage (missing response template)");
    	}
    	else
        try {
        	Document doc=XMLutils.bytesToDoc(docText);	// pacs.002 template

            String id=msg.getJMSMessageID();
            if (msg.getJMSRedelivered()) logger.info("Redelivered "+new Date()+" "+id);

            TextMessage tm = (TextMessage) msg;
            Document msgdoc=XMLutils.stringToDoc(tm.getText());	// pacs.008 input msg
            String txid=XMLutils.getElementValue(msgdoc,"TxId");
            String status=XMLutils.getElementValue(msgdoc,"GrpSts");
            String acceptanceTime=XMLutils.getElementValue(doc,"AccptncDtTm");

            if (txid==null) {
            	logger.warn("Illegal message "+tm.getText());
            	return;
            }            
            if (status==null)
            	status="";
            String reason=XMLutils.getElementValue(msgdoc,"StsRsnInf");
            if (reason==null) reason="";
            
            if (dbSessionBean.getTXinfo(id)==null)
            	dbSessionBean.insertTX(id,txid,"request",status.trim(),reason.trim(),tm.getText());
            else
            	logger.warn("Duplicate "+new Date()+" "+id+" "+txid);
            
            XMLutils.setElementValue(doc,"MsgId",hashCode()+"-"+System.nanoTime());
            XMLutils.setElementValue(doc,"CreDtTm",dateTimeFormat.format(new Date()));
        	// Copy BIC from pacs.008 <DbtrAgt>FinInstnId><BIC> 
            String debtorBIC=XMLutils.getElementValue(XMLutils.getElement(msgdoc,"DbtrAgt"),"BIC");
            // To pacs.002 <DbtrAgt>FinInstnId><BIC>
            XMLutils.setElementValue(XMLutils.getElement(doc,"DbtrAgt"),"BIC",debtorBIC);
            
            XMLutils.setElementValue(doc,"OrgnlMsgId",XMLutils.getElementValue(msgdoc,"MsgId"));
            float value=0;
            try {
            	// Convert to float but could do a SEPA currency format check here (not needed for a demo)
            	value=Float.parseFloat(XMLutils.getElementValue(msgdoc,"IntrBkSttlmAmt"));
            	if(value>rejectLimit||value==0) reason="AM02";	// Limit reason code
            } catch (RuntimeException e) {
            	reason="FF01";	// Syntax / system error reason code
            }

            Date aTime = null;
            try {
            	dateTimeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            	aTime = dateTimeFormat.parse(acceptanceTime);
            } catch (Exception e) {
            	logger.error("Bad AccptncDtTm "+acceptanceTime+" "+txid);
            	return;
            }
            if (status.equals("ACCP")) {
	            // Check timeout
	        	long diff=new Date().getTime()-aTime.getTime();
	        	logger.debug("Acceptance time {}",aTime);
	        	if (diff>SEVENSECS) {
	        		reason="AB06";	// Instructed agent timeout reject
	        	}
            }

        	// Reject message if value above limit to enable reject flow testing or if a timeout
            if(!reason.isEmpty()) {
            	XMLutils.setElementValue(doc,"GrpSts","RJCT");
               	Element grpInf=XMLutils.getElement(doc,"OrgnlGrpInfAndSts");
               	Element cdNode = doc.createElement("Cd");
               	cdNode.setTextContent(reason);
               	Element rsnNode = doc.createElement("Rsn");
               	rsnNode.appendChild(cdNode);
               	Element rsnInfNode = doc.createElement("StsRsnInf");
               	rsnInfNode.appendChild(rsnNode);
                grpInf.appendChild(rsnInfNode);
            }
            else
            	XMLutils.setElementValue(doc,"GrpSts","ACCP");
            XMLutils.setElementValue(doc,"OrgnlInstrId",XMLutils.getElementValue(msgdoc,"InstrId"));
            XMLutils.setElementValue(doc,"OrgnlEndToEndId",XMLutils.getElementValue(msgdoc,"EndToEndId"));
            XMLutils.setElementValue(doc,"OrgnlTxId",txid);
            XMLutils.copyElementValues(msgdoc,doc,"AccptncDtTm");
            
            // Assume that we are the bank that receives the credit so use the pacs.008 CdtrAgt BIC as our BIC
            // This makes this test code work as an answering machine for any bank (in real life this should be checked)
            Element credAgt=XMLutils.getElement(msgdoc,"CdtrAgt");
            if (credAgt==null) throw new EJBException(new RuntimeException("Missing CdtrAgt"));
            String creditorBIC=XMLutils.getElementValue(credAgt,"BIC");
            XMLutils.setElementValue(XMLutils.getElement(doc,"InstgAgt"),"BIC",creditorBIC);           

            // Simulate work if specified in RmtInf Ustrd or if a delay is specified by a system property.
            String sleepStr=XMLutils.getElementValue(msgdoc,"Ustrd");
            if (sleepStr==null && delayStr!=null) sleepStr=delayStr;
            long workTime=0;
            if (sleepStr!=null)
            try {
            	workTime=Long.parseUnsignedLong(sleepStr);
            } catch (Exception e) {};
            // Work / slowness simulation - time specified if numeric value passed with pacs.008 in remittance info
           	try {Thread.sleep(workTime);} catch (InterruptedException ie) {logger.error("Sleep "+ie);};

            // Get a pooled connection
            QueueConnection conn = qcf.createQueueConnection();
            conn.start();
            QueueSession session = conn.createQueueSession(false,
                        QueueSession.AUTO_ACKNOWLEDGE);
            // Look up destination - use the input request queue queue name and make it a response queue name
        	String destinationName="instantpayments_mybank_beneficiary_payment_response";	// Default if input queue not found
            Destination requestQueue=msg.getJMSDestination();
            if (requestQueue!=null) {
            	destinationName=requestQueue.toString();
            	// Remove Artemis created wrapper (if any)
            	destinationName=destinationName.replace("ActiveMQQueue[jms.queue.","");
            	destinationName=destinationName.replace("]","");
            	// Remove Activemq wrapper (if any)
            	destinationName=destinationName.replaceFirst("queue://", "");
            	// Chane request to response
            	destinationName=destinationName.replaceFirst("_request$","_response");
            }
            Queue responseDest;
            try {	// Try looking up the name in the context case it is a JNDI name 
                Context ic = new InitialContext();
            	responseDest = (Queue)ic.lookup(destinationName);
        	} catch (NamingException e) {
                responseDest = session.createQueue(destinationName);            	
            }           
            QueueSender sender = session.createSender(responseDest);
            TextMessage sendmsg = session.createTextMessage(XMLutils.documentToString(doc));
            sendmsg.setJMSType(value>rejectLimit?"RJCT":"ACCP"); // Allows for broker to use message selector
            sender.send(sendmsg);
            sender.close();
            session.close();
            conn.close();	// Return connection to the pool
           
            // Throw exception on bad value to test rollback error handling - real applications code would reject the message
            if (value<0 && !msg.getJMSRedelivered()) throw new EJBException(new RuntimeException("Bad tx "+txid+" value "+value));           
        } catch(JMSException e) {
            throw new EJBException(e);
		}
    }

}

