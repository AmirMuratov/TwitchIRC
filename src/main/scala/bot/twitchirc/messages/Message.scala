package bot.twitchirc.messages

/**
  * Created by amir.
  */
sealed trait Message

object Message {

  private[twitchirc] case object Ping extends Message

  case class UnknownMessage(message: String) extends Message

  private[twitchirc] case class JoinConfirmation(channel: String) extends Message
  private[twitchirc] case class PartConfirmation(channel: String) extends Message

  case class UserJoinedChannel(user: String, channel: String) extends Message
  case class UserLeftChannel(user: String, channel: String) extends Message
  case class UserGainModeMessage(user: String, channel: String) extends Message
  case class UserLostModeMessage(user: String, channel: String) extends Message
  case class ChannelUserList(channel: String, users: Seq[String]) extends Message
  private[twitchirc] case class EndOfUserList(channel: String) extends Message

  case class IncomingMessage(tags: Map[String, String], user: String, channel: String, message: String) extends Message
  case class IncomingWhisper(tags: Map[String, String], user: String, message: String) extends Message
  case class UserBan(tags: Map[String, String], channel: String, user: String) extends Message
  case class GlobalUserState(tags: Map[String, String]) extends Message
  case class RoomState(tags: Map[String, String], channel: String) extends Message
  case class UserNotice(tags: Map[String, String], channel: String, message: String) extends Message
  case class UserState(tags: Map[String, String], channel: String) extends Message

  case class HostStart(channel: String, hostChannel: String, viewers: Option[Int]) extends Message
  case class HostStop(channel: String, viewers: Option[Int]) extends Message
  case class Notice(tags: Map[String, String], channel: String, message: String) extends Message

}