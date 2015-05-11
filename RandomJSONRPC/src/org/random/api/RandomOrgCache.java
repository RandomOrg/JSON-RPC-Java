package org.random.api;

import java.lang.reflect.Array;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.random.api.exception.RandomOrgInsufficientBitsError;

import com.google.gson.JsonObject;

/** Precache class for frequently used requests.
 **
 ** ** WARNING **
 ** Instances of this class should only be obtained using a RandomOrgClient's 
 ** createCache() methods.
 ** 
 ** This class strives to keep a Queue of response results populated for instant 
 ** access via its public get() method. Work is done by a background Thread, which 
 ** issues the appropriate request at suitable intervals.
 ** 
 ** @param <T> return array type, e.g., int[]
 ** 
 **/
public class RandomOrgCache<T> {
	
	private JsonObjectInputCallable<JsonObject> requestFunction;
	private JsonObjectInputCallable<T> processFunction;
	
	private JsonObject request;
	
	// thread-safe queue to store the cached values
	private BlockingQueue<T> queue = new LinkedBlockingQueue<T>();
	private int cacheSize;
	
	private int bulkRequestNumber, requestNumber, requestSize;

	// lock to allow notification when an item is consumed or pause state is updated.
	private Object lock = new Object();
	private boolean paused = false;

	// bits used by this cache - note, not the same as bits remaining on server
	private long usedBits = 0;
	
	// requests used by this cache - note, not the same as requests remaining on server
	private long usedRequests = 0;
	
	private static final Logger LOGGER = Logger.getLogger(RandomOrgClient.class.getPackage().getName());

