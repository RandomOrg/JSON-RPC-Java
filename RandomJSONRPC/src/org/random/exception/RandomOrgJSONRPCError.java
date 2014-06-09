package org.random.api.exception;

/** Exception raised by the RandomOrgClient class when the server
 ** returns a JSON-RPC Error.
 **
 ** @see https://api.random.org/json-rpc/1/error-codes
 **/
public class RandomOrgJSONRPCError extends RuntimeException {

	/** Constructs a new exception with the specified detail message.
	 **
	 ** @param message @see java.lang.Exception#Exception(java.lang.String) 
	 **/
	public RandomOrgJSONRPCError(String message) {
		super(message);
	}
}