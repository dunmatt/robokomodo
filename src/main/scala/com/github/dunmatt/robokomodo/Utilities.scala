package com.github.dunmatt.robokomodo

import com.github.dunmatt.roboclaw.Command
import org.slf4j.Logger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure

object Utilities {
  def readSetRead[T, U]( port: SerialPortManager
                       , readCmd: Command[T]
                       , test: T=>Boolean
                       , setCmd: Command[U]
                       , log: Option[Logger] = None): Future[T] = {
    port.sendCommand(readCmd).flatMap{ v =>
      if (test(v)) {
        Future.successful(v)
      } else {
        log.foreach(_.debug(s"Unexpected response $v to $readCmd, sending $setCmd to correct."))
        port.sendCommand(setCmd).flatMap{ _ =>
          port.sendCommand(readCmd).filter(test).andThen{ case Failure(f) =>
            log.foreach(_.error(s"Couldn't confirm valid settings via $readCmd.  The problem is $f"))
          }
        }
      }
    }
  }

  def readSetRead[T, U]( port: SerialPortManager
                       , readCmd: Command[T]
                       , test: T=>Boolean
                       , setCmds: Seq[Command[U]]
                       , log: Option[Logger]): Future[T] = {
    port.sendCommand(readCmd).flatMap{ v =>
      if (test(v)) {
        Future.successful(v)
      } else {
        log.foreach(_.debug(s"Unexpected response $v to $readCmd, sending $setCmds to correct."))
        Future.sequence(setCmds.map(port.sendCommand)).flatMap{ _ =>
          port.sendCommand(readCmd).filter(test).andThen{ case Failure(f) =>
            log.foreach(_.error(s"Couldn't confirm valid settings via $readCmd.  The problem is $f"))
          }
        }
      }
    }
  }
}
