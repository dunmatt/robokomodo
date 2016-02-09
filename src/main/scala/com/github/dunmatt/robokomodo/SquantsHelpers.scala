package com.github.dunmatt.robokomodo

import squants.motion.{ AngularVelocity, RadiansPerSecond, TurnsPerSecond, Velocity }
import squants.space.{ Angle, Area, Length, Meters, Turns }
import squants.space.AngleConversions._
import squants.time.Time
import squants.time.TimeConversions._

object SquantsHelpers {
  implicit class AnglableSpeed(av: AngularVelocity) {
    def *(t: Time): Angle = Turns(av.toTurnsPerSecond * t.toSeconds)
  }

  implicit class SpeedableAngle(a: Angle) {
    def /(t: Time): AngularVelocity = TurnsPerSecond(a.toTurns / t.toSeconds)
  }

  implicit class RootableArea(a: Area) {
    def sqrt: Length = Meters(math.sqrt(a.toSquareMeters))
  }

  implicit class SmarterAngularVelocity(av: AngularVelocity) {
    def speedAtDistance(radius: Length): Velocity = av.toRadiansPerSecond * radius / 1.seconds
  }

  implicit class SmarterVelocity(v: Velocity) {
    def rotationalSpeed(radius: Length): AngularVelocity = RadiansPerSecond(v * 1.seconds / radius)
  }

  def atan2(y: Double, x: Double): Angle = math.atan2(y, x).radians

  def atan2(y: Length, x: Length): Angle = math.atan2(y.toMeters, x.toMeters).radians
}
