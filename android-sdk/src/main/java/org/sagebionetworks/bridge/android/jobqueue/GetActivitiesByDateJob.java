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

package org.sagebionetworks.bridge.android.jobqueue;

import org.joda.time.DateTime;
import rx.Observable;

import org.sagebionetworks.bridge.android.persistence.ScheduledActivityEntity;

public class GetActivitiesByDateJob extends BridgeJob {
    private final DateTime startTime;
    private final DateTime endTime;

    public GetActivitiesByDateJob(DateTime startTime, DateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    public void onRun() {
        // Defer to activity manager to get and cache activities.
        Observable<ScheduledActivityEntity> observable = getActivityManagerV2()
                .downloadAndCacheScheduledActivitiesForDates(startTime, endTime);

        // Queued jobs are run asynchronously. The pattern here is that when onRun completes, the
        // job is complete, so block on observable completion.
        observable.toBlocking().lastOrDefault(null);
    }
}
