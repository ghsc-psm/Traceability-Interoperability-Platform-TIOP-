package com.usaid;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TransfromLambdaHandler implements RequestHandler<Object, String> {

	private Connection con;
	private String secretDetails;
	private String smtpSecret;

	@Override
	public String handleRequest(Object event, Context context) {
		context.getLogger().log("TransfromLambdaHandler::handleRequest::Start");
		String fileName = null;
		String bucketName = null;
		String source = null;
		String destination = null;
		String gtinInfo = null;
		int objEventCount = 0;
		int aggEventCount = 0;
		
		DataOutputStream wr = null;
		HttpURLConnection connection = null;

		S3Object s3object = null;
		S3ObjectInputStream inputStream = null;

		if (event instanceof LinkedHashMap) {
			LinkedHashMap<String, String> mapEvent = (LinkedHashMap<String, String>) event;
			fileName = mapEvent.get("fileName");
			bucketName = mapEvent.get("bucketName");

			source = mapEvent.get("source");
			destination = mapEvent.get("destination");
			gtinInfo = mapEvent.get("gtinInfo");

			String objCount = mapEvent.get("objEventCount");
			if (objCount != null)
				objEventCount = Integer.parseInt(objCount);
			String aggCount = mapEvent.get("aggEventCount");
			if (aggCount != null)
				aggEventCount = Integer.parseInt(aggCount);

			context.getLogger().log("TransfromLambdaHandler::fileName = " + fileName);
			context.getLogger().log("TransfromLambdaHandler::bucketName = " + bucketName);
			
			secretDetails = mapEvent.get("secretDetails");
			smtpSecret = mapEvent.get("smtpSecretName");
			
		}

		try {
			context.getLogger().log("-----------------------------------TransfromLambdaHandler::1");
			AmazonS3 s3client = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
					.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
			s3object = s3client.getObject(bucketName, fileName);
			inputStream = s3object.getObjectContent();
			StringBuilder textBuilder = new StringBuilder();

			Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			int c = 0;
			while ((c = reader.read()) != -1) {
				textBuilder.append((char) c);
			}
			
			this.con = getConnection(secretDetails);
			
			context.getLogger().log("-----------------------------------TransfromLambdaHandler::con = "+con);
			
			context.getLogger().log("-----------------------------------TransfromLambdaHandler::2");
			String urlString = System.getenv(TIOPConstants.xmlToJsonConversionURL);  
			String jsonInputString = textBuilder.toString();
			// Create a URL object
			URL url = new URL(urlString);
			// Open a connection
			connection = (HttpURLConnection) url.openConnection();
			try {
				// Set the request method to POST
				connection.setRequestMethod("POST");
				// Set request headers
				connection.setRequestProperty("Content-Type", "application/xml; utf-8");
				// connection.setRequestProperty("Accept", "application/xml");
				connection.setDoOutput(true);
				connection.setConnectTimeout(900000);
				// Write the request body

				context.getLogger().log("-----------------------------------TransfromLambdaHandler::3 -- "+urlString);
				wr = new DataOutputStream(connection.getOutputStream());
				wr.writeBytes(jsonInputString);
				wr.flush();
				context.getLogger().log("-----------------------------------TransfromLambdaHandler::4");
			} catch (Exception e) {
				context.getLogger().log("-----------------------------------TransfromLambdaHandler::5 error = "+e.getMessage());
				throw new TIOPException(e.getMessage());
			}
			// Get the response code
			int responseCode = connection.getResponseCode();
			context.getLogger().log("-----------------------------------TransfromLambdaHandler::6");
			context.getLogger().log("TransfromLambdaHandler:: -- responseCode = " + responseCode);
			if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) { // success
				// Read the response
				context.getLogger().log("-----------------------------------TransfromLambdaHandler::7");
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();
				String transformedJson = response.toString();
				
				ObjectMapper mapper = new ObjectMapper();
				mapper.configure(Feature.AUTO_CLOSE_SOURCE, true);

		        // read the json strings and convert it into JsonNode
		        JsonNode node = mapper.readTree(transformedJson);
		        context.getLogger().log("-----------------------------------TransfromLambdaHandler::8");

		        // display the JsonNode
		          
		        JsonNode epcisBody = node.get("epcisBody");
				if (epcisBody != null) {
					JsonNode eventList = epcisBody.get("eventList");
					int jsonObjEventCount = 0;
					int jsonAggEventCount = 0;
					//StringBuilder payload = new StringBuilder();
					for (int i = 0; i < eventList.size(); i++) {
						JsonNode chNode = eventList.get(i);
						//String chNodeStr = chNode.toString();
						String eventType = chNode.get("type").toString();
						//context.getLogger().log("-----------chNodeStr1 = "+chNodeStr);
						//chNodeStr = chNodeStr.replaceAll("tiop:", "");
						if (eventType.contains(TIOPConstants.ObjectEvent)) {
							jsonObjEventCount++;
						} else if (eventType.contains(TIOPConstants.AggregationEvent)) {
							jsonAggEventCount++;
						}

					}
				
					context.getLogger().log("jsonObjEventCount = " + jsonObjEventCount);
					context.getLogger().log("jsonAggEventCount = " + jsonAggEventCount);

					if (jsonObjEventCount == objEventCount && jsonAggEventCount == aggEventCount) {
						context.getLogger().log("TransfromLambdaHandler::XML and JSON Event conts are same");
					} else {
						context.getLogger().log("TransfromLambdaHandler::ERROR::XML and JSON Event conts are different. Log the error.");
						Date date = new Date();
						SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
						String strDate = formatter.format(date);
						String detailMsg = "Event counts mismatch between original XML 1.2 version document and converted JSON 2.0 document.";
						final String htmlBody = "<h4>An issue [EXC007] encountered while processing the file "
								+ fileName + " which was received on " + strDate + ".</h4>"
								+ "<h4>Details of the Issue:</h4>" + "<p>" + detailMsg + "</p>"
								+ "<p>TIOP operation team</p>";
						insertTransformationErrorLog(context, detailMsg, fileName, objEventCount, aggEventCount,
								gtinInfo, source, destination);
						// TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);
						sendMail(fileName, htmlBody);
						return "XML and JSON Event conts are different.";
					}

					fileName = fileName.replaceFirst(".xml", ".json");
					AmazonS3 s3client1 = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
							.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
					ObjectMetadata metadata = new ObjectMetadata();
					InputStream streamIn = new ByteArrayInputStream(transformedJson.getBytes());
					metadata.setContentLength(streamIn.available());
					PutObjectRequest putRequest = new PutObjectRequest(System.getenv(TIOPConstants.destinationS3), fileName, streamIn, metadata);
					s3client1.putObject(putRequest);
					insertTransformationInfo(context, fileName, jsonObjEventCount, jsonAggEventCount, gtinInfo, source,	destination);
					
					
					context.getLogger().log("Transformation end successfully");
				} else {
					context.getLogger().log("-----------------------------------TransfromLambdaHandler::9");
					context.getLogger().log("Error case -- node = " + node);
					
					String status = node.get("status").toString();
					String detail = node.get("detail").toString();
					detail = detail.replaceAll("\"", "");
					detail = detail.replaceAll("\n", " ");
					context.getLogger().log(status+"-------TransfromLambdaHandler::9.10-----------"+detail);
					
					String exp = "EXC009";
					String errorCode = "400";
					
					if (status.equals("400")) {
						exp = "EXC008";
						errorCode = "400";
					} else if (status.equals("500")) {
						exp = "EXC009";
						errorCode = "500";
					}

					Date date = new Date();
					SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
					String strDate = formatter.format(date);
					final String htmlBody = "<h4>An issue [" + exp + "] encountered while processing the file "	+ fileName + " which was received on " + strDate + ".</h4>"
							+ "<h4>Details of the Issue:</h4>" 
							+ "<p>HTTP " + errorCode + " response from Document Conversion API. "+detail+"</p>" 
							+ "<p>TIOP operation team</p>";
					insertTransformationErrorLog(context, detail.toString(), fileName, objEventCount, aggEventCount, gtinInfo,
							source, destination);
					sendMail(fileName, htmlBody);
					context.getLogger().log("-------TransfromLambdaHandler::9.20-----------");
					return errorCode + " response from Document Conversion API";

				}
			} else {
				context.getLogger().log("POST request failed. Response Code:::: " + responseCode);
			}
			context.getLogger().log("-----------------------------------TransfromLambdaHandler::10");

			// Disconnect the connection

			connection.disconnect();

		} catch (Exception e) {
			context.getLogger().log("TransfromLambdaHandler Error ::: -- " + e.getMessage());
			e.printStackTrace();
			if(e.getMessage() != null) {
				Date date = new Date();
				SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
				String strDate = formatter.format(date);
				final String htmlBody = "<h4>An issue [EXC009] encountered while processing the file " + fileName
						+ " which was received on " + strDate + ".</h4>" + "<h4>Details of the Issue:</h4>" + "<p>"
						+ e.getMessage() + "</p>" + "<p>TIOP operation team</p>";
				insertTransformationErrorLog(context, e.getMessage(), fileName, objEventCount, aggEventCount, gtinInfo,	source, destination);
				// TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);
				sendMail(fileName, htmlBody);
			}

		} finally {
			try {
				source = null;
				destination = null;
				if (wr != null) wr.close();
				if (con != null && !con.isClosed())	con.close();
				if(connection != null) connection.disconnect();
				if (inputStream != null) inputStream.close();
				if (s3object != null) s3object.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			context.getLogger().log("Transformation finally end");
		}

		return "Transformation end successfully";
	}
	

	private void sendMail(String fileName, final String htmlBody) {
		String smtpHost = TIOPUtil.getKeyValue(smtpSecret, "smtpHost");
		String username = TIOPUtil.getKeyValue(smtpSecret, "smtpUser");
		String password = TIOPUtil.getKeyValue(smtpSecret, "smtpPassword");
		String smtPort = TIOPUtil.getKeyValue(smtpSecret, "smtpPort");
		
		String env = System.getenv(TIOPConstants.env);
		final String subject = "["+env.toUpperCase()+"] File Processing Issue: ["+fileName+"] - Attention Needed";
		// Set up the SMTP server properties
		Properties props = new Properties();
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", smtpHost); // SMTP server address
		props.put("mail.smtp.port", smtPort);
		props.put("mail.smtp.ssl.trust", smtpHost);
		props.put("mail.smtp.ssl.protocols", "TLSv1.2");
		// Get the Session object
		Session session = Session.getInstance(props, new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(username, password);
			}
		});

		try {
			// Create a default MimeMessage object
			Message message = new MimeMessage(session);
			// Set From: header field
			message.setFrom(new InternetAddress(System.getenv(TIOPConstants.toEmailId)));
			// Set To: header field
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(System.getenv(TIOPConstants.fromEmailId)));
			// Set Subject: header field
			message.setSubject(subject);
			// Set the actual message
			message.setContent(htmlBody, "text/html");
			// Send message
			Transport.send(message);
			// sendemail-send");
		} catch (MessagingException e) {
			e.printStackTrace();
		}
	}

	private Connection getConnection(String secretDetails) throws ClassNotFoundException, SQLException {
		if (con == null || con.isClosed()) {
			String username = TIOPUtil.getKeyValue(secretDetails, "username");
			String password = TIOPUtil.getKeyValue(secretDetails, "password");
			String host = TIOPUtil.getKeyValue(secretDetails, "host");
			String port = TIOPUtil.getKeyValue(secretDetails, "port");
			//String dbInstanceIdentifier = getKeyValue(secretDetails, "dbInstanceIdentifier");
			String dbUrl = "jdbc:mysql://"+host+":"+port+"/tiopdb";
			System.out.println("TIOPUtil::getConnection::dbUrl = "+dbUrl);
			Class.forName(TIOPConstants.dbdriver);
			con = DriverManager.getConnection(dbUrl, username, password);
		}
		return con;
	}

	private void insertTransformationInfo(Context context, String fileName, int objEventCount, int aggEventCount,
			String gtinInfo, String source, String destination) {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String strDate = formatter.format(date);

		String query = "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text) \r\n"
				+ "VALUES (\r\n" 
				+ "null, -- insert null for this senerio \r\n" 
				+ "(select distinct stp.partner_id\r\n"
				+ "from location sl\r\n" + "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"+ source + "'),\r\n" 
				+ "(select distinct  dtp.partner_id\r\n" + "from location dl\r\n"
				+ "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"+ destination + "'),\r\n" 
				+ "(select distinct sl.location_id\r\n" + "from location sl\r\n"
				+ "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"+ source + "'),\r\n" 
				+ "(select distinct  dl.location_id\r\n" + "from location dl\r\n"
				+ "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='" + destination + "'),\r\n" 
				+ "(select distinct ti.item_id\r\n"
				+ "from trade_item ti where ti.current_indicator='A' and gtin_uri ='" + gtinInfo + "'),\r\n"
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
				+ "and sl.gln_uri = '" + source + "' \r\n" 
				+ "and dl.gln = '"	+ destination 
				+ "'\r\n" + "and ti.gtin_uri ='" + gtinInfo + "'),\r\n" 
				+ "7, -- Recieved --\r\n" 
				+ "'"	+ fileName + "' , -- the transformed JSON document name (jjoshi/4K_events_05062024.JSON)\r\n"
				+ objEventCount + ", -- Object event counts\r\n" 
				+ aggEventCount	+ ",  -- Aggregation event counts\r\n" 
				+ "null, -- Exception detail\r\n" 
				+ "'" + strDate + "',\r\n"
				+ "'tiop_transformation', -- id that insert data in tiopdb\r\n" 
				+ "'" + strDate + "',\r\n"
				+ "'tiop_transformation', -- id that insert data in tiopdb\r\n" 
				+ "'A',\r\n" + "'');";
		try {
			// context.getLogger().log("insertErrorLog ::: Start");
			con = getConnection(this.secretDetails);
			// context.getLogger().log("insertErrorLog ::: con = "+con);
			Statement stmt = con.createStatement();
			context.getLogger().log("insertBasicInfo ::: query = " + query);
			stmt.executeUpdate(query);
			context.getLogger().log("insertBasicInfo ::: query inserted successfully");
		} catch (Exception e) {
			context.getLogger().log("insertBasicInfo ::: db error = " + e.getMessage());
		}
	}

	private void insertTransformationErrorLog(Context context, String msg, String fileName, int objEventCount,
			int aggEventCount, String gtinInfo, String source, String destination) {
		if (msg != null) msg = msg.replaceAll("\"", "");
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // 2024-04-05 20:31:02
		String strDate = formatter.format(date);

		String query = "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text) \r\n"
				+ "VALUES (\r\n" 
				+ "null, -- 1 (Object Event), 2 (Aggregation Event)\r\n"
				+ "(select distinct stp.partner_id\r\n" 
				+ "from location sl\r\n" 
				+ "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='" + source + "'),\r\n" 
				+ "(select distinct  dtp.partner_id\r\n" 
				+ "from location dl\r\n"
				+ "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='" + destination + "'),\r\n" 
				+ "(select distinct sl.location_id\r\n" 
				+ "from location sl\r\n"
				+ "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='" + source + "'),\r\n" 
				+ "(select distinct  dl.location_id\r\n" 
				+ "from location dl\r\n"
				+ "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='" + destination + "'),\r\n" 
				+ "(select distinct ti.item_id\r\n"
				+ "from trade_item ti where ti.current_indicator='A' and gtin_uri ='" + gtinInfo + "'),\r\n"
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
				+ "and sl.gln_uri = '" + source + "' \r\n" 
				+ "and dl.gln = '" + destination + "'\r\n" 
				+ "and ti.gtin_uri ='" + gtinInfo + "'),\r\n"
				+ "5, -- transformation failed --\r\n" 
				+ "'" + fileName + "' , -- the document name (jjoshi/4K_events_05062024.xml)\r\n" 
				+ "null, -- Object event counts\r\n"
				+ "null,  -- Aggregation event counts\r\n" 
				+ "'" + msg
				+ "',  -- Event counts mismatch between original XML 1.2 version document and converted JSON 2.0 document (1200, 1000) -- Exception detail\r\n"
				+ "'" + strDate + "',\r\n" 
				+ "'tiop_transformation', -- id that insert data in tiopdb\r\n" + "'"
				+ strDate + "',\r\n" 
				+ "'tiop_transformation', -- id that insert data in tiopdb\r\n" 
				+ "'A',\r\n"
				+ "''); ";

		try {
			context.getLogger().log("insertErrorLog ::: Start");
			con = getConnection(this.secretDetails);
			//context.getLogger().log("insertErrorLog ::: con = " + con);
			Statement stmt = con.createStatement();
			// context.getLogger().log("insertErrorLog ::: query = "+query);
			stmt.executeUpdate(query);
			context.getLogger().log("insertErrorLog ::: query inserted successfully for ");
		} catch (Exception e) {
			context.getLogger().log("insertErrorLog ::: db error = " + e.getMessage());
		}
	}

}
