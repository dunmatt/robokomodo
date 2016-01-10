package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw.{ Command, ReadFirmwareVersion, Utilities }
import gnu.io._
import java.nio.ByteBuffer
import java.nio.channels.Channels
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Try }

class SerialPortManager(portInfo: CommPortIdentifier) extends SerialPortEventListener {
  val log = LoggerFactory.getLogger(getClass)

  log.info(s"Attempting to open ${portInfo.getName}...")
  protected val port: SerialPort = portInfo.open("Robokomodo Control", SerialPortManager.TIMEOUT).asInstanceOf[SerialPort]
  port.addEventListener(this)
  port.notifyOnDataAvailable(true)
  log.info(s"${portInfo.getName} open.")

  protected val input = Channels.newChannel(port.getInputStream)

  protected val output = Channels.newChannel(port.getOutputStream)

  def disconnect: Unit = {
    port.removeEventListener
    port.close
    input.close
    output.close
    log.info(s"Disconnected from ${portInfo.getName}")
  }

  protected val buffer = ByteBuffer.allocate(128)
  protected var resultsHandler: Option[ByteBuffer => Unit] = None

  protected def buildSimpleDataHandler[R](cmd: Command[R], p: Promise[R]): (ByteBuffer => Unit) = {
    def handler(data: ByteBuffer): Unit = {
      resultsHandler = None
      p.complete(cmd.parseResults(data))
    }
    handler
  }

  protected def buildChecksummedDataHandler[R](cmd: Command[R], p: Promise[R]): (ByteBuffer => Unit) = {
    def handler(data: ByteBuffer): Unit = {
      if (Utilities.verifyChecksum(cmd.address, cmd.command, data)) {
        resultsHandler = None
        p.complete(cmd.parseResults(data))
      } else {
        println(s"$cmd not yet: $data")
        (0 until 29).foreach{ i => println(data.get(i)) }
        println("===")
        println(cmd.parseResults(data))
      }
    }
    handler
  }

  def sendCommand[R](cmd: Command[R]): Future[R] = {
    // TODO: potentially enqueue this command
    val result = Promise[R]
    if (cmd.expectsCrc) {
      resultsHandler = Some(buildChecksummedDataHandler(cmd, result))
    } else {
      resultsHandler = Some(buildSimpleDataHandler(cmd, result))
    }
    cmd.populateByteBuffer(buffer)
    output.write(buffer)
    buffer.clear
    result.future
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

  def connectedToRoboClaw: Boolean = {
    // TODO: scan through all possible addresses
    Await.result(sendCommand(ReadFirmwareVersion(0x86.toByte)).map { s =>
      println(s)  // TODO: take this out
      s.nonEmpty
    }, 500 millis)
  }
}


object SerialPortManager {
  val TIMEOUT = 2000

  def findAnything = findSerialPorts("/dev/cu.")

  def findRoboClaws = findSerialPorts("/dev/cu.usbmodem")

  def findXbees = findSerialPorts("/dev/cu.usbserial")

  def findSerialPorts(prefix: String): Seq[SerialPortManager] = {
    CommPortIdentifier.getPortIdentifiers.map {
      case p: CommPortIdentifier => p  // hooray java type erasure!
    }.filter { p =>
      p.getPortType == CommPortIdentifier.PORT_SERIAL && p.getName.startsWith(prefix)
    }.map { p =>
      new SerialPortManager(p)
    }.filter {
      _.connectedToRoboClaw
    }.toSeq
  }
}
