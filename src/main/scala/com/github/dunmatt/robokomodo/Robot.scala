package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw._
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success }
import squants.electro.ElectricCurrentConversions._
import squants.electro.ElectricPotentialConversions._
import squants.motion.{ AngularVelocity }
import squants.space.AngleConversions._
import squants.space.LengthConversions._
import SquantsHelpers._

sealed trait State
case object NOT_STARTED extends State
case object STARTING extends State
case object READY extends State
case class GoingTo(coord: ArenaCoordinate) extends State
case object RUNNING extends State
case object SHUT_DOWN extends State
// case object SELF_TEST extends State

class Robot(serialPorts: Map[Byte, SerialPortManager]) extends InitialSetup {
  import Robot._
  protected val log = LoggerFactory.getLogger(getClass)
  protected val stallCurrent = 6.5 amps  // from the pololu product page
  protected val ratedVoltage = 6 volts  // from the pololu product page
  val motors = RoboTriple( new Motor(0x86.toByte, true, -60 degrees, ratedVoltage / stallCurrent)
                         , new Motor(0x86.toByte, false, 60 degrees, ratedVoltage / stallCurrent)
                         , new Motor(0x87.toByte, false, 60 degrees, ratedVoltage / stallCurrent))
  val radius = 146 millimeters  // this value from CAD, make sure it's up to date
  val batteryCellCount = 4
  val safeVoltageRange = Range(MIN_LIPO_CELL_VOLTAGE * batteryCellCount, MAX_LIPO_CELL_VOLTAGE * batteryCellCount)
  // TODO: add the weapon motor
  val requiredControllers = Set( motors.left.controllerAddress
                               , motors.right.controllerAddress
                               , motors.rear.controllerAddress)

  protected def allowedTransitions(prev: State, cur: State): Boolean = (prev, cur) match {
    case (SHUT_DOWN, _) => false
    case (_, NOT_STARTED) => false
    case (NOT_STARTED, STARTING) => true
    case (NOT_STARTED, SHUT_DOWN) => true
    case (NOT_STARTED, _) => false
    case (STARTING, READY) => true
    case (STARTING, SHUT_DOWN) => true
    case (STARTING, _) => false
    case (_, _) => true
  }

  protected def handleStateTransition(prev: State, cur: State): Unit = (prev, cur) match {
    case (RUNNING, _) => // TODO: disable the weapon
    case (_, RUNNING) => // TODO: enable the weapon
    case (_, _) => Unit
  }

  private var fsm = new StateMachine[State](NOT_STARTED, allowedTransitions, handleStateTransition)

  def aiLoop: Unit = {
    while (fsm.state != SHUT_DOWN) {
      aiStep
      // TODO: maybe sleep?
    }
    log.info("Shutting down.")
  }

  def aiStep: Unit = fsm.state match {
    case NOT_STARTED => fsm.state = STARTING; start
    case STARTING => Thread.sleep(10)
    case READY => Thread.sleep(10)
    case GoingTo(loc) => Unit  // TODO: write me
    case RUNNING => Unit  // TODO: write me
    case SHUT_DOWN => Unit
  }

  def start: Unit = {
    Future.reduce(serialPorts.keySet.map(startMotorController))(_ && _).onComplete {
      case Success(true) =>
        fsm.state = READY
      case Success(false) =>
        log.error("Problem initializing robot, shutting down.")
        fsm.state = SHUT_DOWN
      case Failure(e) =>
        log.error(s"Problem initializing robot: $e")
        log.error("Shutting down.")
        fsm.state = SHUT_DOWN
    }
  }

  def startMotorController(addr: Byte): Future[Boolean] = {
    val port = serialPorts(addr)
    port.sendCommand(ReadFirmwareVersion(addr)).foreach{ v => log.info(s"Found roboclaw at $addr ($v).") }
    Future.reduce(Set( checkBatteryVoltageAndLimits(addr, port)
                     , checkStatus(addr, port)
                     , checkEncoders(addr, port)
                     , checkConfiguration(addr, port)
                    // TODO: check the PID parameters and include them in the results
                     ))(_ && _)
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
    motors.map { motor =>
      val spinContribution = setPoints.dTheta * (radius / motor.wheelCircumference)
      val xContribution = motor.i * setPoints.dx.rotationalSpeed(radius)
      val yContribution = motor.j * setPoints.dy.rotationalSpeed(radius)
      spinContribution + xContribution + yContribution
    }
  }

  def motorControllerCommandsToAchieve(setPoints: RobotCoordinateRates): RoboTriple[Command[Unit]] = {
    motorControllerCommandsToAchieve(motorSpeedsToAchieve(setPoints))
  }

  def motorControllerCommandsToAchieve(setPoints: RoboTriple[AngularVelocity]): RoboTriple[Command[Unit]] = {
    motors.zip(setPoints).map{ case (motor, setPoint) =>
      val addr = motor.controllerAddress
      val pulseRate = motor.motorSpeedToPulseRate(setPoint)
      motor.chooseCommand(DriveM1WithSignedSpeed(addr, pulseRate), DriveM2WithSignedSpeed(addr, pulseRate))
    }
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
