package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw._
import squants.DimensionlessConversions._
import squants.electro.{ ElectricalResistance, ElectricCurrent, ElectricPotential }
import squants.motion.{ AngularVelocity, TurnsPerSecond, Velocity }
import squants.space.Angle
import squants.space.AngleConversions._
import squants.space.LengthConversions._
import squants.time.Frequency
import squants.time.TimeConversions._
import SquantsHelpers._

// https://www.pololu.com/product/2273
class Motor( val controllerAddress: Byte
           , channel1: Boolean  // TODO: does this need to be exposed?
           , val forward: Angle
           , stallResistance: ElectricalResistance
           , kv: AngularVelocity) {
  val wheelDiameter = 60 millimeters
  val gearboxReduction = (22*20*22*22*23) / (12*12*10*10*10)
  val encoderCountsPerMotorTurn = 48 each
  val commandFactory = CommandFactory(controllerAddress, channel1)

  def wheelCircumference = wheelDiameter * math.Pi

  def wheelSpeedAtLinearSpeed(v: Velocity): AngularVelocity = {
    TurnsPerSecond(v * 1.seconds / wheelCircumference)
  }

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

  def freeRunSpeedAt(voltage: ElectricPotential): AngularVelocity = voltage.toVolts * kv / gearboxReduction
}
