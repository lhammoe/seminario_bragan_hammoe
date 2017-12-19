package ar.bh

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.{OutputMode, ProcessingTime}
import org.apache.spark.sql.types._


object TwitterStreamingETL extends App {
  var argumentsSize = 3;
  if (args.length < argumentsSize) {
    System.err.println(
      s"""
         |Usage: TwitterStreamingETL <brokers> <topics>
         |  <brokers> is a list of one or more Kafka brokers
         |  <topics> is a list of one or more kafka topics to consume from
         |  <path> path to save what it is in kafka
         |  TwitterStreamingETL kafka:9092 tweets /dataset/twitter
        """.stripMargin)
    System.exit(1)
  }

  val Array(brokers, topics, path) = args
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

  jsons.printSchema

  val schema = StructType(Seq(
    StructField("contributors", StringType, nullable = true)
    ,StructField("created_at", TimestampType, nullable = true)
    ,StructField("entities", StructType(Array(
                                          StructField("hashtags",StructType(Array(StructField("element",StructType(
                                            Array(StructField("text",StringType,nullable =true))
                                          ),nullable = true)))))),nullable=true)
    ,StructField("geo", StructType(Array(
      StructField("coordinates",StructType(
        Array(
          StructField("element", DoubleType, nullable = true)
        )),nullable = true)
      ,StructField("type", StringType,nullable = true)
      ))
    )
    ,StructField("id", LongType, nullable = true)
    ,StructField("id_str", StringType, nullable = true)
    ,StructField("in_reply_to_screen_name", StringType, nullable = true)
    ,StructField("in_reply_to_user_id", LongType, nullable = true)
    ,StructField("in_reply_to_user_id_str", StringType, nullable = true)
    ,StructField("lang", StringType, nullable = true)
    ,StructField("place", StructType(
      Seq(
        StructField("country",StringType,nullable = true)
        ,StructField("country_code",StringType, nullable = true)
        ,StructField("full_name",StringType, nullable = true)
        ,StructField("id",StringType, nullable = true)
        ,StructField("name",StringType, nullable = true)
        ,StructField("place_type",StringType, nullable = true)
        ,StructField("url",StringType, nullable = true)
      )
    ), nullable = true)
    ,StructField("reply_count", LongType, nullable = true)
    ,StructField("retweet_count", LongType, nullable = true)
    ,StructField("retweeted", BooleanType, nullable = true)
    ,StructField("source", StringType, nullable = true)
    ,StructField("text", StringType, nullable = true)
    ,StructField("user", StructType(
      Seq(
        StructField("created_at", TimestampType, nullable = true)
        ,StructField("description",StringType, nullable = true)
        ,StructField("followers_count", LongType, nullable = true)
        ,StructField("friends_count", LongType, nullable = true)
        ,StructField("geo_enabled", BooleanType, nullable = true)
        ,StructField("id",LongType, nullable = true)
        ,StructField("lang",StringType, nullable = true)
        ,StructField("location",StringType, nullable = true)
        ,StructField("name",StringType, nullable = true)
        ,StructField("screen_name",StringType, nullable = true)
        ,StructField("time_zone",StringType, nullable = true)
        ,StructField("utc_offset",LongType, nullable = true)
      )
    ), nullable = true)

  ))

  import org.apache.spark.sql.functions._
  import spark.implicits._

  jsons.select($"value").printSchema()

  val jsonOptions = Map("timestampFormat" -> "EEE MMM dd HH:mm:ss Z yyyy")
  val tweetsJson = jsons.
    select(from_json($"value".cast("string"),schema,jsonOptions).as("values"))

  tweetsJson.printSchema

  val tweets = tweetsJson.select($"values.*")

  tweets.printSchema

  // Write to Parquet
  tweets.
    withColumn("year", year($"created_at")).
    withColumn("month", month($"created_at")).
    withColumn("day", dayofmonth($"created_at")).
    withColumn("hour", hour($"created_at")).
    withColumn("minute", minute($"created_at")).
    withColumn("text", $"text").
    writeStream.
    format("parquet").
    partitionBy("year", "month", "day", "hour", "minute").
    option("startingOffsets", "earliest").
    option("checkpointLocation", "/dataset/checkpoint").
    option("path", path).
    trigger(ProcessingTime("30 seconds")).
    start()

  //Thread.sleep(300000)

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
  Thread.sleep(3000)
}
