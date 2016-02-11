package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw._
import squants.DimensionlessConversions._
import squants.electro.{ ElectricalResistance, ElectricCurrent, ElectricPotential }
import squants.motion.AngularVelocity
import squants.space.Angle
import squants.space.AngleConversions._
import squants.space.LengthConversions._
import squants.time.Frequency
import squants.time.TimeConversions._
import SquantsHelpers._

// https://www.pololu.com/product/2273
class Motor( val controllerAddress: Byte
           , val channel1: Boolean  // TODO: does this need to be exposed?
           , mountAngle: Angle
           , stallResistance: ElectricalResistance) {
  val wheelDiameter = 60 millimeters
  val gearboxReduction = (22*20*22*22*23) / (12*12*10*10*10)
  val encoderCountsPerMotorTurn = 48 each
  // [i j] is the "forward" vector for the wheel
  val i = -mountAngle.sin
  val j = -mountAngle.cos

  def wheelCircumference = wheelDiameter * math.Pi

  def pulseRateToMotorSpeed(pulseRate: Frequency): AngularVelocity = {
    val dt = 1.seconds
    (pulseRate * dt / encoderCountsPerMotorTurn).turns / dt
  }

  def motorSpeedToPulseRate(av: AngularVelocity): Frequency = {
    val dt = 1.seconds
    (av * dt).toTurns * encoderCountsPerMotorTurn / dt
  }

  def pulseRateToShaftSpeed(pulseRate: Frequency): AngularVelocity = {
    pulseRateToMotorSpeed(pulseRate) / gearboxReduction
  }

  def stallCurrentAt(voltage: ElectricPotential): ElectricCurrent = voltage / stallResistance

  def chooseCommand[T](m1Cmd: Command[T], m2Cmd: Command[T]): Command[T] = channel1 match {
    case true => m1Cmd
    case false => m2Cmd
  }
}
