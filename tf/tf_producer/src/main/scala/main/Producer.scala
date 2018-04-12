package main

import java.text.DecimalFormat
import java.util

import com.alibaba.fastjson.JSON
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import utils.PropertyUtil
import org.joda.time.DateTimeUtils

import scala.util.Random

/**
  * @ Autheor:ldl
  *
  */

object Producer {
  def main(args: Array[String]): Unit = {
    //获取kafka的配置信息
    val kafkaProperties = PropertyUtil.properties

    //创建kafka生产者对象
    val producer = new KafkaProducer[String, String](kafkaProperties)

    //模拟生产实时数据
    var startTime = DateTimeUtils.currentTimeMillis() / 1000

    //数据模拟(堵车)切换周期 单位:秒
    val trafficCycle = 10
    //循环产生模拟数据
    while (true) {
      //模拟产生监测点id
      val randomMonitorId = new DecimalFormat("0000").format(Random.nextInt(20) + 1)
      var randomSpeed = "000"
      val currentTime = DateTimeUtils.currentTimeMillis() / 1000

      if (currentTime - startTime > trafficCycle) {
        randomSpeed = new DecimalFormat("000").format(Random.nextInt(16))
        if (currentTime - startTime > trafficCycle * 2) {
          startTime = currentTime
        }
      } else {
        randomSpeed = new DecimalFormat("000").format(Random.nextInt(31) + 30)
      }

      println(randomMonitorId + "..." + randomSpeed)
      //将数据序列化为JSON:K-V结构
      val jsonMap = new util.HashMap[String, String]()
      jsonMap.put("monitor_id", randomMonitorId)
      jsonMap.put("speed", randomSpeed)

      //将每一条数据序列化为一个json事件
      val event = JSON.toJSON(jsonMap)
      println(event)

      //将封装好的数据发送给kafka
      //      val producerRecord = new ProducerRecord[String,String](kafkaProperties.getProperty("kafka.topics"))
      //      producer.send(producerRecord,event.toString)
      producer.send(new ProducerRecord[String, String](kafkaProperties.getProperty("kafka.topics"), event.toString))
      Thread.sleep(200)
    }
  }
}
