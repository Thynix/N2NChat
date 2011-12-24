package plugins.N2NChat.core.message;

import freenet.node.FSParseException;
import freenet.support.Base64;
import freenet.support.IllegalBase64Exception;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import plugins.N2NChat.core.ByteArray;

import java.io.UnsupportedEncodingException;
import java.util.Date;

/**
 * Provides a chat message with text intended for other participants to read.
 */
public class Message implements Base {

	public final long globalIdentifier;
	public final ByteArray pubKeyHash;
	public final Date timeComposed;
	public final String text;

	public Message(long globalIdentifier, ByteArray pubKeyHash, Date timeComposed, String text) {
		this.globalIdentifier = globalIdentifier;
		this.pubKeyHash = pubKeyHash;
		this.timeComposed = timeComposed;
		this.text = text;
	}

	public Message(SimpleFieldSet fieldSet) throws FSParseException, IllegalBase64Exception {
		this.globalIdentifier = Fields.getGlobalIdentifier(fieldSet);
		this.pubKeyHash = Fields.getPubKeyHash(fieldSet);
		this.timeComposed = Fields.getTimeComposed(fieldSet);
		this.text = Fields.getText(fieldSet);
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		SimpleFieldSet fieldSet = Fields.getFieldSet(Type.MESSAGE);
		Fields.putGlobalIdentifier(fieldSet, globalIdentifier);
		Fields.putPubKeyHash(fieldSet, pubKeyHash);
		Fields.putTimeComposed(fieldSet, timeComposed);
		try {
			Fields.putText(fieldSet, text);
		} catch (UnsupportedEncodingException e) {
			Logger.error(Message.class, "JVM does not support UTF-8, skipping encoding message text.", e);
		}
		return fieldSet;
	}
}
