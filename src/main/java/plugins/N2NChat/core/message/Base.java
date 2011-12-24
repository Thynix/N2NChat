package plugins.N2NChat.core.message;

import freenet.support.SimpleFieldSet;

/**
 * Describes the functionality to generate a SimpleFieldSet.
 */
public interface Base {

	/**
	 * @return a SimpleFieldSet describing the message.
	 */
	public SimpleFieldSet getFieldSet();

}
