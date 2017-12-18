package ar.bh

import java.io.{BufferedReader, File, FileNotFoundException, InputStream, InputStreamReader}
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.{Base64, Properties}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}

//import ar.BH.TweetsGenerator.{props, topic}
import org.apache.kafka.clients.producer.KafkaProducer

import scala.collection.JavaConverters._

class TwitterStream(
                     propsKafka: Properties,
                     propsAuth: Properties,
                     path: String,
                     kafkaTopic: String,
                     savingInterval: Long,
                     filtersTrack: Array[String],
                     filtersLocations: String) {

  private val threadName = "tweet-downloader"
  val spark = SparkSession.builder.appName("Tweets:ETL").getOrCreate()
  val producer = new KafkaProducer[String, String](propsKafka)

  {
    // Throw an exception if there is already an active stream.
    // We do this check at here to prevent users from overriding the existing
    // TwitterStream and losing the reference of the active stream.
    val hasActiveStream = Thread.getAllStackTraces().keySet().asScala.map(_.getName).contains(threadName)
    if (hasActiveStream) {
      throw new RuntimeException(
        "There is already an active stream that writes tweets to the configured path. " +
          "Please stop the existing stream first (using twitterStream.stop()).")
    }
  }

  @volatile private var thread: Thread = null
  @volatile private var isStopped = false
  @volatile var isDownloading = false
  @volatile var exception: Throwable = null

  private var httpclient: CloseableHttpClient = null
  private var input: InputStream = null
  private var httpGet: HttpGet = null

  private def encode(string: String): String = {
    URLEncoder.encode(string, StandardCharsets.UTF_8.name)
  }

  def start(): Unit = synchronized {


    isDownloading = false
    isStopped = false
    thread = new Thread(threadName) {
      override def run(): Unit = {
        httpclient = HttpClients.createDefault()
        try {
          requestStream(httpclient)
        } catch {
          case e: Throwable => exception = e
        } finally {
          TwitterStream.this.stop()
        }
      }
    }
    thread.start()
  }

  private def requestStream(httpclient: CloseableHttpClient): Unit = {
    val url = "https://stream.twitter.com/1.1/statuses/filter.json"
    val timestamp = System.currentTimeMillis / 1000
    val nonce = timestamp + scala.util.Random.nextInt
    val oauthNonce = nonce.toString
    val oauthTimestamp = timestamp.toString

    val oauthHeaderParams = List(
      "oauth_consumer_key" -> encode(propsAuth.getProperty("consumerKey")),
      "oauth_signature_method" -> encode("HMAC-SHA1"),
      "oauth_timestamp" -> encode(oauthTimestamp),
      "oauth_nonce" -> encode(oauthNonce),
      "oauth_token" -> encode(propsAuth.getProperty("accessToken")),
      "oauth_version" -> "1.0"
    )
    // Parameters used by requests
    // See https://dev.twitter.com/streaming/overview/request-parameters for a complete list of available parameters.
    val requestParams1 = List("track" -> encode(filtersTrack.mkString(",")))
    val requestParams2 = List("locations" -> encode(filtersLocations))

    val parameters = (oauthHeaderParams ++ requestParams1 ++ requestParams2).sortBy(_._1).map(pair => s"""${pair._1}=${pair._2}""").mkString("&")
    println(parameters)
    val base = s"GET&${encode(url)}&${encode(parameters)}"
    val oauthBaseString: String = base.toString
    val signature = generateSignature(oauthBaseString)
    val oauthFinalHeaderParams = oauthHeaderParams ::: List("oauth_signature" -> encode(signature))
    val authHeader = "OAuth " + ((oauthFinalHeaderParams.sortBy(_._1).map(pair => s"""${pair._1}="${pair._2}"""")).mkString(", "))

    httpGet = new HttpGet(s"https://stream.twitter.com/1.1/statuses/filter.json?${(requestParams1 ++ requestParams2).map(pair => s"""${pair._1}=${pair._2}""").mkString("&")}")
    httpGet.addHeader("Authorization", authHeader)
    println("Downloading tweets!")
    val response = httpclient.execute(httpGet)
    val entity = response.getEntity()
    input = entity.getContent()
    //println(entity)
    //println(input)
    if (response.getStatusLine.getStatusCode != 200) {
      throw new RuntimeException(IOUtils.toString(input, StandardCharsets.UTF_8))
    }
    isDownloading = true
    val reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
    //def foo: String = //= Stream.continually(reader.readLine()).takeWhile(_ != null)

    var line: String = null
    var lineno = 1
    line = reader.readLine()

    def foo: String = line

    println(foo)
    var lastSavingTime = System.currentTimeMillis()
    val s = new StringBuilder()
    while (line != null && !isStopped) {
      lineno += 1
      line = reader.readLine()
      s.append(line + "\n")
      //display(s)
      val now = System.currentTimeMillis()
      if (now - lastSavingTime >= savingInterval) {
        val file = new File(path, now.toString).getAbsolutePath
        println("saving to " + file)
        //dbutils.fs.put(file, s.toString, true)
        sendToKafka.process(spark, s.toString())
        lastSavingTime = now
        s.clear()
      }
    }
  }

  private def generateSignature(data: String): String = {
    val mac = Mac.getInstance("HmacSHA1")
    val oauthSignature = encode(propsAuth.getProperty("consumerSecret")) + "&" + encode(propsAuth.getProperty("accessTokenSecret"))
    val spec = new SecretKeySpec(oauthSignature.getBytes, "HmacSHA1")
    mac.init(spec)
    val byteHMAC = mac.doFinal(data.getBytes)
    return Base64.getEncoder.encodeToString(byteHMAC)
  }

  def stop(): Unit = synchronized {
    isStopped = true
    isDownloading = false
    try {
      if (httpGet != null) {
        httpGet.abort()
        httpGet = null
      }
      if (input != null) {
        input.close()
        input = null
      }
      if (httpclient != null) {
        httpclient.close()
        httpclient = null
      }
      if (thread != null) {
        thread.interrupt()
        thread = null
      }
    } catch {
      case _: Throwable =>
    }
  }



  object sendToKafka {

    def process(spark: SparkSession, tweet: String): Unit = {

      val data = new ProducerRecord[String, String](kafkaTopic, null, tweet)
      producer.send(data)
      producer.close()

    }
  }

}
