package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw.{ Command, CommLayer, ReadStandardConfigSettings }
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

class SerialPortManager(portInfo: CommPortIdentifier) extends SerialPortEventListener with CommLayer {
  protected val log = LoggerFactory.getLogger(getClass)
  protected val buffer = ByteBuffer.allocate(128)
  protected val commandQueue = mutable.Queue.empty[() => Unit]
  protected var resultsHandler: Option[ByteBuffer => Unit] = None

  log.info(s"Attempting to open ${portInfo.getName}...")
  protected val port: SerialPort = portInfo.open("Robokomodo Control", SerialPortManager.TIMEOUT).asInstanceOf[SerialPort]
  port.setSerialPortParams(57600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE)
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
    log.info(s"Found RoboClaws at addresses ${connectedRoboClaws.map(_ & 0xff)}.")
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
    commandQueue.synchronized {
      resultsHandler = None
      if (commandQueue.nonEmpty) {
        commandQueue.dequeue()()  // sends the next queued command
      }
    }
    p.complete(result)
  }

  protected def buildSimpleDataHandler[R](cmd: Command[R], p: Promise[R]): (ByteBuffer => Unit) = {
    (data: ByteBuffer) => completePromise(p, cmd.parseResults(data))
  }

  protected def buildChecksummedDataHandler[R](cmd: Command[R], p: Promise[R]): (ByteBuffer => Unit) = {
    (data: ByteBuffer) =>
      if (com.github.dunmatt.roboclaw.Utilities.verifyChecksum(cmd.address, cmd.command, data)) {
        buffer.flip
        completePromise(p, cmd.parseResults(data))
      } else {
        log.warn(s"$cmd not yet: $data")  // TODO: demote this to debug, once it's clear how often it happens
      }
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
    Future {  // in case for whatever reason the motor controller doesn't get back to us, time out
      val timeout = 250
      Thread.sleep(timeout)
      completePromise(result, Failure(new Exception(s"no response in ${timeout}ms")))
    }
  }

  protected def connectsToRoboClaw(address: Byte): Boolean = {
    Try(Await.result(sendCommand(ReadStandardConfigSettings(address)), 1000 millis)).toOption.exists { config =>
      config.address == address && config.packetSerialMode
    }
  }

  protected override def serialEvent(evt: SerialPortEvent): Unit = evt.getEventType match {
    case SerialPortEvent.DATA_AVAILABLE if resultsHandler.isEmpty =>
      val errorBuffer = ByteBuffer.allocate(256)
      input.read(errorBuffer)
      val buf = (0 until errorBuffer.limit).map(errorBuffer.get(_) & 0xff).mkString(" ")
      log.warn(s"Receiving data despite no current command: $buf")
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

  // TODO: refactor this to return a future!
  def findSerialPorts(prefix: String): Map[Byte, SerialPortManager] = {
    CommPortIdentifier.getPortIdentifiers.map {
      case p: CommPortIdentifier => p  // hooray java type erasure!
    }.filter { p =>
      p.getPortType == CommPortIdentifier.PORT_SERIAL && p.getName.startsWith(prefix)
    }.flatMap { p =>
      val m = new SerialPortManager(p)
      m.connectedRoboClaws.map((_, m))
    }.toMap
  }
}
