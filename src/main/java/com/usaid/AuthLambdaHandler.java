package com.usaid;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream; 

public class AuthLambdaHandler implements RequestHandler<S3Event, String> {
	private Connection con;
	private Context context;
	
	@Override
	public String handleRequest(S3Event s3Event, Context context) {
		String bucketName = "";
		String fileName = "";
		this.context = context;
	    context.getLogger().log("TIOPDocumentAuth::handleRequest ::: Stat");
		bucketName = s3Event.getRecords().get(0).getS3().getBucket().getName();
		fileName = s3Event.getRecords().get(0).getS3().getObject().getKey();
		context.getLogger().log("BucketName :: " + bucketName);
		context.getLogger().log("fileName :: " + fileName);
		S3Object s3object = null;
		S3ObjectInputStream inputStream = null;
		Document document;
		try {
			AmazonS3 s3client = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
					.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
			context.getLogger().log("TIOPDocumentAuth::handleRequest ::: Start");
			String env = System.getenv("env");
			context.getLogger().log("TIOPDocumentAuth::handleRequest ::: env = "+env);
			s3object = s3client.getObject(bucketName, fileName);
			inputStream = s3object.getObjectContent();
			context.getLogger().log("TIOPDocumentAuth::handleRequest ::: 1");
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			document = builder.parse(inputStream);
			context.getLogger().log("TIOPDocumentAuth::handleRequest ::: 2");
			document.getDocumentElement().normalize();
			context.getLogger().log("TIOPDocumentAuth::handleRequest ::: 3");
//			if (inputStream != null) inputStream.close();
//			if (s3object != null) s3object.close();
			this.con = getConnection();
			Map<String, String> validationInfo = new HashMap<String, String>();
			// get <EPCISBody>
			NodeList listEPCISBody = document.getElementsByTagName(TIOPConstants.EPCISBody);
			for (int temp = 0; temp < listEPCISBody.getLength(); temp++) {
				Node nodeEPCISBody = listEPCISBody.item(temp);
				// context.getLogger().log("Current Element1 : " + nodeEPCISBody.getNodeName());
				if (nodeEPCISBody.getNodeType() == Node.ELEMENT_NODE
						&& nodeEPCISBody.getNodeName().equals(TIOPConstants.EPCISBody)) {
					Element elementEPCISBody = (Element) nodeEPCISBody;

					// get <EventList>
					validationInfo = parseEventList(elementEPCISBody, fileName, bucketName);
				}
			}	
			
			//validate lamda call
			String source = validationInfo.get("source");
			String destination = validationInfo.get("destination");
			String gtinInfo = validationInfo.get("gtinInfo");
	    	int objEventCount = 0;
	    	int aggEventCount = 0;
	    	String objCount = validationInfo.get("objEventCount");
	    	if(objCount != null) objEventCount = Integer.parseInt(objCount);
	    	String aggCount = validationInfo.get("aggEventCount");
	    	if(aggCount != null) aggEventCount = Integer.parseInt(aggCount);
			invokeValidateLamda(context, bucketName, fileName, gtinInfo, source, destination, objEventCount, aggEventCount, listEPCISBody);
			
			if (inputStream != null) inputStream.close();
			if (s3object != null) s3object.close();
			context.getLogger().log("Authenticate successfully for file '" + fileName + "' from s3 bucket '" + bucketName + "'");
			
		} catch (TIOPException e) {
			context.getLogger().log("caught TIOPAuthException. Email sent and logged the error.");
			return "Error while reading file from S3 :::" + e.getMessage();
		} 
		catch (Exception e) {
			context.getLogger().log("Auth Exception::Exception message : "+e.getMessage());
			//e.printStackTrace();
			return "Error in TIOP Auth :::" + e.getMessage();
		} 
		
		finally {
			try {
//				source = null;
//				destination = null;
				if (con != null  && !con.isClosed()) con.close();
				if (inputStream != null) inputStream.close();
				if (s3object != null) s3object.close();
			} catch (Exception e) {
				context.getLogger().log("AuthLambdaHandler::Exception::finally = "+e.getMessage());
			}
			context.getLogger().log("TIOPAuth::finally end");
		}

		return "Authenticate successfully for file '" + fileName + "' from s3 bucket '" + bucketName + "'";
	}


