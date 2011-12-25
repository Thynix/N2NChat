package plugins.N2NChat.core.message;

/**
 * Identifiers for the message types that can be sent and received.
 */
public enum Type {
	MESSAGE,       //A chat message from a user to the other participants.
	OFFER_INVITE,  //An invite offer to another room.
	RETRACT_INVITE,//A retraction of an invite offer.
	ACCEPT_INVITE, //Acceptance of an invitation.
	REJECT_INVITE, //Rejection of an invitation.
	JOIN,          //A participant joining.
	LEAVE          //A participant leaving.
}
