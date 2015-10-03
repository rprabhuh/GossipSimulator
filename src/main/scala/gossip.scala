import akka.actor._
import akka.actor.Props
import scala.math._

sealed trait Gossip

case class Initialize(actorRefs: Array[ActorRef]) extends Gossip
case class StartGossip(message: String) extends Gossip
case class ReportMsgRecvd(message: String) extends Gossip
case class StartPushSum(delta: Double) extends Gossip
case class ComputePushSum(s: Double, w: Double, delta: Double) extends Gossip
case class Result(sum: Double, weight: Double) extends Gossip 
case class RecordStartTime(startTime:Long) extends Gossip
case class RecordNumPeople(strtTime: Int) extends Gossip

class Node(listener: ActorRef, numResend: Int, nodeNum: Int) extends Actor {
    var neighbors:Array[ActorRef] = null
    var numMsgHeard = 0 


    // Used for Push-Sum only
    var sum = nodeNum.toDouble
    var weight = 1.0
    var termRound = 1 

    def receive = {
        case Initialize(actorRefs) =>
            neighbors = actorRefs 
        case StartGossip(message) =>
            //println("recieved a StartGossip message in " +self)
            numMsgHeard += 1

            // Notify listener after getting the first message (to help find convergence)
            if (numMsgHeard == 1)
                listener ! ReportMsgRecvd(message)

            // If the current rumour has been heard < 10 times, send the message again
            if (numMsgHeard < 100) {
                // Get a random neighbor 
                var randNeighbor = scala.util.Random.nextInt(neighbors.length)
                //println("Sending msg to " + randNeighbor)
                neighbors(randNeighbor) ! StartGossip(message)
            }


        case StartPushSum(delta) => 
            //println("Node "+ self + ": Starting Push-Sum . . . .")
            //println("\n********** NODE " + nodeNum + " ************")
            //println("Sum = " + sum + " Weight = " + weight + " Average = " + (sum/weight))

            var randNeighbor = scala.util.Random.nextInt(neighbors.length)
            sum = sum/2
            weight = weight/2
            neighbors(randNeighbor) ! ComputePushSum(sum, weight, delta)


        case ComputePushSum(s, w, delta) =>
            //println("\n********** NODE " + nodeNum + " ************")
            //println("Sum = " + sum + " Weight = " + weight + " Average = " + (sum/weight))
            //println("Received: Sum = " + s + "\tWeight = " + w)
            //println("TOTAL: Sum = " + (sum+s) + "\tWeight = " + (weight+w))
            var newsum = sum + s
            var newweight = weight + w
            // Check for divergence, and terminate if avg is within delta for 3 consecutive rounds
            if (abs(sum/weight - newsum/newweight) > delta) {
                termRound = 0
                sum += s
                weight += w

                sum = sum/2
                weight = weight/2
                var randNeighbor = scala.util.Random.nextInt(neighbors.length)
                neighbors(randNeighbor) ! ComputePushSum(sum, weight, delta)

            }
            else if (termRound >= 3)  {
                // println("TERMINATING: Sum = " + sum + "\tWeight = " + weight + "\tAverage = " + sum/weight)
                listener ! Result(sum, weight)
            }
            else {
                var randNeighbor = scala.util.Random.nextInt(neighbors.length)
                sum = sum/2
                weight = weight/2
                neighbors(randNeighbor) ! ComputePushSum(sum, weight, delta)
                termRound += 1
            }


        case _ =>
            println("Error: Invalid message!")
            System.exit(1)
    }
}

