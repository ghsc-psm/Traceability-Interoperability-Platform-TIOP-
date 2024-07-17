package com.usaid;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream; 

public class ValidateLambdaHandler implements RequestHandler<Object, String> {
	private Connection con;
	private Context context;
	
	@Override
	public String handleRequest(Object event, Context context) {
		this.context = context;
	    context.getLogger().log("TIOPDocumentValidation::handleRequest ::: Start");
	    
	    String source = null;
	    String destination = null;
		String gtinInfo = null;
		String fileName = null;
		String bucketName = null;
		int objEventCount = 0;
		int aggEventCount = 0;
		S3Object s3object = null;
		S3ObjectInputStream inputStream = null;
//		S3Object s3object1 = null;
//		S3ObjectInputStream inputStream1 = null;
		Document document;
		
		 if(event instanceof LinkedHashMap) {
		    	LinkedHashMap<String, Object> mapEvent = (LinkedHashMap<String, Object>) event;
		    	bucketName = (String) mapEvent.get("bucketName");
		    	fileName = (String) mapEvent.get("keyName");
		    	source = (String) mapEvent.get("source");
		    	destination = (String) mapEvent.get("destination");
		    	gtinInfo = (String) mapEvent.get("gtinInfo");
		    	
		    	String objCount = (String) mapEvent.get("objEventCount");
		    	if(objCount != null) objEventCount = Integer.parseInt(objCount);
		    	String aggCount = (String) mapEvent.get("aggEventCount");
		    	if(aggCount != null) aggEventCount = Integer.parseInt(aggCount);
		    	
		    	//listEPCISBody1 = (NodeList) mapEvent.get("listEPCISBody");
		    	
		    	context.getLogger().log("ValidateLambdaHandler::BucketName :: " + bucketName);
				context.getLogger().log("ValidateLambdaHandler::fileName :: " + fileName);
				context.getLogger().log("ValidateLambdaHandler::Total ObjectEvent count = "+objEventCount);
				context.getLogger().log("ValidateLambdaHandler::Total AggregationEvent count = "+aggEventCount);
				context.getLogger().log("ValidateLambdaHandler::source = "+source);
				context.getLogger().log("ValidateLambdaHandler::destination = "+destination);
				context.getLogger().log("ValidateLambdaHandler::gtinInfo = "+gtinInfo);
				//context.getLogger().log("ValidateLambdaHandler::listEPCISBody = "+listEPCISBody1);
		   }
		
		
		try {
			AmazonS3 s3client = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
					.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
			s3object = s3client.getObject(bucketName, fileName);
			inputStream = s3object.getObjectContent();
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			document = builder.parse(inputStream);
			document.getDocumentElement().normalize();

			this.con = getConnection();

			// get <EPCISBody>
			NodeList listEPCISBody = document.getElementsByTagName("EPCISBody");
			for (int temp = 0; temp < listEPCISBody.getLength(); temp++) {
				Node nodeEPCISBody = listEPCISBody.item(temp);
				// context.getLogger().log("Current Element1 : " + nodeEPCISBody.getNodeName());
				if (nodeEPCISBody.getNodeType() == Node.ELEMENT_NODE
						&& nodeEPCISBody.getNodeName().equals(TIOPConstants.EPCISBody)) {
					Element elementEPCISBody = (Element) nodeEPCISBody;

					// get <EventList>
					parseEventList(elementEPCISBody, source, destination, gtinInfo, fileName, objEventCount, aggEventCount);
				}

			}	
			//Router lamda -- arn:aws:lambda:us-east-1:654654535046:function:TIOPRouter
			invokeRouterfromLamda(context, fileName, bucketName, source, destination, gtinInfo, objEventCount, aggEventCount);
			//Transfrom lamda call
			invokeTransfromLamda(context, fileName, bucketName, source, destination, gtinInfo, objEventCount, aggEventCount);

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
				if (inputStream != null) inputStream.close();
				//if (inputStream1 != null) inputStream1.close();
				if (s3object != null) s3object.close();
				//if (s3object1 != null) s3object1.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			context.getLogger().log("ValidateLambdaHandler finally end");
		}

		return "Validated successfully for file '" + fileName + "' from s3 bucket '" + bucketName + "'";
	}
	
