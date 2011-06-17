package freenet.node;

import freenet.l10n.NodeL10n;
import freenet.support.HSLColor;
import freenet.support.HTMLNode;
import freenet.support.Logger;

import java.awt.Color;
import java.lang.String;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;

/**
 * The N2NChatRoom class keeps track of what has been said in a chat room, parses new messages, formats them, and is
 * responsible for system messages such as joins, leaves, and day changes.
 */
public class N2NChatRoom {

	public static SimpleDateFormat timeComposedFormat = new SimpleDateFormat("D yyyy HH:mm:s");

	private Calendar lastMessageReceived;
	//Everyone in this room. The key is the identity hash, the value is the name of the node.
	private HashMap<Integer, Participant> participants;
	//TODO: Is there a better way to allow chat rooms access to connected peers?
	private HashMap<Integer, DarknetPeerNode> peerNodes;
	private HTMLNode log;
	private HTMLNode participantListing;
	private String localName;
	private long globalIdentifier;
	private String username;
	private SimpleDateFormat dayChangeFormat;
	private SimpleDateFormat timestampFormat;

	//TODO: Participant perhaps icons for whether messages sent to them have gone through.
	/**
	 * Initializes date formatters and starts the room off with a timestamp of the day.
	 * @param localName Name of the chatroom. Only applies locally.
	 * @param globalIdentifier Global ID; used by all participants to specify what room a message is for.
	 * @param username This node's username in this chat.
	 * @param peerNodes This node's Darknet peers. Used to search for direct connections to nodes invited in by
	 * others and find the names of directly connected senders.
	 */
	public N2NChatRoom(String localName, long globalIdentifier, String username, DarknetPeerNode[] peerNodes){
		this.localName = localName;
		this.globalIdentifier = globalIdentifier;
		this.username = username;
		updatePeerNodes(peerNodes);
		participants = new HashMap<Integer, Participant>();
		lastMessageReceived = Calendar.getInstance();
		lastMessageReceived.setTime(new Date());
		//TODO: Allow date display formatting configuration.
		//Ex: Wednesday, June 1, 2011 (followed by newline)
		dayChangeFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy");
		//Ex: 04:48:30 PM
		timestampFormat = new SimpleDateFormat("hh:mm:ss a");
		//TODO: What size should this box be? Will the automatic size be reasonable?
		//TODO: Likely full width with limited height.
		log = new HTMLNode("div", "class", "overflow:scroll");
		//Start out the chat by setting the day.
		log.addChild("p", dayChangeFormat.format(lastMessageReceived.getTime()));
	}

	public void updatePeerNodes(DarknetPeerNode[] updatedPeerNodes) {
		this.peerNodes = new HashMap<Integer, DarknetPeerNode>();
		for (DarknetPeerNode node : updatedPeerNodes) {
			peerNodes.put(node.getIdentityHash(), node);
		}
		//TODO: Check new peers for direct connections to those currently routed and backup routes.
	}

