package org.random.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.random.util.RandomOrgRandom;

/** A set of tests for RandomOrgRandom.java
 **/
public class RandomOrgRandomBasicTest {
	
	private static RandomOrgRandom random;

	private static final String API_KEY_1 = "YOUR_API_KEY_HERE";
	
	@BeforeClass
	public static void testSetup() {
		random = new RandomOrgRandom(API_KEY_1);
	}

	@Test
	public void testIntegerRange(){
		try {
			int i = random.nextInt(10);
			assertNotNull(i);
			assertTrue(i >= 0);
			assertTrue(i < 10);
		} catch (Exception e) {
			Assert.fail("Networking error: " + e.getClass().getName() + ":" + e.getMessage());
		}
	}
}
