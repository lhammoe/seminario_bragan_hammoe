package ar.bh

import java.time.ZonedDateTime
import java.util.Properties

import org.apache.spark.sql.SparkSession

object TweetsGenerator extends App {
  val rnd = new scala.util.Random(42)
  // This is when Dataset ends
  var tradingBeginOfTime = ZonedDateTime.parse("2017-11-11T10:00:00Z")

  var argumentsSize = 4;

  if (args.length < argumentsSize) {
    System.err.println(
      s"""
         |Usage: TweetsGenerator <brokers> <topics>
         |  <brokers> is a list of one or more Kafka brokers
         |  <topic> one kafka topic to produce to
         |  <outputPath> path to save tweets
         |  <savingInterval> seconds to define creation file interval
         |  <filtersTrack> words to filter tweets, separated by commas.
         |  <filtersLocations> geo references: longitud,latitud. 2 points that represent a rectangule of the cover area.
         |
         |
         |  TweetsGenerator kafka:9092 stocks
        """.stripMargin)
    System.exit(1)
  }

  val Array(brokers, topic, outputPath, savingInterval, filtersTrackArg, filtersLocations) = args

  println(
    s"""
       |Consuming tweets $brokers/$topic
    """.stripMargin)

  val spark = SparkSession.builder.appName("Tweets:ETL").getOrCreate()

  val props = new Properties()
  props.put("bootstrap.servers", brokers)
  props.put("client.id", "TweetsGenerator")
  props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

  val propsAuth = new Properties()
  propsAuth.put("consumerKey", "s6wI5gsn4qV3OnlKFD0HEYMXF")
  propsAuth.put("consumerSecret", "zkngW66O6ojT5P5Q2xqGVvqltl8qwqq9vCCZLt5XLxzkbxPQlK")
  propsAuth.put("accessToken", "859902580994052096-lSzW6HMF0KYH3bVWiVm0EjeImoSSKlj")
  propsAuth.put("accessTokenSecret", "NdXq3sR05mm05QoMhRZOe6Z3cc8O2o4vcH0z4KRqNhVkF")

  val savingIntervalNumber = savingInterval.toLong
  val filtersTrack = Array(filtersTrackArg)

  val twitterStream = new TwitterStream(props, propsAuth, outputPath, savingIntervalNumber, filtersTrack,filtersLocations)

  twitterStream.start()
  try{
    while (!twitterStream.isDownloading && twitterStream.exception == null) {
      Thread.sleep(100)
    }
    if (twitterStream.exception != null) {
      throw twitterStream.exception
    }
  }catch{
    case e: Exception => twitterStream.stop()
  }

  Thread.sleep(10000)

  twitterStream.stop()

  spark.stop()
}