package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw._
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success
import squants.electro.ElectricPotentialConversions._
import squants.motion.{ AngularVelocity, Velocity }
import squants.space.AngleConversions._
import squants.space.LengthConversions._
import SquantsHelpers._

sealed trait State
case object NOT_STARTED extends State
case object READY extends State
case class GoingTo(coord: ArenaCoordinate) extends State
case object RUNNING extends State
case object SHUT_DOWN extends State
// case object SELF_TEST extends State

class Robot(serialPorts: Map[Byte, SerialPortManager]) {
  import Robot._
  protected val log = LoggerFactory.getLogger(getClass)
  // TODO: clean up the mapping from motor controller channel to motor, as is there are redundancies
  val motors = RoboTriple( new Motor(0x86.toByte, true, -60 degrees)
                         , new Motor(0x86.toByte, false, 60 degrees)
                         , new Motor(0x87.toByte, false, 60 degrees))
  val radius = 146 millimeters  // this value from CAD, make sure it's up to date
  val batteryCellCount = 4
  // TODO: add the weapon motor
  val requiredControllers = Set( motors.left.controllerAddress
                               , motors.right.controllerAddress
                               , motors.rear.controllerAddress)

  protected def allowedTransitions(prev: State, cur: State): Boolean = (prev, cur) match {
    case (SHUT_DOWN, _) => false
    case (NOT_STARTED, READY) => true
    case (NOT_STARTED, SHUT_DOWN) => true
    case (NOT_STARTED, _) => false
    case (_, NOT_STARTED) => false
    case (_, _) => true
  }

  protected def handleStateTransition(prev: State, cur: State): Unit = (prev, cur) match {
    case (RUNNING, _) => // TODO: disable the weapon
    case (_, RUNNING) => // TODO: enable the weapon
    case (_, _) => Unit
  }

  private var fsm = new StateMachine[State](NOT_STARTED, allowedTransitions, handleStateTransition)

  // TODO: delete this if it isn't used by the first test of the base
  // def getMotor(address: Byte, channel1: Boolean): Option[Motor] = (address, channel1) match {
  //   case (rightMotor.controllerAddress, rightMotor.channel1) => Some(rightMotor)
  //   case (leftMotor.controllerAddress, leftMotor.channel1) => Some(leftMotor)
  //   case (rearMotor.controllerAddress, rearMotor.channel1) => Some(rearMotor)
  //   case _ => None   // TODO: log a warning
  // }

  def aiLoop: Unit = {
    while (fsm.state != SHUT_DOWN) {
      aiStep
      // TODO: maybe sleep?
    }
    log.info("Shutting down.")
  }

  def aiStep: Unit = fsm.state match {
    case NOT_STARTED => start
    case READY => Thread.sleep(10)
    case GoingTo(loc) => Unit  // TODO: write me
    case RUNNING => Unit  // TODO: write me
    case SHUT_DOWN => Unit
  }

  def start: Unit = {
    // TODO: do something with the results...
    serialPorts.keySet.map(startMotorController)
  }

  def startMotorController(addr: Byte): Future[Boolean] = {
    val port = serialPorts(addr)
    val version = port.sendCommand(ReadFirmwareVersion(addr))
    version.foreach{ v => log.info(s"Found roboclaw at $addr ($v).") }
    val voltageOkay = port.sendCommand(ReadMainBatteryVoltageSettings(addr)).map{ range =>
      val expectedMin = MIN_LIPO_CELL_VOLTAGE * batteryCellCount
      val expectedMax = MAX_LIPO_CELL_VOLTAGE * batteryCellCount
      if (range.min == expectedMin && range.max == expectedMax) {
        range
      } else {
        log.debug(s"Got unexpected battery voltage range $range, resetting.")
        port.sendCommand(SetMainBatteryVoltages(addr, expectedMin, expectedMax))
        Range(expectedMin, expectedMax)
      }
    }.flatMap{ range =>
      port.sendCommand(ReadMainBatteryVoltage(addr)).andThen{ case Success(v) =>
        val batteryLevel = (v - range.min) / (range.max - range.min) * 100
        log.info(s"Main battery at $batteryLevel% ($v)")
      }.map(range.contains)
    }
    voltageOkay
  }

  def stop: Unit = {
    fsm.state = READY
    serialPorts.foreach { case (addr, port) =>
      port.sendCommand(DriveM1M2WithSignedDutyCycle(addr, TwoMotorData(0d, 0d)))
    }
  }

  def stopAndShutDown: Unit = {
    stop
    fsm.state = SHUT_DOWN
  }

  def motorSpeedsToAchieve(setPoints: RobotCoordinateRates): RoboTriple[AngularVelocity] = {
    motors.map { m =>
      val spinContribution = setPoints.dTheta * (radius / m.wheelCircumference)
      val xContribution = m.i * setPoints.dx.rotationalSpeed(radius)
      val yContribution = m.j * setPoints.dy.rotationalSpeed(radius)
      spinContribution + xContribution + yContribution
    }
  }

  def motorControllerCommandsToAchieve(setPoints: RobotCoordinateRates): Set[UnitCommand] = {
    motorControllerCommandsToAchieve(motorSpeedsToAchieve(setPoints))
  }

  def motorControllerCommandsToAchieve(setPoint: RoboTriple[AngularVelocity]): Set[UnitCommand] = {
    val freqs = motors.zip(setPoint).map { case (m, s) => m.motorSpeedToPulseRate(s) }
    val a = DriveM1M2WithSignedSpeed(motors.left.controllerAddress, TwoMotorData(freqs.left, freqs.right))
    val b = DriveM2WithSignedSpeed(motors.rear.controllerAddress, freqs.rear)
    Set(a, b)
  }
}

object Robot {
  val MIN_LIPO_CELL_VOLTAGE = 3.2 volts
  val MAX_LIPO_CELL_VOLTAGE = 4.2 volts
}

case class RoboTriple[A](left: A, right: A, rear: A) {
  def map[B](fn: A=>B): RoboTriple[B] = RoboTriple(fn(left), fn(right), fn(rear))
  def zip[B](rt: RoboTriple[B]): RoboTriple[(A, B)] = RoboTriple( (left, rt.left)
                                                                , (right, rt.right)
                                                                , (rear, rt.rear))
}
