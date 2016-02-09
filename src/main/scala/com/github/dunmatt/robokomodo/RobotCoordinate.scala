package com.github.dunmatt.robokomodo

import squants.motion.{ AngularVelocity, Velocity }
import squants.space.{ Angle, Length }

case class RobotCoordinate(x: Length, y: Length)

case class RobotCoordinateRates(dx: Velocity, dy: Velocity, dTheta: AngularVelocity)