	private Connection getConnection() throws ClassNotFoundException, SQLException {
		if (con == null || con.isClosed()) {
			Class.forName(TIOPConstants.dbdriver);
			con = DriverManager.getConnection(TIOPConstants.dburl, TIOPConstants.dbuser, TIOPConstants.dbpass);
		}
		return con;
	}
	
	private void invokeValidateLamda(Context context, String bucketName, String fileName, String gtinInfo, String source, String destination, int  objEventCount, int aggEventCount, NodeList listEPCISBody) {
		context.getLogger().log("Calling validate lamda");
		JSONObject payloadObject = new JSONObject();
		payloadObject.put("keyName", fileName);
		payloadObject.put("bucketName", bucketName);
		payloadObject.put("source", source);
		payloadObject.put("destination", destination);
		payloadObject.put("gtinInfo", gtinInfo);
		payloadObject.put("objEventCount", String.valueOf(objEventCount));
		payloadObject.put("aggEventCount", String.valueOf(aggEventCount));
		payloadObject.put("listEPCISBody", listEPCISBody);
		
		AWSLambda client = AWSLambdaAsyncClient.builder().withRegion(Regions.US_EAST_1).build();

		InvokeRequest request = new InvokeRequest();
		String TIOPDocumentValidation = System.getenv("TIOPDocumentValidation");
		request.withFunctionName(TIOPDocumentValidation).withPayload(payloadObject.toString());
		context.getLogger().log("Calling TIOPDocumentValidation with payload = "+payloadObject.toString());
		InvokeResult invoke = client.invoke(request);
		context.getLogger().log("Result invoking " +TIOPDocumentValidation +" == " + ": " + invoke);
	}

	
	
	private  Map<String, String> parseEventList(Element elementEPCISBody, String fileName, String bucketName) throws TIOPException {
		NodeList listEventList = elementEPCISBody.getElementsByTagName("EventList");
		int objEventCount = 0;
		int aggEventCount = 0;
		int eventTypeId = 0;
		String source = null;
		String destination = null;
		String gtinInfo = null;
		Map<String, String> basicInfoMap = null;
		
		for (int temp = 0; temp < listEventList.getLength(); temp++) {
			Node nodeEventList = listEventList.item(temp);
			//context.getLogger().log("Current Element2 : " + nodeEventList.getNodeName());

			if (nodeEventList.getNodeType() == Node.ELEMENT_NODE && nodeEventList.getNodeName().equals(TIOPConstants.EventList)) {
				Element elementEventList = (Element) nodeEventList;
				
				if(basicInfoMap == null || basicInfoMap.isEmpty()) {
					basicInfoMap = parseBasicInfo(elementEventList, fileName);
					context.getLogger().log("basicInfoMap : " + basicInfoMap);
					source = basicInfoMap.get("source");
					destination = basicInfoMap.get("destination");
					gtinInfo = basicInfoMap.get("gtinInfo");
					
					String objCount = basicInfoMap.get("objEventCount");
					String aggCount = basicInfoMap.get("aggEventCount");
					String eventType = basicInfoMap.get("eventType");
					if(objCount != null) objEventCount = Integer.parseInt(objCount);
					if(aggCount != null) aggEventCount = Integer.parseInt(aggCount);
					if(eventType != null) eventTypeId = Integer.parseInt(eventType);
					
					insertBasicInfo(fileName, objEventCount, aggEventCount, gtinInfo, source, destination);
					
				}
				
				List<String> gtin_uriList = null;
				if(gtin_uriList == null || gtin_uriList.isEmpty()) {
					gtin_uriList = getEPCListFromDB(eventTypeId, source, destination, fileName, objEventCount, aggEventCount, gtinInfo);
					if(gtin_uriList == null || gtin_uriList.isEmpty()) throw new TIOPException("Manufacture GLN uri ["+source+"], recipient country GLN ["+destination+"], and GTIN uri ["+gtinInfo+"] combination does not exist in TIOP business rules");
			    } 
				
				//String gtinInfo, String source, String destination
				
				parseObjectEvent(elementEventList, gtin_uriList, fileName, objEventCount, aggEventCount, gtinInfo, source, destination);
				parseAggregationEventt(elementEventList, gtin_uriList, fileName,  objEventCount, aggEventCount, gtinInfo, source, destination);
			}

		}
		
		//validate lamda call
		Map<String, String> validationInfo = new HashMap<String, String>();
		validationInfo.put("gtinInfo", gtinInfo);
		validationInfo.put("source", source);
		validationInfo.put("destination", destination);
		validationInfo.put("objEventCount", String.valueOf(objEventCount));
		validationInfo.put("aggEventCount", String.valueOf(aggEventCount));
		return validationInfo;
		//invokeValidateLamda(context, bucketName, fileName, gtinInfo, source, destination, objEventCount, aggEventCount);
	}
	
