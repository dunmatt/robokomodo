package com.github.dunmatt.robokomodo

import squants.space.AngleConversions._

class Robot() {
  val rightMotor = new Motor(0x86.toByte, true, -60 degrees)
  val leftMotor = new Motor(0x86.toByte, false,  60 degrees)
  val rearMotor = new Motor(0x87.toByte, false, 180 degrees)

  def getMotor(address: Byte, channel1: Boolean): Option[Motor] = (address, channel1) match {
    case (rightMotor.controllerAddress, rightMotor.channel1) => Some(rightMotor)
    case (leftMotor.controllerAddress, leftMotor.channel1) => Some(leftMotor)
    case (rearMotor.controllerAddress, rearMotor.channel1) => Some(rearMotor)
    case _ => None   // TODO: log a warning
  }
}
