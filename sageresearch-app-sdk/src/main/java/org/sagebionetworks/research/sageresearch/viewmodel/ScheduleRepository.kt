package org.sagebionetworks.research.sageresearch.viewmodel

import android.content.Context
import android.support.annotation.VisibleForTesting
import org.joda.time.DateTime
import org.joda.time.Days
import org.sagebionetworks.bridge.researchstack.BridgeDataProvider

import org.sagebionetworks.bridge.rest.model.ScheduledActivityListV4
import org.sagebionetworks.research.sageresearch.dao.room.EntityTypeConverters
import org.sagebionetworks.research.sageresearch.dao.room.ResearchDatabase
import org.sagebionetworks.research.sageresearch.dao.room.ScheduledActivityEntityDao
import org.sagebionetworks.research.sageresearch.util.SingletonWithParam

import rx.Observable
import rx.schedulers.Schedulers

import java.util.TreeMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

//
//  Copyright © 2018 Sage Bionetworks. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
//
// 1.  Redistributions of source code must retain the above copyright notice, this
// list of conditions and the following disclaimer.
//
// 2.  Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation and/or
// other materials provided with the distribution.
//
// 3.  Neither the name of the copyright holder(s) nor the names of any contributors
// may be used to endorse or promote products derived from this software without
// specific prior written permission. No license is granted to the trademarks of
// the copyright holders even if such marks are included in this software.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//

/**
 * The ScheduleRepository is responsible for downloading the study's schedules and saving them to the Room database
 * All @see ScheduleViewModel are required to call this class' function syncSchedules() to ensure that
 * the schedules are currently synced with bridge.
 */
open class ScheduleRepository(context: Context) {

    /**
     * Creates a singleton that takes Context as a parameter to access
     * Use ScheduleRepository.getInstance(context)
     * TODO: mdephillips 9/2/18 use dagger?
     */
    companion object : SingletonWithParam<ScheduleRepository, Context>({
        ScheduleRepository(it.applicationContext)
    })

    private val entityConverter = EntityTypeConverters()

    private val scheduleDao: ScheduledActivityEntityDao =
            ResearchDatabase.getInstance(context).scheduleDao()

    private var isSyncing = AtomicBoolean(false)
    private var isSynced = AtomicBoolean(false)

    // TODO: mdephillips 9/2/18 read this from bridge config
    /**
     * The number of days ahead we request from the server to allow for offline operation
     */
    open var cachedDaysAhead: Int = 7

    /**
     * The maximum days of schedules we can request at one time
     * ridge only allows requesting activity data in 14 day windows,
     * so we must break the request up into 14 day sections if necessary
     */
    val maxRequestDays: Int = 14

    @VisibleForTesting
    open fun studyStartDate(): DateTime? {
        return BridgeDataProvider.getInstance().participantCreatedOn
    }

    /**
     * Open for testing purposes
     * @return the current date and time
     */
    @VisibleForTesting
    open fun now(): DateTime {
        return DateTime.now()
    }

    /**
     * Force the schedules to be re-loaded from bridge.
     * This is useful when an event occurs that we know will affect the schedules on bridge, i.e. data group changes.
     */
    internal fun forceSyncSchedules() {
        if (isSyncing.get()) {
            return // we are currently syncing, no need to re-sync
        }
        isSynced.set(false)
        syncSchedules()
    }

    /**
     * Makes sure the schedules we have cached are synced with the bridge server.
     * This function only operates if we are not currently syncing or have successfully synced
     */
    fun syncSchedules() {
        if (isSynced.get() || isSyncing.get()) {
            return // we are already retrieving the study's schedules
        }

        isSyncing.set(true)

        val startDate = studyStartDate()?.withTimeAtStartOfDay() ?: return  // return if we aren't signed in yet
        val endDate = now().plusDays(cachedDaysAhead)
        val requestMap = ScheduleRepositoryHelper.buildRequestMap(startDate, endDate, maxRequestDays)

        // Use these atomic variables to track of the state of the requests
        val requestHasFailed = AtomicBoolean(false)
        val requestCounter = AtomicInteger(requestMap.keys.size)

        // Run all the requests, and join the results for the user
        for (start in requestMap.keys) {
            requestMap[start]?.let { end: DateTime ->
                BridgeDataProvider.getInstance().getActivities(start, end)
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .subscribe({ activityListV4 ->
                            // If another request previously failed we won't trigger any success callbacks
                            if (!requestHasFailed.get()) {
                                requestCounter.set(requestCounter.get() - 1)
                                if (requestCounter.get() <= 0) {
                                    isSynced.set(true)
                                    isSyncing.set(false)
                                }
                            }
                            // No matter if other requests failed, still cache a successful request
                            cacheSchedules(activityListV4)
                        }, { throwable ->
                            // Consider the sync a failure if one or more requests fail
                            if (!requestHasFailed.get()) {
                                isSynced.set(false)
                                isSyncing.set(false)
                                requestHasFailed.set(true)
                            }
                        })
            }
        }
    }

    private fun cacheSchedules(activityListV4: ScheduledActivityListV4) {
        Observable.just(scheduleDao)
                .subscribeOn(Schedulers.io())
                .subscribe({ dao ->
                    entityConverter.fromScheduledActivityListV4(activityListV4)?.let { list ->
                        if (!reconcileWithDictionary()) {
                            dao.upsert(list)
                        }
                    }
                }, {
                    // TODO: mdephillips 9/4/18 how to do logger in kotlin?
                })
    }

    // TODO: mdephillips 9/2/18 correctly reconcile the schedules instead of doing an upsert
    fun reconcileWithDictionary(): Boolean {
        return false
        // For all but the client-writable fields, the server value is completely canonical.
        // For the client-writable fields, the client value is canonical unless it is nil.
        // @see ScheduleActivityEntity.clientWritableCopy()
    }
}

object ScheduleRepositoryHelper {
    /**
     * Builds a map of requests to make to the server that is appropriately grouped to not exceed the maxRequestDays.
     * The map will have requests in whole days only so that it is easier to avoid the concern of exceeding max.
     * The map is built in reverse order, so that requests favor getting today's new data first.
     * @param start of the request
     * @param end of the request
     * @param maxRequestDays the maximum number of days that can be requested
     */
    fun buildRequestMap(start: DateTime, end: DateTime, maxRequestDays: Int): Map<DateTime, DateTime> {
        // A LinkedHashMap will ensure that the requests are made in the order that we add key/values
        val requestMap = LinkedHashMap<DateTime, DateTime>()

        val startOfDay = start.withTimeAtStartOfDay()
        val endOfDay = end.withTimeAtStartOfDay().plusDays(1)
        var endDateCounter = DateTime(endOfDay)
        var durationCounter = Days.daysBetween(startOfDay, endOfDay).days

        // Build the map in reverse order, so that requests favor getting today's new data first
        while (durationCounter > maxRequestDays) {
            val requestStartDate = endDateCounter.minusDays(maxRequestDays)

            // We subtract 1 millisecond so that the schedules returned from bridge
            // do not return schedules for the next day if scheduled at midnight inclusively
            requestMap[requestStartDate] = endDateCounter.minusMillis(1)
            endDateCounter = endDateCounter.minusDays(maxRequestDays)
            durationCounter -= maxRequestDays
        }
        requestMap[startOfDay] = endDateCounter.minusMillis(1)

        return requestMap
    }
}