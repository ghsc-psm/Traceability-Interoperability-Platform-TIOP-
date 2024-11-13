package com.usaid;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usaid.exceptions.RouterConfigException;

public class RouterLambdaHandler implements RequestHandler<Object, String> {

	private Connection con;

	@Override
	public String handleRequest(Object event, Context context) {
		context.getLogger().log("RouterLambdaHandler::handleRequest::Start");
		String fileName = null;
		String bucketName = null;
		String source = null;
		String destination = null;
		String gtinInfo = null;
		int objEventCount = 0;
		int aggEventCount = 0;

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
			context.getLogger()
					.log("RouterLambdaHandler::Total ObjectEvent count = " + objEventCount);
			context.getLogger()
					.log("RouterLambdaHandler::Total AggregationEvent count = " + aggEventCount);
			context.getLogger().log("RouterLambdaHandler::source = " + source);
			context.getLogger().log("RouterLambdaHandler::destination = " + destination);
			context.getLogger().log("RouterLambdaHandler::gtinInfo = " + gtinInfo);
			context.getLogger().log("RouterLambdaHandler::fileName = " + fileName);
			context.getLogger().log("RouterLambdaHandler::bucketName = " + bucketName);

			String secretName = "";
			try {
				String routerInfo = getRouterInfo(context, destination);
				context.getLogger().log("RouterLambdaHandler::getRouterInfo = " + routerInfo);
				if (routerInfo == null || routerInfo.isBlank()) {
					throw new RouterConfigException("EXC010",
							"An error occurred while routing the EPCIS document. Routing record does not exist for recipient GLN ["
									+ destination + "].");

					// [6151100444677]."

				} else if (routerInfo != null && routerInfo.contains("#")) {
					secretName = routerInfo.split("#")[2];
				}
				context.getLogger().log("RouterLambdaHandler::secretName = " + secretName);
				String countryrouting = TIOPUtil.getSecretDetails(secretName);
				String apiURL = TIOPUtil.getKeyValue(countryrouting, "APIURL");
				String bearerToken = TIOPUtil.getKeyValue(countryrouting, "BearerToken");

				context.getLogger().log("RouterLambdaHandler::handleRequest::apiURL = " + apiURL);
				// context.getLogger().log("RouterLambdaHandler::handleRequest::bearerToken =
				// "+bearerToken);

				AmazonS3 s3client = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
						.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
				s3object = s3client.getObject(bucketName, fileName);
				inputStream = s3object.getObjectContent();
				StringBuilder textBuilder = new StringBuilder();

				Reader reader = new BufferedReader(
						new InputStreamReader(inputStream, StandardCharsets.UTF_8));
				char[] buffer = new char[1024];
				int numCharsRead;
				while ((numCharsRead = reader.read(buffer)) != -1) {
					textBuilder.append(buffer, 0, numCharsRead);
				}
				// context.getLogger().log("RouterLambdaHandler::handleRequest::INPUT XML =
				// "+textBuilder.toString());

				bearerToken = "Bearer " + bearerToken;

				// context.getLogger().log("RouterLambdaHandler::handleRequest::1 -- bearerToken
				// = "+bearerToken);

				HttpPost request = new HttpPost(apiURL);
				StringEntity se = new StringEntity(textBuilder.toString());
				request.setHeader("Content-Type", "application/xml");
				request.setHeader("Authorization", bearerToken);
				request.setEntity(se);

				CloseableHttpClient httpClient = HttpClientBuilder.create().build();
				long startTime = System.currentTimeMillis();
				context.getLogger().log("HTTP request started at: " + startTime);

				try (CloseableHttpResponse response = httpClient.execute(request)) {
					long endTime = System.currentTimeMillis();

					context.getLogger().log("HTTP request completed at: " + endTime);
					context.getLogger().log("Time taken: " + (endTime - startTime) + " ms");
					int status = response.getStatusLine().getStatusCode();
					String body = new String(response.getEntity().getContent().readAllBytes());

					context.getLogger().log("Response status---> " + status);
					context.getLogger().log("Response response ---> " + body);

					if (status == 200) {
						insertRouterInfo(context, fileName, objEventCount, aggEventCount, gtinInfo,
								source, destination);
					} else if (status == 401) {

						Header authHeader = response.getFirstHeader("WWW-Authenticate");
						if (authHeader != null) {
							String authHeaderValue = authHeader.getValue();
							context.getLogger().log("WWW-Authenticate Header: " + authHeaderValue);

							// Step 2: Parse the header to extract `error` and `error_description`
							String error = null;
							String errorDescription = null;

							// Check if the header contains "error" and "error_description"
							if (authHeaderValue.contains("error=")) {
								error = authHeaderValue.split("error=")[1].split(",")[0]
										.replace("\"", "").trim();
							}
							if (authHeaderValue.contains("error_description=")) {
								errorDescription = authHeaderValue.split("error_description=")[1]
										.replace("\"", "").trim();
								errorDescription = errorDescription.replace("'", "''");
							}

							// Print or log the extracted values
							context.getLogger().log("Error: " + error);
							context.getLogger().log("Error Description: " + errorDescription);

							insertRouterErrorLog(context, errorDescription, fileName, objEventCount,
									aggEventCount, gtinInfo, source, destination);
							Date date = new Date();
							SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
							String strDate = formatter.format(date);
							final String htmlBody = "<h4>An issue [EXC010] encountered while processing the file "
									+ fileName + " which was received on " + strDate + ".</h4>"
									+ "<h4>Details of the Issue:</h4>" + "<p>An error occurred (HTTP "
									+ status + ") while routing the EPCIS document. " + errorDescription
									+ "</p>" + "<p>TIOP operation team</p>";
							TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);
						}
					} else {
						ObjectMapper mapper = new ObjectMapper();
						JsonNode bodyNode = mapper.readTree(body);
						String message = null;
						JsonNode messageNode = bodyNode.get("message");

						if (messageNode == null) {
							messageNode = bodyNode.get("errors");
							if (messageNode != null) {
								message = messageNode.toString();
								message = message.split(":")[1];
								message = message.replaceAll("}", "");
							}
						} else {
							message = messageNode.toString();
						}

						context.getLogger().log("raw message -- " + message);

						if (message != null) {
							message = message.replaceAll("[\\[\\]]", "");
							message = message.replaceAll("\"", "");
							context.getLogger().log("final message -- " + message);
							insertRouterErrorLog(context, message, fileName, objEventCount,
									aggEventCount, gtinInfo, source, destination);
							Date date = new Date();
							SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
							String strDate = formatter.format(date);
							final String htmlBody = "<h4>An issue [EXC010] encountered while processing the file "
									+ fileName + " which was received on " + strDate + ".</h4>"
									+ "<h4>Details of the Issue:</h4>"
									+ "<p>An error occurred (HTTP " + status
									+ ") while routing the EPCIS document. " + message + "</p>"
									+ "<p>TIOP operation team</p>";
							TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);
						}

					}

				}

			} catch (RouterConfigException rce) {

				rce.printStackTrace();
				String message = rce.getMessage();

				insertRouterErrorLog(context, message, fileName, objEventCount, aggEventCount,
						gtinInfo, source, destination);
				Date date = new Date();
				SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
				String strDate = formatter.format(date);
				final String htmlBody = "<h4>An issue [EXC010] encountered while processing the file "
						+ fileName + " which was received on " + strDate + ".</h4>"
						+ "<h4>Details of the Issue:</h4>"
						+ "<p>An error occurred while routing the EPCIS document. Routing record does not exist for recipient GLN ["
						+ destination + "].</p>" + "<p>TIOP operation team</p>";
				TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);

			} catch (Exception e) {
				e.printStackTrace();
				String message = e.getMessage();
				insertRouterErrorLog(context, message, fileName, objEventCount, aggEventCount,
						gtinInfo, source, destination);
				Date date = new Date();
				SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
				String strDate = formatter.format(date);
				final String htmlBody = "<h4>An issue [EXC010] encountered while processing the file "
						+ fileName + " which was received on " + strDate + ".</h4>"
						+ "<h4>Details of the Issue:</h4>"
						+ "<p>An error occurred while routing the EPCIS document. " + message
						+ "</p>" + "<p>TIOP operation team</p>";
				TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);

			}
		}

		return "Router success";
	}

	private Connection getConnection() throws ClassNotFoundException, SQLException {
		if (con == null || con.isClosed()) {
			con = TIOPUtil.getConnection();
		}
		return con;
	}

	private String getRouterInfo(Context context, String destination) throws Exception {
		context.getLogger().log("rdsDbTeat ::: Start");
		StringBuilder sb = new StringBuilder();
		String query = "select tr.route_type,tr.security_type, tr.secret_name, tr.secret_key, tr.document_version from location dl\r\n"
				+ "inner join tiop_route tr\r\n"
				+ "on dl.location_id =tr.destination_location_id\r\n"
				+ "where dl.current_indicator ='A'\r\n" + "and tr.current_indicator ='A'\r\n"
				+ "and dl.gln = '" + destination + "'";

		try {
			context.getLogger().log("getRouterInfo ::: Start");
			con = getConnection();
			// context.getLogger().log("getEPCListFromDB ::: con = "+con);
			Statement stmt = con.createStatement();
			context.getLogger().log("getRouterInfo ::: query = " + query);
			ResultSet rs = stmt.executeQuery(query);

			while (rs.next()) {
				sb.append(rs.getString(1));
				sb.append("#");
				sb.append(rs.getString(2));
				sb.append("#");
				sb.append(rs.getString(3));
			}

		} catch (Exception e) {
			context.getLogger().log("getRouterInfo ::: db error = " + e.getMessage());

			throw new Exception("Failed to fetch routing configuration from the database.");
		}
		context.getLogger().log("getRouterInfo ::: DB ResultSet = " + sb.toString());
		return sb.toString();

	}

	private void insertRouterInfo(Context context, String fileName, int objEventCount,
			int aggEventCount, String gtinInfo, String source, String destination) {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String strDate = formatter.format(date);

		String query = "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text) \r\n"
				+ "VALUES (\r\n" + "null, -- insert null for this senerio \r\n"
				+ "(select distinct stp.partner_id\r\n" + "from location sl\r\n"
				+ "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"
				+ source + "'),\r\n" + "(select distinct  dtp.partner_id\r\n"
				+ "from location dl\r\n" + "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"
				+ destination + "'),\r\n" + "(select distinct sl.location_id\r\n"
				+ "from location sl\r\n" + "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"
				+ source + "'),\r\n" + "(select distinct  dl.location_id\r\n"
				+ "from location dl\r\n" + "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"
				+ destination + "'),\r\n" + "(select distinct ti.item_id\r\n"
				+ "from trade_item ti where ti.current_indicator='A' and gtin_uri ='" + gtinInfo
				+ "'),\r\n" + "(select tr.rule_id from \r\n" + "tiop_rule tr \r\n"
				+ "inner join tiop_status ts \r\n" + "ON tr.status_id = ts.status_id \r\n"
				+ "inner join location sl\r\n" + "on tr.source_location_id = sl.location_id \r\n"
				+ "inner join location dl \r\n"
				+ "on tr.destination_location_id = dl.location_id\r\n"
				+ "inner join trade_item ti \r\n" + "on tr.item_id =ti.item_id \r\n" + "where\r\n"
				+ "ts.status_description ='Active'\r\n" + "and sl.current_indicator ='A'\r\n"
				+ "and dl.current_indicator ='A'\r\n" + "and ti.current_indicator ='A'\r\n"
				+ "and sl.gln_uri = '" + source + "' \r\n" + "and dl.gln = '" + destination
				+ "'\r\n" + "and ti.gtin_uri ='" + gtinInfo + "'),\r\n" + "9, -- Delivered --\r\n"
				+ "'" + fileName
				+ "' , -- the delivered xml document name (jjoshi/4K_events_05062024.xml)\r\n"
				+ objEventCount + ", -- Object event counts\r\n" + aggEventCount
				+ ",  -- Aggregation event counts\r\n" + "null, -- Exception detail\r\n" + "'"
				+ strDate + "',\r\n" + "'tiop_route', -- id that insert data in tiopdb\r\n" + "'"
				+ strDate + "',\r\n" + "'tiop_route', -- id that insert data in tiopdb\r\n"
				+ "'A',\r\n" + "'');";
		try {
			// context.getLogger().log("insertErrorLog ::: Start");
			con = getConnection();
			// context.getLogger().log("insertErrorLog ::: con = "+con);
			Statement stmt = con.createStatement();
			context.getLogger().log("insertRouterInfo ::: query = " + query);
			stmt.executeUpdate(query);
			context.getLogger().log("insertRouterInfo ::: query inserted successfully");
		} catch (Exception e) {
			context.getLogger().log("insertRouterInfo ::: db error = " + e.getMessage());
		}
	}

	private void insertRouterErrorLog(Context context, String msg, String fileName,
			int objEventCount, int aggEventCount, String gtinInfo, String source,
			String destination) {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // 2024-04-05
																					// 20:31:02
		String strDate = formatter.format(date);

		String query = "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text) \r\n"
				+ "VALUES (\r\n" + "null, -- insert null for this senerio \r\n"
				+ "(select distinct stp.partner_id\r\n" + "from location sl\r\n"
				+ "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"
				+ source + "'),\r\n" + "(select distinct  dtp.partner_id\r\n"
				+ "from location dl\r\n" + "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"
				+ destination + "'),\r\n" + "(select distinct sl.location_id\r\n"
				+ "from location sl\r\n" + "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"
				+ source + "'),\r\n" + "(select distinct  dl.location_id\r\n"
				+ "from location dl\r\n" + "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"
				+ destination + "'),\r\n" + "(select distinct ti.item_id\r\n"
				+ "from trade_item ti where ti.current_indicator='A' and gtin_uri ='" + gtinInfo
				+ "'),\r\n" + "(select tr.rule_id from \r\n" + "tiop_rule tr \r\n"
				+ "inner join tiop_status ts \r\n" + "ON tr.status_id = ts.status_id \r\n"
				+ "inner join location sl\r\n" + "on tr.source_location_id = sl.location_id \r\n"
				+ "inner join location dl \r\n"
				+ "on tr.destination_location_id = dl.location_id\r\n"
				+ "inner join trade_item ti \r\n" + "on tr.item_id =ti.item_id \r\n" + "where\r\n"
				+ "ts.status_description ='Active'\r\n" + "and sl.current_indicator ='A'\r\n"
				+ "and dl.current_indicator ='A'\r\n" + "and ti.current_indicator ='A'\r\n"
				+ "and sl.gln_uri = '" + source + "' \r\n" + "and dl.gln = '" + destination
				+ "'\r\n" + "and ti.gtin_uri ='" + gtinInfo + "'),\r\n"
				+ "8, -- Routing failed --\r\n" + "'" + fileName
				+ "' , -- the xml document name (jjoshi/4K_events_05062024.xml)\r\n" + objEventCount
				+ ", -- Object event counts\r\n" + aggEventCount
				+ ",  -- Aggregation event counts\r\n" + "'" + msg + "', -- Exception detail\r\n"
				+ "'" + strDate + "',\r\n" + "'tiop_route', -- id that insert data in tiopdb\r\n"
				+ "'" + strDate + "',\r\n" + "'tiop_route', -- id that insert data in tiopdb\r\n"
				+ "'A',\r\n" + "'');";

		try {
			context.getLogger().log("insertErrorLog ::: Start");
			con = getConnection();
			Statement stmt = con.createStatement();
			context.getLogger().log("insertErrorLog ::: query = " + query);
			stmt.executeUpdate(query);
			context.getLogger().log("insertErrorLog ::: query inserted successfully");
		} catch (Exception e) {
			context.getLogger().log("insertErrorLog ::: db error = " + e.getMessage());
		}
	}

	public String handleRequest() {
		System.out.println("RouterLambdaHandler::handleRequest::Start");
		String fileName = null;
		String bucketName = null;
		String source = null;
		String destination = null;
		String gtinInfo = null;
		int objEventCount = 0;
		int aggEventCount = 0;

		S3Object s3object = null;
		S3ObjectInputStream inputStream = null;

		// if (event instanceof LinkedHashMap) {
		// LinkedHashMap<String, String> mapEvent = (LinkedHashMap<String, String>)
		// event;
		fileName = "wirshad/EPCIS_v1_2__09_11_2024__06_31_07.xml";
		bucketName = "tiopsftpfilesdevtest";

		source = "urn:epc:id:sgln:8903726.02035.0";
		destination = "6151100444677";
		gtinInfo = "urn:epc:id:sgtin:8903726.026982";

		String objCount = "3";
		if (objCount != null)
			objEventCount = Integer.parseInt(objCount);
		String aggCount = "12";
		if (aggCount != null)
			aggEventCount = Integer.parseInt(aggCount);
		System.out.println("RouterLambdaHandler::Total ObjectEvent count = " + objEventCount);
		System.out.println("RouterLambdaHandler::Total AggregationEvent count = " + aggEventCount);
		System.out.println("RouterLambdaHandler::source = " + source);
		System.out.println("RouterLambdaHandler::destination = " + destination);
		System.out.println("RouterLambdaHandler::gtinInfo = " + gtinInfo);
		System.out.println("RouterLambdaHandler::fileName = " + fileName);
		System.out.println("RouterLambdaHandler::bucketName = " + bucketName);

		String secretName = "";
		try {
			String routerInfo = getRouterInfo(null, destination);
			System.out.println("RouterLambdaHandler::getRouterInfo = " + routerInfo);
			if (routerInfo == null || routerInfo.isBlank()) {
				throw new RouterConfigException("EXC010",
						"An error occurred while routing the EPCIS document. Routing record does not exist for recipient GLN ["
								+ destination + "].");

				// [6151100444677]."

			} else if (routerInfo != null && routerInfo.contains("#")) {
				secretName = routerInfo.split("#")[2];
			}
			System.out.println("RouterLambdaHandler::secretName = " + secretName);
			String countryrouting = TIOPUtil.getSecretDetails(secretName);
			String apiURL = TIOPUtil.getKeyValue(countryrouting, "APIURL");
			String bearerToken = TIOPUtil.getKeyValue(countryrouting, "BearerToken");

			System.out.println("RouterLambdaHandler::handleRequest::apiURL = " + apiURL);
			// context.getLogger().log("RouterLambdaHandler::handleRequest::bearerToken =
			// "+bearerToken);

			AmazonS3 s3client = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
					.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
			s3object = s3client.getObject(bucketName, fileName);
			inputStream = s3object.getObjectContent();
			StringBuilder textBuilder = new StringBuilder();

			Reader reader = new BufferedReader(
					new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			char[] buffer = new char[1024];
			int numCharsRead;
			while ((numCharsRead = reader.read(buffer)) != -1) {
				textBuilder.append(buffer, 0, numCharsRead);
			}
			// context.getLogger().log("RouterLambdaHandler::handleRequest::INPUT XML =
			// "+textBuilder.toString());

			bearerToken = "Bearer " + bearerToken;

			// context.getLogger().log("RouterLambdaHandler::handleRequest::1 -- bearerToken
			// = "+bearerToken);

			HttpPost request = new HttpPost(apiURL);
			StringEntity se = new StringEntity(textBuilder.toString());
			request.setHeader("Content-Type", "application/xml");
			request.setHeader("Authorization", bearerToken);
			request.setEntity(se);

			CloseableHttpClient httpClient = HttpClientBuilder.create().build();
			long startTime = System.currentTimeMillis();
			System.out.println("HTTP request started at: " + startTime);

			try (CloseableHttpResponse response = httpClient.execute(request)) {
				long endTime = System.currentTimeMillis();

				System.out.println("HTTP request completed at: " + endTime);
				System.out.println("Time taken: " + (endTime - startTime) + " ms");
				int status = response.getStatusLine().getStatusCode();
				String body = new String(response.getEntity().getContent().readAllBytes());

				System.out.println("Response status---> " + status);
				System.out.println("Response response ---> " + body);

				if (status == 200) {
					insertRouterInfo(null, fileName, objEventCount, aggEventCount, gtinInfo, source,
							destination);
				} else if (status == 401) {

					Header authHeader = response.getFirstHeader("WWW-Authenticate");
					if (authHeader != null) {
						String authHeaderValue = authHeader.getValue();
						System.out.println("WWW-Authenticate Header: " + authHeaderValue);

						// Step 2: Parse the header to extract `error` and `error_description`
						String error = null;
						String errorDescription = null;

						// Check if the header contains "error" and "error_description"
						if (authHeaderValue.contains("error=")) {
							error = authHeaderValue.split("error=")[1].split(",")[0]
									.replace("\"", "").trim();
						}
						if (authHeaderValue.contains("error_description=")) {
							errorDescription = authHeaderValue.split("error_description=")[1]
									.replace("\"", "").trim();
						}

						// Print or log the extracted values
						System.out.println("Error: " + error);
						System.out.println("Error Description: " + errorDescription);

						insertRouterErrorLog(null, errorDescription, fileName, objEventCount,
								aggEventCount, gtinInfo, source, destination);
						Date date = new Date();
						SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
						String strDate = formatter.format(date);
						final String htmlBody = "<h4>An issue [EXC010] encountered while processing the file "
								+ fileName + " which was received on " + strDate + ".</h4>"
								+ "<h4>Details of the Issue:</h4>" + "<p>An error occurred (HTTP "
								+ status + ") while routing the EPCIS document. " + errorDescription
								+ "</p>" + "<p>TIOP operation team</p>";
						TIOPAuthSendEmail.sendMail(null, fileName, htmlBody);
					}
				} else {
					ObjectMapper mapper = new ObjectMapper();
					JsonNode bodyNode = mapper.readTree(body);
					String message = null;
					JsonNode messageNode = bodyNode.get("message");

					if (messageNode == null) {
						messageNode = bodyNode.get("errors");
						if (messageNode != null) {
							message = messageNode.toString();
							message = message.split(":")[1];
							message = message.replaceAll("}", "");
						}
					} else {
						message = messageNode.toString();
					}

					System.out.println("raw message -- " + message);

					if (message != null) {
						message = message.replaceAll("[\\[\\]]", "");
						message = message.replaceAll("\"", "");
						System.out.println("final message -- " + message);
						insertRouterErrorLog(null, message, fileName, objEventCount, aggEventCount,
								gtinInfo, source, destination);
						Date date = new Date();
						SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
						String strDate = formatter.format(date);
						final String htmlBody = "<h4>An issue [EXC010] encountered while processing the file "
								+ fileName + " which was received on " + strDate + ".</h4>"
								+ "<h4>Details of the Issue:</h4>" + "<p>An error occurred (HTTP "
								+ status + ") while routing the EPCIS document. " + message + "</p>"
								+ "<p>TIOP operation team</p>";
						TIOPAuthSendEmail.sendMail(null, fileName, htmlBody);
					}

				}

			}

		} catch (RouterConfigException rce) {

			rce.printStackTrace();
			String message = rce.getMessage();

			insertRouterErrorLog(null, message, fileName, objEventCount, aggEventCount, gtinInfo,
					source, destination);
			Date date = new Date();
			SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
			String strDate = formatter.format(date);
			final String htmlBody = "<h4>An issue [EXC010] encountered while processing the file "
					+ fileName + " which was received on " + strDate + ".</h4>"
					+ "<h4>Details of the Issue:</h4>"
					+ "<p>An error occurred while routing the EPCIS document. Routing record does not exist for recipient GLN ["
					+ destination + "].</p>" + "<p>TIOP operation team</p>";
			TIOPAuthSendEmail.sendMail(null, fileName, htmlBody);

		} catch (Exception e) {
			e.printStackTrace();
			String message = e.getMessage();
			insertRouterErrorLog(null, message, fileName, objEventCount, aggEventCount, gtinInfo,
					source, destination);
			Date date = new Date();
			SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
			String strDate = formatter.format(date);
			final String htmlBody = "<h4>An issue [EXC010] encountered while processing the file "
					+ fileName + " which was received on " + strDate + ".</h4>"
					+ "<h4>Details of the Issue:</h4>"
					+ "<p>An error occurred while routing the EPCIS document. " + message + "</p>"
					+ "<p>TIOP operation team</p>";
			TIOPAuthSendEmail.sendMail(null, fileName, htmlBody);

			// }
		}

		return "Router success";
	}

	public static void main(String args[]) {

		RouterLambdaHandler handler = new RouterLambdaHandler();
		handler.handleRequest();

	}

}
