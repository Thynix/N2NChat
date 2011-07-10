package plugins.N2NChat;

import freenet.l10n.PluginL10n;
import freenet.node.DarknetPeerNode;
import freenet.support.Base64;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;

import java.awt.*;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * The ChatRoom class keeps track of what has been said in a chat room, parses new messages, formats them, and is
 * responsible for system messages such as joins, leaves, and day changes.
 */
public class ChatRoom {

	private Calendar lastMessageReceived;
	/**
	 * All participants present in this room except for the local node.
	 * The key is the public key hash, the value is the Participant object.
	 */
	private HashMap<ByteArray, Participant> participants;
	//TODO: Move list of DarknetPeerNodes to N2NChatPlugin and check for alternate routes from there.
	private HashMap<ByteArray, DarknetPeerNode> peerNodes;
	/**
	 * Invites sent out to this room. Key is public key hash of the peer the invite was sent to,
	 * Value is their NameEntry.
	 */
	private HashMap<ByteArray, NameEntry> sentInvites;
	private HTMLNode log;
	private HTMLNode participantListing;
	private String roomName;
	private long globalIdentifier;
	/**
	 * Username and styling of this node in this room.
	 */
	private NameEntry username;
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
		this.username = new NameEntry(username, "font-weight:bold;", true);
		this.l10n = l10n;
		updatePeerNodes(peerNodes);
		participants = new HashMap<ByteArray, Participant>();
		sentInvites = new HashMap<ByteArray, NameEntry>();
		lastMessageReceived = Calendar.getInstance();
		lastMessageReceived.setTime(new Date());
		//TODO: What size should this box be? Will the automatic size be reasonable?
		//TODO: Likely full width with limited height.
		log = new HTMLNode("ul", "style", "overflow:scroll;background-color:white;height:100%;list-style-type:none;");
		//Start out the chat by setting the day.
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
		ByteArray pubKeyHash = new ByteArray(invitedBy.getPubKeyHash());
		participants.put(pubKeyHash, new Participant(invitedBy.getName(), pubKeyHash, invitedBy, true, false));
		updateParticipantListing();
	}

	//TODO: This should move out of the chat room and into N2NPlugin to avoid having multiple copies.
	public void updatePeerNodes(DarknetPeerNode[] updatedPeerNodes) {
		this.peerNodes = new HashMap<ByteArray, DarknetPeerNode>();
		for (DarknetPeerNode node : updatedPeerNodes) {
			peerNodes.put(new ByteArray(node.getPubKeyHash()), node);
		}
		//TODO: Check new peers for direct connections to those currently routed and backup routes.
	}

	/**
	 * Adds a directly connected participant that was invited locally.
	 * This node will route messages to and from them.
	 * @param newParticipantPeer The peer that was invited.
	 * @param username The name of this user as referred to within this chat.
	 * @return True if the participant was added, false if not.
	 */
	public boolean inviteParticipant(DarknetPeerNode newParticipantPeer, String username) {
		//Check if the participant is already participating.
		if (addParticipant(new ByteArray(newParticipantPeer.getPubKeyHash()), newParticipantPeer.getName(),
		        newParticipantPeer, true, true)) {
			//They aren't; this is a fresh join.
			Participant newParticipant = participants.get(new ByteArray(newParticipantPeer.getPubKeyHash()));
			for (ByteArray pubKeyHash : participants.keySet()) {
				Participant existingParticipant = participants.get(pubKeyHash);
				if (!pubKeyHash.equals(new ByteArray(newParticipantPeer.getPubKeyHash())) &&
				        existingParticipant.directlyConnected) {
					//Send all other participants a join for the new participant.
					sendJoin(existingParticipant.peerNode, newParticipant, true);
					//Send the new participant silent joins for all other participants.
					sendJoin(newParticipantPeer, existingParticipant, false);
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
	 * @param displayJoin Whether the join should display a message.
	 * @return True if the participant was added, false otherwise.
	 */
	public boolean joinedParticipant(ByteArray joinedPublicKeyHash, String name, DarknetPeerNode routedBy, boolean displayJoin) {
		/*TODO: Query directly connected participants for backup routing paths.*/
		if (addParticipant(joinedPublicKeyHash, name, routedBy, false, displayJoin)) {
			Participant newParticipant = participants.get(joinedPublicKeyHash);
			Logger.minor(this, "Received join for "+newParticipant.name+" from "+routedBy.getName()+" in room '"+roomName+"' ("+globalIdentifier+") displayJoin="+displayJoin);
			for (ByteArray pubKeyHash : participants.keySet()) {
				//Route this join to all directly connected participants,
				if (participants.get(pubKeyHash).directlyConnected && !pubKeyHash.equals(new ByteArray(routedBy.getPubKeyHash()))) {
					sendJoin(participants.get(pubKeyHash).peerNode, newParticipant, true);
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Adds a participant to this chat room so that messages they send here can be received.
	 * @param publicKeyHash Public key hash of the participant to add.
	 * @param name Name of the participant to add.
	 * @param peerNode If directly connected, the DarknetPeerNode to invite. If not, the DarknetPeerNode who invited
	 * this participant and is authorized to route their messages.
	 * @param invitedLocally True if invited by the local node, false if invited by someone else.
	 * @param displayJoin Whether the join should be displayed in the messages panel. For example, if a newly invited
	 * participant is receiving a join about an existing participant, it probably shouldn't display a join.
	 * @return True if participant was added, false otherwise.
	 */
	private boolean addParticipant(ByteArray publicKeyHash, String name, DarknetPeerNode peerNode,
		       boolean invitedLocally, boolean displayJoin) {
		//A participant cannot be in a chat room multiple times at once.
		if (participants.containsKey(publicKeyHash)) {
			return false;
		}
		boolean directlyConnected = publicKeyHash.equals(new ByteArray(peerNode.getPubKeyHash()));
		//TODO: If this participant was invited by someone else but is directly connected, connect to them directly.
		//TODO: If they're directly connected, any messages would be echoed to them, which would cause
		//TODO: duplicates on their end as whoever invited them would also route those messages.
		//TODO: Should directlyConnected and locallyInvited be replaced with (routeFor and) routeTo?
		/*if (directlyConnected) {
			peerNode = peerNodes.get(publicKeyHash);
		}*/
		Participant newPart = new Participant(name, publicKeyHash, peerNode, directlyConnected, invitedLocally);
		participants.put(publicKeyHash, newPart);
		updateParticipantListing();
		
		if (displayJoin) {
			HTMLNode line = log.addChild("li");
			line.addChild("div", "style", newPart.nameStyle+"display:inline;", name);
			line.addChild("#", ' '+l10n("joined"));
		}
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
	//TODO: Should this return a more descriptive state? Will other things care whether the removal was successful?
	public boolean removeParticipant(ByteArray removePubKeyHash, ByteArray senderPubKeyHash, boolean connectionProblem) {
		String error = checkPresenceAndAuthorization("remove.", removePubKeyHash, senderPubKeyHash);
		if (error != null) {
			Logger.warning(this, l10n("removeReceived",
				new String[]{"removeName", "removeHash", "fromName", "fromHash"},
				new String[]{findName(removePubKeyHash, removePubKeyHash),
				        Base64.encode(removePubKeyHash.getBytes()),
				        peerNodes.get(senderPubKeyHash).getName(),
				        Base64.encode(senderPubKeyHash.getBytes())})
				+ ' ' + error + ' ' + l10n("roomInfo",
				new String[]{"roomName", "globalIdentifier"},
				new String[]{roomName, String.valueOf(globalIdentifier)}));
			return false;
		}

		//The identity to remove and the sender of the request are in the chat room, and the sender of the
		//request is authorized to remove the identity.
		Participant removedParticipant = participants.get(removePubKeyHash);
		if (connectionProblem) {
			HTMLNode line = log.addChild("li");
			line.addChild("div", "style", removedParticipant.nameStyle+"display:inline;", removedParticipant.name+' ');
			line.addChild("#", ' '+l10n("lostConnection"));
		} else {
			HTMLNode line = log.addChild("li");
			line.addChild("div", "style", removedParticipant.nameStyle+"display:inline;", removedParticipant.name+' ');
			line.addChild("#", ' '+l10n("left"));
		}
		Logger.minor(this, "Received leave for "+removedParticipant.name+" from "+participants.get(senderPubKeyHash).name+" in room '"+roomName+"' ("+globalIdentifier+"). ConnectionProblem="+connectionProblem);
		participants.remove(removePubKeyHash);

		Set<ByteArray> keySet = participants.keySet();
		for (ByteArray pubKeyHash : keySet) {
			Participant participant = participants.get(pubKeyHash);
			//Remove from the room any other participants the leaving node routed for.
			if (removePubKeyHash.equals(new ByteArray(participant.peerNode.getPubKeyHash()))) {
				//TODO: This assumes from a localization standpoint that the name comes before the
				//TODO: phrase. How to add HTML tags that aren't sanitized away in a localized string?
				HTMLNode line = log.addChild("li");
				line.addChild("div", "style", participant.nameStyle+"display:inline;", participant.name);
				line.addChild("#", ' '+l10n("lostConnection"));
				participants.remove(pubKeyHash);
			//Send this disconnect to all participants this node is connected to, provided it didn't deliver this.
			//pubKeyHash will be equal to the peerNode.getPubKeyHash() because it's locally invited
			//and thus directly connected.
			} else if (participant.directlyConnected && !senderPubKeyHash.equals(pubKeyHash)) {
				sendLeave(participant.peerNode, removePubKeyHash);
			}
		}
		updateParticipantListing();
		return true;
	}

	public boolean containsParticipant(ByteArray pubKeyHash) {
		return participants.containsKey(pubKeyHash);
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
	private String findName (ByteArray targetPubKeyHash, ByteArray senderPubKeyHash) {
		//Sender must be connected in order to send something, so their name is always known.
		if (senderPubKeyHash.equals(targetPubKeyHash)) {
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
	private String checkPresenceAndAuthorization(String prefix, ByteArray targetPubKeyHash, ByteArray senderPubKeyHash) {
		//Neither the sender nor the identity it concerns are in the chat room.
		if (!participants.containsKey(senderPubKeyHash) && !participants.containsKey(targetPubKeyHash)) {
			if (senderPubKeyHash.equals(targetPubKeyHash)) {
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

		//The sender of the request and target are in the chat room, but the sender of the request
		//is not authorized to route for the target. This may occur in legitimate circumstances if this node has
		//a direct connection to a peer that the sender of the request invited.
		if (!senderPubKeyHash.equals(new ByteArray(participants.get(targetPubKeyHash).peerNode.getPubKeyHash()))) {
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
	public boolean receiveMessage(ByteArray composedBy, Date timeComposed, ByteArray deliveredBy, String message) {
		String error = checkPresenceAndAuthorization("message.", composedBy, deliveredBy);
		if (error != null) {
			assert(composedBy != null && deliveredBy != null);
			Logger.warning(this, l10n("messageReceived",
				new String[]{"composerName", "composerHash", "fromName", "fromHash"},
				new String[]{findName(composedBy, deliveredBy),
				        Base64.encode(composedBy.getBytes()),
				        peerNodes.get(deliveredBy).getName(),
				        Base64.encode(deliveredBy.getBytes())})
				+ ' ' + error + ' ' + l10n("roomInfo",
				new String[]{"roomName", "globalIdentifier"},
				new String[]{roomName, String.valueOf(globalIdentifier)}));
			Logger.minor(this, "participants contains the following key hashes:");
			for (ByteArray keyHash : participants.keySet()) {
				Logger.minor(this, Base64.encode(keyHash.getBytes()));
			}
			Logger.minor(this, "Done listing key hashes.");
			return false;
		}

		Calendar now = Calendar.getInstance();
		now.setTime(new Date());
		addDateOnDayChange(now);

		//Ex: [ 04:38:30 PM ]
		//Ex: Tooltip of time composed.
		HTMLNode messageLine = log.addChild("li", "title",
		        l10n("composed", "time", N2NChatPlugin.composedFormat.format(timeComposed.getTime())),
		        "[ "+ N2NChatPlugin.receivedFormat.format(now.getTime())+" ] ");

		Participant composer = participants.get(composedBy);
		//Ex: BillyBob:
		//With text color based on public key hash.
		messageLine.addChild("div", "style", composer.nameStyle+"display:inline;", composer.name+": ");

		//Ex: Blah blah blah.
		messageLine.addChild("#", message);

		lastMessageReceived = now;

		Participant sender = participants.get(deliveredBy);

		Logger.minor(this, "Received chat message composed by "+composer.name+" delivered by "+sender.name+" in room '"+roomName+"' ("+globalIdentifier+")");

		//TODO: How to avoid duplicate sending? Currently more than one node can't be directly connected
		//TODO: to any one participant, so for now it should be okay. When there's more interconnected routing,
		//TODO: should there be some kind of message/join/leave identifier so that duplicates can be dropped?
		//TODO: TCP sequence identifiers might be good to look into.
		for (Participant participant : participants.values()) {
			if (participant.directlyConnected && participant != sender) {
				sendMessage(participant.peerNode, timeComposed, message, composedBy);
			}
		}

		return true;
	}

	private void updateParticipantListing() {
		//Sort participants, pending invites, and this node alphabetically.
		ArrayList<NameEntry> names = new ArrayList<NameEntry>(participants.values());
		names.addAll(sentInvites.values());
		names.add(username);
		Collections.sort(names);

		participantListing = new HTMLNode("ul", "style", "overflow:scroll;background-color:white;list-style-type:none;");
		participantListing.addChild("li", l10n("participantsPresent", "numberOf",
		        String.valueOf(participants.size()+1)));

		//List participants with colored name text and routing information on tooltip.
		for (NameEntry entry : names) {
			//TODO: How do browsers behave given an empty title?
			String routing = "";
			String suffix = "";
			if (entry instanceof Participant) {
				//It's a participant, list connection information on tooltip.
				Participant participant = (Participant)entry;
				if (participant.pubKeyHash.equals(new ByteArray(participant.peerNode.getPubKeyHash()))) {
					routing = l10n("connectedDirectly",
					        new String[] { "nodeName", "nodeID" },
					        new String[] { participant.peerNode.getName(),
					                Base64.encode(participant.peerNode.getPubKeyHash()) });
				} else {
					routing = l10n("connectedThrough",
						new String[] { "nodeName", "nodeID", "pubKeyHash" },
						new String[] { participant.peerNode.getName(),
							Base64.encode(participant.peerNode.getPubKeyHash()),
							Base64.encode(participant.pubKeyHash.getBytes()) });
				}
			} else if (entry.equals(username)) {
				suffix = " ("+l10n("you")+')';
			} else {
				//It's an invite.TODO: Include which peer this is? It'll only really be an issue
				//TODO: if usernames can differ from node nicknames.
				suffix = " ("+l10n("invitePending")+')';
			}
			participantListing.addChild("li",
			        new String[] { "style", "title" },
			        new String[] { entry.nameStyle, routing },
			        entry.name+suffix);
		}
	}

	public void sendOwnMessage(String message) {
		Calendar now = Calendar.getInstance();
		now.setTime(new Date());
		addDateOnDayChange(now);

		//[ 04:38:20 PM ] Username: Blah blah blah.
		HTMLNode line = log.addChild("li", "[ "+ N2NChatPlugin.receivedFormat.format(now.getTime())+" ] ");
		line.addChild("div", "style", username.nameStyle+"display:inline;", username.name+": ");
		line.addChild("#", message);
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
	private void sendMessage(DarknetPeerNode darkPeer, Date timeComposed, String message, ByteArray composedBy) {
		SimpleFieldSet fs = new SimpleFieldSet(true);
		fs.put("timeComposed", timeComposed.getTime());

		//TODO: Why doesn't SFS allow byte arrays?
		try {
			fs.putSingle("text", Base64.encode(message.getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e) {
			throw new Error("This JVM does not support UTF-8! Cannot encode message.");
		}

		formatPubKeyHash(composedBy, fs);
		Logger.minor(this, "Sent message composed " + (composedBy == null ? "locally" : "by " +
		         participants.get(composedBy).name) + " in room '"+roomName+"' (" + globalIdentifier + ") to " + darkPeer.getName());
		sendBase(darkPeer, fs, N2NChatPlugin.MESSAGE);
	}

	/**
	 * Sends the specified darknet peer a notification that a participant with the given public key hash has joined.
	 * This means that unless that peer is directly connected to a node with this public key hash, it will accept
	 * messages from that participant through this node.
	 * @param sendTo The darknet peer to send the notification to.
	 * @param newParticipant The new participant.
	 * @param displayJoin Whether the join should generate a message.
	 */
	private void sendJoin(DarknetPeerNode sendTo, Participant newParticipant, boolean displayJoin) {
		SimpleFieldSet fs = formatPubKeyHash(newParticipant.pubKeyHash);
		try {
			fs.putSingle("username", Base64.encode(newParticipant.name.getBytes("UTF-8")));
			fs.put("displayJoin", displayJoin);
		} catch (UnsupportedEncodingException e) {
			throw new Error("This JVM does not support UTF-8! Cannot encode join.");
		}
		Logger.minor(this, "Sent join of " + newParticipant.name + " in room '"+roomName+"' (" + globalIdentifier + ") to " + sendTo.getName());
		sendBase(sendTo, fs, N2NChatPlugin.JOIN);
	}

	/**
	 * Sends the specified darknet peer a notification that a participant with the given public key hash has left.
	 * If this darknet peer is not accepting messages for this participant through this node, it will ignore this
	 * notification.
	 * @param darkPeer The darknet peer to send the notification to.
	 * @param pubKeyHash The public key hash of the participant that has left.
	 */
	private void sendLeave(DarknetPeerNode darkPeer, ByteArray pubKeyHash) {
		Logger.minor(this, "Sent leave in room "+globalIdentifier+" to "+darkPeer.getName());
		sendBase(darkPeer, formatPubKeyHash(pubKeyHash), N2NChatPlugin.LEAVE);
	}

	/**
	 * Whether or not a sent invite to that peer is pending.
	 * @param pubKeyHash public key hash to check
	 * @return true if an invite to that peer is pending, false if not.
	 */
	public boolean inviteSentTo(ByteArray pubKeyHash) {
		return sentInvites.containsKey(pubKeyHash);
	}

	public boolean sendInviteOffer(DarknetPeerNode darkPeer, String username) {
		ByteArray pubKeyHash = new ByteArray(darkPeer.getPubKeyHash());
		if (sentInvites.containsKey(pubKeyHash)) {
			return false;
		}

		SimpleFieldSet fs = new SimpleFieldSet(true);
		try {
			fs.putSingle("username", Base64.encode(username.getBytes("UTF-8")));
			fs.putSingle("roomName", Base64.encode(roomName.getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e) {
			throw new Error("JVM does not support UTF-8! Cannot encode username string!");
		}
		Logger.minor(this, "Sent invite offer for room " + globalIdentifier + " to " + darkPeer.getName());
		sendBase(darkPeer, fs, N2NChatPlugin.OFFER_INVITE);
		sentInvites.put(pubKeyHash, new NameEntry(username, pubKeyHash));
		updateParticipantListing();
		return true;
	}

	public boolean sendInviteRetract(DarknetPeerNode darkPeer) {
		if (sentInvites.containsKey(new ByteArray(darkPeer.getPubKeyHash()))) {
			Logger.minor(this, "Retracted "+darkPeer.getName()+"'s invite to room "+globalIdentifier);
			sendBase(darkPeer, null, N2NChatPlugin.RETRACT_INVITE);
			sentInvites.remove(new ByteArray(darkPeer.getPubKeyHash()));
			return true;
		} else {
			Logger.warning(this, "Attempted to remove "+darkPeer.getName()+"'s invite to room "+globalIdentifier);
		}
		return false;
	}

	/**
	 * Handles invitation responses.
	 * @param darkPeer The peer that sent the accept/reject.
	 * @param accepted Whether the invite was accepted.
	 * @return True if the invite was removed, false if the invite does not exist.
	 */
	private boolean receiveInvite(DarknetPeerNode darkPeer, boolean accepted) {
		ByteArray darkPeerHash = new ByteArray(darkPeer.getPubKeyHash());
		if (!sentInvites.containsKey(darkPeerHash)) {
			Logger.warning(this, "Received message from "+darkPeer.getName()+" about nonexistent invite to room "+globalIdentifier);
			return false;
		}
		if (accepted) {
			inviteParticipant(darkPeer, sentInvites.get(darkPeerHash).name);
		}
		sentInvites.remove(darkPeerHash);
		updateParticipantListing();
		return true;
	}

	public boolean receiveInviteAccept(DarknetPeerNode darkPeer) {
		Logger.minor(this, darkPeer.getName()+" accepted an invite to room '"+roomName+"' ("+globalIdentifier+")");
		return receiveInvite(darkPeer, true);
	}

	public boolean receiveInviteReject(DarknetPeerNode darkPeer) {
		Logger.minor(this, darkPeer.getName()+" rejected an invite to room '"+roomName+"' ("+globalIdentifier+")");
		return receiveInvite(darkPeer, false);
	}

	/**
	 * Returns a SimpleFieldSet with the applicable pubKeyHash field.
	 * @param pubKeyHash The key hash to add. Can be null, in which case the field will not be added.
	 * @param fs To add fields to. Can be null, in which case a new one will be created.
	 * @return Empty or unmodified SimpleFieldSet if pubKeyHash is null.
	 */
	private SimpleFieldSet formatPubKeyHash(ByteArray pubKeyHash, SimpleFieldSet fs) {
		if (fs == null) {
			fs = new SimpleFieldSet(true);
		}
		if (pubKeyHash != null) {
			fs.putSingle("pubKeyHash", Base64.encode(pubKeyHash.getBytes()));
		}
		return fs;
	}

	private SimpleFieldSet formatPubKeyHash(ByteArray pubKeyHash) {
		return formatPubKeyHash(pubKeyHash, null);
	}

	private String l10n(String key) {
		return l10n.getBase().getString("room." + key);
	}

	private String l10n(String key, String pattern, String value) {
		return l10n(key, new String[]{pattern}, new String[]{value});
	}

	private String l10n(String key, String[] pattern, String[] value) {
		return l10n.getBase().getString("room." + key, pattern, value);
	}

	/**
	 * Used to keep track of participants in a chat room. Records whether they are directly connected, whether this
	 * node routes for them, what DarknetPeerNode is used to contact them, and what CSS styling their name uses.
	 */
	private class Participant extends NameEntry {

		public final boolean directlyConnected;
		public final boolean locallyInvited;
		public final DarknetPeerNode peerNode;

		/**
		 * @param directlyConnected Whether this participant is directly connected to this node. If so,messages
		 * will be sent to peerNode. If not, peerNode is authorized to route their messages and remove request.
		 * @param pubKeyHash The public key hash of the participant. Used for name CSS styling.
		 * @param locallyInvited Whether this node invited the participant. If so, this node will route their
		 * anything from them to all other directly connected peers.
		 * @param name The name of this participant.
		 * @param peerNode If directly connected, used to send messages. If not directly connected, only this
		 * node is authorized to route things for this participant.
		 */
		public Participant(String name, ByteArray pubKeyHash, DarknetPeerNode peerNode, boolean directlyConnected,
			        boolean locallyInvited) {
			super(name, pubKeyHash);
			this.peerNode = peerNode;
			this.directlyConnected = directlyConnected;
			this.locallyInvited = locallyInvited;
		}
	}

	/**
	 * Base class for Participant. Tracks name and name CSS styling. Used for pending invites and one's own name
	 * in the participants panel. This is done so they can all be alphabetically sorted by name and still colored.
	 * It also includes the public key hash used to style, if applicable, for use in tooltips.
	 */
	private class NameEntry implements Comparable<NameEntry> {

		public final String name;
		public final String nameStyle;
		public final ByteArray pubKeyHash;

		/**
		 * Drop shadow for legibility
		 */
		private final String additionalStyling = "text-shadow:2px 2px 4px #000000;";

		/**
		 * Initializes a NameEntry with a name and styling based off a public key hash.
		 * @param name The name of the entry.
		 * @param pubKeyHash The public key hash used to generate the color for that entry.
		 */
		public NameEntry(String name, ByteArray pubKeyHash) {
			this.name = name;
			nameStyle = hashColor(pubKeyHash.getBytes())+additionalStyling;
			this.pubKeyHash = pubKeyHash;
		}

		/**
		 * Initializes a NameEntry with a name and an explicitly set nameStyle.
		 * @param name The name of the NameEntry.
		 * @param nameStyle The nameStyle of the entry.
		 * @param applyAdditionalStyling Whether the explicitly specified nameStyle should be appended with
		 * additionalStyling.
		 */
		public NameEntry(String name, String nameStyle, boolean applyAdditionalStyling) {
			this.name = name;
			if (applyAdditionalStyling) {
				this.nameStyle = nameStyle+additionalStyling;
			} else {
				this.nameStyle = nameStyle;
			}
			pubKeyHash = null;
		}

		/**
		 * Returns CSS coloring for a given ByteArray,
		 * @param pubKeyHash An identifying ByteArray with at least four bytes.
		 * @return CSS styling: a color based on the bytes and a drop shadow.
		 */
		protected String hashColor(byte[] pubKeyHash) {
			//Bits 24-31 map to ~40%-80% luminosity to keep the colors distinguishable and visible on white.
			//Bits 0-23 are used by Color for RGB.
			//TODO: Assuming hash is at least 4 bytes. How long is this actually? Check DarknetCrypto.
			assert(pubKeyHash.length >= 4);
			int hashInt = pubKeyHash[0] | (pubKeyHash[1] << 8) | (pubKeyHash[2] << 16);
			HSLColor colorManipulator = new HSLColor(new Color(hashInt));
			//[3] for luminance bit. 127 (-128) is the max value of a signed byte; scaled +/-20 from 60.
			float luminance = pubKeyHash[3]/127*20f+60f;
			colorManipulator.adjustLuminance(luminance);
			Color nameColor = colorManipulator.getRGB();
			return "color:rgb("+nameColor.getRed()+','+nameColor.getGreen()+','+nameColor.getBlue()+");";
		}

		//Alphabetical sorting
		public int compareTo(NameEntry other) {
			return this.name.compareToIgnoreCase(other.name);
		}
	}
}
