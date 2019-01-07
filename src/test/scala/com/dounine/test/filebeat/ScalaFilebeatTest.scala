package com.dounine.test.filebeat

import java.time.{Duration, LocalDateTime}
import java.util.concurrent.TimeUnit

import com.dounine.scala.filebeat.ScalaFilebeat
import com.dounine.scala.filebeat.util.JobUtil
import com.dounine.scala.filebeat.util.JobUtil.AppendLog

object ScalaFilebeatTest {

  def main(args: Array[String]): Unit = {

    //过滤优先级 includeSuffix => ignoreOlder => includePaths => excludePaths
    val workPath = "/Users/huanghuanlai/dounine/github/scala-filebeat/filebeat"

    val job = JobUtil.createJob(
      workPath,
      debug = true,
      compression = true, //压缩seek文件,操作不可逆,一但修改所有文件将当成新文件处理
      logChatset = "utf-8",
      logPaths = Array("/Users/huanghuanlai/dounine/github/scala-filebeat/logdir2"),
      jobName = "test11", //only world
      splitLine = "\n", //换行符号
      recursive = true, //递归子目录
      includeSuffixs = Array("log", "txt"), //日志后缀
      ignoreOlder = "24h", //忽略多久不更新的文件
      intervalFileStatus = "1s", //监听文件内容变动频率
      intervalScanFile = "30s", //扫描目录中匹配条件的频率
      includePaths = Array(".*"), //匹配路径(正则表达式)
      excludePaths = Array(), //排除路径(正则表达式)
      handlerFileClose = "24h", //关闭多久不活跃文件句柄
      logDeques = 2000, //等append日志最大等待队列池(>0有数据丢失风险)
      new AppendLog {

        var count: Int = 0
        var begin: LocalDateTime = LocalDateTime.now()

        @throws[Exception]
        override def append(line: String): Unit = {
          count += 1
          //          println(s"line = ${count}")
        }

        override def complete(filePath: String): Unit = {
          println(filePath + " => " + Duration.between(begin, LocalDateTime.now()).getSeconds)
        }
      }
    )
    //
    //    val job1 = JobUtil.createJob(
    //      workPath,
    //      debug = false,
    //      compression = true, //压缩seek文件,操作不可逆,一但修改所有文件将当成新文件处理
    //      logChatset = "utf-8",
    //      logPaths = Array("/Users/huanghuanlai/dounine/github/scala-filebeat/logdir1"),
    //      jobName = "test1", //only world
    //      splitLine = "\n", //换行符号
    //      recursive = true, //递归子目录
    //      includeSuffixs = Array("log", "txt"), //日志后缀
    //      ignoreOlder = "24h", //忽略多久不更新的文件
    //      intervalFileStatus = "1s", //监听文件内容变动频率
    //      intervalScanFile = "30s", //扫描目录中匹配条件的频率
    //      includePaths = Array(".*"), //匹配
    //      // 路径(正则表达式)
    //      excludePaths = Array(), //排除路径(正则表达式)
    //      handlerFileClose = "24h", //自动关闭多久不活跃文件句柄
    //      logDeques = 2000, //最大等待队列池
    //      new AppendLog {
    //
    //        override def init(): Unit = {
    //          println(s" = job init")
    //        }
    //
    //        override def append(line: String): Unit = {
    //          println(s"line1 = $line")
    //        }
    //      }
    //    )

    ScalaFilebeat.runJobs(Array(job), 10)
    TimeUnit.MINUTES.sleep(10)
    ScalaFilebeat.shutdown()
  }
}
