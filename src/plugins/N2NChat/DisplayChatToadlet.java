package plugins.N2NChat;

import freenet.clients.http.*;
import freenet.l10n.NodeL10n;
import freenet.node.DarknetPeerNode;
import freenet.support.Base64;
import freenet.support.HTMLNode;
import freenet.support.IllegalBase64Exception;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;

public class DisplayChatToadlet extends Toadlet implements LinkEnabledCallback {

	private N2NChatPlugin chatPlugin;

	public DisplayChatToadlet(N2NChatPlugin chatPlugin) {
		super(chatPlugin.pluginRespirator().getHLSimpleClient());
		this.chatPlugin = chatPlugin;
	}

	public String path() {
		return "/chat/display/";
	}

	public boolean isEnabled (ToadletContext ctx) {
		return (!chatPlugin.pluginRespirator().getToadletContainer().publicGatewayMode()) ||
		        ((ctx != null) && ctx.isAllowedFullAccess());
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws
		ToadletContextClosedException, IOException, RedirectException {

		//Ensure that the form password is correctly set.
		String pass = request.getPartAsStringFailsafe("formPassword", 32);
		if ( pass == null || !pass.equals(chatPlugin.pluginRespirator().getNode().clientCore.formPassword)) {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", path());
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}

		if (!request.isPartSet("room") || request.getPartAsStringFailsafe("room", 4096).isEmpty()) {
			super.sendErrorPage(ctx, 500, "Invalid room", "A room was not properly requested.");
			return;
		} else if (!chatPlugin.chatRooms.containsKey(Long.valueOf(request.getPartAsStringFailsafe("room", 4096)))) {
			super.sendErrorPage(ctx, 500, "Nonexistent room", "The requested room does not exist.");
			return;
		}

		long globalIdentifier = Long.valueOf(request.getPartAsStringFailsafe("room", 4096));

		if (request.isPartSet("message") && !request.getPartAsStringFailsafe("message", 4096).isEmpty()) {
			chatPlugin.chatRooms.get(globalIdentifier).sendOwnMessage(request.getPartAsStringFailsafe("message", 4096));
		} else if (request.isPartSet("invite") && !request.getPartAsStringFailsafe("invite", 4096).isEmpty()) {
			//TODO: What is the length of a public key hash? (when base 64 encoded?)
			try {
				byte[] pubKeyHash = Base64.decode(request.getPartAsStringFailsafe("invite", 4096));
				String username = request.getPartAsStringFailsafe("invite", 4096);
				DarknetPeerNode peerNode = null;
				for (DarknetPeerNode node : chatPlugin.pluginRespirator().getNode().getDarknetConnections()) {
					if (Arrays.equals(node.getPubKeyHash(), pubKeyHash)) {
						peerNode = node;
						break;
					}
				}
				if (peerNode == null) {
					super.sendErrorPage(ctx, 500, "Invalid public key hash", "A peer with that hash does not exist.");
					return;
				}
				chatPlugin.chatRooms.get(globalIdentifier).sendInviteOffer(peerNode, username);
			} catch (IllegalBase64Exception e) {
				super.sendErrorPage(ctx, 500, "Invalid public key hash", "The public key hash to invite was not valid base 64 encoding.");
				return;
			}
		}
	}

	public void handleMethodGet(URI uri, HTTPRequest request, ToadletContext ctx) throws
		ToadletContextClosedException, IOException, RedirectException {

		if (!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase()
			        .getString("Toadlet.unauthorized"));
			return;
		}

		if (request.isParameterSet("room") && !request.getParam("room").isEmpty()) {
			long globalIdentifier = Long.valueOf(request.getParam("room"));
			//Ensure the chat room exists,
			if (!chatPlugin.chatRooms.containsKey(globalIdentifier)) {
				//TODO: localization
				super.sendErrorPage(ctx, 500, "Invalid Room", "This node is not present in the specified room.");
			}
			ChatRoom chatRoom = chatPlugin.chatRooms.get(globalIdentifier);
			PageNode pn = ctx.getPageMaker().getPageNode(chatRoom.getRoomName(), ctx);

			//Add main messages area.
			pn.content.addChild(new HTMLNode("div", "class", "messagePane").addChild(chatRoom.getLog()));

			//Add listing of current participants.
			HTMLNode participantPane = pn.content.addChild("div", "class", "participantPane");
			participantPane.addChild(chatRoom.getParticipantListing());
			//And ability to invite those not already participating. Don't display if all connected darknet
			//peers are already participating.
			ArrayList<DarknetPeerNode> uninvitedPeers = uninvitedPeers(globalIdentifier);
			if (uninvitedPeers.size() > 0) {
				//Allow inviting more participants.
				HTMLNode inviteForm = ctx.addFormChild(participantPane, path(), "invite-participant");
				inviteForm.addChild("input", new String[] { "type", "name", "value" },
				        new String[] { "hidden", "room", String.valueOf(globalIdentifier) });
				HTMLNode dropDown = inviteForm.addChild("select", "name", "invite");
				for (DarknetPeerNode peerNode : uninvitedPeers) {
					dropDown.addChild("option", "value", Base64.encode(peerNode.getPubKeyHash()),
					        peerNode.getName());
				}
				inviteForm.addChild("input", new String[] { "type", "value", "name" },
				        new String[] { "submit", "send-invite",
				                chatPlugin.l10n().getBase().getString("sendInvitation")});
			}
			//TODO: Show status of pending invites.

			//Add message sending area.
			HTMLNode messageEntry = ctx.addFormChild(pn.content, path(), "send-message");
			messageEntry.addChild("input", new String[] { "type", "name", "value" },
			        new String[] { "hidden", "room", String.valueOf(globalIdentifier) });
			messageEntry.addChild("textarera", new String[] { "id", "rows", "cols" },
			        new String[] { "message", "5", "80"});
			messageEntry.addChild("input", new String[] { "type", "name", "value"},
			        new String[] { "submit", "send-message", chatPlugin.l10n().getBase().getString("send")});

		} else {
			super.sendErrorPage(ctx, 500, "Room Not Specified", "The room to display was not specified.");
		}
	}

	public ArrayList<DarknetPeerNode> uninvitedPeers(long globalIdentifier) {
		ChatRoom chatRoom = chatPlugin.chatRooms.get(globalIdentifier);
		ArrayList<DarknetPeerNode> list = new ArrayList<DarknetPeerNode>();
		for (DarknetPeerNode peerNode : chatPlugin.pluginRespirator().getNode().getDarknetConnections()) {
			if (!chatRoom.containsParticipant(peerNode.getPubKeyHash())) {
				list.add(peerNode);
			}
		}
		return list;
	}

}
