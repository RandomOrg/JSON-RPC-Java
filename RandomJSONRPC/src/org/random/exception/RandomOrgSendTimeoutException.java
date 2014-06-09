package org.random.api.exception;

/** Exception raised by the RandomOrgClient class when its set 
 ** blocking timeout is exceeded before the request can be sent. 
 **/
public class RandomOrgSendTimeoutException extends RuntimeException {

	/** Constructs a new exception with the specified detail message.
	 **
	 ** @param message @see java.lang.Exception#Exception(java.lang.String) 
	 **/
	public RandomOrgSendTimeoutException(String message) {
		super(message);
	}
}