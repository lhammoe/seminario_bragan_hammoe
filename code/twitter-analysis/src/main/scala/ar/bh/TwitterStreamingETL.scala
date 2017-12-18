package ar.bh

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.{OutputMode, ProcessingTime}
import org.apache.spark.sql.types._


object TwitterStreamingETL extends App {
  var argumentsSize = 2;
  if (args.length < argumentsSize) {
    System.err.println(
      s"""
         |Usage: TwitterStreamingETL <brokers> <topics>
         |  <brokers> is a list of one or more Kafka brokers
         |  <topics> is a list of one or more kafka topics to consume from
         |  TweetsGenerator kafka:9092 tweets
        """.stripMargin)
    System.exit(1)
  }

  val Array(brokers, topics) = args
  val spark = SparkSession.
    builder.
    appName("Twitter:StreamingETL").
    getOrCreate()

  // Create DataSet representing the stream of input lines from kafka
  //  https://databricks.com/blog/2017/04/26/processing-data-in-apache-kafka-with-structured-streaming-in-apache-spark-2-2.html
  val jsons = spark.
    readStream.
    format("kafka").
    option("kafka.bootstrap.servers", brokers).
    option("subscribe", topics).
    load()
  //    option("startingOffsets", "earliest").

  jsons.
  jsons.printSchema

  val schema = StructType(Seq(
    StructField("text", StringType, nullable = false),
    StructField("created_at", TimestampType, nullable = false),
    StructField("user", StringType, nullable = false)
  ))

  import org.apache.spark.sql.functions._
  import spark.implicits._

  //val jsonOptions = Map("timestampFormat" -> "yyyy-MM-dd'T'HH:mm'Z'")
  val tweetsJson = jsons.
    select(from_json($"value".cast("string"), schema).as("values"))

  tweetsJson.printSchema

  val tweets = tweetsJson.select($"values.*")

  tweets.printSchema

  // Write to Parquet
  tweets.
    /*withColumn("year", year($"created_at")).
    withColumn("month", month($"created_at")).
    withColumn("day", dayofmonth($"created_at")).
    withColumn("hour", hour($"created_at")).
    withColumn("minute", minute($"created_at")).*/
    withColumn("texto", $"text").
    writeStream.
    format("parquet").
    //partitionBy("year", "month", "day", "hour", "minute").
    option("startingOffsets", "earliest").
    option("checkpointLocation", "/dataset/checkpoint").
    option("path", "/dataset/twitterStreaming.parquet").
    trigger(ProcessingTime("30 seconds")).
    start()

  // There is no JDBC sink for now!
  // https://databricks.com/blog/2017/04/04/real-time-end-to-end-integration-with-apache-kafka-in-apache-sparks-structured-streaming.html
  //

  // Using as an ordinary DF
  /*val avgPricing = stocks.
    groupBy($"symbol").
    agg(avg($"price").as("avg_price"))

  // Start running the query that prints the running results to the console
  val query = avgPricing.writeStream.
    outputMode(OutputMode.Complete).
    format("console").
    trigger(ProcessingTime("10 seconds")).
    start()*/

  // Have all the aggregates in an in-memory table
  //  avgPricing
  //    .writeStream
  //    .queryName("avgPricing")    // this query name will be the table name
  //    .outputMode("complete")
  //    .format("memory")
  //    .start()
  //
  //  spark.sql("select * from avgPricing").show()   // interactively query in-memory table

  /*query.awaitTermination()*/
}
