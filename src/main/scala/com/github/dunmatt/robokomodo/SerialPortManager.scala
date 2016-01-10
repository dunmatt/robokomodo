package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw.{ Command, ReadFirmwareVersion, Utilities }
import gnu.io._
import java.nio.ByteBuffer
import java.nio.channels.Channels
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Try }

class SerialPortManager(portInfo: CommPortIdentifier) extends SerialPortEventListener {
  protected val log = LoggerFactory.getLogger(getClass)
  protected val buffer = ByteBuffer.allocate(128)
  protected val commandQueue = mutable.Queue.empty[() => Unit]
  protected var resultsHandler: Option[ByteBuffer => Unit] = None

  log.info(s"Attempting to open ${portInfo.getName}...")
  protected val port: SerialPort = portInfo.open("Robokomodo Control", SerialPortManager.TIMEOUT).asInstanceOf[SerialPort]
  protected val input = Channels.newChannel(port.getInputStream)
  protected val output = Channels.newChannel(port.getOutputStream)
  port.addEventListener(this)
  port.notifyOnDataAvailable(true)
  log.info(s"${portInfo.getName} open, looking for RoboClaws...")
  val connectedRoboClaws = (0x80 until 0x88).map(_.toByte).filter(connectsToRoboClaw).toSet
  if (connectedRoboClaws.isEmpty) {
    log.info(s"No RoboClaws found connected to ${portInfo.getName}.")
    disconnect
  } else {
    log.info(s"Found RoboClaws at addresses ${connectedRoboClaws.map(_.toInt)}.")
  }

  def disconnect: Unit = {
    port.removeEventListener
    port.close
    input.close
    output.close
    log.info(s"Disconnected from ${portInfo.getName}")
  }

  def sendCommand[R](cmd: Command[R]): Future[R] = {
    val result = Promise[R]
    commandQueue.synchronized {
      if (commandQueue.isEmpty && resultsHandler.isEmpty) {
        actuallySendCommand(cmd, result)
      } else {
        commandQueue.enqueue( () => actuallySendCommand(cmd, result))
      }
    }
    result.future
  }

  protected def completePromise[R](p: Promise[R], result: Try[R]): Unit = {
    resultsHandler = None
    commandQueue.synchronized {
      commandQueue.dequeue()()  // sends the next queued command
    }
    p.complete(result)
  }

  protected def buildSimpleDataHandler[R](cmd: Command[R], p: Promise[R]): (ByteBuffer => Unit) = {
    def handler(data: ByteBuffer): Unit = {
      completePromise(p, cmd.parseResults(data))
    }
    handler
  }

  protected def buildChecksummedDataHandler[R](cmd: Command[R], p: Promise[R]): (ByteBuffer => Unit) = {
    def handler(data: ByteBuffer): Unit = {
      if (Utilities.verifyChecksum(cmd.address, cmd.command, data)) {
        completePromise(p, cmd.parseResults(data))
      } else {
        log.warn(s"$cmd not yet: $data")  // TODO: demote this to debug, once it's clear how often it happens
        // (0 until 29).foreach{ i => println(data.get(i)) }
        // println("===")
        // println(cmd.parseResults(data))
      }
    }
    handler
  }

  protected def actuallySendCommand[R](cmd: Command[R], result: Promise[R]): Unit = {
    if (cmd.expectsCrc) {
      resultsHandler = Some(buildChecksummedDataHandler(cmd, result))
    } else {
      resultsHandler = Some(buildSimpleDataHandler(cmd, result))
    }
    cmd.populateByteBuffer(buffer)
    output.write(buffer)
    buffer.clear
  }

  protected def connectsToRoboClaw(address: Byte): Boolean = {
    Try(Await.result(sendCommand(ReadFirmwareVersion(address)), 100 millis)).isSuccess
  }

  override def serialEvent(evt: SerialPortEvent): Unit = evt.getEventType match {
    case SerialPortEvent.DATA_AVAILABLE if resultsHandler.isEmpty =>
      log.warn("Receiving data despite no current command.")
    case SerialPortEvent.DATA_AVAILABLE =>
      input.read(buffer)
      resultsHandler.foreach(_(buffer))
    case SerialPortEvent.OUTPUT_BUFFER_EMPTY => Unit
    case _ => log.debug(s"Non data-available serial port event: $evt.")
  }
}


object SerialPortManager {
  val TIMEOUT = 2000

  def findAnything = findSerialPorts("/dev/cu.")

  def findRoboClaws = findSerialPorts("/dev/cu.usbmodem")

  def findXbees = findSerialPorts("/dev/cu.usbserial")

  def findSerialPorts(prefix: String): Set[SerialPortManager] = {
    CommPortIdentifier.getPortIdentifiers.map {
      case p: CommPortIdentifier => p  // hooray java type erasure!
    }.filter { p =>
      p.getPortType == CommPortIdentifier.PORT_SERIAL && p.getName.startsWith(prefix)
    }.map { p =>
      new SerialPortManager(p)
    }.filter {
      _.connectedRoboClaws.nonEmpty
    }.toSet
  }
}
