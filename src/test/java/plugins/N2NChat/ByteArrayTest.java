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

	public void testHashCode() {
		assertEquals(Arrays.hashCode(BytesA), a.hashCode());
	}

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
}
