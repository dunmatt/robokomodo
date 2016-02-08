package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw._
import squants.motion.{ AngularVelocity, Velocity }
import squants.space.AngleConversions._
import squants.space.LengthConversions._
import SquantsHelpers._

class Robot() {
  // TODO: clean up the mapping from motor controller channel to motor, as is there are redundancies
  val motors = RoboTriple( new Motor(0x86.toByte, true, -60 degrees)
                         , new Motor(0x86.toByte, false, 60 degrees)
                         , new Motor(0x87.toByte, false, 60 degrees))
  val radius = 146 millimeters  // this value from CAD, make sure it's up to date

  // TODO: delete this if it isn't used by the first test of the base
  // def getMotor(address: Byte, channel1: Boolean): Option[Motor] = (address, channel1) match {
  //   case (rightMotor.controllerAddress, rightMotor.channel1) => Some(rightMotor)
  //   case (leftMotor.controllerAddress, leftMotor.channel1) => Some(leftMotor)
  //   case (rearMotor.controllerAddress, rearMotor.channel1) => Some(rearMotor)
  //   case _ => None   // TODO: log a warning
  // }

  def motorSpeedsToAchieve(x: Velocity, y: Velocity, theta: AngularVelocity): RoboTriple[AngularVelocity] = {
    motors.map { m =>
      val spinContribution = theta * (radius / m.wheelCircumference)
      val linearContribution = m.i * x.rotationalSpeed(radius) + m.j * y.rotationalSpeed(radius)
      spinContribution + linearContribution
    }
  }

  def motorControllerCommandsToAchieve(setPoint: RoboTriple[AngularVelocity]): Set[UnitCommand] = {
    val freqs = motors.zip(setPoint).map { case (m, s) =>
      m.motorSpeedToPulseRate(s)
    }
    val a = DriveM1M2WithSignedSpeed(motors.left.controllerAddress, TwoMotorData(freqs.left, freqs.right))
    val b = DriveM2WithSignedSpeed(motors.rear.controllerAddress, freqs.rear)
    Set(a, b)
  }
}

case class RoboTriple[A](left: A, right: A, rear: A) {
  def map[B](fn: A=>B): RoboTriple[B] = RoboTriple(fn(left), fn(right), fn(rear))
  def zip[B](rt: RoboTriple[B]): RoboTriple[(A, B)] = RoboTriple( (left, rt.left)
                                                                , (right, rt.right)
                                                                , (rear, rt.rear))
}
