package plugins.N2NChat.core.message;

import freenet.node.FSParseException;
import freenet.support.IllegalBase64Exception;
import freenet.support.SimpleFieldSet;

/**
 * Provides a retract invite message.
 */
public class RetractInvite implements Base {

	public final long globalIdentifier;

	public RetractInvite(long globalIdentifier) {
		this.globalIdentifier = globalIdentifier;
	}

	public RetractInvite(SimpleFieldSet fieldSet) throws FSParseException, IllegalBase64Exception {
		this.globalIdentifier = Fields.getGlobalIdentifier(fieldSet);
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		SimpleFieldSet fieldSet = Fields.getFieldSet(Type.RETRACT_INVITE);
		Fields.putGlobalIdentifier(fieldSet, globalIdentifier);
		return fieldSet;
	}
}
