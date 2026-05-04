// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec;

import cc.craftospc.ccspeakercodecs.codec.adpcm.ADPCMDecoder;
import cc.craftospc.ccspeakercodecs.codec.adpcm.ADPCMEncoder;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// Notice: This code was majority written using Qwen3.6. It has been audited by a
// human, and is not used in actual mod code.

public final class Benchmark {
    private static final int WARMUP_RUNS = 5;
    private static final int TIMED_RUNS = 50;

    public static void main(String[] args) {
        if (!"true".equals(System.getProperty("ccspeakercodecs.profile"))) return;

        runBenchmark(Codec.QOA, "QOA");
        runBenchmark(new OpusCodec(0), "Opus");
        System.out.println();
        runADPCMBenchmarks();
    }

    private static short[] generateTestAudio(int numSamples) {
        short[] audio = new short[numSamples];
        for (int i = 0; i < numSamples; i++) {
            double t = i / 48000.0;
            double v = Math.sin(2 * Math.PI * 440 * t)
                    + 0.5 * Math.sin(2 * Math.PI * 880 * t)
                    + 0.3 * Math.sin(2 * Math.PI * 220 * t);
            double envelope = Math.min(i / 100.0, Math.min(1.0, (numSamples - i) / 100.0));
            v *= envelope;
            audio[i] = (short) Math.max(-32768, Math.min(32767, v * Short.MAX_VALUE));
        }
        return audio;
    }

    private static void runBenchmark(Codec codec, String name) {
        short[] audio = generateTestAudio(96000); // 2 sec @ 48 kHz (Codec takes 48 kHz audio)

        for (int run = 0; run < WARMUP_RUNS; run++) {
            byte[] encoded = codec.encode(audio);
            codec.decode(encoded, audio.length);
        }

        int[] encodeTimes = new int[TIMED_RUNS];
        int[] decodeTimes = new int[TIMED_RUNS];

        for (int i = 0; i < TIMED_RUNS; i++) {
            long start = System.nanoTime();
            byte[] encoded = codec.encode(audio);
            encodeTimes[i] = (int) ((System.nanoTime() - start) / 1_000_000L);

            start = System.nanoTime();
            codec.decode(encoded, audio.length);
            decodeTimes[i] = (int) ((System.nanoTime() - start) / 1_000_000L);
        }

        printCodecLine(name, encodeTimes, decodeTimes, audio.length);
    }

    private static void runADPCMBenchmarks() {
        int[] bpsArray = {2, 3, 4, 5};
        int[] lookaheads = {3, 4, 5, 6};
        int[] noiseShaping = {ADPCMEncoder.NOISE_SHAPING_OFF, ADPCMEncoder.NOISE_SHAPING_STATIC,
                              ADPCMEncoder.NOISE_SHAPING_DYNAMIC};
        String[] noiseNames = {"OFF", "STATIC", "DYNAMIC"};
        int[] sampleRates = {24000, 16000, 12000, 9600};
        double audioDuration = 2.0; // seconds for all codecs

        int numConfigs = bpsArray.length * lookaheads.length * noiseShaping.length;
        ExecutorService executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() / 2);
        List<CompletableFuture<Void>> futures = new ArrayList<>(numConfigs);
        AtomicInteger completed = new AtomicInteger(0);

