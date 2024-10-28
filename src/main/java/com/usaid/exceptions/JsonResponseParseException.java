package com.usaid.exceptions;

public class JsonResponseParseException extends RuntimeException {
	
	private String errorCode;

	public JsonResponseParseException() { 

	}

	public JsonResponseParseException(String errorCode, String errorDescription) {
		super(errorDescription);
		this.errorCode = errorCode;
	}

	public String getErrorCode() {

		return this.errorCode;
	}


}
