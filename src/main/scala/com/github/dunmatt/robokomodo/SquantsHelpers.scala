package com.github.dunmatt.robokomodo

import squants.motion.{ AngularVelocity, TurnsPerSecond }
import squants.space.Angle
import squants.time.Time

object SquantsHelpers {
  implicit class SpeedableAngle(a: Angle) {
    def /(t: Time): AngularVelocity = TurnsPerSecond(a.toTurns / t.toSeconds)
  }
}
