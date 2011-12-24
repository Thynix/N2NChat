package plugins.N2NChat.core.message;

import freenet.node.FSParseException;
import freenet.support.IllegalBase64Exception;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import plugins.N2NChat.core.ByteArray;

import java.io.UnsupportedEncodingException;

/**
 * Provides a join message.
 */
public class Join implements Base {

	public final long globalIdentifier;
	public final ByteArray pubKeyHash;
	public final String username;
	public final boolean displayJoin;

	/**
	 * Constructs a join message with the given attributes.
	 * @param globalIdentifier The global identifier of the room the subject of the message is joining.
	 * @param pubKeyHash The public key hash of the joining user.
	 * @param username The username of the joining user.
	 * @param displayJoin Whether this join event should be displayed. For example, it would probably not be if
	 * 
	 */
	public Join(long globalIdentifier, ByteArray pubKeyHash, String username, boolean displayJoin) {
		this.globalIdentifier = globalIdentifier;
		this.pubKeyHash = pubKeyHash;
		this.username = username;
		this.displayJoin = displayJoin;
	}

	/**
	 * Constructs a join message with the attributes parsed from the fieldSet. Expects formatting of Join type.
	 * @param fieldSet to parse message from
	 * @throws FSParseException If fields are missing.
	 * @throws IllegalBase64Exception If fields are malformed.
	 */
	public Join(SimpleFieldSet fieldSet) throws FSParseException, IllegalBase64Exception {
		this.globalIdentifier = Fields.getGlobalIdentifier(fieldSet);
		this.pubKeyHash = Fields.getPubKeyHash(fieldSet);
		this.username = Fields.getUsername(fieldSet);
		this.displayJoin = Fields.getDisplayJoin(fieldSet);
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		SimpleFieldSet fieldSet = Fields.getFieldSet(Type.JOIN);
		Fields.putGlobalIdentifier(fieldSet, globalIdentifier);
		Fields.putPubKeyHash(fieldSet, pubKeyHash);
		try {
			Fields.putUsername(fieldSet, username);
		} catch (UnsupportedEncodingException e) {
			Logger.error(this, "JVM does not support UTF-8.", e);
		}
		Fields.putDisplayJoin(fieldSet, displayJoin);
		return fieldSet;
	}
}
