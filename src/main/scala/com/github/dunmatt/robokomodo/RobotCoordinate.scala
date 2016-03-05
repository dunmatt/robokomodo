package com.github.dunmatt.robokomodo

import squants.motion.{ AngularVelocity, Velocity }
import squants.space.{ Angle, Length }
import squants.space.AngleConversions._
import SquantsHelpers._

case class RobotCoordinate(x: Length, y: Length) {
  def approachAt(v: Velocity, dTheta: AngularVelocity): RobotPolarRates = {
    val theta = SquantsHelpers.atan2(y, x)
    if (theta >= 0.radians) {
      RobotPolarRates(v, theta, dTheta)
    } else {
      RobotPolarRates(v, theta, -dTheta)
    }
  }
}

case class RobotPolarRates(v: Velocity, theta: Angle, dTheta: AngularVelocity) {
  def dx: Velocity = v * theta.cos
  def dy: Velocity = v * theta.sin
}
