package com.usaid;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class TIOPUtil {

	public static Connection getConnection() throws ClassNotFoundException, SQLException {
		String secretDetails = getSecretDetails(System.getenv(TIOPConstants.dbSecret));
		//System.out.println("TIOPUtil::getConnection::secretDetails = "+secretDetails);
		DBInfo dbInfo = getDBInfo(secretDetails);
		//System.out.println("TIOPUtil::getConnection::dbInfo = "+dbInfo.toString());
		String username = dbInfo.getUsername();
		String password = dbInfo.getPassword();
		String host = dbInfo.getHost();
		String port = dbInfo.getPort();
		String dbUrl = "jdbc:mysql://"+host+":"+port+"/tiopdb";
		System.out.println("TIOPUtil::getConnection::dbUrl = "+dbUrl);
		Class.forName(TIOPConstants.dbdriver);
		return DriverManager.getConnection(dbUrl, username, password);
	}

	public static String getSecretDetails(String secretName) {
		Region region = Region.US_EAST_1;
		SecretsManagerClient secretsClient = SecretsManagerClient.builder().region(region).build();
		String secret = "none";
		try {
			GetSecretValueRequest valueRequest = GetSecretValueRequest.builder().secretId(secretName).build();
			GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
			secret = valueResponse.secretString();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return secret;
	}

	public static String getKeyValue(String secret, String key) {
		String str = "";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode node;
		try {
			node = mapper.readTree(secret);
			if (node.get(key) != null)
				str = node.get(key).toString();
		} catch (Exception e) {
			e.printStackTrace();
		} 

		return str.replaceAll("\"", "");
	}
	
	public static DBInfo getDBInfo(String secret) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			DBInfo dbInfo = mapper.readValue(secret, DBInfo.class);
			return dbInfo;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
