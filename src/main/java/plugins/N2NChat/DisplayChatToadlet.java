package plugins.N2NChat;

import freenet.clients.http.*;
import freenet.l10n.NodeL10n;
import freenet.l10n.PluginL10n;
import freenet.node.DarknetPeerNode;
import freenet.node.Node;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Base64;
import freenet.support.HTMLNode;
import freenet.support.IllegalBase64Exception;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class DisplayChatToadlet extends Toadlet implements LinkEnabledCallback {

	private HashMap<Long, ChatRoom> chatRooms;
	private PluginL10n l10n;
	private Node node;
	private PluginRespirator pluginRespirator;

	public DisplayChatToadlet(N2NChatPlugin chatPlugin) {
		super(chatPlugin.pluginRespirator().getHLSimpleClient());
		this.chatRooms = chatPlugin.chatRooms;
		this.l10n = chatPlugin.l10n();
		this.pluginRespirator = chatPlugin.pluginRespirator();
		this.node = pluginRespirator.getNode();
	}

	public static String PATH = "/n2n-chat/display/";

	public String path() {
		return PATH;
	}

	public boolean isEnabled (ToadletContext ctx) {
		return (!pluginRespirator.getToadletContainer().publicGatewayMode()) ||
		        ((ctx != null) && ctx.isAllowedFullAccess());
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws
		ToadletContextClosedException, IOException, RedirectException {

		if (!request.isPartSet("room") || request.getPartAsStringFailsafe("room", 4096).isEmpty()) {
			super.sendErrorPage(ctx, 500, "Invalid room", "A room was not properly requested.");
			return;
		} else if (!chatRooms.containsKey(Long.valueOf(request.getPartAsStringFailsafe("room", 4096)))) {
			super.sendErrorPage(ctx, 500, "Nonexistent room", "The requested room does not exist.");
			return;
		}
		//TODO: Should the password check be the first thing?
		long globalIdentifier = Long.valueOf(request.getPartAsStringFailsafe("room", 4096));

		//Ensure that the form password is correctly set.
		String pass = request.getPartAsStringFailsafe("formPassword", 32);
		if ( pass == null || !pass.equals(node.clientCore.formPassword)) {
			selfRefresh(globalIdentifier, ctx);
			return;
		}

		if (request.isPartSet("message") && !request.getPartAsStringFailsafe("message", 4096).isEmpty()) {
			chatRooms.get(globalIdentifier).sendOwnMessage(request.getPartAsStringFailsafe("message", 4096));
			selfRefresh(globalIdentifier, ctx);
			return;
		} else if (request.isPartSet("invite") && !request.getPartAsStringFailsafe("invite", 4096).isEmpty()) {
			//TODO: What is the length of a public key hash? (when base 64 encoded?)
			try {
				byte[] pubKeyHash = Base64.decode(request.getPartAsStringFailsafe("invite", 4096));
				DarknetPeerNode peerNode = null;
				for (DarknetPeerNode node : this.node.getDarknetConnections()) {
					if (Arrays.equals(node.getPubKeyHash(), pubKeyHash)) {
						peerNode = node;
						break;
					}
				}
				if (peerNode == null) {
					super.sendErrorPage(ctx, 500, "Invalid public key hash", "A peer with that hash does not exist.");
					return;
				}
				chatRooms.get(globalIdentifier).sendInviteOffer(peerNode, peerNode.getName());
				selfRefresh(globalIdentifier, ctx);
				return;
			} catch (IllegalBase64Exception e) {
				super.sendErrorPage(ctx, 500, "Invalid public key hash", "The public key hash to invite was not valid base 64 encoding.");
				return;
			}
		}
		super.sendErrorPage(ctx, 500, "Argument error", "Not enough recognized arguments were provided to do anything.");
	}

	private void selfRefresh(long globalIdentifier, ToadletContext ctx) throws ToadletContextClosedException, IOException{
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		headers.put("Location", PATH+"?room="+globalIdentifier);
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws
		ToadletContextClosedException, IOException, RedirectException {

		if (!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase()
			        .getString("Toadlet.unauthorized"));
			return;
		}

		if (request.isParameterSet("room") && !request.getParam("room").isEmpty()) {
			long globalIdentifier = Long.valueOf(request.getParam("room"));
			//Ensure the chat room exists,
			if (!chatRooms.containsKey(globalIdentifier)) {
				//TODO: localization
				super.sendErrorPage(ctx, 500, "Invalid Room", "This node is not present in the specified room.");
			}
			ChatRoom chatRoom = chatRooms.get(globalIdentifier);

			//Only messages have been requested.
			if (request.isParameterSet("messagesPane")) {
				writeHTMLReply(ctx, 200, "OK", null, chatRoom.getLog().generate());
				return;
			} else if (request.isParameterSet("participantsList")) {
				writeHTMLReply(ctx, 200, "OK", null, chatRoom.getParticipantListing().generate());
				return;
			}
			PageNode pn = ctx.getPageMaker().getPageNode(chatRoom.getRoomName(), ctx);
			//pn.addCustomStyleSheet("/n2n-chat/static/css/display.css");
			pn.headNode.addChild("script",
			        new String[] { "type", "src" },
			        new String[] { "text/javascript", "/n2n-chat/static/js/jquery.min.js"});
			pn.headNode.addChild("script",
			        new String[] { "type", "src" },
			        new String[] { "text/javascript", "/n2n-chat/static/js/display.js"});

			//So that the members and messages pane share vertical size.
			HTMLNode panes = pn.content.addChild("div", "style", "display:table;width:100%");

			//Add main messages area.
			panes.addChild("div", new String[] { "style", "id" },
			        new String[] { "display:table-cell;width:80%;", "messagesPane" })
			        .addChild(chatRoom.getLog());

			//Add listing of current participants.
			HTMLNode participantsPane = panes.addChild("div", "style", "display:table-cell;width:20%");
			//Container for participants listing only; invite dropdown should not be disturbed by the auto-refresh.
			HTMLNode participantsListing = participantsPane.addChild("div", "id", "participantsList");
			participantsListing.addChild(chatRoom.getParticipantListing());

			//And ability to invite those not already participating. Don't display if all connected darknet
			//peers are already participating.
			ArrayList<DarknetPeerNode> uninvitedPeers = uninvitedPeers(globalIdentifier);
			if (uninvitedPeers.size() > 0) {
				//Allow inviting more participants.
				HTMLNode inviteForm = ctx.addFormChild(participantsPane, path(), "invite-participant");
				inviteForm.addChild("input", new String[] { "type", "name", "value" },
				        new String[] { "hidden", "room", String.valueOf(globalIdentifier) });
				HTMLNode dropDown = inviteForm.addChild("select", "name", "invite");
				for (DarknetPeerNode peerNode : uninvitedPeers) {
					dropDown.addChild("option", "value", Base64.encode(peerNode.getPubKeyHash()),
					        peerNode.getName());
				}
				inviteForm.addChild("input", new String[] { "type", "name", "value" },
				        new String[] { "submit", "send-invite",
				                l10n("sendInvitation")});
			}

			//Add message sending area.
			HTMLNode messageEntry = ctx.addFormChild(pn.content, path(), "send-message");
			messageEntry.addChild("input", new String[] { "type", "name", "style" },
			        new String[] { "text", "message", "width:80%;" });
			messageEntry.addChild("input", new String[] { "type", "name", "value" },
			        new String[] { "hidden", "room", String.valueOf(globalIdentifier)} );
			messageEntry.addChild("input", new String[] { "type", "name", "value"},
			        new String[] { "submit", "send-message", l10n("send") } );
			writeHTMLReply(ctx, 200, "OK", null, pn.outer.generate());
		} else {
			super.sendErrorPage(ctx, 500, "Room Not Specified", "The room to display was not specified.");
		}
	}

	public ArrayList<DarknetPeerNode> uninvitedPeers(long globalIdentifier) {
		ChatRoom chatRoom = chatRooms.get(globalIdentifier);
		ArrayList<DarknetPeerNode> list = new ArrayList<DarknetPeerNode>();
		for (DarknetPeerNode peerNode : node.getDarknetConnections()) {
			if (!chatRoom.containsParticipant(new ByteArray(peerNode.getPubKeyHash())) &&
			        !chatRoom.inviteSentTo(new ByteArray(peerNode.getPubKeyHash()))) {
				list.add(peerNode);
			}
		}
		return list;
	}

	private String l10n(String key) {
		return l10n.getBase().getString("room."+key);
	}
}
