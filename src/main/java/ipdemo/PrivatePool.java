package ipdemo;

import javax.naming.InitialContext;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.jms.pool.PooledConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/** 
 * Get connection from a private queue connection pool<br/>
 * A private pool is not managed by Wildfly and is created as a static option. This is only useful for test purposes such
 * as avoiding transaction management or specifying an alternative broker host. If the property, ActiveMQhostStr, 
 * does not exist then the pool is not created and the calling code must use the managed pool. 
 * @author Allan Smith
 * 
 */
public class PrivatePool {

	static private PooledConnectionFactory privatePooledConnectionFactory=null;

   	// Check too see if there is a separate ActiveMQ host str (if not the Wildfly pool will be used).
	// Get the data source (broker) host name from a system property "ActiveMQhostStr" or 
    // get data source using the Wildfly naming (jndi) subsystem <bindings> in standalone.xml:
    // <simple name="java:global/ActiveMQhostStr" value="failover:tcp://localhost:61616"/>
	//
	// This code is provided as an example of defining a private connection pool (not container managed)
	// which may be useful for accessing ActiveMQ features. It can also be useful to create a pool which is
	// not defined with XA transaction management which may be the case with the managed pools. 
	// It is usually simpler to define one or more managed pools in the application server
	// (standalone.xml for JBoss / Wildfly).

	static PooledConnectionFactory createPrivatePool(InitialContext iniCtx, Logger logger) {
		String hostStr=System.getProperty("ActiveMQhostStr");
		// If not defined as a system property, check if it is defined as a JNDI name
		if (hostStr==null) try {
			hostStr=(String)iniCtx.lookup("/global/ActiveMQhostStr");
		} catch (Exception e){};
		// If the host name exists and we have not already created the statically defined pool create the pool
		if (hostStr!=null && privatePooledConnectionFactory==null) {

    		logger.info("Using: {}",hostStr);
    		ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(hostStr);
    		
    		String user=System.getProperty("ActiveMQuser");
    		if (user==null)
    		try {
    			user=(String)iniCtx.lookup("/global/ActiveMQuser");
    		} catch (Exception e){};
    		if (user!=null) {
        		logger.info("Using user name: {}",user);
    			factory.setUserName(user);
    		}

    		String password=System.getProperty("ActiveMQpassword");
    		if (password==null)
			try {
				password=(String)iniCtx.lookup("/global/ActiveMQpassword");
			} catch (Exception e){};
    		if (password!=null) {
				factory.setPassword(password);
    		}
 		
	    	privatePooledConnectionFactory = new PooledConnectionFactory();
	    	privatePooledConnectionFactory.setConnectionFactory(factory);
	    	privatePooledConnectionFactory.setIdleTimeout(0);	// Default 0 - no timeout
	    	privatePooledConnectionFactory.setMaxConnections(15); // Default 10
	    	privatePooledConnectionFactory.start();
		}
		return privatePooledConnectionFactory;
	}
}
