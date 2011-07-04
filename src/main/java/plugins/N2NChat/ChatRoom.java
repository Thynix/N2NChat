package plugins.N2NChat;

import freenet.l10n.PluginL10n;
import freenet.node.DarknetPeerNode;
import freenet.support.Base64;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;

import java.awt.Color;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.Arrays;

/**
 * The ChatRoom class keeps track of what has been said in a chat room, parses new messages, formats them, and is
 * responsible for system messages such as joins, leaves, and day changes.
 */
public class ChatRoom {

	private Calendar lastMessageReceived;
	/**Everyone in this room. The key is the public key hash, the value is the participant object.*/
	private HashMap<byte[], Participant> participants;
	//TODO: Move list of DarknetPeerNodes to N2NChatPlugin and check for alternate routes from there.
	private HashMap<byte[], DarknetPeerNode> peerNodes;
	/**Invites sent out to this room. Key is public key hash of the peer the invite was sent to,
	 * Value is the offered username.
	 */
	private HashMap<byte[], String> sentInvites;
	private HTMLNode log;
	private HTMLNode participantListing;
	private String roomName;
	private long globalIdentifier;
	private String username;
	private PluginL10n l10n;

	//TODO: Participant icons for whether messages sent to them have gone through. Would require ACKs in the case of
	//TODO: participants that are not directly connected.
	/**
	 * Initializes date formatters and starts the room off with a timestamp of the day.
	 * @param roomName Name of the chat room. Used for local display and in invitations.
	 * @param globalIdentifier Global ID; used by all participants to specify what room a message is for.
	 * @param username This node's username in this chat.
	 * @param peerNodes This node's Darknet peers. Used to search for direct connections to nodes invited by others..
	 * @param l10n Plugin l10n from N2NChatPlugin.
	 */
	public ChatRoom(String roomName, long globalIdentifier, String username, DarknetPeerNode[] peerNodes, PluginL10n l10n){
		this.roomName = roomName;
		this.globalIdentifier = globalIdentifier;
		this.username = username;
		this.l10n = l10n;
		updatePeerNodes(peerNodes);
		participants = new HashMap<byte[], Participant>();
		sentInvites = new HashMap<byte[], String>();
		lastMessageReceived = Calendar.getInstance();
		lastMessageReceived.setTime(new Date());
		//TODO: What size should this box be? Will the automatic size be reasonable?
		//TODO: Likely full width with limited height.
		log = new HTMLNode("div", "style", "overflow:scroll;background-color:white;height:100%");
		//Start out the chat by setting the day.
		log.addChild("ul", "style", "list-style-type:none;");
		log.addChild("li", N2NChatPlugin.dayChangeFormat.format(lastMessageReceived.getTime()));
		updateParticipantListing();
	}

	/**
	 * Initializes date formatters and starts the room off with a timestamp of the day.
	 * @param roomName Name of the chat room. Used for local display and in invitations.
	 * @param globalIdentifier Global ID; used by all participants to specify what room a message is for.
	 * @param username This node's username in this chat.
	 * @param peerNodes This node's Darknet peers. Used to search for direct connections to nodes invited by others..
	 * @param l10n Plugin l10n from N2NChatPlugin.
	 * @param invitedBy DarknetPeerNode that invited this node to the chat.
	 */
	public ChatRoom(String roomName, long globalIdentifier, String username, DarknetPeerNode[] peerNodes,
	        PluginL10n l10n, DarknetPeerNode invitedBy) {
		this(roomName, globalIdentifier, username, peerNodes, l10n);
		participants.put(invitedBy.getPubKeyHash(), 
		        new Participant(invitedBy.getPubKeyHash(), invitedBy.getName(), invitedBy, true, false));
		updateParticipantListing();
	}

	public void updatePeerNodes(DarknetPeerNode[] updatedPeerNodes) {
		this.peerNodes = new HashMap<byte[], DarknetPeerNode>();
		for (DarknetPeerNode node : updatedPeerNodes) {
			peerNodes.put(node.getPubKeyHash(), node);
		}
		//TODO: Check new peers for direct connections to those currently routed and backup routes.
	}

