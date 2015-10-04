# Team
Rahul Prabhu

Sanil Sinai Borkar


# Compiling
SBT is used to build the code. To compile, just run the following command from the project root direcory
```
$ sbt compile
```

# Running
To run the program, type 'sbt' at the command prompt to enter the 'sbt' command-prompt.
```
$ sbt
```

The program then runs with the following command:
```
$ run <num_of_nodes> <topology> <algorithm>
```

Here, **num_of_nodes** denotes the number of nodes in the network topology, **topology** denotes the network topology, and **algorithm** denotes the algorithm to be used.


# Command Line Arguments
There are 3 command-line arguments that can be input to the program:

* **num_of_nodes**: The number of nodes in the network topology. It is a positive integer value.
* **topology**: The network topology. It should be one of 'full', 'line', '3D' or 'imp3D'.
* **algorithm**: The algorithm. It should be either 'gossip' (Gossip Protocol) or 'push-sum' (Push-Sum Algorithm).

Any other values will result in an error, and program termination.


# Topology
Each network node is represented by an (Akka) actor. There are 4 kinds of network topologies as given below:

* **Full** - Every actor is a neighbor of all other actors. That is, every actor can talk directly to any other actor.
* **Line** - Actors are arranged in a line. Each actor has only 2 neighboors (one left and one right, unless you are the first or last actor).
* **3D** - Actors form a 3D grid. The actors can only talk to the grid neigbors.
* **Imperfect 3D** - Grid arrangement but one random other neighboor is selected from the list of all actors (6+1 neighbors)


# Working
In all of the topologies, one of the nodes is selected at random, called the *leader*. This is the node which will start sending the message for each of the algorithms. Consider there are *N* nodes in the network.

* **Full topology**: The *leader* node can select any one of the (N-1) nodes to send the message to.
* **Line topology**: The *leader* node can select any one of the 2 nodes (one to its right and one to its left) to send the message to.
* **3D topology**: The nodes are arranged in a 3D grid. We have assumed that it wraps around itself (in each row, the last node of the grid connects to the first node). The *leader* node can then select any one of the 6 (left, right, front, back, above, below) nodes to send the message to.
* **Imperfect 3D**: The nodes are arranged in a 3D grid. We have assumed that it wraps around itself (in each row, the last node of the grid connects to the first node). The *leader* node can then select any one of the 7 (left, right, front, back, above, below, and a random node) nodes to send the message to.

***In case of 3D and Imperfect 3D, the number of network nodes is rounded to the nearest cube if *N* is not a perfect cube.***

### Gossip Protocol
In case of the Gossip Protocol, the message sent involves just a string that needs to propagate to all of the nodes in the network.

### Push-Sum Protocol
In this case, each actor (node) sends half its sum and half its weight as the message to its chosen neighbor. The other half of the sum and weight it keeps for itself.

### Convergence Measure
To measure the convergence time:

* **Gossip**: The time taken for the message to reach all of the nodes in the network topology being considered.

* **Push-Sum**: The time taken for the average of all the nodes in the network topology being considered to converge within a specified delta (10^-10 in our case) for 3 consecutive rounds.


# Largest Network to deal with for each type of topology and algorithm
| Algorithm  | Topology | Network Size | Convergence Time (ms) |
| ---------- | -------- | ------------ | --------------------- |
| Gossip | Full | 1,000,000 | 21499 |
| Gossip | Line | 1,000,000 |  |
| Gossip | 3D | 1,000,000 | 33982 |
| Gossip | Imperfect 3D | 1,000,000 | 43076 |
| Push-Sum | Full | 1,000,000 | 144787 |
| Push-Sum | Line |  |
| Push-Sum | 3D | 750,000 |
| Push-Sum | Imperfect 3D | 1,000,000 | 566623 |

The program was run on an 8 core machine.