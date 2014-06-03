package org.random.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.Test;
import org.random.api.RandomJSONRPC;

/**
 * A set of tests for RandomJSONRPC.java
 * @author Anders Haahr
 *
 */
public class RandomJSONRPCBasicTest{

	private static RandomJSONRPC rpc;
	private static final String API_KEY = "430dbec3-ef2e-4a97-8c4e-360a35cd70c0";
	private static final int BIT_QUOTA = 1000000;

	@BeforeClass
	public static void testSetup() {
		rpc = new RandomJSONRPC(API_KEY);
	}

	@Test
	public void testPositiveGenerateInteger_1(){		
		assertNotNull(rpc.generateIntegers(10, 0, 10));
	}

	@Test
	public void testPositiveGenerateInteger_2(){
		assertNotNull(rpc.generateIntegers(10, 0, 10, false));
	}

	@Test
	public void testPositiveGenerateDecimalFractions_1(){
		assertNotNull(rpc.generateDecimalFractions(10, 5));
	}

	@Test
	public void testPositiveGenerateDecimalFractions_2(){
		assertNotNull(rpc.generateDecimalFractions(10, 5, false));
	}

	@Test
	public void testPositiveGenerateGaussians(){
		assertNotNull(rpc.generateGaussians(10, 3.41d, 2.1d, 4));
	}

	@Test
	public void testPositiveGenerateStrings_1(){
		assertNotNull(rpc.generateStrings(10, 5, "abcd"));
	}

	@Test
	public void testPositiveGenerateStrings_2(){
		assertNotNull(rpc.generateStrings(10, 5, "abcd", false));
	}

	@Test
	public void testPositiveGenerateUUIDs(){
		assertNotNull(rpc.generateUUIDs(10));
	}

	@Test
	public void testPositiveGetBitsLeft_1(){
		rpc.getBitsLeft();
	}

	@Test
	public void testPositiveGetBitsLeft_2(){
		RandomJSONRPC rpc2 = new RandomJSONRPC(API_KEY);		
		int bitsLeft = rpc2.getBitsLeft();
		System.out.println(bitsLeft);
		assertTrue(0 <= bitsLeft && bitsLeft <= BIT_QUOTA);
	}

	@Test
	public void testPositiveMaxBlockingTime(){
		RandomJSONRPC rpc2 = new RandomJSONRPC(API_KEY, 1000);
		rpc2.generateIntegers(1, 0, 1);
		rpc2.generateIntegers(1, 0, 1);
	}

	@Test
	public void testNegativeErrorMessage202(){		
		try{
			rpc.generateIntegers(100000, 0, 10);
			Assert.fail("should have thrown illegalArgumentException");
		}
		catch(IllegalArgumentException e){
			System.out.println(e.getMessage());
		}		
	}

	@Test
	public void testNegativeErrorMessage203(){
		try{
			rpc.generateIntegers(10, 0, 1000000001);
			Assert.fail("Should have thrown IllegalArgumentException");
		}
		catch(IllegalArgumentException e){
			System.out.println(e.getMessage());
		}
	}

	@Test
	public void testNegativeErrorMessage300(){
		try{
			rpc.generateIntegers(10, 10, 0);
			Assert.fail("Should have thrown IllegalArgumentException");
		}
		catch(IllegalArgumentException e){
			System.out.println(e.getMessage());
		}
	}

	@Test
	public void testNegativeErrorMessage301(){
		try{
			rpc.generateIntegers(20, 0, 10, false);
			Assert.fail("Should have thrown IllegalArgumentException");
		}
		catch(IllegalArgumentException e){
			System.out.println(e.getMessage());
		}
	}

	@Test
	public void testNegativeErrorMessage400(){
		try{
			RandomJSONRPC rpc2 = new RandomJSONRPC("ffffffff-ffff-ffff-ffff-ffffffffffff");
			rpc2.generateIntegers(1, 0, 1);
			Assert.fail("Should have thrown IllegalArgumentException");
		}
		catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
		}
	}

	@Test
	public void testNegativeMaxBlockingTime(){
		RandomJSONRPC rpc2 = new RandomJSONRPC(API_KEY, 10);
		rpc2.generateIntegers(1, 0, 1);
		try{
			rpc2.generateIntegers(1, 0, 1);
			Assert.fail("Should have thrown RutimeException");
		}
		catch (RuntimeException e) {
			System.out.println(e.getMessage());
		}
	}	

}