class Listener extends Actor {
    var msgsReceived = 0
    var startTime = 0L
    var numPeople = 0
    def receive = {
        case ReportMsgRecvd(message) =>
            var endTime = System.currentTimeMillis()
            msgsReceived += 1
            //println(msgsReceived + " : " + sender)
            if(msgsReceived == numPeople) {
                println("Time for convergence: "+(endTime-startTime)+"ms")
                System.exit(0)
            }
            //println(msgsReceived)

        case Result(sum, weight) =>
            var endTime = System.currentTimeMillis()
            println("Sum = " + sum + " Weight = " + weight + " Average = " + (sum/weight))
            println("Time for convergence: " + (endTime-startTime) +"ms")
            System.exit(0)

        case RecordStartTime(strtTime) =>
            startTime = strtTime

        case RecordNumPeople(numpeople) =>
            numPeople = numpeople
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
            if(topology == "3D" || topology == "imp3D") {
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

            // Validate Protocol
            if (protocol != "gossip" && protocol != "push-sum") {
                println("Error: Invalid Protocol. Please enter either gossip or push-sum.");
                System.exit(1); 
            }

            val listener = system.actorOf(Props[Listener], name = "listener")

            println("Building topology . . . .")

            // Consider all the topologies
            topology match {

                // FULL TOPOLOGY
                case "full" =>
                    var Nodes:Array[ActorRef] = new Array[ActorRef](numNodes)
                    for( i <- 0 until numNodes) {
                        Nodes(i) = system.actorOf(Props(new Node(listener, numResend, i+1)));
                    }
                    for( i <- 0 until numNodes) {
                        Nodes(i) ! Initialize(Nodes)
                    }

                    // Randomly select the leader node
                    val leader = scala.util.Random.nextInt(numNodes)
                    if (protocol == "gossip") {
                        listener ! RecordNumPeople(numNodes)
                        listener ! RecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Gossip")
                        Nodes(leader) ! StartGossip("Hello")
                    }
                    else if (protocol == "push-sum") {
                        listener ! RecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Push-Sum")
                        Nodes(leader) ! StartPushSum(pow(10, -10))
                    }


                    // 3D TOPOLOGY
                case "3D" =>
                    // Construct an array of 6 neighboring nodes
                    var cubesquare = pow(cuberoot,2).toInt
                    var Nodes = Array.ofDim[ActorRef](cuberoot,cuberoot,cuberoot)

                    for(i <- 0 until cuberoot) {
                        for(j <- 0 until cuberoot) {
                            for(k <- 0 until cuberoot) {
                                Nodes(i)(j)(k) = system.actorOf(Props(new Node(listener, numResend,(i*cubesquare)+(j*cuberoot)+k+1)));
                            }
                        }
                    }

                    for(i <- 0 until cuberoot) {
                        for(j <- 0 until cuberoot) {
                            for(k <- 0 until cuberoot) {
                                var NeighborArray = Array(Nodes((i-1+cuberoot)%cuberoot)(j)(k), Nodes((i+1+cuberoot)%cuberoot)(j)(k),
                                    Nodes(i)((j-1+cuberoot)%cuberoot)(k), Nodes(i)((j+1+cuberoot)%cuberoot)(k),
                                    Nodes(i)(j)((k-1+cuberoot)%cuberoot), Nodes(i)(j)((k+1+cuberoot)%cuberoot))
                                Nodes(i)(j)(k) ! Initialize(NeighborArray)
                            }
                        }
                    }

                    val d1 = scala.util.Random.nextInt(cuberoot)
                    val d2 = scala.util.Random.nextInt(cuberoot)
                    val d3 = scala.util.Random.nextInt(cuberoot)

                    if (protocol == "gossip") {
                        listener ! RecordNumPeople(numNodes)
                        listener ! RecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Gossip")
                        Nodes(d1)(d2)(d3) ! StartGossip("J'aime le chocolat")
                    }
                    else if (protocol == "push-sum") {
                        listener ! RecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Push-Sum")
                        Nodes(d1)(d2)(d3) ! StartPushSum(pow(10, -10))
                    }


                    // LINE TOPOLOGY
                case "line" =>
                    var Nodes:Array[ActorRef] = new Array[ActorRef](numNodes)
                    for(i <- 0 until numNodes) {
                        Nodes(i) = system.actorOf(Props(new Node(listener, numResend, i+1)));
                    }

                    for(i <- 0 until numNodes) {
                        var NeighborArray = Array(Nodes((i-1+numNodes)%numNodes), Nodes((i+1+numNodes)%numNodes))
                        Nodes(i) ! Initialize(NeighborArray)
                    }

                    // Randomly select the leader node
                    val leader = scala.util.Random.nextInt(numNodes)
                    if (protocol == "gossip") {
                        listener ! RecordNumPeople(numNodes)
                        listener ! RecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Gossip")
                        Nodes(leader) ! StartGossip("Let's get to work!")
                    } else if (protocol == "push-sum") {
                        listener ! RecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Push-Sum")
                        println("Main() - Calling now..")
                        Nodes(leader) ! StartPushSum(pow(10, -10))
                    }


                    // IMPERFECT 3D TOPOLOGY
                case "imp3D" =>
                    var cubesquare = pow(cuberoot,2).toInt
                    var Nodes = Array.ofDim[ActorRef](cuberoot,cuberoot,cuberoot)

                    for(i <- 0 until cuberoot) {
                        for(j <- 0 until cuberoot) {
                            for(k <- 0 until cuberoot) {
                                Nodes(i)(j)(k) = system.actorOf(Props(new Node(listener, numResend,(i*cubesquare)+(j*cuberoot)+k+1)));
                            }
                        }
                    }

                    var d1 = 0
                    var d2 = 0
                    var d3 = 0
                    for(i <- 0 until cuberoot) {
                        for(j <- 0 until cuberoot) {
                            for(k <- 0 until cuberoot) {

                                // Choose a random neighbor
                                d1 = scala.util.Random.nextInt(cuberoot)
                                d2 = scala.util.Random.nextInt(cuberoot)
                                d3 = scala.util.Random.nextInt(cuberoot)

                                // NeighborArray consists of 6 neighbors + 1 randomly chosen neighbor
                                var NeighborArray = Array(Nodes((i-1+cuberoot)%cuberoot)(j)(k), Nodes((i+1+cuberoot)%cuberoot)(j)(k),
                                    Nodes(i)((j-1+cuberoot)%cuberoot)(k), Nodes(i)((j+1+cuberoot)%cuberoot)(k),
                                    Nodes(i)(j)((k-1+cuberoot)%cuberoot), Nodes(i)(j)((k+1+cuberoot)%cuberoot),
                                    Nodes(d1)(d2)(d3))

                                Nodes(i)(j)(k) ! Initialize(NeighborArray)
                            }
                        }
                    }

                    d1 = scala.util.Random.nextInt(cuberoot)
                    d2 = scala.util.Random.nextInt(cuberoot)
                    d3 = scala.util.Random.nextInt(cuberoot)

                    if (protocol == "gossip") {
                        listener ! RecordNumPeople(numNodes)
                        listener ! RecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Gossip")
                        Nodes(d1)(d2)(d3) ! StartGossip("J'aime le piment")
                    }
                    else if (protocol == "push-sum") {
                        listener ! RecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Push-Sum")
                        Nodes(d1)(d2)(d3) ! StartPushSum(pow(10, -10))
                    }


                case _ =>
                    println("Error: Invalid topology")
                    System.exit(1)
            }

    } 
    def isAllDigits(x: String) = x forall Character.isDigit
}