	private Map<String, String> parseBasicInfo(Element elementEventList, String fileName) throws TIOPException {
		String bizStep = null;
		Map<String, String> basicInfoMap = new HashMap<String, String>();
		int objEventCount = 0;
		int aggEventCount = 0;
		String source = null;
		String destination = null;
		String gtinInfo = null;
		
		NodeList listObjectEvent = elementEventList.getElementsByTagName(TIOPConstants.ObjectEvent);
		if(listObjectEvent != null) objEventCount = listObjectEvent.getLength();
		
		for (int temp = 0; temp < listObjectEvent.getLength(); temp++) {
			Node nodeObjectEvent = listObjectEvent.item(temp);
			//context.getLogger().log("Current Element3 : " + nodeObjectEvent.getNodeName());
			if (nodeObjectEvent.getNodeType() == Node.ELEMENT_NODE
					&& nodeObjectEvent.getNodeName().equals(TIOPConstants.ObjectEvent)) {
				Element elementObjectEvent = (Element) nodeObjectEvent;
				
				
				bizStep = getSimpleNode(elementObjectEvent, TIOPConstants.bizStep);
				//context.getLogger().log("bizStep == " + bizStep);
				basicInfoMap.put("eventType", "1");
				if(bizStep.contains(TIOPConstants.shipping)) {
					//context.getLogger().log("Present");
					if(source == null) {
						source = extractGN(getSource(elementObjectEvent));
						basicInfoMap.put("eventType", "1");
						//context.getLogger().log("Current source in OE== " + source);
				    } 
					
					if(destination == null) {
						destination = getSimpleNode(elementObjectEvent, TIOPConstants.tiop_nts_gln);
						basicInfoMap.put("eventType", "1");
						//context.getLogger().log("Current destination in OE== " + destination);
				    } 
				}
				
				if(gtinInfo == null || gtinInfo.isBlank()) {
					Set<String> epcSetInfo = parseEPCList(elementObjectEvent, TIOPConstants.epcList);
					//context.getLogger().log("----- epcSet = " + epcSetInfo);
					if(epcSetInfo != null && !epcSetInfo.isEmpty()) {
						for(String gtin : epcSetInfo) {
							gtinInfo = gtin;
						}
						//context.getLogger().log("----- epcInfo = " + gtinInfo);
					}
				}
			}
		}
		NodeList listAggregationEvent = elementEventList.getElementsByTagName(TIOPConstants.AggregationEvent);
		if(listAggregationEvent != null) aggEventCount = listAggregationEvent.getLength();
		for (int temp = 0; temp < listAggregationEvent.getLength(); temp++) {
			Node nodeAggregationEvent = listAggregationEvent.item(temp);
			// context.getLogger().log("Current Element3 : " + nodeObjectEvent.getNodeName());
			if (nodeAggregationEvent.getNodeType() == Node.ELEMENT_NODE
					&& nodeAggregationEvent.getNodeName().equals(TIOPConstants.AggregationEvent)) {
				Element elementAggregationEvent = (Element) nodeAggregationEvent;
				
				bizStep = getSimpleNode(elementAggregationEvent, TIOPConstants.bizStep);
				basicInfoMap.put("eventType", "2");
				//context.getLogger().log("bizStep == " + bizStep);
				if(bizStep.contains(TIOPConstants.shipping)) {
					//context.getLogger().log("Present");
					if(source == null) {
						source = extractGN(getSource(elementAggregationEvent));
						basicInfoMap.put("eventType", "2");
						//context.getLogger().log("Current source in OE== " + source);
				    } 
					
					
					
					if(destination == null) {
						destination = getSimpleNode(elementAggregationEvent, TIOPConstants.tiop_nts_gln);
						basicInfoMap.put("eventType", "2");
						//context.getLogger().log("Current destination in OE== " + destination);
				    } 
				}
				
				if(gtinInfo == null || gtinInfo.isBlank()) {
					Set<String> epcSetInfo = parseEPCList(elementAggregationEvent, TIOPConstants.epcList);
					//context.getLogger().log("----- epcSet = " + epcSetInfo);
					if(epcSetInfo != null && !epcSetInfo.isEmpty()) {
						for(String gtin : epcSetInfo) {
							gtinInfo = gtin;
						}
						//context.getLogger().log("----- epcInfo = " + gtinInfo);
					}
				}
				
				
			}
		}
		context.getLogger().log("Total ObjectEvent count = "+objEventCount);
		context.getLogger().log("Total AggregationEvent count = "+aggEventCount);
		context.getLogger().log("source = "+source);
		context.getLogger().log("destination = "+destination);
		context.getLogger().log("gtinInfo = "+gtinInfo);
		context.getLogger().log("eventType = "+basicInfoMap.get("eventType"));
		
		basicInfoMap.put("objEventCount", String.valueOf(objEventCount));
		basicInfoMap.put("aggEventCount", String.valueOf(aggEventCount));
		basicInfoMap.put("source", source);
		basicInfoMap.put("destination", destination);
		basicInfoMap.put("gtinInfo", gtinInfo);
		return basicInfoMap;
	}
	

