package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw._
import org.slf4j.Logger
import squants.electro.{ ElectricCurrent, ElectricPotential }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait InitialSetup {
  import Robot._
  protected def log: Logger
  protected def stallCurrent: ElectricCurrent
  protected def ratedVoltage: ElectricPotential
  protected def motors: RoboTriple[Motor]

  def setUpCurrentLimits(mainVoltage: ElectricPotential, serialPorts: Map[Byte, SerialPortManager]): Future[Boolean] = {
    val limit = stallCurrent * ratedVoltage / mainVoltage
    val setCorrectly = (ec: ElectricCurrent) => ec == limit
    val results = motors.map{ motor =>
      val addr = motor.controllerAddress
      Utilities.readSetRead( serialPorts(addr)
                           , motor.chooseCommand(ReadM1CurrentLimit(addr), ReadM2CurrentLimit(addr))
                           , setCorrectly
                           , motor.chooseCommand(SetM1CurrentLimit(addr, limit), SetM2CurrentLimit(addr, limit))
                           , Some(log)).map(setCorrectly)
    }
    Future.reduce(Seq(results.left, results.right, results.rear))(_ && _)
  }
}
