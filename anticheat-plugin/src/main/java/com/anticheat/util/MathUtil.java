package com.anticheat.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MathUtil {

    /**
     * GCD of two floating-point values using the Euclidean algorithm with epsilon guard.
     */
    public static double gcd(double a, double b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b > 1e-10) {
            double t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    /**
     * Population standard deviation of an array of doubles.
     */
    public static double standardDeviation(double[] values) {
        if (values == null || values.length == 0) return 0;
        double mean = Arrays.stream(values).average().orElse(0);
        double variance = Arrays.stream(values)
            .map(v -> Math.pow(v - mean, 2))
            .average().orElse(0);
        return Math.sqrt(variance);
    }

    /**
     * Shannon entropy of inter-arrival times, computed by bucketing intervals into 10ms bins.
     */
    public static double entropy(long[] timestamps) {
        if (timestamps == null || timestamps.length < 2) return 0;

        long[] intervals = new long[timestamps.length - 1];
        for (int i = 1; i < timestamps.length; i++) {
            intervals[i - 1] = timestamps[i] - timestamps[i - 1];
        }

        // Bucket into 10ms bins
        Map<Long, Integer> buckets = new HashMap<>();
        for (long interval : intervals) {
            long bucket = interval / 10;
            buckets.merge(bucket, 1, Integer::sum);
        }

        int total = intervals.length;
        double entropy = 0;
        for (int count : buckets.values()) {
            double p = (double) count / total;
            if (p > 0) {
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    /**
     * Excess kurtosis of a distribution.
     */
    public static double kurtosis(double[] values) {
        if (values == null || values.length < 4) return 0;
        double mean = Arrays.stream(values).average().orElse(0);
        double variance = Arrays.stream(values).map(v -> Math.pow(v - mean, 2)).average().orElse(0);
        if (variance == 0) return 0;
        double fourthMoment = Arrays.stream(values).map(v -> Math.pow(v - mean, 4)).average().orElse(0);
        return (fourthMoment / (variance * variance)) - 3.0;
    }

    /**
     * Skewness of a distribution.
     */
    public static double skewness(double[] values) {
        if (values == null || values.length < 3) return 0;
        double mean = Arrays.stream(values).average().orElse(0);
        double variance = Arrays.stream(values).map(v -> Math.pow(v - mean, 2)).average().orElse(0);
        if (variance == 0) return 0;
        double stdDev = Math.sqrt(variance);
        double thirdMoment = Arrays.stream(values).map(v -> Math.pow(v - mean, 3)).average().orElse(0);
        return thirdMoment / (stdDev * stdDev * stdDev);
    }

    /**
     * Angle in degrees between two 3D vectors.
     */
    public static double angleBetween(double x1, double y1, double z1,
                                      double x2, double y2, double z2) {
        double dot = x1 * x2 + y1 * y2 + z1 * z2;
        double mag1 = Math.sqrt(x1 * x1 + y1 * y1 + z1 * z1);
        double mag2 = Math.sqrt(x2 * x2 + y2 * y2 + z2 * z2);
        if (mag1 == 0 || mag2 == 0) return 0;
        double cosAngle = dot / (mag1 * mag2);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle)); // clamp for floating point
        return Math.toDegrees(Math.acos(cosAngle));
    }

    /**
     * Clamp a value between min and max.
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
