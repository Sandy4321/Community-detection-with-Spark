import org.apache.log4j.{Level, Logger}
import org.apache.spark.graphx._
import org.apache.spark.mllib.clustering.LDA
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.{SparkConf, SparkContext}
import utils._

import scala.collection.mutable.ArrayBuffer
import scala.math._

// To make some of the examples work we will also need RDD

import org.apache.spark.rdd.RDD

// Useful links
// http://ampcamp.berkeley.edu/big-data-mini-course/graph-analytics-with-graphx.html
// https://spark.apache.org/docs/latest/graphx-programming-guide.html

object GraphxTesting {

    val RED = "\033[1;30m"
    val ENDC = "\033[0m"

    def main(args: Array[String]) {

        println("\n\n**************************************************************")
        println("******************       GraphxTesting      ******************")
        println("**************************************************************\n")

        val cu = new CassandraUtils
        val comUtils = new CommunityUtils
        val gu = new GraphUtils
        val ru = new RDDUtils

        // Display only warning and infos messages
        Logger.getLogger("org").setLevel(Level.ERROR)
        Logger.getLogger("akka").setLevel(Level.ERROR)

        // Not displaying infos messages
        //Logger.getLogger("org").setLevel(Level.OFF)
        //Logger.getLogger("akka").setLevel(Level.OFF)

        // Spark configuration
        val sparkConf = new SparkConf(true)
            .setMaster("local[2]")
            .setAppName("GraphxTesting")
            .set("spark.cassandra.connection.host", "127.0.0.1") // Link to Cassandra

        // Init SparkContext
        val sc = new SparkContext(sparkConf)

        // Create Vertices and Edges
        val (users, relationships, defaultUser) = initGraph(sc)

        // Build the initial Graph
        val graph = Graph(users, relationships, defaultUser).cache()

        /*

        println("\n**************************************************************")
        println("                       TEST METHODS                           ")
        println("**************************************************************")

        println("\n--------------------------------------------------------------")
        println("Operations on tweets")
        println("--------------------------------------------------------------\n")

        // See who communicates with who
        time { ru displayAllCommunications(graph) }

        // Let's find user id
        val id = time { ru findUserIDByNameInGraph(graph, "Michael") }
        println("ID for user Michael is : " + id.toString)

        // Find username with user ID
        val name = time { ru findUserNameByIDInGraph(graph, 1) }
        println("Name for id 1 is : " + name.toString)

        // get tweet content with tweet ID
        var resultGetTweetContentFromID =  time { cu getTweetContentFromID(sc,"606461329357045760") }
        println(resultGetTweetContentFromID)

        // this one does not exist
        resultGetTweetContentFromID =  time { cu getTweetContentFromID(sc,"604230254979346433") }
        println(resultGetTweetContentFromID)

        // Get tweets from user
        val resultGetTweetsIDFromUser = time { cu getTweetsIDFromUser(sc,"209144549") }
        resultGetTweetsIDFromUser.foreach(println(_))

        // Count in and out degrees
        //time { gu inAndOutDegrees(graph) }


        println("\n--------------------------------------------------------------")
        println("Community detection")
        println("--------------------------------------------------------------\n")

        // Call ConnectedComponents
        time { comUtils cc(graph, users) }

        // Call StronglyConnectedComponents
        time { comUtils scc(graph, 1) }

        // Get triangle Count
        time { comUtils getTriangleCount(graph, users) }

        // Get PageRank
        time { gu getPageRank(graph, users) }

        // K-Core decomposition
        time { comUtils getKCoreGraph(graph, users, 4, true) }

        // LabelPropagation
        val graphLabelPropagation = time { LabelPropagation.run(graph, 4).cache() }

        println("VERTICES")
        graphLabelPropagation.vertices.collect.foreach(println(_))

        val labelVertices = graphLabelPropagation.vertices

        val displayVertices = users.join(labelVertices).map {
            case (id, (username, rank)) => (id, username, rank)
        }
        println("VERTICES NAMED")

        // Print the result descending
        println(displayVertices.collect().sortBy(_._3).reverse.mkString("\n"))
        println("EDGES")

        graphLabelPropagation.edges.collect.foreach(println(_))


        println("\n**************************************************************")
        println("                       FIRST EXAMPLE                          ")
        println("**************************************************************")


        println("\n--------------------------------------------------------------")
        println("First Step - K-Core Decomposition algorithm")
        println("--------------------------------------------------------------")

        // K-Core decomposition
        val graph_2 = time { comUtils getKCoreGraph(graph, users, 5, false) }.cache()

        graph_2.edges.collect.foreach(println(_))
        graph_2.vertices.collect.foreach(println(_))

        println("\n--------------------------------------------------------------")
        println("Second Step - Connected Components algorithm")
        println("--------------------------------------------------------------")

        // Call ConnectedComponents
        time { comUtils cc(graph_2, graph_2.vertices) }

        println("\n--------------------------------------------------------------")
        println("Third Step - Get Tweets from Edges")
        println("--------------------------------------------------------------")

        val corpusWords = time { cu getTweetsContentFromEdge(sc, graph_2.edges, true) }
        corpusWords.foreach(println(_))

        /*println("\n--------------------------------------------------------------")
        println("Fourth Step - LDA Algorithm")
        println("--------------------------------------------------------------")

        val nTopics = 10
        val nIterations = 10
        val nWordsByTopics = 10
        val nStopwords  = 20
        time { mu getLDA(sc, corpusWords, nTopics, nIterations, nWordsByTopics, nStopwords, true) }*/

        */


        println("\n**************************************************************")
        println("                       SECOND EXAMPLE                         ")
        println("**************************************************************")

        println("\n--------------------------------------------------------------")
        println("First Step - Split community : \n" +
            "\t     Connected Components algorithm to find different\n" +
            "\t     communities")
        println("--------------------------------------------------------------")

        //time { comUtils cc(graph, graph.vertices) }

        val subGraphes = time {
            comUtils splitCommunity(graph, users, false)
        }

        println("\n--------------------------------------------------------------")
        println("Second Step - Calculate LDA for every communities\n" +
            "\t 1. Get Tweets from Edges\n" +
            "\t 2. LDA Algorithm")
        println("--------------------------------------------------------------")
        var iComm = 1
        //for (community <- subGraphes){
        println("--------------------------")
        println("Community : " + iComm)
        println("--------------------------")
        //community.edges.collect().foreach(println(_))
        //community.vertices.collect().foreach(println(_))

        println("--------------------------")
        println("Get Tweets from Edges")
        println("--------------------------")
        //val corpus = time { cu getTweetsContentFromEdge(sc, community.edges, false) }

        println("--------------------------")
        println("LDA Algorithm")
        println("--------------------------")
        val numTopics = 5
        val numIterations = 10
        val numWordsByTopics = 5
        val numStopwords = 0

        // Initialize LDA
        println(color("\nCall InitLDA", RED))

        val topicSmoothing = 1.2
        val termSmoothing = 1.2

        // Set LDA parameters
        val lda = new LDA()
            .setOptimizer("online")
            .setK(numTopics)
            .setDocConcentration(topicSmoothing)
            .setTopicConcentration(termSmoothing)
            .setMaxIterations(numIterations)

        // Create documents
        var firstDoc = ArrayBuffer[String]()
        firstDoc += "Concentration parameter commonly named for the prior placed"

        // Init LDA
        val mu = new MllibUtils(lda, sc, firstDoc, firstDoc)

        // First tweet
        mu newTweet ("Concentration distributions topics Concentration")

        // Get documents and word's array
        val (newdoc: RDD[(Long, Vector)], newvocabArray) = time {
            mu createDocuments(sc, 0)
        }

        var ldaModel = lda.run(newdoc)

        // Find topics
        ldaModel = time {
            mu findTopics(ldaModel, newvocabArray, numWordsByTopics, true)
        }

        // Second tweet
        mu newTweet ("October arrived, spreading a damp chill")

        val (newdoc2: RDD[(Long, Vector)], newvocabArray2) = time {
            mu createDocuments(sc, 0)
        }

        ldaModel = lda.run(newdoc2)

        // Find
        ldaModel = time {
            mu findTopics(ldaModel, newvocabArray2, numWordsByTopics, true)
        }


        iComm += 1
        //}

        // Generate Vertices
        val collectionVertices = ArrayBuffer[(Long, String)]()
        collectionVertices += ((2732329846L, "Michael"))
        collectionVertices += ((132988448L, "Jean"))

        // Convert it to RDD
        val VerticesRDD = ru ArrayToVertices(sc, collectionVertices)

        // Generate Hash
        val random = abs(gu murmurHash64A ("MichaelCaraccio".getBytes))

        // Add edges
        val collectionEdge = ArrayBuffer[Edge[String]]()
        collectionEdge += Edge(random, 132988448L, "606460188367974400")
        collectionEdge += Edge(2732329846L, 2941487254L, "606461336986386435")
        collectionEdge += Edge(2732329846L, 601389784L, "606461384767897600")

        // Convert it to RDD
        val EdgeRDD = ru ArrayToEdges(sc, collectionEdge)

        // Create Graph
        val testGraph = Graph(VerticesRDD, EdgeRDD)

        testGraph.vertices.collect.foreach(println(_))
        testGraph.edges.collect.foreach(println(_))
    }

