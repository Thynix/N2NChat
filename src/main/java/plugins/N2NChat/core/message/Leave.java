package plugins.N2NChat.core.message;

import freenet.node.FSParseException;
import freenet.support.IllegalBase64Exception;
import freenet.support.SimpleFieldSet;
import plugins.N2NChat.core.ByteArray;

/**
 * Provides a leave message.
 */
public class Leave implements Base {

	public final long globalIdentifier;
	public final ByteArray pubKeyHash;

	public Leave(long globalIdentifier, ByteArray pubKeyHash) {
		this.globalIdentifier = globalIdentifier;
		this.pubKeyHash = pubKeyHash;
	}

	/**
	 * Constructs a new Leave message. Expects formatting of Leave message type.
	 * @param fieldSet constructs from
	 * @throws FSParseException If missing field.
	 * @throws IllegalBase64Exception If malformed field.
	 */
	public Leave(SimpleFieldSet fieldSet) throws FSParseException, IllegalBase64Exception {
		this.globalIdentifier = Fields.getGlobalIdentifier(fieldSet);
		this.pubKeyHash = Fields.getPubKeyHash(fieldSet);
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		SimpleFieldSet fieldSet = Fields.getFieldSet(Type.LEAVE);
		Fields.putGlobalIdentifier(fieldSet, globalIdentifier);
		Fields.putPubKeyHash(fieldSet, pubKeyHash);
		return fieldSet;
	}
}
