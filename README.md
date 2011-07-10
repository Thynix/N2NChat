# N2N Chat Plugin

It needs a better name, but N2N Chat Plugin is a chat plugin for [Freenet](http://www.freenetproject.org) that allows
communication between groups of people that are connected as friends on Freenet. Its intent is to allow real-time chat
rooms with an arbitrary number of participants. Anyone that connects to a chat room can invite anyone else they are
connected to.

## Building

N2N Chat Plugin uses Apache Maven to build. It currently requires
[Fred 0.7.5.1378](https://github.com/freenet/fred-official/tree/build01378),
[Freenet-ext 29](http://downloads.freenetproject.org/alpha/freenet-ext.jar), and Apache Commons
[commons-io 2.0.1](http://commons.apache.org/io/download_io.cgi). Once you have these dependancies, you can install
 them in Maven by running

    mvn install:install-file -Dfile=freenet.jar -DgroupId=org.freenetproject -DartifactId=fred -Dversion=0.7.5.1378 -Dpackaging=jar
    mvn install:install-file -Dfile=freenet-ext.jar -DgroupId=org.freenetproject -DartifactId=freenet-ext -Dversion=29 -Dpackaging=jar
    mvn install:install-file -Dfile=commons-io-2.0.1.jar -DgroupId=org.apache.commons -DartifactId=commons-io -Dversion=2.0.1 -Dpackaging=jar

Now you can build the package with ``mvn package`` and generate the Javadocs with ``mvn javadoc:javadoc``.

Because N2N Chat Plugin runs as a plugin to Freenet, Fred and Freenet-ext are not compiled in as dependencies. Apache
commons-io is because Freenet does not provide it.

## Internals

### ChatRoom

ChatRooms contain Participant objects which contain username, styling, and routing information. Whenever a ChatRoom
receives a message, whether it was composed by that Freenet node or not, it routes the message to all other
Participants that are directly connected to that Freenet node. This allows messages from any Participant to be routed
to all others. Here "message" refers to a text message, join, or leave. Each ChatRoom has a name and global identifier.
The name is selected by the user, and the global identifier is randomly generated. In the event of a global identifier
collision, someone would not be able to be in more than one of the colliding rooms at once. As events occur, it
generates HTMLNodes for the Participants listing and messages pane that are retrieved by DisplayChatToadlet through
asynchronous GETs. These panes contain information on mouseover: the timestamps list the time the message was composed,
and the participants list how they are connected to the current node, as well as public key hashes.

ChatRoom objects handle:

Received:

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

Received:
- Invitation offers and retractions

Sending:
- Invitation acceptance and rejection

### MainPageToadlet

MainPageToadlet provides a user interface to create new chat rooms, open a chat room's page, accept and reject invites,
and disconnect from chat rooms. It uses N2NChatPlugin's ChatRoom and invite HashMaps. Its path is "/n2n-chat/main-page/".
To allow accepting or rejecting invitations, it displays them in a table with links to itself with URL parameters for
accepting or rejecting a given invitation. Room creation is done by POSTing a new room name to itself, and disconnection
is done by POSTing the global identifier of the room to disconnect from to itself. It does not contain Javascript.

### DisplayChatToadlet

DisplayChatToadlet provides a user interface to a room for sending and reading messages and inviting participants. It
uses jQuery to continually refresh the messages pane and participants listing, as well as asynchronously POST sent
messages and maintain focus in the field. Its goal is to provide an interface close to a desktop IM client such as
Pidgin.

### StaticResourceToadlet

StaticResourceToadlet provides access to static resources packages within the .jar. It is used by pages to retrieve CSS
and Javascript files.

## Future Ideas

- Some kind of chat server (XMPP?) so that other programs can be used as interfaces with the chat. This could be similar
 to FLIP. Too similar?
- More robust routing with backup route negotiation and direct connections whenever possible. Currently connections
form a chain that is very vulnerable to disruption.
- More route negotiation would allow for better knowledge of the network, which could be rendered into a cool diagram.
- Message signing to prevent the currently trivially easy message forging.