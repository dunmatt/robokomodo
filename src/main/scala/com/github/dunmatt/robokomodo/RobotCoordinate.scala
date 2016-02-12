package com.github.dunmatt.robokomodo

import squants.motion.{ AngularVelocity, Velocity }
import squants.space.{ Angle, Length }
import SquantsHelpers._

case class RobotCoordinate(x: Length, y: Length) {
  def approachAt(v: Velocity, dTheta: AngularVelocity): RobotCoordinateRates = {
    val theta = SquantsHelpers.atan2(y, x)
    // TODO: possibly negate dTheta if going around the other way would be faster
    RobotCoordinateRates(v * theta.cos, v * theta.sin, dTheta)
  }
}

case class RobotCoordinateRates(dx: Velocity, dy: Velocity, dTheta: AngularVelocity)
