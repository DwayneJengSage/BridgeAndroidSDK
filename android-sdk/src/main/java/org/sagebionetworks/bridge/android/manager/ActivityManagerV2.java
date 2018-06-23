/*
 *    Copyright 2018 Sage Bionetworks
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.sagebionetworks.bridge.android.manager;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.android.util.retrofit.RxUtils.toBodySingle;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Singleton;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import com.birbit.android.jobqueue.JobManager;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Completable;
import rx.Observable;

import org.sagebionetworks.bridge.android.jobqueue.GetActivitiesByDateJob;
import org.sagebionetworks.bridge.android.jobqueue.UpdateActivitiesJob;
import org.sagebionetworks.bridge.android.persistence.PersistenceUtils;
import org.sagebionetworks.bridge.android.persistence.ScheduledActivityDAO;
import org.sagebionetworks.bridge.android.persistence.ScheduledActivityEntity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;

@AnyThread
@Singleton
public class ActivityManagerV2 {
    private static final Logger LOG = LoggerFactory.getLogger(ActivityManagerV2.class);

    // Cache activities for 24 hours.
    private static final long CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000;

    @NonNull
    private final AtomicReference<AuthenticationManager.AuthStateHolder>
            authStateHolderAtomicReference;

    @NonNull
    private final JobManager jobManager;

    @NonNull
    private final ScheduledActivityDAO scheduledActivityDAO;

    public ActivityManagerV2(@NonNull AuthenticationManager authManager,
            @NonNull JobManager jobManager, @NonNull ScheduledActivityDAO scheduledActivityDAO) {
        checkNotNull(authManager);
        checkNotNull(jobManager);
        checkNotNull(scheduledActivityDAO);

        this.authStateHolderAtomicReference = authManager.getAuthStateReference();
        this.jobManager = jobManager;
        this.scheduledActivityDAO = scheduledActivityDAO;
    }

    public Observable<ScheduledActivity> getActivities(@NonNull DateTime startTime,
            @NonNull DateTime endTime) {
        DateTime normalizedEndTime;
        try {
            normalizedEndTime = normalizeEndTime(startTime, endTime);
        } catch (Exception e) {
            // in case we do something wrong with JodaTime
            return Observable.error(e);
        }

        return Observable.fromCallable(() -> scheduledActivityDAO
                .getScheduledActivitiesForDates(startTime.getMillis(), normalizedEndTime.getMillis()))
                .flatMap(activityList -> {
                    if (activityList == null || activityList.isEmpty()) {
                        // Cache has no elements. Call server and return an in-place Observable.
                        return downloadAndCacheScheduledActivitiesForDates(startTime,
                                normalizedEndTime);
                    } else {
                        // Check cache expiration.
                        long now = System.currentTimeMillis();
                        boolean hasExpired = false;
                        for (ScheduledActivityEntity oneActivity : activityList) {
                            if (oneActivity.getLastSyncedOn() + CACHE_TTL_MILLIS < now) {
                                hasExpired = true;
                                break;
                            }
                        }

                        // If we have expired cache elements, kick off a job to update the cache in
                        // the background.
                        if (hasExpired) {
                            jobManager.addJobInBackground(new GetActivitiesByDateJob(startTime,
                                    normalizedEndTime));
                        }

                        // Return the cached elements we have, in case we don't have network
                        // connectivity or the call is otherwise too expensive.
                        return Observable.from(activityList);
                    }
                }).map(PersistenceUtils::databaseActivityToServerActivity);
    }

    public Observable<ScheduledActivityEntity> downloadAndCacheScheduledActivitiesForDates(
            @NonNull DateTime startTime, @NonNull DateTime normalizedEndTime) {
        // todo pagination
        return toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                .getScheduledActivitiesByDateRange(startTime, normalizedEndTime))
                .toObservable()
                .flatMap(list -> Observable.from(list.getItems()))
                .map(activity -> {
                    ScheduledActivityEntity activityEntity = PersistenceUtils
                            .serverActivityToDatabaseActivity(activity);
                    scheduledActivityDAO.writeScheduledActivities(activityEntity);
                    return activityEntity;
                });
    }

    private static DateTime normalizeEndTime(@NonNull DateTime startTime,
            @NonNull DateTime endTime) {
        int startOffset = startTime.getZone().getOffset(startTime);
        int endOffset = endTime.getZone().getOffset(endTime);
        if (startOffset != endOffset) {
            DateTime normalizedEndTime = endTime.toDateTime(DateTimeZone.forOffsetMillis(startOffset));
            LOG.warn("Correcting for mismatched offset. startTime: {}, endTime: {}, newEndTime: {}", startTime,
                    endTime, normalizedEndTime);
            return normalizedEndTime;
        } else {
            return endTime;
        }
    }

    public Completable updateActivities(@NonNull List<ScheduledActivity> activityList) {
        return Completable.fromAction(() -> {
            List<ScheduledActivityEntity> entityList = Lists.transform(activityList,
                    PersistenceUtils::serverActivityToDatabaseActivity);
            scheduledActivityDAO.writeScheduledActivities(Iterables.toArray(entityList,
                    ScheduledActivityEntity.class));
        }).mergeWith(Completable.fromAction(() -> jobManager.addJobInBackground(
                new UpdateActivitiesJob(activityList))));
    }

    public Completable updateRemoteScheduledActivities(
            @NonNull List<ScheduledActivity> activityList) {
        return toBodySingle(authStateHolderAtomicReference.get().forConsentedUsersApi
                .updateScheduledActivities(activityList)).toCompletable();
    }
}
