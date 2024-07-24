package com.usaid;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

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
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BulkLoadLambdaHandler implements RequestHandler<S3Event, String> {

	private Connection con;
	
	@Override
	public String handleRequest(S3Event s3Event, Context context) {
		context.getLogger().log("BulkLoadLambdaHandler::handleRequest::Start");
		String bucketName = "";
		String fileName = "";
		int jsonObjEventCount = 0;
		int jsonAggEventCount = 0;
		String destination = "";
		String tiopbillto_gln = "";
		String source = "";
		
		
	    context.getLogger().log("BulkLoadLambdaHandler::handleRequest ::: Stat");
		bucketName = s3Event.getRecords().get(0).getS3().getBucket().getName();
		fileName = s3Event.getRecords().get(0).getS3().getObject().getKey();
		context.getLogger().log("BucketName :: " + bucketName);
		context.getLogger().log("fileName :: " + fileName);
		S3Object s3object = null;
		S3ObjectInputStream inputStream = null;
		try {
			AmazonS3 s3client = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
					.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
			context.getLogger().log("BulkLoadLambdaHandler::handleRequest ::: Start");
			String env = System.getenv("env");
			context.getLogger().log("BulkLoadLambdaHandler::handleRequest ::: env = "+env);
			s3object = s3client.getObject(bucketName, fileName);
			inputStream = s3object.getObjectContent();
			context.getLogger().log("BulkLoadLambdaHandler::handleRequest ::: 1");
			
			StringBuilder textBuilder = new StringBuilder();

			Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			char[] buffer = new char[1024];
            int numCharsRead;
            while ((numCharsRead = reader.read(buffer)) != -1) {
                textBuilder.append(buffer, 0, numCharsRead);
            }
			//context.getLogger().log("BulkLoadLambdaHandler::handleRequest::INPUT Json = "+textBuilder.toString());
			
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(Feature.AUTO_CLOSE_SOURCE, true);
	        // read the json strings and convert it into JsonNode
	        JsonNode node = mapper.readTree(textBuilder.toString());
	          
	        JsonNode epcisBody = node.get("epcisBody");
			
			if (epcisBody != null) {
				JsonNode eventList = epcisBody.get("eventList");
				jsonObjEventCount = 0;
				jsonAggEventCount = 0;
				destination = "";
				tiopbillto_gln = "";
				source = "";
				StringBuilder payload = new StringBuilder();
				
				for (int i = 0; i < eventList.size(); i++) {
					JsonNode chNode = eventList.get(i);
					String eventType = chNode.get("type").toString();
					String bizStep = chNode.get("bizStep").toString();
					if(bizStep.contains("shipping")) {
						destination = chNode.get("tiop:nts_gln").toString();
						tiopbillto_gln = chNode.get("tiop:billto_gln").toString();
						JsonNode sopurceNode = chNode.get("bizLocation");
						source = sopurceNode.get("id").toString();
					}
					
					if (eventType.contains(TIOPConstants.ObjectEvent)) {
						jsonObjEventCount++;
					} else if (eventType.contains(TIOPConstants.AggregationEvent)) {
						jsonAggEventCount++;
					}
				}
				
				for (int i = 0; i < eventList.size(); i++) {
					JsonNode chNode = eventList.get(i);
					String chNodeStr = chNode.toString();
					String eventType = chNode.get("type").toString();
					String bill_to_gln = ",\"tiop:psa\":"+tiopbillto_gln;
					chNodeStr = chNodeStr.substring(0, chNodeStr.length()-1);
					chNodeStr = chNodeStr + bill_to_gln;
					String contextStr = ",\"@context\":[{\"tiop\":\"https://ref.opentiop.org/epcis/\"}]";
					if(!chNodeStr.contains("@context")) {
						if (eventType.contains(TIOPConstants.ObjectEvent)) {
							chNodeStr = chNodeStr.replaceAll("\"type\":\"ObjectEvent\"",
									"\"type\":\"ObjectEvent\"" + contextStr);
						} else if (eventType.contains(TIOPConstants.AggregationEvent)) {
							chNodeStr = chNodeStr.replaceAll("\"type\":\"AggregationEvent\"",
									"\"type\":\"AggregationEvent\"" + contextStr);
						}
					}
					chNodeStr = chNodeStr + "}";

					payload.append("{\"index\": {\"_index\": \"epcis_index\"}}\n");
					payload.append(chNodeStr+"\n");

				}
				context.getLogger().log("jsonObjEventCount = " + jsonObjEventCount);
				context.getLogger().log("jsonAggEventCount = " + jsonAggEventCount);
				context.getLogger().log("-----------payload = "+payload.toString());
					
				try {
					String blSecret = TIOPUtil.getSecretDetails(System.getenv(TIOPConstants.blSecretName));
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
					    String body = new String(response.getEntity().getContent().readAllBytes());

					    context.getLogger().log("Response status-- "+status);
					    context.getLogger().log("Response response -- "+body);
			        }
				    
				} catch (Exception e) {
					String message = e.getMessage();
					context.getLogger().log("BulkLoadLambdaHandler Exception::Exception message : "+message);
					e.printStackTrace();
					insertBulkLoadErrorLog(context, message, fileName, jsonObjEventCount, jsonAggEventCount, "urn:epc:id:sgtin:0000128.239405", source, destination);
					Date date = new Date();
					SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
					String strDate = formatter.format(date);
					final String htmlBody = "<h4>An issue [EXC011] encountered while processing the file "+fileName+" which was received on "+strDate+".</h4>"
							+ "<h4>Details of the Issue:</h4>"
							+ "<p>An error occurred in bulkload while updating tiop dashboard. "+ message+"</p>" 
							+ "<p>TIOP operation team</p>";
					TIOPAuthSendEmail.sendMail(context, fileName, htmlBody);
					e.printStackTrace();
				}
			}
			
			if (inputStream != null) inputStream.close();
			if (s3object != null) s3object.close();
			context.getLogger().log("BulkLoadLambdaHandler successfully for file '" + fileName + "' from s3 bucket '" + bucketName + "'");
			
		} catch (Exception e) {
			return "Error in BulkLoadLambdaHandler :::" + e.getMessage();
		} 
		return "BulkLoadLambda success";
	}
	
	public static CloseableHttpClient createHttpClientWithDisabledSSL() throws Exception {
        // Create a trust manager that does not validate certificate chains
        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial((chain, authType) -> true)
                .build();

        // Create an HttpClient that uses the custom SSL context and hostname verifier
        return HttpClientBuilder.create()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
    }
	
	private void insertBulkLoadErrorLog(Context context, String msg, String fileName, int objEventCount, int aggEventCount, String gtinInfo, String source, String destination) {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // 2024-04-05 20:31:02
		String strDate = formatter.format(date);

		String query = "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text)\r\n"
				+ "VALUES (\r\n"
				+ "null, -- 1 (Object Event), 2 (Aggregation Event)\r\n"
				+ "(select distinct stp.partner_id\r\n"
				+ "from location sl\r\n"
				+ "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"+ source +"'),\r\n"
				+ "(select distinct  dtp.partner_id\r\n"
				+ "from location dl\r\n"
				+ "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"+ destination +"'),\r\n"
				+ "(select distinct sl.location_id\r\n"
				+ "from location sl\r\n"
				+ "inner join trading_partner stp\r\n"
				+ "on sl.partner_id =stp.partner_id where sl.current_indicator ='A' and stp.current_indicator ='A' and gln_uri ='"+ source +"'),\r\n"
				+ "(select distinct  dl.location_id\r\n"
				+ "from location dl\r\n"
				+ "inner join trading_partner dtp\r\n"
				+ "on dl.partner_id =dtp.partner_id where dl.current_indicator='A' and dtp.current_indicator ='A' and gln ='"+ destination +"'),\r\n"
				+ "(select distinct ti.item_id\r\n"
				+ "from trade_item ti where ti.current_indicator='A' and gtin_uri ='"+ gtinInfo +"'),\r\n"
				+ "(select tr.rule_id from\r\n"
				+ "tiop_rule tr\r\n"
				+ "inner join tiop_status ts\r\n"
				+ "ON tr.status_id = ts.status_id\r\n"
				+ "inner join location sl\r\n"
				+ "on tr.source_location_id = sl.location_id\r\n"
				+ "inner join location dl\r\n"
				+ "on tr.destination_location_id = dl.location_id\r\n"
				+ "inner join trade_item ti\r\n"
				+ "on tr.item_id =ti.item_id\r\n"
				+ "where\r\n"
				+ "ts.status_description ='Active'\r\n"
				+ "and sl.current_indicator ='A'\r\n"
				+ "and dl.current_indicator ='A'\r\n"
				+ "and ti.current_indicator ='A'\r\n"
				+ "and sl.gln_uri = '"+ source +"'\r\n"
				+ "and dl.gln = '"+ destination +"'\r\n"
				+ "and ti.gtin_uri ='"+ gtinInfo +"'),\r\n"
				+ "11, -- bulkload failed --\r\n"
                + "'"+fileName+"' , -- the json document name\r\n"
                + objEventCount+ ", -- Object event counts\r\n"
	            + aggEventCount+ ",  -- Aggregation event counts\r\n"
	            + "'"+msg+"', -- Exception detail\r\n"
	            + "'"+strDate+"',\r\n"
				+ "'tiop_bulkload', -- id that insert data in tiopdb\r\n"
				+ "'"+strDate+"',\r\n"
				+ "'tiop_bulkload', -- id that insert data in tiopdb\r\n"
				+ "'A',\r\n"
				+ "'');";

		try {
			context.getLogger().log("insertErrorLog ::: Start");
			con = getConnection();
			context.getLogger().log("insertErrorLog ::: con = " + con);
			Statement stmt = con.createStatement();
			// context.getLogger().log("insertErrorLog ::: query = "+query);
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
	
}
