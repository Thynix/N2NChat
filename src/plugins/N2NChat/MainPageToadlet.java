package plugins.N2NChat;

import freenet.clients.http.*;
import freenet.l10n.NodeL10n;
import freenet.l10n.PluginL10n;
import freenet.node.DarknetPeerNode;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;


/**
 * Lists the chat rooms the node is currently participating in or has been invited to. Allows creation of new chat
 * rooms and accepting or rejecting invitations received from others.
 */
public class MainPageToadlet extends Toadlet implements LinkEnabledCallback {

	//TODO: Do we really have to take all this stuff from the chatPlugin? Seems excessive...
	private Node node;
	private NodeClientCore clientCore;
	private PluginRespirator pluginRespirator;
	private HashMap<Long, ChatRoom> chatRooms;
	private HashMap<Long, N2NChatPlugin.chatInvite> receivedInvites;
	//TODO: Or node l10n?
	private PluginL10n l10n;
	public static final int roomNameTruncate = 255;

	public MainPageToadlet(N2NChatPlugin chatPlugin) {
		super(chatPlugin.pluginRespirator().getHLSimpleClient());
		this.clientCore = chatPlugin.pluginRespirator().getNode().clientCore;
		this.pluginRespirator = chatPlugin.pluginRespirator();
		this.node = chatPlugin.pluginRespirator().getNode();
		this.l10n = chatPlugin.l10n();
		this.chatRooms = chatPlugin.chatRooms;
		this.receivedInvites = chatPlugin.receivedInvites;
	}

	public String path() {
		return "/n2n-chat/main-page/";
	}

	public boolean isEnabled (ToadletContext ctx) {
		return (!pluginRespirator.getToadletContainer().publicGatewayMode()) ||
		        ((ctx != null) && ctx.isAllowedFullAccess());
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws
	        ToadletContextClosedException, IOException, RedirectException {

		//TODO: This is duplicate code between here and DisplayChatToadlet; refactor.
		//Ensure that the form password is correctly set.
		String pass = request.getPartAsStringFailsafe("formPassword", 32);
		if ( pass == null || !pass.equals(clientCore.formPassword)) {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", path());
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}

		//TODO: Tell user about the maximum length of the room name instead of silently truncating.
		if (request.isPartSet("new-room-name") &&
			        !request.getPartAsStringFailsafe("new-room-name", roomNameTruncate).isEmpty()) {
			long globalIdentifier = node.random.nextLong();
			String roomName = request.getPartAsStringFailsafe("new-room-name", roomNameTruncate);
			String userName = node.getMyName();
			DarknetPeerNode[] darknetPeerNodes = node.getDarknetConnections();
			chatRooms.put(globalIdentifier,
			        new ChatRoom(roomName, globalIdentifier, userName, darknetPeerNodes, l10n));
		}
		handleMethodGET(uri, request, ctx);
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws
	        ToadletContextClosedException, IOException, RedirectException {

		if (!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase()
			        .getString("Toadlet.unauthorized"));
			return;
		}

		//TODO: Localization
		//List current chat rooms
		PageNode pn = ctx.getPageMaker().getPageNode("Chat Room Listing", ctx);
		HTMLNode content = pn.content;
		HTMLNode roomListing = content.addChild("div", "class", "room-listing");
		for (ChatRoom chatRoom : chatRooms.values()) {
			roomListing.addChild("a", "href", DisplayChatToadlet.PATH+"?room="+chatRoom.getGlobalIdentifier(),
			        chatRoom.getRoomName());
		}
		//Allow creating a new room
		HTMLNode createChatForm = ctx.addFormChild(roomListing, path(), "create-room");
		createChatForm.addChild("input", new String[] {"type", "name", "value"},
		        new String[] { "text", "new-room-name", "New room name"});
		createChatForm.addChild("input", new String[] { "type", "name", "value"},
		        new String[] { "submit", "create-chat", l10n.getBase().getString("newRoom") });

		//List received invitations.
		HTMLNode inviteListing = content.addChild("div", "class", "invite-listing");
		for (long globalIdentifier : receivedInvites.keySet()) {
			N2NChatPlugin.chatInvite invite = receivedInvites.get(globalIdentifier);
			HTMLNode entry = inviteListing.addChild("p");
			entry.addChild("#", "Room Name: "+invite.roomName+" Username: "+invite.username+" Invited by:"
			        +invite.darkPeer.getName());
			entry.addChild("a", "href", path()+"?accept="+globalIdentifier, "Accept" );
			entry.addChild("a", "href", path()+"?reject="+globalIdentifier, "Reject" );
		}

		writeHTMLReply(ctx, 200, "OK", null, pn.outer.generate());
	}
}