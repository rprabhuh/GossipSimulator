import akka.actor._
import akka.actor.Props
import scala.math._

sealed trait FailureGossip

case class FailureInitialize(actorRefs: Array[ActorRef]) extends FailureGossip
case class FailureStartGossip(message: String, timeToSleep: Int) extends FailureGossip
case class FailureReportMsgRecvd(message: String) extends FailureGossip
case class FailureStartPushSum(delta: Double) extends FailureGossip
case class FailureComputePushSum(s: Double, w: Double, delta: Double) extends FailureGossip
case class FailureResult(sum: Double, weight: Double) extends FailureGossip 
case class FailureRecordStartTime(startTime:Long) extends FailureGossip
case class FailureRecordNumPeople(strtTime: Int) extends FailureGossip
case class FailureGoToSleep(timeToSleep: Int) extends FailureGossip
case class FailureLicenseToKill(nodesRef: Array[ActorRef]) extends FailureGossip
case class FailureKill(timeToSleep: Int) extends FailureGossip


// Contract Killer
class ContractKiller(nodesToKill: Int) extends Actor {

    def receive = {
        case FailureLicenseToKill(nodesRef) =>
            for(i <- 0 until nodesToKill) {
                nodesRef(i) ! FailureKill(10)
                //Thread.sleep(1)
            }

        case _ =>
            println("Error: Invalid message!")
            System.exit(1)
    }
}

class FailureNode(listener: ActorRef, numResend: Int, nodeNum: Int, state: String) extends Actor {
    var neighbors:Array[ActorRef] = null
    var numMsgHeard = 0 
    var currState = state

    // Used for Push-Sum only
    var sum = nodeNum.toDouble
    var weight = 1.0
    var termRound = 1 

    def receive = {
        case FailureInitialize(actorRefs) =>
            neighbors = actorRefs 

        case FailureKill(timeToSleep) =>
            //println(self + " - Going to sleep")
            currState = "DEAD"
            //Thread.sleep(timeToSleep)
            //println(self + " - I'm UP!")

        case FailureStartGossip(message, timeToSleep) =>
            if (currState == "ALIVE") {
    /*            if (timeToSleep != 0)
                    Thread.sleep(timeToSleep)*/
                //println(self + " - "+ message)
                numMsgHeard += 1
    
                // Notify listener after getting the first message (to help find convergence)
                if (numMsgHeard == 1)
                    listener ! FailureReportMsgRecvd(message)
    
                // If the current rumour has been heard < 100 times, send the message again
                if (numMsgHeard < 10000) {
                    // Get a random neighbor 
                    var randNeighbor = scala.util.Random.nextInt(neighbors.length)
                    //println("Sending msg to " + randNeighbor)
                    neighbors(randNeighbor) ! FailureStartGossip(message, 0)
                }   
            }


        case FailureStartPushSum(delta) => 
            //println("Node "+ self + ": Starting Push-Sum . . . .")
            //println("\n********** NODE " + nodeNum + " ************")
            //println("Sum = " + sum + " Weight = " + weight + " Average = " + (sum/weight))

            var randNeighbor = scala.util.Random.nextInt(neighbors.length)
            sum = sum/2
            weight = weight/2
            neighbors(randNeighbor) ! FailureComputePushSum(sum, weight, delta)


        case FailureComputePushSum(s, w, delta) =>
            //println("\n********** NODE " + nodeNum + " ************")
            //println("Sum = " + sum + " Weight = " + weight + " Average = " + (sum/weight))
            //println("Received: Sum = " + s + "\tWeight = " + w)
            //println("TOTAL: Sum = " + (sum+s) + "\tWeight = " + (weight+w))
            if (currState == "ALIVE") {
    
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
                    neighbors(randNeighbor) ! FailureComputePushSum(sum, weight, delta)
    
                }
                else if (termRound >= 3)  {
                    // println("TERMINATING: Sum = " + sum + "\tWeight = " + weight + "\tAverage = " + sum/weight)
                    listener ! FailureResult(sum, weight)
                }
                else {
                    var randNeighbor = scala.util.Random.nextInt(neighbors.length)
                    sum = sum/2
                    weight = weight/2
                    neighbors(randNeighbor) ! FailureComputePushSum(sum, weight, delta)
                    termRound += 1
                }
            }


        case _ =>
            println("Error: Invalid message!")
            System.exit(1)
    }
}