	/**
	 * Adds a directly connected participant that was invited locally.
	 * This node will route messages to and from them.
	 * @param participant The peer that was invited.
	 * @param username The name of this user as referred to within this chat.
	 * @return True if the participant was added, false if not.
	 */
	public boolean inviteParticipant(DarknetPeerNode participant, String username) {
		if (addParticipant(participant.getIdentityHash(), participant.getName(), participant, true)) {
			for (int identityHash : participants.keySet()) {
				if (identityHash != participant.getIdentityHash() &&
				        participants.get(identityHash).directlyConnected) {
					//Send all other participants a join for the new participant.
					participants.get(identityHash).peerNode.sendChatJoin(globalIdentifier,
					        participant.getIdentityHash(), username);
					//Send the new participant joins for all other participants.
					participant.sendChatJoin(globalIdentifier, identityHash,
					        participants.get(identityHash).name);
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Adds a participant that was invited remotely.
	 * @param identityHash The identity hash of the new participant.
	 * @param name The name of the new participant.
	 * @param routedBy The peer that routed the invite. This peer will be authorized to route all other things
	 * with regards to this participant.
	 * @return True if the participant was added, false otherwise.
	 */
	public boolean joinedParticipant(int identityHash, String name, DarknetPeerNode routedBy) {
		/*TODO: Query directly connected participants for backup routing paths.*/
		if (addParticipant(identityHash, name, routedBy, false)) {
			for (int IDHash : participants.keySet()) {
				//Route this join to all participants this node routes for.
				if (participants.get(IDHash).locallyInvited) {
					participants.get(IDHash).peerNode.
					        sendChatJoin(globalIdentifier, identityHash, name);
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Adds a participant to this chatroom so that messages they send here can be received.
	 * @param identityHash Identity hash of the participant to add.
	 * @param name Name of the participant to add.
	 * @param peerNode If directly connected, the DarknetPeerNode to invite. If not, the DarknetPeerNode who invited
	 * this participant and is authorized to route their messages.
	 * @param invitedLocally True if invited by the local node, false if invited by someone else.
	 * @return True if participant was added, false otherwise.
	 */
	private boolean addParticipant(int identityHash, String name, DarknetPeerNode peerNode, boolean invitedLocally) {
		//A participant cannot be in a chat room multiple times at once.
		if (participants.containsKey(identityHash)) {
			return false;
		}
		boolean directlyConnected = peerNodes.containsKey(identityHash);
		participants.put(identityHash, new Participant(identityHash, name, peerNode, directlyConnected,
		        invitedLocally));
		log.addChild("p", l10n("joined", "name", name));
		updateParticipantListing();
		return true;
	}

	/**
	 * Removes a participant from the chat room.
	 * @param removeIdentityHash Identity hash of the participant to remove.
	 * @param senderIdentityHash Identity hash of the participant that sent the removal request. In order for the
	 * removal to be valid, this must either be the participant authorized to route this person's messages or the
	 * participant themselves.
	 * @param connectionProblem Used internally to indicate whether a departure message was received or the node
	 * disconnected. If true "lost connection" is used rather than "left".
	 * @return True if the participant was removed; false if not. More detailed error messages are written to
	 * the log.
	 */
	//TODO: Should this return a more descriptive state? Will other things care whether the removal was successful?
	public boolean removeParticipant(int removeIdentityHash, int senderIdentityHash, boolean connectionProblem) {
		String error = checkPresenceAndAuthorization("remove", removeIdentityHash, senderIdentityHash);
		if (error != null) {
			Logger.warning(this, l10n("removeReceived",
			        new String[] { "removeName", "removeHash", "fromName", "fromHash" },
			        new String[] { findName(removeIdentityHash, senderIdentityHash),
			                String.valueOf(removeIdentityHash),
			                peerNodes.get(senderIdentityHash).getName(),
			                String.valueOf(senderIdentityHash) })
			        +' '+error+' '+l10n("roomInfo",
			        new String[] { "localName", "globalIdentifier"},
			        new String[] { localName, String.valueOf(globalIdentifier) }));
			return false;
		}

		//The identity to remove and the sender of the request are in the chat room, and the sender of the
		//request is authorized to remove the identity.
		if (connectionProblem) {
			log.addChild(l10n("lostConnection", "name", participants.get(removeIdentityHash).name));
		} else {
			log.addChild(l10n("left", "name", participants.get(removeIdentityHash).name));
		}
		participants.remove(removeIdentityHash);

		Set<Integer> identityHashes = participants.keySet();
		for (int identityHash : identityHashes) {
			//Remove from the room any other participants the leaving node routed for.
			if (participants.get(identityHash).peerNode.getIdentityHash() == removeIdentityHash) {
				participants.remove(identityHash);
				log.addChild(l10n("lostConnection", "name", participants.get(identityHash).name));
			//Send this disconnect to all participants this node invited.
			} else if (participants.get(identityHash).locallyInvited) {
				participants.get(identityHash).peerNode.sendChatLeave(globalIdentifier, removeIdentityHash);
			}
		}
		return true;
	}

	/**
	 * Gets a name for a given identity hash, if possible. Used internally to allow for the nickname of darknet
	 * nodes in error messages.
	 * @param targetIdentityHash Explicit identity hash of the subject.
	 * @param senderIdentityHash Identity hash of the peer that sent the message.
	 * @return Either a name or "an unknown participant"
	 */
	private String findName (int targetIdentityHash, int senderIdentityHash) {
		//Sender must be connected in order to send something, so their name is always known.
		if (senderIdentityHash == targetIdentityHash) {
			return peerNodes.get(senderIdentityHash).getName();
		} else if (participants.containsKey(targetIdentityHash)) {
			return participants.get(targetIdentityHash).name;
		} else {
			return "an unknown participant";
		}
	}

	/**
	 * Checks whether the identities are in the chat room, and whether the sender is authorized to route actions
	 * taken by the target identity.
	 * @param prefix l10n prefix to be added to "N2NChatRoom." for error message.
	 * @param targetIdentityHash Identity hash the event concerns.
	 * @param senderIdentityHash Identity hash of the sender.
	 * @return null if the identities are in the room and the sender is authorized.
	 * If there's a problem it returns descriptive text.
	 */
	private String checkPresenceAndAuthorization(String prefix, int targetIdentityHash, int senderIdentityHash) {
		//Neither the sender nor the identity it concerns are in the chat room.
		if (!participants.containsKey(senderIdentityHash) && !participants.containsKey(targetIdentityHash)) {
			if (senderIdentityHash == targetIdentityHash) {
				return l10n("nonparticipant");
			} else {
				return l10n(prefix+"senderAndTargetNonparticipant");
			}
		}

		//The sender of the request is not in the chat room and the identity to remove is.
		if (!participants.containsKey(senderIdentityHash) && participants.containsKey(targetIdentityHash)) {
			return l10n(prefix+"senderNonparticipant");
		}

		//The identity to remove is not in the chat room and the sender of the request is.
		if (!participants.containsKey(targetIdentityHash)) {
			return l10n(prefix+"targetNonparticipant");
		}

		//The sender of the request and identity to remove are in the chat room, but the sender of the request
		//is not authorized to remove that identity. This may occur in legitimate circumstances if this node has
		//a direct connection to a peer that the sender of the request invited.
		if (participants.get(targetIdentityHash).peerNode.getIdentityHash() != senderIdentityHash) {
			return l10n(prefix+"senderUnauthorized");
		}

		return null;
	}

	//TODO: Log persistance.
	public HTMLNode getLog() {
		return log;
	}

	public HTMLNode getParticipantListing() {
		return participantListing;
	}

	public long getGlobalIdentifier() {
		return globalIdentifier;
	}

	public String getLocalName() {
		return localName;
	}

	/**
	 * @param name Name to check for
	 * @return True if a participant in the room has that name, false if not.
	 */
	public boolean nameExists(String name) {
		for (Participant participant : participants.values()) {
			if (participant.name.equals(name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Attempts to add a message to the chat room log.
	 * @param composedBy The identity hash of the composer of the message.
	 * @param timeComposed The time at which the message was composed.
	 * @param deliveredBy The identity hash of the darknet peer node that delivered the message.
	 * @param message The message to add.
	 * @return True if the message was added; false if the message's composer is not in this chat room or the
	 */
	public boolean receiveMessage(int composedBy, Date timeComposed, int deliveredBy, String message) {
		String error = checkPresenceAndAuthorization("message", composedBy, deliveredBy);
		if (error != null) {
			Logger.warning(this, l10n("messageReceived",
			        new String[] { "composerName", "composerHash", "fromName", "fromHash" },
			        new String[] { findName(composedBy, deliveredBy),
			                String.valueOf(composedBy),
			                peerNodes.get(deliveredBy).getName(),
			                String.valueOf(deliveredBy) })
			        +' '+error+' '+l10n("roomInfo",
			        new String[] { "localName", "globalIdentifier"},
			        new String[] { localName, String.valueOf(globalIdentifier) }));
			return false;
		}

		Calendar now = Calendar.getInstance();
		now.setTime(new Date());
		addDateOnDayChange(now);

		HTMLNode messageLine = log.addChild("p");

		//Ex: [ 04:38:30 PM ]
		//Ex: Tooltip of time composed.
		messageLine.addChild("timestamp", "title",
		        l10n("composed", "time", timeComposedFormat.format(timeComposed.getTime())),
		        "[ "+timestampFormat.format(now.getTime())+" ] ");

		Participant user = participants.get(composedBy);
		//Ex: BillyBob:
		//With text color based on identity hash with a complementary background for visibility.
		Color textColor = user.nameColor;
		String name = user.name;
		messageLine.addChild("div", "class", "color:rgb("+textColor.getRed()+','+textColor.getGreen()+','+
		        textColor.getBlue()+')', name+": ");

		//Ex: Blah blah blah.
		messageLine.addChild("#", message);

		lastMessageReceived = now;

		//If this node routes this user's messages, send it to all other directly connected participants.
		if (user.locallyInvited) {
			for (Participant participant : participants.values()) {
				if (participant.directlyConnected && participant != user) {
					participant.peerNode.sendChatMessage(timeComposed, composedBy, globalIdentifier,
					        message);
				}
			}
		}

		return true;
	}

	private void updateParticipantListing() {
		//Sort participants alphabetically.
		Participant[] sortedParticipants = participants.values().toArray(new Participant[participants.size()]);
		Arrays.sort(sortedParticipants);

		participantListing = new HTMLNode("div", "class", "overflow:scroll");

		//List participants alphabetically with colored name text and routing information on tooltip.
		for (Participant participant : sortedParticipants) {
			String routing;
			if (participant.directlyConnected) {
				routing = l10n("connectedDirectly",
				        new String[] { "nodeName", "nodeID" },
				        new String[] { participant.peerNode.getName(),
				                participant.peerNode.getIdentityString() });
			} else {
				routing = l10n("connectedThrough",
				        new String[] { "nameInRoom", "nodeName", "nodeID" },
				        new String[] { participants.get(participant.peerNode.getIdentityHash()).name,
				                participant.peerNode.getName(),
				                participant.peerNode.getIdentityString() });
			}
			Color nameColor = participant.nameColor;
			String color = "rgb("+nameColor.getRed()+','+nameColor.getGreen()+','+nameColor.getBlue()+')';
			participantListing.addChild("p", "title", routing).addChild("div", "style", color);
		}
	}

	public void sendMessage(String message) {
		Calendar now = Calendar.getInstance();
		now.setTime(new Date());
		addDateOnDayChange(now);

		//[ 04:38:20 PM ] Nodename: Blah blah blah.
		log.addChild("p", "[ "+timestampFormat.format(now.getTime())+" ] "+ username +": "+message);
		lastMessageReceived = now;

		//Send this message to others.
		for (Participant participant : participants.values()) {
			if (participant.directlyConnected) {
				participant.peerNode.sendChatMessage(now.getTime(), globalIdentifier, message);
			}
		}
	}

	/**
	 * List the current date if the day changed.
	 * @param now What date to regard as the current one.
	 */
	private void addDateOnDayChange(Calendar now) {
		if (now.get(Calendar.DAY_OF_YEAR) != lastMessageReceived.get(Calendar.DAY_OF_YEAR) ||
		        now.get(Calendar.YEAR) != lastMessageReceived.get(Calendar.YEAR)) {
			log.addChild("p", dayChangeFormat.format(now.getTime()));
		}
	}

	private String l10n(String key) {
		return NodeL10n.getBase().getString("N2NChatRoom."+key);
	}

	private String l10n(String key, String pattern, String value) {
		return l10n(key, new String[] { pattern }, new String[] { value });
	}

	private String l10n(String key, String[] pattern, String[] value) {
		return NodeL10n.getBase().getString("N2NChatRoom."+key, pattern, value);
	}

	/**
	 * Used to keep track of participants in a chat room. Records whether they are directly connected, whether this
	 * node routes for them, what DarknetPeerNode is used to contact them, and what color their name is.
	 */
	private class Participant implements Comparable<Participant> {

		public final boolean directlyConnected;
		public final boolean locallyInvited;
		public final String name;
		public final DarknetPeerNode peerNode;
		public final Color nameColor;

		/**
		 * Constructor for a participant. Does nothing more than assign values.
		 * @param identityHash Identity hash of the participant node. Used to calculate name color; not stored.
		 * @param directlyConnected Whether this participant is directly connected to this node. If so,messages
		 * will be sent to peerNode. If not, peerNode is authorized to route their messages and remove request.
		 * @param locallyInvited Whether this node invited the participant. If so, this node will route their
		 * anything from them to all other directly connected peers.
		 * @param name The name of this participant.
		 * @param peerNode If directly connected, used to send messages. If not directly connected, only this
		 * node is authorized to route things for this participant.
		 */
		public Participant(int identityHash, String name, DarknetPeerNode peerNode, boolean directlyConnected,
			        boolean locallyInvited) {
			this.name = name;
			this.peerNode = peerNode;
			this.directlyConnected = directlyConnected;
			this.locallyInvited = locallyInvited;

			//Bits 24-31 map to ~40%-85% luminosity to keep the colors distinguishable and visible on white.
			//Bits 0-23 are used by Color for RGB.
			HSLColor colorManipulator = new HSLColor(new Color(identityHash));
			byte luminanceByte = (byte)(identityHash >>> 24);
			float luminance = luminanceByte/127*22.5f+62.5f;
			colorManipulator.adjustLuminance(luminance);
			nameColor = colorManipulator.getRGB();
		}

		public int compareTo(Participant other) {
			return name.compareTo(other.name);
		}
	}
}
