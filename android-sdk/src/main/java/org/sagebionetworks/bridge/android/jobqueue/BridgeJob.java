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

import javax.inject.Inject;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.android.manager.ActivityManagerV2;
import org.sagebionetworks.bridge.rest.exceptions.BridgeSDKException;

public abstract class BridgeJob extends Job {
    private static final Logger LOG = LoggerFactory.getLogger(BridgeJob.class);

    private static final long BACKOFF_DELAY = 100;
    private static final int MAX_TRIES = 5;

    // As per sample code, priority 500 is considered medium priority.
    private static final int PRIORITY = 500;

    @Inject
    private ActivityManagerV2 activityManagerV2;

    protected BridgeJob() {
        super(new Params(PRIORITY).requireNetwork().persist());
    }

    protected ActivityManagerV2 getActivityManagerV2() {
        return activityManagerV2;
    }

    @Override
    protected int getRetryLimit() {
        return MAX_TRIES;
    }

    @Override
    public void onAdded() {
        // Job has been scheduled but not executed yet. Nothing to do yet.
    }

    @Override
    protected void onCancel(int cancelReason, @Nullable Throwable throwable) {
        LOG.error("Error running async job, job=" + this.getClass().getName() + ", cancelReason=" +
                cancelReason + ", errorClass=" +
                (throwable != null ? throwable.getClass().getName() : "null") + ", message=" +
                (throwable != null ? throwable.getMessage() : "null"));
    }

    @Override
    protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount,
            int maxRunCount) {
        if (throwable instanceof BridgeSDKException) {
            int statusCode = ((BridgeSDKException) throwable).getStatusCode();
            if (statusCode >= 400 && statusCode <= 499) {
                // Determinisitic failure from the server. Do not retry.
                return RetryConstraint.CANCEL;
            }
        }

        // In all other cases, we should retry, up to the limit, with exponential backoff.
        return RetryConstraint.createExponentialBackoff(runCount, BACKOFF_DELAY);
    }
}
