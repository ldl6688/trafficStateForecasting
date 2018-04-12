package main


import com.alibaba.fastjson.{JSON, TypeReference}
import kafka.serializer.StringDecoder
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.{DateTime}
import utils.{PropertyUtil, RedisUtil}

/**
  * @ Autheor:ldl
  *
  */

object SparkConsumer {
  def main(args: Array[String]): Unit = {

    //初始化spark
    val sparkConf = new SparkConf().setMaster("local[*]").setAppName("trafficStreaming")
    val sc = new SparkContext(sparkConf)
    val ssc = new StreamingContext(sc, Seconds(5))
    ssc.checkpoint("./ssc/checkpoint")

    //配置kafka参数
    val kafkaParams = Map("metadata.broker.list" ->
      PropertyUtil.getProperty("metadata.broker.list"))

    //配置kafka主题
    val topics = Set(PropertyUtil.getProperty("kafka.topics"))

    //读取kafka主题中的每一条数据
    val kafkaLineDStream = KafkaUtils.createDirectStream[
      String,
      String,
      StringDecoder,
      StringDecoder](
      ssc, kafkaParams, topics)
      .map(_._2)

    val event = kafkaLineDStream.map(line => {
      //解析json事件到HashMap中
      val lineJavaMap = JSON.parseObject(line,
        new TypeReference[java.util.Map[String, String]]() {})

      //将java的HashMap转化为scala的HashMap
      import scala.collection.JavaConverters._
      val lineScalaMap: collection.mutable.Map[String, String] =
        mapAsScalaMapConverter(lineJavaMap).asScala
      lineScalaMap
    })

    //将每一条数据按照monitor_id聚合,"车辆速度"叠加,"车辆数"叠加
    val sumOfSpeedAndCount = event
      .map(e => (e.get("monitor_id").get, e.get("speed").get))
      .mapValues(s => (s.toInt, 1))
      .reduceByKeyAndWindow(
        (t1: (Int, Int), t2: (Int, Int)) => (t1._1 + t2._1, t1._2 + t2._2),
        Seconds(20),
        Seconds(10))

    //数据库的索引号为1
    val dbIndex = 1

    //将采集到的数据按照每分钟存储到redis中,用于后期的建模与分析
    sumOfSpeedAndCount.foreachRDD(rdd => {
      rdd.foreachPartition(partitionRecords => {
        partitionRecords.filter((tuple: (String, (Int, Int))) => tuple._2._2 > 0)
          .foreach(pair => {
            val jedis = RedisUtil.pool.getResource
            val monitor_id = pair._1
            val sumOfSpeed = pair._2._1
            val sumOfCarCount = pair._2._2


            // val currentTime = new DateTime()
            val currentTime = DateTime.now()
            val hourMinuteTime = currentTime.toString("HHmm")
            val date = currentTime.toString("yyyyMMdd")

            //存入redis数据库
            jedis.select(dbIndex)
            jedis.hset(date + "_" + monitor_id,hourMinuteTime,sumOfSpeed + "_" + sumOfCarCount)
            println(date + "_" + monitor_id)
              RedisUtil.pool.returnResource(jedis)
          })
      })
    })

    ssc.start()
    ssc.awaitTermination()
  }

}
