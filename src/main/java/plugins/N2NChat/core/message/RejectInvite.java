package plugins.N2NChat.core.message;

import freenet.node.FSParseException;
import freenet.support.IllegalBase64Exception;
import freenet.support.SimpleFieldSet;

/**
 * Provides a reject invite message.
 */
public class RejectInvite implements Base {

	public final long globalIdentifier;

	public RejectInvite(long globalIdentifier) {
		this.globalIdentifier = globalIdentifier;
	}

	public RejectInvite(SimpleFieldSet fieldSet) throws FSParseException, IllegalBase64Exception {
		this.globalIdentifier = Fields.getGlobalIdentifier(fieldSet);
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		SimpleFieldSet fieldSet = Fields.getFieldSet(Type.REJECT_INVITE);
		Fields.putGlobalIdentifier(fieldSet, globalIdentifier);
		return fieldSet;
	}
}
