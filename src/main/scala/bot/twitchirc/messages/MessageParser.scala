package bot.twitchirc.messages

import bot.twitchirc.messages.Message._

import scala.util.Try

/**
  * Created by amir.
  */
class MessageParser(nick: String) {

  private val nameTemplate = "[^ @!:-]*"

  private val pingRegexp = "PING :tmi.twitch.tv".r
  private val successfulJoin = s":$nick!$nick@$nick.tmi.twitch.tv JOIN ($nameTemplate)".r
  private val successfulPart = s":$nick!$nick@$nick.tmi.twitch.tv PART ($nameTemplate)".r
  private val successfulPrivmsg = s":$nick!$nick@$nick.tmi.twitch.tv PRIVMSG ($nameTemplate) :(.*)".r //should be before incomingChatMessage

  //membership
  private val userJoinedChannel = s":($nameTemplate)!$nameTemplate@$nameTemplate.tmi.twitch.tv JOIN ($nameTemplate)".r
  private val userLeftChannel = s":($nameTemplate)!$nameTemplate@$nameTemplate.tmi.twitch.tv PART ($nameTemplate)".r
  private val userGainMode = s":jtv MODE ($nameTemplate) \\+o ($nameTemplate)".r
  private val userLostMode = s":jtv MODE ($nameTemplate) -o ($nameTemplate)".r
  private val channelUserList = s":$nick.tmi.twitch.tv 353 $nick = ($nameTemplate) :(.*)".r
  private val endOfUserList = s":$nick.tmi.twitch.tv 366 $nick ($nameTemplate) :End of /NAMES list".r

  //tags
  private val incomingChatMessage = s"([^ ]*) :($nameTemplate)!$nameTemplate@$nameTemplate.tmi.twitch.tv PRIVMSG ($nameTemplate) :(.*)".r
  private val incomingWhisper = s"([^ ]*) :($nameTemplate)!$nameTemplate@$nameTemplate.tmi.twitch.tv WHISPER $nick :(.*)".r
  private val userBan = s"([^ ]*) :tmi.twitch.tv CLEARCHAT ($nameTemplate) :($nameTemplate)".r
  private val globalUserState = s"([^ ]*) :tmi.twitch.tv GLOBALUSERSTATE".r
  private val roomState = s"([^ ]*) :tmi.twitch.tv ROOMSTATE ($nameTemplate)".r
  private val userNotice = s"([^ ]*) :tmi.twitch.tv USERNOTICE ($nameTemplate)( :.*)?".r
  private val userState = s"([^ ]*) :tmi.twitch.tv USERSTATE ($nameTemplate)".r

  //UnknownMessage(:tmi.twitch.tv HOSTTARGET #dota2ruhub :lightofheaven -)

  //commands
  private val hostStart = s":tmi.twitch.tv HOSTTARGET ($nameTemplate) :($nameTemplate) (.*)".r
  private val hostStop = s":tmi.twitch.tv HOSTTARGET ($nameTemplate) :- (.*)".r
  private val notice = s"([^ ]*) :tmi.twitch.tv NOTICE ($nameTemplate) :(.*)".r
  //todo
  //??? RECONNECT 	Rejoin channels after a restart.

  def parseLine(line: String): Message = {
    line match {
      case pingRegexp() => Ping
      case successfulJoin(channel) => JoinConfirmation(channel)
      case successfulPart(channel) => PartConfirmation(channel)
      case successfulPrivmsg(channel, message) => MessageDeliverConfirmation(channel, message)

      case userJoinedChannel(user, channel) => UserJoinedChannel(user, channel)
      case userLeftChannel(user, channel) => UserLeftChannel(user, channel)
      case userGainMode(user, channel) => UserGainModeMessage(user, channel)
      case userLostMode(user, channel) => UserLostModeMessage(user, channel)
      case channelUserList(channel, users) => ChannelUserList(channel, parseUsers(users))
      case endOfUserList(channel) => EndOfUserList(channel)

      case incomingChatMessage(tags, user, channel, message) => IncomingMessage(parseTags(tags), user, channel, message)
      case incomingWhisper(tags, user, message) => IncomingWhisper(parseTags(tags), user, message)
      case userBan(tags, channel, user) => UserBan(parseTags(tags), channel, user)
      case globalUserState(tags) => GlobalUserState(parseTags(tags))
      case roomState(tags, channel) => RoomState(parseTags(tags), channel)
      case userNotice(tags, channel, message) => UserNotice(parseTags(tags), channel, if (message != null) message.substring(2) else "")
      case userState(tags, channel) => UserState(parseTags(tags), channel)

      case hostStart(channel, hostChannel, viewers) => HostStart(channel, hostChannel, Try(viewers.toInt).toOption)
      case hostStop(channel, viewers) => HostStop(channel, Try(viewers.toInt).toOption)
      case notice(tags, channel, message) => Notice(parseTags(tags), channel, message)

      case message => UnknownMessage(message)
    }
  }

  private def parseUsers(users: String): Seq[String] = {
    users.split(" ")
  }

  //@ban-duration=<ban-duration>;ban-reason=<ban-reason>
  private def parseTags(tags: String): Map[String, String] = {
    tags.tail split ";" map { tag =>
      val parsedTag = tag.split("=", -1)
      (parsedTag(0), parsedTag(1))
    } toMap
  }
}


