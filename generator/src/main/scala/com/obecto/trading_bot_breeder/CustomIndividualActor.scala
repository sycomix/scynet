package com.obecto.trading_bot_breeder

import com.obecto.gattakka.{Individual}
import com.obecto.gattakka.genetics.{Genome}
import com.obecto.gattakka.messages.individual.{Initialize}
import java.io.{IOException, File, PrintWriter}
import java.nio.file._
import java.security.{MessageDigest}
import scala.sys.process._
import spray.json._
import Math.{abs}

class CustomIndividualActor(genome: Genome) extends Individual(genome) {
  // import context.dispatcher

  var process: scala.sys.process.Process = null
  var stopping = false
  val strategy = mapGenomeToStringStrategy()
  // val shortHash = BigInt(strategy.hashCode()).abs.toString(16).padTo(6, '0').substring(0, 6)
  val shortHash = MessageDigest.getInstance("MD5").digest(strategy.getBytes()).map("%02x".format(_)).mkString.substring(0, 10)
  var startTime = 0l

  override def customReceive = {
    case Initialize(data) =>
      startProcess()
  }

  override def postStop(): Unit = {
    if (process != null) {
      stopping = true
      process.destroy()
    }
  }

  def dispatchFitness(fitness: Double, displayScore: Double, iterations: Int): Unit = {
    super.dispatchFitness(fitness)
    if (!fitness.isNaN && fitness != 0.0) {
      val endTime = System.currentTimeMillis / 1000
      val sign = if (displayScore > 0) 1 else 0
      printToFile(new File(f"../results/running-${shortHash}/genome.txt")) { p =>
        p.println(s"fitness = $fitness")
        p.println(s"score = $displayScore")
        p.println(s"iterations = $iterations")
        p.println(s"time = ${endTime - startTime}s")
        p.println(s"hash = $shortHash")
        p.println(s"chromosome = $strategy")
      }
    }
  }

  private def printToFile(f: File)(op: PrintWriter => Unit): Unit = {
    val p = new PrintWriter(f)
    try { op(p) } finally { p.close() }
  }

  private def startProcess(): Unit = {
    println(f"Started $shortHash")
    startTime = System.currentTimeMillis / 1000
    var errorText = ""
    var shouldPrintError = false

    val io = new ProcessIO(
      in => {
        try {
          in.write(strategy.toCharArray.map(_.toByte))
          in.close()
        } catch {
          case _: IOException => println("Failed to write to input")
        }
      },
      out => {
        val resultLines = scala.io.Source.fromInputStream(out).getLines.toList
        val resultMap = resultLines.view.map({line =>
          val parts = line.split("=", 2)
          if (parts.length == 2) Some((parts(0).trim, parts(1).trim))
          else None
        }).flatten.toMap
        out.close()

        if (resultMap.contains("score")) {
          val score = resultMap.getOrElse("score", "0").toDouble
          val displayScore = resultMap.getOrElse("display_score", "0").toDouble
          val iterations = resultMap.getOrElse("iterations", "-1").toInt
          dispatchFitness(score, displayScore, iterations)

          val sign = if (displayScore > 0) 1 else 0
          val scoreStr = f"$sign${abs(displayScore)%07.2f}"
          val oldPath: Path = Paths.get(s"../results/running-$shortHash")
          val newPath: Path = Paths.get(s"../results/$scoreStr-$shortHash")
          Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING)

          val endTime = System.currentTimeMillis / 1000
          val duration = endTime - startTime
          println(f"Finished $shortHash for $duration seconds")

        } else if (!stopping) {
          dispatchFitness(Double.NaN, Double.NaN, -1)
          if (errorText == "") {
            shouldPrintError = true
          } else {
            printError(strategy, errorText, shortHash)
          }
        }
      },
      err => {
        val errorText = scala.io.Source.fromInputStream(err).mkString
        err.close()
        println(errorText)

        if(shouldPrintError) {
          printError(strategy, errorText, shortHash)
        }
        
      })

    process = Process(Main.commandToRun, new File("../")) run io
  }

  private def printError(strategy: String, errorText: String, shortHash: String): Unit = {
    val oldPath: Path = Paths.get(s"../results/running-$shortHash")
    val newPath: Path = Paths.get(s"../results/error-$shortHash")
    Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING)

    printToFile(new File(f"$newPath/error.txt")) { p =>
      p.println(s"chromosome = $strategy")
      p.println(errorText)
    }

    val endTime = System.currentTimeMillis / 1000
    val duration = endTime - startTime
    println(f"Errored $shortHash for $duration seconds")
  }

  private def mapGenomeToStringStrategy(): String = {
    import DefaultJsonProtocol._
    import Converter.AnyJsonProtocol._

    Converter.serialize(genome).toJson.compactPrint
  }
}
