package com.usaid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;

public class TIOPPurgeLambdaHandler implements RequestHandler<S3Event, String> {

	private Connection con;
	Context context = null;

	@Override
	public String handleRequest(S3Event s3Event, Context context) {

		this.context = context;
		String purgedBucketName = "";
		String purgedDocumentName = "";

		context.getLogger().log("TIOPDocumentAuth::handleRequest ::: Start");

		purgedBucketName = s3Event.getRecords().get(0).getS3().getBucket().getName();
		purgedDocumentName = s3Event.getRecords().get(0).getS3().getObject().getKey();

		context.getLogger().log("Purged Document Name: " + purgedDocumentName);

		try {

			insertPurgedDocDetails(purgedDocumentName);

		} catch (Exception ex) {

			ex.printStackTrace();
			context.getLogger()
					.log(" Purged document insertion Failed ::: db error = " + ex.getMessage());
		}

		return "Success";
	}

	/*
	 * Method to insert the failure scenario
	 */
	private void insertPurgedDocDetails(String purgedFileName) {

		String query = "INSERT INTO tiopdb.tiop_operation ( event_type_id, source_partner_id, destination_partner_id, source_location_id, destination_location_id, item_id, rule_id, status_id, document_name, object_event_count, aggregation_event_count, exception_detail, create_date, creator_id, last_modified_date, last_modified_by, current_indicator, ods_text)\r\n"
				+ "(SELECT DISTINCT\r\n" + "null as event_type_id,\r\n"
				+ "tp.source_partner_id, \r\n" + "tp.destination_partner_id, \r\n"
				+ "tp.source_location_id, \r\n" + "tp.destination_location_id, \r\n"
				+ "tp.item_id, \r\n" + "tp.rule_id, \r\n" + "10 as status_id , \r\n"
				+ "tp.document_name, \r\n" + "tp.object_event_count, \r\n"
				+ "tp.aggregation_event_count,\r\n" + "null as exception_detail ,\r\n"
				+ "current_timestamp as create_date,\r\n"
				+ "'tiop_purged', -- id that insert data in tiopdb\r\n"
				+ "current_timestamp as last_modified_date ,\r\n"
				+ "'tiop_purged', -- id that insert data in tiopdb\r\n"
				+ "'A' as current_indicator,\r\n" + "'' as ods_text\r\n"
				+ "FROM tiopdb.tiop_operation tp where tp.document_name ='" + purgedFileName + "')";

		try {
			context.getLogger().log("inserting Purged Document details to DB ::: Start");

			con = getConnection();
			Statement stmt = con.createStatement();
			context.getLogger().log("Query for purged document insert ::: = " + query);
			stmt.executeUpdate(query);

			context.getLogger().log(" Purged document inserted successfully.");

		} catch (Exception e) {

			e.printStackTrace();
			String message = e.getMessage();

			context.getLogger()
					.log("TIOPPurgeLambdaHandler Exception::Failure message: is " + message);
			insertPurgeErrorLog(purgedFileName, message);

			Date date = new Date();
			SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
			String strDate = formatter.format(date);
			final String htmlBody = "<h4>An issue [EXC013] encountered while inserting purged document "
					+ purgedFileName + " details to the database on " + strDate + ".</h4>"
					+ "<h4>Details of the Issue:</h4>"
					+ "<p>An error occurred in bulkload while insering purged document details to TIOP database."
					+ message + "</p>" + "<p>TIOP operation team</p>";
			
			TIOPPurgeSendEmail.sendMail(context, purgedFileName, htmlBody);
		}

	}

