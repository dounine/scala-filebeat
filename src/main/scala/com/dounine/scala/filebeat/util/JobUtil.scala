package com.dounine.scala.filebeat.util

import java.io.File
import java.util.concurrent.{LinkedBlockingDeque, TimeUnit}

import com.dounine.scala.filebeat.{Job, FilebeatJob}
import com.google.common.cache.{CacheBuilder, CacheLoader}
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.{FileFilterUtils, IOFileFilter}

object JobUtil {

  trait AppendLog {
    def init(): Unit ={

    }
    def append(line: String): Unit = {

    }
  }

  def getSecondsByAlias(alias: String): Long = {
    val value = alias.substring(0, alias.length - 1).toLong
    alias.reverse.charAt(0) match {
      case 's' => value
      case 'm' => value * 60
      case 'h' => value * 60 * 60
      case 'd' => value * 60 * 60 * 24 * 30
      case default@_ => default.toLong
    }
  }

  def createJob(workPath: String, debug: Boolean = false, compression: Boolean,logChatset:String, logPaths: Array[String], jobName: String, splitLine: String = "\n", recursive: Boolean, includeSuffixs: Array[String], ignoreOlder: String, intervalFileStatus: String, intervalScanFile: String, includePaths: Array[String], excludePaths: Array[String], handlerFileClose: String, appendLog: AppendLog): FilebeatJob = {
    val ignoreOlderSeconds = getSecondsByAlias(ignoreOlder)
    val suffixTypeFilters = includeSuffixs.map {
      suff => FileFilterUtils.suffixFileFilter(suff)
    }
    val ignoreOlderFilter = new IOFileFilter {
      override def accept(file: File): Boolean = (System.currentTimeMillis() - file.lastModified()) / 1000 <= ignoreOlderSeconds
      override def accept(file: File, s: String): Boolean = false
    }
    val includeExcludeFilter = new IOFileFilter {
      override def accept(file: File): Boolean = {
        if (!recursive) {
          if (file.isDirectory) {
            return false
          }
        }
        val excludePathsMatch = excludePaths.flatMap {
          excludePath => {
            if (file.getAbsolutePath.matches(excludePath)) {
              Array(x = true)
            } else {
              Array[Boolean]()
            }
          }
        }.contains(true)

        val includePathsMatch = includePaths.flatMap {
          excludePath => {
            if (file.getAbsolutePath.matches(excludePath)) {
              Array(x = true)
            } else {
              Array[Boolean]()
            }
          }
        }.contains(true)

        includePathsMatch || !excludePathsMatch
      }

      override def accept(file: File, s: String): Boolean = true
    }

    val fileFilter = FileFilterUtils.and(
      FileFilterUtils.or(suffixTypeFilters: _*), //文件后缀匹配
      ignoreOlderFilter, //忽略指定时间段以外修改的日志内容
      includeExcludeFilter
    )
    val handlerFiles = CacheBuilder.newBuilder()
      .expireAfterWrite(getSecondsByAlias(handlerFileClose), TimeUnit.SECONDS)
      .build[String, File](new CacheLoader[String, File] {
      override def load(k: String): File = {
        null
      }
    })
    val seekDB = CacheBuilder.newBuilder()
      .expireAfterWrite(getSecondsByAlias(handlerFileClose), TimeUnit.SECONDS)
      .build[String, java.lang.Long](new CacheLoader[String, java.lang.Long] {
      override def load(path: String): java.lang.Long = {
        val dbFile = FileUtils.getFile(s"$workPath/$jobName/seek.db")
        val seekLines = FileUtils.readLines(dbFile, "utf-8")
        import scala.collection.JavaConversions._
        val matchLine = seekLines.flatMap {
          line => {
            if (line.split("\t")(0).equals(path)) {
              Array(line)
            } else {
              Array[String]()
            }
          }
        }
        if (matchLine.nonEmpty) {
          matchLine.head.split("\t")(1).toLong
        } else {
          -1L
        }
      }
    })
    val recursiveDir = if (recursive) {
      FileFilterUtils.directoryFileFilter()
    } else {
      FileFilterUtils.fileFileFilter()
    }
    new FilebeatJob(Job(
      jobName,
      debug,
      logChatset,
      compression,
      workPath,
      splitLine,
      logPaths,
      recursive,
      intervalFileStatus,
      intervalScanFile,
      recursiveDir,
      fileFilter,
      new LinkedBlockingDeque[String](),
      handlerFiles,
      seekDB,
      appendLog
    ))
  }


}
