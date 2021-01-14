package org.random.api;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import org.random.api.exception.RandomOrgBadHTTPResponseException;
import org.random.api.exception.RandomOrgInsufficientBitsError;
import org.random.api.exception.RandomOrgInsufficientRequestsError;
import org.random.api.exception.RandomOrgJSONRPCError;
import org.random.api.exception.RandomOrgKeyNotRunningError;
import org.random.api.exception.RandomOrgRANDOMORGError;
import org.random.api.exception.RandomOrgSendTimeoutException;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * RandomOrgClient main class through which API functions are accessed.
 * 
 * This class provides either serialized or unserialized (determined on class creation) access 
 * to both the signed and unsigned methods of the RANDOM.ORG API. These are threadsafe and 
 * implemented as blocking remote procedure calls.
 * 
 * If requests are to be issued serially a background Thread will maintain a Queue of requests 
 * to process in sequence. 
 * 
 * The class also provides access to creation of a convenience class, RandomOrgCache, for precaching 
 * API responses when the request is known in advance.
 * 
 * This class will only allow the creation of one instance per API key. If an instance of this class 
 * already exists for a given key, that instance will be returned instead of a new instance.
 * 
 * This class obeys most of the guidelines set forth in https://api.random.org/json-rpc/2
 * All requests respect the server's advisoryDelay returned in any responses, or use DEFAULT_DELAY 
 * if no advisoryDelay is returned. If the supplied API key is paused, i.e., has exceeded its daily 
 * bit/request allowance, this implementation will back off until midnight UTC.
 *
 * @see https://api.random.org/
 * @see http://code.google.com/p/google-gson/
 * @author Anders Haahr
 */
public class RandomOrgClient {
	
	// Basic RANDOM.ORG API functions https://api.random.org/json-rpc/2
	private static final String INTEGER_METHOD					= "generateIntegers";
	private static final String INTEGER_SEQUENCE_METHOD			= "generateIntegerSequences";
	private static final String DECIMAL_FRACTION_METHOD			= "generateDecimalFractions";
	private static final String GAUSSIAN_METHOD					= "generateGaussians";
	private static final String STRING_METHOD					= "generateStrings";
	private static final String UUID_METHOD						= "generateUUIDs";
	private static final String BLOB_METHOD						= "generateBlobs";
	private static final String GET_USAGE_METHOD				= "getUsage";

	// Signed RANDOM.ORG API functions https://api.random.org/json-rpc/2/signed
	private static final String SIGNED_INTEGER_METHOD			= "generateSignedIntegers";
	private static final String SIGNED_INTEGER_SEQUENCE_METHOD	= "generateSignedIntegerSequences";
	private static final String SIGNED_DECIMAL_FRACTION_METHOD	= "generateSignedDecimalFractions";
	private static final String SIGNED_GAUSSIAN_METHOD			= "generateSignedGaussians";
	private static final String SIGNED_STRING_METHOD			= "generateSignedStrings";
	private static final String SIGNED_UUID_METHOD				= "generateSignedUUIDs";
	private static final String SIGNED_BLOB_METHOD				= "generateSignedBlobs";
	private static final String GET_RESULT_METHOD               = "getResult";
	private static final String VERIFY_SIGNATURE_METHOD			= "verifySignature";

	// Blob format literals
	public static final String BLOB_FORMAT_BASE64				= "base64";
	public static final String BLOB_FORMAT_HEX					= "hex";

	// Default back-off to use if no advisoryDelay back-off supplied by server (1 second)
	private static final int DEFAULT_DELAY						= 1*1000;

	// On request fetch fresh allowance state if current state data is older than this value (1 hour)
	private static final int ALLOWANCE_STATE_REFRESH_SECONDS	= 3600*1000;
	
	// Default data sizes in bits
	private static final int UUID_SIZE							= 122;
	
	// Default parameter values
	public static final boolean DEFAULT_REPLACEMENT				= true;
	public static final int DEFAULT_INT_BASE					= 10;
	public static final JsonObject DEFAULT_USER_DATA			= null;
	public static final int DEFAULT_CACHE_SIZE					= 20;
	public static final int DEFAULT_CACHE_SIZE_SMALL			= 10;	//UUID and BLOB caches

	// Maintain a dictionary of API keys and their instances.
	private static HashMap<String, RandomOrgClient> keyIndexedInstances = new HashMap<String, RandomOrgClient>();

	private static HashSet<Integer> randomOrgErrors = new HashSet<Integer>();
	static {
		int[] ints = {100, 101, 200, 201, 202, 203, 204, 300, 301, 302, 303, 304, 305, 306, 307, 
				400, 401, 402, 403, 404, 405, 500, 32000};
		for (int i : ints) {
			RandomOrgClient.randomOrgErrors.add(i);
		}
	};
    
	private static final Logger LOGGER = Logger.getLogger(RandomOrgClient.class.getPackage().getName());
	
	private String apiKey;
	private long blockingTimeout;
	private int httpTimeout;
	private boolean serialized;
	
	// Maintain info to obey server advisory delay
	private Object advisoryDelayLock = new Object();
	private int advisoryDelay = 0;
	private long lastResponseReceivedTime = 0;
	
    // Maintain usage statistics from server
    private int requestsLeft = -1;
    private int bitsLeft = -1;

    // Back-off info for when API key is detected as not running - probably because key 
    // has exceeded its daily usage limit. Back-off runs until midnight UTC.
    private long backoff = -1;
    private String backoffError;

    private LinkedList<HashMap<String, Object>> serializedQueue;
    
    // Gson instance for handling certain Json operations
    private Gson gson = new Gson();	
	
    /**
	 * Ensure only one instance of RandomOrgClient exists per API key. Create a new instance 
	 * if the supplied key isn't already known, otherwise return the previously instantiated one.
	 * 
	 * New instance will have a blockingTimeout of 24*60*60*1000 milliseconds, i.e., 1 day, 
	 * a httpTimeout of 120*1000 milliseconds, and will issue serialized requests.
	 *
	 * @param apiKey of instance to create/find, obtained from RANDOM.ORG, see: https://api.random.org/api-keys
	 * 
	 * @return new instance if instance doesn't already exist for this key, else existing instance.
	 */
	public static RandomOrgClient getRandomOrgClient(String apiKey) {
		return RandomOrgClient.getRandomOrgClient(apiKey, 24*60*60*1000, 120*1000, true);		
	}

	/**
	 * Ensure only one instance of RandomOrgClient exists per API key. Create a new instance 
	 * if the supplied key isn't already known, otherwise return the previously instantiated one.
	 *
	 * @param apiKey of instance to create/find, obtained from RANDOM.ORG, see: https://api.random.org/api-keys
	 * @param blockingTimeout maximum time in milliseconds to wait before being allowed to send 
	 *        a request. Note this is a hint not a guarantee. Be advised advisory delay from server 
	 *        must always be obeyed. Supply a value of -1 to allow blocking forever 
	 *        (default 24*60*60*1000, i.e., 1 day).
	 * @param httpTimeout maximum time in milliseconds to wait for the server response to a 
	 *        request (default 120*1000).
	 * @param serialized determines whether or not requests from this instance will be added to 
	 *        a Queue and issued serially or sent when received, obeying any advisory delay 
	 *        (default true).
	 * 
	 * @return new instance if instance doesn't already exist for this key, else existing instance.
	 */
	public static RandomOrgClient getRandomOrgClient(String apiKey, long blockingTimeout, int httpTimeout, boolean serialized) {
		RandomOrgClient instance = RandomOrgClient.keyIndexedInstances.get(apiKey);
		
		if (instance == null) {
			instance = new RandomOrgClient(apiKey, blockingTimeout, httpTimeout, serialized);
			RandomOrgClient.keyIndexedInstances.put(apiKey, instance);
		}
		
		return instance;
	}
	
