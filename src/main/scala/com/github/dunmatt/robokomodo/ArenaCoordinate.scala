package com.github.dunmatt.robokomodo

import squants.space.{ Angle, Length }
import SquantsHelpers._

// TODO: once the vision system is online document the coordinate system here
case class ArenaCoordinate(x: Length, y: Length, heading: Angle) {
  def relativePositionOf(coord: ArenaCoordinate): RobotCoordinate = {
    // TODO: do a 4 quadrants test to make sure this behaves
    val relativeBearing = SquantsHelpers.atan2(coord.y - y, coord.x - x) - heading
    val r = distanceTo(coord)
    RobotCoordinate(r * relativeBearing.cos, r * relativeBearing.sin)
  }

  def distanceTo(coord: ArenaCoordinate): Length = {
    ((coord.x - x).squared + (coord.y - y).squared).sqrt
  }
}
