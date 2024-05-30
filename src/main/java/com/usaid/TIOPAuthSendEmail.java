package com.usaid;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

public class TIOPAuthSendEmail {

	static void sendMail(Context context, int type, String to, String fileName, String source, String destination, String gtin) {
		to = "swarchat@in.ibm.com";  //HSS-GS1GlobalStandards-HQ@ghsc-psm.org
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
		String strDate = formatter.format(date);
		
//		source = "urn:epc:id:sgln:87922754.0001.0";
//		destination = "2340009878907";
//		gtin = "urn:epc:id:sgtin:0000128.239405";
		
		context.getLogger().log("Email:: source = " + source+ " --- destination = "+destination+"  --- gtin = "+gtin);
//		context.getLogger().log("log ===> Manufacture GLN uri ["+source+"], recipient country GLN ["+destination+"], and GTIN uri ["+gtin+"] combination does not exist in TIOP business rules.");

		final String FROM = "schatterjee@ghsc-psm.org";
		final String SUBJECT = "File Processing Issue: ["+fileName+"] - Attention Needed";
//		final String HTMLBODY =  "<p>An issue encountered while processing the file "+fileName+" which was received on ["+strDate+"]</P>"
//				               + "<p><b>Details of the Issue:<b>​"
//				               + "<br>GTIN [urn:epc:id:sscc:87922754.112345678] does not exist in the TIOP business rules"
//				               + "<p><br>TIOP operation team​";
		
		//final String HTMLBODY = "<h4>An issue [EXC001] encountered while processing the file "+fileName+" which was received on ["+strDate+"].</h4><h4>Details of the Issue:</h4>Manufacture GLN uri ["+gtin+"], recipient country GLN ["+destination+"], and GTIN uri ["+source+"] combination does not exist in TIOP business rules.</p><p>TIOP operation team</p>";
		
		final String HTMLBODY = "<h4>An issue [EXC001] encountered while processing the file "+fileName+" which was received on "+strDate+".</h4>"
				+ "<h4>Details of the Issue:</h4>"
				+ "Manufacture GLN uri ["+source+"], recipient country GLN ["+destination+"], and GTIN uri ["+gtin+"] combination does not exist in TIOP business rules.</p>"
				+ "<p>TIOP operation team</p>";
		
		final String TEXTBODY = "This email was sent through Amazon SES using the AWS SDK for Java.";
		
		List<String> toAddress = new ArrayList<String>();
		toAddress.add(to);
		toAddress.add("wirshad@us.ibm.com");
		toAddress.add("jaideep.joshi@ibm.com");

		try {
			AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
					// Replace US_WEST_2 with the AWS Region you're using for
					// Amazon SES.
					.withRegion(Regions.US_EAST_1).build();
			context.getLogger().log("The email send start - 1");
			SendEmailRequest request = new SendEmailRequest().withDestination(new Destination().withToAddresses(toAddress))
					.withMessage(new Message()
					.withBody(new Body().withHtml(new Content().withCharset("UTF-8").withData(HTMLBODY))
					.withText(new Content().withCharset("UTF-8").withData(TEXTBODY)))
					.withSubject(new Content().withCharset("UTF-8").withData(SUBJECT)))
					.withSource(FROM);
			// Comment or remove the next line if you are not using a
			// configuration set
			// .withConfigurationSetName(CONFIGSET)
			//;
			context.getLogger().log("The email send start - to "+to);
			client.sendEmail(request);
			context.getLogger().log("Email sent to -- " + to);
		} catch (Exception ex) {
			context.getLogger().log("The email was not sent. Error message: " + ex.getMessage());
		}

	}

}
