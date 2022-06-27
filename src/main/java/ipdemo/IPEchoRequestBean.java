package ipdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
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
    
    private Destination requestQueue=null;
    
    private String destinationName="instantpayments_mybank_echo_response";
    
	private String defaultTemplate="Echo_Response.xml";
    
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
    
	private byte[] docText;
	
	@EJB
    IPDBSessionBean dbSessionBean;
    
	public IPEchoRequestBean() {
		
	}

    @PostConstruct
    public void ejbCreate()
    {
    	docText= XMLutils.getTemplate(defaultTemplate);
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
            
            // Look up input destination - use the input request queue queue name and make it a response queue name
        	if (requestQueue==null) {
        		requestQueue=msg.getJMSDestination();	// This is the queue to which this MDB is listening
        		logger.info("Listening to: {}",requestQueue);

            	destinationName=requestQueue.toString();
            	// Remove Artemis created wrapper (if any)
            	destinationName=destinationName.replace("ActiveMQQueue[","");
            	destinationName=destinationName.replace("jms.queue.","");
            	destinationName=destinationName.replace("]","");
            	// Remove Activemq wrapper (if any)
            	destinationName=destinationName.replaceFirst("queue://", "");
            	// Change request to response
            	destinationName=destinationName.replaceFirst("_request$","_response");
            	
            	logger.info("Sending to JNDI name: {}",destinationName);
            }
    		
        	MessageUtils.sendMessage(XMLutils.documentToString(replydoc),destinationName);

        } catch(JMSException | IOException | NamingException e) {
            logger.error("Unexpected error "+e);
        }
    }
}

