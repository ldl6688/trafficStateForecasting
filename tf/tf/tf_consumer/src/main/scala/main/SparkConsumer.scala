package main

import java.text.SimpleDateFormat
import java.util.Calendar

import com.alibaba.fastjson.{JSON, TypeReference}
import kafka.serializer.StringDecoder
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import utils.{PropertyUtil, RedisUtil}

object SparkConsumer {
  def main(args: Array[String]): Unit = {
    //初始化Spark
    val sparkConf = new SparkConf().setMaster("local[2]").setAppName("TrafficStreaming")
    val sc = new SparkContext(sparkConf)
    val ssc = new StreamingContext(sc, Seconds(5))
    ssc.checkpoint("./ssc/checkpoint")

    //配置kafka参数
    val kafkaParams = Map("metadata.broker.list" -> PropertyUtil.getProperty("metadata.broker.list"))

    //配置消费主题
    val topics = Set(PropertyUtil.getProperty("kafka.topics"))

    //读取kafka中的value数据
    val kafkaLineDStream = KafkaUtils.createDirectStream[
      String,
      String,
      StringDecoder,
      StringDecoder](ssc, kafkaParams, topics)
      .map(_._2)

    val event = kafkaLineDStream.map(line => {
      //JSon解析
      val lineJavaMap = JSON.parseObject(line, new TypeReference[java.util.Map[String, String]]() {})

      import scala.collection.JavaConverters._
      //将JavaMap转为ScalaMap
      val lineScalaMap: collection.mutable.Map[String, String] = mapAsScalaMapConverter(lineJavaMap).asScala
      println(lineScalaMap)
      lineScalaMap
    })

    //将每一条数据进行简单的聚合，然后存放到redis中
    //目标：(0001, (3000, 100))
    val sumOfSpeedAndCount = event
      .map(e => (e.get("monitor_id").get, e.get("speed").get)) //(0001, 050)
      .mapValues(v => (v.toInt, 1)) //(0001, 050) --->   (0001, (050, 1)),  (0001, (035, 1))
      .reduceByKeyAndWindow((t1: (Int, Int), t2: (Int, Int)) => (t1._1 + t2._1, t1._2 + t2._2), Seconds(60), Seconds(60))//(0001, (3000, 100))

    //选择数据库索引
    val dbIndex = 1

    sumOfSpeedAndCount.foreachRDD(rdd => {
      rdd.foreachPartition(partitionRecord => {
        partitionRecord
          .filter((tuple: (String, (Int, Int))) => tuple._2._2 > 0)
          .foreach(pair => {
            val jedis = RedisUtil.pool.getResource
            val monitorId = pair._1
            val sumOfSpeed = pair._2._1
            val sumOfCarCount = pair._2._2

            //将数据实时保存到redis中
            val currentTime = Calendar.getInstance().getTime
            val hmSDF = new SimpleDateFormat("HHmm")
            val dateSDF = new SimpleDateFormat("yyyyMMdd")

            val hourMinuteTime = hmSDF.format(currentTime)//1453
            val date = dateSDF.format(currentTime)//20190115

            jedis.select(dbIndex)
            jedis.hset(date + "_" + monitorId, hourMinuteTime, sumOfSpeed + "_" + sumOfCarCount)
            RedisUtil.pool.returnResource(jedis)
          })
      })
    })
    ssc.start
    ssc.awaitTermination
  }
}
