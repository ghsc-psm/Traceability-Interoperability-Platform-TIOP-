package com.usaid;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLContext;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.w3c.dom.Document;

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

	@Override
	public String handleRequest(S3Event s3Event, Context context) {
		context.getLogger().log("BulkLoadLambdaHandler::handleRequest::Start");
		String bucketName = "";
		String fileName = "";
	    context.getLogger().log("BulkLoadLambdaHandler::handleRequest ::: Stat");
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
			context.getLogger().log("BulkLoadLambdaHandler::handleRequest ::: Start");
			String env = System.getenv("env");
			context.getLogger().log("BulkLoadLambdaHandler::handleRequest ::: env = "+env);
			s3object = s3client.getObject(bucketName, fileName);
			inputStream = s3object.getObjectContent();
			context.getLogger().log("BulkLoadLambdaHandler::handleRequest ::: 1");
			
			StringBuilder textBuilder = new StringBuilder();

			Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			int c = 0;
			while ((c = reader.read()) != -1) {
				textBuilder.append((char) c);
			}
			context.getLogger().log("BulkLoadLambdaHandler::handleRequest::INPUT Json = "+textBuilder.toString());
			
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(Feature.AUTO_CLOSE_SOURCE, true);
	        // read the json strings and convert it into JsonNode
			context.getLogger().log("-----------------------------------BulkLoadLambdaHandler::8.0.1");
	        JsonNode node = mapper.readTree(textBuilder.toString());
	        context.getLogger().log("-----------------------------------BulkLoadLambdaHandler::8.1");
	          
	        JsonNode epcisBody = node.get("epcisBody");
			
			if (epcisBody != null) {
				JsonNode eventList = epcisBody.get("eventList");
				int jsonObjEventCount = 0;
				int jsonAggEventCount = 0;
				StringBuilder payload = new StringBuilder();
				for (int i = 0; i < eventList.size(); i++) {
					JsonNode chNode = eventList.get(i);
					String chNodeStr = chNode.toString();
					String eventType = chNode.get("type").toString();
					context.getLogger().log("-----------chNodeStr1 = "+chNodeStr);
					
					chNodeStr = chNodeStr.replaceAll("tiop:", "");
					
					if (eventType.contains(TIOPConstants.ObjectEvent)) {
						jsonObjEventCount++;
						chNodeStr = chNodeStr.replaceAll("\"type\":\"ObjectEvent\"", "\"eventType\":\"ObjectEvent\"");
						//context.getLogger().log("-----------chNodeStr2 = "+chNodeStr);
						//chNodeStr = chNodeStr.replaceAll("\"type\": \"ObjectEvent\"", "\"eventType\":\"ObjectEvent\"");
						//context.getLogger().log("-----------chNodeStr3 = "+chNodeStr);
					} else if (eventType.contains(TIOPConstants.AggregationEvent)) {
						jsonAggEventCount++;
						chNodeStr = chNodeStr.replaceAll("\"type\":\"AggregationEvent\"", "\"eventType\":\"AggregationEvent\"");
						//context.getLogger().log("-----------chNodeStr4 = "+chNodeStr);
						//chNodeStr = chNodeStr.replaceAll("\"type\": \"AggregationEvent\"", "\"eventType\":\"AggregationEvent\"");
						//context.getLogger().log("-----------chNodeStr5 = "+chNodeStr);
					}
					payload.append("{\"index\": {\"_index\": \"epcis_index\"}}\n");
					payload.append(chNodeStr+"\n");

				}
				context.getLogger().log("-----------payload = "+payload.toString());
					
				try {

					String apiURL = "https://ec2-18-233-6-149.compute-1.amazonaws.com:9200/epcis_index/_bulk";
					String bearerToken = "Basic YWRtaW46dGVzdDEyMzQ=";
					
					CloseableHttpClient httpClient = createHttpClientWithDisabledSSL();
					HttpPost request = new HttpPost(apiURL);
			        StringEntity se = new StringEntity(payload.toString()); 
			        request.setHeader("Content-Type", "application/json");
			        request.setHeader("Authorization", bearerToken);
			        request.setEntity(se);
			        
			        try (CloseableHttpResponse response = httpClient.execute(request)) {
			        	int status = response.getStatusLine().getStatusCode();
					    String body = new String(response.getEntity().getContent().readAllBytes());

					    context.getLogger().log("Response status-- "+status);
					    context.getLogger().log("Response response -- "+body);
			        }
/*					
					HttpRequest request = HttpRequest.newBuilder()
				        	.POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
				        	.uri(URI.create(apiURL))
				        	.header("Content-Type", "application/xml")
				        	.header("Authorization", bearerToken)
				        	.build(); 
					HttpResponse<String> bulkResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
*/				    
				    
				} catch (Exception e) {
					e.printStackTrace();
				}
					
				context.getLogger().log("jsonObjEventCount = " + jsonObjEventCount);
				context.getLogger().log("jsonAggEventCount = " + jsonAggEventCount);
			}
			
			if (inputStream != null) inputStream.close();
			if (s3object != null) s3object.close();
			context.getLogger().log("BulkLoadLambdaHandler successfully for file '" + fileName + "' from s3 bucket '" + bucketName + "'");
			
		} catch (Exception e) {
			context.getLogger().log("BulkLoadLambdaHandler Exception::Exception message : "+e.getMessage());
			e.printStackTrace();
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
	
}
