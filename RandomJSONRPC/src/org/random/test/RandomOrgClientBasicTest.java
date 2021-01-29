package org.random.test;

import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.random.api.RandomOrgCache;
import org.random.api.RandomOrgClient;
import org.random.api.exception.RandomOrgRANDOMORGError;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import static org.hamcrest.Matchers.*;

/** 
 * A set of tests for RandomOrgClient.java
 */
public class RandomOrgClientBasicTest {
	
	@Rule
	public ErrorCollector collector = new ErrorCollector();
	
	private static RandomOrgClient[] rocs = new RandomOrgClient[2];
	
	private static final String API_KEY_1 = "YOUR_API_KEY_HERE";
	private static final String API_KEY_2 = "YOUR_API_KEY_HERE";
	
	private static final int BIT_QUOTA = 1000000;
	
	private static final int[] LENGTH = {3, 4, 5, 6};
	private static final int[] MIN = {0, 10, 20, 30};
	private static final int[] MAX = {40, 50, 60, 70};
	private static final boolean[] REPLACEMENT = {false, true, false, true};
	private static final int[] BASE = {2, 8, 10, 16};
	
	private static JsonObject userData = new JsonObject();
	
	@BeforeClass
	public static void testSetup() {
		rocs[0] = RandomOrgClient.getRandomOrgClient(API_KEY_1, 5000, 120000, false);
		rocs[1] = RandomOrgClient.getRandomOrgClient(API_KEY_2);
		
		userData.addProperty("Test", "Text");
		userData.addProperty("Test2", "Text2");
	}
	
