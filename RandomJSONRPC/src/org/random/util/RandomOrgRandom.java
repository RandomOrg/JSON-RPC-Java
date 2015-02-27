package org.random.util;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Random;

import org.random.api.RandomOrgCache;
import org.random.api.RandomOrgClient;

/**
 * An implementation of {@link Random} with a true RNG.
 * <p>
 * This implements a true random number generator based on the random.org service.
 * For more information about that see <code>https://random.org/</code>.
 * <p>
 * This class offers cached number retrieving from the random.org server over the official API.
 * This cached numbers can be instantly access bit wise by the typical public methods of the random class.
 * If more bits are requested as cached the methods will block till they get retrieved from the server.
 * <p>
 * To use this class a official API key from <code>https://api.random.org/api-keys</code> is required.
 * <p>
 * To optimize that cache for your needs you can setup the cache size on creation of an instance. By default
 * it will cache 16 16-bit values sums up to 256 bits. 
 * 
 * @see https://random.org/
 * @see https://api.random.org/
 * 
 * @author Adam Wagenhäuser
 *
 */
public class RandomOrgRandom extends Random {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4785372106424073371L;

	/**
	 * Default cache size of 16 values.
	 */
	protected static final int DEFAULT_CACHE_SIZE = 16;
	
	/**
	 * Count of 16-bit values to cache.
	 * <p>
	 * So the cache size is <code>16 * cacheSize</code> bits.
	 * <p>
	 * Must be at least 2, default is 16.
	 */
	protected int cacheSize;
	
	/**
	 * The local 16-bit value random.org cache.
	 * <p>
	 * It stores up to {@link #cacheSize} values.
	 */
	protected RandomOrgCache<int[]> cache;
	
	/**
	 * The random.org client used for the cache.
	 * 
	 * @see #cache 
	 */
	protected RandomOrgClient client;
	
	/**
	 * Left to right bit buffer for getting single bits.
	 * 
	 * @see #bitBufferLength
	 */
	protected int bitBuffer = 0;
	
	/**
	 * Count of currently stored bits in the <code>bitBuffer</code>.
	 * 
	 * @see #bitBuffer
	 */
	protected int bitBufferLength = 0;
	
	/**
	 * Create a new Random instance for the given api key.
	 * <p>
	 * This Random needs an official API key for the random.org API. See
	 * https://api.random.org/api-keys for more details.
	 * <p>
	 * The random.org library guarantees that not multiple clients are running
	 * at once. So it's save to create multiple <code>RandomOrgRandom</code>s
	 * with the same key, if needed.
	 * 
	 * @param apiKey an API key to use
	 */
	public RandomOrgRandom(String apiKey) {
		this(apiKey, DEFAULT_CACHE_SIZE);
	}
	
	/**
	 * Create a new Random instance for the given api key and given cache size.
	 * <p>
	 * This Random needs an official API key for the random.org API. See
	 * https://api.random.org/api-keys for more details.
	 * <p>
	 * The <code>cacheSize</code> specifies the count of 16-bit values maintained by a background thread.
	 * If you set the value to high such a creation of a random will consume a lot of the allowance.
	 * It it is to low your application will block frequently. The default value is 16 values.
	 * <p>
	 * The random.org library guarantees that not multiple clients are running
	 * at once. So it's save to create multiple <code>RandomOrgRandom</code>s
	 * with the same key, if needed.
	 * 
	 * @param apiKey an API key to use
	 * @param cacheSize the size of the cache to use
	 */
	public RandomOrgRandom(String apiKey, int cacheSize) {
		
		this.cacheSize = cacheSize;
		
		/*
		 * Defining the random.org client with the given app key.
		 */
		client = RandomOrgClient.getRandomOrgClient(apiKey);
		
		/*
		 * Getting always 16-bit numbers at once.
		 */
		cache = client.createIntegerCache(1, 0, 0xFFFF, true, cacheSize);
		
	}
	
	/**
	 * Create a new Random instance using the given client.
	 * <p>
	 * This Random needs a random.org client instance for the random.org API.
	 * <p>
	 * The random.org library guarantees that not multiple clients are running
	 * at once. So it's save to create multiple <code>RandomOrgRandom</code>s
	 * with the same key, if needed.
	 * 
	 * @param client a random.org client to use
	 */
	public RandomOrgRandom(RandomOrgClient client) {
		this(client, DEFAULT_CACHE_SIZE);
	}
	
