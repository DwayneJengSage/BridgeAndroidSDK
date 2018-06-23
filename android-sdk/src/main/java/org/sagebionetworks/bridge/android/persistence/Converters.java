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

import android.arch.persistence.room.TypeConverter;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.rest.model.ScheduleStatus;

public class Converters {
    @TypeConverter
    public static String dateTimeToString(DateTime dateTime) {
        return dateTime.toString();
    }

    @TypeConverter
    public static DateTime stringToDateTime(String str) {
        return DateTime.parse(str);
    }

    @TypeConverter
    public static String scheduleStatusToString(ScheduleStatus scheduleStatus) {
        return scheduleStatus.name();
    }

    @TypeConverter
    public static ScheduleStatus stringToScheduleStatus(String str) {
        return ScheduleStatus.valueOf(str);
    }
}
