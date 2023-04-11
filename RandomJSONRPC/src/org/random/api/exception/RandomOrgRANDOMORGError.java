package org.random.api.exception;

/** 
 * Exception raised by the RandomOrgClient class when the server
 * returns a RANDOM.ORG Error.
 *
 * @see https://api.random.org/json-rpc/4/error-codes
 */
public class RandomOrgRANDOMORGError extends RuntimeException {
	int code = -1;
	
	/** 
	 * Constructs a new exception with the specified detail message.
	 *
	 * @param message @see java.lang.Exception#Exception(java.lang.String) 
	 */
	public RandomOrgRANDOMORGError(String message) {
		super(message);
	}

	/** 
	 * Constructs a new exception with the specified detail message and
	 * error code.
	 *
	 * @param message @see java.lang.Exception#Exception(java.lang.String)
	 * @param code The error code as specified here: https://api.random.org/json-rpc/4/error-codes
	 */
	public RandomOrgRANDOMORGError(String message, int code) {
		super(message);
		this.code = code;
	}
	
	/**
	 * Returns the error code (or -1 if none was specified). See here for
	 * more information: https://api.random.org/json-rpc/4/error-codes
	 */
	public int getCode() {
		return code;
	}
}