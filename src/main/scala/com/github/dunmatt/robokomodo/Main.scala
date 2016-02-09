package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw._
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

object Main extends App {
  protected val log = LoggerFactory.getLogger(getClass)
  val ports = SerialPortManager.findXbees
  val robot = new Robot(ports)
  if (robot.requiredControllers.subsetOf(ports.keySet)) {
    robot.aiLoop
  } else {
    log.error(s"Couldn't find motor controllers: ${robot.requiredControllers &~ ports.keySet}")
  }
  Thread.sleep(100)
  println(Try(ports.values.toSet.foreach{ m: SerialPortManager => m.disconnect }))
}
