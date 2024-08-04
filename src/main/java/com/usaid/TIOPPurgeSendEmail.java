package com.usaid;

import java.util.ArrayList;
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

public class TIOPPurgeSendEmail {
	
	public static void sendMail(Context context, String fileName, String htmlBody) {
		String to = System.getenv(TIOPConstants.toEmailId); //"HSS-GS1GlobalStandards-HQ@ghsc-psm.org";
		List<String> toAddress = new ArrayList<String>();
		if(to !=null && to.contains(",")) {
			String arr[] = to.split(",");
			for(int i=0; i<arr.length; i++) {
				toAddress.add(arr[i].trim());
			}
		} else if(to !=null) {
			toAddress.add(to);
		}
		
		String env = System.getenv(TIOPConstants.env);
		final String SUBJECT = "["+env.toUpperCase()+"] File Processing Issue: ["+fileName+"] - Attention Needed";
		final String TEXTBODY = "This email was sent through Amazon SES using the AWS SDK for Java.";
		final String FROM = System.getenv(TIOPConstants.fromEmailId);
		try {
			sendMail(context, htmlBody, toAddress, SUBJECT, TEXTBODY, FROM);
		} catch (Exception ex) {
			context.getLogger().log("The email was not sent. Error message: " + ex.getMessage());
		}
		
	}

	private static void sendMail(Context context, String htmlBody, List<String> toAddress, final String SUBJECT,
			final String TEXTBODY, final String FROM) {
		AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
				.withRegion(Regions.US_EAST_1).build();
		context.getLogger().log("The email send start - 1");
		SendEmailRequest request = new SendEmailRequest().withDestination(new Destination().withToAddresses(toAddress))
				.withMessage(new Message()
				.withBody(new Body().withHtml(new Content().withCharset("UTF-8").withData(htmlBody))
				.withText(new Content().withCharset("UTF-8").withData(TEXTBODY)))
				.withSubject(new Content().withCharset("UTF-8").withData(SUBJECT)))
				.withSource(FROM);
		context.getLogger().log("The email send start - to "+toAddress);
		client.sendEmail(request);
		context.getLogger().log("Email sent to -- " + toAddress);
	}


}
