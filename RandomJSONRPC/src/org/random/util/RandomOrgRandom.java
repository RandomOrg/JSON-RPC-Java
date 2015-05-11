package org.random.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
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
 ** By default this caches a maximum of 512 bits in two blobs of 256 bits each.
 ** To optimize the cache for your needs you can setup the cache size (number of blobs) and 
 ** cache bits (number of bits per blob) on creation of an instance.
 ** 
 ** @see https://random.org/
 ** @see https://api.random.org/
 ** 
 ** @author Adam Wagenhaeuser <adam@wag-web.de>
 **/
public class RandomOrgRandom extends Random {

	/**
	 **/
	private static final long serialVersionUID = 4785372106424073371L;
	
	/** integer bit masks */
	private static final int[] MASKS = new int[] {
		0x00000000,
		0x00000001,
		0x00000003,
		0x00000007,
		0x0000000F,
		0x0000001F,
		0x0000003F,
		0x0000007F,
		0x000000FF,
		0x000001FF,
		0x000003FF,
		0x000007FF,
		0x00000FFF,
		0x00001FFF,
		0x00003FFF,
		0x00007FFF,
		0x0000FFFF,
		0x0001FFFF,
		0x0003FFFF,
		0x0007FFFF,
		0x000FFFFF,
		0x001FFFFF,
		0x003FFFFF,
		0x007FFFFF,
		0x00FFFFFF,
		0x01FFFFFF,
		0x03FFFFFF,
		0x07FFFFFF,
		0x0FFFFFFF,
		0x1FFFFFFF,
		0x3FFFFFFF,
		0x7FFFFFFF,
		0xFFFFFFFF,
	};

	/** Default cache size is 256.
	 **/
	private static final int DEFAULT_CACHE_BITS = 256;

	/** Default cache number is 2.
	 **/
	private static final int DEFAULT_CACHE_SIZE = 2;

	/** Cache bits - number of bits to cache per blob.
	 **
	 ** Must be within the [1,1048576] range and must be divisible by 8.
	 ** This will be automatically enforced (rounded up unless too large).
	 **
	 ** Default is 256.
	 **/
	private int cacheBits;
	
	/** Cache size - number of blobs to cache.
	 **
	 ** Cache size minimum is 2, which will be automatically enforced.
	 ** Default size is 2.
	 **/
	private int cacheSize;

	/** The local RANDOM.ORG blob cache.
	 ** <p>
	 ** This stores up to {@link #cacheSize}*{@link #cacheBits} values.
	 **/
	private RandomOrgCache<String[]> cache;

	/** The current blob from which random numbers are being pulled.
	 ** Once blob is in use it is removed from the blob cache.
	 **/
	private byte[] currentBlob;
	
	/** Next index into the currently active blob.
	 **/
	private int currentBlobIndex;
	
	/** The RANDOM.ORG client used for the cache.
	 ** 
	 ** @see #cache 
	 **/
	private RandomOrgClient client;
	
	/** Left to right bit buffer for getting less than 8 bits.
	 **
	 ** @see #bitBufferLength
	 **/
	private byte bitBuffer = 0;
	