	private void invokeRouterfromLamda(Context context, String fileName, String bucketName, String source,
			String destination, String gtinInfo, int objEventCount, int aggEventCount) {
		context.getLogger().log("Calling TIOPRouter lamda");

		JSONObject payloadObject = new JSONObject();
		payloadObject.put("bucketName", bucketName);
		payloadObject.put("fileName", fileName);
		payloadObject.put("source", source);
		payloadObject.put("destination", destination);
		payloadObject.put("gtinInfo", gtinInfo);
		payloadObject.put("objEventCount", String.valueOf(objEventCount));
		payloadObject.put("aggEventCount", String.valueOf(aggEventCount));

		AWSLambda client = AWSLambdaAsyncClient.builder().withRegion(Regions.US_EAST_1).build();
		String TIOPRouter = System.getenv("TIOPRouter");
		InvokeRequest request = new InvokeRequest();
		request.withFunctionName(TIOPRouter)
				.withPayload(payloadObject.toString());
		context.getLogger().log("Calling TIOPRouter with payload = " +payloadObject.toString());
		InvokeResult invoke = client.invoke(request);
		context.getLogger().log("Result invoking TIOPRouter  == " + invoke);
	}
	
	

	private void invokeTransfromLamda(Context context, String fileName, String bucketName, String source, String destination, String gtinInfo, int objEventCount, int aggEventCount) {
		//context.getLogger().log("Calling validate lamda");
		
		JSONObject payloadObject = new JSONObject();
		payloadObject.put("bucketName", bucketName);
		payloadObject.put("fileName", fileName);
		payloadObject.put("source", source);
		payloadObject.put("destination", destination);
		payloadObject.put("gtinInfo", gtinInfo);
		payloadObject.put("objEventCount", String.valueOf(objEventCount));
		payloadObject.put("aggEventCount", String.valueOf(aggEventCount));
		
		String secretDetails = TIOPUtil.getSecretDetails(System.getenv(TIOPConstants.dbSecret));
		context.getLogger().log("Result invoking TIOPRouter  == " + secretDetails);
		payloadObject.put("secretDetails", secretDetails);
		
		AWSLambda client = AWSLambdaAsyncClient.builder().withRegion(Regions.US_EAST_1).build();
		String TIOPDocumentTransform = System.getenv("TIOPDocumentTransform");
		InvokeRequest request = new InvokeRequest();
		request.withFunctionName(TIOPDocumentTransform).withPayload(payloadObject.toString());
		//context.getLogger().log("Calling TransfromLambdaHandler with payload = "+payloadObject.toString());
		InvokeResult invoke = client.invoke(request);
		context.getLogger().log("Result invoking TIOPDocumentValidation  == " + invoke);
	}
	
	private Connection getConnection() throws ClassNotFoundException, SQLException {
		if (con == null || con.isClosed()) {
			con = TIOPUtil.getConnection();
		}
		return con;
	}
	