    /**
     * @constructor time
     *
     *              timer for profiling block
     *
     * @param R $block - Block executed
     * @return Unit
     */
    def time[R](block: => R): R = {
        val t0 = System.nanoTime()
        val result = block // call-by-name
        val t1 = System.nanoTime()
        println("Elapsed time: " + (t1 - t0) / 1000000000.0 + " seconds")
        result
    }

    /**
     * @constructor initGraph
     *
     *              init data - construct graph and populate it
     *
     * @param SparkContext $sc - Sparkcontext
     * @return RDD[(VertexId, (String))] - users (Vertices)
     *         RDD[Edge[String]] - relationship (Edges)
     *         String - default user
     */
    def initGraph(sc: SparkContext): (RDD[(VertexId, (String))], RDD[Edge[String]], String) = {
        println(color("\nCall : initGraph", RED))

        // Create an RDD for the vertices
        val users: RDD[(VertexId, (String))] =
            sc.parallelize(Array(
                (2732329846L, "Michael"),
                (132988448L, "David"),
                (473822999L, "Sarah"),
                (2932436311L, "Jean"),
                (2249679902L, "Raphael"),
                (601389784L, "Lucie"),
                (2941487254L, "Harold"),
                (1192483885L, "Pierre"),
                (465776805L, "Christophe"),
                (838147628L, "Zoe"),
                (2564641105L, "Fabien"),
                (1518391292L, "Nicolas")
            ))

        // Create an RDD for edges
        val relationships: RDD[Edge[String]] =
            sc.parallelize(Array(
                Edge(2732329846L, 132988448L, "608919340121870338"),
                Edge(2732329846L, 2941487254L, "608919742347264000"),
                Edge(2732329846L, 601389784L, "608918664549687299"),
                Edge(601389784L, 2732329846L, "608918165117104129"),
                Edge(2941487254L, 1192483885L, "608921008020566016"),
                Edge(2941487254L, 132988448L, "608920341084258304"),
                Edge(132988448L, 838147628L, "608919327694270464"),
                Edge(838147628L, 132988448L, "608919807887552513"),
                Edge(838147628L, 473822999L, "608919870277869568"),
                Edge(465776805L, 2941487254L, "608920678117597184"),
                Edge(465776805L, 601389784L, "608917990365499392"),
                Edge(465776805L, 2249679902L, "608918336643039232"),
                Edge(2249679902L, 465776805L, "608919570796163072"),
                Edge(2932436311L, 465776805L, "608921304377475073"),
                Edge(1192483885L, 2941487254L, "608921260387610624"),
                Edge(465776805L, 2941487254L, "608918707797110784"),
                Edge(601389784L, 2732329846L, "608919779542339584"),
                Edge(2932436311L, 465776805L, "608917272883789824"),
                Edge(2941487254L, 465776805L, "608920374680506368"),
                Edge(2941487254L, 1192483885L, "608920849664450560"),
                Edge(2941487254L, 1192483885L, "608917634822733824"),
                Edge(1192483885L, 2941487254L, "608920742990868480"),
                Edge(1192483885L, 2941487254L, "608921092334354432"),
                Edge(2732329846L, 132988448L, "608917366538424320"),
                Edge(2941487254L, 132988448L, "608920981650976769"),
                Edge(132988448L, 2941487254L, "608920887639855104"),
                Edge(132988448L, 2941487254L, "608916751988867072"),
                Edge(132988448L, 2941487254L, "608919716137033730"),
                Edge(601389784L, 2732329846L, "608921306705354752"),
                Edge(601389784L, 2732329846L, "608918359913164801"),
                Edge(2732329846L, 2941487254L, "608920468985266176"),
                Edge(2732329846L, 2941487254L, "608918157806432257"),
                Edge(2564641105L, 1518391292L, "608918942086799360"),
                Edge(1518391292L, 2564641105L, "608921314104094720")
            ))

        // Define a default user in case there are relationship with missing user
        val defaultUser = "John Doe"

        (users, relationships, defaultUser)
    }

    /**
     * @constructor
     *
     *
     *
     * @param
     * @return
     */
    /*def isVerticeInGraph(): Unit ={

    }*/

    def color(str: String, col: String): String = "%s%s%s".format(col, str, ENDC)
}