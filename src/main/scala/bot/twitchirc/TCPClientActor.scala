package bot.twitchirc

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import bot.twitchirc.TCPClientActor.{CloseConnection, DataReceived, SendData, WriteFailed, _}

/**
  * Created by amir.
  */
private[twitchirc] class TCPClientActor(remote: InetSocketAddress) extends Actor with ActorLogging {

  import Tcp._
  import context.system

  log.info(s"trying to connect ${remote.getHostName}:${remote.getPort} ")
  private val manager = IO(Tcp)
  manager ! Connect(remote)

  private var buffer: String = ""

  override def postStop(): Unit = {
    manager ! Close
  }

  private val delimiter = List(13, 10).map(_.toChar).mkString

  def receive: Receive = {
    case CommandFailed(_: Connect) ⇒
      context.parent ! ConnectionFailed("can't connect to server")
      context stop self
    case Connected(_, _) ⇒
      context.parent ! TCPClientActor.Connected
      val connection = sender()
      connection ! Register(self)
      context become {
        case SendData(data) ⇒
          //log.info(s"sending: $data")
          connection ! Write(ByteString(data + "\n"))
        case CloseConnection ⇒
          connection ! Close
          context stop self

        case CommandFailed(w: Write) ⇒
          // O/S buffer was full
          context.parent ! WriteFailed(w.data.toString())
        case Received(data) ⇒
          val split = (buffer ++ data.utf8String).split(delimiter, -1)
          buffer = split.last
          split.init foreach { line =>
            //log.info(s"received: $line")
            context.parent ! DataReceived(line)
          }
      }
  }
}

object TCPClientActor {
  def props(remote: InetSocketAddress) =
    Props(classOf[TCPClientActor], remote)

  case object Connected

  case class ConnectionFailed(message: String)

  private[twitchirc] case class DataReceived(data: String)

  private[twitchirc] case class WriteFailed(data: String)

  private[twitchirc] case class SendData(data: String)

  private[twitchirc] case object CloseConnection

}