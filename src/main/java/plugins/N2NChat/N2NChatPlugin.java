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

package plugins.N2NChat;

import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.l10n.NodeL10n;
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
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

	//
	// MEMBER VARIABLES
	//

	/** The logger. */
	//private static final Logger logger = Logging.getLogger(N2NChatPlugin.class);

	/** The plugin respirator. */
	private PluginRespirator pluginRespirator;

	/** The l10n helper. */
	private PluginL10n l10n;

	/** HashMap containing all chat rooms this node is present in. The key is the global identifier. */
	public HashMap<Long, ChatRoom> chatRooms;

	/** Key is global identifier*/
	public HashMap<Long, chatInvite> receivedInvites;

	private MainPageToadlet mpt;
	private DisplayChatToadlet displayChatToadlet;

	//
	// ACCESSORS
	//

	/**
	 * Returns the plugin respirator for this plugin.
	 *
	 * @return The plugin respirator
	 */
	public PluginRespirator pluginRespirator() {
		return pluginRespirator;
	}

	/**
	 * Returns the plugin’s l10n helper.
	 *
	 * @return The plugin’s l10n helper
	 */
	public PluginL10n l10n() {
		return l10n;
	}

	//
	// FREDPLUGIN METHODS
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void runPlugin(PluginRespirator pluginRespirator) {
		this.pluginRespirator = pluginRespirator;
		this.chatRooms = new HashMap<Long, ChatRoom>();
		this.receivedInvites = new HashMap<Long, chatInvite>();

		//TODO: Need to store and retrieve config somehow.
		mpt = new MainPageToadlet(this);
		pluginRespirator.getPageMaker().addNavigationCategory(mpt.path(), "plugin.menuName", "plugin.menuName.tooltip", this);
		pluginRespirator.getToadletContainer().register(mpt,
		         "plugin.menuName", mpt.path(), true, "plugin.mainPage", "plugin.mainPage.tooltip", false, mpt);
		pluginRespirator.getToadletContainer().register(mpt, null, mpt.path(), true, false);

		displayChatToadlet = new DisplayChatToadlet(this);
		pluginRespirator.getToadletContainer().register(displayChatToadlet, null, displayChatToadlet.path(), true, false);
		pluginRespirator.getNode().registerNodeToNodeMessageListener(N2N_MESSAGE_TYPE_CHAT, N2NChatListener);
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
		pluginRespirator().getPageMaker().removeNavigationCategory("plugin.menuName");

		//Unregister pages
		pluginRespirator.getToadletContainer().unregister(mpt);
		pluginRespirator.getToadletContainer().unregister(displayChatToadlet);
	}

	//
	// INTERFACE FredPluginL10n
	//

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getString(String key) {
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

	private NodeToNodeMessageListener N2NChatListener = new NodeToNodeMessageListener() {
		public void handleMessage(byte[] data, boolean fromDarknet, PeerNode source, int type) {
			if (!fromDarknet) {
				freenet.support.Logger.error(this, "Received N2N chat message from non-darknet peer " + source);
				return;
			}
			DarknetPeerNode darkSource = (DarknetPeerNode) source;
			freenet.support.Logger.normal(this, "Received N2N chat from '" + darkSource.getPeer() + "'");
			SimpleFieldSet fs = null;
			try {
				fs = new SimpleFieldSet(new String(data, "UTF-8"), false, true);
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
				//TODO: Add localized, logged error message.
				Logger.error(this, "Failed to parse global identifier from " + ((DarknetPeerNode) source).getName() + '.');
				return;
			}

			//We already know it's a chat message, but what kind?
			try {
				type = fs.getInt("type");
			} catch (FSParseException e) {
				Logger.error(this, "Failed to read message type in message about room "+globalIdentifier);
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
				} catch (IllegalBase64Exception e) {
					Logger.error(this, "Invalid base64 encoding on user/room name", e);
				}
				return;
			} else if (type == RETRACT_INVITE) {
				if (receivedInvites.containsKey(globalIdentifier) &&
				        receivedInvites.get(globalIdentifier).darkPeer == darkSource) {
					receivedInvites.remove(darkSource.getPubKeyHash());
				}
				return;
			}

			//Check that the requested room exists.
			if (!chatRooms.containsKey(globalIdentifier)) {
				Logger.error(this, l10n.getBase().getString("plugin.nonexistentRoom",
				        new String[] { "globalIdentifier", "type" },
				        new String[] { String.valueOf(globalIdentifier), String.valueOf(type) }));
				return;
			}

			//TODO: Do these need to fire web pushing events?

			//A darknet peer accepted an invite this node offered. Add them to the chat room.
			if (type == ACCEPT_INVITE) {
				Logger.minor(this, "Received invite accept for room " + globalIdentifier + " from " + darkSource.getName());
				chatRooms.get(globalIdentifier).receiveInviteAccept(darkSource);
				return;
			//A darknet peer rejected an invite this node offered; remove it from list of pending invites.
			} else if (type == REJECT_INVITE) {
				Logger.minor(this, "Received invite reject for room "+globalIdentifier+" from "+darkSource.getName());
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
				pubKeyHash = new ByteArray(darkSource.getPubKeyHash());
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
					        new ByteArray(darkSource.getPubKeyHash()),
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
					chatRooms.get(globalIdentifier).joinedParticipant(pubKeyHash,
 					        new String(Base64.decode(fs.get("username"))), darkSource);
				} catch (IllegalBase64Exception e) {
					Logger.error(this, "Invalid base64 encoding on username", e);
				}
				return;
			//Someone left a chat room.
			} else if (type == LEAVE) {
				chatRooms.get(globalIdentifier).removeParticipant(pubKeyHash,
				        new ByteArray(darkSource.getPubKeyHash()), false);
			}
		}
	};
}
