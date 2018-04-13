package main

import java.text.DecimalFormat
import java.util
import java.util.Calendar

import com.alibaba.fastjson.JSON
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import utils.PropertyUtil

import scala.util.Random

object Producer {
  def main(args: Array[String]): Unit = {
    //读取kafka配置信息
    val props = PropertyUtil.properties
    //创建生产者
    val produccer = new KafkaProducer[String, String](props)

    //开始模拟生产数据
    //不堵车：格式：0001,30~60
    //堵车：格式：0001,15~30
    //模拟数据时，当前时间,单位：秒
    var startTime = Calendar.getInstance().getTimeInMillis / 1000
    //定义某一个卡口车流状态切换的周期，每过5分钟，切换一次状态,单位：秒
    val trafficCycle = 60 * 15

    while(true){
      //模拟产生卡口（监测点）id：1~20
      val randomMonitorId = new DecimalFormat("0000").format(Random.nextInt(20) + 1)

      //模拟车速
      var randomSpeed = ""
      val currentTime = Calendar.getInstance().getTimeInMillis / 1000
      //每5分钟切换一次
      if(currentTime - startTime > trafficCycle){
        randomSpeed = new DecimalFormat("000").format(Random.nextInt(16))//0~15
        if(currentTime - startTime > trafficCycle * 2){
          randomSpeed = new DecimalFormat("000").format(Random.nextInt(31) + 30)//30~60
          startTime = currentTime
        }
      }else{
        randomSpeed = new DecimalFormat("000").format(Random.nextInt(31) + 30)//30~60
      }

      val jsonMap = new util.HashMap[String, String]()
      jsonMap.put("monitor_id", randomMonitorId)
      jsonMap.put("speed", randomSpeed)

      val event = JSON.toJSON(jsonMap)
      println(event)

      produccer.send(new ProducerRecord[String, String](PropertyUtil.getProperty("kafka.topics"), event.toString))
      Thread.sleep(300)
    }
  }
}
