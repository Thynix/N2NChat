/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package plugins.N2NChat.core;

import freenet.clients.http.*;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.l10n.PluginL10n;
import freenet.node.DarknetPeerNode;
import freenet.node.FSParseException;
import freenet.node.NodeToNodeMessageListener;
import freenet.node.PeerNode;
import freenet.pluginmanager.*;
import freenet.support.Base64;
import freenet.support.IllegalBase64Exception;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;

import plugins.N2NChat.webui.DisplayChatToadlet;
import plugins.N2NChat.webui.MainPageToadlet;
import plugins.N2NChat.webui.StaticResourceToadlet;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

/**
 * This class interfaces with Freenet. It is the class that is loaded by the node.
 */
public class N2NChatPlugin implements FredPlugin, FredPluginL10n, FredPluginBaseL10n, FredPluginThreadless, FredPluginVersioned {

	//
	// CONSTANTS
	//

	/** Type identifier for all chat messages. Used to register message listener.*/
	public static final int N2N_MESSAGE_TYPE_CHAT = 103;

	/** Type identifier for a message */
	public static final int MESSAGE = 1;

	/** Type identifier for an invite offer */
	public static final int OFFER_INVITE = 2;

	/** Type identifier for a retraction of an invite */
	public static final int RETRACT_INVITE = 3;

	/** Type identifier for accepting an invite */
	public static final int ACCEPT_INVITE = 4;

	/** Type identifier for rejecting an invite */
	public static final int REJECT_INVITE = 5;

	/** Type identifier for a participant joining */
	public static final int JOIN = 6;

	/** Type identifier for a participant leaving */
	public static final int LEAVE = 7;

	/** The version. */
	public static final String VERSION = "0.0.1";

	//TODO: Allow date display formatting configuration.
	/** Date format used for timestamp when a message is received on a different day than the last.
	 * Ex: Wednesday, June 1, 2011
	 **/
	public static final SimpleDateFormat dayChangeFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy");

	/** Date format used for the "time received" timestamp on messages. Ex: 04:48:30 PM */
	public static final SimpleDateFormat receivedFormat = new SimpleDateFormat("hh:mm:ss a");

	/** Date format used for the "time composed" timestamp on messages. Ex: 04:48:30 PM, June 1, 2011*/
	public static final SimpleDateFormat composedFormat = new SimpleDateFormat("hh:mm:ss a, MMMM dd, yyyy");

	/** Key is global identifier*/
	public HashMap<Long, chatInvite> receivedInvites;

	/** Prefix for strings to pass through l10n. Used to put room names in chat menu.*/
	public static final String l10nRaw = "RAWSTRING.";

	//
	// MEMBER VARIABLES
	//

	/** The logger. */
	//private static final Logger logger = Logging.getLogger(N2NChatPlugin.class);

	/** The plugin respirator. */
	private PluginRespirator pluginRespirator;

	private ToadletContainer tc;

	/** The l10n helper. */
	private PluginL10n l10n;

	/** HashMap containing all chat rooms this node is present in. The key is the global identifier. */
	private HashMap<Long, ChatRoom> chatRooms;

	private MainPageToadlet mpt;
	private DisplayChatToadlet displayChatToadlet;
	private StaticResourceToadlet srt;

	/**l10n key for chat menu name */
	private static final String chatMenu = "N2NChatPlugin.menuName";

	//
	// ACCESSORS
	//

	/**
	 * Returns the plugin respirator for this plugin.
	 * @return The plugin respirator
	 */
	public PluginRespirator pluginRespirator() {
		return pluginRespirator;
	}

	/**
	 * Returns the plugin’s l10n helper.
	 * @return The plugin’s l10n helper
	 */
	public PluginL10n l10n() {
		return l10n;
	}

	public boolean roomExists(long globalIdentifier) {
		return chatRooms.containsKey(globalIdentifier);
	}

	public ChatRoom getRoom(long globalIdentifier) {
		return chatRooms.get(globalIdentifier);
	}

	public Collection<ChatRoom> getRooms() {
		return chatRooms.values();
	}

	public boolean noRooms() {
		return chatRooms.isEmpty();
	}

	//
	// MODIFIERS
	//

	public ChatRoom removeChatRoom(long globalIdentifier) {
		return chatRooms.remove(globalIdentifier);
	}

	public ChatRoom addChatRoom(long globalIdentifier, String roomName, String username) {
		return chatRooms.put(globalIdentifier, new ChatRoom(roomName, globalIdentifier, username,
		        pluginRespirator.getNode().getDarknetConnections(), l10n));
	}

	public ChatRoom addChatRoom(long globalIdentifier, String roomName, String username, DarknetPeerNode invitedBy) {
		return chatRooms.put(globalIdentifier, new ChatRoom(roomName, globalIdentifier, username,
		        pluginRespirator.getNode().getDarknetConnections(), l10n, invitedBy));
	}

