package com.usaid;

public interface TIOPConstants {
	String dburl = "jdbc:mysql://tiopdevtestdb.cx8a24s68i3t.us-east-1.rds.amazonaws.com:3306/tiopdb";
	String dbuser = "schatterjee";
	String dbpass = "Test!234";  //secret
	//String dbsecret = "dbsecret";  //secret
	
	//String ec2_xml_to_json_convert_url = "http://ec2-3-89-88-184.compute-1.amazonaws.com:8080/api/convert/json/2.0";
	//String ec2_xml_to_json_convert_url = "http://ec2-3-89-65-81.compute-1.amazonaws.com:8080/api/convert/json/2.0";
	String ec2_xml_to_json_convert_url = "http://ec2-44-201-249-14.compute-1.amazonaws.com:8080/api/convert/json/2.0";
	
	String destinationS3 = "epcis2.0documents"; 
	
	String smtp_host = "email-smtp.us-east-1.amazonaws.com";
	String smtp_user = "AKIAZQ3DTOWDBHUFO3C5";
	String smtp_pass = "BGnGmWJ3UVwtqOVsnvMmeLw80SfK60NnGovB6Z0mFb3n";  //secret
	String smtp_port = "587";
	
	String toEmail = "HSS-GS1GlobalStandards-HQ@ghsc-psm.org";
	String fromEmail = "schatterjee@ghsc-psm.org";
	
	String dbdriver = "com.mysql.jdbc.Driver";
	String EPCISBody = "EPCISBody";
	String EventList = "EventList";
	String ObjectEvent = "ObjectEvent";
	String AggregationEvent = "AggregationEvent";
	String bizStep = "bizStep";
	String shipping = ":shipping";
	String epcList = "epcList";
	String tiop_nts_gln = "tiop:nts_gln";
	String childEPCs = "childEPCs";
	String parentID = "parentID";
	String urn_sgtin = "urn:epc:id:sgtin";
	String bizLocation = "bizLocation";
	String epc = "epc";
	String eventTime = "eventTime";
	String eventTimeZoneOffset = "eventTimeZoneOffset";
	String extension = "extension";
	String destinationList = "destinationList";
	String destination = "destination";
}
