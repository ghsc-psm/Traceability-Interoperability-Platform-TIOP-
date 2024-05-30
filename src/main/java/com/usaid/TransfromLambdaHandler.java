package com.usaid;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;

import org.json.JSONObject;

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
	
	
	
	@Override
	public String handleRequest(Object event, Context context) {
	    context.getLogger().log("TransfromLambdaHandler::handleRequest::Start");
		String fileName= null;
	    String fileContent = null;
		
		 if(event instanceof LinkedHashMap) {
		    	LinkedHashMap<String, String> mapEvent = (LinkedHashMap<String, String>) event;
		    	fileName =  mapEvent.get("fileName");
		    	fileContent = mapEvent.get("fileContent");
		    			    	
		    	//context.getLogger().log("fileContent = " + fileContent);
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
            //connection.setRequestProperty("Accept", "application/xml");
            connection.setDoOutput(true);
            context.getLogger().log("TransfromLambdaHandler::1 -- "+fileName);
            // Write the request body
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.writeBytes(jsonInputString);
                wr.flush();
            }
            context.getLogger().log("TransfromLambdaHandler::2 -- "+fileName);
            // Get the response code
            int responseCode = connection.getResponseCode();
            context.getLogger().log("TransfromLambdaHandler::3 -- responseCode = "+responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) { // success
            	context.getLogger().log("RestApiLambdaHandler::4 ");
                // Read the response
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                // Print the response
                context.getLogger().log("Response: " + response.toString());
                
                //invokeS3PutLamda(context, fileName, response.toString());
                
                fileName = fileName.replaceFirst(".xml", ".json");
                String bucketName = "epcis2.0documents";
                context.getLogger().log("Put request 1 -- Transformation start -- fileName = "+fileName+" --- bucketName = "+bucketName);
                
                
                AmazonS3 s3client = AmazonS3Client.builder().withRegion(Regions.US_EAST_1)
    					.withCredentials(new DefaultAWSCredentialsProviderChain()).build();
                
                context.getLogger().log("Put request 2 -- s3client = "+s3client);
                ObjectMetadata metadata = new ObjectMetadata();
                InputStream streamIn = new ByteArrayInputStream(response.toString().getBytes());
                metadata.setContentLength(streamIn.available());
                context.getLogger().log("Put request 3 -- file size = "+streamIn.available());
                //PutObjectRequest putRequest = new PutObjectRequest("epcis2.0documents", fileName, streamIn, metadata);
                
                PutObjectRequest putRequest = new PutObjectRequest("epcis2.0documents", fileName, streamIn, metadata);
                s3client.putObject(putRequest);
                
                //PutObjectResult result = s3client.putObject(bucketName, fileName, streamIn, metadata);
                context.getLogger().log("Put request 4 -- complete");

            } else {
                context.getLogger().log("POST request failed. Response Code: " + responseCode);
            }
           
            // Disconnect the connection
            connection.disconnect();
			
		} catch (Exception e) {
			return "Error while reading file from S3 :::" + e.getMessage();
		} 
		finally {
			context.getLogger().log("----------------------------------- finally end");
		}

		return "Transformation end successfully";
	}

	private void invokeS3PutLamda(Context context, String fileName, String fileContent) {
		context.getLogger().log("invoke TransTest start  == " + fileName);
		JSONObject payloadObject = new JSONObject();
		payloadObject.put("fileContent", fileContent);
		payloadObject.put("fileName", fileName);
		
		AWSLambda client = AWSLambdaAsyncClient.builder().withRegion(Regions.US_EAST_1).build();

		InvokeRequest request = new InvokeRequest();
		request.withFunctionName("arn:aws:lambda:us-east-1:654654535046:function:TransTest").withPayload(payloadObject.toString());
		//context.getLogger().log("Calling TransfromLambdaHandler with payload = "+payloadObject.toString());
		InvokeResult invoke = client.invoke(request);
		context.getLogger().log("invoke TransTest done ");
		
	}


	
	


}
