/*
 * Copyright (c) 2021, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.krux.starport.system

import scala.concurrent.duration._
import scala.concurrent.Future

import akka.actor.typed.scaladsl._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.pattern.StatusReply
import com.codahale.metrics.Timer

import com.krux.starport.cli.SchedulerOptions
import com.krux.starport.config.StarportSettings
import com.krux.starport.db.record.Pipeline

object ScheduleService {

  sealed trait Msg
  case class ScheduleReq(
    options: SchedulerOptions,
    localJars: Map[String, String],
    numDispatchers: Int,
    pipelines: List[Pipeline],
    starportSetting: StarportSettings,
    scheduleTimer: Timer,
    replyTo: ActorRef[StatusReply[Int]]
  ) extends Msg

  def apply(): Behavior[Msg] = Behaviors.receive {
    case (context, r: ScheduleReq) =>
      context.spawnAnonymous(
        SchedulerActor(
          r.options,
          r.localJars,
          r.numDispatchers,
          r.pipelines,
          r.starportSetting,
          r.scheduleTimer,
          r.replyTo
        )
      )
      Behaviors.same
  }

  val system = ActorSystem(apply(), "schedule-service")

  // TODO make sure this can only be called one a time
  def schedule(
    options: SchedulerOptions,
    localJars: Map[String, String],
    numDispatchers: Int,
    pipelines: List[Pipeline],
    starportSetting: StarportSettings,
    scheduleTimer: Timer
  ): Future[Int] = {
    import akka.actor.typed.scaladsl.AskPattern._
    system.askWithStatus[Int](ref =>
      ScheduleReq(
        options,
        localJars,
        numDispatchers,
        pipelines,
        starportSetting,
        scheduleTimer,
        ref
      )
    )(45.minutes, system.scheduler)

  }

  def terminate() = system.terminate

}
