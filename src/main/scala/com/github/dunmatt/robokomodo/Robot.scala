package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw._
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success }
import squants.electro.ElectricCurrentConversions._
import squants.electro.ElectricPotentialConversions._
import squants.motion.{ AngularVelocity }
import squants.motion.VelocityConversions._
import squants.motion.AngularVelocityConversions._
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
  protected val kv = (10000 / ratedVoltage.toVolts / 60).turnsPerSecond  // from the pololu product page
  val motors = RoboTriple( new Motor(0x86.toByte, true, 150 degrees, ratedVoltage / stallCurrent, kv)
                         , new Motor(0x86.toByte, false, 30 degrees, ratedVoltage / stallCurrent, kv)
                         , new Motor(0x87.toByte, false, 270 degrees, ratedVoltage / stallCurrent, kv))
  val radius = 146 millimeters  // this value from CAD, make sure it's up to date
  val batteryCellCount = 4
  val safeVoltageRange = Range(MIN_LIPO_CELL_VOLTAGE * batteryCellCount, MAX_LIPO_CELL_VOLTAGE * batteryCellCount)
  // TODO: add the weapon motor
  val requiredControllers = Set( motors.left.controllerAddress
                               , motors.right.controllerAddress
                               , motors.rear.controllerAddress)

  protected def allowedTransitions(prev: State, cur: State): Boolean = (prev, cur) match {
    case (_, SHUT_DOWN) => true
    case (SHUT_DOWN, _) => false
    case (_, NOT_STARTED) => false
    case (NOT_STARTED, STARTING) => true
    case (NOT_STARTED, _) => false
    case (STARTING, READY) => true
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

  protected def aiStep: Unit = fsm.state match {
    case NOT_STARTED => fsm.state = STARTING; start
    case STARTING => Thread.sleep(10)  // wait for async starting stuff to complete
    case READY => Thread.sleep(10)
    case GoingTo(loc) => Unit  // TODO: call driveTowards
    case RUNNING => Unit  // TODO: write me
    case SHUT_DOWN => Unit
  }

  protected def start: Unit = {
    Future.reduce(serialPorts.keySet.map(startMotorController))(_ && _).onComplete {
      case Success(true) =>
        // TODO: start watchdogs
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

  protected def startMotorController(addr: Byte): Future[Boolean] = {
    val port = serialPorts(addr)
    port.sendCommand(ReadFirmwareVersion(addr)).foreach{ v => log.info(s"Found roboclaw at $addr ($v).") }
    Future.reduce(Set( checkBatteryVoltageAndLimits(addr, port)
                     , checkStatus(addr, port)
                     , checkEncoders(addr, port)
                     , checkConfiguration(addr, port)
                    // TODO: check the PID parameters and include them in the results
                     ))(_ && _)
  }

  def goTo(target: ArenaCoordinate): Unit = fsm.state match {
    case READY => fsm.state = GoingTo(target)
    case GoingTo(_) => fsm.state = GoingTo(target)
    case RUNNING => fsm.state = GoingTo(target)
    case _ => Unit
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

  protected def driveTowards(current: ArenaCoordinate, target: ArenaCoordinate): Unit = {
    if (current.distanceTo(target) < 5.centimeters) {  // TODO: pull this threshhold out and put it somewhere good
      stop
    } else {
      // TODO: maybe do something smart to generate the speed?
      val setPoints = current.relativePositionOf(target).approachAt(1.mps, 1.radiansPerSecond)
      motorControllerCommandsToAchieve(setPoints).foreach{ cmd =>
        serialPorts(cmd.address).sendCommand(cmd)
      }
    }
  }

  def motorSpeedsToAchieve(setPoints: RobotPolarRates): RoboTriple[AngularVelocity] = {
    motors.map { motor =>
      // the math here comes from http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.99.1083&rep=rep1&type=pdf
      val spin = setPoints.dTheta * (radius / motor.wheelCircumference)
      val translate = setPoints.v * (motor.forward - setPoints.theta).cos
      spin + motor.wheelSpeedAtLinearSpeed(translate)
    }
  }

  def motorControllerCommandsToAchieve(setPoints: RobotPolarRates): RoboTriple[Command[Unit]] = {
    motorControllerCommandsToAchieve(motorSpeedsToAchieve(setPoints))
  }

  def motorControllerCommandsToAchieve(setPoints: RoboTriple[AngularVelocity]): RoboTriple[Command[Unit]] = {
    motors.zip(setPoints).map{ case (motor, setPoint) =>
      val pulseRate = motor.motorSpeedToPulseRate(setPoint)
      motor.commandFactory.driveWithSignedSpeed(pulseRate)
    }
  }
}

object Robot {
  val MIN_LIPO_CELL_VOLTAGE = 3.2 volts
  val MAX_LIPO_CELL_VOLTAGE = 4.2 volts
}

case class RoboTriple[A](left: A, right: A, rear: A) {
  def foreach(fn: A=>Unit): Unit = {
    fn(left)
    fn(right)
    fn(rear)
  }
  def map[B](fn: A=>B): RoboTriple[B] = RoboTriple(fn(left), fn(right), fn(rear))
  def zip[B](rt: RoboTriple[B]): RoboTriple[(A, B)] = RoboTriple( (left, rt.left)
                                                                , (right, rt.right)
                                                                , (rear, rt.rear))
}
