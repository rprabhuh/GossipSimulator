import akka.actor._
import akka.actor.Props

sealed trait Gossip

case class StartGossip(message: String) extends Gossip
case class ReportMsgRecvd(message: String) extends Gossip


class Node() extends Actor {
  def receive = {
    case StartGossip(message) =>

  }
}

class Listener extends Actor {
  def receive = {
    case ReportMsgRecvd(message) =>

  }
}


object gossipProtocol extends App {
  override def main(args: Array[String]) {

  }
}
