package com.usaid;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import java.util.Set;
import javax.net.ssl.SSLContext;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CopyObjectResult;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BulkLoadLambdaHandler implements RequestHandler<S3Event, String> {

	private Connection con;
	String processedJsonBucketName = "";
	String processedXmlBucketName = "";
	String sourceXmlBucketName = "";
	String sourceJsonBucketName = "";
	AmazonS3 s3Client = null;
	Context context = null;

	@Override
	public String handleRequest(S3Event s3Event, Context context) {
		context.getLogger().log("BulkLoadLambdaHandler::handleRequest::Start");
		this.context = context;

		String fileName = "";
		int jsonObjEventCount = 0;
		int jsonAggEventCount = 0;
		String destination = "";
		String tiopbillto_gln = "";
		String source = "";

		String gtin = "";

		sourceJsonBucketName = s3Event.getRecords().get(0).getS3().getBucket().getName();

		fileName = s3Event.getRecords().get(0).getS3().getObject().getKey();
		context.getLogger()
				.log("BucketName :: " + sourceJsonBucketName + " ::  and fileName " + fileName);
		S3Object s3Object = null;
		S3ObjectInputStream inputStream = null;
		try {
			s3Client = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
					.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
			String env = System.getenv("env");
			processedJsonBucketName = System.getenv("processedJsonBucketName");
			processedXmlBucketName = System.getenv("processedXmlBucketName");
			sourceXmlBucketName = System.getenv("sourceXmlBucketName");

			context.getLogger()
					.log("BulkLoadLambdaHandler -> processedJsonBucketName "
							+ processedJsonBucketName + " processedXmlBucketName "
							+ processedXmlBucketName);

			context.getLogger().log("BulkLoadLambdaHandler::handleRequest ::: env = " + env);
			s3Object = s3Client.getObject(sourceJsonBucketName, fileName);
			inputStream = s3Object.getObjectContent();

			StringBuilder textBuilder = new StringBuilder();

			Reader reader = new BufferedReader(
					new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			char[] buffer = new char[1024];
			int numCharsRead;
			while ((numCharsRead = reader.read(buffer)) != -1) {
				textBuilder.append(buffer, 0, numCharsRead);
			}

			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(Feature.AUTO_CLOSE_SOURCE, true);
			// read the json strings and convert it into JsonNode
			JsonNode node = mapper.readTree(textBuilder.toString());

			JsonNode epcisBody = node.get("epcisBody");

			if (epcisBody != null) {
				JsonNode eventList = epcisBody.get("eventList");
				StringBuilder payload = new StringBuilder();

				for (int i = 0; i < eventList.size(); i++) {
					JsonNode chNode = eventList.get(i);
					String eventType = chNode.get("type").toString();
					String bizStep = chNode.get("bizStep").toString();
					if (bizStep.contains("shipping")) {
						destination = chNode.get("tiop:nts_gln").toString();
						if (!destination.isEmpty())
							destination = destination.replaceAll("\"", "");
						tiopbillto_gln = chNode.get("tiop:billto_gln").toString();

						JsonNode sopurceNode = chNode.get("bizLocation");
						source = sopurceNode.get("id").toString();
						if (source != null)
							source = source.substring(source.length() - 14, source.length() - 1);
						context.getLogger().log("-----------destination = " + destination);
						context.getLogger().log("-----------source = " + source);
					} else if (gtin.isEmpty()) {
						JsonNode epcList = chNode.get("epcList");
						if (epcList != null) {
							gtin = epcList.get(0).toString();
							gtin = gtin.split("https://id.gs1.org/")[1];
							gtin = gtin.substring(3, 17);
							context.getLogger().log("-----------gtin = " + gtin);
						}

					}

					if (eventType.contains(TIOPConstants.ObjectEvent)) {
						jsonObjEventCount++;
					} else if (eventType.contains(TIOPConstants.AggregationEvent)) {
						jsonAggEventCount++;
					}
				}

				Set<String> dbHashValueList = getHashFromDB(context);
				context.getLogger().log("Total Hash from db = " + dbHashValueList.size());
				Set<String> newHashValueSet = new LinkedHashSet<String>();

				for (int i = 0; i < eventList.size(); i++) {
					JsonNode chNode = eventList.get(i);
					String chNodeStr = chNode.toString();
					String eventType = chNode.get("type").toString();
					String bill_to_gln = ",\"tiop:psa\":" + tiopbillto_gln;
					chNodeStr = chNodeStr.substring(0, chNodeStr.length() - 1);
					chNodeStr = chNodeStr + bill_to_gln;
					String contextStr = ",\"@context\":[{\"tiop\":\"https://ref.opentiop.org/epcis/\"}]";
					if (!chNodeStr.contains("@context")) {
						if (eventType.contains(TIOPConstants.ObjectEvent)) {
							chNodeStr = chNodeStr.replaceAll("\"type\":\"ObjectEvent\"",
									"\"type\":\"ObjectEvent\"" + contextStr);
						} else if (eventType.contains(TIOPConstants.AggregationEvent)) {
							chNodeStr = chNodeStr.replaceAll("\"type\":\"AggregationEvent\"",
									"\"type\":\"AggregationEvent\"" + contextStr);
						}
					}

					chNodeStr = chNodeStr + "}\n";

					String eventStr = chNode.toString();
					eventStr = eventStr.replaceAll(" ", "");
					MessageDigest messageDigest;

					try {
						messageDigest = MessageDigest.getInstance("SHA-256");
						messageDigest.update(eventStr.getBytes());
						String eventHash = new String(messageDigest.digest());

						if (!dbHashValueList.contains(eventHash)) {
							newHashValueSet.add(eventHash);
							payload.append("{\"index\": {\"_index\": \"epcis_index\"}}\n");
							payload.append(chNodeStr);
						}
					} catch (NoSuchAlgorithmException e) {
						e.printStackTrace();
					}
				}
				context.getLogger().log("jsonObjEventCount = " + jsonObjEventCount);
				context.getLogger().log("jsonAggEventCount = " + jsonAggEventCount);
				context.getLogger().log("No of unique event = " + dbHashValueList.size());

				if (payload != null && payload.length() > 0) {
					context.getLogger().log("-----------payload for bulkupload = " + payload.toString());

					try {
						String blSecret = TIOPUtil
								.getSecretDetails(System.getenv(TIOPConstants.blSecretName));
						String apiURL = TIOPUtil.getKeyValue(blSecret, "apiURL");
						String authToken = TIOPUtil.getKeyValue(blSecret, "authToken");
						CloseableHttpClient httpClient = createHttpClientWithDisabledSSL();
						HttpPost request = new HttpPost(apiURL);
						StringEntity se = new StringEntity(payload.toString());
						request.setHeader("Content-Type", "application/json");
						request.setHeader("Authorization", authToken);
						request.setEntity(se);

						try (CloseableHttpResponse response = httpClient.execute(request)) {
							int status = response.getStatusLine().getStatusCode();
							String message = new String(
									response.getEntity().getContent().readAllBytes());

							context.getLogger().log("Response status-- " + status);
							context.getLogger().log("Response response -- " + message);

							if (status == 200) {
								context.getLogger().log(
										"BulkLoadLambdaHandler successfully uploaded data to repository for the '"
												+ fileName + "' from s3 bucket '"
												+ sourceJsonBucketName + "'");

								context.getLogger().log("Executing the method to remove any failed hash values before inserting to DB");
								removeFailedRecordHash(message, newHashValueSet);
								
								context.getLogger().log("Inserting hash values to database.");
								if(newHashValueSet !=null && newHashValueSet.size() >0) {
									insertHashData(context, newHashValueSet);
								}

								// Calling the method to move the files to processed bucket
								postRepositoryUpdateProcess(jsonObjEventCount, jsonAggEventCount,
										gtin, source, destination, fileName);
							} else {

								ObjectMapper mapperObj = new ObjectMapper();
								JsonNode bodyNode = mapperObj.readTree(message);

								JsonNode messageNode = bodyNode.get("message");
								if (messageNode == null) {
									messageNode = bodyNode.get("error");
									if (messageNode != null) {
										message = messageNode.toString();
										message = message.replaceAll("\"", "");
									}
								} else {
									message = messageNode.toString();
								}

								context.getLogger()
										.log("BulkLoadLambdaHandler Exception::Exception message: "
												+ message);
								insertBulkLoadErrorLog(context, message, fileName,
										jsonObjEventCount, jsonAggEventCount, gtin, source,
										destination);

								Date date = new Date();
								SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
								String strDate = formatter.format(date);
								final String htmlBody = "<h4>An issue [EXC012] encountered while processing the file "
										+ fileName + " which was received on " + strDate
										+ " to Open Search Dashboard repository.</h4>"
										+ "<h4>Details of the Issue:</h4>"
										+ "<p>An error occurred in bulkload while updating Open Search Dashboard Repository. "
										+ message + "</p>" + "<p>TIOP operation team</p>";
								// sendMail(context, fileName, htmlBody);
								TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);

							}
						}

					} catch (Exception e) {
						String message = e.getMessage();
						context.getLogger().log(
								"BulkLoadLambdaHandler Exception::Exception message : " + message);
						insertBulkLoadErrorLog(context, message, fileName, jsonObjEventCount,
								jsonAggEventCount, gtin, source, destination);
						Date date = new Date();
						SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
						String strDate = formatter.format(date);
						final String htmlBody = "<h4>An issue [EXC011] encountered while processing the file "
								+ fileName + " which was received on " + strDate
								+ " to Open Search Dashboard repository.</h4>"
								+ "<h4>Details of the Issue:</h4>"
								+ "<p>An error occurred in bulkload while updating Open Search Dashboard Repository. "
								+ message + "</p>" + "<p>TIOP operation team</p>";
						// sendMail(context, fileName, htmlBody);
						TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);
						e.printStackTrace();
					}
				} else {
					context.getLogger().log("All the events are duplicate");
				}

			}

			if (inputStream != null)
				inputStream.close();
			if (s3Object != null)
				s3Object.close();

		} catch (Exception e) {
			return "Error in BulkLoadLambdaHandler :::" + e.getMessage();
		}
		return "BulkLoadLambda success";
	}

	private void insertHashData(Context context, Set<String> insertHash)
			throws ClassNotFoundException, SQLException {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // 2024-04-05
																					// 20:31:02
		String strDate = formatter.format(date);
		con = getConnection();
		PreparedStatement ps = con
				.prepareStatement("INSERT INTO tiopdb.event_hash(hash, create_date) VALUES(?, ?);");

		for (String data : insertHash) {
			ps.setString(1, String.valueOf(data));
			ps.setString(2, strDate);
			ps.addBatch();
		}
		ps.clearParameters();
		int[] results = ps.executeBatch();
		// context.getLogger().log("Inserted hash data count = " + results.length);
	}

	private Set<String> getHashFromDB(Context context) {
		Set<String> hashSet = new LinkedHashSet<String>();
		String query = "SELECT hash FROM tiopdb.event_hash;";

		try {
			con = getConnection();
			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				if (rs.getString(1) != null)
					hashSet.add(rs.getString(1));
			}

		} catch (Exception e) {
			context.getLogger().log("getHashFromDB ::: db error = " + e.getMessage());
		}

		return hashSet;
	}

	
	private static CloseableHttpClient createHttpClientWithDisabledSSL() throws Exception {
		// Create a trust manager that does not validate certificate chains
		SSLContext sslContext = SSLContextBuilder.create()
				.loadTrustMaterial((chain, authType) -> true).build();

		// Create an HttpClient that uses the custom SSL context and hostname verifier
		return HttpClientBuilder.create().setSSLContext(sslContext)
				.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).build();
	}

	private void insertBulkLoadErrorLog(Context context, String msg, String fileName,
			int objEventCount, int aggEventCount, String gtinInfo, String source,
			String destination) {

		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // 2024-04-05
																					// 20:31:02
		String strDate = formatter.format(date);

		String query = "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text)\r\n"

				+ "VALUES (\r\n" + "null, -- 1 (Object Event), 2 (Aggregation Event)\r\n"
				+ "(select distinct stp.partner_id\r\n" + "from location sl\r\n"
				+ "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln ='"
				+ source + "'),\r\n" + "(select distinct  dtp.partner_id\r\n"
				+ "from location dl\r\n" + "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"
				+ destination + "'),\r\n" + "(select distinct sl.location_id\r\n"
				+ "from location sl\r\n" + "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln ='"
				+ source + "'),\r\n" + "(select distinct  dl.location_id\r\n"
				+ "from location dl\r\n" + "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"
				+ destination + "'),\r\n" + "(select distinct ti.item_id\r\n"
				+ "from trade_item ti where ti.current_indicator='A' and gtin = '" + gtinInfo
				+ "'),\r\n" + "(select tr.rule_id from\r\n" + "tiop_rule tr\r\n"
				+ "inner join tiop_status ts\r\n" + "ON tr.status_id = ts.status_id\r\n"
				+ "inner join location sl\r\n" + "on tr.source_location_id = sl.location_id\r\n"
				+ "inner join location dl\r\n"
				+ "on tr.destination_location_id = dl.location_id\r\n"
				+ "inner join trade_item ti\r\n" + "on tr.item_id =ti.item_id\r\n" + "where\r\n"
				+ "ts.status_description ='Active'\r\n" + "and sl.current_indicator ='A'\r\n"
				+ "and dl.current_indicator ='A'\r\n" + "and ti.current_indicator ='A'\r\n"
				+ "and sl.gln = '" + source + "'\r\n" + "and dl.gln = '" + destination + "'\r\n"
				+ "and ti.gtin ='" + gtinInfo + "'),\r\n" + "11, -- bulkload failed --\r\n" + "'"
				+ fileName + "' , -- the json document name\r\n" + objEventCount
				+ ", -- Object event counts\r\n" + aggEventCount
				+ ",  -- Aggregation event counts\r\n" + "'" + msg + "', -- Exception detail\r\n"
				+ "'" + strDate + "',\r\n" + "'tiop_bulkload', -- id that insert data in tiopdb\r\n"
				+ "'" + strDate + "',\r\n" + "'tiop_bulkload', -- id that insert data in tiopdb\r\n"
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

	private Connection getConnection() throws ClassNotFoundException, SQLException {
		if (con == null || con.isClosed()) {
			con = TIOPUtil.getConnection();
		}
		return con;
	}

	/*
	 * Method to copy the xml and json document to processed bucket.
	 */
	private void copyS3Object(String sourceBucket, String destinationBucket, String docName) {
		try {
			CopyObjectRequest copyObjectRequest = new CopyObjectRequest(sourceBucket, docName,
					destinationBucket, docName);

			CopyObjectResult copyObjectResult = s3Client.copyObject(copyObjectRequest);
			context.getLogger().log("Copy succeeded: " + copyObjectResult.getETag());

		} catch (Exception e) {
			System.err.println(e.getMessage());

		}
	}

	/*
	 * This method helps to move the processed document to a different bucket and
	 * delete the same from source bucket.
	 */
	private void postRepositoryUpdateProcess(int jsonObjEventCount, int jsonAggEventCount,
			String gtinInfo, String source, String destination, String fileName) {

		try {

			// copy the json file to processed bucket for json.
			copyS3Object(sourceJsonBucketName, processedJsonBucketName, fileName);
			context.getLogger().log("BulkLoadLambdaHandler - moved the processed json document "
					+ fileName + " to target bucket " + processedJsonBucketName);

			s3Client.deleteObject(new DeleteObjectRequest(sourceJsonBucketName, fileName));
			context.getLogger().log("BulkLoadLambdaHandler - Deleted the processed json document "
					+ fileName + " from  " + sourceJsonBucketName);

			fileName = fileName.replace(".json", ".xml");
			// copy the xml file to processed bucket for xml.
			copyS3Object(sourceXmlBucketName, processedXmlBucketName, fileName);
			context.getLogger().log("BulkLoadLambdaHandler - moved the processed xml document "
					+ fileName + " to target bucket " + processedXmlBucketName);

			s3Client.deleteObject(new DeleteObjectRequest(sourceXmlBucketName, fileName));
			context.getLogger().log("BulkLoadLambdaHandler - Deleted the processed xml document "
					+ fileName + " from  " + sourceXmlBucketName);

		} catch (Exception e) {
			String message = e.getMessage();
			context.getLogger()
					.log("BulkLoadLambdaHandler Exception::Exception message : " + message);
			e.printStackTrace();
			insertBulkLoadErrorLog(context, message, fileName, jsonObjEventCount, jsonAggEventCount,
					gtinInfo, source, destination);
			Date date = new Date();
			SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
			String strDate = formatter.format(date);
			final String htmlBody = "<h4>An issue [EXC013] encountered while performing open search dashboard"
					+ " repository for processing the file " + fileName + " which was received on "
					+ strDate + ".t</h4>" + "<h4>Details of the Issue:</h4>"
					+ "<p>An error occurred during open search dashboard updates. Though repository update is successful, "
					+ "but there is a failure while moving the processed files to to a different bucket in S3 - . "
					+ message + "</p>" + "<p>TIOP operation team</p>";
			context.getLogger().log("BulkLoadLambdaHandler Send mail trigger start ");
			TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);

		}

	}

	private Set removeFailedRecordHash(String jsonString, Set insertHash) {

		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode jsonObject = null;
		try {
			jsonObject = objectMapper.readTree(jsonString);

			JsonNode itemsArray = jsonObject.get("items");

			Set<String> removeHash = new HashSet<String>();
			List<String> hashList = new ArrayList<>(insertHash);
			if (itemsArray.isArray()) {

				for (int i = 0; i < itemsArray.size(); i++) {

					if (itemsArray.get(i).get("index").get("status").intValue() == 400) {
						removeHash.add(hashList.get(i));
					}

				}
			}
			//removing the hash values which are failed to insert to open search.
			for (String element : removeHash) {
				context.getLogger().log("Failed hash values are "+ element);
				insertHash.remove(element);
			}
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return insertHash;

	}

	
}
