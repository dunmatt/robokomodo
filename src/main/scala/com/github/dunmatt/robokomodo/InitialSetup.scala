package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw._
import org.slf4j.Logger
import squants.electro.{ ElectricCurrent, ElectricPotential }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

trait InitialSetup {
  import Robot._
  protected def log: Logger
  protected def stallCurrent: ElectricCurrent
  protected def ratedVoltage: ElectricPotential
  protected def motors: RoboTriple[Motor]
  protected def safeVoltageRange: Range[ElectricPotential]
  protected def batteryCellCount: Int

  def setUpCurrentLimits(mainVoltage: ElectricPotential, serialPorts: Map[Byte, SerialPortManager]): Future[Boolean] = {
    val limit = stallCurrent * ratedVoltage / mainVoltage
    val setCorrectly = (ec: ElectricCurrent) => ec == limit
    val results = motors.map{ motor =>
      val addr = motor.controllerAddress
      Utilities.readSetRead( serialPorts(addr)
                           , motor.chooseCommand(ReadM1CurrentLimit(addr), ReadM2CurrentLimit(addr))
                           , setCorrectly
                           , motor.chooseCommand(SetM1CurrentLimit(addr, limit), SetM2CurrentLimit(addr, limit))
                           , Some(log)
                           ).map(setCorrectly)
    }
    Future.reduce(Seq(results.left, results.right, results.rear))(_ && _)
  }

  def checkBatteryVoltageAndLimits(addr: Byte, port: SerialPortManager): Future[Boolean] = {
    Utilities.readSetRead( port
                         , ReadMainBatteryVoltageSettings(addr)
                         , ((r: Range[ElectricPotential]) => r.min == safeVoltageRange.min && r.max == safeVoltageRange.max)
                         , SetMainBatteryVoltages(addr, safeVoltageRange.min, safeVoltageRange.max)
                         , Some(log)
                         ).flatMap{ range =>
      port.sendCommand(ReadMainBatteryVoltage(addr)).andThen{ case Success(v) =>
        val batteryLevel = (v - range.min) / (range.max - range.min) * 100
        log.info(s"Main battery at $batteryLevel% ($v)")
      }.map(range.contains)
    }
  }

  def checkStatus(addr: Byte, port: SerialPortManager): Future[Boolean] = {
    port.sendCommand(ReadStatus(addr)).andThen{ case Success(status) =>
      if (status.normal) {
        log.info("Passed status check")
      } else {
        log.error(s"Status check failed, code ${status.status & 0xffff}")
      }
    }.map(_.normal)
  }

  def checkConfiguration(addr: Byte, port: SerialPortManager): Future[Boolean] = {
    port.sendCommand(ReadStandardConfigSettings(addr)).map{ config =>
      if (!config.packetSerialMode) {
        log.error(s"RoboClaw $addr not in packet serial mode!")
      }
      if (!config.multiUnitMode) {
        log.error(s"RoboClaw $addr not in multi unit mode!")
      }
      if (!batteryProtectValid(config)) {
        log.error(s"Invalid battery protection level!")
      }
      config.packetSerialMode && config.multiUnitMode && batteryProtectValid(config)
    }
  }

  def checkEncoders(addr: Byte, port: SerialPortManager): Future[Boolean] = {
    Utilities.readSetRead( port
                         , ReadEncoderMode(addr)
                         , ((encs: TwoMotorData[EncoderMode]) => encs.m1 == QUADRATURE && encs.m2 == QUADRATURE)
                         , Seq(SetMotor1EncoderMode(addr, QUADRATURE), SetMotor2EncoderMode(addr, QUADRATURE))
                         , Some(log)).map(_ => true)
  }

  protected def batteryProtectValid(config: ConfigSettings): Boolean = {
    config.batteryProtectAuto || (batteryCellCount match {
      case 2 => config.batteryProtect2Cell
      case 3 => config.batteryProtect3Cell
      case 4 => config.batteryProtect4Cell
      case 5 => config.batteryProtect5Cell
      case 6 => config.batteryProtect6Cell
      case 7 => config.batteryProtect7Cell
      case _ => false
    })
  }
}