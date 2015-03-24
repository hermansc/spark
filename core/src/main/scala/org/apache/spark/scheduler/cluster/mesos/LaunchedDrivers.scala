/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.scheduler.cluster.mesos

import scala.collection.mutable
import org.apache.mesos.Protos.SlaveID

/**
 * Tracks all the launched or running drivers in the Mesos cluster scheduler.
 * @param state Persistence engine to store state.
 */
private[mesos] class LaunchedDrivers(state: ClusterPersistenceEngine) {
  private val drivers = new mutable.HashMap[String, ClusterTaskState]

  // Holds the list of tasks that needs to reconciliation from the master.
  // All states that are loaded after failover are added here.
  val pendingRecover = new mutable.HashMap[String, SlaveID]

  initialize()

  def initialize() {
    state.fetchAll[ClusterTaskState]().foreach {
      case state =>
        drivers(state.taskId.getValue) = state
        pendingRecover(state.taskId.getValue) = state.slaveId
    }
  }

  def get(submissionId: String) = drivers(submissionId)

  def states: Iterable[ClusterTaskState] = {
    drivers.values.map(_.copy()).toList
  }

  def contains(submissionId: String): Boolean = drivers.contains(submissionId)

  def remove(submissionId: String): Option[ClusterTaskState] = {
    if (pendingRecover.contains(submissionId)) {
      pendingRecover.remove(submissionId)
    }

    val removedState = drivers.remove(submissionId)
    state.expunge(submissionId)
    removedState
  }

  def set(submissionId: String, newState: ClusterTaskState) {
    if (pendingRecover.contains(newState.taskId.getValue)) {
      pendingRecover.remove(newState.taskId.getValue)
    }
    drivers(submissionId) = newState
    state.persist(submissionId, newState)
  }

  def isEmpty = drivers.isEmpty

}