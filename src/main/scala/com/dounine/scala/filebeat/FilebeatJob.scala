package com.dounine.scala.filebeat

import java.io.{File, RandomAccessFile}
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

import com.dounine.scala.filebeat.util.{CrcUtil, JobUtil}
import org.apache.commons.io.FileUtils

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

class FilebeatJob(val job: Job) extends Runnable {

  initSeekDb(job.jobName)

  override def run(): Unit = {

    job.appendLog.init()

    ScalaFilebeat.scheduledThreadPool.scheduleAtFixedRate(new Runnable {
      override def run(): Unit = {
        job.logPaths.foreach {
          logPath => {
            val dirFile = FileUtils.getFile(logPath)
            val logFiles = FileUtils.listFiles(dirFile, job.fileFilter, job.dirFilter)
            logFiles.forEach(new Consumer[File] {
              override def accept(logFile: File): Unit = {
                if (job.debug) {
                  if (!job.handlerFiles.asMap().keySet().contains(logFile.getAbsolutePath)) {
                    println(s"<${job.jobName}> listener file => ${logFile.getAbsoluteFile}")
                  }
                }
                job.handlerFiles.put(logFile.getAbsolutePath, logFile)
              }
            })
          }
        }

      }
    }, 0, JobUtil.getSecondsByAlias(job.intervalScanFile), TimeUnit.SECONDS)

    ScalaFilebeat.scheduledThreadPool.scheduleAtFixedRate(new Runnable {
      override def run(): Unit = {
        job.handlerFiles.asMap().values().forEach(new Consumer[File] {
          override def accept(t: File): Unit = {
            val fileLength = t.length()
            val absPath = if (job.compression) {
              CrcUtil.crc32(t.getAbsolutePath).toString
            } else {
              t.getAbsolutePath
            }
            val fileDbSeek = job.seekDB.get(absPath)
            val lines = if (fileDbSeek == -1) {
              if (job.debug) {
                println(s"<${job.jobName}> file[new] => ${t.getAbsoluteFile}")
              }
              //new file
              readLinesForSeek(0, t)
            } else if (fileDbSeek < t.length()) {
              if (job.debug) {
                println(s"<${job.jobName}> file[change] => ${t.getAbsoluteFile}")
              }
              //file change
              readLinesForSeek(fileDbSeek, t)
            } else {
              //none
              Array[String]()
            }
            if (lines.nonEmpty) {
              lines.foreach(job.linesBlockQueue.add)
            }
            job.seekDB.put(absPath, fileLength)
          }
        })
        flushCacheSeekToDb()
      }
    }, 1, JobUtil.getSecondsByAlias(job.intervalFileStatus), TimeUnit.SECONDS)
    ScalaFilebeat.scheduledThreadPool.schedule(new Runnable {
      override def run(): Unit = {
        while (!ScalaFilebeat.scheduledThreadPool.isShutdown) {
          val line: String = job.linesBlockQueue.poll()

          if (null != line) {
            job.appendLog.append(line)
          }
        }
      }
    }, 1, TimeUnit.MILLISECONDS)

  }

  def flushCacheSeekToDb(): Unit = {
    val dbFile = FileUtils.getFile(s"${job.workPath}/${job.jobName}/seek.db")
    val seekLines = FileUtils.readLines(dbFile, "utf-8")
    val tmpList = ListBuffer.empty ++= job.seekDB.asMap().keys
    var matchCount = 0
    val matchLine = seekLines.map {
      line => {
        val lineInfos = line.split("\t")
        val currentSeek = job.seekDB.get(lineInfos(0))
        tmpList -= lineInfos(0)
        if (!currentSeek.equals(lineInfos(1).toLong)) {
          //seek索引不相同,更新
          matchCount += 1
          s"${lineInfos(0)}\t$currentSeek"
        } else {
          line
        }
      }
    }.toList ++ tmpList.map {
      //插入没有匹配到的文件,新文件
      filePath => {
        matchCount += 1
        filePath + "\t" + job.seekDB.get(filePath)
      }
    }
    if (matchCount > 0) {
      FileUtils.writeLines(dbFile, matchLine, false)
    }
  }

  def initSeekDb(dbName: String): Unit = {
    val dbFold = FileUtils.getFile(s"${job.workPath}/${job.jobName}")
    if (!dbFold.exists()) {
      dbFold.mkdirs()
    }
    val dbFile = FileUtils.getFile(s"${job.workPath}/${job.jobName}/seek.db")
    if (!dbFile.exists()) {
      dbFile.createNewFile()
    }
  }

  def readLinesForSeek(seek: Long, file: File): Array[String] = {
    val randomFile = new RandomAccessFile(file, "r")
    randomFile.seek(seek)
    val byteList = new Array[Byte]((file.length() - seek).toInt)
    randomFile.readFully(byteList)
    randomFile.close()
    new String(byteList, job.logChatset).split(job.splitLine)
  }

}
