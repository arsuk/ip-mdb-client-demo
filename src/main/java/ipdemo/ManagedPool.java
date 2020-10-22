package ipdemo;

import javax.jms.QueueConnectionFactory;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;


public class ManagedPool {

	static private QueueConnectionFactory privateManagedConnectionFactory=null;

	// Lookup system property "ConnectionFactory" to find out which pool we should use.
	// If not found we use the connection factory and pool defined in the default standalone.xml for JBoss / Wildfly.

	static QueueConnectionFactory getPool(InitialContext iniCtx, Logger logger) throws NamingException {
    	// Use an application server defined ActiveMQ connection pool using a JNDI name from a system property
		String cfStr=System.getProperty("ConnectionFactory");
		// If the system property is not defined use the JNDI name used by the JBoss standalone-full.xml example 
		if (cfStr==null) cfStr="/ConnectionFactory";

		Object cf = iniCtx.lookup(cfStr);
		privateManagedConnectionFactory = (QueueConnectionFactory) cf;
		logger.info("Using queue factory "+cfStr);

		return privateManagedConnectionFactory;
	}
}
