package main

import java.text.SimpleDateFormat
import java.util.Date

import org.apache.spark.mllib.classification.LogisticRegressionModel
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.{SparkConf, SparkContext}
import utils.RedisUtil

import scala.collection.mutable.ArrayBuffer

/**
  * 数据预测
  */
object Prediction {
  def main(args: Array[String]): Unit = {
    //配置spark
    val sparkConf = new SparkConf().setMaster("local[2]").setAppName("PredictionApp")
    val sc = new SparkContext(sparkConf)

    //拿到想要预测的监测点
    val dateSDF = new SimpleDateFormat("yyyyMMdd")
    val hmSDF = new SimpleDateFormat("HHmm")
    val userSDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    //用户传递的时间
    val inputDateString = "2018-04-13 13:30:00"
    val inputDate = userSDF.parse(inputDateString)

    //"2018-04-13 13:59:30"  --->   20180413
    val dayOfInputDate = dateSDF.format(inputDate)
    //"2018-04-13 13:59:30"  --->  1359
    val hourMinuteOfInputDate = hmSDF.format(inputDate)

    val monitorIDs = List("0005", "0015")
    val monitorRelations = Map[String, Array[String]](
      "0005" -> Array("0003", "0004", "0005", "0006", "0007"),
      "0015" -> Array("0013", "0014", "0015", "0016", "0017"))

    //取得历史数据
    val dbIndex = 1
    val jedis = RedisUtil.pool.getResource

    jedis.select(dbIndex)

    monitorIDs.map(monitorID => {
      //"0003", "0004", "0005", "0006", "0007"
      val monitorRelationList = monitorRelations.get(monitorID).get
      //[(0005, {1305=3000_100, 1306=500_10}), (0006, {1305=3000_100, 1306=500_10})]
      val relationsInfo = monitorRelationList.map(monitorID => {
        (monitorID, jedis.hgetAll(dayOfInputDate + "_" + monitorID))
      })

      //准备历史数据
      val dataX = ArrayBuffer[Double]()
      for(index <- 0 to 2){
        val oneMoment = inputDate.getTime - 60 * index * 1000
        val oneHM = hmSDF.format(new Date(oneMoment))
        for((k, v) <- relationsInfo){
          if(v.containsKey(oneHM)){
            val speedAndCarCount = v.get(oneHM).split("_")
            val valueX = speedAndCarCount(0).toFloat / speedAndCarCount(1).toFloat
            dataX += valueX
          }else{
            dataX += 60.0F
          }
        }
      }
      val historyVector = Vectors.dense(dataX.toArray)
      println(historyVector)
      //加载模型
      val modelHDFSPath = jedis.hget("model", monitorID)
      val model = LogisticRegressionModel.load(sc, modelHDFSPath)

      //预测
      val prediction = model.predict(historyVector)
      println(monitorID + "_" + "堵车评估值：" + prediction + "通畅级别：" + (if(prediction > 3) "通畅" else "拥堵"))

      //保存结果
      jedis.hset(inputDateString, monitorID, prediction.toString)
    })

    RedisUtil.pool.returnResource(jedis)

  }
}
