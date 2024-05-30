package com.usaid;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream; 

public class ValidateLambdaHandler implements RequestHandler<Object, String> {
	private String dburl = "jdbc:mysql://tiopdevtestdb.cx8a24s68i3t.us-east-1.rds.amazonaws.com:3306/tiopdb";
	private String dbuser = "schatterjee";
	private String dbpass = "Test!234";
	private Connection con;
//	private String source;
//	private String destination;
//	private String gtinInfo;
	private Context context;
//	private String fileName;
//	private String bucketName;
//	private int objEventCount = 0;
//	private int aggEventCount = 0;
	
	@Override
	public String handleRequest(Object event, Context context) {
		this.context = context;
	    context.getLogger().log("TIOPDocumentValidation::handleRequest ::: Start");
		//String bucketName= null;
	    
	    String source = null;
	    String destination = null;
		String gtinInfo = null;
		String fileName = null;
		String bucketName = null;
		int objEventCount = 0;
		int aggEventCount = 0;
		
		 if(event instanceof LinkedHashMap) {
		    	LinkedHashMap<String, String> mapEvent = (LinkedHashMap<String, String>) event;
		    	bucketName = mapEvent.get("bucketName");
		    	fileName = mapEvent.get("keyName");
		    	source = mapEvent.get("source");
		    	destination = mapEvent.get("destination");
		    	gtinInfo = mapEvent.get("gtinInfo");
		    	
		    	String objCount = mapEvent.get("objEventCount");
		    	if(objCount != null) objEventCount = Integer.parseInt(objCount);
		    	String aggCount = mapEvent.get("aggEventCount");
		    	if(aggCount != null) aggEventCount = Integer.parseInt(aggCount);
		    	
		    	context.getLogger().log("BucketName :: " + bucketName);
				context.getLogger().log("fileName :: " + fileName);
				context.getLogger().log("Total ObjectEvent count = "+objEventCount);
				context.getLogger().log("Total AggregationEvent count = "+aggEventCount);
				context.getLogger().log("source = "+source);
				context.getLogger().log("destination = "+destination);
				context.getLogger().log("gtinInfo = "+gtinInfo);
		   }
		
		S3Object s3object = null;
		S3ObjectInputStream inputStream = null;
		Document document;
		try {
			AmazonS3 s3client = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
					.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
			s3object = s3client.getObject(bucketName, fileName);
			inputStream = s3object.getObjectContent();
			context.getLogger().log("-----------------------------------inputStream 1 ");
			
			AmazonS3 s3client1 = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
					.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
			S3Object s3object1 = s3client1.getObject(bucketName, fileName);
			S3ObjectInputStream inputStream1 = s3object1.getObjectContent();
			context.getLogger().log("-----------------------------------inputStream 2");
			StringBuilder textBuilder = new StringBuilder();
		    try (Reader reader = new BufferedReader(new InputStreamReader
		      (inputStream1, StandardCharsets.UTF_8))) {
		        int c = 0;
		        while ((c = reader.read()) != -1) {
		            textBuilder.append((char) c);
		        }
		    }
			
			//String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			context.getLogger().log("-----------------------------------textBuilder");
			context.getLogger().log("-----------------------------------string");
			

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			document = builder.parse(inputStream);
			document.getDocumentElement().normalize();
//			context.getLogger().log("----------------------------------- closing streems 1");
//			if (inputStream != null) inputStream.close();
//			if (s3object != null) s3object.close();
			this.con = getConnection();

			// get <EPCISBody>
			NodeList listEPCISBody = document.getElementsByTagName("EPCISBody");
			for (int temp = 0; temp < listEPCISBody.getLength(); temp++) {
				Node nodeEPCISBody = listEPCISBody.item(temp);
				// context.getLogger().log("Current Element1 : " + nodeEPCISBody.getNodeName());
				if (nodeEPCISBody.getNodeType() == Node.ELEMENT_NODE
						&& nodeEPCISBody.getNodeName().equals("EPCISBody")) {
					Element elementEPCISBody = (Element) nodeEPCISBody;

					// get <EventList>
					parseEventList(elementEPCISBody, source, destination, gtinInfo, fileName, objEventCount, aggEventCount);
				}

			}	
			//Transfrom lamda call
			invokeTransfromLamda(context, fileName, textBuilder.toString());
/*			
			context.getLogger().log("-----------------------------------inputStream 2 = "+inputStream.toString());
			StringBuilder textBuilder = new StringBuilder();
		    try (Reader reader = new BufferedReader(new InputStreamReader
		      (inputStream, StandardCharsets.UTF_8))) {
		        int c = 0;
		        while ((c = reader.read()) != -1) {
		            textBuilder.append((char) c);
		        }
		    }
			
			//String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			context.getLogger().log("-----------------------------------textBuilder = "+textBuilder);
			context.getLogger().log("-----------------------------------string = "+textBuilder.toString());
			
*/			
//			if (inputStream != null) inputStream.close();
//			if (s3object != null) s3object.close();
			context.getLogger().log("Validated successfully for file '" + fileName + "' from s3 bucket '" + bucketName + "'");
			
		} catch (TIOPException e) {
			context.getLogger().log("----------------------------------- caught TIOPDocumentValidation. Email sent and logged the error.");
			return "Error while reading file from S3 :::" + e.getMessage();
		} catch (Exception e) {
			return "Error while reading file from S3 :::" + e.getMessage();
		} 
		finally {
			try {
				source = null;
				destination = null;
				//if (con != null && !con.isClosed()) con.close();
				//if (inputStream != null) inputStream.close();
				//if (s3object != null) s3object.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			context.getLogger().log("----------------------------------- finally end");
		}

		return "Validated successfully for file '" + fileName + "' from s3 bucket '" + bucketName + "'";
	}
	
	private void invokeTransfromLamda(Context context, String fileName, String fileContent) {
		//context.getLogger().log("Calling validate lamda");
		
		JSONObject payloadObject = new JSONObject();
		payloadObject.put("fileContent", fileContent);
		payloadObject.put("fileName", fileName);
		
		AWSLambda client = AWSLambdaAsyncClient.builder().withRegion(Regions.US_EAST_1).build();

		InvokeRequest request = new InvokeRequest();
		request.withFunctionName("arn:aws:lambda:us-east-1:654654535046:function:TIOPDocumentTransform").withPayload(payloadObject.toString());
		//context.getLogger().log("Calling TransfromLambdaHandler with payload = "+payloadObject.toString());
		InvokeResult invoke = client.invoke(request);
		context.getLogger().log("Result invoking TIOPDocumentValidation  == " + invoke);
	}

	private Connection getConnection() throws ClassNotFoundException, SQLException {
		if (con == null || con.isClosed()) {
			Class.forName("com.mysql.jdbc.Driver");
			con = DriverManager.getConnection(dburl, dbuser, dbpass);
		}
		return con;
	}
	
	
//	private String getContact(String sourceGlnUri) {
//		//context.getLogger().log("rdsDbTeat ::: Start");
//	 StringBuffer contactList = new StringBuffer();
//	 String query = "select distinct first_name , last_name, email  from contact c \r\n"
//	 		+ "inner join  trading_partner tp on c.partner_id =tp.partner_id\r\n"
//	 		+ "inner join location l on tp.partner_id =l.partner_id\r\n"
//	 		+ "inner join tiop_rule tr on tr.source_location_id = l.location_id\r\n"
//	 		+ "where c.current_indicator ='A' and l.current_indicator ='A'\r\n"
//	 		+ "and tr.source_gln_uri = '"+sourceGlnUri+"'";
//		try {
//			context.getLogger().log("getContact ::: Start");
//			con = getConnection();
//			context.getLogger().log("getContact ::: con = "+con);
//			Statement stmt = con.createStatement();
//			context.getLogger().log("getContact ::: query = "+query);
//			ResultSet rs = stmt.executeQuery(query);
//			boolean more= false;
//			while (rs.next()) {
//				if(more) contactList.append(",");
//				if(rs.getString(1) != null) contactList.append(rs.getString(3));
//			}
//			
//			if(gtin_uriList.isEmpty()) {
//				context.getLogger().log("<<<<<<<<<<<<< No contact found for source input.");
//			}
//			
//		} catch (Exception e) {
//			context.getLogger().log("rdsDbTeat ::: db error = " + e.getMessage());
//		}
//		context.getLogger().log("getContact ::: DB ResultSet = "+contactList);
//		return contactList.toString();
//
//	}
	
	private Set<String> getContact(String sourceGlnUri) {
		// context.getLogger().log("rdsDbTeat ::: Start");
		//StringBuffer contactList = new StringBuffer();
		Set<String> toEmailSet = new HashSet<String>();
		String query1 = "select distinct first_name , last_name, email  from contact c \r\n"
				+ "inner join  trading_partner tp on c.partner_id =tp.partner_id\r\n"
				+ "inner join location l on tp.partner_id =l.partner_id\r\n"
				+ "inner join tiop_rule tr on tr.source_location_id = l.location_id\r\n"
				+ "where c.current_indicator ='A' and l.current_indicator ='A'\r\n" + "and tr.source_gln_uri = '"
				+ sourceGlnUri + "'";
		String query = "select distinct first_name , last_name, email  \r\n"
				+ "from contact c \r\n"
				+ "inner join  trading_partner tp on c.partner_id =tp.partner_id\r\n"
				+ "inner join location sl on tp.partner_id =sl.partner_id\r\n"
				+ "inner join tiop_rule tr on tr.source_location_id = sl.location_id\r\n"
				+ "where c.current_indicator ='A' \r\n"
				+ "and tp.current_indicator ='A'\r\n"
				+ "and sl.current_indicator ='A'\r\n"
				+ "and sl.gln_uri = '"+sourceGlnUri+"'";
		try {
			//context.getLogger().log("getContact ::: Start");
			con = getConnection();
			//context.getLogger().log("getContact ::: con = " + con);
			Statement stmt = con.createStatement();
			context.getLogger().log("getContact ::: query = " + query);
			ResultSet rs = stmt.executeQuery(query);
			boolean more = false;
			while (rs.next()) {
				toEmailSet.add(rs.getString(3));
//				if (more)
//					contactList.append(",");
//				if (rs.getString(1) != null)
//					contactList.append(rs.getString(1));
//				contactList.append(" ");
//				if (rs.getString(2) != null)
//					contactList.append(rs.getString(2));
//				contactList.append("#");
//				if (rs.getString(3) != null)
//					contactList.append(rs.getString(3));
//				more = true;
			}

		} catch (Exception e) {
			context.getLogger().log("rdsDbTeat ::: db error = " + e.getMessage());
		}
		context.getLogger().log("getContact ::: DB ResultSet = " + toEmailSet);
		return toEmailSet;

	}
	
	private void insertErrorLog(int eventTypeId, String msg, String source, String destination, String gtinInfo, String fileName, int objEventCount, int aggEventCount) {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  //2024-04-05 20:31:02
		String strDate= formatter.format(date);
//		String query1 = "INSERT INTO tiop_operation (event_type_id, event_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text, exception_detail) \r\n"
//		 		+ "values ("+eventType+", -- 1 (Object Event), 2 (Aggregation Event)\r\n"
//		 		+ "null, -- <eventID>urn:uuid:b15534cf-0edd-42c4-a2b6-776bad00e811</eventID>\r\n"
//		 		+ "(select distinct stp.partner_id \r\n"
//		 		+ "from location sl \r\n"
//		 		+ "inner join trading_partner stp \r\n"
//		 		+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"+sourceGlnUri+"'),\r\n"
//		 		+ "(select distinct  dtp.partner_id\r\n"
//		 		+ "from location dl \r\n"
//		 		+ "inner join trading_partner dtp \r\n"
//		 		+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"+destination+"'),\r\n"
//		 		+ "(select distinct sl.location_id\r\n"
//		 		+ "from location sl \r\n"
//		 		+ "inner join trading_partner stp \r\n"
//		 		+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"+sourceGlnUri+"'),\r\n"
//		 		+ "(select distinct  dl.location_id \r\n"
//		 		+ "from location dl \r\n"
//		 		+ "inner join trading_partner dtp \r\n"
//		 		+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"+destination+"'),\r\n"
//		 		+ "(select distinct ti.item_id \r\n"
//		 		+ "from trade_item ti where ti.current_indicator='A' and gtin_uri ='"+gtinUri+"'),\r\n"
//		 		+ "(select tr.rule_id from \r\n"
//		 		+ "tiop_rule tr \r\n"
//		 		+ "inner join tiop_status ts \r\n"
//		 		+ "ON tr.status_id = ts.status_id \r\n"
//		 		+ "where \r\n"
//		 		+ "tr.source_gln_uri = '"+sourceGlnUri+"' \r\n"
//		 		+ "and tr.destination_gln ='"+destination+"'\r\n"
//		 		+ "and tr.gtin_uri ='"+gtinUri+"'\r\n"
//		 		+ "and ts.status_description ='Active'),"
//		 		+ errorType+ ", -- validation --\r\n"
//		 		+ "'"+strDate+"',\r\n"
//		 		+ "'tiop_validation', -- id that insert data in tiopdb\r\n"
//		 		+ "'"+strDate+"',\r\n"
//		 		+ "'"+modifiedBy+"', -- id that insert data in tiopdb\r\n"
//		 		+ "'A',\r\n"
//		 		+ "'',\r\n"
//		 		+ "'"+ msg+"')";
		
		String query = "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text) \r\n"
				+ "VALUES (\r\n"
				+ eventTypeId+", -- 1 (Object Event), 2 (Aggregation Event)\r\n"
				+ "(select distinct stp.partner_id\r\n"
				+ "from location sl\r\n"
				+ "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"+source+"'),\r\n"
				+ "(select distinct  dtp.partner_id\r\n"
				+ "from location dl\r\n"
				+ "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"+destination+"'),\r\n"
				+ "(select distinct sl.location_id\r\n"
				+ "from location sl\r\n"
				+ "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"+source+"'),\r\n"
				+ "(select distinct  dl.location_id\r\n"
				+ "from location dl\r\n"
				+ "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"+destination+"'),\r\n"
				+ "(select distinct ti.item_id\r\n"
				+ "from trade_item ti where ti.current_indicator='A' and gtin_uri ='"+gtinInfo+"'),\r\n"
				+ "(select tr.rule_id from \r\n"
				+ "tiop_rule tr \r\n"
				+ "inner join tiop_status ts \r\n"
				+ "ON tr.status_id = ts.status_id \r\n"
				+ "inner join location sl\r\n"
				+ "on tr.source_location_id = sl.location_id \r\n"
				+ "inner join location dl \r\n"
				+ "on tr.destination_location_id = dl.location_id\r\n"
				+ "inner join trade_item ti \r\n"
				+ "on tr.item_id =ti.item_id \r\n"
				+ "where\r\n"
				+ "ts.status_description ='Active'\r\n"
				+ "and sl.current_indicator ='A'\r\n"
				+ "and dl.current_indicator ='A'\r\n"
				+ "and ti.current_indicator ='A'\r\n"
				+ "and sl.gln_uri = '"+source+"' \r\n"
				+ "and dl.gln = '"+destination+"'\r\n"
				+ "and ti.gtin_uri ='"+gtinInfo+"'),\r\n"
				+ "4, -- validation failed --\r\n"
				+ "'"+fileName+"' , -- the document name (jjoshi/4K_events_05062024.xml)\r\n"
				+ objEventCount+", -- Object event counts\r\n"
				+ aggEventCount+",  -- Aggregation event counts\r\n"
				+ "'"+msg+"', -- Exception detail\r\n"
				+ "'"+strDate+"',\r\n"
				+ "'tiop_validation', -- id that insert data in tiopdb\r\n"
				+ "'"+strDate+"',\r\n"
				+ "'tiop_validation', -- id that insert data in tiopdb\r\n"
				+ "'A',\r\n"
				+ "'');";
		try {
			//context.getLogger().log("insertErrorLog ::: Start");
			con = getConnection();
			//context.getLogger().log("insertErrorLog ::: con = "+con);
			Statement stmt = con.createStatement();
			context.getLogger().log("insertErrorLog ::: query = "+query);
			stmt.executeUpdate(query);
			context.getLogger().log("insertErrorLog ::: query inserted successfully");
		} catch (Exception e) {
			context.getLogger().log("insertErrorLog ::: db error = " + e.getMessage());
		}
	}



	private  void parseEventList(Element elementEPCISBody, String source, String destination, String gtinInfo, String fileName, int objEventCount, int aggEventCount) throws TIOPException {
		NodeList listEventList = elementEPCISBody.getElementsByTagName("EventList");
		for (int temp = 0; temp < listEventList.getLength(); temp++) {
			Node nodeEventList = listEventList.item(temp);
			//context.getLogger().log("Current Element2 : " + nodeEventList.getNodeName());

			if (nodeEventList.getNodeType() == Node.ELEMENT_NODE && nodeEventList.getNodeName().equals("EventList")) {
				Element elementEventList = (Element) nodeEventList;

				// get <ObjectEvent>
				try {
					parseObjectEvent(elementEventList, source, destination, gtinInfo, fileName, objEventCount, aggEventCount);
				} catch (TIOPException e) {
					context.getLogger().log("----------------------------------- caught TIOPAuthException at parseObjectEvent ");
					context.getLogger().log("Sending error email and insert error log in db for error = "+e.getMessage());
					TIOPValidationSendEmail.sendMail(context, 1, getContact(source), fileName, e.getMessage());
					String msg = e.getMessage();
					if(msg.contains("#")) {
						msg = (msg.split("#"))[1];
					}
					insertErrorLog(1, msg, source, destination, gtinInfo, fileName, objEventCount, aggEventCount);
					throw new TIOPException(msg);
				}
				
				try {
					parseAggregationEventt(elementEventList, source, destination, gtinInfo, fileName, objEventCount, aggEventCount);
				} catch (TIOPException e) {
					context.getLogger().log("----------------------------------- caught TIOPAuthException at parseAggregationEventt ");
					context.getLogger().log("Sending error email and insert error log in db for error = "+e.getMessage());
					TIOPValidationSendEmail.sendMail(context, 1, getContact(source), fileName, e.getMessage());
					String msg = e.getMessage();
					if(msg.contains("#")) {
						msg = (msg.split("#"))[1];
					}
					insertErrorLog(2, msg, source, destination, gtinInfo, fileName, objEventCount, aggEventCount);
					throw new TIOPException(msg);
				}

			}

		}
	}

	private  void parseAggregationEventt(Element elementEventList, String source, String destination, String gtinInfo, String fileName, int objEventCount, int aggEventCount) throws TIOPException {
		NodeList listAggregationEvent = elementEventList.getElementsByTagName("AggregationEvent");
		for (int temp = 0; temp < listAggregationEvent.getLength(); temp++) {
			Node nodeAggregationEvent = listAggregationEvent.item(temp);
			//context.getLogger().log("Current Element3 : " + nodeAggregationEvent.getNodeName());
			if (nodeAggregationEvent.getNodeType() == Node.ELEMENT_NODE
					&& nodeAggregationEvent.getNodeName().equals("AggregationEvent")) {
				Element elementAggregationEvent = (Element) nodeAggregationEvent;
				
//				if(source == null) {
//					source = extractGN(getSource(elementAggregationEvent));
//					context.getLogger().log("Current source in AE== " + source);
//			    } 
//				
//				
//				if(destination == null) {
//					destination = getSimpleNode(elementAggregationEvent, "tiopvoc:nts_gln");
//					context.getLogger().log("Current destination in AE== " + destination);
//			    } 
				
				
//				Set<String> childEPCsSet = parseEPCList(elementAggregationEvent, "childEPCs");
				//context.getLogger().log("----- parseAggregationEventt::childEPCsSet = " + childEPCsSet);
//				for(String st: childEPCsSet) {
//					gtin = st;
//				}
				
				String eventTime = getSimpleNode(elementAggregationEvent, "eventTime");
				if(eventTime == null || eventTime.isBlank()) throw new TIOPException("EXC002#Event Time is blank or missing");
				
				String eventTimeZoneOffset = getSimpleNode(elementAggregationEvent, "eventTimeZoneOffset");
				if(eventTimeZoneOffset == null || eventTimeZoneOffset.isBlank()) throw new TIOPException("EXC003#Event Time ZoneOffset is blank or missing");
				
				String bizStep = getSimpleNode(elementAggregationEvent, "bizStep");
				if(bizStep == null || bizStep.isBlank()) throw new TIOPException("EXC004#Event BizStep is blank or missing");
				
				Set<String> parentIDSet = new HashSet<String>();
				String parentID = getSimpleNode(elementAggregationEvent, "parentID");
				if(parentID == null || parentID.isBlank()) throw new TIOPException("EXC005#Event ParentID is blank or missing");
				
				validateLocation(elementAggregationEvent);
				
			}
		}
		context.getLogger().log("Total AggregationEvent count = "+listAggregationEvent.getLength());
		
	}
	

	private  void parseObjectEvent(Element elementEventList, String source, String destination, String gtinInfo, String fileName, int objEventCount, int aggEventCount) throws TIOPException {
		NodeList listObjectEvent = elementEventList.getElementsByTagName("ObjectEvent");
		for (int temp = 0; temp < listObjectEvent.getLength(); temp++) {
			Node nodeObjectEvent = listObjectEvent.item(temp);
			//context.getLogger().log("Current Element3 : " + nodeObjectEvent.getNodeName());
			if (nodeObjectEvent.getNodeType() == Node.ELEMENT_NODE
					&& nodeObjectEvent.getNodeName().equals("ObjectEvent")) {
				Element elementObjectEvent = (Element) nodeObjectEvent;
				
//				if(source == null) {
//					source = extractGN(getSource(elementObjectEvent));
//					context.getLogger().log("Current source in OE== " + source);
//			    } 
//				
//				
//				if(destination == null) {
//					destination = getSimpleNode(elementObjectEvent, "tiopvoc:nts_gln");
//					context.getLogger().log("Current destination in OE== " + destination);
//			    } 
				
				
//				Set<String> epcSet = parseEPCList(elementObjectEvent, "epcList");
				//context.getLogger().log("----- parseObjectEvent::gtinList = " + epcSet);
//				for(String st: epcSet) {
//					gtin = st;
//				}
				
				String eventTime = getSimpleNode(elementObjectEvent, "eventTime");
				if(eventTime == null || eventTime.isBlank()) throw new TIOPException("EXC002#Event Time is blank or missing");
				
				String eventTimeZoneOffset = getSimpleNode(elementObjectEvent, "eventTimeZoneOffset");
				if(eventTimeZoneOffset == null || eventTimeZoneOffset.isBlank()) throw new TIOPException("EXC003#Event Time ZoneOffset is blank or missing");
				
				String bizStep = getSimpleNode(elementObjectEvent, "bizStep");
				if(bizStep == null || bizStep.isBlank()) throw new TIOPException("EXC004#Event BizStep is blank or missing");
				
				validateLocation(elementObjectEvent);
								
			}
		}
		context.getLogger().log("Total ObjectEvent count = "+listObjectEvent.getLength());
	}
	
//	private  Set<String> parseEPCList(Element elementObjectEvent, String nodeName) {
//		Set<String> gtinSet = new HashSet<String>();
//		NodeList listEPCList = elementObjectEvent.getElementsByTagName(nodeName);
//		for (int temp = 0; temp < listEPCList.getLength(); temp++) {
//			Node nodeEPCList = listEPCList.item(temp);
//			// context.getLogger().log("Current Element : " + nodeEPCList.getNodeName());
//			if (nodeEPCList.getNodeType() == Node.ELEMENT_NODE && nodeEPCList.getNodeName().equals(nodeName)) {
//				Element elementEPCList = (Element) nodeEPCList;
//
//				NodeList listEPC = elementObjectEvent.getElementsByTagName("epc");
//				// context.getLogger().log("epc length : " + listEPC.getLength());
//				for (int j = 0; j < listEPC.getLength(); j++) {
//					String epc = elementEPCList.getElementsByTagName("epc").item(j).getTextContent();
//					//context.getLogger().log("epc uri [" + j + "] = " + epc);
//					if (epc.contains("urn:epc:id:sgtin")) {
//						// context.getLogger().log("sgtin :::> " + epc.substring(0, epc.lastIndexOf('.')));
//						gtinSet.add(epc.substring(0, epc.lastIndexOf('.')));
//					}
//				}
//			}
//		}
//		return gtinSet;
//	}
	
	private void validateLocation(Element elementObjectEvent) throws TIOPException {
		String bizStep = getSimpleNode(elementObjectEvent, "bizStep");
		if(bizStep.contains("packing")) {
			Set<String> locationSet = geLocationFromExtension(elementObjectEvent);
			context.getLogger().log("locationSet ==== "+locationSet);
			if(locationSet == null || locationSet.isEmpty()) throw new TIOPException("EXC006#Recipient GLN (physical ship-to location) is blank or missing");
		}
		
	}
	
	private Set<String> geLocationFromExtension(Element elementObjectEvent) {
		Set<String> locSet = new HashSet<String>();
		NodeList listExtension = elementObjectEvent.getElementsByTagName("extension");
		for (int temp = 0; temp < listExtension.getLength(); temp++) {
			Node nodeExtension = listExtension.item(temp);
			// context.getLogger().log("Current Element : " + nodeEPCList.getNodeName());
			if (nodeExtension.getNodeType() == Node.ELEMENT_NODE && nodeExtension.getNodeName().equals("extension")) {
				Element elementExtension = (Element) nodeExtension;
				String location = parseLocation(elementExtension);
				if(location != null && !location.isBlank()) locSet.add(location);
			}
		}
		return locSet;
	}

	private String parseLocation(Element elementExtension) {
		String location = null;
		NodeList listDestinationList = elementExtension.getElementsByTagName("destinationList");
		for (int temp = 0; temp < listDestinationList.getLength(); temp++) {
			Node nodeDestinationList = listDestinationList.item(temp);
			// context.getLogger().log("Current Element : " + nodeDestinationList.getNodeName());
			if (nodeDestinationList.getNodeType() == Node.ELEMENT_NODE
					&& nodeDestinationList.getNodeName().equals("destinationList")) {
				Element elementDestinationList = (Element) nodeDestinationList;
				NodeList lisDestination = elementDestinationList.getElementsByTagName("destination");
				for (int j = 0; j < lisDestination.getLength(); j++) {
					location = elementDestinationList.getElementsByTagName("destination").item(j).getTextContent();
				}
			}
		}
		return location;
	}

	private  String getSimpleNode(Element elementObjectEvent, String nodeName) {
		String node = null;
		try {
			if(elementObjectEvent.getElementsByTagName(nodeName) != null) {
				if(elementObjectEvent.getElementsByTagName(nodeName).getLength() > 0) {
					node = elementObjectEvent.getElementsByTagName(nodeName).item(0).getTextContent();
				}
			}
		} catch (Exception e) {
			context.getLogger().log("error ----------------- "+e.getMessage());
			e.printStackTrace();
		}
		
		//context.getLogger().log("Current "+ nodeName+" == " + node);
		return node;
	}

//	private  String getSource(Element elementObjectEvent) {
//		String source = null;
//		NodeList nodelistBizLocation = elementObjectEvent.getElementsByTagName("bizLocation");
//		for (int temp = 0; temp < nodelistBizLocation.getLength(); temp++) {
//			Node nodeBizLocation = nodelistBizLocation.item(temp);
//			// context.getLogger().log("Current Element : " + nodeEPCList.getNodeName());
//			if (nodeBizLocation.getNodeType() == Node.ELEMENT_NODE && nodeBizLocation.getNodeName().equals("bizLocation")) {
//				Element elementBizLocation = (Element) nodeBizLocation;
//				source = elementBizLocation.getElementsByTagName("id").item(0).getTextContent();
//			}
//		}
//		return source;
//	}
//	
//
//
//
//	private  String extractGN(String gn) {
//		if(gn != null) {
//			String last = gn.substring(gn.lastIndexOf('.'), gn.length());
//			if (!last.contains(".0")) {
//				gn = gn.substring(0, gn.lastIndexOf('.'));
//			}
//		}
//		return gn;
//	}

}
