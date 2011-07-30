package plugins.N2NChat.webui;

import freenet.clients.http.*;
import freenet.l10n.NodeL10n;
import freenet.l10n.PluginL10n;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLNode;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

import plugins.N2NChat.core.N2NChatPlugin;
import plugins.N2NChat.core.ChatRoom;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;


/**
 * Lists the chat rooms the node is currently participating in or has been invited to. Allows creation of new chat
 * rooms and accepting or rejecting invitations received from others.
 */
public class MainPageToadlet extends Toadlet implements LinkEnabledCallback {

	public static final int roomNameTruncate = 255;

	//TODO: Do we really have to take all this stuff from the chatPlugin? Seems excessive...
	private Node node;
	private NodeClientCore clientCore;
	private PluginRespirator pluginRespirator;
	private HashMap<Long, N2NChatPlugin.chatInvite> receivedInvites;
	private PluginL10n l10n;
	private HTMLNode invitationTable;
	private boolean updatedInvites;
	private boolean hasError;
	private N2NChatPlugin chatPlugin;

	public MainPageToadlet(N2NChatPlugin chatPlugin) {
		super(chatPlugin.pluginRespirator().getHLSimpleClient());
		this.clientCore = chatPlugin.pluginRespirator().getNode().clientCore;
		this.pluginRespirator = chatPlugin.pluginRespirator();
		this.node = chatPlugin.pluginRespirator().getNode();
		this.l10n = chatPlugin.l10n();
		this.receivedInvites = chatPlugin.receivedInvites;
		this.chatPlugin = chatPlugin;
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

		if (request.isPartSet("disconnect")) {
			long globalIdentifier = Long.parseLong(request.getPartAsStringFailsafe("globalIdentifier", 4096));
			if (!chatPlugin.roomExists(globalIdentifier)) {
				super.sendErrorPage(ctx, 403, l10n("invalidRoomTitle"), l10n("invalidRoom"));
				return;
			}
			chatPlugin.getRoom(globalIdentifier).disconnect();
			chatPlugin.removeChatRoom(globalIdentifier);
		}

		if (request.isPartSet("new-room-name") &&
			        !request.getPartAsStringFailsafe("new-room-name", roomNameTruncate).isEmpty()) {
			long globalIdentifier = node.random.nextLong();
			String roomName = request.getPartAsStringFailsafe("new-room-name", roomNameTruncate+1);
			if(roomName.length()>roomNameTruncate)
			{
     			hasError = true;
     			writeTemporaryRedirect(ctx,"too long","/n2n-chat/main-page/");
     			return;
			}
			String userName = node.getMyName();
			chatPlugin.addChatRoom(globalIdentifier, roomName, userName);
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

		if (request.isParameterSet("accept")) {
			//User accepted an invite.
			long globalIdentifier = Long.parseLong(request.getParam("accept"));
			if (!receivedInvites.containsKey(globalIdentifier)) {
				super.sendErrorPage(ctx, 403, l10n("invalidInviteTitle"), l10n("invalidInvite"));
				return;
			}
			N2NChatPlugin.chatInvite invite = receivedInvites.get(globalIdentifier);
			//Create the new room.
			chatPlugin.addChatRoom(globalIdentifier, invite.roomName, invite.username, invite.darkPeer);
			//Then send invite acceptance.
			N2NChatPlugin.sendInviteAccept(invite.darkPeer, globalIdentifier);
			//Invite is accepted and so no longer pending.
			receivedInvites.remove(globalIdentifier);
		} else if (request.isParameterSet("reject")) {
			//User rejected an invite.
			long globalIdentifier = Long.parseLong(request.getParam("reject"));
			if (!receivedInvites.containsKey(globalIdentifier)) {
				super.sendErrorPage(ctx, 403, l10n("invalidInviteTitle"), l10n("invalidInvite"));
				return;
			}
			//Send the inviting peer the invite rejection.
			N2NChatPlugin.sendInviteReject(receivedInvites.get(globalIdentifier).darkPeer, globalIdentifier);
			//Invite is rejected and so no longer pending.
			receivedInvites.remove(globalIdentifier);
		} else if (request.isParameterSet("invitationTable")) {
			if (updatedInvites) {
				updatedInvites = false;
				writeHTMLReply(ctx, 200, "OK", invitationTable.generate());
				return;
			} else {
				writeHTMLReply(ctx, 304, "Not Modified", "");
				return;
			}
		}

		//List current chat rooms
		PageNode pn = ctx.getPageMaker().getPageNode(l10n("chatRoomListing"), ctx);
		PageMaker pm = ctx.getPageMaker();
		pn.addCustomStyleSheet("/n2n-chat/static/css/main-page.css");
		pn.headNode.addChild("script",
		        new String[] { "type", "src" },
		        new String[] { "text/javascript", "/n2n-chat/static/js/jquery.min.js"});
		pn.headNode.addChild("script",
		        new String[] { "type", "src" },
		        new String[] { "text/javascript", "/n2n-chat/static/js/main-page.js"});
		HTMLNode content = pn.content;

		if (chatPlugin.noRooms()) {
			HTMLNode welcome = addInfoBox(l10n("welcome"), pm, content);
			welcome.addChild("#", l10n("explain"));
		}

		HTMLNode roomsBox = addInfoBox(l10n("rooms"), pm, content);
		HTMLNode roomListing = roomsBox.addChild("ul",
		        new String[] { "class", "style" },
		        new String[] { "room-listing", "list-style-type:none;" });
		for (ChatRoom chatRoom : chatPlugin.getRooms()) {
			HTMLNode roomEntry = roomListing.addChild("li");
			roomEntry.addChild("a", "href", DisplayChatToadlet.PATH + "?room=" + chatRoom.getGlobalIdentifier(),
			        chatRoom.getRoomName());
			HTMLNode disconnectForm = ctx.addFormChild(roomEntry, path(), "disconnect");
			disconnectForm.addChild("input",
			        new String[] { "type", "name", "value" },
			        new String[] { "hidden", "globalIdentifier", String.valueOf(chatRoom.getGlobalIdentifier()) });
			disconnectForm.addChild("input",
			        new String[] { "type", "name", "value" },
			        new String[] { "submit", "disconnect", l10n("disconnect") });
		}
		//Allow creating a new room
		HTMLNode createChatForm = ctx.addFormChild(roomListing, path(), "create-room");
		createChatForm.addChild("input", new String[] {"type", "name", "value"},
		        new String[] { "text", "new-room-name", l10n("newRoom") });
		createChatForm.addChild("input", new String[] { "type", "name", "value"},
		        new String[] { "submit", "create-chat", l10n("create") });
		//warn user of max room-name length        
		if(hasError){
		   createChatForm.addChild("#",l10n("tooLong"));}        

		//List received invitations.
		HTMLNode invitationsBox = addInfoBox(l10n("pendingInvitations"), pm, content);
		updateInvitationTable();
		invitationsBox.addChild("div", "id", "invitationContainer").addChild(invitationTable);

		writeHTMLReply(ctx, 200, "OK", null, pn.outer.generate());
	}

	//TODO: This is much more general than just the main page toadlet; where can it go?
	/**
	 * Adds an InfoBox with the given title to the parent HTMLNode. Idea credit to p0s/x0r in Freetalk.
	 * @param infoBoxTitle The title of the InfoBox.
	 * @param pm The pageMaker to get the InfoBox from.
	 * @param parent Parent HTMLNode to add the InfoBox to.
	 * @return HTMLNode of the InfoBox's content.
	 */
	private HTMLNode addInfoBox(String infoBoxTitle, PageMaker pm, HTMLNode parent) {
		InfoboxNode invitationsBox = pm.getInfobox(infoBoxTitle);
		parent.addChild(invitationsBox.outer);
		return invitationsBox.content;
	}

	private String l10n(String key) {
		return l10n.getBase().getString("main."+key);
	}

	public void updateInvitationTable() {
		invitationTable = new HTMLNode("table");
		HTMLNode tableHeaders = invitationTable.addChild("tr");
		tableHeaders.addChild("th", l10n("roomName"));
		tableHeaders.addChild("th", l10n("username"));
		tableHeaders.addChild("th", l10n("invitedBy"));
		tableHeaders.addChild("th", l10n("accept"));
		tableHeaders.addChild("th", l10n("reject"));
		for (long globalIdentifier : receivedInvites.keySet()) {
			N2NChatPlugin.chatInvite invite = receivedInvites.get(globalIdentifier);
			HTMLNode entry = invitationTable.addChild("tr");
			entry.addChild("td", invite.roomName);
			entry.addChild("td", invite.username);
			entry.addChild("td", invite.darkPeer.getName());
			entry.addChild("td").addChild("a", "href", path()+"?accept="+globalIdentifier, l10n("accept") );
			entry.addChild("td").addChild("a", "href", path()+"?reject="+globalIdentifier, l10n("reject") );
		}

		updatedInvites = true;
    }
}
