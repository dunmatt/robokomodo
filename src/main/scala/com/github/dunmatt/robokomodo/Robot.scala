package com.github.dunmatt.robokomodo

import squants.space.AngleConversions._

class Robot() {
  val rightMotor = new Motor(0x86.toByte, true, -60 degrees)
  val leftMotor = new Motor(0x86.toByte, false,  60 degrees)
  val rearMotor = new Motor(0x87.toByte, false, 180 degrees)
}
