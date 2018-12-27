package com.dounine.scala.filebeat.util

import java.util.zip.CRC32

object CrcUtil {

  def crc32(value: String): Long = {
    val crc32: CRC32 = new CRC32
    crc32.update(value.getBytes)
    crc32.getValue
  }

}
