package com.usaid;

public interface TIOPConstants {
	String dbSecret = "dbSecretName";
	
	String xmlToJsonConversionURL = "xmlToJsonConversionURL";
	
	String destinationS3 = "destinationS3"; 
	
	String toEmailId = "toEmailId"; //"HSS-GS1GlobalStandards-HQ@ghsc-psm.org";
	String fromEmailId = "fromEmailId";
	
	String env = "env";
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
	
	String ValidationLambda = "TIOPDocumentValidation";
	String TransformLambda = "TIOPDocumentTransform";
	String RouterLambda = "TIOPRouter";

	String blSecretName = "blSecretName";
	String smtpSecretName = "smtpSecretName";
}