class FailureListener extends Actor {
    var msgsReceived = 0
    var startTime = 0L
    var numPeople = 0
    def receive = {
        case FailureReportMsgRecvd(message) =>
            var endTime = System.currentTimeMillis()
            msgsReceived += 1
            //println(msgsReceived) // + " : " + sender)
            if(msgsReceived == numPeople) {
                println("Time for convergence: "+(endTime-startTime)+"ms")
                System.exit(0)
            }
            //println(msgsReceived)

        case FailureResult(sum, weight) =>
            var endTime = System.currentTimeMillis()
            println("Sum = " + sum + " Weight = " + weight + " Average = " + (sum/weight))
            println("Time for convergence: " + (endTime-startTime) +"ms")
            System.exit(0)

        case FailureRecordStartTime(strtTime) =>
            startTime = strtTime

        case FailureRecordNumPeople(numpeople) =>
            numPeople = numpeople

        case _ =>
            println("Error: Invalid message!")
            System.exit(1)
    }
}


object GossipProtocol extends App {
    override def main(args: Array[String]) {
        if(args.length != 4) {
            println("Error: Enter the correct arguments");
            System.exit(1)
        }

        val system = ActorSystem("Gossip")
        var topology = ""
        var protocol = ""
        var percentFailure = 0
        var nodesToKill = 0
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

            // Check for the failure model parameter
            if (!isAllDigits(args(3))) {
                println("Error: Invalid argument. Please enter an integer.");
                System.exit(1);    
            }
            else {
                percentFailure = args(3).toInt
                nodesToKill = floor(percentFailure*numNodes/100).toInt
            }

            val listener = system.actorOf(Props[FailureListener], name = "listener")

            println("Building topology . . . .")


            // Consider all the topologies
            topology match {

                // FULL TOPOLOGY
                case "full" =>
                    var Nodes:Array[ActorRef] = new Array[ActorRef](numNodes)
                    for( i <- 0 until numNodes) {
                        Nodes(i) = system.actorOf(Props(new FailureNode(listener, numResend, i+1, "ALIVE")));
                    }
                    for( i <- 0 until numNodes) {
                        Nodes(i) ! FailureInitialize(Nodes)
                    }

                    var nodesFail:Array[ActorRef] = new Array[ActorRef](nodesToKill)
                    for(i <- 0 until nodesToKill) {
                        val victim = scala.util.Random.nextInt(numNodes)
                        nodesFail(i) = Nodes(victim)
                    }
                    

                    // Randomly select the leader node
                    val leader = scala.util.Random.nextInt(numNodes)
                    if (protocol == "gossip") {
                        listener ! FailureRecordNumPeople(numNodes)
                        listener ! FailureRecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Gossip")
                        Nodes(leader) ! FailureStartGossip("Hello", 20)

                    }
                    else if (protocol == "push-sum") {
                        listener ! FailureRecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Push-Sum")
                        Nodes(leader) ! FailureStartPushSum(pow(10, -1))
                    }

                    // Activate the killer
                    var killer = system.actorOf(Props(new ContractKiller(nodesToKill)))
                    killer ! FailureLicenseToKill(nodesFail)


                    // 3D TOPOLOGY
                case "3D" =>
                    // Construct an array of 6 neighboring nodes
                    var cubesquare = pow(cuberoot,2).toInt
                    var Nodes = Array.ofDim[ActorRef](cuberoot,cuberoot,cuberoot)

                    for(i <- 0 until cuberoot) {
                        for(j <- 0 until cuberoot) {
                            for(k <- 0 until cuberoot) {
                                Nodes(i)(j)(k) = system.actorOf(Props(new FailureNode(listener, numResend,(i*cubesquare)+(j*cuberoot)+k+1, "ALIVE")));
                            }
                        }
                    }

                    for(i <- 0 until cuberoot) {
                        for(j <- 0 until cuberoot) {
                            for(k <- 0 until cuberoot) {
                                var NeighborArray = Array(Nodes((i-1+cuberoot)%cuberoot)(j)(k), Nodes((i+1+cuberoot)%cuberoot)(j)(k),
                                    Nodes(i)((j-1+cuberoot)%cuberoot)(k), Nodes(i)((j+1+cuberoot)%cuberoot)(k),
                                    Nodes(i)(j)((k-1+cuberoot)%cuberoot), Nodes(i)(j)((k+1+cuberoot)%cuberoot))
                                Nodes(i)(j)(k) ! FailureInitialize(NeighborArray)
                            }
                        }
                    }



                    var nodesFail:Array[ActorRef] = new Array[ActorRef](nodesToKill)
                    for(i <- 0 until nodesToKill) {
                        val victim1 = scala.util.Random.nextInt(cuberoot)
                        val victim2 = scala.util.Random.nextInt(cuberoot)
                        val victim3 = scala.util.Random.nextInt(cuberoot)
                        nodesFail(i) = Nodes(victim1)(victim2)(victim3)
                    }
                    
                    val d1 = scala.util.Random.nextInt(cuberoot)
                    val d2 = scala.util.Random.nextInt(cuberoot)
                    val d3 = scala.util.Random.nextInt(cuberoot)

                    if (protocol == "gossip") {
                        listener ! FailureRecordNumPeople(numNodes)
                        listener ! FailureRecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Gossip")
                        Nodes(d1)(d2)(d3) ! FailureStartGossip("J'aime le chocolat", 100)
                    }
                    else if (protocol == "push-sum") {
                        listener ! FailureRecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Push-Sum")
                        Nodes(d1)(d2)(d3) ! FailureStartPushSum(pow(10, -1))
                    }

                    // Activate the killer
                    var killer = system.actorOf(Props(new ContractKiller(nodesToKill)))
                    killer ! FailureLicenseToKill(nodesFail)


                    // LINE TOPOLOGY
                case "line" =>
                    var Nodes:Array[ActorRef] = new Array[ActorRef](numNodes)
                    for(i <- 0 until numNodes) {
                        Nodes(i) = system.actorOf(Props(new FailureNode(listener, numResend, i+1, "ALIVE")));
                    }

                    for(i <- 0 until numNodes) {
                        var NeighborArray = Array(Nodes((i-1+numNodes)%numNodes), Nodes((i+1+numNodes)%numNodes))
                        Nodes(i) ! FailureInitialize(NeighborArray)
                    }

                    var nodesFail:Array[ActorRef] = new Array[ActorRef](nodesToKill)
                    for(i <- 0 until nodesToKill) {
                        val victim = scala.util.Random.nextInt(numNodes)
                        nodesFail(i) = Nodes(victim)
                    }

                    // Randomly select the leader node
                    val leader = scala.util.Random.nextInt(numNodes)
                    if (protocol == "gossip") {
                        listener ! FailureRecordNumPeople(numNodes)
                        listener ! FailureRecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Gossip")
                        Nodes(leader) ! FailureStartGossip("Let's get to work!", 100)
                    } else if (protocol == "push-sum") {
                        listener ! FailureRecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Push-Sum")
                        println("Main() - Calling now..")
                        Nodes(leader) ! FailureStartPushSum(pow(10, -3))
                    }

                    // Activate the killer
                    var killer = system.actorOf(Props(new ContractKiller(nodesToKill)))
                    killer ! FailureLicenseToKill(nodesFail)



                    // IMPERFECT 3D TOPOLOGY
                case "imp3D" =>
                    var cubesquare = pow(cuberoot,2).toInt
                    var Nodes = Array.ofDim[ActorRef](cuberoot,cuberoot,cuberoot)

                    for(i <- 0 until cuberoot) {
                        for(j <- 0 until cuberoot) {
                            for(k <- 0 until cuberoot) {
                                Nodes(i)(j)(k) = system.actorOf(Props(new FailureNode(listener, numResend,(i*cubesquare)+(j*cuberoot)+k+1, "ALIVE")));
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

                                Nodes(i)(j)(k) ! FailureInitialize(NeighborArray)
                            }
                        }
                    }


                    var nodesFail:Array[ActorRef] = new Array[ActorRef](nodesToKill)
                    for(i <- 0 until nodesToKill) {
                        val victim1 = scala.util.Random.nextInt(cuberoot)
                        val victim2 = scala.util.Random.nextInt(cuberoot)
                        val victim3 = scala.util.Random.nextInt(cuberoot)
                        nodesFail(i) = Nodes(victim1)(victim2)(victim3)
                    }

                    d1 = scala.util.Random.nextInt(cuberoot)
                    d2 = scala.util.Random.nextInt(cuberoot)
                    d3 = scala.util.Random.nextInt(cuberoot)

                    if (protocol == "gossip") {
                        listener ! FailureRecordNumPeople(numNodes)
                        listener ! FailureRecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Gossip")
                        Nodes(d1)(d2)(d3) ! FailureStartGossip("J'aime le piment", 100)
                    }
                    else if (protocol == "push-sum") {
                        listener ! FailureRecordStartTime(System.currentTimeMillis())
                        println("Starting Protocol Push-Sum")
                        Nodes(d1)(d2)(d3) ! FailureStartPushSum(pow(10, -3))
                    }

                    // Activate the killer
                    var killer = system.actorOf(Props(new ContractKiller(nodesToKill)))
                    killer ! FailureLicenseToKill(nodesFail)

                case _ =>
                    println("Error: Invalid topology")
                    System.exit(1)
            }
    } 

    def isAllDigits(x: String) = x forall Character.isDigit
}
