package bot.twitchirc

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import bot.twitchirc.TCPClientActor.{Connected, ConnectionFailed, DataReceived, SendData}
import bot.twitchirc.TwitchIRCActor._
import bot.twitchirc.messages.Message._
import bot.twitchirc.messages.MessageParser

import scala.concurrent.duration._

/**
  * Created by amir.
  */
class TwitchIRCActor(address: InetSocketAddress, nick: String, oauth: String, props: TwitchIRCProps) extends Actor with ActorLogging {

  import context.dispatcher

  assert(nick.forall(!_.isUpper))

  private val respondTimeout = 30 seconds

  private val successfulConnectionMessage = s":tmi.twitch.tv 376 $nick :>"
  private val successfulMembership = ":tmi.twitch.tv CAP * ACK :twitch.tv/membership"
  private val successfulTags = ":tmi.twitch.tv CAP * ACK :twitch.tv/tags"
  private val successfulCommands = ":tmi.twitch.tv CAP * ACK :twitch.tv/commands"

  private val tcpClientActor = context.actorOf(TCPClientActor.props(address), "TCPClient")
  private var timeoutTerminator: Cancellable = _
  private var membershipTimeout: Cancellable = _
  private var tagsTimeout: Cancellable = _
  private var commandsTimeout: Cancellable = _
  private val messageParser = new MessageParser(nick)
  private var listeners: Set[ActorRef] = Set.empty

  private var joins: Map[String, Cancellable] = Map.empty
  private var parts: Map[String, Cancellable] = Map.empty
  private var channelsToUsers: Map[String, Seq[String]] = Map.empty

  override def preStart(): Unit = {
    log.info("starting TwitchIrc")
  }

  override def receive: Receive = {
    case err@ConnectionFailed(_) =>
      log.error("Connection to twitch server failed. Shutting down.")
      listeners.foreach(_ ! err)
      context stop self
    case Connected =>
      log.info("Successfully connected to twitch server")
      if (props.membership) {
        tcpClientActor ! SendData("CAP REQ :twitch.tv/membership")
        membershipTimeout = context.system.scheduler.scheduleOnce(respondTimeout)({
          log.error("Twitch server doesn't respond for membership capability request")
        })
      }
      if (props.tags) {
        tcpClientActor ! SendData("CAP REQ :twitch.tv/tags")
        tagsTimeout = context.system.scheduler.scheduleOnce(respondTimeout)({
          log.error("Twitch server doesn't respond for tags capability request")
        })
      }
      if (props.commands) {
        tcpClientActor ! SendData("CAP REQ :twitch.tv/commands")
        commandsTimeout = context.system.scheduler.scheduleOnce(respondTimeout)({
          log.error("Twitch server doesn't respond for commands capability request")
        })
      }
      //try to login
      tcpClientActor ! SendData(s"PASS $oauth")
      tcpClientActor ! SendData(s"NICK $nick")
      timeoutTerminator = context.system.scheduler.scheduleOnce(respondTimeout)({
        log.error("Connected to server, but can't log in")
        listeners.foreach(_ ! ConnectionFailed("Connected to server, but can't log in"))
        context stop self
      })
    case DataReceived(`successfulMembership`) if props.membership =>
      membershipTimeout.cancel()
      log.info("membership capability included")
    case DataReceived(`successfulTags`) if props.tags =>
      tagsTimeout.cancel()
      log.info("tags capability included")
    case DataReceived(`successfulCommands`) if props.commands =>
      commandsTimeout.cancel()
      log.info("commands capability included")
    case DataReceived(`successfulConnectionMessage`) =>
      timeoutTerminator.cancel()
      log.info("Successfully logged in")
      self ! Join("#" + nick)
      context become commandParser
      listeners.foreach(_ ! Connected)
    case AddListener(listener: ActorRef) => listeners += listener
    case DeleteListener(listener: ActorRef) => listeners -= listener
  }

  private def commandParser: Receive = {
    case DataReceived(line) =>
      messageParser.parseLine(line) match {
        case Ping =>
          tcpClientActor ! SendData("PONG :tmi.twitch.tv")
        case JoinConfirmation(channel) =>
          joins.get(channel).foreach(_.cancel())
          joins -= channel
        case PartConfirmation(channel) =>
          parts.get(channel).foreach(_.cancel())
          parts -= channel

        case ChannelUserList(channel, users) =>
          channelsToUsers += channel -> (users ++ channelsToUsers.getOrElse(channel, Seq()))
        case EndOfUserList(channel) =>
          listeners.foreach(_ ! ChannelUserList(channel, channelsToUsers.getOrElse(channel, Seq())))
          channelsToUsers -= channel

        case msg@(UserJoinedChannel(_, _)
                  | UserLeftChannel(_, _)
                  | UserGainModeMessage(_, _)
                  | UserLostModeMessage(_, _)
                  | IncomingMessage(_, _, _, _)
                  | IncomingWhisper(_, _, _)
                  | UserBan(_, _, _)
                  | GlobalUserState(_)
                  | RoomState(_, _)
                  | UserNotice(_, _, _)
                  | UserState(_, _)
                  | HostStart(_, _, _)
                  | HostStop(_, _)
                  | Notice(_, _, _)) => listeners.foreach(_ ! msg)
        case msg@UnknownMessage(_) => listeners.foreach(_ ! msg)
      }
    case Join(name) =>
      val channelName = name.toLowerCase
      tcpClientActor ! SendData(s"JOIN $channelName")
      joins += channelName -> context.system.scheduler.scheduleOnce(respondTimeout)({
        log.error(s"Twitch server doesn't respond JOIN $channelName request")
        joins -= channelName
      })
    case Part(name) =>
      val channelName = name.toLowerCase
      tcpClientActor ! SendData(s"PART $channelName")
      parts += channelName -> context.system.scheduler.scheduleOnce(respondTimeout)({
        log.error(s"Twitch server doesn't respond PART $channelName request")
        parts -= channelName
      })
    case SendMessage(name, message) =>
      val channelName = name.toLowerCase
      tcpClientActor ! SendData(s"PRIVMSG $channelName :$message")
    case SendWhisper(name, message) =>
      val userName = name.toLowerCase
      tcpClientActor ! SendData(s"PRIVMSG #$nick :/w $userName $message")
    case AddListener(listener: ActorRef) => listeners += listener
    case DeleteListener(listener: ActorRef) => listeners -= listener
  }

}

object TwitchIRCActor {

  /**
    *
    * @param membership Adds membership state event data
    * @param tags       Adds IRC V3 message tags to several commands, if enabled with the commands capability.
    * @param commands   Enables several Twitch-specific commands
    * @param botType    throttling parameter
    */
  case class TwitchIRCProps(membership: Boolean = true, tags: Boolean = true, commands: Boolean = true, botType: BotType = KnownBot)

  sealed trait BotType
  case object KnownBot extends BotType
  case object Verified extends BotType

  private val defaultAddress = new InetSocketAddress("irc.chat.twitch.tv", 6667)

  private val defaultProps = TwitchIRCProps()

  def props(nick: String, oauth: String, address: InetSocketAddress = defaultAddress, props: TwitchIRCProps = defaultProps): Props =
    Props(new TwitchIRCActor(address, nick.toLowerCase, oauth, props))

  case class Join(channelName: String)

  case class Part(channelName: String)

  case class SendMessage(channelName: String, message: String)

  case class SendWhisper(userName: String, message: String)

  case class AddListener(listener: ActorRef)

  case class DeleteListener(listener: ActorRef)


}