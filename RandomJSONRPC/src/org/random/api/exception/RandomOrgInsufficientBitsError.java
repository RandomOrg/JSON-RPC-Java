package org.random.api.exception;

/** Exception raised by the RandomOrgClient class when its API key's 
 ** request has exceeded its remaining server bits allowance. If the 
 ** client is currently issuing large requests it may be possible to 
 ** succeed with smaller requests. Use the client's getBitsLeft() call 
 ** to help determine if an alternative request size is appropriate.
 **/
public class RandomOrgInsufficientBitsError extends RuntimeException {

	/** Constructs a new exception with the specified detail message.
	 **
	 ** @param message @see java.lang.Exception#Exception(java.lang.String) 
	 **/
	public RandomOrgInsufficientBitsError(String message) {
		super(message);
	}
}