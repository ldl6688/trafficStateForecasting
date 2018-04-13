package main

import java.io.{File, PrintWriter}
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import org.apache.spark.mllib.classification.LogisticRegressionWithLBFGS
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.{SparkConf, SparkContext}
import utils.RedisUtil

import scala.collection.mutable.ArrayBuffer

object Train {
  def main(args: Array[String]): Unit = {
    //创建输出流，保存中间结果
    val writer = new PrintWriter(new File("model_training.log"))

    //配置spark
    val sparkConf = new SparkConf().setMaster("local[2]").setAppName("TrafficModelingApp")
    val sc = new SparkContext(sparkConf)

    //获取redis中的数据
    val dbIndex = 1
    val jedis = RedisUtil.pool.getResource
    jedis.select(dbIndex)

    //确定“目标监测点”以及“目标监测点的相关监测点”
    val monitorIDs = List("0005", "0015")
    val monitorRelations = Map[String, Array[String]](
      "0005" -> Array("0003", "0004", "0005", "0006", "0007"),
      "0015" -> Array("0013", "0014", "0015", "0016", "0017"))

    //key:20190115_0003
    //fields: 0938
    //value:3000_100
    monitorIDs.map(monitorID => {//"0005", "0015"
      //Array("0003", "0004", "0005", "0006", "0007")
      val monitorRelationList = monitorRelations.get(monitorID).get
      //设置时间相关
      //Java Date对象
      val currentDate = Calendar.getInstance().getTime
      //设置当前小时和分钟数
      val hmSDF = new SimpleDateFormat("HHmm")
      //设置天的格式化
      val dateSDF = new SimpleDateFormat("yyyyMMdd")
      //20190115
      val dateOfString = dateSDF.format(currentDate)

      //根据相关监测点的数据，取得当日特征向量值，以及结果向量值
//      [("0003", ("0946" -> "3000_100", "0947" -> "500_10")),
//      ("0004", ("0946" -> "3000_100", "0947" -> "500_10")),
//      ("0005", ("0946" -> "3000_100", "0947" -> "500_10"))]
      val relationsInfo = monitorRelationList.map(monitorID => {
        (monitorID, jedis.hgetAll(dateOfString + "_" + monitorID))
      })

      //将时间回滚到历史时间
      //需要反复探讨和调改的
      val hours = 1.5
      //存放特征向量和结果向量的封装对象
      val dataTrain = ArrayBuffer[LabeledPoint]()
      //存放特征向量
      val dataX = ArrayBuffer[Double]()
      //存放结果向量
      val dataY = ArrayBuffer[Double]()

      //将时间拉回到n个小时之前
      for(i <- Range((60 * hours).toInt, 2, -1)){
        //for循环每执行1次，就是一组dataX和dataY的封装（LabeledPoint）
        dataX.clear()
        dataY.clear()
        for(index <- 0 to 2){
          val oneMomnet = currentDate.getTime - 60 * i * 1000 + 60 * index * 1000
          //HHmm,1011
          val oneHM = hmSDF.format(new Date(oneMomnet))
          //取出相关监测点的对应时间节点上的数据
          //      [("0003", ("0946" -> "3000_100", "0947" -> "500_10")),
          //      ("0004", ("0946" -> "3000_100", "0947" -> "500_10")),
          //      ("0005", ("0946" -> "3000_100", "0947" -> "500_10"))]
          for((k, v) <- relationsInfo){
            //组装结果向量Label，dataY
            if(k == monitorID && index == 2){
              val nextMoment = oneMomnet + 60 * 1 * 1000
              val nextHM = hmSDF.format(new Date(nextMoment))//0921
              //判断redis取出来的数据中，对应这个时间点的key有没有数据
              if(v.containsKey(nextHM)){
                val speedAndCarCount = v.get(nextHM).split("_")
                val valueY = speedAndCarCount(0).toFloat / speedAndCarCount(1).toFloat
                dataY += valueY
              }
            }

            //拼装特征向量，取15个特征值
            if(v.containsKey(oneHM)){
              val speedAndCarCount = v.get(oneHM).split("_")
              val valueX = speedAndCarCount(0).toFloat / speedAndCarCount(1).toFloat
              dataX += valueX
            }else{
              //该时间点，该道路上没有车，则说明通畅，默认值：60km/h
              //需要反复探讨和调改的
              dataX += 60.0F
            }
          }
        }
        //必须保证label是有数据的，且只有1个
        if(dataY.toArray.length == 1){
          val label = dataY.toArray.head
//          0~6 7,8,9  ---> 10
          //1
          val record = LabeledPoint(if(label.toInt / 10 < 10) label.toInt / 10 else 10, Vectors.dense(dataX.toArray))
          dataTrain += record
        }
      }

      //准备训练模型
      dataTrain.foreach(e => {
        writer.write(e.toString() + "\r\n")
        println(e)
      })
      //将数据矩阵转换成一个RDD集合
      val rddData = sc.parallelize(dataTrain)
      val randomSplits = rddData.randomSplit(Array(0.8, 0.2), 11L)
      //训练集
      val trainData = randomSplits(0)
      //测试集
      val testData = randomSplits(1)

      //开始训练
      val model = new LogisticRegressionWithLBFGS().setNumClasses(11).run(trainData)
      //使用测试集对模型进行评估
      val predictionAndLabels = testData.map{
        case LabeledPoint(label, features) =>
          val prediction = model.predict(features)
          (prediction, label)
      }

      //得到当前评估值
      val metrics = new MulticlassMetrics(predictionAndLabels)
      val accuracy = metrics.accuracy
      println(accuracy)
      //将评估结果保存到本地文件中
      writer.write(accuracy.toString + "\r\n")
      //保存模型
      if(accuracy >= 0.0){
        val hdfsPath = "hdfs://linux01:8020/traffic/model/" +
          monitorID +
          "_" +
          new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime)
        model.save(sc, hdfsPath)
        //key:model
        //fields:0005
        //value:hdfsPath
        jedis.hset("model", monitorID, hdfsPath)
      }
    })

    RedisUtil.pool.returnResource(jedis)
    writer.flush
    writer.close
  }
}
