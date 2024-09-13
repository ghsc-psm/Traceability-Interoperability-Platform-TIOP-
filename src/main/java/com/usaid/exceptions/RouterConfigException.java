package com.usaid.exceptions;

public class RouterConfigException extends RuntimeException{
	
	private String errorCode;

	public RouterConfigException() { 

	}

	public RouterConfigException(String errorCode, String errorDescription) {
		super(errorDescription);
		this.errorCode = errorCode;
	}

	public String getErrorCode() {

		return this.errorCode;
	}


}
