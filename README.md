# Darknet Chat Plugin

It needs a better name, but Darknet Chat Plugin is a chat plugin for [Freenet](http://www.freenetproject.org) that allows
communication between groups of people that are connected as friends on Freenet. Its intent is to allow real-time chat
rooms with an arbitrary number of participants. Anyone that connects to a chat room can invite anyone else they are
connected to.

## Building

Darknet Chat Plugin uses Apache Maven to build. It currently requires
[Fred 0.7.5.1387](http://downloads.freenetproject.org/alpha/freenet-build01387.jar),
[Freenet-ext 29](http://downloads.freenetproject.org/alpha/freenet-ext.jar), and Apache Commons
[commons-io 2.0.1](http://commons.apache.org/io/download_io.cgi). Once you have these dependancies, you can install
 them in Maven by running

    mvn install:install-file -Dfile=freenet.jar -DgroupId=org.freenetproject -DartifactId=fred -Dversion=0.7.5.1387 -Dpackaging=jar
    mvn install:install-file -Dfile=freenet-ext.jar -DgroupId=org.freenetproject -DartifactId=freenet-ext -Dversion=29 -Dpackaging=jar
    mvn install:install-file -Dfile=commons-io-2.0.1.jar -DgroupId=org.apache.commons -DartifactId=commons-io -Dversion=2.0.1 -Dpackaging=jar

Now you can build the package with ``mvn package`` and generate the Javadocs with ``mvn javadoc:javadoc``.

Because Darknet Chat Plugin runs as a plugin to Freenet, Fred and Freenet-ext are not compiled in as dependencies. Apache
commons-io is because Freenet does not provide it.

## Logging

If you'd like to log debug messages from the Darknet chat plugin, you can add
``plugins.N2NChat:MINOR,plugins.N2NChat.ChatRoom:MINOR`` to ``Detailed priority thresholds`` under the advanced mode of
Configuration > Logs. Seemingly due to logger strangeness, the messages might not appear until after the node is
restarted. It can be useful to monitor the log file with ``tail -F logs/freenet-latest.log``.

## Internals

### ChatRoom

ChatRooms contain Participant objects which contain username, styling, and routing information. Peers' public key hashes
are used as peer identifiers. Whenever a ChatRoom receives a message, whether it was composed by that Freenet node or
not, it routes the message to all other Participants that are directly connected to that Freenet node. This allows
messages from any Participant to be routed to all others. Here "message" refers to a text message, join, or leave. Each
ChatRoom has a name and global identifier. The name is selected by the user, and the global identifier is randomly
generated. In the event of a global identifier collision, someone would not be able to be in more than one of the
colliding rooms at once. As events occur, it generates HTMLNodes for the Participants listing and messages pane that are
retrieved by DisplayChatToadlet through asynchronous GETs. These panes contain information on mouseover: the timestamps
list the time the message was composed, and the participants list how they are connected to the current node, as well as
public key hashes. HTML is escaped using Freenet's existing HTML filtering. (HTMLNodes)

ChatRoom objects handle:

Receiving:

- Joins
- Leaves
- Text messages
- Invitation acceptance and rejection

Sending:

- Joins
- Leaves
- Text messages
- Invitation offers and retractions


### N2NChatPlugin

N2NChatPlugin is the plugin base class. It registers the plugin's pages and the Darknet message handler. It
maintains HashMaps which contain all ChatRooms and track received invitations.
The N2NChatPlugin object handles:

Receiving:

- Invitation offers and retractions

Sending:

- Invitation acceptance and rejection

### MainPageToadlet

MainPageToadlet provides a user interface to create new chat rooms, open a chat room's page, accept and reject invites,
and disconnect from chat rooms. It uses N2NChatPlugin's ChatRoom and invite HashMaps. To allow accepting or rejecting
invitations, it displays them in a table with links to itself with URL parameters for accepting or rejecting a given
invitation. Room creation is done by POSTing a new room name to itself, and disconnection is done by POSTing the global
identifier of the room to disconnect from to itself. It uses jQuery to continually refresh the invitations table.

### DisplayChatToadlet

DisplayChatToadlet provides a user interface to a room for sending and reading messages and inviting participants. It
uses jQuery to continually refresh the messages pane, participants listing, and invitation drop-down, as well as
asynchronously POST sent messages and maintain focus in the field. Its goal is to provide an interface close to a
desktop IM client such as Pidgin.

### StaticResourceToadlet

StaticResourceToadlet provides access to static resources packages within the .jar. It is used by pages to retrieve CSS
and Javascript files.

## Message format

The base identifier (``n2ntype`` to ``sendNodeToNodeMessage()``) used to register the listener is
``N2N_MESSAGE_TYPE_CHAT``, or 103.

<table>
  <tr>
    <th>Field</th><th>Purpose</th>
  </tr>
  <tr>
    <td>``type``</td><td>Type of chat message,</td>
  </tr>
  <tr>
    <td>``globalIdentifier``</td><td>Which room - hopefully-unique identifier.</td>
  </tr>
  <tr>
    <td>``pubKeyHash``</td><td>Which participant.</td>
  </tr>
  <tr>
    <td>``username``</td><td>Participant's username.</td>
  </tr>
  <tr>
    <td>``roomName``</td><td>ChatRoom human-readable name.</td>
  </tr>
  <tr>
    <td>``timeComposed``</td><td>Time the message was composed.</td>
  </tr>
  <tr>
    <td>``text``</td><td>Text of the chat message.</td>
  </tr>
  <tr>
    <td>``displayJoin``</td><td>Whether a join message should be displayed.</td>
  </tr>
</table>

<table>
  <tr>
    <th>Message</th><th>``type``</th><th>``globalIdentifier``</th><th>``pubKeyHash``</th><th>``username``</th><th>``roomName``</th><th>``timeComposed``</th><th>``text``</th><th>``displayJoin``</th>
  </tr>
  <tr>
    <td>``MESSAGE``</td><td>1</td><td>✓</td><td>✓</td><td></td><td></td><td>✓</td><td>✓</td><td></td>
  </tr>
  <tr>
    <td>``OFFER_INVITE``</td><td>2</td><td>✓</td><td></td><td>✓</td><td>✓</td><td></td><td></td><td></td>
  </tr>
  <tr>
    <td>``RETRACT_INVITE``</td><td>3</td><td>✓</td><td></td><td></td><td></td><td></td><td></td><td></td>
  </tr>
  <tr>
    <td>``ACCEPT_INVITE``</td><td>4</td><td>✓</td><td></td><td></td><td></td><td></td><td></td><td></td>
  </tr>
  <tr>
    <td>``REJECT_INVITE``</td><td>5</td><td>✓</td><td></td><td></td><td></td><td></td><td></td><td></td>
  </tr>
  <tr>
    <td>``JOIN``</td><td>6</td><td>✓</td><td>✓</td><td>✓</td><td></td><td></td><td></td><td>✓</td>
  </tr>
  <tr>
    <td>``LEAVE``</td><td>7</td><td>✓</td><td>✓</td><td></td><td></td><td></td><td></td><td></td>
  </tr>
</table>


## Future Ideas

- Some kind of chat server (XMPP? IRC?) so that existing programs can be used as interfaces with the chat.
  This could be similar to FLIP. Too similar? A server could be a separate plugin that also implements the same
  message protocol or is an addition to the web interface.
- More robust routing with backup route negotiation and direct connections whenever possible. Currently connections
form a chain that is very vulnerable to disruption.
- More route negotiation would allow for better knowledge of the network, which could be rendered into a cool diagram.
- Message signing to prevent the currently trivially easy message forging.
- Apache commons-io is used as a straightforward, although not particularly elegant in this case, way to get Javascript
  from the jar. It might be better to somehow extend Freenet's StaticToadlet.
