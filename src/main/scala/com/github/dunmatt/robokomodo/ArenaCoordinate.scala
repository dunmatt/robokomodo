package com.github.dunmatt.robokomodo

import squants.space.{ Angle, Length }
import SquantsHelpers._

// TODO: once the vision system is online document the coordinate system here
case class ArenaCoordinate(x: Length, y: Length, heading: Angle) {
  def relativePositionOf(coord: ArenaCoordinate): RobotCoordinate = {
    val dx = coord.x - x
    val dy = coord.y - y
    // TODO: do a 4 quadrants test to make sure this behaves
    val relativeBearing = SquantsHelpers.atan2(dy, dx) - heading
    val r = (dx.squared + dy.squared).sqrt
    RobotCoordinate(r * relativeBearing.cos, r * relativeBearing.sin)
  }
}
