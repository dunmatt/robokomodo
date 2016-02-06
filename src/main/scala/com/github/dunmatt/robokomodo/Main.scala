package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

object Main extends App {
  val ports = SerialPortManager.findXbees
  Thread.sleep(1000)
  println(Try(ports.foreach(_.disconnect)))
}
