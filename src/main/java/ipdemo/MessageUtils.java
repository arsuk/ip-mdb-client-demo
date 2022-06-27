package ipdemo;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * Common message handling methods
 * 
 * NOTE: extra exception handling has been added for ActiveMQ Artemis (RedHat AMQ7) which is not necessary with ActimeMQ (RedHat AMQ6).
 * This needs cleaning up in the future - it scans the exception text as the cause is a sub-exception. Must be a better way.
 * 
 * @author allan
 *
 */
public class MessageUtils {
	
	private static final Logger logger = LoggerFactory.getLogger(MessageUtils.class);
	
	static boolean firstTime = true;

	static String sendMessage (String messageStr, String destinationName) throws NamingException {
		return sendMessage (messageStr, destinationName, null);
	}
	
	static String sendMessage (String messageStr, String destinationName, String status) throws NamingException {
		Context ic = new InitialContext();
		ConnectionFactory cf;
		Connection connection = null;
		Session session=null;
		
		String msgID=null;

		String servletHost=System.getProperty("ActiveMQhostStr");
		if (servletHost!=null) {
			if (firstTime) logger.info("Using host "+servletHost);
			String user=System.getProperty("ActiveMQuser");
			if (user!=null && firstTime) logger.info("Using user name "+user);
			String password=System.getProperty("ActiveMQpassword");
			cf=new ActiveMQConnectionFactory(user,password,servletHost);					
		} else {
			String cfStr=System.getProperty("ConnectionFactory");
			if (cfStr==null) cfStr="/ConnectionFactory";
			if (firstTime) logger.info("Using "+cfStr);
			cf = (ConnectionFactory)ic.lookup(cfStr);
		}
		firstTime=false;
		
		// Retry sends ignoring 'blocking exceptions'
		boolean retry=true;
		for (int retryCount=0; retryCount<10 && retry; retryCount++)
		try {
			// Get connection
			connection = cf.createConnection();
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			TextMessage message = session.createTextMessage();
			message.setText(messageStr);
			if (status!=null)
				message.setJMSType(status); // Allows for broker to use message selector
            Queue queue;
            try {
            	queue = (Queue)ic.lookup(destinationName);
        	} catch (NamingException e) {
                queue = session.createQueue(destinationName);            	
            }
			MessageProducer publisher = session.createProducer(queue);

			connection.start();

			publisher.send(message);
			msgID=message.getJMSMessageID();

			if (retryCount>0) logger.info("Retry "+retryCount+" OK");
			retry=false; 
		} catch (javax.jms.JMSException e) {
			String thisClass=Thread.currentThread().getStackTrace()[1].getClassName();
			String callerClass=thisClass;
			int i=2;
			while (callerClass.equals(thisClass)) {
				callerClass=Thread.currentThread().getStackTrace()[i].getClassName();
				i++;
			}
			if (e.toString().contains("Unblocking a blocking call") || e.toString().contains("Timed out") 
					|| e.toString().contains("Could not create a session") || e.toString().contains("Session closed") ) {
				logger.warn("Retrying "+callerClass+" call after "+e);
				try {
					if (session!=null) session.close();
				} catch (Exception ee) {logger.warn("Session close "+ee);};
				try {
					connection.close();
					Thread.sleep(1000);
				} catch (Exception ee) {logger.warn("Connection close "+ee);};
			} else {
				logger.error("Unexpected JMS error from "+callerClass+" : "+e);
				retry=false;
			}
		} finally {
			try {
				if (session!=null) session.close();
				if (connection!=null)connection.close();
			} catch (Exception ee) {};
		}
		if (msgID==null) logger.error("Message not Sent");
		return msgID;		
	}
}
