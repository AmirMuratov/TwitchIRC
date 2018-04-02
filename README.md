# Twitch IRC
Twitch irc implementation on scala using akka actors. Twitch server API described [here](https://dev.twitch.tv/docs/irc)
### Usage
Twitch bot is an actor which sends messages about all events to its listeners.
### Messages that can be sent to TwitchIRCActor
* ```Join(channelName: String)```
* ```Part(channelName: String)```
* ```SendMessage(channelName: String, message: String)```
* ```SendWhisper(userName: String, message: String)```
* ```AddListener(listener: ActorRef)```
* ```DeleteListener(listener: ActorRef)```
### Events
if ```tags``` is disabled then ```tags``` are empty

success/fail log in events:
* ```ConnectionFailed(err: String)```
* ```Connected```

event after logging in(contains information about user in ```tags```)
* ```GlobalUserState(tags: Map[String, String])```

message events:
* ```IncomingMessage(tags: Map[String, String], user: String, channel: String, message: String)```
* ```IncomingWhisper(tags: Map[String, String], user: String, message: String)```

sent only if ```membership``` is enabled:
* ```UserJoinedChannel(user: String, channel: String)```
* ```UserLeftChannel(user: String, channel: String)```
* ```UserGainModeMessage(user: String, channel: String)```
* ```UserLostModeMessage(user: String, channel: String)```
* ```ChannelUserList(channel: String, users: Seq[String])```

sent only if ```commands``` is enabled:
* ```UserBan(tags: Map[String, String], channel: String, user: String)```
* ```HostStart(channel: String, hostChannel: String, viewers: Option[Int])```
* ```HostStop(channel: String, viewers: Option[Int])```
* ```Notice(tags: Map[String, String], channel: String, message: String)```
* ```RoomState(tags: Map[String, String], channel: String)```
* ```UserNotice(tags: Map[String, String], channel: String, message: String)```
* ```UserState(tags: Map[String, String], channel: String)```

### Creating example
Code inside actor:
```
import bot.twitchirc.TwitchIRCActor
import bot.twitchirc.TwitchIRCActor.{AddListener, TwitchIRCProps}

val twitchIRCActor = context.actorOf(TwitchIRCActor.props(nick, oauth,
    props = TwitchIRCProps(membership = true, tags = true, commands = true)), "TwitchIRCActor")
  twitchIRCActor ! AddListener(self)
```
```nick``` - twitch login
```oauth``` - can be obtained [here](https://twitchapps.com/tmi/)
### TwitchIRCProps - case class with properties:
* membership - server send information about users join/left. Described [here](https://dev.twitch.tv/docs/irc#twitch-irc-capability-membership).
* tags - additional information about users/events in messages. Described [here](https://dev.twitch.tv/docs/irc#twitch-irc-capability-tags).
* commands - adds additional commands. Described [here](https://dev.twitch.tv/docs/irc#twitch-irc-capability-commands).