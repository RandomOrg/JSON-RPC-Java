package org.random.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.random.api.RandomOrgCache;
import org.random.api.RandomOrgClient;
import org.random.api.exception.RandomOrgBadHTTPResponseException;
import org.random.api.exception.RandomOrgInsufficientBitsError;
import org.random.api.exception.RandomOrgInsufficientRequestsError;
import org.random.api.exception.RandomOrgJSONRPCError;
import org.random.api.exception.RandomOrgKeyNotRunningError;
import org.random.api.exception.RandomOrgRANDOMORGError;
import org.random.api.exception.RandomOrgSendTimeoutException;

/** A partial implementation of {@link Random} using RANDOM.ORG for true RNG.
 ** <p>
 ** This implements a true random number generator based on the RANDOM.ORG service.
 ** For more information see <code>https://api.random.org/</code>.
 ** <p>
 ** This class offers cached number retrieving from the random.org server over the official API.
 ** The cached numbers can be accessed bit wise by the typical public methods of the random class.
 ** If more bits are requested than currently cached the methods will block until more data is 
 ** retrieved from the server.
 ** <p>
 ** To use this class a official API key from <code>https://api.random.org/api-keys</code> is required.
 ** <p>
 ** To optimize the cache for your needs you can setup the cache size on creation of an instance. By default
 ** it will cache 16 16-bit values, a maximum of 256 bits.
 ** 
 ** @see https://random.org/
 ** @see https://api.random.org/
 ** 
 ** @author Adam Wagenhäuser
 **/
public class RandomOrgRandom extends Random {

	/**
	 **/
	private static final long serialVersionUID = 4785372106424073371L;

	/** Default cache size is 16.
	 **/
	protected static final int DEFAULT_CACHE_SIZE = 16;
	
	/** Cache size - number of 16-bit values to cache.
	 ** <p>
	 ** Total cache size is <code>16 * cacheSize</code> bits.
	 ** <p>
	 ** Cache size minimum is 2, which will be automatically enforced.
	 ** Default size is 16.
	 **/
	protected int cacheSize;
	
	/** The local 16-bit value RANDOM.ORG cache.
	 ** <p>
	 ** This stores up to {@link #cacheSize} values.
	 **/
	protected RandomOrgCache<int[]> cache;
	
	/** The RANDOM.ORG client used for the cache.
	 ** 
	 ** @see #cache 
	 **/
	protected RandomOrgClient client;
	
	/** Left to right bit buffer for getting less than 16 bits.
	 **
	 ** @see #bitBufferLength
	 **/
	protected int bitBuffer = 0;
	
	/** Number of bits currently stored in the <code>bitBuffer</code>.
	 ** 
	 ** @see #bitBuffer
	 **/
	protected int bitBufferLength = 0;
	
	private static final Logger LOGGER = Logger.getLogger(RandomOrgClient.class.getPackage().getName());

	/** Create a new RandomOrgRandom instance for the given API key.
	 ** <p>
	 ** This RandomOrgRandom needs an official API key for the RANDOM.ORG API.
	 ** See https://api.random.org/api-keys for more details.
	 ** <p>
	 ** The RANDOM.ORG library guarantees only one client is running per API key at any 
	 ** given time, so it's safe to create multiple <code>RandomOrgRandom</code> classes
	 ** with the same API key if needed.
	 ** 
	 ** @param apiKey a RANDOM.ORG API key
	 **/
	public RandomOrgRandom(String apiKey) {
		this(apiKey, DEFAULT_CACHE_SIZE);
	}
	
	/** Create a new RandomOrgRandom instance for the given API key and cache size.
	 ** <p>
	 ** This RandomOrgRandom needs an official API key for the RANDOM.ORG API.
	 ** See https://api.random.org/api-keys for more details.
	 ** <p>
	 ** The <code>cacheSize</code> specifies the number of 16-bit values maintained by a background thread.
	 ** If you set the value too high it may consume much of your daily allowance on the server.
	 ** It the value is too low your application will block frequently. The default value is 16.
	 ** <p>
	 ** The RANDOM.ORG library guarantees only one client is running per API key at any 
	 ** given time, so it's safe to create multiple <code>RandomOrgRandom</code> classes
	 ** with the same API key if needed.
	 ** 
	 ** @param apiKey a RANDOM.ORG API key
	 ** @param cacheSize the desired cache size
	 **/
	public RandomOrgRandom(String apiKey, int cacheSize) {
		
		this.cacheSize = cacheSize;
		
		// Defining the RANDOM.ORG client with the given API key.
		this.client = RandomOrgClient.getRandomOrgClient(apiKey);
		
		// Getting several 16-bit numbers at once.
		this.cache = this.client.createIntegerCache(1, 0, 0xFFFF, true, cacheSize);
	}
	
