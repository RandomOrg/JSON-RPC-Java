JSON-RPC-Java
===============

RANDOM.ORG JSON-RPC API (Release 4) implementation.

This is a Java implementation of the RANDOM.ORG JSON-RPC API (R4). It provides either serialized or unserialized access to both the signed and unsigned methods of the API through the RandomOrgClient class. It also provides a convenience class through the RandomOrgClient class, the RandomOrgCache, for precaching requests. In the context of this module, a serialized client is one for which the sequence of requests matches the sequence of responses.

Installation
------------

Requires the `gson <https://code.google.com/p/google-gson/>`_ lib and `Commons Codec <http://commons.apache.org/proper/commons-codec/>`_ lib for normal operation, and the `junit <http://junit.org/>`_ lib and `hamcrest <http://hamcrest.org/JavaHamcrest/>`_ lib to run tests.

Usage
-----

The default setup is best for non-time-critical serialized requests, e.g., batch clients:

.. code-block:: java

	RandomOrgClient roc = RandomOrgClient.getRandomOrgClient(YOUR_API_KEY_HERE);
	try {
		int[] randoms = roc.generateIntegers(5, 0, 10);
		System.out.println(Arrays.toString(randoms));
	} catch (...) { ... }
	
	[9, 5, 4, 1, 10]

...or for more time sensitive serialized applications, e.g., real-time draws, use:

.. code-block:: java

	RandomOrgClient roc = RandomOrgClient.getRandomOrgClient(YOUR_API_KEY_HERE, 2000, 10000, true);
	try {
		HashMap<String, Object> randoms = roc.generateSignedIntegers(5, 0, 10);
		System.out.println(randoms.toString());
	} catch (...) { ... }
	
	{random={"method":"generateSignedIntegers","hashedApiKey":"HASHED_KEY_HERE","n":5,"min":0,"max":10,"replacement":true,"base":10,"data":[4,0,5,5,2],"completionTime":"2014-06-09 17:04:23Z","serialNumber":2416}, data=[I@12d56b37, signature=SIGNATURE_HERE}

If obtaining some kind of response instantly is important, a cache should be used. A cache will populate itself as quickly and efficiently as possible allowing pre-obtained randomness to be supplied instantly. If randomness is not available - e.g., the cache is empty - the cache will throw a NoSuchElementException allowing the lack of randomness to be handled without delay:

.. code-block:: java

	RandomOrgClient roc = RandomOrgClient.getRandomOrgClient(YOUR_API_KEY_HERE, 60000l*60000l, 30000, true);
	RandomOrgCache<int[]> c = roc.createIntegerCache(5, 0, 10);
	while (true) {
		try {
			int[] randoms = c.get();
			System.out.println(Arrays.toString(randoms));
		} catch (NoSuchElementException e) {
			// handle lack of true random number here
			// possibly use a pseudo random number generator
		}
	}
	
	[10, 3, 1, 9, 0]
	[8, 9, 8, 3, 5]
	[3, 5, 2, 8, 2]
	...

Note that caches don't support signed responses as it is assumed that clients using the signing features want full control over the serial numbering of responses.
	
Finally, it is possible to request live results as-soon-as-possible and without serialization, however this may be more prone to timeout failures as the client must obey the server's advisory delay times if the server is overloaded:

.. code-block:: java

	RandomOrgClient roc = RandomOrgClient.getRandomOrgClient(YOUR_API_KEY_HERE, 0, 10000, false);
	try {
		int[] randoms = roc.generateIntegers(5, 0, 10);
		System.out.println(Arrays.toString(randoms));
	} catch (...) { ... }
	
	[8, 10, 10, 4, 0]

This library now also includes a RANDOM.ORG implementation of the `java.util.Random` class via `RandomOrgRandom`. Usage of supplied methods is similar to `java.util.Random`, with true random numbers returned or a `java.util.NoSuchElementException` exception thrown if true randomness is unavailable.

.. code-block:: java

	RandomOrgRandom ror = new RandomOrgRandom(YOUR_API_KEY_HERE);
	try {
		System.out.println(ror.nextInt(10));
	} catch (NoSuchElementException e) { /* fallback */ }
	
	6

Documentation
-------------

For a full list of available randomness generation functions and other features see RandomOrgClient.java documentation and https://api.random.org/json-rpc/4

Tests
-----

Note that to run the accompanying tests the API_KEY fields must be given authentic values. 
