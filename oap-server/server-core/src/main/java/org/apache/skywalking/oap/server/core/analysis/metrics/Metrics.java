/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.analysis.metrics;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Metrics represents the statistic data, which analysis by OAL script or hard code. It has the lifecycle controlled by
 * TTL(time to live).
 */
public abstract class Metrics extends StreamData implements StorageData {

    public static final String TIME_BUCKET = "time_bucket";
    public static final String ENTITY_ID = "entity_id";

    /**
     * Time attribute
     */
    @Getter
    @Setter
    @Column(columnName = TIME_BUCKET)
    private long timeBucket;

    /**
     * Time in the cache, only work when MetricsPersistentWorker#enableDatabaseSession == true.
     */
    @Getter
    private long survivalTime = 0L;

    /**
     * Merge the given metrics instance, these two must be the same metrics type.
     *
     * @param metrics to be merged
     */
    public abstract void combine(Metrics metrics);

    /**
     * Calculate the metrics final value when required.
     */
    public abstract void calculate();

    /**
     * Downsampling the metrics to hour precision.
     *
     * @return the metrics in hour precision in the clone mode.
     */
    public abstract Metrics toHour();

    /**
     * Downsampling the metrics to day precision.
     *
     * @return the metrics in day precision in the clone mode.
     */
    public abstract Metrics toDay();

    /**
     * Downsampling the metrics to month precision.
     *
     * @return the metrics in month precision in the clone mode.
     */
    public abstract Metrics toMonth();

    /**
     * Extend the {@link #survivalTime}
     *
     * @param value to extend
     */
    public void extendSurvivalTime(long value) {
        survivalTime += value;
    }

    public long toTimeBucketInHour() {
        if (isMinuteBucket()) {
            return timeBucket / 100;
        } else {
            throw new IllegalStateException("Current time bucket is not in minute dimensionality");
        }
    }

    public long toTimeBucketInDay() {
        if (isMinuteBucket()) {
            return timeBucket / 10000;
        } else if (isHourBucket()) {
            return timeBucket / 100;
        } else {
            throw new IllegalStateException("Current time bucket is not in minute dimensionality");
        }
    }

    public long toTimeBucketInMonth() {
        if (isMinuteBucket()) {
            return timeBucket / 1000000;
        } else if (isHourBucket()) {
            return timeBucket / 10000;
        } else if (isDayBucket()) {
            return timeBucket / 100;
        } else {
            throw new IllegalStateException("Current time bucket is not in minute dimensionality");
        }
    }

    /**
     * Always get the duration for this time bucket in minute.
     */
    protected long getDurationInMinute() {
        if (isMinuteBucket()) {
            return 1;
        } else if (isHourBucket()) {
            return 60;
        } else if (isDayBucket()) {
            return 24 * 60;
        } else {
            /*
             * In month time bucket status.
             * Usually after {@link #toTimeBucketInMonth()} called.
             */
            DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyyMM");
            int dayOfMonth = formatter.parseLocalDate(timeBucket + "").getDayOfMonth();
            return dayOfMonth * 24 * 60;
        }
    }

    /**
     * timeBucket in minute 201809120511 min 100000000000 max 999999999999
     */
    private boolean isMinuteBucket() {
        return timeBucket < 999999999999L && timeBucket > 100000000000L;
    }

    private boolean isHourBucket() {
        return timeBucket < 9999999999L && timeBucket > 1000000000L;
    }

    private boolean isDayBucket() {
        return timeBucket < 99999999L && timeBucket > 10000000L;
    }
}
