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

package org.sagebionetworks.bridge.android.persistence;

import java.util.Map;

import org.sagebionetworks.bridge.rest.RestUtils;
import org.sagebionetworks.bridge.rest.model.Activity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;

public class PersistenceUtils {
    public static ScheduledActivity databaseActivityToServerActivity(
            ScheduledActivityEntity databaseActivity) {
        // Basic attributes.
        ScheduledActivity serverActivity = new ScheduledActivity();
        serverActivity.setGuid(databaseActivity.getGuid());
        serverActivity.setExpiresOn(databaseActivity.getExpiresOn());
        serverActivity.setFinishedOn(databaseActivity.getFinishedOn());
        serverActivity.setPersistent(databaseActivity.getPersistent());
        serverActivity.setSchedulePlanGuid(databaseActivity.getSchedulePlanGuid());
        serverActivity.setScheduledOn(databaseActivity.getScheduledOn());
        serverActivity.setStartedOn(databaseActivity.getStartedOn());
        serverActivity.setStatus(databaseActivity.getStatus());

        // There are some nested attributes and lists that Room doesn't handle very well, so we
        // store them as JSON. Convert these back into POJOs.
        serverActivity.setActivity(RestUtils.GSON.fromJson(databaseActivity.getActivityJson(),
                Activity.class));
        serverActivity.setClientData(RestUtils.GSON.fromJson(databaseActivity.getClientDataJson(),
                Map.class));

        // Server activity has no need for dirty flag or sync time.

        return serverActivity;
    }

    public static ScheduledActivityEntity serverActivityToDatabaseActivity(
            ScheduledActivity serverActivity) {
        // Basic attributes.
        ScheduledActivityEntity databaseActivity = new ScheduledActivityEntity();
        databaseActivity.setGuid(serverActivity.getGuid());
        databaseActivity.setExpiresOn(serverActivity.getExpiresOn());
        databaseActivity.setFinishedOn(serverActivity.getFinishedOn());
        databaseActivity.setPersistent(serverActivity.isPersistent());
        databaseActivity.setSchedulePlanGuid(serverActivity.getSchedulePlanGuid());
        databaseActivity.setScheduledOn(serverActivity.getScheduledOn());
        databaseActivity.setStartedOn(serverActivity.getStartedOn());
        databaseActivity.setStatus(serverActivity.getStatus());

        // There are some nested attributes and lists that Room doesn't handle very well. Convert
        // these to JSON.
        databaseActivity.setActivityJson(RestUtils.GSON.toJson(serverActivity.getActivity()));
        databaseActivity.setClientDataJson(RestUtils.GSON.toJson(serverActivity.getClientData()));

        // Scheduled activities pulled from the server are generally not dirty and we just updated.
        databaseActivity.setDirty(false);
        databaseActivity.setLastSyncedOn(System.currentTimeMillis());

        return databaseActivity;
    }
}
