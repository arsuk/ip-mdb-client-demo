package ipdemo;
 
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.w3c.dom.*;

import javax.xml.parsers.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
 
@WebServlet("/stats")
public class IPTestServletStats extends HttpServlet {

	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");	// 2018-12-28
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264

    private static long lastTime=0;
    private static long lastSendCount=0;
    private static long lastBeneficiaryCount=0;
    private static long lastOriginatorCount=0;
    private static long lastErrorCount=0;
    private static long lastLateCount=0;

	private PrintWriter writer;
	
	@Override
    public void init() throws ServletException {
    }
 
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    	
    	writer=res.getWriter();
       
                
        long now=System.currentTimeMillis();
        long diffSecs=(now-lastTime)/1000;
        if (diffSecs<1) diffSecs=1;
        long sendDiff=IPTestServlet.totalSendCount-lastSendCount;
        long origDiff=IPOriginatorResponseBean.totalCount-lastOriginatorCount;
        long beneDiff=IPBeneficiaryConfirmationBean.totalCount-lastBeneficiaryCount;

       	writer.println(
       			dateTimeFormat.format(new Date())+", Active "+IPTestServlet.activeCount+
       			", Interval "+(now-lastTime)+"ms"+
       			", Servlet Originator count "+sendDiff);
       	writer.println(
       			"Originator Response count "+origDiff+
       			", Av.Resp "+(IPOriginatorResponseBean.averageResponse)+
       			", Errors "+(IPOriginatorResponseBean.totalErrorCount-lastErrorCount)+
       			", Late "+(IPOriginatorResponseBean.totalLateCount-lastLateCount)+
       			", TPS "+String.format("%.1f", (float)origDiff/diffSecs));
       	writer.println(       	
        		"Beneneficiary Confirmation count "+beneDiff+
        		", Av.Resp "+(IPBeneficiaryConfirmationBean.averageResponse)+
       			", TPS "+String.format("%.1f", (float)beneDiff/diffSecs));
       	writer.println("\nTotals: responses "+IPOriginatorResponseBean.totalCount+
       			", confirmations "+IPBeneficiaryConfirmationBean.totalCount+
       			", errors "+IPOriginatorResponseBean.totalErrorCount+
       			", late "+IPOriginatorResponseBean.totalLateCount);

       	lastSendCount=IPTestServlet.totalSendCount;
       	lastBeneficiaryCount=IPBeneficiaryConfirmationBean.totalCount;
        lastOriginatorCount=IPOriginatorResponseBean.totalCount;
       	lastTime=System.currentTimeMillis();
       	lastErrorCount=IPOriginatorResponseBean.totalErrorCount;
       	lastLateCount=IPOriginatorResponseBean.totalLateCount;
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    	doGet(req,res);
    }

}

