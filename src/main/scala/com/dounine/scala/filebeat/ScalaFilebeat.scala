package com.dounine.scala.filebeat

import java.io.File
import java.util.concurrent.{Executors, LinkedBlockingDeque, ScheduledExecutorService}

import com.dounine.scala.filebeat.util.JobUtil.AppendLog
import com.google.common.cache.LoadingCache
import org.apache.commons.io.filefilter.IOFileFilter

object ScalaFilebeat {

  var scheduledThreadPool: ScheduledExecutorService = _

  def runJobs(jobs: Array[FilebeatJob], threadPool: Int = 5): Unit = {
    if (null == scheduledThreadPool) {
      scheduledThreadPool = Executors.newScheduledThreadPool(threadPool)
    }
    jobs.foreach {
      job => {
        println(s"run job => ${job.job}")
        scheduledThreadPool.execute(job)
      }
    }
  }

  def shutdown(shutdownNow: Boolean = false): Unit = {
    if (shutdownNow) {
      scheduledThreadPool.shutdownNow()
    } else {
      scheduledThreadPool.shutdown()
    }
  }
}

case class Job(
                jobName: String, //任务名称
                debug: Boolean,
                logChatset: String,
                compression: Boolean,
                workPath: String,
                splitLine: String,
                logPaths: Array[String],
                recursive: Boolean,
                intervalFileStatus: String,
                intervalScanFile: String,
                dirFilter: IOFileFilter,
                fileFilter: IOFileFilter,
                linesBlockQueue: LinkedBlockingDeque[String],
                handlerFiles: LoadingCache[String, File],
                seekDB: LoadingCache[String, java.lang.Long], //文件索引缓存
                appendLog: AppendLog
              )