	/**
	 * Constructor. Initialize class and start serialized request sending Thread running as 
	 * a daemon if applicable.
	 *
	 * @param apiKey of instance to create/find, obtained from RANDOM.ORG, see: https://api.random.org/api-keys
	 * @param blockingTimeout maximum time in milliseconds to wait before being allowed to 
	 *        send a request. Note this is a hint not a guarantee. Be advised advisory delay 
	 *        from server must always be obeyed. Supply a value of -1 to allow blocking forever 
	 *        (default 24*60*60*1000, i.e., 1 day).
	 * @param httpTimeout maximum time in milliseconds to wait for the server response to a 
	 * 	      request. (default 120*1000).
	 * @param serialized determines whether or not requests from this instance will be added to 
	 *        a Queue and issued serially or sent when received, obeying any advisory delay 
	 *        (default true).
	 */
	private RandomOrgClient(String apiKey, long blockingTimeout, int httpTimeout, boolean serialized) {		
		if (serialized) {
			// set up the serialized request Queue and Thread
			this.serializedQueue = new LinkedList<HashMap<String, Object>>();
			
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					RandomOrgClient.this.threadedRequestSending();
				}			
			});
			t.setDaemon(true);
			t.start();
		}
		
		this.serialized = serialized;
		
		this.apiKey = apiKey;
		this.blockingTimeout = blockingTimeout;
		this.httpTimeout = httpTimeout;
		
		try {
			this.getUsage();
		} catch (Exception e) {
			LOGGER.log(Level.INFO, e.getMessage());
		}
	}
	
	// Basic methods for generating randomness, see: https://api.random.org/json-rpc/2/basic
	
	/**
	 * Request and return an array of true random integers within a user-defined range from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateIntegers
	 *
	 * @param n the number of random integers you need. Must be within the [1,1e4] range.
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * 
	 * @return int[] of random integers.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public int[] generateIntegers(int n, int min, int max) 
			throws RandomOrgSendTimeoutException, 
			  	   RandomOrgKeyNotRunningError, 
			  	   RandomOrgInsufficientRequestsError, 
			  	   RandomOrgInsufficientBitsError, 
			  	   RandomOrgBadHTTPResponseException, 
			  	   RandomOrgRANDOMORGError, 
			  	   RandomOrgJSONRPCError, 
			  	   MalformedURLException, 
			  	   IOException {		
		return this.generateIntegers(n, min, max, DEFAULT_REPLACEMENT);
	}
	
	/**
	 * Request and return an array of true random integers within a user-defined range from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateIntegers
	 *
	 * @param n the number of random integers you need. Must be within the [1,1e4] range.
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * 
	 * @return int[] of true random integers.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public int[] generateIntegers(int n, int min, int max, boolean replacement) 
			throws RandomOrgSendTimeoutException, 
			       RandomOrgKeyNotRunningError, 
			       RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {
		return this.extractInts(this.integerMethod(n, min, max, replacement, DEFAULT_INT_BASE));
	}
	
	/**
	 * Request and return an array of true random integers within a user-defined range from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateIntegers
	 *
	 * @param n the number of random integers you need. Must be within the [1,1e4] range.
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * @param base the base that will be used to display the numbers. Values allowed are 2, 8, 10 
	 *        and 16 (default 10). For base 10, if you would prefer an int[] to be returned instead 
	 *        of a String[], please use the {@link #generateIntegers(int n, int min, int max, 
	 *        boolean replacement) generateIntegers} method without the base parameter.
	 * 
	 * @return String[] of true random integers.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public String[] generateIntegers(int n, int min, int max, boolean replacement, int base) 
			throws RandomOrgSendTimeoutException, 
			       RandomOrgKeyNotRunningError, 
			       RandomOrgInsufficientRequestsError, 
			       RandomOrgInsufficientBitsError, 
			       RandomOrgBadHTTPResponseException, 
			       RandomOrgRANDOMORGError,	
			       RandomOrgJSONRPCError, 
			       MalformedURLException, 
			       IOException {		
		return this.extractStrings(this.integerMethod(n, min, max, replacement, base));
	}
	
	/**
	 * Request and return uniform sequences of true random integers within user-defined ranges 
	 * from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateIntegerSequences
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length the length of each array of random integers requested. Must be within the 
	 *        [1,1e4] range. 
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 *
	 * @return int[][] of true random integers.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */	
	public int[][] generateIntegerSequences(int n, int length, int min, int max) 
			throws RandomOrgSendTimeoutException, 
			       RandomOrgKeyNotRunningError, 
			       RandomOrgInsufficientRequestsError, 
			       RandomOrgInsufficientBitsError, 
			       RandomOrgBadHTTPResponseException, 
			       RandomOrgRANDOMORGError, 
			       RandomOrgJSONRPCError, 
			       MalformedURLException, 
			       IOException {		
		return this.generateIntegerSequences(n, length, min, max, DEFAULT_REPLACEMENT);
	}
	
	/**
	 * Request and return uniform sequences of true random integers within user-defined ranges 
	 * from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateIntegerSequences
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length the length of each array of random integers requested. Must be within the 
	 *        [1,1e4] range. 
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 *
	 * @return int[][] of true random integers.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */	
	public int[][] generateIntegerSequences(int n, int length, int min, int max, boolean replacement) 
			throws RandomOrgSendTimeoutException, 
			       RandomOrgKeyNotRunningError, 
			       RandomOrgInsufficientRequestsError, 
			       RandomOrgInsufficientBitsError, 
			       RandomOrgBadHTTPResponseException, 
			       RandomOrgRANDOMORGError, 
			       RandomOrgJSONRPCError, 
			       MalformedURLException, 
			       IOException {		
		return this.extractIntSequences(this.integerSequencesMethod(n, length, min, max, replacement, DEFAULT_INT_BASE, DEFAULT_USER_DATA, false));
	}
	
	/**
	 * Request and return uniform sequences of true random integers within user-defined ranges 
	 * from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateIntegerSequences
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length the length of each array of random integers requested. Must be within the 
	 *        [1,1e4] range. 
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * @param base the base that will be used to display the numbers. Values allowed are 2, 8, 10 
	 *        and 16 (default 10). For base 10, if you would prefer an int[][] to be returned 
	 *        instead of a String[][] please use the {@link #generateIntegerSequences(int n, int length, 
	 *        int min, int max, boolean replacement) generateIntegerSequences} method without
	 *        the base parameter.
	 *
	 * @return String[][] of true random integers.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */	
	public String[][] generateIntegerSequences(int n, int length, int min, int max, boolean replacement, int base) 
			throws RandomOrgSendTimeoutException, 
			       RandomOrgKeyNotRunningError, 
			       RandomOrgInsufficientRequestsError, 
			       RandomOrgInsufficientBitsError, 
			       RandomOrgBadHTTPResponseException, 
			       RandomOrgRANDOMORGError, 
			       RandomOrgJSONRPCError, 
			       MalformedURLException, 
			       IOException {		
		return this.extractIntSequencesString(this.integerSequencesMethod(n, length, 
				min, max, replacement, base, DEFAULT_USER_DATA, false));
	}
	
	/**
	 * Request and return uniform or multiform sequences of true random integers within user-defined 
	 * ranges from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateIntegerSequences
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length an array with n integers each specifying the length of the sequence 
	 *        identified by its index. Each value in the array must be within the [1,1e4] range. 
	 * @param min an array with n integers, each specifying the lower boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 * @param max an array with n integers, each specifying the upper boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 *
	 * @return int[][] of true random integers.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */	
	public int[][] generateIntegerSequences(int n, int[] length, int[] min, int[] max) 
			throws RandomOrgSendTimeoutException, 
			       RandomOrgKeyNotRunningError, 
			       RandomOrgInsufficientRequestsError, 
			       RandomOrgInsufficientBitsError, 
			       RandomOrgBadHTTPResponseException, 
			       RandomOrgRANDOMORGError, 
			       RandomOrgJSONRPCError, 
			       MalformedURLException, 
			       IOException {		
		boolean[] replacement = new boolean[n];
		Arrays.fill(replacement, DEFAULT_REPLACEMENT);
		
		return this.generateIntegerSequences(n, length, min, max, replacement);
	}
	
	/**
	 * Request and return uniform or multiform sequences of true random integers within user-defined 
	 * ranges from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateIntegerSequences
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length an array with n integers each specifying the length of the sequence 
	 *        identified by its index. Each value in the array must be within the [1,1e4] range. 
	 * @param min an array with n integers, each specifying the lower boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 * @param max an array with n integers, each specifying the upper boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 * @param replacement an array with n Boolean values, each specifying whether the sequence 
	 *        identified by its index will be created with or without replacement. If true, the 
	 *        resulting numbers may contain duplicate values, otherwise the numbers will all be 
	 *        unique within each sequence (default true).
	 *
	 * @return int[][] of true random integers.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public int[][] generateIntegerSequences(int n, int[] length, int[] min, int[] max, boolean[] replacement) 
			throws RandomOrgSendTimeoutException, 
			       RandomOrgKeyNotRunningError, 
			       RandomOrgInsufficientRequestsError, 
			       RandomOrgInsufficientBitsError, 
			       RandomOrgBadHTTPResponseException, 
			       RandomOrgRANDOMORGError, 
			       RandomOrgJSONRPCError, 
			       MalformedURLException, 
			       IOException {		
		int[] base = new int[n];
		Arrays.fill(base, DEFAULT_INT_BASE);
		
		return this.extractIntSequences(this.integerSequencesMethod(n, length, 
				min, max, replacement, base, DEFAULT_USER_DATA, false));				
	}
	
	/**
	 * Request and return uniform or multiform sequences of true random integers within user-defined 
	 * ranges from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateIntegerSequences
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length an array with n integers each specifying the length of the sequence 
	 *        identified by its index. Each value in the array must be within the [1,1e4] range. 
	 * @param min an array with n integers, each specifying the lower boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 * @param max an array with n integers, each specifying the upper boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 * @param replacement an array with n Boolean values, each specifying whether the sequence 
	 *        identified by its index will be created with or without replacement. If true, the 
	 *        resulting numbers may contain duplicate values, otherwise the numbers will all be 
	 *        unique within each sequence (default true).
	 * @param base an array with n integer values, each specifying the base that will be used to 
	 * 		  display the sequence identified by its index. Values allowed are 2, 8, 10 and 16 
	 *        (default 10). For base 10, if you require an int[][] instead of a String[][], please 
	 *        use the {@link #generateIntegerSequences(int n, int[] length, int[] min, int[] max, 
	 *        boolean[] replacement) generateIntegerSequences} method without the base parameter.
	 *
	 * @return String[][] of true random integers.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public String[][] generateIntegerSequences(int n, int[] length, int[] min, int[] max, boolean[] replacement, int[] base) 
			throws RandomOrgSendTimeoutException, 
                   RandomOrgKeyNotRunningError, 
                   RandomOrgInsufficientRequestsError, 
                   RandomOrgInsufficientBitsError, 
                   RandomOrgBadHTTPResponseException, 
                   RandomOrgRANDOMORGError, 
                   RandomOrgJSONRPCError, 
                   MalformedURLException, 
                   IOException {		
		return this.extractIntSequencesString(this.integerSequencesMethod(n, length, 
				min, max, replacement, base, DEFAULT_USER_DATA, false));
	}

	/**
	 * Request and return a list (size n) of true random decimal fractions, from a uniform 
	 * distribution across the [0,1] interval with a user-defined number of decimal places 
	 * from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateDecimalFractions
	 *
	 * @param n how many random decimal fractions you need. Must be within the [1,1e4] range.
	 * @param decimalPlaces the number of decimal places to use. Must be within the [1,20] range.
	 * 
	 * @return double[] of true random decimal fractions.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public double[] generateDecimalFractions(int n, int decimalPlaces) 
			throws RandomOrgSendTimeoutException, 
			       RandomOrgKeyNotRunningError, 
			       RandomOrgInsufficientRequestsError, 
			       RandomOrgInsufficientBitsError, 
			       RandomOrgBadHTTPResponseException, 
			       RandomOrgRANDOMORGError, 
			       RandomOrgJSONRPCError, 
			       MalformedURLException, 
			       IOException {
		return this.generateDecimalFractions(n, decimalPlaces, DEFAULT_REPLACEMENT);
	}

	/**
	 * Request and return a list (size n) of true random decimal fractions, from a uniform 
	 * distribution across the [0,1] interval with a user-defined number of decimal places 
	 * from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateDecimalFractions
	 *
	 * @param n how many random decimal fractions you need. Must be within the [1,1e4] range.
	 * @param decimalPlaces the number of decimal places to use. Must be within the [1,20] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 *
	 * @return double[] of true random decimal fractions.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public double[] generateDecimalFractions(int n, int decimalPlaces, boolean replacement) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject request = new JsonObject();

		request.addProperty("n", n);
		request.addProperty("decimalPlaces", decimalPlaces);
		request.addProperty("replacement", replacement);
		
		request = this.generateKeyedRequest(request, DECIMAL_FRACTION_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		return this.extractDoubles(response);
	}
	
	/**
	 * Request and return a list (size n) of true random numbers from a Gaussian distribution 
	 * (also known as a normal distribution). The form uses a Box-Muller Transform to generate 
	 * the Gaussian distribution from uniformly distributed numbers. 
	 * See: https://api.random.org/json-rpc/2/basic#generateGaussians
     * 
	 * @param n how many random numbers you need. Must be within the [1,1e4] range.
	 * @param mean the distribution's mean. Must be within the [-1e6,1e6] range.
	 * @param standardDeviation the distribution's standard deviation. Must be within the 
	 *        [-1e6,1e6] range.
	 * @param significantDigits the number of significant digits to use. Must be within the 
	 *        [2,20] range.
	 *
	 * @return double[] of true random doubles from a Gaussian distribution.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public double[] generateGaussians(int n, double mean, double standardDeviation, int significantDigits) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject request = new JsonObject();

		request.addProperty("n", n);
		request.addProperty("mean", mean);
		request.addProperty("standardDeviation", standardDeviation);
		request.addProperty("significantDigits", significantDigits);
		
		request = this.generateKeyedRequest(request, GAUSSIAN_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		return this.extractDoubles(response);
	}

	/**
	 * Request and return a list (size n) of true random unicode strings from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateStrings
     *
	 * @param n how many random strings you need. Must be within the [1,1e4] range.
	 * @param length the length of each string. Must be within the [1,20] range. All strings 
	 *        will be of the same length.
	 * @param characters a string that contains the set of characters that are allowed to occur 
	 *        in the random strings. The maximum number of characters is 80.
	 *
	 * @return String[] of true random Strings.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public String[] generateStrings(int n, int length, String characters) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		return this.generateStrings(n, length, characters, DEFAULT_REPLACEMENT);
	}
	
	/**
	 * Request and return a list (size n) of true random unicode strings from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateStrings
     *
	 * @param n how many random strings you need. Must be within the [1,1e4] range.
	 * @param length the length of each string. Must be within the [1,20] range. All strings 
	 *        will be of the same length.
	 * @param characters a string that contains the set of characters that are allowed to occur 
	 *        in the random strings. The maximum number of characters is 80.
	 * @param replacement specifies whether the random strings should be picked with replacement. 
	 *        If True the resulting list of strings may contain duplicates, otherwise the strings 
	 *        will all be unique (default true).
	 *
	 * @return String[] of true random Strings.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public String[] generateStrings(int n, int length, String characters, boolean replacement) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject request = new JsonObject();

		request.addProperty("n", n);
		request.addProperty("length", length);
		request.addProperty("characters", characters);
		request.addProperty("replacement", replacement);
		
		request = this.generateKeyedRequest(request, STRING_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		return this.extractStrings(response);
	}

	/**
	 * Request and return a list (size n) of version 4 true random Universally Unique IDentifiers 
	 * (UUIDs) in accordance with section 4.4 of RFC 4122, from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateUUIDs
     * 
	 * @param n how many random UUIDs you need. Must be within the [1,1e3] range.
	 *
	 * @return UUID[] of true random UUIDs.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public UUID[] generateUUIDs(int n) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject request = new JsonObject();
		
		request.addProperty("n", n);
		
		request = this.generateKeyedRequest(request, UUID_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		return this.extractUUIDs(response);
	}

	/**
	 * Request and return a list (size n) of Binary Large OBjects (BLOBs) as unicode strings 
	 * containing true random data from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateBlobs
     * 
	 * @param n how many random blobs you need. Must be within the [1,100] range.
	 * @param size the size of each blob, measured in bits. Must be within the [1,1048576] range 
	 *        and must be divisible by 8.
	 *
	 * @return String[] of true random blobs as Strings.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public String[] generateBlobs(int n, int size) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		return this.generateBlobs(n, size, BLOB_FORMAT_BASE64);
	}

	/**
	 * Request and return a list (size n) of Binary Large OBjects (BLOBs) as unicode strings 
	 * containing true random data from the server. 
	 * See: https://api.random.org/json-rpc/2/basic#generateBlobs
     * 
	 * @param n how many random blobs you need. Must be within the [1,100] range.
	 * @param size the size of each blob, measured in bits. Must be within the [1,1048576] range 
	 *        and must be divisible by 8.
	 * @param format specifies the format in which the blobs will be returned. Values allowed 
	 *        are BLOB_FORMAT_BASE64 and BLOB_FORMAT_HEX (default BLOB_FORMAT_BASE64).
	 *
	 * @return String[] of true random blobs as Strings.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public String[] generateBlobs(int n, int size, String format) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject request = new JsonObject();
		
		request.addProperty("n", n);
		request.addProperty("size", size);
		request.addProperty("format", format);
		
		request = this.generateKeyedRequest(request, BLOB_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		return this.extractStrings(response);
	}
	
	// Signed methods for generating randomness, see: https://api.random.org/json-rpc/2/signed
	
	/**
	 * Request a list (size n) of true random integers within a user-defined range from the server. 
	 * Returns a dictionary object with the parsed integer list mapped to 'data', the original 
	 * response mapped to 'random', and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedIntegers
	 *
	 * @param n how many random integers you need. Must be within the [1,1e4] range.
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, 
	 *         "data": random int[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedIntegers(int n, int min, int max) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		return this.generateSignedIntegers(n, min, max, DEFAULT_REPLACEMENT);
	}
	
	/**
	 * Request a list (size n) of true random integers within a user-defined range from the server. 
	 * Returns a dictionary object with the parsed integer list mapped to 'data', the original 
	 * response mapped to 'random', and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedIntegers
	 *
	 * @param n how many random integers you need. Must be within the [1,1e4] range.
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, 
	 *         "data": random int[] 
	 *         
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedIntegers(int n, int min, int max, boolean replacement) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		return this.generateSignedIntegers(n, min, max, replacement, DEFAULT_INT_BASE, DEFAULT_USER_DATA);
	}
	
	/**
	 * Request a list (size n) of true random integers within a user-defined range from the server. 
	 * Returns a dictionary object with the parsed integer list mapped to 'data', the original 
	 * response mapped to 'random', and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedIntegers
	 *
	 * @param n how many random integers you need. Must be within the [1,1e4] range.
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * @param base the base that will be used to display the numbers. Values allowed are 2, 
	 *        8, 10 and 16 (default 10).
	 * @param userData JsonObject that will be included in unmodified form. Its maximum size 
	 *        in encoded (String) form is 1,000 characters (default null).
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, 
	 *         "data": random int[] if decimal (base 10)
	 *                 or random String[] if non-decimal (any other base value)
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedIntegers(int n, int min, int max, boolean replacement, int base, JsonObject userData) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {
		JsonObject request = new JsonObject();

		request.addProperty("n", n);
		request.addProperty("min", min);
		request.addProperty("max", max);
		request.addProperty("replacement", replacement);
		request.addProperty("base", base);
		request.add("userData", userData);
		
		request = this.generateKeyedRequest(request, SIGNED_INTEGER_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		HashMap<String, Object> result = new HashMap<String, Object>();
		if (base == 10) {
			result.put("data", this.extractInts(response));
		} else {
			result.put("data", this.extractStrings(response));
		}
		return this.extractSignedResponse(response, result);
	}
	
	/**
	 * Request and return uniform sequences of true random integers within user-defined 
	 * ranges from the server. Returns a dictionary object with the parsed 2D integer array mapped to 
	 * 'data', the original response mapped to 'random', and the response's signature mapped to 'signature'.
	 * See: https://api.random.org/json-rpc/2/signed#generateIntegerSequences
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length the length of each array of random integers requested. Must be within the 
	 *        [1,1e4] range. 
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * 
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, 
	 *         "data": random int[][] 
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */	
	public HashMap<String, Object> generateSignedIntegerSequences(int n, int length, int min, int max) 
			throws RandomOrgSendTimeoutException, 
                   RandomOrgKeyNotRunningError,
                   RandomOrgInsufficientRequestsError, 
                   RandomOrgInsufficientBitsError,
                   RandomOrgBadHTTPResponseException,
                   RandomOrgRANDOMORGError,
                   RandomOrgJSONRPCError,
                   MalformedURLException,
                   IOException {		
		return this.generateSignedIntegerSequences(n, length, min, max, DEFAULT_REPLACEMENT, 
				DEFAULT_INT_BASE, DEFAULT_USER_DATA);
	}
	
	/**
	 * Request and return uniform sequences of true random integers within user-defined 
	 * ranges from the server. Returns a dictionary object with the parsed 2D integer array mapped to 
	 * 'data', the original response mapped to 'random', and the response's signature mapped to 'signature'.
	 * See: https://api.random.org/json-rpc/2/signed#generateIntegerSequences
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length the length of each array of random integers requested. Must be within the 
	 *        [1,1e4] range. 
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * @param base the base that will be used to display the numbers. Values allowed are 2, 8, 
	 *        10 and 16 (default 10). 
	 * @param userData JsonObject that will be included in unmodified form. Its maximum size in 
	 *        encoded (String) form is 1,000 characters (default null).
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, 
	 *         "data": random int[][] if decimal (base 10) 
	 *                 or random String[][] if non-decimal (any other base value)
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */	
	public HashMap<String, Object> generateSignedIntegerSequences(int n, int length, int min, int max, boolean replacement, int base, JsonObject userData) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject response = this.integerSequencesMethod(n, length, min, max, 
				replacement, base, userData, true);
		
		HashMap<String, Object> result = new HashMap<String, Object>();
		if (base == 10) {
			result.put("data", this.extractIntSequences(response));
		} else {
			result.put("data", this.extractIntSequencesString(response));
		}
		
		return this.extractSignedResponse(response, result);
	}
	
	/**
	 * Request and return uniform or multiform sequences of true random integers within user-defined 
	 * ranges from the server. Returns a dictionary object with the parsed 2D integer array mapped to 
	 * 'data', the original response mapped to 'random', and the response's signature mapped to 'signature'.
	 * See: https://api.random.org/json-rpc/2/signed#generateIntegerSequences"
	 * https://api.random.org/json-rpc/2/signed#generateIntegerSequences
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length the length of each array of random integers requested. Must be within the 
	 *        [1,1e4] range. 
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, 
	 *         "data": random int[][]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */		
	public HashMap<String, Object> generateSignedIntegerSequences(int n, int[] length, int[] min, int[] max) 
			throws RandomOrgSendTimeoutException, 
                   RandomOrgKeyNotRunningError,
                   RandomOrgInsufficientRequestsError, 
                   RandomOrgInsufficientBitsError,
                   RandomOrgBadHTTPResponseException,
                   RandomOrgRANDOMORGError,
                   RandomOrgJSONRPCError,
                   MalformedURLException,
                   IOException {		
		boolean[] replacement = new boolean[n];
		Arrays.fill(replacement, DEFAULT_REPLACEMENT);
		int[] base = new int[n];
		Arrays.fill(base, DEFAULT_INT_BASE);
		
		return this.generateSignedIntegerSequences(n, length, min, max, replacement, 
				base, DEFAULT_USER_DATA);
	}
	
	/**
	 * Request and return uniform or multiform sequences of true random integers within user-defined 
	 * ranges from the server. Returns a dictionary object with the parsed 2D integer array mapped to 
	 * 'data', the original response mapped to 'random', and the response's signature mapped to 'signature'.
	 * See: https://api.random.org/json-rpc/2/signed#generateIntegerSequences
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length an array with n integers each specifying the length of the 
	 * 		  sequence identified by its index. Each value in the array must 
	 *        be within the [1,1e4] range. 
	 * @param min an array with n integers, each specifying the lower boundary of 
	 *        the sequence identified by its index. Each value in the array must 
	 *        be within the [-1e9,1e9] range.
	 * @param max an array with n integers, each specifying the upper boundary of 
	 *        the sequence identified by its index. Each value in the array must 
	 *        be within the [-1e9,1e9] range.
	 * @param replacement an array with n Boolean values, each specifying whether 
	 *        the sequence identified by its index will be created with or without 
	 *        replacement. If true, the resulting numbers may contain 
	 *        duplicate values, otherwise the numbers will all be unique within 
	 *        each sequence (default true).
	 * @param base an array with n integer values, each specifying the base 
	 *        that will be used to display the sequence identified by its index. 
	 *        Values allowed are 2, 8, 10 and 16 (default 10). 
	 * @param userData JsonObject that will be included in unmodified form. Its maximum size 
	 *        in encoded (String) form is 1,000 characters (default null).
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, 
	 *         "data": random int[][] if decimal (all base values are 10) 
	 *                 or random String[][] if non-decimal (any other mix of base values)
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */	
	public HashMap<String, Object> generateSignedIntegerSequences(int n, int[] length, int[] min, int[] max, boolean[] replacement, int[] base, JsonObject userData) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		int[] defaultBase = new int[n];
		Arrays.fill(defaultBase, DEFAULT_INT_BASE);
		
		JsonObject response = this.integerSequencesMethod(n, length, min, max, 
				replacement, base, userData, true);
		
		HashMap<String, Object> result = new HashMap<String, Object>();
		if (Arrays.equals(base, defaultBase)) {
			result.put("data", this.extractIntSequences(response));
		} else {
			result.put("data", this.extractIntSequencesString(response));
		}
		
		return this.extractSignedResponse(response, result);
	}

	/**
	 * Request a list (size n) of true random decimal fractions, from a uniform distribution 
	 * across the [0,1] interval with a  user-defined number of decimal places from the server. 
	 * Returns a dictionary object with the parsed decimal fraction list mapped to 'data', the 
	 * original response mapped to 'random', and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedDecimalFractions
	 *
	 * @param n how many random decimal fractions you need. Must be within the [1,1e4] range.
	 * @param decimalPlaces the number of decimal places to use. Must be within the [1,20] range.
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random double[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedDecimalFractions(int n, int decimalPlaces) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		return this.generateSignedDecimalFractions(n, decimalPlaces, DEFAULT_REPLACEMENT);
	}

	/**
	 * Request a list (size n) of true random decimal fractions, from a uniform distribution 
	 * across the [0,1] interval with a  user-defined number of decimal places from the server. 
	 * Returns a dictionary object with the parsed decimal fraction list mapped to 'data', the 
	 * original response mapped to 'random', and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedDecimalFractions
	 *
	 * @param n how many random decimal fractions you need. Must be within the [1,1e4] range.
	 * @param decimalPlaces the number of decimal places to use. Must be within the [1,20] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random double[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedDecimalFractions(int n, int decimalPlaces, boolean replacement) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		return this.generateSignedDecimalFractions(n, decimalPlaces, replacement, DEFAULT_USER_DATA);
	}
	
	/**
	 * Request a list (size n) of true random decimal fractions, from a uniform distribution 
	 * across the [0,1] interval with a  user-defined number of decimal places from the server. 
	 * Returns a dictionary object with the parsed decimal fraction list mapped to 'data', the 
	 * original response mapped to 'random', and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedDecimalFractions
	 *
	 * @param n how many random decimal fractions you need. Must be within the [1,1e4] range.
	 * @param decimalPlaces the number of decimal places to use. Must be within the [1,20] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * @param userData JsonObject that will be included in unmodified form. Its maximum size in 
	 *        encoded (String) form is 1,000 characters (default null).
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random double[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedDecimalFractions(int n, int decimalPlaces, boolean replacement, JsonObject userData) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject request = new JsonObject();

		request.addProperty("n", n);
		request.addProperty("decimalPlaces", decimalPlaces);
		request.addProperty("replacement", replacement);
		request.add("userData", userData);
		
		request = this.generateKeyedRequest(request, SIGNED_DECIMAL_FRACTION_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		HashMap<String, Object> result = new HashMap<String, Object>();
		result.put("data", this.extractDoubles(response));
		
		return this.extractSignedResponse(response, result);
	}

	/**
	 * Request a list (size n) of true random numbers from a Gaussian distribution (also known 
	 * as a normal distribution). The form uses a Box-Muller Transform to generate the Gaussian 
	 * distribution from uniformly distributed numbers. Returns a dictionary object with the 
	 * parsed random number list mapped to 'data', the original response mapped to 'random', 
	 * and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedGaussians
     * 
	 * @param n how many random numbers you need. Must be within the [1,1e4] range.
	 * @param mean the distribution's mean. Must be within the [-1e6,1e6] range.
	 * @param standardDeviation the distribution's standard deviation. Must be within the 
	 *        [-1e6,1e6] range.
	 * @param significantDigits the number of significant digits to use. Must be within the 
	 *        [2,20] range.
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random double[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedGaussians(int n, double mean, double standardDeviation, int significantDigits) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		return this.generateSignedGaussians(n, mean, standardDeviation, 
				significantDigits, DEFAULT_USER_DATA);
	}
	
	/**
	 * Request a list (size n) of true random numbers from a Gaussian distribution (also known 
	 * as a normal distribution). The form uses a Box-Muller Transform to generate the Gaussian 
	 * distribution from uniformly distributed numbers. Returns a dictionary object with the 
	 * parsed random number list mapped to 'data', the original response mapped to 'random', 
	 * and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedGaussians
     * 
	 * @param n how many random numbers you need. Must be within the [1,1e4] range.
	 * @param mean the distribution's mean. Must be within the [-1e6,1e6] range.
	 * @param standardDeviation the distribution's standard deviation. Must be within the 
	 *        [-1e6,1e6] range.
	 * @param significantDigits the number of significant digits to use. Must be within the 
	 *        [2,20] range.
	 * @param userData JsonObject that will be included in unmodified form. Its maximum size 
	 *        in encoded (String) form is 1,000 characters (default null).
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random double[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedGaussians(int n, double mean, double standardDeviation, int significantDigits, JsonObject userData) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject request = new JsonObject();

		request.addProperty("n", n);
		request.addProperty("mean", mean);
		request.addProperty("standardDeviation", standardDeviation);
		request.addProperty("significantDigits", significantDigits);
		request.add("userData", userData);
		
		request = this.generateKeyedRequest(request, SIGNED_GAUSSIAN_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		HashMap<String, Object> result = new HashMap<String, Object>();
		result.put("data", this.extractDoubles(response));
		
		return this.extractSignedResponse(response, result);
	}

	/**
	 * Request a list (size n) of true random strings from the server. Returns a dictionary 
	 * object with the parsed random string list mapped to 'data', the original response mapped 
	 * to 'random', and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedStrings
     *
	 * @param n how many random strings you need. Must be within the [1,1e4] range.
	 * @param length the length of each string. Must be within the [1,20] range. All strings 
	 *        will be of the same length.
	 * @param characters a string that contains the set of characters that are allowed to 
	 *        occur in the random strings. The maximum number of characters is 80.
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random String[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedStrings(int n, int length, String characters) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {
		return this.generateSignedStrings(n, length, characters, DEFAULT_REPLACEMENT);
	}
	
	/**
	 * Request a list (size n) of true random strings from the server. Returns a dictionary 
	 * object with the parsed random string list mapped to 'data',the original response mapped 
	 * to 'random', and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedStrings
     *
	 * @param n how many random strings you need. Must be within the [1,1e4] range.
	 * @param length the length of each string. Must be within the [1,20] range. All strings 
	 *        will be of the same length.
	 * @param characters a string that contains the set of characters that are allowed to 
	 *        occur in the random strings. The maximum number of characters is 80.
	 * @param replacement specifies whether the random strings should be picked with replacement. 
	 *        If true, the resulting list of strings may contain duplicates, otherwise the strings 
	 *        will all be unique (default true).
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random String[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedStrings(int n, int length, String characters, boolean replacement) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		return this.generateSignedStrings(n, length, characters, replacement, 
				DEFAULT_USER_DATA);
	}
	
	/**
	 * Request a list (size n) of true random strings from the server. Returns a dictionary 
	 * object with the parsed random string list mapped to 'data', the original response mapped 
	 * to 'random', and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedStrings
     *
	 * @param n how many random strings you need. Must be within the [1,1e4] range.
	 * @param length the length of each string. Must be within the [1,20] range. All strings 
	 *        will be of the same length.
	 * @param characters a string that contains the set of characters that are allowed to 
	 *        occur in the random strings. The maximum number of characters is 80.
	 * @param replacement specifies whether the random strings should be picked with replacement. 
	 *        If true, the resulting list of strings may contain duplicates, otherwise the strings 
	 *        will all be unique (default true).
	 * @param userData JsonObject that will be included in unmodified form. Its maximum size 
	 *        in encoded (String) form is 1,000 characters (default null).
	 * 
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random String[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedStrings(int n, int length, String characters, boolean replacement, JsonObject userData) 
					throws RandomOrgSendTimeoutException,
					RandomOrgKeyNotRunningError,
					RandomOrgInsufficientRequestsError, 
					RandomOrgInsufficientBitsError,
					RandomOrgBadHTTPResponseException,
					RandomOrgRANDOMORGError,
					RandomOrgJSONRPCError,
					MalformedURLException,
					IOException {		
		JsonObject request = new JsonObject();

		request.addProperty("n", n);
		request.addProperty("length", length);
		request.addProperty("characters", characters);
		request.addProperty("replacement", replacement);
		request.add("userData", userData);
		
		request = this.generateKeyedRequest(request, SIGNED_STRING_METHOD);
		
		JsonObject response = this.sendRequest(request);

		HashMap<String, Object> result = new HashMap<String, Object>();
		result.put("data", this.extractStrings(response));
		
		return this.extractSignedResponse(response, result);
	}

	/**
	 * Request a list (size n) of version 4 true random Universally Unique IDentifiers (UUIDs) 
	 * in accordance with section 4.4 of RFC 4122, from the server. Returns a dictionary 
	 * object with the parsed random UUID list mapped to 'data', the original response mapped 
	 * to 'random', and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedUUIDs
     * 
	 * @param n how many random UUIDs you need. Must be within the [1,1e3] range.
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random UUID[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedUUIDs(int n) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		return this.generateSignedUUIDs(n, DEFAULT_USER_DATA);
	}
	
	/**
	 * Request a list (size n) of version 4 true random Universally Unique IDentifiers (UUIDs) 
	 * in accordance with section 4.4 of RFC 4122, from the server. Returns a dictionary 
	 * object with the parsed random UUID list mapped to 'data', the original response mapped 
	 * to 'random', and the response's signature mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedUUIDs
     * 
	 * @param n how many random UUIDs you need. Must be within the [1,1e3] range.
	 * @param userData JsonObject that will be included in unmodified form. Its maximum size 
	 *        in encoded (String) form is 1,000 characters (default null).
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random UUID[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedUUIDs(int n, JsonObject userData) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject request = new JsonObject();
		
		request.addProperty("n", n);
		request.add("userData", userData);
		
		request = this.generateKeyedRequest(request, SIGNED_UUID_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		HashMap<String, Object> result = new HashMap<String, Object>();
		result.put("data", this.extractUUIDs(response));
		
		return this.extractSignedResponse(response, result);
	}

	/**
	 * Request a list (size n) of Binary Large OBjects (BLOBs) containing true random data 
	 * from the server. Returns a dictionary object with the parsed random BLOB list mapped 
	 * to 'data', the original response mapped to 'random', and the response's signature 
	 * mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedBlobs 
     * 
	 * @param n how many random blobs you need. Must be within the [1,100] range.
	 * @param size the size of each blob, measured in bits. Must be within the [1,1048576] 
	 *        range and must be divisible by 8.
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random String[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedBlobs(int n, int size) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		return this.generateSignedBlobs(n, size, BLOB_FORMAT_BASE64);
	}

	/**
	 * Request a list (size n) of Binary Large OBjects (BLOBs) containing true random data 
	 * from the server. Returns a dictionary object with the parsed random BLOB list mapped 
	 * to 'data', the original response mapped to 'random', and the response's signature 
	 * mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedBlobs 
     * 
	 * @param n how many random blobs you need. Must be within the [1,100] range.
	 * @param size the size of each blob, measured in bits. Must be within the [1,1048576] 
	 *        range and must be divisible by 8.
	 * @param format specifies the format in which the blobs will be returned. Values allowed 
	 *        are BLOB_FORMAT_BASE64 and BLOB_FORMAT_HEX (default BLOB_FORMAT_BASE64).
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random String[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedBlobs(int n, int size, String format) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		return this.generateSignedBlobs(n,  size, format, DEFAULT_USER_DATA);
	}
	
	/**
	 * Request a list (size n) of Binary Large OBjects (BLOBs) containing true random data 
	 * from the server. Returns a dictionary object with the parsed random BLOB list mapped 
	 * to 'data', the original response mapped to 'random', and the response's signature 
	 * mapped to 'signature'. 
	 * See: https://api.random.org/json-rpc/2/signed#generateSignedBlobs 
     * 
	 * @param n how many random blobs you need. Must be within the [1,100] range.
	 * @param size the size of each blob, measured in bits. Must be within the [1,1048576] 
	 *        range and must be divisible by 8.
	 * @param format specifies the format in which the blobs will be returned. Values allowed 
	 *        are BLOB_FORMAT_BASE64 and BLOB_FORMAT_HEX (default BLOB_FORMAT_BASE64).
	 * @param userData JsonObject that will be included in unmodified form. Its maximum size 
	 *        in encoded (String) form is 1,000 characters (default null).
	 *
	 * @return HashMap with "random": random JsonObject, 
	 *         "signature": signature String, "data": random String[]
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> generateSignedBlobs(int n, int size, String format, JsonObject userData) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject request = new JsonObject();
		
		request.addProperty("n", n);
		request.addProperty("size", size);
		request.addProperty("format", format);
		request.add("userData", userData);
		
		request = this.generateKeyedRequest(request, SIGNED_BLOB_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		HashMap<String, Object> result = new HashMap<String, Object>();
		result.put("data", this.extractStrings(response));
		
		return this.extractSignedResponse(response, result);
	}
	
	// Retrieve a signed result generated within the last 24h, 
	// see https://api.random.org/json-rpc/2/signed#getResult
		
	/**
	 * Retrieve signed random values generated within the last 24h, using a serial number. 
	 * If the historical response was found, a response with the result property containing 
	 * the same values that were returned by the method that was used to generate the values. 
	 * See: https://api.random.org/json-rpc/2/signed#getResult
     * 
	 * @param serialNumber an integer containing the serial number associated with the response 
	 *        you wish to retrieve.
	 * 
	 * @return HashMap with "random": random JsonObject, "signature": signature String
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public HashMap<String, Object> getResult(int serialNumber) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject request = new JsonObject();
		
		request.addProperty("serialNumber", serialNumber);
		
		request = this.generateKeyedRequest(request, GET_RESULT_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		HashMap<String, Object> result = new HashMap<String, Object>();
		
		return this.extractSignedResponse(response, result);
	}

	// Signature verification for signed methods, see: https://api.random.org/json-rpc/2/signed
	
	/**
	 * Verify the signature of a response previously received from one of the methods in 
	 * the Signed API with the server. This is used to examine the authenticity of numbers. 
	 * Return True on verification success. 
	 * See: https://api.random.org/json-rpc/2/signed#verifySignature
     * 
	 * @param random the random field from a response returned by RANDOM.ORG through one of 
	 *        the Signed API methods.
	 * @param signature the signature field from the same response that the random field 
	 *        originates from.
	 *
	 * @return verification success.
	 *
	 *@throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public boolean verifySignature(JsonObject random, String signature) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {		
		JsonObject request = new JsonObject();
		
		request.add("random", random);
		request.addProperty("signature", signature);
		
		request = this.generateRequest(request, VERIFY_SIGNATURE_METHOD);
		
		JsonObject response = this.sendRequest(request);
		
		return this.extractVerificationResponse(response);
	}

	// Methods used to create a cache for any given randomness request.

	/**
	 * Get a RandomOrgCache to obtain random integers. The RandomOrgCache can be polled for 
	 * new results conforming to the output format of the input request. RandomOrgCache type 
	 * is same as expected return value.
	 *
	 * @param n how many random integers you need. Must be within the [1,1e4] range.
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 *
	 * @return {@code RandomOrgCache<int[]>}
	 */
	public RandomOrgCache<int[]> createIntegerCache(int n, int min, int max) {
		return this.createIntegerCache(n, min, max, DEFAULT_REPLACEMENT, DEFAULT_CACHE_SIZE);
	}
	
	/**
	 * Get a RandomOrgCache to obtain random integers. The RandomOrgCache can be polled for 
	 * new results conforming to the output format of the input request. RandomOrgCache type 
	 * is same as expected return value.
	 *
	 * @param n how many random integers you need. Must be within the [1,1e4] range.
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * @param cacheSize number of result-sets for the cache to try to maintain at any given time 
	 *        (default 20, minimum 2).
	 *
	 * @return {@code RandomOrgCache<int[]>}
	 */
	public RandomOrgCache<int[]> createIntegerCache(int n, int min, int max, boolean replacement, int cacheSize) {
		if (cacheSize < 2) {
			cacheSize = 2;
		}

		JsonObject request = new JsonObject();
		
		request.addProperty("min", min);
		request.addProperty("max", max);
		request.addProperty("replacement", replacement);			

		int bulkN = 0;

		// If possible, make requests more efficient by bulk-ordering from the server. 
		// initially set at cache_size/2, but cache will auto-shrink bulk request size if requests can't be fulfilled.
		if (replacement) {
			bulkN = cacheSize/2;
			request.addProperty("n", bulkN*n);

		// not possible to make the request more efficient
		} else {
			request.addProperty("n", n);
		}

		// get the request object for use in all requests from this cache
		request = this.generateKeyedRequest(request, INTEGER_METHOD);
		
		// max single request size, in bits, for adjusting bulk requests later
		int maxRequestSize = (int) Math.ceil(Math.log(max - min + 1)/Math.log(2) * n);
		
		return new RandomOrgCache<int[]>(
				new JsonObjectInputCallable<JsonObject>() {
					@Override
					public JsonObject call() throws RandomOrgSendTimeoutException, 
													RandomOrgKeyNotRunningError, 
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError, 
													RandomOrgBadHTTPResponseException, 
													RandomOrgRANDOMORGError, 
													RandomOrgJSONRPCError, 
													MalformedURLException, 
													IOException {
						return RandomOrgClient.this.sendRequest(this.input);
					}
				}, new JsonObjectInputCallable<int[]>() {
					@Override
					public int[] call() {
						return RandomOrgClient.this.extractInts(this.input);
					}
				},
				request, cacheSize, bulkN, n, maxRequestSize);
	}
	
	/**
	 * Get a RandomOrgCache to obtain random integers. The RandomOrgCache can be polled for 
	 * new results conforming to the output format of the input request. RandomOrgCache type 
	 * is same as expected return value.
	 *
	 * @param n how many random integers you need. Must be within the [1,1e4] range.
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * @param base the base that will be used to display the numbers. Values allowed are 2, 8, 10 
	 *        and 16 (default 10). For base 10, if you would prefer a {@code RandomOrgCache<int[]>} 
	 *        to be returned instead of a {@code RandomOrgCache<String[]>}, please use the {@link 
	 *        #createIntegerCache(int n, int min, int max, boolean replacement, int cacheSize) 
	 *        createIntegerCache} method without the base parameter.
	 * @param cacheSize number of result-sets for the cache to try to maintain at any given time 
	 *        (default 20, minimum 2).
	 *
	 * @return {@code RandomOrgCache<String[]>}
	 */
	public RandomOrgCache<String[]> createIntegerCache(int n, int min, int max, boolean replacement, int base, int cacheSize) {
		if (cacheSize < 2) {
			cacheSize = 2;
		}

		JsonObject request = new JsonObject();
		
		request.addProperty("min", min);
		request.addProperty("max", max);
		request.addProperty("replacement", replacement);
		request.addProperty("base", base);

		int bulkN = 0;

		// If possible, make requests more efficient by bulk-ordering from the server. 
		// initially set at cache_size/2, but cache will auto-shrink bulk request size 
		// if requests can't be fulfilled.
		if (replacement) {
			bulkN = cacheSize/2;
			request.addProperty("n", bulkN*n);

		// not possible to make the request more efficient
		} else {
			request.addProperty("n", n);
		}

		// get the request object for use in all requests from this cache
		request = this.generateKeyedRequest(request, INTEGER_METHOD);
		
		// max single request size, in bits, for adjusting bulk requests later
		int maxRequestSize = (int) Math.ceil(Math.log(max - min + 1)/Math.log(2) * n);
		
		return new RandomOrgCache<String[]>(
				new JsonObjectInputCallable<JsonObject>() {
					@Override
					public JsonObject call() throws RandomOrgSendTimeoutException, 
													RandomOrgKeyNotRunningError, 
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError, 
													RandomOrgBadHTTPResponseException, 
													RandomOrgRANDOMORGError, 
													RandomOrgJSONRPCError, 
													MalformedURLException, 
													IOException {
						return RandomOrgClient.this.sendRequest(this.input);
					}
				}, new JsonObjectInputCallable<String[]>() {
					@Override
					public String[] call() {
						return RandomOrgClient.this.extractStrings(this.input);
					}
				},
				request, cacheSize, bulkN, n, maxRequestSize);
	}
	
	/**
	 * Get a RandomOrgCache to obtain random integer sequences. The RandomOrgCache can be polled 
	 * for new results conforming to the output format of the input request. RandomOrgCache 
	 * type is same as expected return value.
	 *
	 * @param n how many random integers you need. Must be within the [1,1e4] range.
	 * @param length the length of each array of random integers requested. Must be within 
	 *        the [1,1e4] range. 
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range. 
	 *
	 * @return {@code RandomOrgCache<int[][]>}
	 */
	public RandomOrgCache<int[][]> createIntegerSequenceCache(int n, int length, int min, int max) {
		return this.createIntegerSequenceCache(n, length, min, max, DEFAULT_REPLACEMENT, DEFAULT_CACHE_SIZE_SMALL);
	}
	
	/**
	 * Get a RandomOrgCache to obtain random integer sequences. The RandomOrgCache can be polled 
	 * for new results conforming to the output format of the input request. RandomOrgCache 
	 * type is same as expected return value.
	 *
	 * @param n how many random integers you need. Must be within the [1,1e4] range.
	 * @param length the length of each array of random integers requested. Must be within 
	 *        the [1,1e4] range. 
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default True).
	 * @param cacheSize number of result-sets for the cache to try to maintain at any given 
	 *        time (default 10, minimum 2). 
	 *
	 * @return {@code RandomOrgCache<int[][]>}
	 */
	public RandomOrgCache<int[][]> createIntegerSequenceCache(int n, int length, int min, int max, boolean replacement, int cacheSize) {
		if (cacheSize < 2) {
			cacheSize = 2;
		}

		JsonObject request = new JsonObject();
		
		request.addProperty("length", length);
		request.addProperty("min", min);
		request.addProperty("max", max);
		request.addProperty("replacement", replacement);			

		int bulkN = 0;

		// If possible, make requests more efficient by bulk-ordering from the server. 
		// initially set at cache_size/2, but cache will auto-shrink bulk request size 
		// if requests can't be fulfilled.
		if (replacement) {
			bulkN = cacheSize/2;
			request.addProperty("n", bulkN*n);

		// not possible to make the request more efficient
		} else {
			request.addProperty("n", n);
		}

		// get the request object for use in all requests from this cache
		request = this.generateKeyedRequest(request, INTEGER_SEQUENCE_METHOD);
		
		// max single request size, in bits, for adjusting bulk requests later
		int maxRequestSize = (int) Math.ceil(Math.log(max - min + 1)/Math.log(2) * n);
		
		return new RandomOrgCache<int[][]>(
				new JsonObjectInputCallable<JsonObject>() {
					@Override
					public JsonObject call() throws RandomOrgSendTimeoutException, 
													RandomOrgKeyNotRunningError, 
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError, 
													RandomOrgBadHTTPResponseException, 
													RandomOrgRANDOMORGError, 
													RandomOrgJSONRPCError, 
													MalformedURLException, 
													IOException {
						return RandomOrgClient.this.sendRequest(this.input);
					}
				}, new JsonObjectInputCallable<int[][]>() {
					@Override
					public int[][] call() {
						return RandomOrgClient.this.extractIntSequences(this.input);
					}
				},
				request, cacheSize, bulkN, n, maxRequestSize);
	}
	
	/**
	 * Get a RandomOrgCache to obtain random integer sequences. The RandomOrgCache can be polled 
	 * for new results conforming to the output format of the input request. RandomOrgCache 
	 * type is same as expected return value.
	 *
	 * @param n how many random integers you need. Must be within the [1,1e4] range.
	 * @param length the length of each array of random integers requested. Must be within 
	 *        the [1,1e4] range. 
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default True).
	 * @param base the base that will be used to display the numbers. Values allowed are 2, 
	 *        8, 10 and 16 (default 10). For base 10, if you would prefer a {@code 
	 *        RandomOrgCache<int[][]>} to be returned instead of a {@code RandomOrgCache<String[][]>}, 
	 *        please use the {@link #createIntegerSequenceCache(int n, int length, int min, 
	 *        int max, boolean replacement, int cacheSize) createIntegerSequenceCache} method 
	 *        without the base parameter.
	 * @param cacheSize number of result-sets for the cache to try to maintain at any given 
	 *        time (default 10, minimum 2). 
	 *
	 * @return {@code RandomOrgCache<String[][]>}
	 */
	public RandomOrgCache<String[][]> createIntegerSequenceCache(int n, int length, int min, int max, boolean replacement, int base, int cacheSize) {
		if (cacheSize < 2) {
			cacheSize = 2;
		}

		JsonObject request = new JsonObject();
		
		request.addProperty("length", length);
		request.addProperty("min", min);
		request.addProperty("max", max);
		request.addProperty("replacement", replacement);	
		request.addProperty("base", base);

		int bulkN = 0;

		// If possible, make requests more efficient by bulk-ordering from the server. 
		// initially set at cache_size/2, but cache will auto-shrink bulk request size if requests can't be fulfilled.
		if (replacement) {
			bulkN = cacheSize/2;
			request.addProperty("n", bulkN*n);

		// not possible to make the request more efficient
		} else {
			request.addProperty("n", n);
		}

		// get the request object for use in all requests from this cache
		request = this.generateKeyedRequest(request, INTEGER_SEQUENCE_METHOD);
		
		// max single request size, in bits, for adjusting bulk requests later
		int maxRequestSize = (int) Math.ceil(Math.log(max - min + 1)/Math.log(2) * n);
		
		return new RandomOrgCache<String[][]>(
				new JsonObjectInputCallable<JsonObject>() {
					@Override
					public JsonObject call() throws RandomOrgSendTimeoutException, 
													RandomOrgKeyNotRunningError, 
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError, 
													RandomOrgBadHTTPResponseException, 
													RandomOrgRANDOMORGError, 
													RandomOrgJSONRPCError, 
													MalformedURLException, 
													IOException {
						return RandomOrgClient.this.sendRequest(this.input);
					}
				}, new JsonObjectInputCallable<String[][]>() {
					@Override
					public String[][] call() {
						return RandomOrgClient.this.extractIntSequencesString(this.input);
					}
				},
				request, cacheSize, bulkN, n, maxRequestSize);
	}
	
	/**
	 * Get a RandomOrgCache to obtain random integer sequences. The RandomOrgCache can be 
	 * polled for new results conforming to the output format of the input request. RandomOrgCache 
	 * type is same as expected return value.
	 *
	 * @param n how many random integers you need. Must be within the 
	 *        [1,1e4] range.
	 * @param length an array with n integers each specifying the length of the sequence 
	 *        identified by its index. Each value in the array must be within the [1,1e4] range.
	 * @param min an array with n integers, each specifying the lower boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 * @param max an array with n integers, each specifying the upper boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 *
	 * @return {@code RandomOrgCache<int[][]>}
	 */
	public RandomOrgCache<int[][]> createIntegerSequenceCache(int n, int[] length, int[] min, int[] max) {
		boolean[] replacement = new boolean[n];
		Arrays.fill(replacement, DEFAULT_REPLACEMENT);
		return createIntegerSequenceCache(n, length, min, max, replacement, DEFAULT_CACHE_SIZE_SMALL);
	}

	/**
	 * Get a RandomOrgCache to obtain random integer sequences. The RandomOrgCache can be polled 
	 * for new results conforming to the output format of the input request. RandomOrgCache 
	 * type is same as expected return value.
	 *
	 * @param n how many random integers you need. Must be within the 
	 *        [1,1e4] range.
	 * @param length an array with n integers each specifying the length of the sequence 
	 *        identified by its index. Each value in the array must be within the [1,1e4] range.
	 * @param min an array with n integers, each specifying the lower boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 * @param max an array with n integers, each specifying the upper boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 * @param replacement an array with n boolean values, each specifying whether the sequence 
	 *        identified by its index will be created with or without replacement. If true, the 
	 *        resulting numbers may contain duplicate values, otherwise the numbers will all be 
	 *        unique within each sequence (default boolean[n] with all values set to true).
	 * @param cacheSize number of result-sets for the cache to try to maintain 
	 *        at any given time (default 10, minimum 2).
	 *
	 * @return {@code RandomOrgCache<int[][]>}
	 */
	public RandomOrgCache<int[][]> createIntegerSequenceCache(int n, int[] length, int[] min, int[] max, boolean[] replacement, int cacheSize) {
		if (cacheSize < 2) {
			cacheSize = 2;
		}

		boolean[] defaultReplacement = new boolean[replacement.length];
		Arrays.fill(defaultReplacement, true);	
		
		JsonObject request = new JsonObject();

		int bulkN = 0;

		// If possible, make requests more efficient by bulk-ordering from the server. 
		// initially set at cache_size/2, but cache will auto-shrink bulk request size 
		// if requests can't be fulfilled.
		if (Arrays.equals(replacement, defaultReplacement)) {
			bulkN = cacheSize/2;
			
			request.addProperty("n", bulkN*n);
			
			length = adjust(length, bulkN*n);
			min = adjust(min, bulkN*n);
			max = adjust(max, bulkN*n);
			replacement = adjust(replacement, bulkN*n);

		// not possible to make the request more efficient
		} else {
			request.addProperty("n", n);
		}	
		
		request.add("length", gson.toJsonTree(length));
		request.add("min", gson.toJsonTree(min));
		request.add("max", gson.toJsonTree(max));
		request.add("replacement", gson.toJsonTree(replacement));		

		// get the request object for use in all requests from this cache
		request = this.generateKeyedRequest(request, INTEGER_SEQUENCE_METHOD);
		
		// max single request size, in bits, for adjusting bulk requests later
		
		
		int maxRequestSize = (int) Math.ceil(Math.log(max(max) - min(min) + 1)/Math.log(2) * n * max(length));
		
		return new RandomOrgCache<int[][]>(
				new JsonObjectInputCallable<JsonObject>() {
					@Override
					public JsonObject call() throws RandomOrgSendTimeoutException, 
													RandomOrgKeyNotRunningError, 
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError, 
													RandomOrgBadHTTPResponseException, 
													RandomOrgRANDOMORGError, 
													RandomOrgJSONRPCError, 
													MalformedURLException, 
													IOException {
						return RandomOrgClient.this.sendRequest(this.input);
					}
				}, new JsonObjectInputCallable<int[][]>() {
					@Override
					public int[][] call() {
						return RandomOrgClient.this.extractIntSequences(this.input);
					}
				},
				request, cacheSize, bulkN, n, maxRequestSize);
	}
	
	/**
	 * Get a RandomOrgCache to obtain random integer sequences. The RandomOrgCache can be polled 
	 * for new results conforming to the output format of the input request. RandomOrgCache 
	 * type is same as expected return value.
	 *
	 * @param n how many random integers you need. Must be within the 
	 *        [1,1e4] range.
	 * @param length an array with n integers each specifying the length of the sequence 
	 *        identified by its index. Each value in the array must be within the [1,1e4] range.
	 * @param min an array with n integers, each specifying the lower boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 * @param max an array with n integers, each specifying the upper boundary of the sequence 
	 *        identified by its index. Each value in the array must be within the [-1e9,1e9] range.
	 * @param replacement an array with n boolean values, each specifying whether the sequence 
	 *        identified by its index will be created with or without replacement. If true, the 
	 *        resulting numbers may contain duplicate values, otherwise the numbers will all be 
	 *        unique within each sequence (default boolean[n] with all values set to true).
	 * @param base an array with n integer values, each specifying the base that will be used to 
	 *        display the sequence identified by its index. Values allowed are 2, 8, 10 and 16 
	 *        (default 10). For base 10, if you require an {@code RandomOrgCache<int[][]>} instead of 
	 *        a {@code RandomOrgCache<String[][]>}, please use the {@link #createIntegerSequenceCache(
	 *        int n, int[] length, int[] min, int[] max, boolean[] replacement, int cacheSize) 
	 *        createIntegerSequenceCache} method without the base parameter.
	 * @param cacheSize number of result-sets for the cache to try to maintain 
	 *        at any given time (default 10, minimum 2).
	 *
	 * @return {@code RandomOrgCache<String[][]>}
	 */
	public RandomOrgCache<String[][]> createIntegerSequenceCache(int n, int[] length, int[] min, int[] max, boolean[] replacement, int[] base, int cacheSize) {
		if (cacheSize < 2) {
			cacheSize = 2;
		}

		boolean[] defaultReplacement = new boolean[replacement.length];
		Arrays.fill(defaultReplacement, true);
		
		JsonObject request = new JsonObject();
				
		int bulkN = 0;

		// If possible, make requests more efficient by bulk-ordering from the server. 
		// initially set at cache_size/2, but cache will auto-shrink bulk request size 
		// if requests can't be fulfilled.
		if (Arrays.equals(replacement, defaultReplacement)) {
			bulkN = cacheSize/2;
			
			request.addProperty("n", bulkN*n);
			
			length = adjust(length, bulkN*n);
			min = adjust(min, bulkN*n);
			max = adjust(max, bulkN*n);
			replacement = adjust(replacement, bulkN*n);
			base = adjust(base, bulkN*n);

		// not possible to make the request more efficient
		} else {
			request.addProperty("n", n);
		}		
		
		request.add("length", gson.toJsonTree(length));
		request.add("min", gson.toJsonTree(min));
		request.add("max", gson.toJsonTree(max));
		request.add("replacement", gson.toJsonTree(replacement));
		request.add("base", gson.toJsonTree(base));

		// get the request object for use in all requests from this cache
		request = this.generateKeyedRequest(request, INTEGER_SEQUENCE_METHOD);
		
		// max single request size, in bits, for adjusting bulk requests later
		
		
		int maxRequestSize = (int) Math.ceil(Math.log(max(max) - min(min) + 1)/Math.log(2) * n * max(length));
		
		return new RandomOrgCache<String[][]>(
				new JsonObjectInputCallable<JsonObject>() {
					@Override
					public JsonObject call() throws RandomOrgSendTimeoutException, 
													RandomOrgKeyNotRunningError, 
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError, 
													RandomOrgBadHTTPResponseException, 
													RandomOrgRANDOMORGError, 
													RandomOrgJSONRPCError, 
													MalformedURLException, 
													IOException {
						return RandomOrgClient.this.sendRequest(this.input);
					}
				}, new JsonObjectInputCallable<String[][]>() {
					@Override
					public String[][] call() {
						return RandomOrgClient.this.extractIntSequencesString(this.input);
					}
				},
				request, cacheSize, bulkN, n, maxRequestSize);
	}
	
	/** 
	 * Get a RandomOrgCache to obtain random decimal fractions. The RandomOrgCache can be polled 
	 * for new results conforming to the output format of the input request. RandomOrgCache type 
	 * is same as expected return value.
	 *
	 * @param n how many random decimal fractions you need. Must be within the [1,1e4] range.
	 * @param decimalPlaces the number of decimal places to use. Must be within the [1,20] range.
	 *
	 * @return {@code RandomOrgCache<double[]>}
	 */
	public RandomOrgCache<double[]> createDecimalFractionCache(int n, int decimalPlaces) {
		return this.createDecimalFractionCache(n, decimalPlaces, DEFAULT_REPLACEMENT, DEFAULT_CACHE_SIZE);
	}

	/** 
	 * Get a RandomOrgCache to obtain random decimal fractions. The RandomOrgCache can be polled 
	 * for new results conforming to the output format of the input request. RandomOrgCache type 
	 * is same as expected return value.
	 *
	 * @param n how many random decimal fractions you need. Must be within the [1,1e4] range.
	 * @param decimalPlaces the number of decimal places to use. Must be within the [1,20] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *		  If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * @param cacheSize number of result-sets for the cache to try to maintain at any given time 
	 *        (default 20, minimum 2).
	 *
	 * @return {@code RandomOrgCache<double[]>}
	 */
	public RandomOrgCache<double[]> createDecimalFractionCache(int n, int decimalPlaces, boolean replacement, int cacheSize) {
		if (cacheSize < 2) {
			cacheSize = 2;
		}

		JsonObject request = new JsonObject();
		
		request.addProperty("decimalPlaces", decimalPlaces);
		request.addProperty("replacement", replacement);
		
		int bulkN = 0;

		// If possible, make requests more efficient by bulk-ordering from the server. 
		// initially set at cache_size/2, but cache will auto-shrink bulk request size 
		// if requests can't be fulfilled.
		if (replacement) {
			bulkN = cacheSize/2;
			request.addProperty("n", bulkN*n);

		// not possible to make the request more efficient
		} else {
			request.addProperty("n", n);
		}

		// get the request object for use in all requests from this cache
		request = this.generateKeyedRequest(request, DECIMAL_FRACTION_METHOD);
		
		// max single request size, in bits, for adjusting bulk requests later
		int maxRequestSize = (int) Math.ceil(Math.log(10)/Math.log(2) * decimalPlaces * n);

		return new RandomOrgCache<double[]>(
				new JsonObjectInputCallable<JsonObject>() {
					@Override
					public JsonObject call() throws RandomOrgSendTimeoutException, 
													RandomOrgKeyNotRunningError, 
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError, 
													RandomOrgBadHTTPResponseException, 
													RandomOrgRANDOMORGError, 
													RandomOrgJSONRPCError, 
													MalformedURLException, 
													IOException {
						return RandomOrgClient.this.sendRequest(this.input);
					}
				}, new JsonObjectInputCallable<double[]>() {
					@Override
					public double[] call() {
						return RandomOrgClient.this.extractDoubles(this.input);
					}
				},
				request, cacheSize, bulkN, n, maxRequestSize);
	}

	/** 
	 * Get a RandomOrgCache to obtain random numbers. The RandomOrgCache can be polled for new 
	 * results conforming to the output format of the input request. RandomOrgCache type is same 
	 * as expected return value.
	 *
	 * @param n how many random numbers you need. Must be within the [1,1e4] range.
	 * @param mean the distribution's mean. Must be within the [-1e6,1e6] range.
	 * @param standardDeviation the distribution's standard deviation. Must be within the 
	 *        [-1e6,1e6] range.
	 * @param significantDigits the number of significant digits to use. Must be within the 
	 *        [2,20] range.
	 *
	 * @return {@code RandomOrgCache<double[]>}
	 */
	public RandomOrgCache<double[]> createGaussianCache(int n, double mean, double standardDeviation, int significantDigits) {
		return this.createGaussianCache(n, mean, standardDeviation, significantDigits, DEFAULT_CACHE_SIZE);
	}

	/** 
	 * Get a RandomOrgCache to obtain random numbers. The RandomOrgCache can be polled for new 
	 * results conforming to the output format of the input request. RandomOrgCache type is 
	 * same as expected return value.
	 *
	 * @param n how many random numbers you need. Must be within the [1,1e4] range.
	 * @param mean the distribution's mean. Must be within the [-1e6,1e6] range.
	 * @param standardDeviation the distribution's standard deviation. Must be within the 
	 *        [-1e6,1e6] range.
	 * @param significantDigits the number of significant digits to use. Must be within the 
	 *        [2,20] range.
	 * @param cacheSize number of result-sets for the cache to try to maintain at any given time 
	 *        (default 20, minimum 2).
	 *
	 * @return {@code RandomOrgCache<double[]>}
	 */
	public RandomOrgCache<double[]> createGaussianCache(int n, double mean, double standardDeviation, int significantDigits, int cacheSize) {
		if (cacheSize < 2) {
			cacheSize = 2;
		}

		JsonObject request = new JsonObject();
		
		request.addProperty("mean", mean);
		request.addProperty("standardDeviation", standardDeviation);
		request.addProperty("significantDigits", significantDigits);
		
		int bulkN = 0;

		// make requests more efficient by bulk-ordering from the server. 
		// initially set at cache_size/2, but cache will auto-shrink bulk request size if 
		// requests can't be fulfilled.
		bulkN = cacheSize/2;
		request.addProperty("n", bulkN*n);

		// get the request object for use in all requests from this cache
		request = this.generateKeyedRequest(request, GAUSSIAN_METHOD);
		
		// max single request size, in bits, for adjusting bulk requests later
		int maxRequestSize = (int) Math.ceil(Math.log(Math.pow(10, significantDigits))/Math.log(2) * n);

		return new RandomOrgCache<double[]>(
				new JsonObjectInputCallable<JsonObject>() {
					@Override
					public JsonObject call() throws RandomOrgSendTimeoutException, 
													RandomOrgKeyNotRunningError, 
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError, 
													RandomOrgBadHTTPResponseException, 
													RandomOrgRANDOMORGError, 
													RandomOrgJSONRPCError, 
													MalformedURLException, 
													IOException {
						return RandomOrgClient.this.sendRequest(this.input);
					}
				}, new JsonObjectInputCallable<double[]>() {
					@Override
					public double[] call() {
						return RandomOrgClient.this.extractDoubles(this.input);
					}
				},
				request, cacheSize, bulkN, n, maxRequestSize);
	}

	/** 
	 * Get a RandomOrgCache to obtain random strings. The RandomOrgCache can be polled for new 
	 * results conforming to the output format of the input request. RandomOrgCache type is 
	 * same as expected return value.
	 *
	 * @param n how many random strings you need. Must be within the [1,1e4] range.
	 * @param length the length of each string. Must be within the [1,20] range. All strings will 
	 *        be of the same length.
	 * @param characters a string that contains the set of characters that are allowed to occur 
	 *        in the random strings. The maximum number of characters is 80.
	 *
	 * @return {@code RandomOrgCache<String[]>}
	 */
	public RandomOrgCache<String[]> createStringCache(int n, int length, String characters) {
		return this.createStringCache(n, length, characters, DEFAULT_REPLACEMENT, DEFAULT_CACHE_SIZE);
	}
	
	/** 
	 * Get a RandomOrgCache to obtain random strings. The RandomOrgCache can be polled for new 
	 * results conforming to the output format of the input request. RandomOrgCache type is 
	 * same as expected return value.
	 *
	 * @param n how many random strings you need. Must be within the [1,1e4] range.
	 * @param length the length of each string. Must be within the [1,20] range. All strings 
	 *        will be of the same length.
	 * @param characters a string that contains the set of characters that are allowed to occur 
	 *        in the random strings. The maximum number of characters is 80.
	 * @param replacement specifies whether the random strings should be picked with replacement. 
	 *        If true, the resulting list of strings may contain duplicates, otherwise the strings 
	 *        will all be unique (default true).
	 * @param cacheSize number of result-sets for the cache to try to maintain at any given time 
	 *        (default 20, minimum 2).
	 *
	 * @return {@code RandomOrgCache<String[]>}
	 */
	public RandomOrgCache<String[]> createStringCache(int n, int length, String characters, boolean replacement, int cacheSize) {
		if (cacheSize < 2) {
			cacheSize = 2;
		}

		JsonObject request = new JsonObject();
		
		request.addProperty("length", length);
		request.addProperty("characters", characters);
		request.addProperty("replacement", replacement);
		
		int bulkN = 0;

		// If possible, make requests more efficient by bulk-ordering from the server. 
		// initially set at cache_size/2, but cache will auto-shrink bulk request size 
		// if requests can't be fulfilled.
		if (replacement) {
			bulkN = cacheSize/2;
			request.addProperty("n", bulkN*n);

		// not possible to make the request more efficient
		} else {
			request.addProperty("n", n);
		}

		// get the request object for use in all requests from this cache
		request = this.generateKeyedRequest(request, STRING_METHOD);
		
		// max single request size, in bits, for adjusting bulk requests later
		int maxRequestSize = (int) Math.ceil(Math.log(characters.length())/Math.log(2) * length * n);

		return new RandomOrgCache<String[]>(
				new JsonObjectInputCallable<JsonObject>() {
					@Override
					public JsonObject call() throws RandomOrgSendTimeoutException, 
													RandomOrgKeyNotRunningError, 
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError, 
													RandomOrgBadHTTPResponseException, 
													RandomOrgRANDOMORGError, 
													RandomOrgJSONRPCError, 
													MalformedURLException, 
													IOException {
						return RandomOrgClient.this.sendRequest(this.input);
					}
				}, new JsonObjectInputCallable<String[]>() {
					@Override
					public String[] call() {
						return RandomOrgClient.this.extractStrings(this.input);
					}
				},
				request, cacheSize, bulkN, n, maxRequestSize);
	}

	/** 
	 * Get a RandomOrgCache to obtain UUIDs. The RandomOrgCache can be polled for new results 
	 * conforming to the output format of the input request. RandomOrgCache type is same as 
	 * expected return value.
	 *
	 * @param n how many random UUIDs you need. Must be within the [1,1e3] range.
	 *
	 * @return {@code RandomOrgCache<UUID[]>}
	 */
	public RandomOrgCache<UUID[]> createUUIDCache(int n) {
		return this.createUUIDCache(n, DEFAULT_CACHE_SIZE_SMALL);
	}
	
	/** 
	 * Get a RandomOrgCache to obtain UUIDs. The RandomOrgCache can be polled for new results 
	 * conforming to the output format of the input request. RandomOrgCache type is same as 
	 * expected return value.
	 *
	 * @param n how many random UUIDs you need. Must be within the [1,1e3] range.
	 * @param cacheSize number of result-sets for the cache to try to maintain at any given time
	 *        (default 10, minimum 2).
	 *
	 * @return {@code RandomOrgCache<UUID[]>}
	 */
	public RandomOrgCache<UUID[]> createUUIDCache(int n, int cacheSize) {
		if (cacheSize < 2) {
			cacheSize = 2;
		}

		JsonObject request = new JsonObject();
		
		int bulkN = 0;

		// make requests more efficient by bulk-ordering from the server. 
		// initially set at cache_size/2, but cache will auto-shrink bulk request size if 
		// requests can't be fulfilled.
		bulkN = cacheSize/2;
		request.addProperty("n", bulkN*n);

		// get the request object for use in all requests from this cache
		request = this.generateKeyedRequest(request, UUID_METHOD);
		
		// max single request size, in bits, for adjusting bulk requests later
		int maxRequestSize = n*UUID_SIZE;

		return new RandomOrgCache<UUID[]>(
				new JsonObjectInputCallable<JsonObject>() {
					@Override
					public JsonObject call() throws RandomOrgSendTimeoutException, 
													RandomOrgKeyNotRunningError, 
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError, 
													RandomOrgBadHTTPResponseException, 
													RandomOrgRANDOMORGError, 
													RandomOrgJSONRPCError, 
													MalformedURLException, 
													IOException {
						return RandomOrgClient.this.sendRequest(this.input);
					}
				}, new JsonObjectInputCallable<UUID[]>() {
					@Override
					public UUID[] call() {
						return RandomOrgClient.this.extractUUIDs(this.input);
					}
				},
				request, cacheSize, bulkN, n, maxRequestSize);
	}

	/** 
	 * Get a RandomOrgCache to obtain random blobs. The RandomOrgCache can be polled for new 
	 * results conforming to the output format of the input request. RandomOrgCache type is 
	 * same as expected return value.
	 *
	 * @param n how many random blobs you need. Must be within the [1,20] range.
	 * @param size the size of each blob, measured in bits. Must be within the [1,1048576] range 
	 *        and must be divisible by 8.
	 *
	 * @return {@code RandomOrgCache<String[]>}
	 */
	public RandomOrgCache<String[]> createBlobCache(int n, int size) {
		return this.createBlobCache(n, size, BLOB_FORMAT_BASE64, DEFAULT_CACHE_SIZE_SMALL);
	}
	
	/** 
	 * Get a RandomOrgCache to obtain random blobs. The RandomOrgCache can be polled for new 
	 * results conforming to the output format of the input request. RandomOrgCache type is 
	 * same as expected return value.
	 *
	 * @param n how many random blobs you need. {@code n*(cacheSize/2)} must be within the [1,100] range.
	 * @param size the size of each blob, measured in bits. Must be within the [1,1048576] range 
	 *        and must be divisible by 8.
	 * @param format specifies the format in which the blobs will be returned. Values allowed are 
	 * 		  BLOB_FORMAT_BASE64 and BLOB_FORMAT_HEX (default BLOB_FORMAT_BASE64).
	 * @param cacheSize number of result-sets for the cache to try to maintain at any given time 
	 *        (default 10, minimum 2).
	 *
	 * @return {@code RandomOrgCache<String[]>}
	 */
	public RandomOrgCache<String[]> createBlobCache(int n, int size, String format, int cacheSize) {
		if (cacheSize < 2) {
			cacheSize = 2;
		}

		JsonObject request = new JsonObject();

		request.addProperty("size", size);
		request.addProperty("format", format);

		int bulkN = 0;

		// make requests more efficient by bulk-ordering from the server. 
		// initially set at cache_size/2, but cache will auto-shrink bulk request size 
		// if requests can't be fulfilled.
		bulkN = cacheSize/2;
		request.addProperty("n", bulkN*n);

		// get the request object for use in all requests from this cache
		request = this.generateKeyedRequest(request, BLOB_METHOD);
		
		// max single request size, in bits, for adjusting bulk requests later
		int maxRequestSize = n*size;
				
		return new RandomOrgCache<String[]>(
				new JsonObjectInputCallable<JsonObject>() {
					@Override
					public JsonObject call() throws RandomOrgSendTimeoutException, 
													RandomOrgKeyNotRunningError, 
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError, 
													RandomOrgBadHTTPResponseException, 
													RandomOrgRANDOMORGError, 
													RandomOrgJSONRPCError, 
													MalformedURLException, 
													IOException {
						return RandomOrgClient.this.sendRequest(this.input);
					}
				}, new JsonObjectInputCallable<String[]>() {
					@Override
					public String[] call() {
						return RandomOrgClient.this.extractStrings(this.input);
					}
				},
				request, cacheSize, bulkN, n, maxRequestSize);
	}
	
	// Methods for accessing server usage statistics.
	
	/** 
	 * Return the (estimated) number of remaining API requests available to the client. If 
	 * cached usage info is older than ALLOWANCE_STATE_REFRESH_SECONDS fresh info is obtained 
	 * from server. If fresh info has to be obtained the following exceptions can be raised.
	 *
	 * @return number of requests remaining.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public int getRequestsLeft() throws RandomOrgSendTimeoutException,
										RandomOrgKeyNotRunningError,
										RandomOrgInsufficientRequestsError, 
										RandomOrgInsufficientBitsError,
										RandomOrgBadHTTPResponseException,
										RandomOrgRANDOMORGError,
										RandomOrgJSONRPCError,
										MalformedURLException,
										IOException {
		if (this.requestsLeft < 0 || System.currentTimeMillis() > (this.lastResponseReceivedTime + RandomOrgClient.ALLOWANCE_STATE_REFRESH_SECONDS)) {
			this.getUsage();
		}
		return this.requestsLeft;
	}
	
	/** 
	 * Return the (estimated) number of remaining true random bits available to the client. 
	 * If cached usage info is older than ALLOWANCE_STATE_REFRESH_SECONDS fresh info is obtained 
	 * from server. If fresh info has to be obtained the following exceptions can be raised.
     *
	 * @return number of bits remaining.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	public int getBitsLeft() throws RandomOrgSendTimeoutException,
									RandomOrgKeyNotRunningError,
									RandomOrgInsufficientRequestsError, 
									RandomOrgInsufficientBitsError,
									RandomOrgBadHTTPResponseException,
									RandomOrgRANDOMORGError,
									RandomOrgJSONRPCError,
									MalformedURLException,
									IOException {
		
		if (this.bitsLeft < 0 || System.currentTimeMillis() > (this.lastResponseReceivedTime + RandomOrgClient.ALLOWANCE_STATE_REFRESH_SECONDS)) {
			this.getUsage();
		}
		return this.bitsLeft;
	}
	
	// Server communications & helper functions.

	/** 
	 * Issue a getUsage request to update bits and requests left.
     * 
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	private void getUsage() throws RandomOrgSendTimeoutException,
								   RandomOrgKeyNotRunningError,
								   RandomOrgInsufficientRequestsError, 
								   RandomOrgInsufficientBitsError,
								   RandomOrgBadHTTPResponseException,
								   RandomOrgRANDOMORGError,
								   RandomOrgJSONRPCError,
								   MalformedURLException,
								   IOException {		
		JsonObject request = new JsonObject();
		
		request = this.generateKeyedRequest(request, GET_USAGE_METHOD);
		
		this.sendRequest(request);
	}

	/** 
	 * Add generic request parameters and API key to custom request.
	 *
	 * @param params custom parameters to generate request around.
	 * @param method to send request to.
	 *
	 * @return fleshed out JSON request.
	 */
	private JsonObject generateKeyedRequest(JsonObject params, String method) {		
		params.addProperty("apiKey", this.apiKey);
		
		JsonObject request = new JsonObject();
		
		request.addProperty("jsonrpc", "2.0");
		request.addProperty("method", method);
		request.add("params", params);
		request.addProperty("id", UUID.randomUUID().toString());
		
		return request;
	}
	
	/**
	 * Add generic request parameters to custom request.
	 *
	 * @param params custom parameters to generate request around.
	 * @param method to send request to.
	 *
	 * @return fleshed out JSON request.
	 */
	private JsonObject generateRequest(JsonObject params, String method) {
		JsonObject request = new JsonObject();
		
		request.addProperty("jsonrpc", "2.0");
		request.addProperty("method", method);
		request.add("params", params);
		request.addProperty("id", UUID.randomUUID().toString());
		
		return request;
	}
	
	/** 
	 * Extracts int[] from JSON response.
	 *
	 * @param response JSON from which to extract data.
	 *
	 * @return extracted int[].
	 */
	protected int[] extractInts(JsonObject response) {		
		JsonArray data = this.extractResponse(response);
		int[] randoms = new int[data.size()];
		
		for (int i = 0; i < randoms.length; i++) {
			randoms[i] = data.get(i).getAsInt();
		}
		
		return randoms;
	}
	
	/**
	 * Extracts int[][] from JSON response.
	 *
	 * @param response JSON from which to extract data.
	 *
	 * @return extracted int[][].
	 */
	protected int[][] extractIntSequences(JsonObject response) {		
		JsonArray data = this.extractResponse(response);
		int[][] randoms = new int[data.size()][];
		
		for (int i = 0; i < randoms.length; i++) {
			randoms[i] = gson.fromJson(data.get(i), int[].class);
		}
		
		return randoms;
	}
	
	/**
	 * Extracts String[][] from JSON response.
	 *
	 * @param response JSON from which to extract data.
	 *
	 * @return extracted String[][].
	 */
	protected String[][] extractIntSequencesString(JsonObject response) {
		JsonArray data = this.extractResponse(response);
		String[][] randoms = new String[data.size()][];
		
		for (int i = 0; i < randoms.length; i++) {
			randoms[i] = gson.fromJson(data.get(i), String[].class);
		}
		
		return randoms;
	}
	
	/** 
	 * Extracts double[] from JSON response.
	 *
	 * @param response JSON from which to extract data.
	 *
	 * @return extracted double[].
	 */
	protected double[] extractDoubles(JsonObject response) {		
		JsonArray data = this.extractResponse(response);
		double[] randoms = new double[data.size()];
		
		for (int i = 0; i < randoms.length; i++) {
			randoms[i] = data.get(i).getAsDouble();
		}
		
		return randoms;
	}
	
	/** 
	 * Extracts String[] from JSON response.
	 *
	 * @param response JSON from which to extract data.
	 *
	 * @return extracted String[].
	 */
	protected String[] extractStrings(JsonObject response) {		
		JsonArray data = this.extractResponse(response);
		String[] randoms = new String[data.size()];
		
		for (int i = 0; i < randoms.length; i++) {
			randoms[i] = data.get(i).getAsString();
		}
		
		return randoms;
	}
	
	/**
	 * Extracts UUID[] from JSON response.
	 *
	 * @param response JSON from which to extract data.
	 *
	 * @return extracted UUID[].
	 */
	protected UUID[] extractUUIDs(JsonObject response) {		
		JsonArray data = this.extractResponse(response);
		UUID[] randoms = new UUID[data.size()];
		
		for (int i = 0; i < randoms.length; i++) {
			randoms[i] = UUID.fromString(data.get(i).getAsString());
		}
		
		return randoms;
	}
	
	/** 
	 * Gets random data as separate from response JSON.
	 *
	 * @param response JSON from which to extract data.
	 *
	 * @return JsonArray of random data.
	 */
	private JsonArray extractResponse(JsonObject response) {
		return response.get("result").getAsJsonObject().get("random").getAsJsonObject().get("data").getAsJsonArray();
	}
	
	/** 
	 * Gets signing data from response JSON and add to result HashMap.
	 *
	 * @param response JSON from which to extract data.
	 * @param result to add signing data to.
	 *
	 * @return the passed in result HashMap.
	 */
	private HashMap<String, Object> extractSignedResponse(JsonObject response, HashMap<String, Object> result) {
		result.put("random", response.get("result").getAsJsonObject().get("random").getAsJsonObject());
		result.put("signature", response.get("result").getAsJsonObject().get("signature").getAsString());
		
		return result;
	}
	
	/** 
	 * Gets verification response as separate from response JSON.
	 *
	 * @param response JSON from which to extract verification response.
	 *
	 * @return verification success.
	 */
	private boolean extractVerificationResponse(JsonObject response) {
		return response.get("result").getAsJsonObject().get("authenticity").getAsBoolean();
	}

	/** 
	 * Send request as determined by serialized boolean.
	 * 
	 * @param request JSON to send.
	 *
	 * @return JsonObject response.
	 * 
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	protected JsonObject sendRequest(JsonObject request) throws RandomOrgSendTimeoutException,
																RandomOrgKeyNotRunningError,
																RandomOrgInsufficientRequestsError, 
																RandomOrgInsufficientBitsError, 
																RandomOrgBadHTTPResponseException,
																RandomOrgRANDOMORGError,
																RandomOrgJSONRPCError,
																MalformedURLException,
																IOException {
		
		return this.serialized ? this.sendSerializedRequest(request) : this.sendUnserializedRequest(request);
	}
	
	/** 
	 * Immediate call to server. Networking is run on a separate thread as Android platform 
	 * disallows networking on the main thread.
	 * 
	 * @param request JSON to send.
	 *
	 * @return JsonObject response.
	 * 
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	private JsonObject sendUnserializedRequest(JsonObject request) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {

		// Send request immediately.
		UnserializedRunnable r = new UnserializedRunnable(request);
		new Thread(r).start();
		
		// Wait for response to arrive.
		while (r.getData() == null) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				LOGGER.log(Level.INFO, "Client interrupted while waiting for server to "
						+ "return a response.");
			}
		}

		// Raise any thrown exceptions.
		if (r.getData().containsKey("exception")) {
			this.throwException((Exception) r.getData().get("exception"));
		}

		// Return response.
		return (JsonObject) r.getData().get("response");
	}
	
	/** Runnable for unserialized network calls. */
	private class UnserializedRunnable implements Runnable {
		
		private JsonObject request;
		private HashMap<String, Object> data;
		
		/** @param request object to send to server. */
		public UnserializedRunnable(JsonObject request) {
			super();
			this.request = request;
		}
		
		/** @see java.lang.Runnable#run() */
		@Override
		public void run() {
			this.data = RandomOrgClient.this.sendRequestCore(this.request);
		}
		
		/** @return data returned by network request - or null if not yet arrived. */
		public HashMap<String, Object> getData() {
			return this.data;			
		}
	}
	
	/** 
	 * Add request to queue to be executed by networking thread one-by-one. Method blocks until 
	 * this request receives a response or times out.
	 * 
	 * @param request JSON to send.
	 *
	 * @return JsonObject response.
	 * 
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	private JsonObject sendSerializedRequest(JsonObject request) 
			throws RandomOrgSendTimeoutException, 
				   RandomOrgKeyNotRunningError, 
				   RandomOrgInsufficientRequestsError, 
				   RandomOrgInsufficientBitsError, 
				   RandomOrgBadHTTPResponseException, 
				   RandomOrgRANDOMORGError, 
				   RandomOrgJSONRPCError, 
				   MalformedURLException, 
				   IOException {
		// Creating request to add to the queue with it's own lock.
		Object requestLock = new Object();

		HashMap<String, Object> data = new HashMap<String, Object>();
		data.put("lock", requestLock);
		data.put("request", request);
		data.put("response", null);
		data.put("exception", null);
		
		// Wait on the lock for the specified blocking timeout.
		synchronized (requestLock) {
		
			// Adding request to the queue
			synchronized (this.serializedQueue) {
				this.serializedQueue.offer(data);
				this.serializedQueue.notify();
			}
		
			try {
				if (this.blockingTimeout == -1) {
					requestLock.wait();
				} else {
					requestLock.wait(this.blockingTimeout);
				}
			} catch (InterruptedException e) {
				LOGGER.log(Level.INFO, "Client interrupted while waiting for request to "
						+ "be sent.");
			}

			// Lock has now either been notified or timed out. Examine data to determine 
			// which and react accordingly.
			
			// Request wasn't sent in time, cancel and raise exception.
			if (data.get("response") == null && data.get("exception") == null) {
				data.put("request", null);
				throw new RandomOrgSendTimeoutException("The maximum allowed blocking time of " 
							+ this.blockingTimeout + "millis has been exceeded while waiting "
							+ "for a synchronous request to send.");
			}
			
			// Exception on sending request.
			if (data.get("exception") != null) {
				this.throwException((Exception) data.get("exception"));
			}

			// Request was successful.
			return (JsonObject) data.get("response");
		}
	}
	
	/** Thread to synchronously send requests in queue. */
	protected void threadedRequestSending() {		
		// Thread to execute queued requests.
		while (true) {
			
			HashMap<String, Object> request;
			synchronized (this.serializedQueue) {
				// Block and wait for a request.
				if (this.serializedQueue.isEmpty()) {
					try {
						this.serializedQueue.wait();
					} catch (InterruptedException e) {
						LOGGER.log(Level.INFO, "Client thread interrupted while waiting "
								+ "for a request to send.");
					}
				}
				
				request = this.serializedQueue.pop();
			}
			
			
			// Get the request's lock to indicate request in progress.
			synchronized (request.get("lock")) {
				
				// If request still exists it hasn't been cancelled.
				if (request.get("request") != null) {
					
					// Send request.
					HashMap<String, Object> data = this.sendRequestCore((JsonObject) request.get("request"));
					
					// Set result.
					if (data.containsKey("exception")) {
						request.put("exception", data.get("exception"));
					} else {
						request.put("response", data.get("response"));
					}
				}

				// Notify completion and return
				request.get("lock").notify();
			}
		}
	}
	
	/** 
	 * Throw specific Exception types.
	 *
	 * @param e exception to throw.
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	private void throwException(Exception e) throws RandomOrgSendTimeoutException,
													RandomOrgKeyNotRunningError,
													RandomOrgInsufficientRequestsError, 
													RandomOrgInsufficientBitsError,
													RandomOrgBadHTTPResponseException,
													RandomOrgRANDOMORGError,
													RandomOrgJSONRPCError,
													MalformedURLException,
													IOException {
		
		if (e.getClass() == RandomOrgSendTimeoutException.class) {
			throw (RandomOrgSendTimeoutException) e;
		} else if (e.getClass() == RandomOrgKeyNotRunningError.class) {
			throw (RandomOrgKeyNotRunningError) e;
		} else if (e.getClass() == RandomOrgInsufficientRequestsError.class) {
			throw (RandomOrgInsufficientRequestsError) e;
		} else if (e.getClass() == RandomOrgInsufficientBitsError.class) {
			throw (RandomOrgInsufficientBitsError) e;
		} else if (e.getClass() == RandomOrgBadHTTPResponseException.class) {
			throw (RandomOrgBadHTTPResponseException) e;
		} else if (e.getClass() == RandomOrgRANDOMORGError.class) {
			throw (RandomOrgRANDOMORGError) e;
		} else if (e.getClass() == RandomOrgJSONRPCError.class) {
			throw (RandomOrgJSONRPCError) e;
		} else if (e.getClass() == MalformedURLException.class) {
			throw (MalformedURLException) e;
		} else if (e.getClass() == IOException.class) {
			throw (IOException) e;
		}
	}
	
	/** 
	 * Core send request function.
	 * 
	 * @param request JSON to send.
	 *
	 * @return info on request success/response in a HashMap with one or other of the following entries:
	 *				"exception" : Exception - exception thrown, possible exception types:
	 *											RandomOrgSendTimeoutException
	 *											RandomOrgKeyNotRunningError
	 *											RandomOrgInsufficientRequestsError
	 *											RandomOrgInsufficientBitsError
	 * 											RandomOrgBadHTTPResponseException
	 * 											RandomOrgRANDOMORGError
	 * 											RandomOrgJSONRPCError
	 * 											MalformedURLException
	 *											IOException
	 *				"response"	: JsonObject - response
	 */
	protected HashMap<String, Object> sendRequestCore(JsonObject request) {
		
		HashMap<String, Object> ret = new HashMap<String, Object>();
		
		// If a back-off is set, no more requests can be issued until the required 
		// back-off time is up.
		if (this.backoff != -1) {
			
			// Time not yet up, throw exception.
			if (System.currentTimeMillis() < this.backoff) {
				ret.put("exception", new RandomOrgInsufficientRequestsError(this.backoffError));
				return ret;
			// Time is up, clear back-off.
			} else {
				this.backoff = -1;
				this.backoffError = null;
			}
		}
		
		long wait = 0;
		
		// Check server advisory delay.
		synchronized (this.advisoryDelayLock) {
			wait = this.advisoryDelay - (System.currentTimeMillis() - this.lastResponseReceivedTime);
		}

		// Wait the specified delay if necessary and if wait time is not longer than the 
		// set blocking timeout.
		if (wait > 0) {
			if (this.blockingTimeout != -1 && wait > this.blockingTimeout) {
				ret.put("exception", new RandomOrgSendTimeoutException("The server advisory delay of " 
							+ wait + "millis is greater than the defined maximum allowed "
							+ "blocking time of " + this.blockingTimeout + "millis."));
				return ret;
			}
			try {
				Thread.sleep(wait);
			} catch (InterruptedException e) {
				LOGGER.log(Level.INFO, "Client interrupted while waiting for server "
						+ "mandated blocking time.");
			}
		}
		
		JsonObject response;
		
		// Send the request
		try {
			response = this.post(request);
		} catch (MalformedURLException e) {
			ret.put("exception", e);
			return ret;
		} catch (RandomOrgBadHTTPResponseException e) {
			ret.put("exception", e);
			return ret;
		} catch (IOException e) {
			ret.put("exception", e);
			return ret;
		}

		// Parse the response.
		
		// Has error?
		if (response.has("error")) {
			JsonObject error = response.get("error").getAsJsonObject();

			int code = error.get("code").getAsInt();
			String message = error.get("message").getAsString();
			
			// RandomOrgAllowanceExceededError, API key not running, backoff until midnight UTC, 
			// from RANDOM.ORG Errors: https://api.random.org/json-rpc/2/error-codes
			if (code == 402) {
				
				Calendar date = new GregorianCalendar();
				date.set(Calendar.HOUR_OF_DAY, 0);
				date.set(Calendar.MINUTE, 0);
				date.set(Calendar.SECOND, 0);
				date.set(Calendar.MILLISECOND, 0);
				date.add(Calendar.DAY_OF_MONTH, 1);
				
				this.backoff = date.getTimeInMillis();
				this.backoffError = "Error " + code + ": " + message;
				ret.put("exception", new RandomOrgInsufficientRequestsError(this.backoffError));
				return ret;
			} else if (code == 401) {
				ret.put("exception", new RandomOrgKeyNotRunningError("Error " + code 
						+ ": " + message));
				return ret;

			} else if (code == 403) {
				ret.put("exception", new RandomOrgInsufficientBitsError("Error " + code 
						+ ": " + message, this.bitsLeft));
				return ret;

			// RandomOrgRANDOMORGError from RANDOM.ORG Errors: 
			// https://api.random.org/json-rpc/2/error-codes
			} else if (RandomOrgClient.randomOrgErrors.contains(code)) {
				ret.put("exception", new RandomOrgRANDOMORGError("Error " + code 
						+ ": " + message));
				return ret;
				
			// RandomOrgJSONRPCError from JSON-RPC Errors: 
			// https://api.random.org/json-rpc/2/error-codes
			} else {
				ret.put("exception", new RandomOrgJSONRPCError("Error " + code 
						+ ": " + message));
				return ret;
			}
		}
		
		JsonObject result = response.get("result").getAsJsonObject();
		
		// Update usage statistics
		if (result.has("requestsLeft")) {
			this.requestsLeft = result.get("requestsLeft").getAsInt();
			this.bitsLeft = result.get("bitsLeft").getAsInt();
		}

		// Set new server advisory delay
		synchronized (this.advisoryDelayLock) {
			if (result.has("advisoryDelay")) {
				this.advisoryDelay =  result.get("advisoryDelay").getAsInt();
			} else {
				// Use default if none from server.
				this.advisoryDelay = RandomOrgClient.DEFAULT_DELAY;
			}
			
			this.lastResponseReceivedTime = System.currentTimeMillis();
		}
		
		ret.put("response", response);
		return ret;
	}
	
	/** 
	 * POST JSON to server and return JSON response.
	 * 
	 * @param json request to post. 
	 *
	 * @return JSON response.
	 *
	 * @throws IOException @see java.io.IOException
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 */
	private JsonObject post(JsonObject json) throws IOException, 
													MalformedURLException, 
													RandomOrgBadHTTPResponseException {
		HttpsURLConnection con = (HttpsURLConnection) new URL("https://api.random.org/json-rpc/2/invoke").openConnection();
		con.setConnectTimeout(this.httpTimeout);

		// headers		
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/json");
		
		// send JSON
		con.setDoOutput(true);
		DataOutputStream dos = new DataOutputStream(con.getOutputStream());
		dos.writeBytes(json.toString());
		dos.flush();
		dos.close();

		// check response
		int responseCode = con.getResponseCode();
		
		// return JSON...
		if (responseCode == HttpsURLConnection.HTTP_OK) {
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();

			return new JsonParser().parse(response.toString()).getAsJsonObject();
			
			// Alternative to avoid the deprecation warnings when using Gson 2.8.6+:
			// return JsonParser.parseString(response.toString()).getAsJsonObject();
			
		// ...or throw error
		} else {
			throw new RandomOrgBadHTTPResponseException("Error " + responseCode + ": " 
						+ con.getResponseMessage());
		}
	}
	
	/** Helper function for generateIntegers 
	 * 
	 * @param n how many random integers you need. Must be within the [1,1e4] range.
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * @param base the base that will be used to display the numbers. Values allowed are 2, 8, 10 
	 *        and 16 (default 10).
	 *
	 * @return JsonObject returned from the request
	 * 
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	private JsonObject integerMethod(int n, int min, int max, boolean replacement, int base) 
			throws RandomOrgSendTimeoutException, 
			       RandomOrgKeyNotRunningError, 
			       RandomOrgInsufficientRequestsError, 
			       RandomOrgInsufficientBitsError, 
			       RandomOrgBadHTTPResponseException, 
			       RandomOrgRANDOMORGError, 
			       RandomOrgJSONRPCError, 
			       MalformedURLException, 
			       IOException {		
		JsonObject request = new JsonObject();
		
		request.addProperty("n", n);
		request.addProperty("min", min);
		request.addProperty("max", max);
		request.addProperty("replacement", replacement);
		request.addProperty("base", base);
		
		request = this.generateKeyedRequest(request, INTEGER_METHOD);
		
		return this.sendRequest(request);
	}
	
	/**
	 * Helper function for generateIntegerSequences
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length the length of each array of random integers requested. Must be within the 
	 *        [1,1e4] range. 
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * @param base the base that will be used to display the numbers. Values allowed are 2, 8, 10 
	 *        and 16 (default 10).
	 * @param userData JsonObject that will be included in unmodified form. Its maximum size in 
	 *        encoded (String) form is 1,000 characters (default null).
	 * @param signed boolean representing whether the request uses Basic API (false) or signed 
	 *        API (true).
	 *
	 * @return JsonObject returned from the request
	 * 
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	private JsonObject integerSequencesMethod(int n, int length, int min, int max, boolean replacement, int base, JsonObject userData, boolean signed) 
			throws RandomOrgSendTimeoutException, 
			       RandomOrgKeyNotRunningError, 
			       RandomOrgInsufficientRequestsError, 
			       RandomOrgInsufficientBitsError, 
			       RandomOrgBadHTTPResponseException, 
			       RandomOrgRANDOMORGError, 
			       RandomOrgJSONRPCError, 
			       MalformedURLException, 
			       IOException {		
		JsonObject request = new JsonObject();
		
		request.addProperty("n", n);
		request.addProperty("length", length);
		request.addProperty("min", min);
		request.addProperty("max", max);
		request.addProperty("replacement", replacement);
		request.addProperty("base", base);
		
		if (signed) {
			request.add("userData", userData);
			request = this.generateKeyedRequest(request, SIGNED_INTEGER_SEQUENCE_METHOD);
		} else {		
			request = this.generateKeyedRequest(request, INTEGER_SEQUENCE_METHOD);
		}
		
		return this.sendRequest(request);
	}
	
	/**
	 * Helper function for generateIntegerSequences with array ([]) parameters
	 *
	 * @param n how many arrays of random integers you need. Must be within the [1,1e3] range.
	 * @param length the length of each array of random integers requested. Must be within the 
	 *        [1,1e4] range. 
	 * @param min the lower boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param max the upper boundary for the range from which the random numbers will be picked. 
	 *        Must be within the [-1e9,1e9] range.
	 * @param replacement specifies whether the random numbers should be picked with replacement. 
	 *        If true, the resulting numbers may contain duplicate values, otherwise the numbers 
	 *        will all be unique (default true).
	 * @param base the base that will be used to display the numbers. Values allowed are 2, 8, 10 
	 *        and 16 (default 10).
	 * @param userData JsonObject that will be included in unmodified form. Its maximum size in 
	 *        encoded (String) form is 1,000 characters (default null).
	 * @param signed boolean representing whether the request uses Basic API (false) or signed 
	 *        API (true).
	 *
	 * @return JsonObject returned from the request
	 *
	 * @throws RandomOrgSendTimeoutException blocking timeout is exceeded before the request 
	 *         can be sent. 
	 * @throws RandomOrgKeyNotRunningError API key has been stopped.
	 * @throws RandomOrgInsufficientRequestsError API key's server requests allowance has 
	 *         been exceeded.
	 * @throws RandomOrgInsufficientBitsError API key's server bits allowance has been exceeded.
	 * @throws RandomOrgBadHTTPResponseException if a HTTP 200 OK response not received.
	 * @throws RandomOrgRANDOMORGError server returns a RANDOM.ORG Error.
	 * @throws RandomOrgJSONRPCError server returns a JSON-RPC Error.
	 * @throws MalformedURLException in the unlikely event something goes wrong with URL 
	 *         creation. @see java.net.MalformedURLException
	 * @throws IOException @see java.io.IOException
	 */
	private JsonObject integerSequencesMethod(int n, int[] length, int[] min, int[] max, boolean[] replacement, int[] base, JsonObject userData, boolean signed) 
			throws RandomOrgSendTimeoutException, 
			       RandomOrgKeyNotRunningError, 
			       RandomOrgInsufficientRequestsError, 
			       RandomOrgInsufficientBitsError, 
			       RandomOrgBadHTTPResponseException, 
			       RandomOrgRANDOMORGError, 
			       RandomOrgJSONRPCError, 
			       MalformedURLException, 
			       IOException {		
		JsonObject request = new JsonObject();
		
		request.addProperty("n", n);
		request.add("length", gson.toJsonTree(length).getAsJsonArray());
		request.add("min", gson.toJsonTree(min).getAsJsonArray());
		request.add("max", gson.toJsonTree(max).getAsJsonArray());
		request.add("replacement", gson.toJsonTree(replacement).getAsJsonArray());
		request.add("base", gson.toJsonTree(base).getAsJsonArray());
		
		if (signed) {
			request.add("userData", userData);
			request = this.generateKeyedRequest(request, SIGNED_INTEGER_SEQUENCE_METHOD);
		} else {
			request = this.generateKeyedRequest(request, INTEGER_SEQUENCE_METHOD);
		}
		
		return this.sendRequest(request);
	}
	
	/** Helper function for createIntegerSequenceCache with array ([]) parameters */
	private int min(int[] a) {
		int[] min2 = Arrays.copyOf(a, a.length);
		Arrays.sort(min2);
		return min2[0];
	}
	
	/** Helper function for createIntegerSequenceCache with array ([]) parameters */
	private int max(int[] a) {
		int[] max2 = Arrays.copyOf(a, a.length);
		Arrays.sort(max2);
		return max2[max2.length-1];
	}
	
	/** Helper function for createIntegerSequenceCache with array ([]) parameters */
	private int[] adjust(int[] a, int n) {
		int[] adjusted = new int[n];
		for(int i = 1, k = 0; i <= n / a.length; i++) {
			for (int j = 0; j < a.length; j++) {
				adjusted[k++] = a[j];
			}
		}
		return adjusted;
	}
	
	/** Helper function for createIntegerSequenceCache with array ([]) parameters */
	private boolean[] adjust(boolean[] a, int n) {
		boolean[] adjusted = new boolean[n];
		for(int i = 1, k = 0; i <= n / a.length; i++) {
			for (int j = 0; j < a.length; j++) {
				adjusted[k++] = a[j];
			}
		}
		return adjusted;
	}
}