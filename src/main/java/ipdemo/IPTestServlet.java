package ipdemo;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
/**
 * A Servlet which executes test http requests querying data from the demo DB tables. The queries are translated
 * into callss to the DB session bean. 
 * @author Allan Smith
 *
 */
@WebServlet("/test")
public class IPTestServlet extends HttpServlet {
	private static final Logger logger = LoggerFactory.getLogger(IPTestServlet.class);

    static final long ONESEC=1000;

	public static int activeCount=0;
	public static int totalSendCount;
	public static int sendCount;

	private String defaultTemplate="pacs.008.xml"; 

	private static boolean stopFlag=false;

	@Override
	public void init() throws ServletException {
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");	// 2018-12-28
		SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
		SimpleDateFormat dateTimeFormatGMT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264

		activeCount++;
		PrintWriter writer=res.getWriter();
		String templateFile = req.getParameter("template") != null ? req.getParameter("template") : defaultTemplate;
		String valueStr = req.getParameter("value") != null ? req.getParameter("value") : "10.00";
		String countStr = req.getParameter("count");
		int cnt=1;
		try {cnt=Integer.parseInt(countStr);} catch (Exception e) {}; 
		String tpsStr = req.getParameter("tps");
		int tps=1;
		try {tps=Integer.parseInt(tpsStr);} catch (Exception e) {}; 

		String traceStr = req.getParameter("trace");
		int trace=0;
		try {trace=Integer.parseInt(traceStr);} catch (Exception e) {};
		
		String creditorBIC=req.getParameter("creditorbic");
		String debtorBIC=req.getParameter("debtorbic");
		String destinationName=req.getParameter("queue");
		if (destinationName==null) destinationName = "instantpayments_mybank_originator_payment_request";

		byte[] docText=XMLutils.getTemplate(templateFile);

		Document msgDoc = XMLutils.bytesToDoc(docText);
		if (!templateFile.equals(defaultTemplate)) msgDoc=XMLutils.bytesToDoc(XMLutils.getTemplate(templateFile));

		String resp="TestServlet: "+templateFile+" Value: "+valueStr+" Count: "+cnt+" TPS: "+tps;
		writer.println(resp+"\n");
		if (trace>0) logger.info(resp);
		if (cnt<=0) {
			if (stopFlag)
				totalSendCount=0;
			stopFlag=true;
			writer.println("Stop flag set\n");
		} else {
			stopFlag=false;
			int sendCnt=0;
			int sleepSendCount=0;
			long totalSendTime=0;
			long totalMsgSendTime=0;
			float avSend=0;
			float msgSend=0;
			if (msgDoc==null)
				writer.println("Missing document template "+templateFile+"\n");
			else {
				Context ic;
				ConnectionFactory cf;
				Connection connection = null;

				try {
					ic = new InitialContext();

					String servletHost=System.getProperty("ActiveMQhostStr");
					if (servletHost!=null) {
						logger.info("Using host "+servletHost);
						String user=System.getProperty("ActiveMQuser");
						if (user!=null) logger.info("Using user name "+user);
						String password=System.getProperty("ActiveMQpassword");
						cf=new ActiveMQConnectionFactory(user,password,servletHost);					
					} else {
						String cfStr=System.getProperty("ConnectionFactory");
						if (cfStr==null) cfStr="/ConnectionFactory";
						logger.info("Using "+cfStr);
						cf = (ConnectionFactory)ic.lookup(cfStr);
					}
					// Context lookup - JNDI name of destination must be in the ActiveMQ resource-adapter as a admin-object, or
					// it must be messaging server subsystem as a jms-queue with JBoss / wildfly embedded AMQ 
					connection = cf.createConnection();
					Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		            Queue queue;
		            try {
		            	queue = (Queue)ic.lookup(destinationName);
		        	} catch (NamingException e) {
		                queue = session.createQueue(destinationName);            	
		            }
					MessageProducer publisher = session.createProducer(queue);

					connection.start();
					logger.info("Connected - queue "+queue);
					/**
					 * A session bean that implements all DB queries for the demo. If no datasource is defined as (due to error or because no IPdatasource
					 * property is defined) then no DB is used by the demo. Queries and updates are ignored.
					 * @author Allan Smith
					 *
					 */				
					TextMessage message = session.createTextMessage();

					long startTime=System.currentTimeMillis();
                    int startTPS=1;

					for (sendCnt=0;sendCnt<cnt&&!stopFlag;sendCnt++) {
						// Set msgDoc test values...
						XMLutils.setElementValue(msgDoc,"MsgId",hashCode()+"-"+startTime);
						XMLutils.setElementValue(msgDoc,"CreDtTm",dateTimeFormat.format(new Date()));
						XMLutils.setElementValue(msgDoc,"IntrBkSttlmDt",dateFormat.format(new Date()));
						XMLutils.setElementValue(msgDoc,"TtlIntrBkSttlmAmt",valueStr);
						dateTimeFormatGMT.setTimeZone(TimeZone.getTimeZone("GMT"));	// From java 8 new Instant().toString() available
						XMLutils.setElementValue(msgDoc,"AccptncDtTm",dateTimeFormatGMT.format(new Date())+"Z");
						XMLutils.setElementValue(msgDoc,"IntrBkSttlmAmt",valueStr);
						String TxId="TX"+dateFormat.format(new Date())+" "+System.nanoTime();
						TxId=TxId.replaceAll("-", "");
						XMLutils.setElementValue(msgDoc,"TxId",TxId);
						XMLutils.setElementValue(msgDoc,"EndToEndId",TxId);
						if (debtorBIC!=null) {	// If specified, override template value
				            XMLutils.setElementValue(XMLutils.getElement(msgDoc,"InstgAgt"),"BIC",debtorBIC);           
			            	XMLutils.setElementValue(XMLutils.getElement(msgDoc,"DbtrAgt"),"BIC",debtorBIC);           
						}
						if (creditorBIC!=null)	// If specified, override template value          
			            	XMLutils.setElementValue(XMLutils.getElement(msgDoc,"CdtrAgt"),"BIC",creditorBIC);

						message.setText(XMLutils.documentToString(msgDoc));
						long now=System.currentTimeMillis();
						publisher.send(message);
						totalMsgSendTime=totalMsgSendTime+(System.currentTimeMillis()-now);
						if (trace>0 && sendCnt%trace==0) logger.info("Message sent "+sendCnt+" "+XMLutils.getElementValue(msgDoc,"CreDtTm"));
						totalSendCount++;
						sendCount++;
						sleepSendCount++;
						now=System.currentTimeMillis(); 
						long sendTime=now-startTime;
 						try{	// Sleep if TPS done and not late (sleep until next send needed for TPS)
 							int remainingTps=startTPS-sleepSendCount;
 							if (remainingTps==0) {
 								sleepSendCount=0;
        						totalSendTime=totalSendTime+sendTime;
                                if (sendTime<ONESEC)
 								    Thread.sleep(ONESEC-sendTime);
 								startTime=System.currentTimeMillis();
                                // Set startup tps to max or double
                                if (startTPS<tps) startTPS=(startTPS*2>tps)?tps:startTPS*2;
 							} else {
 	                        	long timeLeft=ONESEC-sendTime;
 								if (timeLeft>10)
 	 								Thread.sleep(timeLeft/remainingTps);	// Divide remaining TPS over time 
 	 							else
 	 								Thread.sleep(0);
 							}
						} catch (Exception e) {};
					}
					avSend=new Float(totalSendTime)/sendCnt;
					msgSend=new Float(totalMsgSendTime)/sendCnt;
					logger.info(String.format("Messges sent %d avg interval %.1f avg send %.1f total(all servlets) %d", sendCnt, avSend, msgSend,totalSendCount));

				} catch (javax.naming.NameNotFoundException e) {
					logger.error("Error: "+e);
					writer.println("Context lookup exception: "+e+"\n");
				} catch (javax.jms.IllegalStateException e) {
					logger.error("Closing: "+e);
				} catch (javax.jms.ResourceAllocationException e) {
					logger.error("Resource error "+e);
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					if (connection !=null) {
						try {
							connection.close();
						} catch (JMSException e) {
							throw new RuntimeException(e);
						}
					}
				}

			}
			writer.println(String.format("Sent %d. Av.interval %.1f ms, Av.Send %.1f ms. See log for details (%s)\n", sendCnt, avSend, msgSend, new Date()));
		}
		activeCount--;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		doGet(req,res);
	}

    public void Destroy() {
        stopFlag=true;
    }
}