	/** Create a new RandomOrgRandom using the given client.
	 ** <p>
	 ** This RandomOrgRandom needs a RANDOM.ORG client instance for the random.org API.
	 ** <p>
	 ** The RANDOM.ORG library guarantees only one client is running per API key at any 
	 ** given time, so it's safe to create multiple <code>RandomOrgRandom</code> classes
	 ** with the same API key if needed.
	 ** 
	 ** @param client the RANDOM.ORG client to use
	 **/
	public RandomOrgRandom(RandomOrgClient client) {
		this(client, DEFAULT_CACHE_SIZE);
	}
	
	/** Create a new RandomOrgRandom using the given client and cache size.
	 ** <p>
	 ** This RandomOrgRandom needs a RANDOM.ORG client instance for the random.org API.
	 ** <p>
	 ** The <code>cacheSize</code> specifies the number of 16-bit values maintained by a background thread.
	 ** If you set the value too high it may consume much of your daily allowance on the server.
	 ** It the value is too low your application will block frequently. The default value is 16.
	 ** <p>
	 ** The RANDOM.ORG library guarantees only one client is running per API key at any 
	 ** given time, so it's safe to create multiple <code>RandomOrgRandom</code> classes
	 ** with the same API key if needed.
	 ** 
	 ** @param client the RANDOM.ORG client to use
	 ** @param cacheSize the desired cache size
	 **/
	public RandomOrgRandom(RandomOrgClient client, int cacheSize) {
		this.client = client;
		this.cacheSize = cacheSize;

		// Getting several 16-bit numbers at once.
		this.cache = this.client.createIntegerCache(1, 0, 0xFFFF, true, cacheSize);
	}
	
	/** Gets the given count of random bits.
	 ** <p>
	 ** Note: for standard retrieval of bits use the <code>next(int)</code> method.
	 ** <p>
	 ** <code>numBits</code> must be in the range of <code>0</code> to <code>16</code> inclusive.
	 ** <p>
	 ** This method will return <code>numBits</code> bits as a 16 bit short value. If
	 ** less than 16 bits are requested the random bits are stored in the least significant bits 
	 ** and the most significant bits are filled with zeros.
	 ** All random numbers are in the range of <code>0</code> to <code>2^numBits - 1</code> inclusive.
	 ** <pre>
	 ** Example getting 12 bits:
	 ** 
	 ** int ranBits = getSubBits(12) & 0x0FFF
	 ** </pre> 
	 ** 
	 ** @param numBits number of bits to fetch
	 **
	 ** @return a short containing the requested number of random bits
	 **
	 ** @see #next(int)
	 **
	 ** @throws IllegalArgumentException if the number of bits requested is not between 0 and 16 inclusive
	 **/
	protected synchronized short getSubBits(int numBits) {
		
		int mask = ((int) (Math.pow(2, numBits)) - 1);
		
		if (numBits < 0 || 16 < numBits) {
			throw new IllegalArgumentException("Only 0 to 16 bits can be fetched");
		}
		
		if (this.bitBufferLength < numBits) {
			this.bitBuffer |= (nextShort() & 0xFFFF) << this.bitBufferLength;
			this.bitBufferLength += 16;
		}
		
		short value = (short) (this.bitBuffer & mask);
		
		this.bitBuffer >>= numBits;
		
		this.bitBufferLength -= numBits;
		
		return value;
	}
	
