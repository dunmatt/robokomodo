package com.github.dunmatt.robokomodo

import squants.motion.{ AngularVelocity, Velocity }
import squants.space.{ Angle, Length }
import SquantsHelpers._

case class RobotCoordinate(x: Length, y: Length) {
  def approachAt(v: Velocity, dTheta: AngularVelocity): RobotPolarRates = {
    val theta = SquantsHelpers.atan2(y, x)
    // TODO: possibly negate dTheta if going around the other way would be faster
    RobotPolarRates(v, theta, dTheta)
  }
}

case class RobotPolarRates(v: Velocity, theta: Angle, dTheta: AngularVelocity) {
  def dx: Velocity = v * theta.cos
  def dy: Velocity = v * theta.sin
}
