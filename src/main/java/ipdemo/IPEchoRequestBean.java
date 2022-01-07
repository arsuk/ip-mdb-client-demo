package ipdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.MessageDriven;
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
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/** 
 * An MDB that simulates an Instant Payments client echo request / response function. 
 * It receives echo requests and replies with an echo response to show that the client is alive.
 * 
 */
@MessageDriven(name = "IPEchoRequestBean", activationConfig = {
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1"),	// Artemis property
        @ActivationConfigProperty(propertyName = "maxSessions", propertyValue = "1"),	// ActiveMQ property
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "instantpayments_mybank_echo_request")
})

public class IPEchoRequestBean implements MessageListener
{
	private static final Logger logger = LoggerFactory.getLogger(IPEchoRequestBean.class);

    private QueueConnectionFactory qcf;
    
    private Destination requestQueue=null;
    
	private String defaultTemplate="Echo_Response.xml";
	
	static private boolean firstTime=true;
    
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
    
	private byte[] docText;
	
	@EJB
    IPDBSessionBean dbSessionBean;
    
	public IPEchoRequestBean() {
		
		// Try to open a private pool (not-managed by Wildfly/JBoss). If null when not defined, will allocate a container managed pool later
    	try { 
            InitialContext iniCtx = new InitialContext();

			qcf=PrivatePool.createPrivatePool(iniCtx,logger);	// Null if not configured in standalone.xml
		} catch (javax.naming.NameNotFoundException je) {
			// Do nothing if no logfile name defined
		} catch (NamingException e) {
            logger.error("Activemq factory {}",e.getMessage());
            throw new EJBException(e);
		}
	}

    @PostConstruct
    public void ejbCreate()
    {
        try {
            // Create connection for replies and forwards
            InitialContext iniCtx = new InitialContext();

            if (qcf==null) {
            	// Use the application server ActiveMQ connection pool
                qcf = ManagedPool.getPool(iniCtx,logger);
            }
            try {
            	docText= XMLutils.getTemplate(defaultTemplate);
            } catch (Exception e) {
            	logger.error("Init Error",e);
            }
        } catch (NamingException e) {
            throw new EJBException("IPEchoRequest Init Exception ", e);
        }
    }

    public void onMessage(Message msg)
    {
    	if (docText==null) {
    		logger.error("Not initialised for onMessage (missing response template)");
    	}
    	else
        try {
        	Document replydoc=XMLutils.bytesToDoc(docText);

            String id=msg.getJMSMessageID();
            if (msg.getJMSRedelivered()) logger.info("Redelivered {} {}",new Date(),id);

            TextMessage tm = (TextMessage) msg;
            Document msgdoc=XMLutils.bytesToDoc(tm.getText().getBytes("UTF-8"));
             
            XMLutils.setElementValue(replydoc,"CreDtTm",dateTimeFormat.format(new Date()));
           	// Copy BIC from echo request <InstgAgt><FinInstnId><BIC>
            Element ia=XMLutils.getElement(msgdoc,"InstdAgt");
            if (ia!=null) {
            	String finInstBIC=XMLutils.getElementValue(ia,"BIC");
            	XMLutils.setElementValue(XMLutils.getElement(replydoc,"InstgAgt"),"BIC",finInstBIC);
            }
           	// Copy InsstrId from echo request <OrgnlInstrId> 
            String origInstId=XMLutils.getElementValue(msgdoc,"InstrId");
            XMLutils.setElementValue(replydoc,"OrgnlInstrId",origInstId);
            
            // Get a pooled connection
            QueueConnection conn = qcf.createQueueConnection();
            conn.start();
            QueueSession session = conn.createQueueSession(false,
                        QueueSession.AUTO_ACKNOWLEDGE);
            // Look up input destination - use the input request queue queue name and make it a response queue name
        	if (requestQueue==null) {
        		requestQueue=msg.getJMSDestination();	// This is the queue to which this MDB is listening
        		logger.info("Listening to: {}",requestQueue);
        	}
           	String destinationName="instantpayments_mybank_echo_response";
            if (requestQueue!=null) {
            	destinationName=requestQueue.toString();
            	// Remove Artemis created wrapper (if any)
            	destinationName=destinationName.replace("ActiveMQQueue[jms.queue.","");
            	destinationName=destinationName.replace("]","");
            	// Remove Activemq wrapper (if any)
            	destinationName=destinationName.replaceFirst("queue://", "");
            	// Change request to response
            	destinationName=destinationName.replaceFirst("_request$","_response");
            }
    		if (firstTime) logger.info("Sending to JNDI name: {}",destinationName);
    		else firstTime=false;
    		
            Queue responseDest;
            try {
                Context ic = new InitialContext();
            	responseDest = (Queue)ic.lookup(destinationName);
        	} catch (NamingException e) {
                responseDest = session.createQueue(destinationName);
            }
            QueueSender sender = session.createSender(responseDest);
            TextMessage sendmsg = session.createTextMessage(XMLutils.documentToString(replydoc));
            sender.send(sendmsg);
            sender.close();    	
            conn.close();	// Return connection to the pool
        } catch(JMSException | IOException e) {
            throw new EJBException(e);
        }
    }
}

