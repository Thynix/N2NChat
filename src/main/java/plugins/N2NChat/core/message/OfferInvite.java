package plugins.N2NChat.core.message;

import freenet.node.FSParseException;
import freenet.support.Base64;
import freenet.support.IllegalBase64Exception;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import plugins.N2NChat.core.ByteArray;

import java.io.UnsupportedEncodingException;

/**
 * Offers an invite to an existing room.
 */
public class OfferInvite implements Base {

	public final long globalIdentifier;
	public final String username;
	public final String roomName;

	public OfferInvite(long globalIdentifier, String username, String roomName) {
		this.globalIdentifier = globalIdentifier;
		this.username = username;
		this.roomName = roomName;
	}

	public OfferInvite(SimpleFieldSet fieldSet) throws FSParseException, IllegalBase64Exception {
		this.globalIdentifier = Fields.getGlobalIdentifier(fieldSet);
		this.username = Fields.getUsername(fieldSet);
		this.roomName = Fields.getRoomName(fieldSet);
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		SimpleFieldSet fieldSet = Fields.getFieldSet(Type.OFFER_INVITE);
		Fields.putGlobalIdentifier(fieldSet, globalIdentifier);
		try {
			Fields.putUsername(fieldSet, username);
		} catch (UnsupportedEncodingException e) {
			Logger.error(Message.class, "JVM does not support UTF-8, skipping encoding username.", e);
		}
		try {
			Fields.putRoomName(fieldSet, roomName);
		} catch (UnsupportedEncodingException e) {
			Logger.error(Message.class, "JVM does not support UTF-8, skipping encoding room name.", e);
		}
		return fieldSet;
	}
}
