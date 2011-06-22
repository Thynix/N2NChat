package plugins.N2NChat;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.*;
import freenet.l10n.NodeL10n;
import freenet.node.DarknetPeerNode;
import freenet.node.Node;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;
import sun.net.idn.StringPrep;
import sun.plugin.navig.motif.Plugin;

import java.io.IOException;
import java.net.URI;


/**
 * Lists the chat rooms the node is currently participating in or has been invited to. Allows creation of new chat
 * rooms and accepting or rejecting invitations received from others.
 */
public class MainPageToadlet extends Toadlet implements LinkEnabledCallback {

	private N2NChatPlugin chatPlugin;
	public static final int roomNameTruncate = 255;

	public MainPageToadlet(N2NChatPlugin chatPlugin) {
		super(chatPlugin.pluginRespirator().getHLSimpleClient());
		this.chatPlugin = chatPlugin;
	}

	public String path() {
		return "/chat/main-page/";
	}

	public boolean isEnabled (ToadletContext ctx) {
		return (!chatPlugin.pluginRespirator().getToadletContainer().publicGatewayMode()) ||
		        ((ctx != null) && ctx.isAllowedFullAccess());
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws
	        ToadletContextClosedException, IOException, RedirectException {

		//TODO: This is duplicate code; refactor.
		//Ensure that the form password is correctly set.
		String pass = request.getPartAsStringFailsafe("formPassword", 32);
		if ( pass == null || !pass.equals(chatPlugin.pluginRespirator().getNode().clientCore.formPassword)) {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", path());
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}

		//TODO: Tell user about the maximum length of the room name instead of silently truncating.
		if (request.isPartSet("new-room-name") &&
			        !request.getPartAsStringFailsafe("new-room-name", roomNameTruncate).isEmpty()) {
			Node node = chatPlugin.pluginRespirator().getNode();
			long globalIdentifier = node.random.nextLong();
			String roomName = request.getPartAsStringFailsafe("new-room-name", roomNameTruncate);
			String userName = node.getMyName();
			DarknetPeerNode[] darknetPeerNodes = node.getDarknetConnections();
			chatPlugin.chatRooms.put(globalIdentifier,
			        new ChatRoom(roomName, globalIdentifier, userName, darknetPeerNodes, chatPlugin.l10n()));
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
		for (ChatRoom chatRoom : chatPlugin.chatRooms.values()) {
			roomListing.addChild("a", "href", "/chat/display/?room="+chatRoom.getGlobalIdentifier(),
			        chatRoom.getRoomName());
		}
		//Allow creating a new room
		HTMLNode createChatForm = ctx.addFormChild(roomListing, path(), "create-room");
		createChatForm.addChild("input", new String[] {"type", "name", "value"},
		        new String[] { "text", "new-room-name", "New room name"});
		createChatForm.addChild("input", new String[] { "type", "name", "value"},
		        new String[] { "submit", "create-chat", chatPlugin.l10n().getBase().getString("newRoom") });

		//List received invitations.
		HTMLNode inviteListing = content.addChild("div", "class", "invite-listing");
		for (long globalIdentifier : chatPlugin.receivedInvites.keySet()) {
			N2NChatPlugin.chatInvite invite = chatPlugin.receivedInvites.get(globalIdentifier);
			HTMLNode entry = inviteListing.addChild("p");
			entry.addChild("#", "Room Name: "+invite.roomName+" Username: "+invite.username+" Invited by:"
			        +invite.darkPeer.getName());
			entry.addChild("a", "href", path()+"?accept="+globalIdentifier, "Accept" );
			entry.addChild("a", "href", path()+"?reject="+globalIdentifier, "Reject" );
		}
	}
}