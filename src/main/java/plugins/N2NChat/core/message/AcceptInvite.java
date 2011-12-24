package plugins.N2NChat.core.message;

import freenet.node.FSParseException;
import freenet.support.SimpleFieldSet;

/**
 * Provides an accept invite message.
 */
public class AcceptInvite implements Base {

	public final long globalIdentifier;

	public AcceptInvite(long globalIdentifier) {
		this.globalIdentifier = globalIdentifier;
	}

	/**
	 * Constructs a new AcceptInvite message. Expects formatting of Leave message type.
	 * @param fieldSet constructs from
	 * @throws FSParseException If missing field.
	 */
	public AcceptInvite(SimpleFieldSet fieldSet) throws FSParseException {
		globalIdentifier = Fields.getGlobalIdentifier(fieldSet);
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		SimpleFieldSet fieldSet = Fields.getFieldSet(Type.ACCEPT_INVITE);
		Fields.putGlobalIdentifier(fieldSet, globalIdentifier);
		return fieldSet;
	}
}