	/** Gets the remaining quota for the used key.
	 ** <p>
	 ** This method gets the bit count still retrievable from the
	 ** server. If this value is negative no more bits can be retrieved.
	 ** <p>
	 ** Note that this method will access a buffered value if that value is not too 
	 ** old, so the returned value can be different from the value on the server.
	 ** <p>
	 ** Note that this value does not contain the local cached bits, so the current
	 ** local available bit count can be larger than the return of this method.
	 ** 
	 ** @return the remaining bit quota
	 **
	 ** @throws RandomOrgSendTimeoutException @see {@link RandomOrgClient#getBitsLeft()}
	 ** @throws RandomOrgKeyNotRunningError @see {@link RandomOrgClient#getBitsLeft()}
	 ** @throws RandomOrgInsufficientRequestsError @see {@link RandomOrgClient#getBitsLeft()}
	 ** @throws RandomOrgInsufficientBitsError @see {@link RandomOrgClient#getBitsLeft()}
	 ** @throws RandomOrgBadHTTPResponseException @see {@link RandomOrgClient#getBitsLeft()}
	 ** @throws RandomOrgRANDOMORGError @see {@link RandomOrgClient#getBitsLeft()}
	 ** @throws RandomOrgJSONRPCError @see {@link RandomOrgClient#getBitsLeft()}
	 ** @throws MalformedURLException @see {@link RandomOrgClient#getBitsLeft()}
	 ** @throws IOException @see {@link RandomOrgClient#getBitsLeft()}
	 **/
	public long getRemainingQuota() throws RandomOrgSendTimeoutException,
										   RandomOrgKeyNotRunningError,
										   RandomOrgInsufficientRequestsError, 
										   RandomOrgInsufficientBitsError,
										   RandomOrgBadHTTPResponseException,
										   RandomOrgRANDOMORGError,
										   RandomOrgJSONRPCError,
										   MalformedURLException,
										   IOException {
		return this.client.getBitsLeft();
	}
	
	/** Gets the next full 16 bit random number.
	 ** 
	 ** @return a 16 bit random number
	 **
	 ** @throws NoSuchElementException if a number couldn't be retrieved
	 **/
	public short nextShort() {
		Short s;
		
		try {
			while (true) {
				s = null;
				
				try {
					s = (short) this.cache.get()[0];
					
				} catch (NoSuchElementException e) {
					try {
						if (getRemainingQuota() <= 0) {
							throw e;
						}
					} catch (RandomOrgKeyNotRunningError e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom getShort Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
						throw e;
					} catch (RandomOrgInsufficientRequestsError e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom getShort Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
						throw e;
					} catch (RandomOrgInsufficientBitsError e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom getShort Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
						throw e;
					} catch (MalformedURLException e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom getShort Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
						throw e;
					} catch (RandomOrgSendTimeoutException e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom getShort Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
					} catch (RandomOrgBadHTTPResponseException e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom getShort Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
					} catch (RandomOrgRANDOMORGError e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom getShort Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
					} catch (RandomOrgJSONRPCError e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom getShort Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
					} catch (IOException e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom getShort Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
					}
				}
				
				if (s == null) {
					// block until new bits are available
					Thread.sleep(100);
				} else {
					return s;
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();

			throw new NoSuchElementException("Interrupt was triggered.");
		}
	}
	
	/** Blocking implementation of {@link java.util.Random#next(int)} using RANDOM.ORG's RNG service.
	 ** 
	 ** @see java.util.Random#next(int)
	 **/
	@Override
	public int next(int bits) {
		int numShorts = (bits+15)/16;
		short b[] = new short[numShorts];
		int next = 0;
		
		// Get full shorts.
		for (int i = 1; i < b.length; i++) {
			b[i] = nextShort();			
		}
		
		// Get last (potentially) partial short.
		int sigleBits = 16 - numShorts*16 + bits;
		b[0] = sigleBits == 16 ? nextShort() : getSubBits(sigleBits);
		
		// Merge together.
		for (int i = 0; i < numShorts; i++) {
			next = (next << 16) + (b[i] & 0xFFFF);
		}
		
		return next;
	}
	
	/** Blocking implementation of {@link java.util.Random#nextInt(int)} using RANDOM.ORG's RNG service.
	 ** 
	 ** @see java.util.Random#nextInt(int)
	 **/
	@Override
	public int nextInt(int n) {
		if (n <= 0) {
			throw new IllegalArgumentException("n must be positive");			
		}

		// between 0 (inclusive) and n (exclusive) there's only one possible return value
		if (n == 1) {
			return 0;
		}
		
		int bits = (int) Math.ceil(Math.log(n) / Math.log(2));
		
		// i.e., n is a power of 2
		if ((n & -n) == n) {
			return next(bits);
		}
		
		// keep searching until a value within the requested range (0..n-1) is found
		// this is wasteful, but it achieves a truly uniform distribution regardless of range
		int val;
		do {
			val = next(bits);
		} while (val >= n);
		
		return val;
	}

	/** Returns the size of the local cache.
	 ** <p>
	 ** This size can be specified in the constructor. 
	 ** 
	 ** @return the size of the cache.
	 **/
	public int getCachSize() {
		return this.cacheSize;
	}
}