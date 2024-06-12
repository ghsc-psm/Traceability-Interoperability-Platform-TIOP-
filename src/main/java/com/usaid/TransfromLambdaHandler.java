package com.usaid;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

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

public class TransfromLambdaHandler implements RequestHandler<Object, String> {

	private String dburl = "jdbc:mysql://tiopdevtestdb.cx8a24s68i3t.us-east-1.rds.amazonaws.com:3306/tiopdb";
	private String dbuser = "schatterjee";
	private String dbpass = "Test!234";
	private Connection con;

	@Override
	public String handleRequest(Object event, Context context) {
		context.getLogger().log("TransfromLambdaHandler::handleRequest::Start");
		String fileName = null;
		String fileContent = null;
		String source = null;
		String destination = null;
		String gtinInfo = null;
		int objEventCount = 0;
		int aggEventCount = 0;
		int jsonObjEventCount = 0;
		int jsonAggEventCount = 0;

		if (event instanceof LinkedHashMap) {
			LinkedHashMap<String, String> mapEvent = (LinkedHashMap<String, String>) event;
			fileName = mapEvent.get("fileName");
			fileContent = mapEvent.get("fileContent");

			source = mapEvent.get("source");
			destination = mapEvent.get("destination");
			gtinInfo = mapEvent.get("gtinInfo");

			String objCount = mapEvent.get("objEventCount");
			if (objCount != null)
				objEventCount = Integer.parseInt(objCount);
			String aggCount = mapEvent.get("aggEventCount");
			if (aggCount != null)
				aggEventCount = Integer.parseInt(aggCount);

			context.getLogger().log("TransfromLambdaHandler::Total ObjectEvent count = " + objEventCount);
			context.getLogger().log("TransfromLambdaHandler::Total AggregationEvent count = " + aggEventCount);
			context.getLogger().log("TransfromLambdaHandler::source = " + source);
			context.getLogger().log("TransfromLambdaHandler::destination = " + destination);
			context.getLogger().log("TransfromLambdaHandler::gtinInfo = " + gtinInfo);
			context.getLogger().log("TransfromLambdaHandler::fileName = " + fileName);
		}

		try {
			context.getLogger().log("-----------------------------------TransfromLambdaHandler::1");
			String urlString = "http://ec2-3-89-88-184.compute-1.amazonaws.com:8080/api/convert/json/2.0";
			String jsonInputString = fileContent;

			// Create a URL object
			URL url = new URL(urlString);
			// Open a connection
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			// Set the request method to POST
			connection.setRequestMethod("POST");
			// Set request headers
			connection.setRequestProperty("Content-Type", "application/xml; utf-8");
			// connection.setRequestProperty("Accept", "application/xml");
			connection.setDoOutput(true);
			// Write the request body
			try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
				wr.writeBytes(jsonInputString);
				wr.flush();
			}
			// Get the response code
			int responseCode = connection.getResponseCode();
			context.getLogger().log("TransfromLambdaHandler:: -- responseCode = " + responseCode);
			if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) { // success
				// Read the response
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();
				String transformedJson = response.toString();
				// Print the response
				// context.getLogger().log("Response: " + transformedJson);
				try {
					JSONParser parser = new JSONParser();
					JSONObject jsonObject = (JSONObject) parser.parse(transformedJson);
					JSONObject epcisBody = (JSONObject) jsonObject.get("epcisBody");
					if (epcisBody != null) {
						JSONArray eventList = (JSONArray) epcisBody.get("eventList");
						context.getLogger().log("Total eventList size = " + eventList.size());

						
						Iterator iterator = eventList.iterator();
						while (iterator.hasNext()) {
							JSONObject eventObj = (JSONObject) iterator.next();
							String eventType = (String) eventObj.get("type");
							if (eventType.equals("ObjectEvent")) {
								jsonObjEventCount++;
							} else if (eventType.equals("AggregationEvent")) {
								jsonAggEventCount++;
							}
						}
						context.getLogger().log("jsonObjEventCount = " + jsonObjEventCount);
						context.getLogger().log("jsonAggEventCount = " + jsonAggEventCount);

						if (jsonObjEventCount == objEventCount && jsonAggEventCount == aggEventCount) {
							context.getLogger().log("TransfromLambdaHandler::XML and JSON Event conts are same");
						} else {
							context.getLogger().log(
									"TransfromLambdaHandler::ERROR::XML and JSON Event conts are different. Log the error.");
							Date date = new Date();
							SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
							String strDate = formatter.format(date);
							String detailMsg = "Event counts mismatch between original XML 1.2 version document and converted JSON 2.0 document.";
							final String htmlBody = "<h4>An issue [EXC007] encountered while processing the file "
									+ fileName + " which was received on " + strDate + ".</h4>"
									+ "<h4>Details of the Issue:</h4>" + "<p>" + detailMsg + "</p>"
									+ "<p>TIOP operation team</p>";
							insertTransformationErrorLog(context, detailMsg, fileName, objEventCount, aggEventCount, gtinInfo,
									source, destination);
							TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);
						}

						fileName = fileName.replaceFirst(".xml", ".json");
						context.getLogger().log("----------------------------------- insert json info start");
						// insertTransformationfo(context, null, fileName, jsonObjEventCount,
						// jsonAggEventCount, gtinInfo, source, destination);
						context.getLogger().log("----------------------------------- insert json info end");
						fileName = fileName.replaceFirst(".xml", ".json");
						String bucketName = "epcis2.0documents";
						context.getLogger().log(
								"Transformation start -- fileName = " + fileName + " --- bucketName = " + bucketName);

						AmazonS3 s3client = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
								.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
						ObjectMetadata metadata = new ObjectMetadata();
						InputStream streamIn = new ByteArrayInputStream(transformedJson.getBytes());
						metadata.setContentLength(streamIn.available());
						PutObjectRequest putRequest = new PutObjectRequest("epcis2.0documents", fileName, streamIn,
								metadata);
						s3client.putObject(putRequest);
						insertTransformationfo(context, fileName, jsonObjEventCount, jsonAggEventCount, gtinInfo, source, destination);
						context.getLogger().log("Transformation end successfully");
					} else {
						String type = (String) jsonObject.get("type");
						String title = (String) jsonObject.get("title");
						Long status = (Long) jsonObject.get("status");
						String detail = (String) jsonObject.get("detail");
						// String course = (String)jsonObject.get("Course");

						context.getLogger().log("type = " + type);
						context.getLogger().log("title = " + title);
						context.getLogger().log("status = " + status);
						context.getLogger().log("detail = " + detail);

						String exp = "EXC009";
						String errorCode = "HTTP400";
						if (status == 400) {
							exp = "EXC008";
							errorCode = "HTTP400";
						} else if (status == 500) {
							exp = "EXC009";
							errorCode = "HTTP500";
						}

						Date date = new Date();
						SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
						String strDate = formatter.format(date);
						final String htmlBody = "<h4>An issue [" + exp + "] encountered while processing the file "
								+ fileName + " which was received on " + strDate + ".</h4>"
								+ "<h4>Details of the Issue:</h4>" + "<p>HTTP " + errorCode
								+ " response from Document Conversion API</p>" // Event counts mismatch between original
																				// XML 1.2 version document and
																				// converted JSON 2.0 document.
								+ "<p>TIOP operation team</p>";
						insertTransformationErrorLog(context, detail, fileName, objEventCount, aggEventCount, gtinInfo, source, destination);
						TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);

					}
				} catch (Exception e) {
					context.getLogger().log("error while persing json object =  " + e.getMessage());
					e.printStackTrace();
				}

			} else {
				context.getLogger().log("POST request failed. Response Code:::: " + responseCode);
			}

			// Disconnect the connection
			
			connection.disconnect();

		} catch (Exception e) {
			context.getLogger().log("Error while reading file from S3 :::" + e.getMessage());
			// e.printStackTrace();
			Date date = new Date();
			SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
			String strDate = formatter.format(date);
			final String htmlBody = "<h4>An issue EXC009 encountered while processing the file " + fileName
					+ " which was received on " + strDate + ".</h4>" + "<h4>Details of the Issue:</h4>" + "<p>"
					+ e.getMessage() + "</p>" // Event counts mismatch between original XML 1.2 version document and
												// converted JSON 2.0 document.
					+ "<p>TIOP operation team</p>";
			insertTransformationErrorLog(context, e.getMessage(), fileName, objEventCount, aggEventCount, gtinInfo, source,
					destination);
			TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);

			return "Error while reading file from S3 :::" + e.getMessage();

		} finally {
			context.getLogger().log("----------------------------------- finally end");
		}

		return "Transformation end successfully";
	}

	private Connection getConnection() throws ClassNotFoundException, SQLException {
		if (con == null || con.isClosed()) {
			Class.forName("com.mysql.jdbc.Driver");
			con = DriverManager.getConnection(dburl, dbuser, dbpass);
		}
		return con;
	}

	private void insertTransformationfo(Context context, String fileName, int objEventCount, int aggEventCount, String gtinInfo,
			String source, String destination) {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String strDate = formatter.format(date);

		String query = "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text) \r\n"
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
				+ "7, -- Recieved --\r\n"
				+ "'jjoshi/4K_events_05062024.json' , -- the transformed JSON document name (jjoshi/4K_events_05062024.JSON)\r\n"
				+ objEventCount+ "4000, -- Object event counts\r\n"
				+ aggEventCount+ "50,  -- Aggregation event counts\r\n"
				+ "null, -- Exception detail\r\n"
				+ "'"+strDate+"',\r\n"
				+ "'tiop_transformation', -- id that insert data in tiopdb\r\n"
				+ "'"+strDate+"',\r\n"
				+ "'tiop_transformation', -- id that insert data in tiopdb\r\n"
				+ "'A',\r\n"
				+ "'');";
		try {
			// context.getLogger().log("insertErrorLog ::: Start");
			con = getConnection();
			// context.getLogger().log("insertErrorLog ::: con = "+con);
			Statement stmt = con.createStatement();
			context.getLogger().log("insertBasicInfo ::: query = " + query);
			stmt.executeUpdate(query);
			context.getLogger().log("insertBasicInfo ::: query inserted successfully");
		} catch (Exception e) {
			context.getLogger().log("insertBasicInfo ::: db error = " + e.getMessage());
		}
	}
	
	private void insertTransformationErrorLog(Context context, String msg, String fileName, int objEventCount, int aggEventCount, String gtinInfo, String source, String destination ) {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  //2024-04-05 20:31:02
		String strDate= formatter.format(date);
		
		 
	 String query = "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text) \r\n"
	 		+ "VALUES (\r\n"
	 		+ "null, -- 1 (Object Event), 2 (Aggregation Event)\r\n"
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
	 		+ "5, -- transformation failed --\r\n"
	 		+ "'jjoshi/4K_events_05062024.xml' , -- the document name (jjoshi/4K_events_05062024.xml)\r\n"
	 		+ "null, -- Object event counts\r\n"
	 		+ "null,  -- Aggregation event counts\r\n"
	 		+ "'"+msg+"',  -- Event counts mismatch between original XML 1.2 version document and converted JSON 2.0 document (1200, 1000) -- Exception detail\r\n"
	 		+ "'"+strDate+"',\r\n"
	 		+ "'tiop_transformation', -- id that insert data in tiopdb\r\n"
	 		+ "'"+strDate+"',\r\n"
	 		+ "'tiop_transformation', -- id that insert data in tiopdb\r\n"
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

	private void invokeS3PutLamda(Context context, String fileName, String fileContent) {
		context.getLogger().log("invoke TransTest start  == " + fileName);
		JSONObject payloadObject = new JSONObject();
		payloadObject.put("fileContent", fileContent);
		payloadObject.put("fileName", fileName);

		AWSLambda client = AWSLambdaAsyncClient.builder().withRegion(Regions.US_EAST_1).build();

		InvokeRequest request = new InvokeRequest();
		request.withFunctionName("arn:aws:lambda:us-east-1:654654535046:function:TransTest")
				.withPayload(payloadObject.toString());
		// context.getLogger().log("Calling TransfromLambdaHandler with payload =
		// "+payloadObject.toString());
		InvokeResult invoke = client.invoke(request);
		context.getLogger().log("invoke TransTest done ");

	}

}
