import akka.actor._
import akka.actor.Props
import scala.math._

sealed trait Gossip

case class StartGossip(message: String) extends Gossip
case class ReportMsgRecvd(message: String) extends Gossip
case class Initialize(actorRefs: Array[ActorRef]) extends Gossip

class Node(listener: ActorRef, numResend: Int) extends Actor {
  var neighbors:Array[ActorRef] = null
  var numMsgHeard = 0

  def receive = {
    case StartGossip(message) =>
      //println("recieved a StartGossip message in " +self)
      numMsgHeard += 1

      // Notify listener after getting the first message (to help find convergence)
      if (numMsgHeard == 1)
        listener ! ReportMsgRecvd(message)

      // If the current rumour has been heard < 10 times, send the message again
      if (numMsgHeard < 10) {
        // Get a random neighbor 
        var randNeighbor = scala.util.Random.nextInt(neighbors.length)
        //println("Sending msg to " + randNeighbor)
        neighbors(randNeighbor) ! StartGossip(message)
      }

    case Initialize(actorRefs) =>
      neighbors = actorRefs
  }
}

class Listener extends Actor {
  var msgsReceived = 0

  def receive = {
    case ReportMsgRecvd(message) =>
      msgsReceived += 1
      println(msgsReceived + " : " + sender)
      //println(msgsReceived)
  }
}


object GossipProtocol extends App {
  override def main(args: Array[String]) {
    if(args.length != 3) {
      println("Error: Enter the correct arguments");
      System.exit(1)
    }

    val system = ActorSystem("Gossip")
    var topology = ""
    var protocol = ""
    var numNodes = 0
    var i = 0
    var j = 0
    var k = 0
    val numResend = 10
    var cuberoot = 1

    topology = args(1)
    protocol = args(2)

    if(isAllDigits(args(0)) == true) {
      if(topology == "3D" || topology == "Imp3D") {
        var temp = args(0).toInt
        cuberoot = ceil(pow(temp, 0.333)).toInt
        numNodes = pow(cuberoot, 3.0).toInt
      } else {
        numNodes = args(0).toInt
      }
      } else {
        println("Error: First argument must be an integer");
        System.exit(1);
      }

      //Validate topology and protocol

      val listener = system.actorOf(Props[Listener], name = "listener")

      // Randomly select the leader node
      val leader = scala.util.Random.nextInt(numNodes)

      // Consider all the topologies
      topology match {
        case "full" =>
          var Nodes:Array[ActorRef] = new Array[ActorRef](numNodes)
          for( i <- 0 until numNodes) {
            Nodes(i) = system.actorOf(Props(new Node(listener, numResend)));
          }
          for( i <- 0 until numNodes) {
            Nodes(i) ! Initialize(Nodes)
          }

          Nodes(leader) ! StartGossip("Hello")


        case "3D" =>
          // Construct an array of 6 neighboring nodes

          var cubesquare = pow(cuberoot,2)
          var Nodes = Array.ofDim[ActorRef](cuberoot,cuberoot,cuberoot)

          for(i <- 0 until cuberoot) {
            for(j <- 0 until cuberoot) {
              for(k <- 0 until cuberoot) {
                Nodes(i)(j)(k) = system.actorOf(Props(new Node(listener, numResend)));
              }
            }
          }


          for(i <- 0 until cuberoot) {
            for(j <- 0 until cuberoot) {
              for(k <- 0 until cuberoot) {
                var NeighborArray = Array(Nodes((i-1+cuberoot)%cuberoot)(j)(k), Nodes((i+1+cuberoot)%cuberoot)(j)(k), Nodes(i)((j-1+cuberoot)%cuberoot)(k), Nodes(i)((j+1+cuberoot)%cuberoot)(k), Nodes(i)(j)((k-1+cuberoot)%cuberoot), Nodes(i)(j)((k+1+cuberoot)%cuberoot))
                Nodes(i)(j)(k) ! Initialize(NeighborArray)
              }
            }
          }

          val d1 = scala.util.Random.nextInt(cuberoot)
          val d2 = scala.util.Random.nextInt(cuberoot)
          val d3 = scala.util.Random.nextInt(cuberoot)

          Nodes(d1)(d2)(d3) ! StartGossip("J'aime le chocolat")
        case "line" =>



        case "imp3D" =>



        case _ =>
          println("Error: Invalid topology")
          System.exit(1)
      }

  } 
  def isAllDigits(x: String) = x forall Character.isDigit
}
