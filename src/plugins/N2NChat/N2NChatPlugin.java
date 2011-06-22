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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.db4o.foundation.InvalidIteratorException;
import freenet.clients.http.StaticToadlet;
import freenet.l10n.NodeL10n;
import freenet.node.DarknetPeerNode;
import freenet.node.FSParseException;
import freenet.node.NodeToNodeMessageListener;
import freenet.node.PeerNode;
import freenet.support.Base64;
import freenet.support.IllegalBase64Exception;
import freenet.support.SimpleFieldSet;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterface;
import net.pterodactylus.util.config.Configuration;
import net.pterodactylus.util.config.ConfigurationException;
import net.pterodactylus.util.config.MapConfigurationBackend;
import net.pterodactylus.util.logging.Logging;
import net.pterodactylus.util.logging.LoggingListener;
import net.pterodactylus.util.version.Version;
import freenet.client.async.DatabaseDisabledException;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.l10n.PluginL10n;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginBaseL10n;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginRespirator;
import sun.security.util.PendingException;

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
	public static final SimpleDateFormat messageReceivedFormat = new SimpleDateFormat("hh:mm:ss a");

	/** Date format used for the "time composed" timestamp on messages. Ex: 04:48:30 PM, June 1, 2011*/
	public static final SimpleDateFormat messageComposedFormat = new SimpleDateFormat("hh:mm:ss a, MMMM dd, yyyy");

	//
	// MEMBER VARIABLES
	//

	/** The logger. */
	private static final Logger logger = Logging.getLogger(N2NChatPlugin.class);

	/** The plugin respirator. */
	private PluginRespirator pluginRespirator;

	/** The l10n helper. */
	private PluginL10n l10n;

	private WebInterface webInterface;

	/** HashMap containing all chat rooms this node is present in. The key is the global identifier. */
	HashMap<Long, ChatRoom> chatRooms;

	/** Key is global identifier*/
	HashMap<Long, chatInvite> receivedInvites;

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

		//TODO: Need to store and retrieve config somehow.

		/*webInterface = new WebInterface(new PluginContext(pluginRespirator));
		webInterface.registerVisible(, , "N2N Chat", null);*/
		MainPageToadlet mpt = new MainPageToadlet(this);
		pluginRespirator.getToadletContainer().register(mpt,
		         "FProxyToadlet.categoryFriends", mpt.path(), false, "Chat", "Chat title", true, mpt);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void terminate() {
		try {
			//Disconnect from all chats
			for (ChatRoom chatRoom : chatRooms.values()) {
				chatRoom.disconnect();
			}
		} finally {
			/* shutdown logger. */
			Logging.shutdown();
		}
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
		return "plugins/N2NChat/l10n/";
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
		fs.put("type", REJECT_INVITE);
		fs.put("globalIdentifier", globalIdentifier);
		darkPeer.sendNodeToNodeMessage(fs, N2N_MESSAGE_TYPE_CHAT, true, System.currentTimeMillis(), false);
	}


	public static void sendInviteAccept(DarknetPeerNode darkPeer, long globalIdentifier) {

		sendInvite(globalIdentifier, darkPeer, ACCEPT_INVITE);
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
				freenet.support.Logger.error(this, "IOException while parsing node to node message data", e);
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
				System.out.println("Failed to parse global identifier from "+((DarknetPeerNode) source).getName()+'.');
				return;
			}

			/*/A darknet peer offered this node an invite. Add it to the list of offered invites to allow
			the user to accept or reject it. If there is an existing invite for this room, it is replaced.
			 */
			if (type == OFFER_INVITE) {
				receivedInvites.put(globalIdentifier, new chatInvite(fs.get("username"),
				        fs.get("roomName"), darkSource));
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
				freenet.support.Logger.error(this, NodeL10n.getBase().getString("N2NChatRoom.nonexistentRoom",
					new String[]{"identityHash", "deliveredByName", "globalIdentifier", "message"},
					new String[]{fs.get("composedBy"), darkSource.getName(),
						String.valueOf(darkSource.getIdentityHash()), fs.get("text")}));
				return;
			}

			//TODO: Do these need to fire web pushing events?

			//A darknet peer accepted an invite this node offered. Add them to the chat room.
			if (type == ACCEPT_INVITE) {
				chatRooms.get(globalIdentifier).receiveInviteAccept(darkSource);
				return;
			//A darknet peer rejected an invite this node offered; remove it from list of pending invites.
			} else if (type == REJECT_INVITE) {
				chatRooms.get(globalIdentifier).receiveInviteReject(darkSource);
				return;
			}

			//Get identity hash for use in a message, join, or leave.
			byte[] pubKeyHash;
			try {
				pubKeyHash = Base64.decode(fs.getString("pubKeyHash"));
			} catch (FSParseException e) {
				//pubKeyHash was not included. This means it pertains to the sender.
				pubKeyHash = darkSource.getPubKeyHash();
			} catch (IllegalBase64Exception e) {
				//Could not parse identity hash. Dropping.
				//TODO: Add localized, logged error message.
				System.out.println("Failed to parse public key hash from "+darkSource.getName()+'.');
				return;
			}

			//A message was received. Attempt to add the message.
			if (type == MESSAGE) {
				try {
					chatRooms.get(globalIdentifier).receiveMessage(
					        pubKeyHash,
					        messageComposedFormat.parse(fs.get("composedTime")),
					        darkSource.getPubKeyHash(), fs.get("text"));
				} catch (ParseException e) {
					//Could not parse date. Dropping.
					//TODO: Add localized, logged error message.
					System.out.println("Failed to parse date from "+darkSource.getName()+'.');
					return;
				}
			//Someone joined a chat room.
			} else if (type == JOIN) {
				chatRooms.get(globalIdentifier).joinedParticipant(pubKeyHash, fs.get("name"),
				        darkSource);
			//Someone left a chat room.
			} else if (type == LEAVE) {
				chatRooms.get(globalIdentifier).removeParticipant(pubKeyHash,
				        darkSource.getPubKeyHash(), false);
			}
		}
	};
}