	/**
	 * Adds a directly connected participant that was invited locally.
	 * This node will route messages to and from them.
	 * @param darknetParticipant The peer that was invited.
	 * @param username The name of this user as referred to within this chat.
	 * @return True if the participant was added, false if not.
	 */
	public boolean inviteParticipant(DarknetPeerNode darknetParticipant, String username) {
		//Check if the participant is already participating.
		if (addParticipant(darknetParticipant.getPubKeyHash(), darknetParticipant.getName(), darknetParticipant,
		        true)) {
			//They aren't; this is a fresh join.
			for (byte[] pubKeyHash : participants.keySet()) {
				if (!Arrays.equals(pubKeyHash, darknetParticipant.getPubKeyHash()) &&
				        participants.get(pubKeyHash).directlyConnected) {
					//Send all other participants a join for the new participant.
					sendJoin(participants.get(pubKeyHash).peerNode,
						darknetParticipant.getPubKeyHash(),
						username);
					//Send the new participant joins for all other participants.
					sendJoin(darknetParticipant, pubKeyHash, participants.get(pubKeyHash).name);
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Adds a participant that was invited remotely.
	 * @param joinedPublicKeyHash The public key hash of the new participant.
	 * @param name The name of the new participant.
	 * @param routedBy The peer that routed the invite. This peer will be authorized to route all other things
	 * with regards to this participant.
	 * @return True if the participant was added, false otherwise.
	 */
	public boolean joinedParticipant(byte[] joinedPublicKeyHash, String name, DarknetPeerNode routedBy) {
		/*TODO: Query directly connected participants for backup routing paths.*/
		if (addParticipant(joinedPublicKeyHash, name, routedBy, false)) {
			for (byte[] pubKeyHash : participants.keySet()) {
				//Route this join to all participants this node routes for, provided the join was not received from them.
				if (participants.get(pubKeyHash).locallyInvited && !Arrays.equals(pubKeyHash, routedBy.getPubKeyHash())) {
					sendJoin(participants.get(pubKeyHash).peerNode, joinedPublicKeyHash, name);
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Adds a participant to this chatroom so that messages they send here can be received.
	 * @param publicKeyHash Public key hash of the participant to add.
	 * @param name Name of the participant to add.
	 * @param peerNode If directly connected, the DarknetPeerNode to invite. If not, the DarknetPeerNode who invited
	 * this participant and is authorized to route their messages.
	 * @param invitedLocally True if invited by the local node, false if invited by someone else.
	 * @return True if participant was added, false otherwise.
	 */
	private boolean addParticipant(byte[] publicKeyHash, String name, DarknetPeerNode peerNode, boolean invitedLocally) {
		//A participant cannot be in a chat room multiple times at once.
		if (participants.containsKey(publicKeyHash)) {
			return false;
		}
		boolean directlyConnected = peerNodes.containsKey(publicKeyHash);
		participants.put(publicKeyHash, new Participant(publicKeyHash, name, peerNode, directlyConnected,
		        invitedLocally));
		log.addChild("li", l10n("joined", "name", name));
		updateParticipantListing();
		return true;
	}

	/**
	 * Removes a participant from the chat room.
	 * @param removePubKeyHash Public key hash of the participant to remove.
	 * @param senderPubKeyHash Public key hash of the participant that sent the removal request. In order for the
	 * removal to be valid, this must either be the participant authorized to route this person's messages or the
	 * participant themselves.
	 * @param connectionProblem Used internally to indicate whether a departure message was received or the node
	 * disconnected. If true "lost connection" is used rather than "left".
	 * @return True if the participant was removed; false if not. More detailed error messages are written to
	 * the log.
	 */
	//TODO: Is putting a public key hash to string a reasonable thing to do in the log? No, it's an array!
	//TODO: Should this return a more descriptive state? Will other things care whether the removal was successful?
	public boolean removeParticipant(byte[] removePubKeyHash, byte[] senderPubKeyHash, boolean connectionProblem) {
		String error = checkPresenceAndAuthorization("remove.", removePubKeyHash, senderPubKeyHash);
		if (error != null) {
			Logger.warning(this, l10n("removeReceived",
			        new String[] { "removeName", "removeHash", "fromName", "fromHash" },
			        new String[] { findName(removePubKeyHash, removePubKeyHash),
			                Base64.encode(removePubKeyHash),
			                peerNodes.get(senderPubKeyHash).getName(),
			                Base64.encode(senderPubKeyHash) })
			        +' '+error+' '+l10n("roomInfo",
			        new String[] { "roomName", "globalIdentifier"},
			        new String[] {roomName, String.valueOf(globalIdentifier) }));
			return false;
		}

		//The identity to remove and the sender of the request are in the chat room, and the sender of the
		//request is authorized to remove the identity.
		if (connectionProblem) {
			log.addChild(l10n("lostConnection", "name", participants.get(removePubKeyHash).name));
		} else {
			log.addChild(l10n("left", "name", participants.get(removePubKeyHash).name));
		}
		participants.remove(removePubKeyHash);

		Set<byte[]> identityHashes = participants.keySet();
		for (byte[] identityHash : identityHashes) {
			//Remove from the room any other participants the leaving node routed for.
			if (participants.get(identityHash).peerNode.getPubKeyHash() == removePubKeyHash) {
				participants.remove(identityHash);
				log.addChild(l10n("lostConnection", "name", participants.get(identityHash).name));
			//Send this disconnect to all participants this node invited.
			} else if (participants.get(identityHash).locallyInvited) {
				sendLeave(participants.get(identityHash).peerNode, removePubKeyHash);
			}
		}
		return true;
	}

	public boolean containsParticipant(byte[] pubKeyHash) {
		return participants.keySet().contains(pubKeyHash);
	}

	public void disconnect() {
		for (Participant participant : participants.values()) {
			if (participant.directlyConnected) {
				//Null public key hash is not included in the field set, and receiving nodes will
				//fill it in with the sender's public key hash.
				sendLeave(participant.peerNode, null);
			}
		}
	}

	/**
	 * Gets a name for a given identity hash, if possible. Used internally to allow for the nickname of darknet
	 * nodes in error messages.
	 * @param targetPubKeyHash Public key hash of the subject.
	 * @param senderPubKeyHash Public key hash of the peer that sent the message.
	 * @return Either a name or "an unknown participant"
	 */
	private String findName (byte[] targetPubKeyHash, byte[] senderPubKeyHash) {
		//Sender must be connected in order to send something, so their name is always known.
		if (Arrays.equals(senderPubKeyHash, targetPubKeyHash)) {
			return peerNodes.get(senderPubKeyHash).getName();
		} else if (participants.containsKey(targetPubKeyHash)) {
			return participants.get(targetPubKeyHash).name;
		} else {
			return "an unknown participant";
		}
	}

	/**
	 * Checks whether the identities are in the chat room, and whether the sender is authorized to route actions
	 * taken by the target identity.
	 * @param prefix l10n prefix to be added to "ChatRoom." for error message.
	 * @param targetPubKeyHash Public key hash of the participant the event concerns.
	 * @param senderPubKeyHash Public key hash of the sender.
	 * @return null if the identities are in the room and the sender is authorized.
	 * If there's a problem it returns descriptive text.
	 */
	private String checkPresenceAndAuthorization(String prefix, byte[] targetPubKeyHash, byte[] senderPubKeyHash) {
		//Neither the sender nor the identity it concerns are in the chat room.
		if (!participants.containsKey(senderPubKeyHash) && !participants.containsKey(targetPubKeyHash)) {
			if (senderPubKeyHash == targetPubKeyHash) {
				return l10n("nonparticipant");
			} else {
				return l10n(prefix+"senderAndTargetNonparticipant");
			}
		}

		//The sender of the request is not in the chat room and the identity to remove is.
		if (!participants.containsKey(senderPubKeyHash) && participants.containsKey(targetPubKeyHash)) {
			return l10n(prefix+"senderNonparticipant");
		}

		//The identity to remove is not in the chat room and the sender of the request is.
		if (!participants.containsKey(targetPubKeyHash)) {
			return l10n(prefix+"targetNonparticipant");
		}

		//The sender of the request and identity to remove are in the chat room, but the sender of the request
		//is not authorized to remove that identity. This may occur in legitimate circumstances if this node has
		//a direct connection to a peer that the sender of the request invited.
		if (participants.get(targetPubKeyHash).peerNode.getPubKeyHash() != senderPubKeyHash) {
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

	public String getRoomName() {
		return roomName;
	}

	/**
	 * Returns whether a participant with that nickname is already in the room. Used to prevent nickname conflicts
	 * when sending invites.
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
	 * @param composedBy The public key hash of the composer of the message.
	 * @param timeComposed The time at which the message was composed.
	 * @param deliveredBy The public key hash of the darknet peer node that delivered the message.
	 * @param message The message to add.
	 * @return True if the message was added; false if the message's composer is not in this chat room or the
	 * sender is not in this chat room.
	 */
	public boolean receiveMessage(byte[] composedBy, Date timeComposed, byte[] deliveredBy, String message) {
		String error = checkPresenceAndAuthorization("message.", composedBy, deliveredBy);
		if (error != null) {
			Logger.warning(this, l10n("messageReceived",
			        new String[] { "composerName", "composerHash", "fromName", "fromHash" },
			        new String[] { findName(composedBy, deliveredBy),
			                Base64.encode(composedBy),
			                peerNodes.get(deliveredBy).getName(),
			                Base64.encode(deliveredBy) })
			        +' '+error+' '+l10n("roomInfo",
			        new String[] { "roomName", "globalIdentifier"},
			        new String[] {roomName, String.valueOf(globalIdentifier) }));
			return false;
		}

		Calendar now = Calendar.getInstance();
		now.setTime(new Date());
		addDateOnDayChange(now);

		//Ex: [ 04:38:30 PM ]
		//Ex: Tooltip of time composed.
		HTMLNode messageLine = log.addChild("li", "title",
		        l10n("composed", "time", N2NChatPlugin.messageComposedFormat.format(timeComposed.getTime())),
		        "[ "+ N2NChatPlugin.messageReceivedFormat.format(now.getTime())+" ] ");

		Participant user = participants.get(composedBy);
		//Ex: BillyBob:
		//With text color based on public key hash.
		Color textColor = user.nameColor;
		String name = user.name;
		messageLine.addChild("div", "style", "color:rgb("+textColor.getRed()+','+textColor.getGreen()+','+
		        textColor.getBlue()+");text-shadow:2px 2px 4px #000000;display:inline", name+": ");

		//Ex: Blah blah blah.
		messageLine.addChild("#", message);

		lastMessageReceived = now;

		//If this node routes this user's messages, send it to all other directly connected participants.
		if (user.locallyInvited) {
			for (Participant participant : participants.values()) {
				if (participant.directlyConnected && participant != user) {
					sendMessage(participant.peerNode, timeComposed, message, composedBy);
				}
			}
		}

		return true;
	}

	private void updateParticipantListing() {
		//Sort participants alphabetically.
		Participant[] sortedParticipants = participants.values().toArray(new Participant[participants.size()]);
		Arrays.sort(sortedParticipants);

		participantListing = new HTMLNode("ul", "style", "overflow:scroll;background-color:white;list-style-type:none;");
		participantListing.addChild("li", l10n("totalParticipants", "numberOf",
		        String.valueOf(sortedParticipants.length+1)));

		//TODO: Username coloring
		//List self
		participantListing.addChild("li", username+" (You)");

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
				        new String[] { participants.get(participant.peerNode.getPubKeyHash()).name,
				                participant.peerNode.getName(),
				                participant.peerNode.getIdentityString() });
			}
			Color nameColor = participant.nameColor;
			String color = "color:rgb("+nameColor.getRed()+','+nameColor.getGreen()+','+nameColor.getBlue()+");";
			participantListing.addChild("li",
			        new String[] { "style", "title" },
			        new String [] { color+";text-shadow:2px 2px 4px #000000", routing }, participant.name);
		}

		//List pending invites
		for (String name : sentInvites.values()) {
			//TODO: username coloring
			participantListing.addChild("li", name+" (Invite pending)");
		}
	}

	public void sendOwnMessage(String message) {
		Calendar now = Calendar.getInstance();
		now.setTime(new Date());
		addDateOnDayChange(now);

		//[ 04:38:20 PM ] Username: Blah blah blah.
		log.addChild("li", "[ "+ N2NChatPlugin.messageReceivedFormat.format(now.getTime())+" ] "+ username +": "+message);
		lastMessageReceived = now;

		//Send this message to others.
		for (Participant participant : participants.values()) {
			if (participant.directlyConnected) {
				sendMessage(participant.peerNode, now.getTime(), message, null);
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
			log.addChild("li", N2NChatPlugin.dayChangeFormat.format(now.getTime()));
		}
	}

	//TODO: is it reasonable to queue chat messages? Desired? Wouldn't they leave the chat room if not connected though?
	//TODO: Remove peers from chat when they disconnect.
	/**
	 * Basic sending message to darknet peer. Adds globalIdentifier and type to anything else in the SimpleFieldSet.
	 * Does not queue.
	 * @param darkPeer The DarknetPeerNode the message will be sent to.
	 * @param fs The SimpleFieldSet thus far. Can be null.
	 * @param type The type of the message. (Ex: N2NChatPlugin.MESSAGE)
	 */
	private void sendBase(DarknetPeerNode darkPeer, SimpleFieldSet fs, int type) {
		if (fs == null) {
			fs = new SimpleFieldSet(true);
		}
		fs.put("globalIdentifier", globalIdentifier);
		fs.put("type", type);
		darkPeer.sendNodeToNodeMessage(fs, N2NChatPlugin.N2N_MESSAGE_TYPE_CHAT, true,
		        System.currentTimeMillis(), false);
	}

	/**
	 * Sends a chat message to the specified darknet peer.
	 * @param darkPeer The darknet peer to send the message to.
	 * @param timeComposed The time the message was composed.
	 * @param message The text of the message to send.
	 * @param composedBy The public key hash of the participant that composed this message. Can be null, in which
	 * case receiving nodes are to assume the sender of the message is the composer.
	 */
	private void sendMessage(DarknetPeerNode darkPeer, Date timeComposed, String message, byte[] composedBy) {
		SimpleFieldSet fs = new SimpleFieldSet(true);
		fs.put("timeComposed", timeComposed.getTime());

		//TODO: Why doesn't SFS allow byte arrays?
		try {
			fs.putSingle("text", Base64.encode(message.getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e) {
			throw new Error("This JVM does not support UTF-8! Cannot encode message.");
		}

		formatPubKeyHash(composedBy, fs);
		System.out.println("Sent message in room "+globalIdentifier+" to "+darkPeer.getName());
		sendBase(darkPeer, fs, N2NChatPlugin.MESSAGE);
	}

	/**
	 * Sends the specified darknet peer a notification that a participant with the given public key hash has joined.
	 * This means that unless that peer is directly connected to a node with this public key hash, it will accept
	 * messages from that participant through this node.
	 * @param darkPeer The darknet peer to send the notification to.
	 * @param pubKeyHash The public key hash of the participant that has joined.
	 * @param username The username of the newly joined participant.
	 */
	private void sendJoin(DarknetPeerNode darkPeer, byte[] pubKeyHash, String username) {
		SimpleFieldSet fs = formatPubKeyHash(pubKeyHash);
		try {
			fs.putSingle("username", Base64.encode(username.getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e) {
			throw new Error("This JVM does not support UTF-8! Cannot encode join.");
		}
		System.out.println("Sent join of "+username+" in room "+globalIdentifier+" to "+darkPeer.getName());
		sendBase(darkPeer, fs, N2NChatPlugin.JOIN);
	}

	/**
	 * Sends the specified darknet peer a notification that a participant with the given public key hash has left.
	 * If this darknet peer is not accepting messages for this participant through this node, it will ignore this
	 * notification.
	 * @param darkPeer The darknet peer to send the notification to.
	 * @param pubKeyHash The public key hash of the participant that has left.
	 */
	private void sendLeave(DarknetPeerNode darkPeer, byte[] pubKeyHash) {
		System.out.println("Sent leave in room "+globalIdentifier+" to "+darkPeer.getName());
		sendBase(darkPeer, formatPubKeyHash(pubKeyHash), N2NChatPlugin.LEAVE);
	}

	/**
	 * Whether or not a sent invite to that peer is pending.
	 * @param pubKeyHash public key hash to check
	 * @return true if an invite to that peer is pending, false if not.
	 */
	public boolean inviteSentTo(byte[] pubKeyHash) {
		return sentInvites.containsKey(pubKeyHash);
	}

	public boolean sendInviteOffer(DarknetPeerNode darkPeer, String username) {
		if (sentInvites.containsKey(darkPeer.getPubKeyHash())) {
			return false;
		}

		SimpleFieldSet fs = new SimpleFieldSet(true);
		try {
			fs.putSingle("username", Base64.encode(username.getBytes("UTF-8")));
			fs.putSingle("roomName", Base64.encode(roomName.getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e) {
			throw new Error("JVM does not support UTF-8! Cannot encode username string!");
		}
		System.out.println("Sent invite offer for room "+globalIdentifier+" to "+darkPeer.getName());
		sendBase(darkPeer, fs, N2NChatPlugin.OFFER_INVITE);
		sentInvites.put(darkPeer.getPubKeyHash(), username);
		updateParticipantListing();
		return true;
	}

	public boolean sendInviteRetract(DarknetPeerNode darkPeer) {
		if (sentInvites.containsKey(darkPeer.getPubKeyHash())) {
			sendBase(darkPeer, null, N2NChatPlugin.RETRACT_INVITE);
			sentInvites.remove(darkPeer.getPubKeyHash());
			return true;
		}
		return false;
	}

	private boolean receiveInvite(DarknetPeerNode darkPeer, boolean inviteParticipant) {
		if (!sentInvites.containsKey(darkPeer.getPubKeyHash())) {
			return false;
		}
		if (inviteParticipant) {
			inviteParticipant(darkPeer, sentInvites.get(darkPeer.getPubKeyHash()));
		}
		sentInvites.remove(darkPeer.getPubKeyHash());
		updateParticipantListing();
		return true;
	}

	public boolean receiveInviteAccept(DarknetPeerNode darkPeer) {
		return receiveInvite(darkPeer, true);
	}

	public boolean receiveInviteReject(DarknetPeerNode darkPeer) {
		return receiveInvite(darkPeer, false);
	}

	/**
	 * Returns a SimpleFieldSet with the applicable pubKeyHash field.
	 * @param pubKeyHash The key hash to add. Can be null, in which case the field will not be added.
	 * @param fs To add fields to. Can be null, in which case a new one will be created.
	 * @return Empty or unmodified SimpleFieldSet if pubKeyHash is null.
	 */
	private SimpleFieldSet formatPubKeyHash(byte[] pubKeyHash, SimpleFieldSet fs) {
		if (fs == null) {
			fs = new SimpleFieldSet(true);
		}
		if (pubKeyHash != null) {
			fs.putSingle("pubKeyHash", Base64.encode(pubKeyHash));
		}
		return fs;
	}

	private SimpleFieldSet formatPubKeyHash(byte[] pubKeyHash) {
		return formatPubKeyHash(pubKeyHash, null);
	}

	private String l10n(String key) {
		return l10n.getBase().getString("room."+key);
	}

	private String l10n(String key, String pattern, String value) {
		return l10n(key, new String[]{pattern}, new String[]{value});
	}

	private String l10n(String key, String[] pattern, String[] value) {
		return l10n.getBase().getString("room." + key, pattern, value);
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
		 * @param publicKeyHash Public key hash of the participant node. Used to calculate name color; not stored.
		 * @param directlyConnected Whether this participant is directly connected to this node. If so,messages
		 * will be sent to peerNode. If not, peerNode is authorized to route their messages and remove request.
		 * @param locallyInvited Whether this node invited the participant. If so, this node will route their
		 * anything from them to all other directly connected peers.
		 * @param name The name of this participant.
		 * @param peerNode If directly connected, used to send messages. If not directly connected, only this
		 * node is authorized to route things for this participant.
		 */
		public Participant(byte[] publicKeyHash, String name, DarknetPeerNode peerNode, boolean directlyConnected,
			        boolean locallyInvited) {
			this.name = name;
			this.peerNode = peerNode;
			this.directlyConnected = directlyConnected;
			this.locallyInvited = locallyInvited;

			//Bits 24-31 map to ~40%-70% luminosity to keep the colors distinguishable and visible on white.
			//Bits 0-23 are used by Color for RGB.
			//TODO: Assuming hash is at least 4 bytes. How long is this actually? Check DarknetCrypto.
			assert(publicKeyHash.length >= 4);
			int hashInt = publicKeyHash[0] | (publicKeyHash[1] << 8) | (publicKeyHash[2] << 16);
			HSLColor colorManipulator = new HSLColor(new Color(hashInt));
			//[3] for luminance bit. 127 (-128) is the maximum value of a signed byte, and is scaled 20 from 60.
			float luminance = publicKeyHash[3]/127*15f+55f;
			colorManipulator.adjustLuminance(luminance);
			nameColor = colorManipulator.getRGB();
		}

		public int compareTo(Participant other) {
			return name.compareTo(other.name);
		}
	}
}
