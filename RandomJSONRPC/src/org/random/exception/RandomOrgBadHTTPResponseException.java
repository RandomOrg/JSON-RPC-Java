package org.random.api.exception;

/** Exception raised by the RandomOrgClient class when the connection
 ** doesn't return a HTTP 200 OK response.
 **/
public class RandomOrgBadHTTPResponseException extends RuntimeException {

	/** Constructs a new exception with the specified detail message.
	 **
	 ** @param message @see java.lang.Exception#Exception(java.lang.String) 
	 **/
	public RandomOrgBadHTTPResponseException(String message) {
		super(message);
	}
}