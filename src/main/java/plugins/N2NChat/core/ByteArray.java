package plugins.N2NChat;

import java.util.Arrays;

/**
 * Used to allow for byte arrays in HashMaps by properly providing hashCode() and equals().
 */
public class ByteArray {

	private byte[] array;

	public ByteArray(byte[] array) {
		this.array = array;
	}

	@Override
	/**
	 * @inheritDoc
	 */
	public int hashCode() {
		return Arrays.hashCode(array);
	}

	@Override
	/**
	 * @inheritDoc
	 */
	public boolean equals(Object o) {
		if (array == o) {
			return true;
		}
		return (o instanceof ByteArray) && Arrays.equals(array, ((ByteArray) o).getBytes());
	}

	/**
	 * Used to get the array contained within the wrapper.
	 * @return The raw byte array contained in this object.
	 */
	public byte[] getBytes() {
		return array;
	}
}
