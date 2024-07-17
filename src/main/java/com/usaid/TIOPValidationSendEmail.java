package com.usaid;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

public class TIOPValidationSendEmail {

	static void sendMail(Context context, int type, Set<String> toEmailSet, String fileName, String message) {
		context.getLogger().log("TIOPValidationSendEmail::sendMail start - to "+toEmailSet+" --- message = "+message);
		String expId = "";
		List<String> toAddress = new ArrayList<String>();
		
		if(message.contains("#")) {
			String arr[] = message.split("#");
			expId = arr[0];
			message = arr[2];
		}
		
		context.getLogger().log("updated - to "+toEmailSet+" --- message = "+message);
		
		if(toEmailSet != null && !toEmailSet.isEmpty()) {
			for(String emailId : toEmailSet) {
				toAddress.add(emailId);
			}
		} 
		
		String to = System.getenv(TIOPConstants.toEmailId); 
		if(to !=null && to.contains(",")) {
			String arr[] = to.split(",");
			for(int i=0; i<arr.length; i++) {
				toAddress.add(arr[i].trim());
			}
		} else if(to !=null) {
			toAddress.add(to);
		}
		
		Date cDate = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
		String curDate = formatter.format(cDate);
		
		Calendar c = Calendar.getInstance(); 
		c.setTime(cDate); 
		c.add(Calendar.DATE, 2);
		cDate = c.getTime();
		String toDate = formatter.format(cDate);
		
		context.getLogger().log("For testing purpose memail is ending to "+to+"  -- curDate = "+curDate+"  -- toDate = "+toDate);
		String FROM = System.getenv(TIOPConstants.fromEmailId);
		String env = System.getenv(TIOPConstants.env);
		String SUBJECT = "["+env.toUpperCase()+"] File Processing Issue: ["+fileName+"] - Your Attention Needed";
		
		final String HTMLBODY = "Dear TIOP Partner,"
				+ "<p>We are writing to inform you of an issue ["+expId+"] encountered while processing the file "+fileName+" which we received on "+curDate+"."
				+ "<h4>Details of the Issue:</h4>"
				+ message
				+ "<h4>Next Steps:</h4>"
				+ "<p>We kindly request a corrected file by "+toDate+" to ensure timely processing of the data.</P>"
				+ "<p>Thank you for your cooperation</p>"
				+ "<p>Sincerely,</P>"
			    + "<p>TIOP operation team</P>";
		
		final String TEXTBODY = "This email was sent through Amazon SES using the AWS SDK for Java.";

		try {
			context.getLogger().log("The email send start");
			sendMail(context, toAddress, FROM, SUBJECT, HTMLBODY, TEXTBODY);
		} catch (Exception ex) {
			context.getLogger().log("The email was not sent. Error message: " + ex.getMessage());
		}

	}

	private static void sendMail(Context context, List<String> toAddress, final String FROM, final String SUBJECT,
			final String HTMLBODY, final String TEXTBODY) {
		AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
				// Replace US_WEST_2 with the AWS Region you're using for
				// Amazon SES.
				.withRegion(Regions.US_EAST_1).build();
		context.getLogger().log("sendMail start");
		SendEmailRequest request = new SendEmailRequest().withDestination(new Destination().withToAddresses(toAddress))
				.withMessage(new Message()
				.withBody(new Body().withHtml(new Content().withCharset("UTF-8").withData(HTMLBODY))
				.withText(new Content().withCharset("UTF-8").withData(TEXTBODY)))
				.withSubject(new Content().withCharset("UTF-8").withData(SUBJECT)))
				.withSource(FROM);
		context.getLogger().log("The email send start - to "+toAddress);
		client.sendEmail(request);
		context.getLogger().log("Email sent to -- " + toAddress);
	}

}