	/**
	 * Create a new Random instance using the given client and given cache size.
	 * <p>
	 * This Random needs a random.org client instance for the random.org API.
	 * <p>
	 * The <code>cacheSize</code> specifies the count of 16-bit values maintained by a background thread.
	 * If you set the value to high such a creation of a random will consume a lot of the allowance.
	 * It it is to low your application will block frequently. The default value is 16 values.
	 * <p>
	 * The random.org library guarantees that not multiple clients are running
	 * at once. So it's save to create multiple <code>RandomOrgRandom</code>s
	 * with the same key, if needed.
	 * 
	 * @param client a random.org client to use
	 * @param cacheSize the size of the cache to use
	 */
	public RandomOrgRandom(RandomOrgClient client, int cacheSize) {
		this.client = client;
		this.cacheSize = cacheSize;

		/*
		 * Getting always 16-bit numbers at once.
		 */
		cache = client.createIntegerCache(1, 0, 0xFFFF, true, cacheSize);
	}
	
	/**
	 * Gets the given count of random bits.
	 * <p>
	 * Note: for usual bits retrieving the <code>next(int)</code> method should be used. 
	 * <p>
	 * The <code>numBits</code> must in range from <code>0</code> to <code>16</code>, both included.
	 * <p>
	 * This method will return the count of bits as a 16 bit short value. If
	 * less than 16 bits are requested the random bits are stored in the less
	 * significant bits and the more significant bits are filled with zeros.
	 * So all random number are in range from <code>0</code> to <code>2^numBits - 1</code>, both included.
	 * <pre>
	 * Example getting 12 bits:
	 * 
	 * int ranBits = getSubBits(12) & 0x0FFF
	 * </pre> 
	 * 
	 * @param numBits the count of bits to get
	 * @return a short containing the given count of random bits
	 * @see #next(int)
	 * @throws NoSuchElementException if the number couldn't be retrieved
	 */
	protected synchronized short getSubBits(int numBits) {
		
		int mask = ((int) (Math.pow(2, numBits)) - 1);
		
		if (numBits < 0 || 16 < numBits) {
			throw new IllegalArgumentException("Only 0 up to 16 bits can be get");
		}
		
		if (bitBufferLength < numBits) {
			bitBuffer |= (nextShort() & 0xFFFF) << bitBufferLength;
			bitBufferLength += 16;
		}
		
		short value = (short) (bitBuffer & mask);
		
		bitBuffer >>= numBits;
		
		bitBufferLength -= numBits;
		
		return value;
	}
	
	/**
	 * Gets the remaining quota for the used key.
	 * <p>
	 * This method gets the count of bit which can be still retrieved from the
	 * server. If this value is negative no more bit can be retrieved.
	 * <p>
	 * Note that this method will access a buffered value if not too old,
	 * so the return can be different from the actual value.
	 * <p>
	 * Note that this value do not contain local cached bit. So the current
	 * local available bits count can be bigger that the return of this method.
	 * 
	 * @return the remaining quota
	 * @throws IOException @see {@link RandomOrgClient#getBitsLeft()}
	 */
	public long getRemainingQuota() throws IOException {
		
		return client.getBitsLeft();
		
	}
	
	/**
	 * Gets the next fully 16 bit random number.
	 * 
	 * @return a 16 bit random number
	 * @throws NoSuchElementException if the number couldn't be retrieved
	 */
	public short nextShort() {
		Short s;
		
		try {
			while (true) {
				s = null;
				
				try {
					s = (short) cache.get()[0];
				} catch (NoSuchElementException e) {
					try {
						if (getRemainingQuota() <= 0) {
							throw e;
						}
					} catch (IOException ioe) {
						// TODO add logger
						throw e;
					}
				}
				
				if (s == null) {
					// TODO block till new bits are available
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
	
	@Override
	protected int next(int numBits) {
		int numShorts = (numBits+15)/16;
		short b[] = new short[numShorts];
		int next = 0;
		
		/*
		 * Get full shorts.
		 */
		
		for (int i = 1; i < b.length; i++)
			b[i] = nextShort();
		
		/*
		 * Get last partial short. 
		 */
		
		int sigleBits = 16 - numShorts*16 + numBits;
		
		b[0] = getSubBits(sigleBits);
		
		/*
		 * Merge together.
		 */
		
		for (int i = 0; i < numShorts; i++)
			next = (next << 16) + (b[i] & 0xFFFF);
		
		return next ;//>>> (numShorts*16 - numBits);
	}

	/**
	 * Returns the size of the cache locally used.
	 * <p>
	 * This size can be specified in the constructor. 
	 * 
	 * @return the size of the cache
	 */
	public int getCachSize() {
		return cacheSize;
	}
}
