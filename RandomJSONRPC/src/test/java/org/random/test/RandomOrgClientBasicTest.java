package org.random.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.random.api.RandomOrgCache;
import org.random.api.RandomOrgClient;
import org.random.api.exception.RandomOrgRANDOMORGError;

import com.google.gson.JsonObject;

/** A set of tests for RandomOrgClient.java
 ** @author Anders Haahr
 **/
public class RandomOrgClientBasicTest {
	
	private static RandomOrgClient roc, roc2;
	
	private static final String API_KEY_1 = "YOUR_API_KEY_HERE";
	private static final String API_KEY_2 = "YOUR_API_KEY_HERE";
	
	private static final int BIT_QUOTA = 1000000;
	
	@BeforeClass
	public static void testSetup() {
		roc = RandomOrgClient.getRandomOrgClient(API_KEY_1, 3000, 120000, false);
		roc2 = RandomOrgClient.getRandomOrgClient(API_KEY_2);
	}
	
	@Test
	public void testInfo() {
		try {
			assertTrue(roc.getBitsLeft() >= 0);
			assertTrue(roc.getRequestsLeft() >= 0);
			
			assertTrue(roc2.getBitsLeft() >= 0);
			assertTrue(roc2.getRequestsLeft() >= 0);

		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}
	
	@Test
	public void testAPIKeyDuplication() {
		RandomOrgClient dupe = RandomOrgClient.getRandomOrgClient(API_KEY_1);
		
		assertTrue(!roc.equals(roc2));
		assertTrue(roc.equals(dupe));
	}
	
	@Test
	public void testPositiveGetBitsLeft_1(){
		try {
			int bitsLeft = roc.getBitsLeft();
			assertTrue(0 <= bitsLeft && bitsLeft <= BIT_QUOTA);
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGetBitsLeft_2(){
		try {
			int bitsLeft = roc2.getBitsLeft();
			assertTrue(0 <= bitsLeft && bitsLeft <= BIT_QUOTA);
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	// Test Errors
	
	@Test
	public void testNegativeErrorMessage202(){
		try{
			roc.generateIntegers(100000, 0, 10);
			Assert.fail("should have thrown RandomOrgRANDOMORGError");
		} catch(RandomOrgRANDOMORGError e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			Assert.fail("should have thrown RandomOrgRANDOMORGError, instead threw " + e.getClass().getName());
		}
		try{
			roc2.generateIntegers(100000, 0, 10);
			Assert.fail("should have thrown RandomOrgRANDOMORGError");
		} catch(RandomOrgRANDOMORGError e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			Assert.fail("should have thrown RandomOrgRANDOMORGError, instead threw " + e.getClass().getName());
		}
	}

	@Test
	public void testNegativeErrorMessage203(){
		try{
			roc.generateIntegers(10, 0, 1000000001);
			Assert.fail("should have thrown RandomOrgRANDOMORGError");
		} catch(RandomOrgRANDOMORGError e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			Assert.fail("should have thrown RandomOrgRANDOMORGError, instead threw " + e.getClass().getName());
		}
		try{
			roc2.generateIntegers(10, 0, 1000000001);
			Assert.fail("should have thrown RandomOrgRANDOMORGError");
		} catch(RandomOrgRANDOMORGError e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			Assert.fail("should have thrown RandomOrgRANDOMORGError, instead threw " + e.getClass().getName());
		}
	}

	@Test
	public void testNegativeErrorMessage300(){
		try{
			roc.generateIntegers(10, 10, 0);
			Assert.fail("should have thrown RandomOrgRANDOMORGError");
		} catch(RandomOrgRANDOMORGError e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			Assert.fail("should have thrown RandomOrgRANDOMORGError, instead threw " + e.getClass().getName());
		}
		try{
			roc2.generateIntegers(10, 10, 0);
			Assert.fail("should have thrown RandomOrgRANDOMORGError");
		} catch(RandomOrgRANDOMORGError e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			Assert.fail("should have thrown RandomOrgRANDOMORGError, instead threw " + e.getClass().getName());
		}
	}

	@Test
	public void testNegativeErrorMessage301(){
		try{
			roc.generateIntegers(20, 0, 10, false);
			Assert.fail("should have thrown RandomOrgRANDOMORGError");
		} catch(RandomOrgRANDOMORGError e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			Assert.fail("should have thrown RandomOrgRANDOMORGError, instead threw " + e.getClass().getName());
		}
		try{
			roc2.generateIntegers(20, 0, 10, false);
			Assert.fail("should have thrown RandomOrgRANDOMORGError");
		} catch(RandomOrgRANDOMORGError e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			Assert.fail("should have thrown RandomOrgRANDOMORGError, instead threw " + e.getClass().getName());
		}
	}

	@Test
	public void testNegativeErrorMessage400(){
		try{
			RandomOrgClient rpc2 = RandomOrgClient.getRandomOrgClient("ffffffff-ffff-ffff-ffff-ffffffffffff");
			rpc2.generateIntegers(1, 0, 1);
			Assert.fail("should have thrown RandomOrgRANDOMORGError");
		} catch(RandomOrgRANDOMORGError e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			Assert.fail("should have thrown RandomOrgRANDOMORGError, instead threw " + e.getClass().getName());
		}
	}
	
	// Test Functions

	@Test
	public void testPositiveGenerateInteger_1(){
		try {
			assertNotNull(roc.generateIntegers(10, 0, 10));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			assertNotNull(roc2.generateIntegers(10, 0, 10));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateInteger_2(){
		try {
			assertNotNull(roc.generateIntegers(10, 0, 10, false));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			assertNotNull(roc2.generateIntegers(10, 0, 10, false));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateDecimalFractions_1(){
		try {
			assertNotNull(roc.generateDecimalFractions(10, 5));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			assertNotNull(roc2.generateDecimalFractions(10, 5));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateDecimalFractions_2(){
		try {
			assertNotNull(roc.generateDecimalFractions(10, 5, false));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			assertNotNull(roc2.generateDecimalFractions(10, 5, false));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateGaussians(){
		try {
			assertNotNull(roc.generateGaussians(10, 3.41d, 2.1d, 4));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			assertNotNull(roc2.generateGaussians(10, 3.41d, 2.1d, 4));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateStrings_1(){
		try {
			assertNotNull(roc.generateStrings(10, 5, "abcd"));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			assertNotNull(roc2.generateStrings(10, 5, "abcd"));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateStrings_2(){
		try {
			assertNotNull(roc.generateStrings(10, 5, "abcd", false));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			assertNotNull(roc2.generateStrings(10, 5, "abcd", false));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateUUIDs(){
		try {
			assertNotNull(roc.generateUUIDs(10));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			assertNotNull(roc2.generateUUIDs(10));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateBlobs_1(){
		try {
			assertNotNull(roc.generateBlobs(10, 16));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			assertNotNull(roc2.generateBlobs(10, 16));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateBlobs_2(){
		try {
			assertNotNull(roc.generateBlobs(10, 16, RandomOrgClient.BLOB_FORMAT_HEX));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			assertNotNull(roc2.generateBlobs(10, 16, RandomOrgClient.BLOB_FORMAT_HEX));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	// Test Functions (Signed)
	
	@Test
	public void testPositiveGenerateSignedInteger_1(){
		try {
			HashMap<String,Object> o = roc.generateSignedIntegers(10, 0, 10);
			
			assertNotNull(o);
			
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
			
			assertTrue(o.get("data").getClass().equals(int[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
			
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			HashMap<String,Object> o = roc2.generateSignedIntegers(10, 0, 10);
			
			assertNotNull(o);
			
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
			
			assertTrue(o.get("data").getClass().equals(int[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
			
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateSignedInteger_2(){
		try {
			HashMap<String,Object> o = roc.generateSignedIntegers(10, 0, 10, false);
			
			assertNotNull(o);
			
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
			
			assertTrue(o.get("data").getClass().equals(int[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
			
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			HashMap<String,Object> o = roc2.generateSignedIntegers(10, 0, 10, false);
			
			assertNotNull(o);
			
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
			
			assertTrue(o.get("data").getClass().equals(int[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
			
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateSignedDecimalFractions_1(){
		try {
			HashMap<String,Object> o = roc.generateSignedDecimalFractions(10, 5);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(double[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			HashMap<String,Object> o = roc2.generateSignedDecimalFractions(10, 5);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(double[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateSignedDecimalFractions_2(){
		try {
			HashMap<String,Object> o = roc.generateSignedDecimalFractions(10, 5, false);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(double[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			HashMap<String,Object> o = roc2.generateSignedDecimalFractions(10, 5, false);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(double[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateSignedGaussians(){
		try {
			HashMap<String,Object> o = roc.generateSignedGaussians(10, 3.41d, 2.1d, 4);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(double[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			HashMap<String,Object> o = roc2.generateSignedGaussians(10, 3.41d, 2.1d, 4);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(double[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateSignedStrings_1(){
		try {
			HashMap<String,Object> o = roc.generateSignedStrings(10, 5, "abcd");
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(String[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			HashMap<String,Object> o = roc2.generateSignedStrings(10, 5, "abcd");
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(String[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateSignedStrings_2(){
		try {
			HashMap<String,Object> o = roc.generateSignedStrings(10, 5, "abcd", false);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(String[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			HashMap<String,Object> o = roc2.generateSignedStrings(10, 5, "abcd", false);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(String[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateSignedUUIDs(){
		try {
			HashMap<String,Object> o = roc.generateSignedUUIDs(10);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(UUID[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			HashMap<String,Object> o = roc2.generateSignedUUIDs(10);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(UUID[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateSignedBlobs_1(){
		try {
			HashMap<String,Object> o = roc.generateSignedBlobs(10, 16);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(String[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			HashMap<String,Object> o = roc2.generateSignedBlobs(10, 16);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(String[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	@Test
	public void testPositiveGenerateSignedBlobs_2(){
		try {
			HashMap<String,Object> o = roc.generateSignedBlobs(10, 16, RandomOrgClient.BLOB_FORMAT_HEX);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(String[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
		try {
			HashMap<String,Object> o = roc2.generateSignedBlobs(10, 16, RandomOrgClient.BLOB_FORMAT_HEX);
		
			assertNotNull(o);
		
			assertTrue(o.containsKey("data"));
			assertTrue(o.containsKey("random"));
			assertTrue(o.containsKey("signature"));
		
			assertTrue(o.get("data").getClass().equals(String[].class));
			assertTrue(o.get("random").getClass().equals(JsonObject.class));			
			assertTrue(o.get("signature").getClass().equals(String.class));
		
			assertTrue(roc.verifySignature((JsonObject)o.get("random"), (String)o.get("signature")));
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}

	// Test Functions (Cache)
	
	@Test
	public void testIntegerCache(){
		RandomOrgCache<int[]> c = roc.createIntegerCache(5, 0, 10);
		c.stop();

		try {
			c.get();
			Assert.fail("should have thrown NoSuchElementException");
		} catch (NoSuchElementException e) {}
		
		c.resume();
		
		int[] got = null;
		
		while (got == null) {
			try {
				got = c.get();
			} catch (NoSuchElementException e) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e1) {
					Assert.fail("shouldn't have been interrupted!");
				}
			}
		}
		
		assertNotNull(got);
	}

	@Test
	public void testDecimalFractionCache(){
		RandomOrgCache<double[]> c = roc2.createDecimalFractionCache(1, 5);
		c.stop();

		try {
			c.get();
			Assert.fail("should have thrown NoSuchElementException");
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
					Assert.fail("shouldn't have been interrupted!");
				}
			}
		}
		
		assertNotNull(got);
	}

	@Test
	public void testGaussianCache(){
		RandomOrgCache<double[]> c = roc.createGaussianCache(10, 3.41d, 2.1d, 4);
		c.stop();

		try {
			c.get();
			Assert.fail("should have thrown NoSuchElementException");
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
					Assert.fail("shouldn't have been interrupted!");
				}
			}
		}
		
		assertNotNull(got);
	}

	@Test
	public void testStringCache(){
		RandomOrgCache<String[]> c = roc2.createStringCache(5, 5, "abcds");
		c.stop();

		try {
			c.get();
			Assert.fail("should have thrown NoSuchElementException");
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
					Assert.fail("shouldn't have been interrupted!");
				}
			}
		}
		
		assertNotNull(got);
	}

	@Test
	public void testUUIDCache(){
		RandomOrgCache<UUID[]> c = roc.createUUIDCache(5);
		c.stop();

		try {
			c.get();
			Assert.fail("should have thrown NoSuchElementException");
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
					Assert.fail("shouldn't have been interrupted!");
				}
			}
		}
		
		assertNotNull(got);
	}
	
	@Test
	public void testBlobCache(){
		RandomOrgCache<String[]> c = roc2.createBlobCache(5, 8);
		c.stop();

		try {
			c.get();
			Assert.fail("should have thrown NoSuchElementException");
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
					Assert.fail("shouldn't have been interrupted!");
				}
			}
		}
		
		assertNotNull(got);
	}
}
