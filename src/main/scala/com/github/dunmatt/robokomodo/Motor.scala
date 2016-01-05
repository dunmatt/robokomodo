package com.github.dunmatt.robokomodo

import squants.motion.AngularVelocity
import squants.space.Angle
import squants.space.AngleConversions._
import squants.space.LengthConversions._
import squants.time.Frequency
import squants.time.TimeConversions._
import SquantsHelpers._

// https://www.pololu.com/product/2273
class Motor(controllerAddress: Byte, channel1: Boolean, mountAngle: Angle) {
  val wheelDiameter = 60 millimeters
  val gearboxReduction = (22*20*22*22*23) / (12*12*10*10*10)
  val encoderCountsPerRevolution = 48

  def pulsesToShaftSpeed(pulses: Frequency): AngularVelocity = {
    val dt = 1.seconds
    ((pulses * dt).toEach / encoderCountsPerRevolution / gearboxReduction).turns / dt
  }
}
