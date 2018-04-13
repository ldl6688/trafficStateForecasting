package utils

import redis.clients.jedis.{JedisPool, JedisPoolConfig}

object RedisUtil {
  //配置redis连接器
  val host = "192.168.216.20"
  val port = 6379
  val timeout = 30000

  val config = new JedisPoolConfig
  //设置最大连接数，默认8个
  config.setMaxTotal(200)
  //设置最大空闲数
  config.setMaxIdle(50)
  //设置最小空闲数
  config.setMinIdle(0)
  //获取连接时最大等待的毫秒数,如果是-1，则无限等待
  config.setMaxWaitMillis(10000)
  //在获取连接时，是否检查链接有效性，默认为false
  config.setTestOnBorrow(true)
  //回收资源时，是否检查连接有效性
  config.setTestOnReturn(true)
  config.setTestOnCreate(true)

  //连接器在连接池中处于空闲状态下，是否检查有效性
  config.setTestWhileIdle(true)

  //两次扫描之间空闲毫秒数
  config.setTimeBetweenEvictionRunsMillis(30000)

  //每次扫描最多的对象数
  config.setNumTestsPerEvictionRun(10)
  //回收链接时，默认最小时间，默认：30分钟
  config.setMinEvictableIdleTimeMillis(60000)
  //连接池
  lazy val pool = new JedisPool(config, host, port, timeout)

  lazy val hook = new Thread{
    override def run() = {
      pool.destroy()
    }
  }
  sys.addShutdownHook(hook)

}