	/** Number of bits currently stored in the <code>bitBuffer</code>.
	 ** 
	 ** @see #bitBuffer
	 **/
	private int bitBufferLength = 0;

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
		this(apiKey, DEFAULT_CACHE_SIZE, DEFAULT_CACHE_BITS);
	}
	
	/** Create a new RandomOrgRandom instance for the given API key and cache size.
	 ** <p>
	 ** This RandomOrgRandom needs an official API key for the RANDOM.ORG API.
	 ** See https://api.random.org/api-keys for more details.
	 ** <p>
	 ** The <code>cacheSize</code> specifies the number of blobs maintained by a background thread.
	 ** If you set the value too high it may consume much of your daily allowance on the server.
	 ** It the value is too low your application may block frequently. The default value is 2.
	 ** <p>
	 ** The <code>cacheBits</code> parameter specifies the number of bits per blob in the cache.
	 ** If you set the value too high it may consume much of your daily allowance on the server.
	 ** It the value is too low your application will block frequently. The default value is 256.
	 ** <p>
	 ** The RANDOM.ORG library guarantees only one client is running per API key at any 
	 ** given time, so it's safe to create multiple <code>RandomOrgRandom</code> classes
	 ** with the same API key if needed.
	 ** 
	 ** @param apiKey a RANDOM.ORG API key
	 ** @param cacheSize the desired cache size
	 **/
	public RandomOrgRandom(String apiKey, int cacheSize, int cacheBits) {
		
		this.cacheSize = cacheSize;

		// must be within the [1,1048576] range and must be divisible by 8.
		this.cacheBits = cacheBits > 0 ? (cacheBits < 1048577 ? (cacheBits % 8 == 0 ? cacheBits : cacheBits+1) : 1048576) : 8;
		 
		// Defining the RANDOM.ORG client with the given API key.
		this.client = RandomOrgClient.getRandomOrgClient(apiKey);
		
		// Getting a blob of random bits at once.
		this.cache = this.client.createBlobCache(1, cacheBits, RandomOrgClient.BLOB_FORMAT_BASE64, cacheSize);
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
		this(client, DEFAULT_CACHE_SIZE, DEFAULT_CACHE_BITS);
	}
	
	/** Create a new RandomOrgRandom using the given client and cache size.
	 ** <p>
	 ** This RandomOrgRandom needs a RANDOM.ORG client instance for the random.org API.
	 ** <p>
	 ** The <code>cacheSize</code> specifies the number of blobs maintained by a background thread.
	 ** If you set the value too high it may consume much of your daily allowance on the server.
	 ** It the value is too low your application may block frequently. The default value is 2.
	 ** <p>
	 ** The <code>cacheBits</code> parameter specifies the number of bits per blob in the cache.
	 ** If you set the value too high it may consume much of your daily allowance on the server.
	 ** It the value is too low your application will block frequently. The default value is 256.
	 ** <p>
	 ** The RANDOM.ORG library guarantees only one client is running per API key at any 
	 ** given time, so it's safe to create multiple <code>RandomOrgRandom</code> classes
	 ** with the same API key if needed.
	 ** 
	 ** @param client the RANDOM.ORG client to use
	 ** @param cacheSize the desired cache size
	 **/
	public RandomOrgRandom(RandomOrgClient client, int cacheSize, int cacheBits) {
		this.client = client;
		this.cacheSize = cacheSize;

		// must be within the [1,1048576] range and must be divisible by 8.
		this.cacheBits = cacheBits > 0 ? (cacheBits < 1048577 ? (cacheBits % 8 == 0 ? cacheBits : cacheBits+1) : 1048576) : 8;

		// Getting a blob of random bits at once.
		this.cache = this.client.createBlobCache(1, cacheBits, RandomOrgClient.BLOB_FORMAT_BASE64, cacheSize);
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
	
	/** Returns the size of the local cache.
	 ** <p>
	 ** This size can be specified in the constructor. 
	 ** 
	 ** @return the size of the cache.
	 **/
	public int getCachSize() {
		return this.cacheSize;
	}

	/** Returns the size of each blob in the cache in bits.
	 ** <p>
	 ** This size can be specified in the constructor. 
	 ** 
	 ** @return the number of bits per blob in the cache.
	 **/
	public int getCachBits() {
		return this.cacheBits;
	}
	
	/** Blocking implementation of {@link java.util.Random#nextInt(int)} using RANDOM.ORG's RNG service.
	 ** 
	 ** @see java.util.Random#nextInt(int)
	 **
	 ** @throws NoSuchElementException if a number couldn't be retrieved.
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

		int r = -1;

		// To simulate random.nextInt(max) using raw data, we should scale it.
		// If max is a power of 2, this is easy: just read the next log2(max)
		// bits from the cached data and transform them to an int.
		if (this.isPow2(n)) {
			r = next(this.log2(n));
		
		// If max is not a power of 2, we calculate max' which is the smallest power of 2
		// greater than max. We then read the next log2(max') bits from the
		// cached data and transform them to an int. If the value is < max,
		// return it.  Otherwise we discard it and read the next log(max') bits,
		// etc.  In the worst case, this is wasteful, but it achieves a uniform
		// distribution regardless of the range.
		} else {

			int nC = nextPow2(n);
			do {
				r = next(this.log2(nC));
			} while (r >= n && r != -1);
		}
		
		return r;
	}

	/** Blocking implementation of {@link java.util.Random#next(int)} using RANDOM.ORG's RNG service.
	 ** 
	 ** @see java.util.Random#next(int)
	 **
	 ** @throws NoSuchElementException if a number couldn't be retrieved.
	 **/
	@Override
	public synchronized int next(int bits) {
		
		// how many full bytes to read
		int sz = bits / 8;
		
		// how many bits
		int n = bits % 8;
				
		// our integer
		byte[] b = new byte[4];
		Arrays.fill(b, (byte)0);

		// position for partial bits, if applicable
		int p = b.length-(sz + 1);

		// need a new set of bytes?
		if (this.currentBlob == null || (this.currentBlobIndex + (sz-1)) >= this.currentBlob.length) {
			
			int remaining = this.currentBlob == null ? 0 : this.currentBlob.length - this.currentBlobIndex;
			
			// save the remaining bytes
			if (remaining > 0) {
				System.arraycopy(this.currentBlob, this.currentBlobIndex, b, b.length-sz, remaining);
				sz-=remaining;
			}
			
			this.moveToNextBlob();
		}
		
		// read the next n bits from the cached data into a byte[]
		System.arraycopy(this.currentBlob, this.currentBlobIndex, b, b.length-sz, sz);
		this.currentBlobIndex+=sz;
		
		// read in single bits
		if (n > 0) {
			b[p] = this.getSubBits(n);
		}
		
		// transform byte[] to int
		int num = ByteBuffer.wrap(b).getInt();
		num &= MASKS[bits];
		
		return num;
	}
	
	/** Gets the given count of random bits.
	 ** <p>
	 ** Note: for standard retrieval of bits use the <code>next(int)</code> method.
	 ** <p>
	 ** <code>numBits</code> must be in the range of <code>1</code> to <code>7</code> inclusive.
	 ** <p>
	 ** This method will return <code>numBits</code> bits as an 8 bit byte value.
	 ** The random bits are stored in the least significant bits and the most significant bits are filled with zeros.
	 ** All random numbers are in the range of <code>0</code> to <code>2^numBits - 1</code> inclusive.
	 ** <pre>
	 ** Example getting 4 bits:
	 ** 
	 ** int ranBits = getSubBits(4) & 0x0F
	 ** </pre> 
	 ** 
	 ** @param numBits number of bits to fetch
	 **
	 ** @return a byte containing the requested number of random bits
	 **
	 ** @see #next(int)
	 **
	 ** @throws IllegalArgumentException if the number of bits requested is not between 1 and 7 inclusive
	 ** @throws NoSuchElementException if a number couldn't be retrieved.
	 **/
	protected synchronized byte getSubBits(int numBits) {
		
		if (numBits < 1 || 7 < numBits) {
			throw new IllegalArgumentException("Only 1 to 7 bits can be fetched");
		}
		
		byte b = (byte) 0;
		int bits = numBits;

		// need a new set of bits?
		if (this.bitBufferLength < numBits) {
			
			// need a new blob?
			if (this.currentBlob == null || this.currentBlobIndex >= this.currentBlob.length) {
				this.moveToNextBlob();
			}

			// save the remaining bits - use mask of number remaining in buffer
			b = (byte) (this.bitBuffer & (byte) ((Math.pow(2, this.bitBufferLength)) - 1));
			bits-=this.bitBufferLength;
			b <<= bits;
			
			// get next byte
			this.bitBuffer = this.currentBlob[this.currentBlobIndex++];
			this.bitBufferLength = 8;
		}
		
		// fill up (rest of) bits - use mask of number remaining to get
		b |= (byte) (this.bitBuffer & (byte) ((Math.pow(2, bits)) - 1));
		
		// move bitbuffer on
		this.bitBuffer >>= bits;
		this.bitBufferLength -= bits;
		
		return b;
	}
	
	/** Gets the next blob from the cache.
	 **
	 ** @throws NoSuchElementException if a blob couldn't be retrieved.
	 **/
	private synchronized void moveToNextBlob() throws NoSuchElementException {
		
		String blob = null;
		
		try {
			while (true) {
				
				blob = null;
				
				try {
					blob = this.cache.get()[0];
				} catch (NoSuchElementException e) {
					try {
						if (getRemainingQuota() <= 0) {
							throw e;
						}
					} catch (RandomOrgKeyNotRunningError e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom moveToNextBlob Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
						throw e;
					} catch (RandomOrgInsufficientRequestsError e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom moveToNextBlob Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
						throw e;
					} catch (RandomOrgInsufficientBitsError e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom moveToNextBlob Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
						throw e;
					} catch (MalformedURLException e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom moveToNextBlob Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
						throw e;
					} catch (RandomOrgSendTimeoutException e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom moveToNextBlob Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
					} catch (RandomOrgBadHTTPResponseException e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom moveToNextBlob Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
					} catch (RandomOrgRANDOMORGError e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom moveToNextBlob Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
					} catch (RandomOrgJSONRPCError e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom moveToNextBlob Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
					} catch (IOException e1) {
						LOGGER.log(Level.INFO, "RandomOrgRandom moveToNextBlob Exception: " + e1.getClass().getName() + ": " + e1.getMessage());
					}
				}
				
				if (blob == null) {
					// block until new bits are available
					Thread.sleep(100);
				} else {
					this.currentBlob = Base64.decodeBase64(blob);
					this.currentBlobIndex = 0;
					return;
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();

			throw new NoSuchElementException("Interrupt was triggered.");
		}
	}
	
	/** Return true if n is a power of 2.
	 ** 
	 ** @param n to evaluate
	 ** @return true if n is a power of 2
	 **/
	private boolean isPow2(int n) {
		return n == 0 ? false : (n & (n - 1)) == 0;
	}
	
	/** Return next power of 2 greater than n.
	 ** 
	 ** @param n to evaluate
	 ** @return next power of 2 greater than n.
	 **/
	private int nextPow2(int n) {
		int v = n;
		v--;
		v |= v >> 1;
		v |= v >> 2;
		v |= v >> 4;
		v |= v >> 8;
		v |= v >> 16;
		v++;
        return v;
	}

	/** Log2 of n.
	 ** 
	 ** @param n to evaluate - should be a power of 2.
	 ** @return Log2 of n
	 **/
	private int log2(int n) {
		
	    int log = 0;
	    
	    if ((n & 0xffff0000) != 0 ) {
	    	n >>>= 16;
	    	log = 16;
	    }
	    if (n >= 256) {
	    	n >>>= 8;
	    	log += 8;
	    }
	    if (n >= 16) {
	    	n >>>= 4;
	    	log += 4;
	    }
	    if (n >= 4) {
	    	n >>>= 2;
	    	log += 2;
	    }
	    return log + (n >>> 1);
	}
}