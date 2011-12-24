package plugins.N2NChat.core.message;

import plugins.N2NChat.core.ByteArray;

import freenet.node.FSParseException;
import freenet.support.Base64;
import freenet.support.IllegalBase64Exception;
import freenet.support.SimpleFieldSet;

import java.io.UnsupportedEncodingException;
import java.util.Date;

/**
 * Utility class to allow generation of and parsing from SimpleFieldSets.
 */
public class Fields {

	//FieldSet field names
	private static final String typeKey = "type";
	private static final String globalIdentifierKey = "globalIdentifier";
	private static final String pubKeyHashKey = "pubKeyHash";
	private static final String usernameKey = "username";
	private static final String roomNameKey = "roomName";
	private static final String timeComposedKey = "timeComposed";
	private static final String textKey = "text";
	private static final String displayJoinKey = "displayJoin";

	/**
	 * This class cannot be instantiated.
	 */
	private Fields() {
		throw new AssertionError();
	}

	private static String getString(SimpleFieldSet fieldSet, String key) throws IllegalBase64Exception {
		return new String(Base64.decode(fieldSet.get(key)));
	}

	private static void putString(SimpleFieldSet fieldSet, String key, String string) throws UnsupportedEncodingException {
		fieldSet.putOverwrite(key, Base64.encode(string.getBytes("UTF-8")));
	}

	/**
	 * Generates a fieldSet.
	 * @param type For this type.
	 * @return A field set with the type field set.
	 */
	public static SimpleFieldSet getFieldSet(Type type) {
		SimpleFieldSet fieldSet = new SimpleFieldSet(true);
		putType(fieldSet, type);
		return fieldSet;
	}

	/*
	 * Gets
	 */

	public static Type getType(SimpleFieldSet fieldSet) {
		return Type.valueOf(fieldSet.get(typeKey));
	}

	/**
	 * Reads the global identifier to which the message is directed.
	 * @param fieldSet To parse global identifier from.
	 * @throws FSParseException global identifier not present
	 * @return the global identifier of the room to which the message is directed.
	 */
	public static long getGlobalIdentifier(SimpleFieldSet fieldSet) throws FSParseException {
		return fieldSet.getLong(globalIdentifierKey);
	}

	/**
	 * Reads the public key hash of the participant the message is about.
	 * @param fieldSet Message fieldset to read the public key hash from.
	 * @return public key hash
	 * @throws IllegalBase64Exception public key improperly encoded
	 */
	public static ByteArray getPubKeyHash(final SimpleFieldSet fieldSet) throws IllegalBase64Exception {
		return new ByteArray(Base64.decode(fieldSet.get(pubKeyHashKey)));
	}

	public static String getUsername(SimpleFieldSet fieldSet) throws IllegalBase64Exception {
		return getString(fieldSet, usernameKey);
	}

	public static String getRoomName(SimpleFieldSet fieldSet) throws IllegalBase64Exception {
		return getString(fieldSet, roomNameKey);
	}

	public static Date getTimeComposed(SimpleFieldSet fieldSet) throws FSParseException {
		return new Date(fieldSet.getLong(timeComposedKey));
	}

	public static String getText(SimpleFieldSet fieldSet) throws IllegalBase64Exception {
		return getString(fieldSet, textKey);
	}

	public static boolean getDisplayJoin(SimpleFieldSet fieldSet) throws FSParseException {
		return fieldSet.getBoolean(displayJoinKey);
	}

	/*
	 * Puts
	 */

	public static void putType(SimpleFieldSet fieldSet, Type type) {
		fieldSet.putOverwrite(typeKey, type.toString());
	}

	/**
	 * Encodes the given global identifier into the fieldset.
	 * @param fieldSet to add the field to - overwrites if any conflict.
	 * @param globalIdentifier identifier of the room the message is directed to
	 */
	public static void putGlobalIdentifier(SimpleFieldSet fieldSet, long globalIdentifier) {
		fieldSet.put(globalIdentifierKey, globalIdentifier);
	}

	/**
	 * Encodes the given public key hash into the fieldset.
	 * @param fieldSet to add the field to - overwrites if any conflict.
	 * @param pubKeyHash public key hash of the participant the message is about
	 */
	public static void putPubKeyHash(SimpleFieldSet fieldSet, ByteArray pubKeyHash) {
		fieldSet.putOverwrite(pubKeyHashKey, Base64.encode(pubKeyHash.getBytes()));
	}

	public static void putUsername(SimpleFieldSet fieldSet, String username) throws UnsupportedEncodingException {
		putString(fieldSet, usernameKey, username);
	}

	public static void putRoomName(SimpleFieldSet fieldSet, String roomName) throws UnsupportedEncodingException {
		putString(fieldSet, roomNameKey, roomName);
	}

	public static void putTimeComposed(SimpleFieldSet fieldSet, Date timeComposed) {
		fieldSet.put(timeComposedKey, timeComposed.getTime());
	}

	public static void putText(SimpleFieldSet fieldSet, String text) throws UnsupportedEncodingException {
		putString(fieldSet, textKey, text);
	}

	public static void putDisplayJoin(SimpleFieldSet fieldSet, boolean displayJoin) {
		fieldSet.put(displayJoinKey, displayJoin);
	}
}