	private void insertPurgeErrorLog(String purgedFileName, String message) {

		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String strDate = formatter.format(date);

		String selectQuery = "SELECT DISTINCT\r\n" + "tp.event_type_id,\r\n"
				+ "tp.source_partner_id, \r\n" + "tp.destination_partner_id, \r\n"
				+ "tp.source_location_id, \r\n" + "tp.destination_location_id, \r\n"
				+ "tp.item_id, \r\n" + "tp.rule_id, \r\n" + "10 as status_id , \r\n"
				+ "tp.document_name, \r\n" + "tp.object_event_count, \r\n"
				+ "tp.aggregation_event_count,\r\n" + "null as exception_detail ,\r\n"
				+ "current_timestamp as create_date,\r\n"
				+ "'tiop_purged', -- id that insert data in tiopdb\r\n"
				+ "current_timestamp as last_modified_date ,\r\n"
				+ "'tiop_purged', -- id that insert data in tiopdb\r\n"
				+ "'A' as current_indicator,\r\n" + "'' as ods_text\r\n"
				+ "FROM tiopdb.tiop_operation tp where tp.document_name ='" + purgedFileName + "'";

		try (Connection con = getConnection();
				Statement stmt = con.createStatement();
				ResultSet rs = stmt.executeQuery(selectQuery)) {

			context.getLogger().log(
					"Query for selecting existing document details during the failure of inserting purged details to DB ::: = "
							+ selectQuery);

			int source_partner_id = 0;
			int destination_partner_id = 0;
			int source_location_id = 0;
			int destination_location_id = 0;
			int item_id = 0;
			int rule_id = 0;
			int status_id = 0;
			int object_event_count = 0;
			int aggregation_event_count = 0;
			String current_indicator = "";
			String ods_text = "";

			if (rs != null && rs.next()) {
				source_partner_id = rs.getInt("source_partner_id");
				destination_partner_id = rs.getInt("destination_partner_id");
				source_location_id = rs.getInt("source_location_id");
				destination_location_id = rs.getInt("destination_location_id");
				item_id = rs.getInt("item_id");
				rule_id = rs.getInt("rule_id");
				status_id = rs.getInt("status_id");
				object_event_count = rs.getInt("object_event_count");
				aggregation_event_count = rs.getInt("aggregation_event_count");
				current_indicator = rs.getString("current_indicator");
				ods_text = rs.getString("ods_text");

			}

			String insertQuery = "INSERT INTO tiopdb.tiop_operation ("
					+ " source_partner_id, destination_partner_id, source_location_id, "
					+ "destination_location_id, item_id, rule_id, status_id, document_name, "
					+ "object_event_count, aggregation_event_count, exception_detail, create_date, "
					+ "creator_id, last_modified_date, last_modified_by, current_indicator, ods_text) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

			try (Connection conn = getConnection();
					PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
				// Set the values dynamically

				pstmt.setInt(1, source_partner_id);
				pstmt.setInt(2, destination_partner_id);
				pstmt.setInt(3, source_location_id);
				pstmt.setInt(4, destination_location_id);
				pstmt.setInt(5, item_id);
				pstmt.setInt(6, rule_id);
				pstmt.setInt(7, status_id);
				pstmt.setString(8, purgedFileName);
				pstmt.setInt(9, object_event_count);
				pstmt.setInt(10, aggregation_event_count);
				pstmt.setString(11, message); // exception_detail, if any
				pstmt.setString(12, strDate); // Ensure this is a properly formatted date
				pstmt.setString(13, "tiop_purged");
				pstmt.setString(14, strDate); // Ensure this is a properly formatted date
				pstmt.setString(15, "tiop_purged");
				pstmt.setString(16, current_indicator);
				pstmt.setString(17, ods_text);

				// Execute the insert statement
				int rowsInserted = pstmt.executeUpdate();

			} catch (Exception e) {
				context.getLogger().log("insertErrorLog while inserting the purge DB updation failure ::: db error = " + e.getMessage());
				e.printStackTrace();
			}

			 context.getLogger().log("insertErrorLog ::: query inserted successfully.");
		} catch (Exception e) {
			context.getLogger().log("insertErrorLog ::: db error = " + e.getMessage());
			e.printStackTrace();
		}

	}

	private Connection getConnection() throws ClassNotFoundException, SQLException {
		if (con == null || con.isClosed()) {
			con = TIOPUtil.getConnection();
		}
		return con;
	}

	public static void main(String args[]) {

		TIOPPurgeLambdaHandler handler = new TIOPPurgeLambdaHandler();

		String documentName = "jgeorge/EPCIS_Document_TST_01082024_v1_026.xml";

		handler.insertPurgeErrorLog(documentName, "");
	}

}
