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

import java.lang.reflect.Type;
import java.util.List;

import com.google.gson.reflect.TypeToken;
import rx.Completable;
import rx.Observable;

import org.sagebionetworks.bridge.android.persistence.ScheduledActivityEntity;
import org.sagebionetworks.bridge.rest.RestUtils;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;

public class UpdateActivitiesJob extends BridgeJob {
    private static final Type ACTIVITY_LIST_TYPE = new TypeToken<List<ScheduledActivity>>(){}
        .getType();

    private transient List<ScheduledActivity> activityList;
    private final String serializedActivityList;

    public UpdateActivitiesJob(List<ScheduledActivity> activityList) {
        this.activityList = activityList;

        // Job is serializable so that JobPriorityQueue can persist the job status. However,
        // ScheduledActivity is not serializable. To make this serializable, we store it as JSON.
        this.serializedActivityList = RestUtils.GSON.toJson(activityList);
    }

    private List<ScheduledActivity> getActivityList() {
        if (activityList == null) {
            activityList = RestUtils.GSON.fromJson(serializedActivityList, ACTIVITY_LIST_TYPE);
        }
        return activityList;
    }

    @Override
    public void onRun() {
        // Defer to activity manager to update activities.
        Completable completable = getActivityManagerV2().updateRemoteScheduledActivities(
                getActivityList());

        // Queued jobs are run asynchronously. The pattern here is that when onRun completes, the
        // job is complete, so block on completable completion.
        completable.await();
    }
}