	private  void parseAggregationEventt(Element elementEventList, List<String> gtin_uriList,  String fileName, int objEventCount, int aggEventCount, String gtinInfo, String source, String destination) throws TIOPException {
		NodeList listAggregationEvent = elementEventList.getElementsByTagName(TIOPConstants.AggregationEvent);
		for (int temp = 0; temp < listAggregationEvent.getLength(); temp++) {
			Node nodeAggregationEvent = listAggregationEvent.item(temp);
			//context.getLogger().log("Current Element3 : " + nodeAggregationEvent.getNodeName());
			if (nodeAggregationEvent.getNodeType() == Node.ELEMENT_NODE
					&& nodeAggregationEvent.getNodeName().equals(TIOPConstants.AggregationEvent)) {
				Element elementAggregationEvent = (Element) nodeAggregationEvent;
				
//				if(source == null) {
//					source = extractGN(getSource(elementAggregationEvent));
//					context.getLogger().log("Current source in AE== " + source);
//			    } 
//				
//				
//				if(destination == null) {
//					destination = getSimpleNode(elementAggregationEvent, "tiop:nts_gln");
//					context.getLogger().log("Current destination in AE== " + destination);
//			    } 
				
//				if(gtin_uriList == null || gtin_uriList.isEmpty()) {
//					//context.getLogger().log("Calling getEPCListFromDB from OE");
//					gtin_uriList = getEPCListFromDB(source, destination);
//			    } 
				
				
				Set<String> childEPCsSet = parseEPCList(elementAggregationEvent, TIOPConstants.childEPCs);
//				String tmp = null;
//				if(childEPCsSet == null || childEPCsSet.isEmpty()) {
//					for(String gtin : childEPCsSet) {
//						tmp = gtin;
//					}
//				}
				
				//context.getLogger().log("----- tmp = " + tmp);
				
//				if(gtin_uriList == null || gtin_uriList.isEmpty()) {
//					context.getLogger().log("Sending error email and insert error log in db as no active gtin are there in db:: source = " + source+ " --- destination = "+destination+"  --- gtin = "+gtinInfo);
//					TIOPAuthSendEmail.sendMail(context, 2, "swarchat@in.ibm.com", fileName, source, destination, gtinInfo);
//					String msg = "Manufacture GLN uri ["+source+"], recipient country GLN ["+destination+"], and GTIN uri ["+gtinInfo+"] combination does not exist in TIOP business rules";
//					insertErrorLog(2, msg, fileName, objEventCount, aggEventCount, gtinInfo, source, destination);
//					throw new TIOPException("Manufacture GLN uri, recipient country GLN, and GTIN uri combination does not exist in TIOP business rules");
//				}
				
				
				//context.getLogger().log("----- childEPCsSet = " + childEPCsSet);
				Set<String> parentIDSet = new HashSet<String>();
				String parentID = getSimpleNode(elementAggregationEvent, TIOPConstants.parentID);
				if(parentID != null && parentID.contains(TIOPConstants.urn_sgtin)) {
					parentID = parentID.substring(0, parentID.lastIndexOf('.'));
					parentIDSet.add(parentID);
				} 
				//context.getLogger().log("----- parentIDSet = " + parentIDSet);
				
				oauthValidation(gtin_uriList, childEPCsSet, 2, fileName, objEventCount, aggEventCount, gtinInfo, source, destination);
				oauthValidation(gtin_uriList, parentIDSet, 2, fileName, objEventCount, aggEventCount, gtinInfo, source, destination);
				
			}
		}
		context.getLogger().log("Total AggregationEvent count = "+listAggregationEvent.getLength());
		
	}
	