	@Test
	public void testInfo() {
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(client(i), roc.getBitsLeft(), greaterThanOrEqualTo(0));
				collector.checkThat(client(i), roc.getRequestsLeft(), greaterThanOrEqualTo(0));
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	
	@Test
	public void testAPIKeyDuplication() {
		RandomOrgClient dupe = RandomOrgClient.getRandomOrgClient(API_KEY_1);
		
		collector.checkThat("RandomOrgClient roc1 should not be the same as "
				+ "RandomOrgClient roc2", rocs[0].equals(rocs[1]), equalTo(false));
		collector.checkThat("RandomOrgClient roc1 and RandomOrgClient dupe "
				+ "should be the same", rocs[0].equals(dupe), equalTo(true));
	}
	
	
	@Test
	public void testPositiveGetBitsLeft(){
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				int bitsLeft = roc.getBitsLeft();
				collector.checkThat(client(i), bitsLeft, greaterThanOrEqualTo(0));
				collector.checkThat(client(i) + i, bitsLeft, lessThanOrEqualTo(BIT_QUOTA));
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}

	// Test Errors
	
	@Test
	public void testNegativeErrorMessage202(){
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				roc.generateIntegers(100000, 0, 10);
				collector.addError(new Error(errorMessage(i)));
			} catch(RandomOrgRANDOMORGError e) {
				System.out.println(e.getMessage());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e)));
			}
			i++;
		}
	}
	
	@Test
	public void testNegativeErrorMessage203(){
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				roc.generateIntegerSequences(3, LENGTH, MIN, MAX);
				collector.addError(new Error(errorMessage(i)));
			} catch(RandomOrgRANDOMORGError e) {
				System.out.println(e.getMessage());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e)));
			}
			i++;
		}
	}
	
	@Test
	public void testNegativeErrorMessage204(){
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				roc.generateIntegerSequences(4, new int[] {1}, MIN, MAX);
				collector.addError(new Error(errorMessage(i)));
			} catch(RandomOrgRANDOMORGError e) {
				System.out.println(e.getMessage());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e)));
			}
			i++;
		}
	}

	@Test
	public void testNegativeErrorMessage300(){
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				roc.generateIntegers(10, 10, 0);
				collector.addError(new Error(errorMessage(i)));
			} catch(RandomOrgRANDOMORGError e) {
				System.out.println(e.getMessage());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e)));
			}
			i++;
		}
	}
	
	@Test
	public void testNegativeErrorMessage301(){
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				roc.generateIntegers(20, 0, 10, false);
				collector.addError(new Error(errorMessage(i)));
			} catch(RandomOrgRANDOMORGError e) {
				System.out.println(e.getMessage());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e)));
			}
			i++;
		}
	}

	@Test
	public void testNegativeErrorMessage400(){
		try{
			RandomOrgClient rpc2 = RandomOrgClient.getRandomOrgClient("ffffffff-ffff-ffff-ffff-ffffffffffff");
			rpc2.generateIntegers(1, 0, 1);
			collector.addError(new Error(errorMessage()));
		} catch(RandomOrgRANDOMORGError e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			collector.addError(new Error(errorMessage(e)));
		}
	}
	
	@Test
	public void testNegativeErrorMessage420(){
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				roc.generateSignedIntegers(5, 0, 10, false, 10, null, "ffffffffffffffff");
				collector.addError(new Error(errorMessage(i)));
			} catch(RandomOrgRANDOMORGError e) {
				System.out.println(e.getMessage());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e)));
			}
			i++;
		}
	}
	
	@Test
	public void testNegativeErrorMessage421(){
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				roc.generateSignedIntegers(5, 0, 10, false, 10, null, "d5b8f6d03f99a134");
				collector.addError(new Error(errorMessage(i)));
			} catch(RandomOrgRANDOMORGError e) {
				System.out.println(e.getMessage());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e)));
			}
			i++;
		}
	}
	
	@Test
	public void testNegativeErrorMessage422(){
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				
				roc.generateSignedIntegers(5, 0, 10, false, 10, null, ticketId);
				roc.generateSignedIntegers(5, 0, 10, false, 10, null, ticketId);
				
				collector.addError(new Error(errorMessage(i)));
			} catch(RandomOrgRANDOMORGError e) {
				System.out.println(e.getMessage());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e)));
			}
			i++;
		}
	}
	
	// Test Functions

	@Test
	public void testPositiveGenerateInteger_1(){
		// Testing generateIntgers(int n, in min, int max)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateIntegers(10, 0, 10), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateInteger_2(){
		// Testing generateIntgers(int n, in min, int max, boolean replacement)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateIntegers(10, 0, 10, false), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateInteger_3(){
		// Testing generateIntgers(int n, in min, int max, boolean replacement, int base)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateIntegers(10, 0, 10, false, 16), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateIntegerSequences_1(){
		// Testing generateIntegerSequences(int n, int length, int min, int max)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateIntegerSequences(3, 5, 0, 10), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateIntegerSequences_2(){
		// Testing generateIntegerSequences(int n, int length, int min, int max, boolean replacement)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateIntegerSequences(3, 5, 0, 10, false), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateIntegerSequences_3(){
		// Testing generateIntegerSequences(int n, int length, int min, int max, boolean replacement, int base)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateIntegerSequences(3, 5, 0, 10, false, 16), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateIntegerSequences_4(){
		// Testing generateIntegerSequences(int n, int[] length, int[] min, int[] max)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateIntegerSequences(4, LENGTH, MIN, MAX), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateIntegerSequences_5(){
		// Testing generateIntegerSequences(int n, int[] length, int[] min, 
		//				int[] max, boolean[] replacement)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateIntegerSequences(4, LENGTH, MIN, MAX, REPLACEMENT), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateIntegerSequences_6(){
		// Testing generateIntegerSequences(int n, int[] length, int[] min, 
		//				int[] max, boolean[] replacement, int[] base)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateIntegerSequences(4, LENGTH, MIN, MAX, REPLACEMENT, BASE), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}

	@Test
	public void testPositiveGenerateDecimalFractions_1(){
		// Testing generateDecimalFractions(int n, int decimalPlaces)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateDecimalFractions(10, 5), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i ++;
		}
	}

	@Test
	public void testPositiveGenerateDecimalFractions_2(){
		// Testing generateDecimalFractions(int n, int decimalPlaces, boolean replacement)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateDecimalFractions(10, 5, false), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateGaussians(){
		// Testing generateGaussians(int n, double mean, double standardDeviation, 
		// 				double significantDigits)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateGaussians(10, 3.41d, 2.1d, 4), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++; 
		}
	}

	@Test
	public void testPositiveGenerateStrings_1(){
		// Testing generateStrings(int n, int length, String characters)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateStrings(10, 5, "abcd"), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateStrings_2(){
		// Testing generateStrings(int n, int length, String characters, 
		// 				boolean replacement)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateStrings(10, 5, "abcd", false), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateUUIDs(){
		// Testing generateUUIDs(int n)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateUUIDs(10), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}

	@Test
	public void testPositiveGenerateBlobs_1(){
		// Testing generateBlobs(int n, int size)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateBlobs(10, 16), notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}

	@Test
	public void testPositiveGenerateBlobs_2(){
		// Testing generateBlobs(int n, int size, String format)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				collector.checkThat(roc.generateBlobs(10, 16, RandomOrgClient.BLOB_FORMAT_HEX), 
						notNullValue());
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}

	// Test Functions (Signed)
	
	@Test
	public void testPositiveGenerateSignedInteger_1() {
		// Testing generateSignedStrings(int n, int min, int max)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String, Object> o = roc.generateSignedIntegers(10, 0, 10);
				
				this.signedValueTester(roc, i, o, int[].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedInteger_2(){
		// Testing generateSignedStrings(int n, int min, int max, boolean replacement)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedIntegers(10, 0, 10, false);
				
				this.signedValueTester(roc, i, o, int[].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedInteger_3(){
		// Testing generateSignedIntegers(int n, int min, int max, boolean replacement, 
		// 				int base, JsonObject userData) -- decimal base
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedIntegers(10, 0, 10, false, 10, userData);
				
				this.signedValueTester(roc, i, o, int[].class, true);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedInteger_4(){
		// Testing generateSignedIntegers(int n, int min, int max, boolean replacement, 
		// 				int base, JsonObject userData) -- non-decimal base
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedIntegers(10, 0, 10, false, 16, userData);
				
				this.signedValueTester(roc, i, o, String[].class, true);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedInteger_5(){
		// Testing generateSignedIntegers(int n, int min, int max, boolean replacement, 
		// 				int base, JsonObject userData, String ticketId) -- decimal base
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				HashMap<String,Object> o = roc.generateSignedIntegers(10, 0, 10, false, 10, userData, ticketId);
				
				this.signedValueTester(roc, i, o, int[].class, true, ticketId);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedInteger_6(){
		// Testing generateSignedIntegers(int n, int min, int max, boolean replacement, 
		// 				int base, JsonObject userData, String ticketId) -- non-decimal base
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				HashMap<String,Object> o = roc.generateSignedIntegers(10, 0, 10, false, 16, userData, ticketId);
				
				this.signedValueTester(roc, i, o, String[].class, true, ticketId);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedIntegerSequences_1(){
		// Testing generateSignedIntegerSequences(int n, int length, int min, int max)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedIntegerSequences(3, 5, 0, 10);
				
				this.signedValueTester(roc, i, o, int[][].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedIntegerSequences_2(){
		// Testing generateSignedIntegerSequences(int n, int length, int min, int max, 
		// 					boolean replacement, int base, JsonObject userData)
		// -- decimal base
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedIntegerSequences(3, 5, 0, 10, false, 10, userData);
				
				this.signedValueTester(roc, i, o, int[][].class, true);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedIntegerSequences_3(){
		// Testing generateSignedIntegerSequences(int n, int length, int min, int max, 
		// 					boolean replacement, int base, JsonObject userData) 
		// -- non-decimal base
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedIntegerSequences(3, 5, 0, 10, false, 16, userData);
				
				this.signedValueTester(roc, i, o, String[][].class, true);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedIntegerSequences_4(){
		// Testing generateSignedIntegerSequences(int n, int length, int min, int max, 
		// 					boolean replacement, int base, JsonObject userData, String ticketId)
		// -- decimal base
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				HashMap<String,Object> o = roc.generateSignedIntegerSequences(3, 5, 0, 10, false, 10, 
						userData, ticketId);
				
				this.signedValueTester(roc, i, o, int[][].class, true, ticketId);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedIntegerSequences_5(){
		// Testing generateSignedIntegerSequences(int n, int length, int min, int max, 
		// 					boolean replacement, int base, JsonObject userData, String ticketId) 
		// -- non-decimal base
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				HashMap<String,Object> o = roc.generateSignedIntegerSequences(3, 5, 0, 10, false, 16, 
						userData, ticketId);
				
				this.signedValueTester(roc, i, o, String[][].class, true, ticketId);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedIntegerSequences_6(){
		// Testing generateSignedIntegerSequences(int n, int[] length, int[] min, int[] max)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedIntegerSequences(4, LENGTH, MIN, MAX);
				
				this.signedValueTester(roc, i, o, int[][].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedIntegerSequences_7(){
		// Testing generateSignedIntegerSequences(int n, int[] length, int[] min, int[] max, 
		//					boolean[] replacement, int[] base, JsonObject userData)
		// -- decimal
		int[] decimalBase = {10, 10, 10, 10};
		int i = 1;
		
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedIntegerSequences(4, LENGTH, MIN, MAX, REPLACEMENT, decimalBase, userData);
				
				this.signedValueTester(roc, i, o, int[][].class, true);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedIntegerSequences_8(){
		// Testing generateSignedIntegerSequences(int n, int[] length, int[] min, int[] max, 
		//					boolean[] replacement, int[] base, JsonObject userData)
		// -- non-decimal
		int i = 1;
		
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedIntegerSequences(4, LENGTH, MIN, MAX, REPLACEMENT, BASE, userData);
				
				this.signedValueTester(roc, i, o, String[][].class, true);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedIntegerSequences_9(){
		// Testing generateSignedIntegerSequences(int n, int[] length, int[] min, int[] max, 
		//					boolean[] replacement, int[] base, JsonObject userData, String ticketId)
		// -- decimal
		int[] decimalBase = {10, 10, 10, 10};
		int i = 1;
		
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				HashMap<String,Object> o = roc.generateSignedIntegerSequences(4, LENGTH, MIN, 
						MAX, REPLACEMENT, decimalBase, userData, ticketId);
				
				this.signedValueTester(roc, i, o, int[][].class, true, ticketId);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedIntegerSequences_10(){
		// Testing generateSignedIntegerSequences(int n, int[] length, int[] min, int[] max, 
		//					boolean[] replacement, int[] base, JsonObject userData, String ticketId)
		// -- non-decimal
		int i = 1;
		
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				HashMap<String,Object> o = roc.generateSignedIntegerSequences(4, LENGTH, MIN, MAX, REPLACEMENT, 
						BASE, userData, ticketId);
				
				this.signedValueTester(roc, i, o, String[][].class, true, ticketId);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedDecimalFractions_1(){
		// Testing generateSignedDecimalFractions(int n, int decimalPlaces)
		int i = 1;
		
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedDecimalFractions(10, 5);
			
				this.signedValueTester(roc, i, o, double[].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}

	@Test
	public void testPositiveGenerateSignedDecimalFractions_2(){
		// Testing generateSignedDecimalFractions(int n, int decimalPlaces, boolean replacement)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedDecimalFractions(10, 5, false);
			
				this.signedValueTester(roc, i, o, double[].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedDecimalFractions_3(){
		// Testing generateSignedDecimalFractions(int n, int decimalPlaces, 
		//				boolean replacement, JsonObject userData)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedDecimalFractions(10, 5, false, userData);
				
				this.signedValueTester(roc, i, o, double[].class, true);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedDecimalFractions_4(){
		// Testing generateSignedDecimalFractions(int n, int decimalPlaces, 
		//				boolean replacement, JsonObject userData, String ticketId)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				HashMap<String,Object> o = roc.generateSignedDecimalFractions(10, 5, false, 
						userData, ticketId);
				
				this.signedValueTester(roc, i, o, double[].class, true, ticketId);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}

	@Test
	public void testPositiveGenerateSignedGaussians_1(){
		// Testing generateSignedGaussians(int n, double mean, double standardDeviation, int significantDigits)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String, Object> o = roc.generateSignedGaussians(10, 3.41d, 2.1d, 4);
				
				this.signedValueTester(roc, i, o, double[].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedGaussians_2(){
		// Testing generateSignedGaussians(int n, double mean, double standardDeviation, 
		// 				int significantDigits, JsonObject userData)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String, Object> o = roc.generateSignedGaussians(10, 3.41d, 2.1d, 4, userData);
				
				this.signedValueTester(roc, i, o, double[].class, true);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedGaussians_3(){
		// Testing generateSignedGaussians(int n, double mean, double standardDeviation, 
		// 				int significantDigits, JsonObject userData, String ticketId)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				HashMap<String, Object> o = roc.generateSignedGaussians(10, 3.41d, 2.1d, 4, 
						userData, ticketId);
				
				this.signedValueTester(roc, i, o, double[].class, true, ticketId);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}

	@Test
	public void testPositiveGenerateSignedStrings_1(){
		// Testing generateSignedStrings(int n, int length, String characters)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedStrings(10, 5, "abcd");
				
				this.signedValueTester(roc, i, o, String[].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}		
	}

	@Test
	public void testPositiveGenerateSignedStrings_2(){
		// Testing generateSignedStrings(int n, int length, String characters, boolean replacement)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedStrings(10, 5, "abcd", false);
		
				this.signedValueTester(roc, i, o, String[].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	public void testPositiveGenerateSignedStrings_3(){
		// Testing generateSignedStrings(int n, int length, String characters, 
		//				boolean replacement, JsonObject userData)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedStrings(10, 5, "abcd", false, userData);
		
				this.signedValueTester(roc, i, o, String[].class, true);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedStrings_4(){
		// Testing generateSignedStrings(int n, int length, String characters, 
		//				boolean replacement, JsonObject userData, String ticketId)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				HashMap<String,Object> o = roc.generateSignedStrings(10, 5, "abcd", false, 
						userData, ticketId);
		
				this.signedValueTester(roc, i, o, String[].class, true, ticketId);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedUUIDs_1(){
		// Testing generateSignedUUIDs(int n)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedUUIDs(10);
				
				this.signedValueTester(roc, i, o, UUID[].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedUUIDs_2(){
		// Testing generateSignedUUIDs(int n, JsonObject userData)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedUUIDs(10, userData);
				
				this.signedValueTester(roc, i, o, UUID[].class, true);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedUUIDs_3(){
		// Testing generateSignedUUIDs(int n, JsonObject userData, String ticketId)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				HashMap<String,Object> o = roc.generateSignedUUIDs(10, userData, ticketId);
				
				this.signedValueTester(roc, i, o, UUID[].class, true, ticketId);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedBlobs_1(){
		// Testing generateSignedBlobs(int n, int size)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedBlobs(10, 16);
				
				this.signedValueTester(roc, i, o, String[].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}

	@Test
	public void testPositiveGenerateSignedBlobs_2(){
		// Testing generateSignedBlobs(int n, int size, String format)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedBlobs(10, 16, RandomOrgClient.BLOB_FORMAT_HEX);
				
				this.signedValueTester(roc, i, o, String[].class);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedBlobs_3(){
		// Testing generateSignedBlobs(int n, int size, String format, JsonObject userData)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String,Object> o = roc.generateSignedBlobs(10, 16, RandomOrgClient.BLOB_FORMAT_HEX, userData);
				
				this.signedValueTester(roc, i, o, String[].class, true);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testPositiveGenerateSignedBlobs_4(){
		// Testing generateSignedBlobs(int n, int size, String format, JsonObject userData, 
		//				String ticketId)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				String ticketId = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				HashMap<String,Object> o = roc.generateSignedBlobs(10, 16, RandomOrgClient.BLOB_FORMAT_HEX, 
						userData, ticketId);
				
				this.signedValueTester(roc, i, o, String[].class, true, ticketId);
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	// Test additional functions
	
	@Test
	public void testGetResult(){
		// Testing getResult(int serialNumber)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				HashMap<String, Object> o = roc.generateSignedIntegers(10, 0, 10);			
				HashMap<String, Object> o2 = roc.getResult(((JsonObject)o.get("random")).get("serialNumber").getAsInt());
				
				JsonObject data = ((JsonObject)o2.get("random")).getAsJsonObject();
				int[] response = new Gson().fromJson(data.get("data"), int[].class);
				
				collector.checkThat((int[]) o.get("data"), equalTo(response));	
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testCreateTickets(){
		// Testing createTickets(int n, boolean showResult)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				JsonObject[] result = roc.createTickets(1, true);				
				collector.checkThat(result[0], notNullValue());
				
				result = roc.createTickets(1, false);				
				collector.checkThat(result[0], notNullValue());
				
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}
	
	@Test
	public void testListTickets(){
		// Testing listTickets(String type) for each type
		int i = 1;
		String[] types = {"singleton", "head", "tail"};
		JsonObject[] tickets = new JsonObject[0];
		for (RandomOrgClient roc : rocs) {
			for (String t : types) {
				try {
					tickets = roc.listTickets(t);
					if (tickets != null) {
						JsonObject ticket = tickets[0];
						if (t.equals("singleton")) {
							collector.checkThat(ticket.get("nextTicketId").isJsonNull(), equalTo(true));
							collector.checkThat(ticket.get("previousTicketId").isJsonNull(), equalTo(true));
						} else if (t.equals("head")) {
							collector.checkThat(ticket.get("nextTicketId").isJsonNull(), equalTo(false));
							collector.checkThat(ticket.get("previousTicketId").isJsonNull(), equalTo(true));
						} else if (t.equals("tail")) {
							collector.checkThat(ticket.get("nextTicketId").isJsonNull(), equalTo(true));
							collector.checkThat(ticket.get("previousTicketId").isJsonNull(), equalTo(false));
						} else { 
							collector.addError(new Error("Invalid ticket type. "));
						}
					}
				} catch (Exception e) {
					collector.addError(new Error(errorMessage(i, e, true)));
				}
			}
			i++;
		}
	}
	
	@Test
	public void testGetTicket(){
		// Testing getTicket(String ticketId)
		int i = 1;
		for (RandomOrgClient roc : rocs) {
			try {
				String ticket = roc.createTickets(1, true)[0].get("ticketId").getAsString();
				String hiddenTicket = roc.createTickets(1, false)[0].get("ticketId").getAsString();
				
				// unused ticket with showResult == true
				HashMap<String, Object> o2 = roc.getTicket(ticket);
				collector.checkThat(((JsonObject) o2.get("result")).get("result").isJsonNull(), equalTo(true));
				
				// used ticket with showResult == true
				HashMap<String, Object> o = roc.generateSignedIntegers(3, 0, 10, false, 10, null, ticket);
				o2 = roc.getTicket(ticket);
				collector.checkThat(o.get("data"), equalTo(o2.get("data")));
				
				// unused ticket with showResult == false
				o2 = roc.getTicket(hiddenTicket);
				collector.checkThat(((JsonObject) o2.get("result")).get("usedTime").isJsonNull(), equalTo(true));
				
				// used ticket with showResult == false
				roc.generateSignedIntegers(3, 0, 10, false, 10, null, hiddenTicket);
				o2 = roc.getTicket(hiddenTicket);
				collector.checkThat(((JsonObject) o2.get("result")).has("result"), equalTo(false));
				collector.checkThat(((JsonObject) o2.get("result")).get("usedTime").isJsonNull(), equalTo(false));
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
			i++;
		}
	}

	// Test Functions (Cache)
	
	@Test
	public void testIntegerCache_1(){
		// Testing createIntegerCache(int n, int min, int max)
		RandomOrgCache<int[]> c = rocs[0].createIntegerCache(5, 0, 10);
		c.stop();

		try {
			c.get();
			collector.addError(new Error("should have thrown NoSuchElementException"));
		} catch (NoSuchElementException e) {}
		
		collector.checkThat(c.isPaused(), equalTo(true));
		c.resume();
		
		int[] got = null;
		
		// Testing RandomOrgCache function get()
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			}
		}
		
		collector.checkThat(got, notNullValue());
		
		got = null;
		
		// Testing RandomOrgCache info functions
		collector.checkThat(c.getCachedValues(), greaterThan(0));
		collector.checkThat((int)c.getUsedBits(), greaterThan(0));
		collector.checkThat((int)c.getUsedRequests(), greaterThan(0));
		
		// Testing RandomOrgCache function getOrWait()
		while (got == null) {
			try {
				got = c.getOrWait();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			} catch (Exception e) {
				collector.addError(new Error("should have returned a value"));
			}
		}
	}
	
	@Test
	public void testIntegerCache_2(){
		// Testing createIntegerCache(int n, int min, int max, boolean replacement, 
		//				int base, int cacheSize)
		RandomOrgCache<String[]> c = rocs[0].createIntegerCache(5, 50, 100, false, 16, 5);
		c.stop();

		try {
			c.get();
			collector.addError(new Error("should have thrown NoSuchElementException"));
		} catch (NoSuchElementException e) {}
		
		collector.checkThat(c.isPaused(), equalTo(true));
		c.resume();
		
		String[] got = null;
		
		// Testing RandomOrgCache function get()
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			}
		}
		
		collector.checkThat(got, notNullValue());
	}
	
	@Test
	public void testIntegerSequenceCache_1(){
		// Testing createIntegerSequenceCache(int n, int length int min, int max)
		RandomOrgCache<int[][]> c = rocs[0].createIntegerSequenceCache(2, 5, 0, 10);
		c.stop();

		try {
			c.get();
			collector.addError(new Error("should have thrown NoSuchElementException"));
		} catch (NoSuchElementException e) {}
		
		c.resume();
		
		int[][] got = null;
		
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			}
		}
		
		collector.checkThat(got, notNullValue());
	}
	
	@Test
	public void testIntegerSequenceCache_2(){
		// Testing createIntegerSequenceCache(int n, int length int min, int max, 
		//				boolean replacement, int base, int cacheSize)
		RandomOrgCache<String[][]> c = rocs[0].createIntegerSequenceCache(2, 5, 0, 10, false, 16, 3);
		c.stop();

		try {
			c.get();
			collector.addError(new Error("should have thrown NoSuchElementException"));
		} catch (NoSuchElementException e) {}
		
		c.resume();
		
		String[][] got = null;
		
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			}
		}
		
		collector.checkThat(got, notNullValue());
	}

	@Test
	public void testIntegerSequenceCache_3(){
		// Testing createIntegerSequenceCache(int n, int[] length, int[] min, int[] max)
		RandomOrgCache<int[][]> c = rocs[0].createIntegerSequenceCache(4, LENGTH, MIN, MAX);
		c.stop();

		try {
			c.get();
			collector.addError(new Error("should have thrown NoSuchElementException"));
		} catch (NoSuchElementException e) {}
		
		c.resume();
		
		int[][] got = null;
		
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			}
		}
		
		collector.checkThat(got, notNullValue());
	}
	
	@Test
	public void testIntegerSequenceCache_4(){
		// Testing createIntegerSequenceCache(int n, int[] length, int[] min, int[] max, 
		// 				boolean[] replacement, int[] base, int cacheSize)
		boolean[] replacement = {true, true, true, true};
		RandomOrgCache<String[][]> c = rocs[0].createIntegerSequenceCache(4, LENGTH, MIN, MAX, replacement, BASE, 10);
		c.stop();

		try {
			c.get();
			collector.addError(new Error("should have thrown NoSuchElementException"));
		} catch (NoSuchElementException e) {}
		
		c.resume();
		
		String[][] got = null;
		
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			}
		}
		
		for (int i = 0; i < got.length; i++) {
			collector.checkThat(got[i].length == LENGTH[i], equalTo(true));
		}
		
		collector.checkThat(got, notNullValue());
	}
	
	@Test
	public void testDecimalFractionCache(){
		// Testing createDecimalFractionCache(int n, int decimalPlaces)
		RandomOrgCache<double[]> c = rocs[1].createDecimalFractionCache(1, 5);
		c.stop();

		try {
			c.get();
			collector.addError(new Error("should have thrown NoSuchElementException"));
		} catch (NoSuchElementException e) {}
		
		c.resume();
		
		double[] got = null;
		
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			}
		}
		
		collector.checkThat(got, notNullValue());
	}

	@Test
	public void testGaussianCache(){
		// Testing createGaussianCache(int n, double mean, double standardDeviation, 
		// 				int significantDigits) 
		RandomOrgCache<double[]> c = rocs[0].createGaussianCache(10, 3.41d, 2.1d, 4);
		c.stop();

		try {
			c.get();
			collector.addError(new Error("should have thrown NoSuchElementException"));
		} catch (NoSuchElementException e) {}
		
		c.resume();
		
		double[] got = null;
		
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			}
		}
		
		collector.checkThat(got, notNullValue());
	}

	@Test
	public void testStringCache(){
		// Testing createStringCache(int n, int length, String characters)
		RandomOrgCache<String[]> c = rocs[1].createStringCache(5, 5, "abcds");
		c.stop();

		try {
			c.get();
			collector.addError(new Error("should have thrown NoSuchElementException"));
		} catch (NoSuchElementException e) {}
		
		c.resume();
		
		String[] got = null;
		
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			}
		}
		
		collector.checkThat(got, notNullValue());
	}

	@Test
	public void testUUIDCache(){
		// Testing createUUIDCache(int n)
		RandomOrgCache<UUID[]> c = rocs[0].createUUIDCache(5);
		c.stop();

		try {
			c.get();
			collector.addError(new Error("should have thrown NoSuchElementException"));
		} catch (NoSuchElementException e) {}
		
		c.resume();
		
		UUID[] got = null;
		
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			}
		}
		
		collector.checkThat(got, notNullValue());
	}
	
	@Test
	public void testBlobCache(){
		// Testing createBlobCache(int n, int size)
		RandomOrgCache<String[]> c = rocs[1].createBlobCache(5, 8);
		c.stop();

		try {
			c.get();
			collector.addError(new Error("should have thrown NoSuchElementException"));
		} catch (NoSuchElementException e) {}
		
		c.resume();
		
		String[] got = null;
		
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					collector.addError(new Error("shouldn't have been interrupted!"));
				}
			}
		}
		
		collector.checkThat(got, notNullValue());
	}
	
	// Error message and helper functions 
	
	private String client(int i) {
		return "RandomOrgClient roc" + i + " ";
	}
	
	private String errorMessage() {
		return errorMessage(-1);
	}
	
	private String errorMessage(int i) {
		return errorMessage(i, null);
	}
	
	private String errorMessage(Exception e) {
		return errorMessage(-1, e);
	}
	
	private String errorMessage(int i, Exception e) {
		return errorMessage(i, e, false);
	}	
	
	private String errorMessage(int i, Exception e, boolean networking) {
		if (networking) {
			return client(i) + " - Networking error: " + e.getClass().getName() 
					+ ":" + e.getMessage();
		} else {
			String s = "";
			if (i > -1) {
				s += client(i);
			}
			s += "should have thrown RandomOrgRANDOMORGError";
			if (e != null) {
				s += ", instead threw "	+ e.getClass().getName();
			}
			return s;
		}
	}
	
	/**
	 *  Helper function for testing methods returning signed values
	 *  
	 * @param roc RandomOrgClient instance being used
	 * @param i index of RandomOrgClient instance in rocs
	 * @param o HashMap<String, Object> returned from call to a RandomOrgClient method 
	 * 		  returning signed values
	 * @param cls Class of the data expected to be returned from the call to the 
	 * 		  RandomOrgClient method
	 * 
	 */
	private void signedValueTester(RandomOrgClient roc, int i, HashMap<String, Object> o, Class<?> cls) {
		this.signedValueTester(roc, i, o, cls, false);
	}
	
	/**
	 *  Helper function for testing methods returning signed values
	 *  
	 * @param roc RandomOrgClient instance being used
	 * @param i index of RandomOrgClient instance in rocs
	 * @param o HashMap<String, Object> returned from call to a RandomOrgClient method 
	 * 		  returning signed values
	 * @param cls Class of the data expected to be returned from the call to the 
	 * 		  RandomOrgClient method
	 * @param hasUserData boolean stating whether the request included userData (true) or not (false)
	 * 
	 */
	private void signedValueTester(RandomOrgClient roc, int i, HashMap<String, Object> o, Class<?> cls, boolean hasUserData) {
		this.signedValueTester(roc, i, o, cls, hasUserData, null);
	}
	
	/**
	 *  Helper function for testing methods returning signed values
	 *  
	 * @param roc RandomOrgClient instance being used
	 * @param i index of RandomOrgClient instance in rocs
	 * @param o HashMap<String, Object> returned from call to a RandomOrgClient method 
	 * 		  returning signed values
	 * @param cls Class of the data expected to be returned from the call to the 
	 * 		  RandomOrgClient method
	 * @param hasUserData boolean stating whether the request included userData (true) or not (false)
	 * @param ticketId String returned from a call to {@link org.random.api.RandomOrgClient#createTickets(int n, boolean showResult) 
	 * 		  createTickets}. {@code null} if none is used.
	 * 
	 */
	private void signedValueTester(RandomOrgClient roc, int i, HashMap<String, Object> o, Class<?> cls, boolean hasUserData, String ticketId) {
		collector.checkThat(o, notNullValue());
		
		collector.checkThat(o.containsKey("data"), equalTo(true));
		collector.checkThat(o.containsKey("random"), equalTo(true));
		collector.checkThat(o.containsKey("signature"), equalTo(true));
		
		collector.checkThat(o.get("data").getClass(), equalTo(cls));
		collector.checkThat(o.get("random").getClass(), equalTo(JsonObject.class));
		collector.checkThat(o.get("signature").getClass(), equalTo(String.class));
		
		if (hasUserData) {
			collector.checkThat(((JsonObject) o.get("random")).get("userData"), equalTo(userData));
		}
		
		if (ticketId != null) {
			try {
				HashMap<String, Object> o2 = roc.getTicket(ticketId);
				collector.checkThat(o.get("data"), equalTo(o2.get("data")));
			} catch (Exception e) {
				collector.addError(new Error(errorMessage(i, e, true)));
			}
		}
		
		try {
			collector.checkThat(roc.verifySignature((JsonObject)o.get("random"), 
					(String)o.get("signature")), equalTo(true));
		} catch (Exception e) {
			collector.addError(new Error(errorMessage(i, e, true)));
		}
	}
}
