package org.random.api.exception;

/** Exception raised by the RandomOrgClient class when its API key's 
 ** server requests allowance has been exceeded. This indicates that a 
 ** back-off until midnight UTC is in effect, before which no requests 
 ** will be sent by the client as no meaningful server responses will 
 ** be returned.
 **/
public class RandomOrgInsufficientRequestsError extends RuntimeException {

	/** Constructs a new exception with the specified detail message.
	 **
	 ** @param message @see java.lang.Exception#Exception(java.lang.String) 
	 **/
	public RandomOrgInsufficientRequestsError(String message) {
		super(message);
	}
}