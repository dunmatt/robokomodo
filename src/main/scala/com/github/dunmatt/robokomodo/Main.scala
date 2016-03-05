package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw._
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._
import scala.util.Try
import squants.time.FrequencyConversions._

object Main extends App {
  protected val log = LoggerFactory.getLogger(getClass)
  val ports = SerialPortManager.findRoboClaws
  // val ports = SerialPortManager.findXbees
  Thread.sleep(1000)
  // val port = ports(0x87.toByte)
  // port.sendCommand(SetVelocityPidConstantsM1(0x87.toByte, 10000 hertz, 0, 0, 0))
  // port.sendCommand(SetVelocityPidConstantsM2(0x87.toByte, 10000 hertz, 0, 0, 0))
  // port.sendCommand(WriteSettingsToEeprom(0x87.toByte))
  val cal = new Calibrator(0x87.toByte, true, ports(0x87.toByte))
  Try(Await.result(cal.calibrateVelocity, 10000 millis))
  // Try(Await.result(cal.calibrateVelocityDebug, 10000 millis))
  // val robot = new Robot(ports)
  // if (robot.requiredControllers.subsetOf(ports.keySet)) {
  //   // this bad boy intercepts SIGINT and Ctrl+C
  //   val mainThread = Thread.currentThread
  //   Runtime.getRuntime.addShutdownHook(new Thread() {override def run = {
  //     log.info("Shutdown hook triggered.")
  //     robot.stopAndShutDown
  //     mainThread.join()
  //   }})
  //   // do everything
  //   robot.aiLoop
  // } else {
  //   log.error(s"Couldn't find motor controllers: ${robot.requiredControllers &~ ports.keySet}")
  // }
  Thread.sleep(5000)
  println(Try(ports.values.toSet.foreach{ m: SerialPortManager => m.disconnect }))
}
