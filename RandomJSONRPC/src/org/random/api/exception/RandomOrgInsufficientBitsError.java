package org.random.api.exception;

/** Exception raised by the RandomOrgClient class when its API key's 
 ** request has exceeded its remaining server bits allowance. If the 
 ** client is currently issuing large requests it may be possible to 
 ** succeed with smaller requests. Use the client's getBitsLeft() call 
 ** or the getBits() in this class to help determine if an alternative 
 ** request size is appropriate.
 **/
public class RandomOrgInsufficientBitsError extends RuntimeException {

	private int bits = -1;
	
	/** Constructs a new exception with the specified detail message.
	 **
	 ** @param message @see java.lang.Exception#Exception(java.lang.String) 
	 ** @param bits remaining just before error thrown
	 **/
	public RandomOrgInsufficientBitsError(String message, int bits) {
		super(message);
		
		this.bits = bits;
	}
	
	/** @return bits remaining just before error.
	 **/
	public int getBits() {
		return this.bits;
	}
}