        for (int bpsIdx = 0; bpsIdx < bpsArray.length; bpsIdx++) {
            int bps = bpsArray[bpsIdx];
            int sampleRate = sampleRates[bpsIdx];
            int numInputSamples = (int) (audioDuration * sampleRate);

            for (int la : lookaheads) {
                for (int nsIdx = 0; nsIdx < noiseShaping.length; nsIdx++) {
                    int ns = noiseShaping[nsIdx];
                    String nsName = noiseNames[nsIdx];

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        short[] resampled = generateTestAudio(numInputSamples);

                        byte[] warmupEncoded = new byte[numInputSamples * bps / 8 + 9];
                        int warmupSz = new ADPCMEncoder(1, sampleRate, la, ns)
                                .encode_block_ex(warmupEncoded, resampled, bps);
                        warmupEncoded = Arrays.copyOf(warmupEncoded, warmupSz);

                        int warmupDecoded = warmupSz * 8 / bps + 1;
                        short[] decodeOut = new short[warmupDecoded];
                        ADPCMDecoder.decode_block_ex(decodeOut, warmupEncoded, 1, bps);

                        int[] encodeTimes = new int[TIMED_RUNS];
                        int[] decodeTimes = new int[TIMED_RUNS];
                        boolean timedOut = false;

                        for (int i = 0; i < TIMED_RUNS; i++) {
                            long start = System.nanoTime();
                            byte[] encoded = new byte[numInputSamples * bps / 8 + 9];
                            int sz = new ADPCMEncoder(1, sampleRate, la, ns)
                                    .encode_block_ex(encoded, resampled, bps);
                            long elapsed = System.nanoTime() - start;

                            if (elapsed > 1_000_000_000L) {
                                timedOut = true;
                                break;
                            }

                            encoded = Arrays.copyOf(encoded, sz);
                            encodeTimes[i] = (int) (elapsed / 1_000_000L);

                            int decodedSz = sz * 8 / bps + 1;
                            short[] decoded = new short[decodedSz];
                            long decodeStart = System.nanoTime();
                            ADPCMDecoder.decode_block_ex(decoded, encoded, 1, bps);
                            decodeTimes[i] = (int) ((System.nanoTime() - decodeStart) / 1_000_000L);
                        }

                        synchronized (Benchmark.class) {
                            ADPCMResults.add(new ADPCMResult(bps, la, nsName,
                                    encodeTimes, decodeTimes, warmupDecoded, audioDuration, timedOut));
                            completed.incrementAndGet();
                        }
                    }, executor).orTimeout(60, TimeUnit.SECONDS).exceptionally((ex) -> {
                        synchronized (Benchmark.class) {
                            ADPCMResults.add(new ADPCMResult(bps, la, nsName, new int[0], new int[0], 0, audioDuration, true));
                            completed.incrementAndGet();
                        }
                        return null;
                    });

                    futures.add(future);
                }
            }
        }

        // Progress meter thread
        Thread progressThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                int done = completed.get();
                if (done >= numConfigs) break;
                System.out.printf("\rADPCM: %d / %d tasks completed", done, numConfigs);
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
        }, "progress-meter");
        progressThread.setDaemon(true);
        progressThread.start();

        // Wait for all benchmarks to complete, then print in order
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        progressThread.interrupt();
        try {progressThread.join();} catch (Exception ignored) {}

        // Clear progress line and print results
        System.out.printf("\r" + " ".repeat(60) + "\r");

        ADPCMResults.sort(Comparator.comparingInt((ADPCMResult r) -> r.bps)
                .thenComparingInt(r -> r.lookahead)
                .thenComparingInt(r -> noiseShapingOrder(r.noise)));

        int lastBps = 2;
        for (ADPCMResult r : ADPCMResults) {
            if (r.bps > lastBps) System.out.println();
            printADPCMLine(r);
            lastBps = r.bps;
        }

        executor.shutdownNow();
    }

    private static int noiseShapingOrder(String noise) {
        return switch (noise) {
            case "OFF" -> 0;
            case "STATIC" -> 1;
            case "DYNAMIC" -> 2;
            default -> 3;
        };
    }

    private static final List<ADPCMResult> ADPCMResults = new ArrayList<>();

    private record ADPCMResult(int bps, int lookahead, String noise,
                               int[] encodeTimes, int[] decodeTimes,
                               int numSamples, double audioDuration, boolean timedOut) {}

    private static double mean(int[] a) {
        long sum = 0;
        int count = 0;
        for (int v : a) { sum += v; count++; }
        return count > 0 ? sum / (double) count : -1;
    }

    private static void printCodecLine(String name, int[] encodeTimes, int[] decodeTimes,
                                       int numSamples) {
        double encodeAvg = mean(encodeTimes);
        double decodeAvg = mean(decodeTimes);
        double rttAvg = encodeAvg + decodeAvg;
        double throughput = (numSamples / rttAvg) * 1000.0;
        double realtimeMul = 2000.0 / encodeAvg;
        System.out.printf("%-39s %7.3f ms   %7.3f ms   %7.3f ms   %9d s/sec  %.1fx real-time%n",
                name, encodeAvg, decodeAvg, rttAvg, (int) throughput, realtimeMul);
    }

    private static void printADPCMLine(ADPCMResult r) {
        if (r.timedOut) {
            System.out.printf("ADPCM bps=%d lookahead=%-2d noise=%-8s Skipped (encoding timeout)%n",
                    r.bps, r.lookahead, r.noise);
        } else {
            double encodeAvg = mean(r.encodeTimes);
            double decodeAvg = mean(r.decodeTimes);
            double rttAvg = encodeAvg + decodeAvg;
            double throughput = (r.numSamples / rttAvg) * 1000.0;
            double realtimeMul = (r.audioDuration * 1000.0) / encodeAvg;
            System.out.printf("ADPCM bps=%d lookahead=%-2d noise=%-8s %7.3f ms   %7.3f ms   %7.3f ms   %9d s/sec  %.1fx real-time%n",
                    r.bps, r.lookahead, r.noise, encodeAvg, decodeAvg, rttAvg, (int) throughput, realtimeMul);
        }
    }
}
