package org.random.api.exception;

/** Exception raised by the RandomOrgClient class when its API key's 
 ** server allowance has been exceeded. This indicates that a back-off 
 ** until midnight UTC is in effect, before which no requests will be 
 ** sent as no meaningful server responses will be returned.
 **/
public class RandomOrgAllowanceExceededException extends RuntimeException {

	/** Constructs a new exception with the specified detail message.
	 **
	 ** @param message @see java.lang.Exception#Exception(java.lang.String) 
	 **/
	public RandomOrgAllowanceExceededException(String message) {
		super(message);
	}
}