	private Set<String> getContact(String sourceGlnUri) {
		Set<String> toEmailSet = new HashSet<String>();
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
			while (rs.next()) {
				toEmailSet.add(rs.getString(3));
			}

		} catch (Exception e) {
			context.getLogger().log("rdsDbTeat ::: db error = " + e.getMessage());
		}
		context.getLogger().log("getContact ::: DB ResultSet = " + toEmailSet);
		return toEmailSet;

	}
	
	private void insertErrorLog(int eventTypeId, String msg, String source, String destination, String gtinInfo, String fileName, int objEventCount, int aggEventCount) {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String strDate= formatter.format(date);

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
		NodeList listEventList = elementEPCISBody.getElementsByTagName(TIOPConstants.EventList);
		context.getLogger().log("-----------------------------------parseEventList start");
		for (int temp = 0; temp < listEventList.getLength(); temp++) {
			Node nodeEventList = listEventList.item(temp);
			//context.getLogger().log("Current Element2 : " + nodeEventList.getNodeName());

			if (nodeEventList.getNodeType() == Node.ELEMENT_NODE && nodeEventList.getNodeName().equals(TIOPConstants.EventList)) {
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
		context.getLogger().log("-----------------------------------parseEventList end");
	}

	private  void parseAggregationEventt(Element elementEventList, String source, String destination, String gtinInfo, String fileName, int objEventCount, int aggEventCount) throws TIOPException {
		NodeList listAggregationEvent = elementEventList.getElementsByTagName(TIOPConstants.AggregationEvent);
		for (int temp = 0; temp < listAggregationEvent.getLength(); temp++) {
			Node nodeAggregationEvent = listAggregationEvent.item(temp);
			//context.getLogger().log("Current Element3 : " + nodeAggregationEvent.getNodeName());
			if (nodeAggregationEvent.getNodeType() == Node.ELEMENT_NODE
					&& nodeAggregationEvent.getNodeName().equals(TIOPConstants.AggregationEvent)) {
				Element elementAggregationEvent = (Element) nodeAggregationEvent;
				
				String eventTime = getSimpleNode(elementAggregationEvent, TIOPConstants.eventTime);
				if(eventTime == null || eventTime.isBlank()) throw new TIOPException("EXC002#Event Time is blank or missing");
				
				String eventTimeZoneOffset = getSimpleNode(elementAggregationEvent, TIOPConstants.eventTimeZoneOffset);
				if(eventTimeZoneOffset == null || eventTimeZoneOffset.isBlank()) throw new TIOPException("EXC003#Event Time ZoneOffset is blank or missing");
				
				String bizStep = getSimpleNode(elementAggregationEvent, TIOPConstants.bizStep);
				if(bizStep == null || bizStep.isBlank()) throw new TIOPException("EXC004#Event BizStep is blank or missing");
				
				Set<String> parentIDSet = new HashSet<String>();
				String parentID = getSimpleNode(elementAggregationEvent, TIOPConstants.parentID);
				if(parentID == null || parentID.isBlank()) throw new TIOPException("EXC005#Event ParentID is blank or missing");
				
				validateLocation(elementAggregationEvent);
				
			}
		}
		context.getLogger().log("Total AggregationEvent count = "+listAggregationEvent.getLength());
		
	}
	

	private  void parseObjectEvent(Element elementEventList, String source, String destination, String gtinInfo, String fileName, int objEventCount, int aggEventCount) throws TIOPException {
		NodeList listObjectEvent = elementEventList.getElementsByTagName(TIOPConstants.ObjectEvent);
		for (int temp = 0; temp < listObjectEvent.getLength(); temp++) {
			Node nodeObjectEvent = listObjectEvent.item(temp);
			//context.getLogger().log("Current Element3 : " + nodeObjectEvent.getNodeName());
			if (nodeObjectEvent.getNodeType() == Node.ELEMENT_NODE
					&& nodeObjectEvent.getNodeName().equals(TIOPConstants.ObjectEvent)) {
				Element elementObjectEvent = (Element) nodeObjectEvent;
				
				String eventTime = getSimpleNode(elementObjectEvent, TIOPConstants.eventTime);
				if(eventTime == null || eventTime.isBlank()) throw new TIOPException("EXC002#Event Time is blank or missing");
				
				String eventTimeZoneOffset = getSimpleNode(elementObjectEvent, TIOPConstants.eventTimeZoneOffset);
				if(eventTimeZoneOffset == null || eventTimeZoneOffset.isBlank()) throw new TIOPException("EXC003#Event Time ZoneOffset is blank or missing");
				
				String bizStep = getSimpleNode(elementObjectEvent, TIOPConstants.bizStep);
				if(bizStep == null || bizStep.isBlank()) throw new TIOPException("EXC004#Event BizStep is blank or missing");
				
				validateLocation(elementObjectEvent);
								
			}
		}
		context.getLogger().log("Total ObjectEvent count = "+listObjectEvent.getLength());
	}
	
	private void validateLocation(Element elementObjectEvent) throws TIOPException {
		String bizStep = getSimpleNode(elementObjectEvent, TIOPConstants.bizStep);
		if(bizStep.contains(TIOPConstants.shipping)) {
			Set<String> locationSet = geLocationFromExtension(elementObjectEvent);
			context.getLogger().log("locationSet ==== "+locationSet);
			if(locationSet == null || locationSet.isEmpty()) throw new TIOPException("EXC006#Recipient GLN (physical ship-to location) is blank or missing");
		}
		
	}
	
	private Set<String> geLocationFromExtension(Element elementObjectEvent) {
		Set<String> locSet = new HashSet<String>();
		NodeList listExtension = elementObjectEvent.getElementsByTagName(TIOPConstants.extension);
		for (int temp = 0; temp < listExtension.getLength(); temp++) {
			Node nodeExtension = listExtension.item(temp);
			// context.getLogger().log("Current Element : " + nodeEPCList.getNodeName());
			if (nodeExtension.getNodeType() == Node.ELEMENT_NODE && nodeExtension.getNodeName().equals(TIOPConstants.extension)) {
				Element elementExtension = (Element) nodeExtension;
				String location = parseLocation(elementExtension);
				if(location != null && !location.isBlank()) locSet.add(location);
			}
		}
		return locSet;
	}

	private String parseLocation(Element elementExtension) {
		String location = null;
		NodeList listDestinationList = elementExtension.getElementsByTagName(TIOPConstants.destinationList);
		for (int temp = 0; temp < listDestinationList.getLength(); temp++) {
			Node nodeDestinationList = listDestinationList.item(temp);
			// context.getLogger().log("Current Element : " + nodeDestinationList.getNodeName());
			if (nodeDestinationList.getNodeType() == Node.ELEMENT_NODE
					&& nodeDestinationList.getNodeName().equals(TIOPConstants.destinationList)) {
				Element elementDestinationList = (Element) nodeDestinationList;
				NodeList lisDestination = elementDestinationList.getElementsByTagName(TIOPConstants.destination);
				for (int j = 0; j < lisDestination.getLength(); j++) {
					location = elementDestinationList.getElementsByTagName(TIOPConstants.destination).item(j).getTextContent();
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

}
