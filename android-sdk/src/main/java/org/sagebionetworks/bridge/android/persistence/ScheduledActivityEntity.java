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

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.rest.model.ScheduleStatus;

@Entity
public class ScheduledActivityEntity {
    @PrimaryKey
    private String guid;

    private String activityJson;
    private String clientDataJson;
    private boolean dirty;
    private DateTime expiresOn;
    private DateTime finishedOn;
    private long lastSyncedOn;
    private Boolean persistent;
    private String schedulePlanGuid;
    private DateTime scheduledOn;
    private DateTime startedOn;
    private ScheduleStatus status;

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getActivityJson() {
        return activityJson;
    }

    public void setActivityJson(String activityJson) {
        this.activityJson = activityJson;
    }

    public String getClientDataJson() {
        return clientDataJson;
    }

    public void setClientDataJson(String clientDataJson) {
        this.clientDataJson = clientDataJson;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public DateTime getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(DateTime expiresOn) {
        this.expiresOn = expiresOn;
    }

    public DateTime getFinishedOn() {
        return finishedOn;
    }

    public void setFinishedOn(DateTime finishedOn) {
        this.finishedOn = finishedOn;
    }

    public long getLastSyncedOn() {
        return lastSyncedOn;
    }

    public void setLastSyncedOn(long lastSyncedOn) {
        this.lastSyncedOn = lastSyncedOn;
    }

    public Boolean getPersistent() {
        return persistent;
    }

    public void setPersistent(Boolean persistent) {
        this.persistent = persistent;
    }

    public String getSchedulePlanGuid() {
        return schedulePlanGuid;
    }

    public void setSchedulePlanGuid(String schedulePlanGuid) {
        this.schedulePlanGuid = schedulePlanGuid;
    }

    public DateTime getScheduledOn() {
        return scheduledOn;
    }

    public void setScheduledOn(DateTime scheduledOn) {
        this.scheduledOn = scheduledOn;
    }

    public DateTime getStartedOn() {
        return startedOn;
    }

    public void setStartedOn(DateTime startedOn) {
        this.startedOn = startedOn;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public void setStatus(ScheduleStatus status) {
        this.status = status;
    }
}
