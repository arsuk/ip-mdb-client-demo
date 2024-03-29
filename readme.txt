The IP MDB based interfacing demonstration
==========================================
The ip-mdb-demo is a simulation of a client system that interacts with the IP CSM. It provides a coding example for the
developers of client implementations.
It is also a basic test framework that uses the same interfaces that we recommend customers to use. One deployment of
this application simulates one test bank. You can define more MDBs and queue names to simulate more than one bank.

Example standalome.xml files are included showing how use the war file wuth Wildfly/JBoss in a standalone mode 
in-vm JMS messaging (myfull) or with external ActiveMQ (amq) or with external ActiveMQ Artemis (artemis) and also
varuiations to use a an external DB data source (h2). 

It consists of servlets for creating the payment messages and showing the stats of the resulting messages, and message
driven beans (MDBs) that simulate the client business processes. These are deployed within an instance of Wildfly. The 
queue names that the beans listen can be defined in the ip-mdb-demo-x.x.war file ejb-jar.xml file. The ActiveMQ connections
for queues that have to be looked up by the application (send queues) can be defined in the Wildfly or JBoss 
standalone-amq.xml configuration file. These are:
1) instantpayments_mybank_originator_payment_request (lookup in the IPTestServlet)
2) instantpayments_mybank_beneficiary_payment_response (lookup in the IPBeneficiaryRequestBean)
3) instantpayments_mybank_echo_response (lookup in the IPEchoRequestBean)
If the filenames are not mapped in the configuration files then the 'mybank' names are used as shown below. It is possible
to define multiple sets of beans to simulate multiple banks if so desired.

The process flow is as follows:

IPTestServlet -------------------> instantpayments_mybank_originator_payment_request -----------> IP CSM

IPBeneficiaryRequestBean <-------- instantpayments_mybank_beneficiary_payment_request <---------- IP CSM
           |
           |---------------------> instantpayments_mybank_beneficiary_payment_response ---------> IP CSM

IPoriginatorResponseBean <-------- instantpayments_mybank_originator_payment_response <---------- IP CSM

IPBeneficiaryConfirmation <------- instantpayments_mybank_beneficiary_payment_confirmation <----- IP CSM

Note that the queue names used are configurable as described below in the 'Running the demo' section.

The IPtestServlet send pacs.008.xml messages test bank beneficiary request queue. Instant Payments will validate the
message and route it to the requested beneficiary queue. The IPbeneficiaryRequestBean if the beneficiary test bank
will read them. It will then create the pacs.002.xml response message using a template and it will copy the originator
fields to the pacs.008 which will then be send to the beneficiary response queue. Instant Payments will receive the
pacs.002.xml and after validation forward it to the originator response queue. If this test bank is the originator then it
should get the response in the IPOriginatorResponseBean. The beneficiary test bank will get a confirmation on the
confirmation queue in the IPbeneficiaryConfirmation bean.
The pacs.008 must use the debtor BIC that Instant Payments expects (belonging to the test bank). The pacs.002 must use the
creditor BIC of the test bank. See the next section for details. If you use the same test bank for the originator and
beneficiary then you only need one BIC and one copy of the test code running. if you want to run two test banks you need
to run two instances of Wildfy/JBoss and configure then with different queue names and BICs.

The package also includes an IPechoRresponseBean. It response to a CSM's echo request with a response message indicating
that the test bank is active.

IPEchoRequestBean <--------------- instantpayments_mybank_echo_request <----------------- CSM
            |
            |--------------------> instantpayments_mybank_echo_response ----------------> CSM

The IPEchoRequestBean assumes that the response queue has the same name as the MDB input queue but with 'request' replaced
with 'response' which allows for multiple bank MDBs and IDs within the demo.
The example can be run without Instant Payments to simulate and test the queue interactions in a stand alone mode. To do
this you define in the standalone-amq.xml file that the originator request queue's physical name is the beneficiary queue.
In this way we bypass the originator request queue and the IPservlet will send its message directly to the 
IPbeneficiaryRequestBean. The beneficiary response queue must similarly entry in the standalone-amq.xml must be defined
with the originator response queue so that the messages are directly send to the IPoriginatorResponseBean.
  
IPtestServlet -------------------> instantpayments_mybank_originator_payment_request
                                                   |
                                                   | Renamed in standalone JNDI definition
                                                   V
IPbeneficiaryRequestBean <-------- instantpayments_mybank_beneficiary_payment_request
           |
           |---------------------> instantpayments_mybank_beneficiary_payment_response
                                                   |
                                                   | Renamed in standalone JNDI definition
                                                   V
IPoriginatorResponseBean <-------- instantpayments_mybank_originator_payment_response 