	private  void parseObjectEvent(Element elementEventList, List<String> gtin_uriList, String fileName, int objEventCount, int aggEventCount, String gtinInfo, String source, String destination) throws TIOPException {
		NodeList listObjectEvent = elementEventList.getElementsByTagName(TIOPConstants.ObjectEvent);
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
//				
//				if(destination == null) {
//					destination = getSimpleNode(elementObjectEvent, "tiop:nts_gln");
//					context.getLogger().log("Current destination in OE== " + destination);
//			    } 
				
				
				
//				if(gtin_uriList == null || gtin_uriList.isEmpty()) {
//					//context.getLogger().log("Calling getEPCListFromDB from OE");
//					gtin_uriList = getEPCListFromDB(source, destination);
//			    } 
				
				
				Set<String> epcSet = parseEPCList(elementObjectEvent, TIOPConstants.epcList);
//				String tmp = null;
//				if(epcSet == null || epcSet.isEmpty()) {
//					for(String gtin : epcSet) {
//						tmp = gtin;
//					}
//				}
				
				//context.getLogger().log("----- tmp = " + tmp);
				
//				if(gtin_uriList == null || gtin_uriList.isEmpty()) {
//					context.getLogger().log("Sending error email and insert error log in db as no active gtin are there in db:: source = " + source+ " --- destination = "+destination+"  --- gtin = "+gtinInfo);
//					TIOPAuthSendEmail.sendMail(context, 1, "swarchat@in.ibm.com", fileName, source, destination, gtinInfo);
//					String msg = "Manufacture GLN uri ["+source+"], recipient country GLN ["+destination+"], and GTIN uri ["+gtinInfo+"] combination does not exist in TIOP business rules";
//					insertErrorLog(1, msg, fileName, objEventCount, aggEventCount, gtinInfo, source, destination);
//					throw new TIOPException("Manufacture GLN uri, recipient country GLN, and GTIN uri combination does not exist in TIOP business rules");
//				}
				oauthValidation(gtin_uriList, epcSet, 1, fileName, objEventCount, aggEventCount, gtinInfo, source, destination);
			}
		}
		context.getLogger().log("Total ObjectEvent count = "+listObjectEvent.getLength());
	}

	private  void oauthValidation(List<String> gtin_uriList, Set<String> epcSet, int eventType, String fileName, int objEventCount, int aggEventCount, String gtinInfo, String source, String destination) throws TIOPException {
		if(!epcSet.isEmpty() && !gtin_uriList.isEmpty()) {
			for(String epc: epcSet) {
				if(!gtin_uriList.contains(epc)) {
					context.getLogger().log("NOT EXIST :: "+epc+" is not exists in Active GTIN list");
					context.getLogger().log("Sending error email and insert error log in db for epc = "+epc);
					
					Date date = new Date();
					SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
					String strDate = formatter.format(date);
					final String htmlBody = "<h4>An issue [EXC001] encountered while processing the file "+fileName+" which was received on "+strDate+".</h4>"
							+ "<h4>Details of the Issue:</h4>"
							+ "Manufacture GLN uri ["+source+"], recipient country GLN ["+destination+"], and GTIN uri ["+epc+"] combination does not exist in TIOP business rules.</p>"
							+ "<p>TIOP operation team</p>";
					TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);
					String msg = "Manufacture GLN uri ["+source+"], recipient country GLN ["+destination+"], and GTIN uri ["+epc+"] combination does not exist in TIOP business rules";
					insertErrorLog(eventType, msg, fileName, objEventCount, aggEventCount, epc, source, destination);
					
					//insertErrorLog(int eventType, int errorType, String modifiedBy, String sourceGlnUri, String destination, String gtinUri) {
					throw new TIOPException("NOT EXIST :: "+epc+" is not exists in Active GTIN list");
				}
			}
		}
	}

	private  String getSimpleNode(Element elementObjectEvent, String nodeName) {
		String node = null;
		try {
			if(elementObjectEvent.getElementsByTagName(nodeName) != null) {
				//context.getLogger().log("If ----------------- size = "+elementObjectEvent.getElementsByTagName(nodeName).getLength());
				node = elementObjectEvent.getElementsByTagName(nodeName).item(0).getTextContent();
			}
		} catch (Exception e) {
			context.getLogger().log("error ----------------- "+e.getMessage());
			//e.printStackTrace();
		}
		
		//context.getLogger().log("Current "+ nodeName+" == " + node);
		return node;
	}

	private  String getSource(Element elementObjectEvent) {
		String source = null;
		NodeList nodelistBizLocation = elementObjectEvent.getElementsByTagName(TIOPConstants.bizLocation);
		for (int temp = 0; temp < nodelistBizLocation.getLength(); temp++) {
			Node nodeBizLocation = nodelistBizLocation.item(temp);
			// context.getLogger().log("Current Element : " + nodeEPCList.getNodeName());
			if (nodeBizLocation.getNodeType() == Node.ELEMENT_NODE && nodeBizLocation.getNodeName().equals(TIOPConstants.bizLocation)) {
				Element elementBizLocation = (Element) nodeBizLocation;
				source = elementBizLocation.getElementsByTagName("id").item(0).getTextContent();
			}
		}
		return source;
	}
	
	private  Set<String> parseEPCList(Element elementObjectEvent, String nodeName) {
		Set<String> gtinSet = new HashSet<String>();
		NodeList listEPCList = elementObjectEvent.getElementsByTagName(nodeName);
		for (int temp = 0; temp < listEPCList.getLength(); temp++) {
			Node nodeEPCList = listEPCList.item(temp);
			// context.getLogger().log("Current Element : " + nodeEPCList.getNodeName());
			if (nodeEPCList.getNodeType() == Node.ELEMENT_NODE && nodeEPCList.getNodeName().equals(nodeName)) {
				Element elementEPCList = (Element) nodeEPCList;

				NodeList listEPC = elementObjectEvent.getElementsByTagName(TIOPConstants.epc);
				// context.getLogger().log("epc length : " + listEPC.getLength());
				for (int j = 0; j < listEPC.getLength(); j++) {
					String epc = elementEPCList.getElementsByTagName(TIOPConstants.epc).item(j).getTextContent();
					//context.getLogger().log("epc uri [" + j + "] = " + epc);
					if (epc.contains(TIOPConstants.urn_sgtin)) {
						// context.getLogger().log("sgtin :::> " + epc.substring(0, epc.lastIndexOf('.')));
						gtinSet.add(epc.substring(0, epc.lastIndexOf('.')));
					}
				}
			}
		}
		return gtinSet;
	}


	private  String extractGN(String gn) {
		if(gn != null) {
			String last = gn.substring(gn.lastIndexOf('.'), gn.length());
			if (!last.contains(".0")) {
				gn = gn.substring(0, gn.lastIndexOf('.'));
			}
		}
		return gn;
	}
	
	private List<String> getEPCListFromDB(int eventType, String source, String destination, String fileName, int objEventCount, int aggEventCount, String gtinInfo) {
		//context.getLogger().log("rdsDbTeat ::: Start");
	 List<String> gtinUriList = new ArrayList<String>();
	 String query = "select ti.gtin_uri  from \r\n"
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
	 		+ "and sl.gln_uri = '"+source+"'\r\n"
	 		+ "and dl.gln = '"+destination+"'";
	 
		try {
			context.getLogger().log("getEPCListFromDB ::: Start");
			con = getConnection();
			//context.getLogger().log("getEPCListFromDB ::: con = "+con);
			Statement stmt = con.createStatement();
			context.getLogger().log("--------------------------------------getEPCListFromDB ::: query = "+query);
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				if(rs.getString(1) != null) gtinUriList.add(rs.getString(1));
			}
			
			if(gtinUriList.isEmpty()) {
				context.getLogger().log("<<<<<<<<<<<<< No record found for source and destination input.");
				context.getLogger().log("Sending error email and insert error log in db as no active gtin are there in db:: source = " + source+ " --- destination = "+destination+"  --- gtin = "+gtinInfo);
				
				Date date = new Date();
				SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
				String strDate = formatter.format(date);
				final String htmlBody = "<h4>An issue [EXC001] encountered while processing the file "+fileName+" which was received on "+strDate+".</h4>"
						+ "<h4>Details of the Issue:</h4>"
						+ "Manufacture GLN uri ["+source+"], recipient country GLN ["+destination+"], and GTIN uri ["+gtinInfo+"] combination does not exist in TIOP business rules.</p>"
						+ "<p>TIOP operation team</p>";
				TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);
				//TIOPAuthSendEmail.sendMail(context, 2, "swarchat@in.ibm.com", fileName, source, destination, gtinInfo);
				String msg = "Manufacture GLN uri ["+source+"], recipient country GLN ["+destination+"], and GTIN uri ["+gtinInfo+"] combination does not exist in TIOP business rules";
				insertErrorLog(eventType, msg, fileName, objEventCount, aggEventCount, gtinInfo, source, destination);
				//throw new TIOPException("Manufacture GLN uri, recipient country GLN, and GTIN uri combination does not exist in TIOP business rules");
			}
		} catch (Exception e) {
			context.getLogger().log("getEPCListFromDB ::: db error = " + e.getMessage());
		}
		context.getLogger().log("getEPCListFromDB ::: DB ResultSet = "+gtinUriList);
		return gtinUriList;

	}
	

	private void insertErrorLog(int eventTypeId, String msg, String fileName, int objEventCount, int aggEventCount, String gtinInfo, String source, String destination ) {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  //2024-04-05 20:31:02
		String strDate= formatter.format(date);
		
		 
	 String query = "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, "
	 		+ "document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text) \r\n"
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
	 		+ "3, -- auth failed --\r\n"
	 		+ "'"+fileName+"' , -- the document name (jjoshi/4K_events_05062024.xml)\r\n"
	 		+ objEventCount+ ", -- Object event counts\r\n"
	 		+ aggEventCount+ ",  -- Aggregation event counts\r\n"
	 		+ "'"+msg+"', -- Exception detail\r\n"
	 		+ "'"+strDate+"',\r\n"
	 		+ "'tiop_auth', -- id that insert data in tiopdb\r\n"
	 		+ "'"+strDate+"',\r\n"
	 		+ "'tiop_auth', -- id that insert data in tiopdb\r\n"
	 		+ "'A',\r\n"
	 		+ "''); ";
	 
	 
		try {
			context.getLogger().log("insertErrorLog ::: Start");
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
	
	private void insertBasicInfo(String fileName, int objEventCount, int aggEventCount, String gtinInfo, String source, String destination) {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String strDate= formatter.format(date);
	 
	 String query = ""
	 		+ "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text) \r\n"
	 		+ "VALUES (\r\n"
	 		+ "null, -- insert null for this senerio \r\n"
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
	 		+ "6, -- Recieved --\r\n"
	 		+ "'"+fileName+"' , -- the document name (jjoshi/4K_events_05062024.xml)\r\n"
	 		+ objEventCount+ ", -- Object event counts\r\n"
	 		+ aggEventCount+ ",  -- Aggregation event counts\r\n"
	 		+ "null, -- Exception detail\r\n"
	 		+ "'"+strDate+"',\r\n"
	 		+ "'tiop_auth', -- id that insert data in tiopdb\r\n"
	 		+ "'"+strDate+"',\r\n"
	 		+ "'tiop_auth', -- id that insert data in tiopdb\r\n"
	 		+ "'A',\r\n"
	 		+ "'');   ";
	 
	 
		try {
			context.getLogger().log("insertBasicInfo ::: Start");
			con = getConnection();
			//context.getLogger().log("insertErrorLog ::: con = "+con);
			Statement stmt = con.createStatement();
			context.getLogger().log("insertBasicInfo ::: query = "+query);
			stmt.executeUpdate(query);
			context.getLogger().log("insertBasicInfo ::: query inserted successfully");
		} catch (Exception e) {
			context.getLogger().log("insertBasicInfo ::: db error = " + e.getMessage());
		}
	}

}
