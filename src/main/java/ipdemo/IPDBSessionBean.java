package ipdemo;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Stateless;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
/**
 * A session bean that implements all DB queries for the demo. If no datasource is defined as (due to error or because no IPdatasource
 * property is defined) then no DB is used by the demo. Queries and updates are ignored.
 * @author Allan Smith
 *
 */
@Stateless
public class IPDBSessionBean  {
	private static final Logger logger = LoggerFactory.getLogger(IPDBSessionBean.class);
	
	DataSource ds;
	
	static final String datasourceProperty="IPdatasource";
	
	public final static String requestType="request";
	public final static String responseType="response";
	public final static String confirmationType="confirmation";

	public void insertTX(String id,String txid,String type,String status, String reason, String msg) {
		Connection con=null;
    	if (ds!=null) {
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.

				Statement stmt = con.createStatement();
				//(id,txid,type,datetime,status,reason,msg)
				String sql = "INSERT INTO TXTABLE " +
						"VALUES ('"+id+"','"+txid+"','"+type+"',NOW(),'"+status+"','"+reason+"','"+msg+"')";
				stmt.executeUpdate(sql);
				stmt.close();
			} catch (SQLException e) {
				logger.error("Insert error " + e);
			} finally {
				if (con != null)
					try {
						con.close();
					} catch (Exception e) {
						logger.error("Error on close " + e);
					}
				;
			}
		}
	}

	public TXstatus[] getTXstatus(String id) {
		return getTXstatus(id,"ID");
	}
	public TXstatus[] getTXstatus(String id, String key) {
		TXstatus data[]=null;
    	long startTime=System.currentTimeMillis();
    	Connection con = null;
    	Statement st = null;
    	if (ds!=null) {
			try {
				con = ds.getConnection();
				st = con.createStatement();
				String sql;
				if (!key.equalsIgnoreCase("ID")&&!key.equalsIgnoreCase("ID"))
					key="ID";
				sql = "SELECT TYPE,ID,TXID,DATETIME,STATUS,REASON FROM TXTABLE WHERE " + key + " = '" + id + "'";
				ResultSet rs = st.executeQuery(sql);
				
		        java.util.ArrayList<TXstatus> dataList =
                        new java.util.ArrayList<TXstatus>();
				int cnt = 0;
				while (rs.next()) {
					cnt++;
					TXstatus row=new TXstatus();
					row.type = rs.getString("TYPE");
					row.id = rs.getString("ID");
					row.txid =	rs.getString("TXID");
					row.datetime = rs.getDate("DATETIME");
					row.status = rs.getString("STATUS");
					row.reason = rs.getString("REASON");
					dataList.add(row);
				}
				if (cnt > 0) {
					data=new TXstatus[dataList.size()];
					for (int i=0;i<data.length;i++) data[i]=dataList.get(i);
				}
				rs.close();
				st.close();
			} catch (SQLException e) {
				logger.error("Query error " + e);
			} finally {
				try {
					if (st!=null) st.close();
					if (con!=null) con.close();
				} catch (SQLException e) {};
				long queryTime=System.currentTimeMillis()-startTime;
				if (queryTime>500) logger.warn("Long query " + queryTime + " ms");
			}
		}
		return data;
	}
	
    public String getTXinfo(String id) {
    	return getTXinfo(id,"ID");
    }
    public String getTXinfo(String id, String key) {
    	long startTime=System.currentTimeMillis();
    	String data=null;
    	Connection con = null;
    	Statement st = null;
    	if (ds!=null) {
			try {
				con = ds.getConnection();
				st = con.createStatement();
				String sql;
				if (!key.equalsIgnoreCase("ID")&&!key.equalsIgnoreCase("ID"))
					key="ID";
				if (id.equals("%"))
					sql = "SELECT * FROM TXTABLE WHERE txid IN (SELECT txid FROM TXTABLE WHERE datetime = SELECT max(datetime) FROM TXTABLE)";
				else if (id.contains("%"))
					sql = "SELECT * FROM TXTABLE WHERE " + key + " LIKE '" + id + "' LIMIT 100";
				else
					sql = "SELECT * FROM TXTABLE WHERE " + key + " = '" + id + "'";
				ResultSet rs = st.executeQuery(sql);
				data = "";
				int cnt = 0;
				while (rs.next()) {
					cnt++;
					data = data + "\n" +
							rs.getString("TYPE") + "\n" +
							rs.getString("ID") + "\n" +
							rs.getString("TXID") + "\n" +
							rs.getString("STATUS") + "\n" +
							rs.getString("REASON") + "\n" +
							rs.getString("DATETIME") + "\n" +
							rs.getString("MSG");
				}
				rs.close();
				st.close();
				if (cnt > 0) data = data + "\nCount " + cnt;
				else
					data=null;
			} catch (SQLException e) {
				logger.error("Query error " + e);
			} finally {
				try {
					if (st!=null) st.close();
					if (con!=null) con.close();
				} catch (SQLException e) {};
				long queryTime=System.currentTimeMillis()-startTime;
				if (queryTime>500) logger.warn("Long query " + queryTime + " ms");
			}
		}
        return data;
    }
    
    public String deleteTX(String id, String key) {
    	long startTime=System.currentTimeMillis();
    	String data=null;
    	Connection con = null;
    	Statement st = null;
    	if (ds!=null) {
			try {
				con = ds.getConnection();
				st = con.createStatement();
				String sql;
				if (id.equals("%")) {
					sql = "TRUNCATE TABLE TXTABLE";
					data="Truncated "+getTXcount(); 
					st.executeUpdate(sql);
				} else {
					if (!key.equalsIgnoreCase("ID")&&!key.equalsIgnoreCase("ID"))
						key="ID";
					sql = "DELETE FROM TXTABLE WHERE "+key+" LIKE '" + id + "'";
					st.executeUpdate(sql);
					data = "Updated " + st.getUpdateCount();
				}
			} catch (SQLException e) {
				logger.error("Query error " + e);
			} finally {
				try {
					if (st!=null) st.close();
					if (con!=null) con.close();
				} catch (SQLException e) {};
				long queryTime=System.currentTimeMillis()-startTime;
				if (queryTime>500) logger.warn("Long delete " + queryTime + " ms");
			}
		}
        return data;
    }
    
    public int getTXcount() {
    	int res=0;
    	Connection con=null;
		if (ds!=null)
        try {
        	con = ds.getConnection();	// Connect using datasource username/pwd etc.

            Statement st = con.createStatement();
            String sql="SELECT COUNT(*) FROM TXTABLE";
            ResultSet rs = st.executeQuery(sql);

            if (rs.next()) {
            	res=rs.getInt(1);
            }
            rs.close();
            st.close();
        } catch (SQLException e) {
	       	logger.error("Count error "+e);
        } finally {
        	if (con!=null)
        	try{
        		con.close();
        	} catch (Exception e) {
        		logger.error("Error on close "+e);
        	};
        } 
        return res;
    }
 
    @PostConstruct
    public void initialize () {
        // Initialize here objects which will be used
        // by the session bean
    	Connection con=null;
        try {
        	InitialContext ctx= new InitialContext();
        	// Get data source (Wildfly naming subsystem <bindings>:
        	// <simple name="java:global/IPdatasource" value="jboss/datasources/ExampleDS"/>
			String datasourceStr=System.getProperty(datasourceProperty);
			if (datasourceStr==null) datasourceStr=(String)ctx.lookup("/global/"+datasourceProperty);	
        	ds=(DataSource)ctx.lookup(datasourceStr);// Example: java:jboss/datasources/ExampleDS
        	// String url = "jdbc:h2:mem:test";
        	// con = DriverManager.getConnection(url,"sa","sa");
        	con = ds.getConnection();	// Connect using datasource username/pwd etc.
        	logger.info("Connected.");
        	Statement stmt = con.createStatement();
            String sql = "CREATE TABLE TXTABLE " +
                         "(id VARCHAR(255), " +
                         " txid VARCHAR(255), " + 
                         " type VARCHAR(255), " + 
                         " datetime DATETIME, " + 
                         " status VARCHAR(4), " +
                         " reason VARCHAR(4), " +
                         " msg VARCHAR(4096), " + 
                         " PRIMARY KEY ( id ))"; 
            stmt.executeUpdate(sql);
            stmt.executeUpdate("CREATE INDEX dtIndex ON TXTABLE (datetime)");
            stmt.executeUpdate("CREATE INDEX txidIndex ON TXTABLE (txid)");
            stmt.close();
        	logger.info("Initialized - table created.");

        } catch (SQLException e) {
        	if (e.toString().contains("already exists")) {
				logger.debug("Initialized - table already present.");
			} else {
				logger.error("Initialization error " + e);
			}
        } catch (NamingException ce) {
        	logger.warn("Datasource not defined "+ce);
        } finally {
        	if (con!=null) {
				try {
					con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
				}
			}
        }
    }
 
    @PreDestroy
    public void destroyBean() {
        // Free here resources acquired by the session bean
        logger.info("Destroyed.");
    } 
}

class TXstatus {
	String id;
	String type;
	String txid;
	Date datetime;
	String status;
	String reason;
}