pacs template files
===================
The pacs.008.xml template should be modified to use the BIC of the test bank and the BIC of the creditor bank. The message
ID and transaction ID will be replaced by the test servlet while creating test messages. The servlet allows you to define a
template parameter so you can have differing versions for a number of beneficiary banks or to test other IP options.
The pacs.002.xml template must be configured with the test bank's BIC.

After Instant Payments has forwarded the pacs.008 to the test
bank beneficiary request queue the IPbeneficiaryRequestBean will read it. It will then create the pacs.002.xml response
using the template and it will copy the originator fields to the pacs.008 which wll be send to the beneficiary response
queue. The pacs.008 must use the debtor BIC that Instant Payments expects and the pacs.002 must use the creditor BIC of
the creditor bank. See the template files and their XML comments.

IPDBsessionBean
=============
The IPoriginatorRequestBean calls the DBsessionBean to check is incoming messages are unique. It will do this only if
a database resource is defined. This is set with the JBoss/Wildfly naming variable global/IPdatasorce in the example
standalone-amq.xml file.
There is a an example datasource configured using the H2 database server supplied with JBoss/Wildfly.

IPTestServlet
=============
The servlet creates one or more payment messages. The XML is read from the pacs.008.xml template. It adjusts dates, the
message IDs and transacionIDs with unique values. The transaction can be provided as a parameter along with the number of
messages wanted (the count) and the TPS rate at which they should be submitted. The request queue name and BICs can also
be adjusted by the servlet. The servlet has a parameter for selecting alternative template files so you can test with
different data values for items that the servlet has no parameters.

IPStatsServlet
==============
This servlet displays the current counts of messages received by the IPOriginatorResponseBean and IPbeneficiaryConfirmation
beans.

IPBeneficiaryRequestBean
========================
This bean reads the originator pacs.008 message and responds with a pacs.002 message. The status will be accept if the value
is less than or equal to 100 and reject if it is greater than 100.

IPOriginatorResponseBean
========================
This bean simply reads the response messages and increments a count of messages. It also logs the count for the last 10
seconds in the
Wildfly server.log if there are messages to count.
The messages can be logged if the environment variable global/IPoriginatorLog is set with a file name in the standalone-amq.xml file.

IPBeneficiaryConfirmation
=========================
This bean simply reads the confirmation messages and increments a count of messages. It also logs the count for the last 10
seconds in the Wildfly server.log if there are messages to count.
The messages can be logged if the JBoss/Wildfly naming variable global/IPconfirmationLog is set with a file name in the
standalone-amq.xml file.

Running the demo
----------------
First the standalone-amq.xml file has been adjusted with the required ActiveMQ broker address, user name and password, and the
ejb-jar.xml has been adjusted to match the queue names you want. The ip-mdb-demo-x.x.war file that contains the ejb-jar.xml
is the the Wildfly subdirectory standalone/deployments. You need to replace the XML using an archive manager. The standalone-amq.xml
file is in the subdirectory standalone/configuration.
To run the Wildfly server you simply have to run the 'start' command file in the wildfly directory.
The test can be started using the html page http://host:port/ip-mdb-demo-1.0/iptest.html and the stats can be read with
http://host:port/ip-mdb-demo-1.0/ipstats.html. A menu is provided in the context index.html (http://localhost:8080 /ip-mdb-demo-1.0).
You can also address the servlet directly. This allows you to use the curl command or other tools for initiating transactions.
For example:
http://localhost:8080/ip-mdb-demo-1.0/test/?count=1000&tps=10&value=50
And to see the stats:
http://localhost:8080/ip-mdb-demo-1.0/stats

The demo can be configured to run with a database for tracking transactions. It also has log file options. These are configured 
using system variables and resource definitions in the standalone.xml.

System Property Settings
------------------------
The following system properties are available for test purposes like problem / timeout simulation:
    <property name="IPdatasource" value="jboss/datasources/ExampleDS"/>	# Data source, for example the H2 default Wildfly DB service
    # Note that the data source is used to log transaction in H2. If this is not wanted set the IPdatasource to an undefined one (e.g. none)
    <property name="IPRequestDelay" value="0"/>		# Slow down request processing (in ms)
    <property name="IPResponseDelay" value="0"/>	# Slow down response processing (in ms)
    <property name="ActiveMQhostStr" value="tcp://localhost:61618"/> # If set, avoid using the Wildfly connection pool for message send
    # Note that there are also ActiveMQuser and ActiveMQpassword system variables but these are not usually necessary  
    <property name="ConnectionFactory" value="/jms/ConnectionFactory"/>	# ActimeMQ connection pool for message sends
    # The above is the name of the example connection pool provided by Wildfly.
System properties can be set in a standalone.xml file or as parameters on the command line invoking Wildfly. Wildfly standalone examples
are provided for ActiveMQ (amq), ActiveMQ Artemis (artemis), InVm queues (myfull), with and without an external H2 server. 
