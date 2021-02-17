package ipdemo;
 
import javax.ejb.EJB;
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
/**
 * A Servlet which execute test http requests. It creates test messages in a specified payment request queue. 
 * @author Allan Smith
 *
 */ 
@WebServlet("/db")
public class IPTestServletDB extends HttpServlet {

	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");	// 2018-12-28
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
	
	@EJB
	IPDBSessionBean dbSessionBean;
	
	@Override
    public void init() throws ServletException {
    }
 
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

    	String id=req.getParameter("id");
    	String key=req.getParameter("key");
    	if (key==null) key="id";
    	
    	String data="bad parameter";
    	
    	if (id==null) 
    		data="Records "+dbSessionBean.getTXcount();
    	else {
    		id=id.replaceAll("\\*", "%");
    		if(key.equals("id") || key.equals("txid"))
    			data=dbSessionBean.getTXinfo(id,key);
    		else
    		if(key.equals("delete"))
    			data=dbSessionBean.deleteTX(id,key);
    		else
    			data="Bad key "+key+", must be id or txid";
    	}

    	PrintWriter writer=res.getWriter();

       	writer.println(dateTimeFormat.format(new Date())+"\n"+data);

    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    	doGet(req,res);
    }

}