	//
	// FREDPLUGIN METHODS
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void runPlugin(PluginRespirator pr) {
		this.pluginRespirator = pr;
		this.chatRooms = new HashMap<Long, ChatRoom>();
		this.receivedInvites = new HashMap<Long, chatInvite>();
		this.tc = pr.getToadletContainer();

		//TODO: Need to store and retrieve config somehow.
		mpt = new MainPageToadlet(this);
		pr.getPageMaker().addNavigationCategory(mpt.path(), chatMenu, "N2NChatPlugin.menuName.tooltip", this);
		tc.register(mpt, chatMenu, mpt.path(), true, "N2NChatPlugin.mainPage", "N2NChatPlugin.mainPage.tooltip", false, mpt);
		tc.register(mpt, null, mpt.path(), true, false);

		displayChatToadlet = new DisplayChatToadlet(this);
		srt = new StaticResourceToadlet(pr);
		tc.register(displayChatToadlet, null, displayChatToadlet.path(), true, false);
		tc.register(srt, null, srt.path(), true, false);

		pr.getNode().registerNodeToNodeMessageListener(N2N_MESSAGE_TYPE_CHAT, N2NChatListener);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void terminate() {
		//Disconnect from all chats
		for (ChatRoom chatRoom : chatRooms.values()) {
			chatRoom.disconnect();
		}

		//Unregister category
		pluginRespirator().getPageMaker().removeNavigationCategory(chatMenu);

		//Unregister pages
		tc.unregister(mpt);
		tc.unregister(displayChatToadlet);
		tc.unregister(srt);
	}

	//
	// INTERFACE FredPluginL10n
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getString(String key) {
		if (key.startsWith(l10nRaw)) {
			return key.substring(l10nRaw.length());
		}
		return l10n.getBase().getString(key);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setLanguage(LANGUAGE newLanguage) {
		l10n = new PluginL10n(this, newLanguage);
	}

	//
	// INTERFACE FredPluginBaseL10n
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getL10nFilesBasePath() {
		return "l10n/";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getL10nFilesMask() {
		return "N2NChat.${lang}.properties";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getL10nOverrideFilesMask() {
		return "N2NChat.${lang}.override.properties";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ClassLoader getPluginClassLoader() {
		return N2NChatPlugin.class.getClassLoader();
	}

	//
	// INTERFACE FredPluginVersioned
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getVersion() {
		return VERSION;
	}

	//
	// CHAT-SPECIFIC METHODS
	//

	public static void sendInvite(long globalIdentifier, DarknetPeerNode darkPeer, int type) {
		SimpleFieldSet fs = new SimpleFieldSet(true);
		fs.put("type", type);
		fs.put("globalIdentifier", globalIdentifier);
		darkPeer.sendNodeToNodeMessage(fs, N2N_MESSAGE_TYPE_CHAT, true, System.currentTimeMillis(), false);
	}


	public static void sendInviteAccept(DarknetPeerNode darkPeer, long globalIdentifier) {
		Logger.minor(N2NChatPlugin.class, "Sent invite accept for room " + globalIdentifier + " to " + darkPeer.getName());
		sendInvite(globalIdentifier, darkPeer, ACCEPT_INVITE);
	}

	public static void sendInviteReject(DarknetPeerNode darkPeer, long globalIdentifier) {
		Logger.minor(N2NChatPlugin.class, "Sent invite reject for room " + globalIdentifier + " to " + darkPeer.getName());
		sendInvite(globalIdentifier, darkPeer, REJECT_INVITE);
	}

	public class chatInvite {
		public final String username;
		public final String roomName;
		public final DarknetPeerNode darkPeer;

		chatInvite(String username, String roomName, DarknetPeerNode darkPeer) {
			this.username = username;
			this.roomName = roomName;
			this.darkPeer = darkPeer;
		}
	}

	//N2N message listener for chat messages. Registered in RunPlugin().
	private NodeToNodeMessageListener N2NChatListener = new NodeToNodeMessageListener() {
		public void handleMessage(byte[] data, boolean fromDarknet, PeerNode source, int type) {
			if (!fromDarknet) {
				freenet.support.Logger.error(this, "Received N2N chat message from non-darknet peer " + source);
				return;
			}
			DarknetPeerNode darkSource = (DarknetPeerNode) source;
			freenet.support.Logger.normal(this, "Received N2N chat from " +darkSource.getName()+" (" + darkSource.getPeer() + ")");
			SimpleFieldSet fs = null;
			try {
				fs = new SimpleFieldSet(new String(data, "UTF-8"), false, true, false);
			} catch (UnsupportedEncodingException e) {
				throw new Error("Impossible: JVM doesn't support UTF-8: " + e, e);
			} catch (IOException e) {
				Logger.error(this, "IOException while parsing node to node message data", e);
				return;
			}

			/*Fields are parsed in the order that they are needed, with those types that need the fewest
			pieces of information from fields checked for first.
			 */

			//Get global identifier.
			long globalIdentifier;
			try {
				globalIdentifier = fs.getLong("globalIdentifier");
			} catch (FSParseException e) {
				//Could not parse global identifier. Dropping.
				//TODO: Add localized error message.
				Logger.error(this, "Failed to parse global identifier from " + ((DarknetPeerNode) source).getName() + '.');
				return;
			}

			//We already know it's a chat message, but what kind?
			try {
				type = fs.getInt("type");
			} catch (FSParseException e) {
				Logger.error(this, "Failed to read message type in message about room "+globalIdentifier);
				return;
			}

			/*/A darknet peer offered this node an invite. Add it to the list of offered invites to allow
			the user to accept or reject it. If there is an existing invite for this room, it is replaced.
			 */
			if (type == OFFER_INVITE) {
				try {
					String username = new String(Base64.decode(fs.get("username")));
					String roomName = new String(Base64.decode(fs.get("roomName")));
					receivedInvites.put(globalIdentifier, new chatInvite(username, roomName,
					        darkSource));
					Logger.minor(this, "Received invitation offer from "+darkSource.getName()+
					        " to room '"+roomName+"' ("+globalIdentifier+") with username '"+username+"'");
					mpt.updateInvitationTable();
				} catch (IllegalBase64Exception e) {
					Logger.error(this, "Invalid base64 encoding on user/room name", e);
				}
				return;
			} else if (type == RETRACT_INVITE) {
				if (receivedInvites.containsKey(globalIdentifier) &&
				        receivedInvites.get(globalIdentifier).darkPeer == darkSource) {
					Logger.minor(this, "Received invite retract from"+darkSource.getName()+
					        " for the invite to room '"+receivedInvites.get(globalIdentifier).roomName+" ("+globalIdentifier+")");
					receivedInvites.remove(globalIdentifier);
					mpt.updateInvitationTable();
				}
				return;
			}

			//Check that the requested room exists.
			if (!chatRooms.containsKey(globalIdentifier)) {
				Logger.error(this, l10n.getBase().getString("N2NChatPlugin.nonexistentRoom",
				        new String[] { "globalIdentifier", "type" },
				        new String[] { String.valueOf(globalIdentifier), String.valueOf(type) }));
				return;
			}

			//TODO: Do these need to fire web pushing events?

			//A darknet peer accepted an invite this node offered. Add them to the chat room.
			if (type == ACCEPT_INVITE) {
				Logger.minor(this, "Received invite accept for room '"+chatRooms.get(globalIdentifier).getRoomName()+"' (" + globalIdentifier + ") from " + darkSource.getName());
				chatRooms.get(globalIdentifier).receiveInviteAccept(darkSource);
				return;
			//A darknet peer rejected an invite this node offered; remove it from list of pending invites.
			} else if (type == REJECT_INVITE) {
				chatRooms.get(globalIdentifier).receiveInviteReject(darkSource);
				return;
			}

			//Get identity hash for use in a message, join, or leave.
			ByteArray pubKeyHash;
			try {
				pubKeyHash = new ByteArray(Base64.decode(fs.getString("pubKeyHash")));
			} catch (FSParseException e) {
				//pubKeyHash was not included. This means it pertains to the sender.
				Logger.minor(this, "Public key hash was not included; assuming sender.");
				pubKeyHash = new ByteArray(darkSource.peerECDSAPubKeyHash);
			} catch (IllegalBase64Exception e) {
				//Could not parse identity hash. Dropping.
				//TODO: Add localized, logged error message.
				Logger.error(this, "Failed to parse public key hash from "+darkSource.getName()+'.');
				return;
			}

			//A message was received. Attempt to add the message.
			if (type == MESSAGE) {
				try {
					chatRooms.get(globalIdentifier).receiveMessage(
					        pubKeyHash,
					        new Date(fs.getLong("timeComposed")),
					        new ByteArray(darkSource.peerECDSAPubKeyHash),
					        new String(Base64.decode(fs.get("text"))));
				} catch (FSParseException e) {
					//TODO: Add localized, logged error message.
					Logger.error(this, "Failed to parse date from " + darkSource.getName() + '.');
				} catch (IllegalBase64Exception e) {
					Logger.error(this, "Invalid base64 encoding on message text", e);
				}
				return;
			//Someone joined a chat room.
			} else if (type == JOIN) {
				try {
					boolean displayJoin = true;
					try {
						displayJoin = fs.getBoolean("displayJoin");
					} catch (FSParseException e) {
						Logger.error(this, "Join message did not include whether to display. Defaulting to display.", e);
					}
					chatRooms.get(globalIdentifier).joinedParticipant(pubKeyHash,
 					        new String(Base64.decode(fs.get("username"))), darkSource, displayJoin);
				} catch (IllegalBase64Exception e) {
					Logger.error(this, "Invalid base64 encoding on username", e);
				}
				return;
			//Someone left a chat room.
			} else if (type == LEAVE) {
				chatRooms.get(globalIdentifier).removeParticipant(pubKeyHash,
				        new ByteArray(darkSource.peerECDSAPubKeyHash), false);
				return;
			}
			Logger.warning(this, "Received chat message of unknown type "+type+" from "+darkSource.getName());
		}
	};
}
