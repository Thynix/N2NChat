package plugins.N2NChat;

import junit.framework.TestCase;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Arrays;

/**
 * Tests ByteArray behavior in HashMaps.
 */
public class ByteArrayTest extends TestCase {

	private byte[] BytesA;
	private byte[] BytesB;
	private ByteArray a;

	public ByteArrayTest() throws UnsupportedEncodingException {
		BytesA = "String1".getBytes("UTF-8");
		BytesB = "String2".getBytes("UTF-8");
		a = new ByteArray(BytesA);
	}

	/**
	 * Tests that containsKey() behaves properly on a HashMap with ByteArray keys.
	 * @throws UnsupportedEncodingException If the JVM does not support UTF-8.
	 */
	public void testInHashMap() throws UnsupportedEncodingException {
		HashMap<ByteArray, String> hashMap = new HashMap<ByteArray, String>();
		hashMap.put(a, "something");
		assertTrue(hashMap.containsKey(a));
		assertTrue(hashMap.containsKey(new ByteArray(BytesA)));
	}

	/**
	 * Tests that what goes into a ByteArray comes out of it again.
	 */
	public void testGetBytes() {
		assertTrue(Arrays.equals(a.getBytes(), BytesA));
	}

	/**
	 * Tests that Arrays.hashCode is used. This test isn't needed - just some kind of hash will do.
	 */
	public void testHashCode() {
		assertEquals(Arrays.hashCode(BytesA), a.hashCode());
	}

	/**
	 * Tests various kinds of equality.
	 */
	public void testEquality() {
		//Same address
		assertTrue(a.equals(a));
		//Same value
		assertTrue(a.equals(new ByteArray(BytesA)));
		//Different value
		assertFalse(a.equals(new ByteArray(BytesB)));
		//Something other than a ByteArray
		assertFalse(a.equals("Something else"));
	}

	/**
	 * Tests whether the byte[] problem which prevents them from being used as HashMap keys exists.
	 * This test will pass if byte arrays are unsuitable for HashMap keys. If it fails, it may be that byte arrays
	 * are now suitable for HashMap keys, and that the ByteArray wrapper is no longer needed.
	 */
	public void testRawByteArray() {
		HashMap<byte[], String> hashMap = new HashMap<byte[], String>();
		hashMap.put(BytesA, "a string");
		//It works if testing the object at the same address,
		assertTrue(hashMap.containsKey(BytesA));
		//but not with the same value
		assertTrue(Arrays.equals(BytesA, BytesA.clone()));
		//at a different address.
		assertFalse(hashMap.containsKey(BytesA.clone()));

	}
}
