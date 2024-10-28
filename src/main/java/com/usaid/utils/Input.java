package com.usaid.utils;

import java.io.InputStream;
import java.io.Reader;

import org.w3c.dom.ls.LSInput;

public class Input implements LSInput{
	
    private String publicId;
    private String systemId;
    private InputStream inputStream;
    
	public Input(String publicId, String systemId, InputStream inputStream) {
        this.publicId = publicId;
        this.systemId = systemId;
        this.inputStream = inputStream;
    }

	@Override
	public Reader getCharacterStream() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setCharacterStream(Reader characterStream) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public InputStream getByteStream() {
		
		return inputStream;
	}

	@Override
	public void setByteStream(InputStream byteStream) {
		  this.inputStream = inputStream;
		
	}

	@Override
	public String getStringData() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setStringData(String stringData) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getSystemId() {
		 return systemId;
	}

	@Override
	public void setSystemId(String systemId) {
		 this.systemId = systemId;
		
	}

	@Override
	public String getPublicId() {
		 return publicId;
	}

	@Override
	public void setPublicId(String publicId) {
		  this.publicId = publicId;
		
	}

	@Override
	public String getBaseURI() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setBaseURI(String baseURI) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getEncoding() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setEncoding(String encoding) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean getCertifiedText() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setCertifiedText(boolean certifiedText) {
		// TODO Auto-generated method stub
		
	}

}
