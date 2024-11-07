package com.usaid.utils;

import java.io.InputStream;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import com.usaid.ValidateLambdaHandler;

public class CustomResourceResolver implements LSResourceResolver {

	  private final String basePath;
	  
	  public CustomResourceResolver(String basePath) {
          this.basePath = basePath; // Base path where your schemas are stored
      }
	  
	@Override
	public LSInput resolveResource(String type, String namespaceURI, String publicId,
			String systemId, String baseURI) {
		 try {
             // Resolve the file path relative to the base path
             String resolvedPath = basePath + systemId;
             InputStream resourceAsStream = ValidateLambdaHandler.class.getResourceAsStream(resolvedPath);

             if (resourceAsStream == null) {
                 System.err.println("Could not resolve the schema: " + systemId);
                 return null;
             }

             return new Input(publicId, systemId, resourceAsStream);
         } catch (Exception e) {
             e.printStackTrace();
             return null;
         }
	}

}