	/** Initialize class and start Queue population Thread running as a daemon.
     ** 
     ** ** WARNING **
     ** Should only be called by RandomOrgClient's createCache() methods.
     ** 
	 ** @param requestFunction function used to send supplied request to server.
	 ** @param processFunction function to process result of requestFunction into expected output.
	 ** @param request request to send to server via requestFunction.
	 ** @param cacheSize number of request responses to try maintain.
	 ** @param bulkRequestNumber if request is set to be issued in bulk, number of result sets in a bulk request, else 0.
	 ** @param requestNumber if request is set to be issued in bulk, number of results in a single request, else 0.
	 ** @param singleRequestSize in bits for adjusting bulk requests if bits are in short supply on the server.
	 **/
	protected RandomOrgCache(JsonObjectInputCallable<JsonObject> requestFunction, JsonObjectInputCallable<T> processFunction, 
							 JsonObject request, int cacheSize, int bulkRequestNumber, int requestNumber, int singleRequestSize) {
		
		this.requestFunction = requestFunction;
		this.processFunction = processFunction;
		
		this.request = request;
		
		this.cacheSize = cacheSize;
		
		this.bulkRequestNumber = bulkRequestNumber;
		this.requestNumber = requestNumber;
		this.requestSize = singleRequestSize;
		
		// Thread to keep RandomOrgCache populated.
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				RandomOrgCache.this.populateQueue();
			}			
		});
		t.setDaemon(true);
		t.start();
	}
	
	/** Keep issuing requests to server until Queue is full. When Queue is full if requests 
	 ** are being issued in bulk, wait until Queue has enough space to accommodate all of a 
	 ** bulk request before issuing a new request, otherwise issue a new request every time 
	 ** an item in the Queue has been consumed.
	 ** 
	 ** Note that requests to the server are blocking, i.e., only one request will be issued by 
	 ** the cache at any given time.
	 **/
	@SuppressWarnings("unchecked")
	protected void populateQueue() {
		while (true) {
			synchronized (this.lock) {
				
				if (this.paused) {
					try {
						this.lock.wait();
					} catch (InterruptedException e) {
						LOGGER.log(Level.INFO, "Cache interrupted while waiting for notify()");
					}
				}
			}
			
			// If we're issuing bulk requests...
			if (this.bulkRequestNumber > 0) {

				// Is there space for a bulk response in the queue?
				if (this.queue.size() < (this.cacheSize - this.bulkRequestNumber)) {
					
					// Issue and process request and response.
					try {
						this.requestFunction.setInput(request);
						JsonObject response = this.requestFunction.call();
						
						this.processFunction.setInput(response);
						T result = this.processFunction.call();

						// Split bulk response into result sets.
						int length = Array.getLength(result);
						
						for (int i = 0; i < length; i+=this.requestNumber) {

							T entry = (T) Array.newInstance(result.getClass().getComponentType(), this.requestNumber);
							
							for (int j = 0; j < this.requestNumber; j++) {
								Array.set(entry, j, Array.get(result, i+j));
							}
							this.queue.offer(entry);
						}
						
						// update usage counters
						this.usedBits += response.get("result").getAsJsonObject().get("bitsUsed").getAsInt();
						this.usedRequests++;
						
					} catch (RandomOrgInsufficientBitsError e) {

						// get bits left
						int bits = e.getBits();
						
						// can we adapt bulk request size?
						if (bits != -1 && this.requestSize < bits) {
							
							this.bulkRequestNumber = bits / this.requestSize;

							// update bulk request size
							this.request.remove("n");
							this.request.addProperty("n", this.bulkRequestNumber*this.requestNumber);
							
						// nope - so error
						} else {
							throw(e);							
						}
						
					} catch (Exception e) {
						// Don't handle failures from requestFunction(), Just try again later.
						LOGGER.log(Level.INFO, "RandomOrgCache populate Exception: " + e.getClass().getName() + ": " + e.getMessage());
					}
				} else {
					// No space, sleep and wait for consumed notification.
					synchronized (this.lock) {
						try {
							this.lock.wait();
						} catch (InterruptedException e) {
							LOGGER.log(Level.INFO, "Cache interrupted while waiting for notify()");
						}
					}
				}
				
			// Not in bulk mode, repopulate queue as it empties.
			} else if (this.queue.size() < this.cacheSize) {
				try {
					this.requestFunction.setInput(request);
					JsonObject response = this.requestFunction.call();
					
					this.processFunction.setInput(response);
					this.queue.offer(this.processFunction.call());
					
					// update usage counters
					this.usedBits += response.get("result").getAsJsonObject().get("bitsUsed").getAsInt();
					this.usedRequests++;

				} catch (Exception e) {
					// Don't handle failures from requestFunction(), Just try again later.
					LOGGER.log(Level.INFO, "RandomOrgCache populate Exception: " + e.getClass().getName() + ": " + e.getMessage());
				}
			} else {
				// No space, sleep and wait for consumed notification.
				synchronized (this.lock) {
					try {
						this.lock.wait();
					} catch (InterruptedException e) {
						LOGGER.log(Level.INFO, "Cache interrupted while waiting for notify()");
					}
				}
			}
		}
	}
	
	/** Cache will no longer continue to populate itself. */
	public void stop() {
		synchronized (this.lock) {
			this.paused = true;
			this.lock.notify();
		}
	}
	
	/** Cache will resume populating itself if stopped. */
	public void resume() {
		synchronized (this.lock) {
			this.paused = false;
			this.lock.notify();
		}
	}
	
	/** Return <code>true</code> if cache is currently not re-populating itself.
	 ** <p>
	 ** Values currently cached may still be retrieved with <code>get()</code>,
	 ** but no new values are being fetched from the server.
	 ** <p>
	 ** This state can be changed with <code>stop()</code> and <code>resume()</code>.
	 ** 
	 ** @see #stop()
	 ** @see #resume()
	 ** 
	 ** @return <code>true</code> if cache is currently not re-populating itself.
	 **/
	public boolean isPaused() {
		return this.paused;
	}
	
	/** Get next response.
	 **
	 ** @return next appropriate response for the request this RandomOrgCache represents 
	 ** or if Queue is empty throws a NoSuchElementException.
	 **/
	public T get() {
		synchronized (this.lock) {
			T result = this.queue.remove();
			this.lock.notify();
			return result;
		}
	}
	
	/** Get next response or wait until the next value is available.
	 ** <p>
	 ** This method will block until a value is available.
	 ** <p>
	 ** Note: if the cache is paused or no more randomness is available from the server this call can result in a dead lock.
	 ** 
	 ** @see #isPaused()
	 ** 
	 ** @return next appropriate response for the request this RandomOrgCache represents
	 **
	 ** @throws InterruptedException if any thread interrupted the current thread before or 
	 ** while the current thread was waiting for a notification. The interrupted status of 
	 ** the current thread is cleared when this exception is thrown.
	 */
	public T getOrWait() throws InterruptedException {
				
		// get result or wait
		T result = this.queue.take();

		// notify cache - check if refill is needed
		synchronized (this.lock) {
			this.lock.notify();
		}
		
		return result;
	}
	
	/** Get number of results of type {@link #T} remaining in the cache.
	 ** <p>
	 ** This essentially returns how often <code>get()</code> may be called without a cache refill,
	 ** or <code>getOrWait()</code> may be called without blocking.
	 ** 
	 ** @return current number of cached results
	 **/
	public int getCachedValues() {
		return this.queue.size();
	}
	
	/** Get number of bits used by this cache.
	 ** 
	 ** @return number of used bits
	 **/
	public long getUsedBits() {
		return this.usedBits;
	}
	
	/** Get number of requests used by this cache.
	 ** 
	 ** @return number of used requests
	 **/
	public long getUsedRequests() {
		return this.usedRequests;
	}
}