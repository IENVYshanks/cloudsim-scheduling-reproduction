package edu.classproject.cloudsim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

record Metrics(double makespanSeconds, double totalExecutionSeconds, double utilization,
               double resourceWastage, double objective, List<Double> vmCompletionSeconds) { }

final class ScheduleEvaluator {
    private ScheduleEvaluator() { }

    static Metrics evaluate(Workload workload, int[] assignment, double makespanWeight, double wastageWeight) {
        if (!Double.isFinite(makespanWeight) || !Double.isFinite(wastageWeight) || makespanWeight < 0 || wastageWeight < 0 || makespanWeight + wastageWeight <= 0)
            throw new IllegalArgumentException("weights must be finite, non-negative, and at least one must be positive");
        if (assignment.length != workload.tasks().size()) throw new IllegalArgumentException("assignment must contain exactly one VM index per task");
        double[] completion = new double[workload.vms().size()];
        double total = 0;
        for (int i = 0; i < assignment.length; i++) {
            int vm = assignment[i];
            if (vm < 0 || vm >= workload.vms().size()) throw new IllegalArgumentException("invalid VM index " + vm + " for " + workload.tasks().get(i).id());
            double duration = workload.tasks().get(i).lengthMi() / workload.vms().get(vm).mips();
            completion[vm] += duration;
            total += duration;
        }
        double makespan = Arrays.stream(completion).max().orElse(0);
        double utilization = makespan == 0 ? 0 : total / (makespan * workload.vms().size());
        double wastage = 1 - utilization;
        return new Metrics(makespan, total, utilization, wastage,
            makespanWeight * makespan + wastageWeight * wastage,
            Arrays.stream(completion).boxed().toList());
    }
}

record AlgorithmResult(String name, int[] assignment, Metrics analyticalMetrics, List<Double> convergence) { }

final class Algorithms {
    private Algorithms() { }

    static AlgorithmResult run(String name, Workload w, long seed, double wm, double ww,
                               int populationSize, int generations, double mutationRate, double crossoverRate) {
        return switch (name) {
            case "random" -> random(w, seed, wm, ww);
            case "fcfs" -> earliestAvailable(w, wm, ww);
            case "min-min" -> minMin(w, wm, ww);
            case "ga" -> genetic(w, seed, wm, ww, populationSize, generations, mutationRate, crossoverRate);
            default -> throw new IllegalArgumentException("unknown algorithm: " + name);
        };
    }

    private static AlgorithmResult random(Workload w, long seed, double wm, double ww) {
        Random rng = new Random(seed);
        int[] a = new int[w.tasks().size()];
        for (int i = 0; i < a.length; i++) a[i] = rng.nextInt(w.vms().size());
        return result("random", w, a, wm, ww, List.of());
    }

    private static AlgorithmResult earliestAvailable(Workload w, double wm, double ww) {
        double[] completion = new double[w.vms().size()];
        int[] a = new int[w.tasks().size()];
        for (int t = 0; t < a.length; t++) {
            int vm = 0;
            for (int j = 1; j < completion.length; j++) if (completion[j] < completion[vm]) vm = j;
            a[t] = vm;
            completion[vm] += w.tasks().get(t).lengthMi() / w.vms().get(vm).mips();
        }
        return result("fcfs", w, a, wm, ww, List.of());
    }

    private static AlgorithmResult minMin(Workload w, double wm, double ww) {
        int n = w.tasks().size(), m = w.vms().size();
        boolean[] pending = new boolean[n]; Arrays.fill(pending, true);
        double[] completion = new double[m]; int[] a = new int[n];
        for (int remaining = n; remaining > 0; remaining--) {
            double best = Double.POSITIVE_INFINITY; int bestTask = -1, bestVm = -1;
            for (int t = 0; t < n; t++) if (pending[t]) for (int vm = 0; vm < m; vm++) {
                double finish = completion[vm] + w.tasks().get(t).lengthMi() / w.vms().get(vm).mips();
                if (finish < best || (finish == best && (t < bestTask || (t == bestTask && vm < bestVm)))) { best = finish; bestTask = t; bestVm = vm; }
            }
            pending[bestTask] = false; a[bestTask] = bestVm; completion[bestVm] = best;
        }
        return result("min-min", w, a, wm, ww, List.of());
    }

    private static AlgorithmResult genetic(Workload w, long seed, double wm, double ww,
                                           int populationSize, int generations, double mutationRate, double crossoverRate) {
        if (populationSize < 2 || generations < 1) throw new IllegalArgumentException("population must be >= 2 and generations >= 1");
        if (!rate(mutationRate) || !rate(crossoverRate)) throw new IllegalArgumentException("mutation and crossover rates must be in [0, 1]");
        Random rng = new Random(seed); int tasks = w.tasks().size(), vms = w.vms().size();
        List<int[]> population = new ArrayList<>();
        int uniqueTarget = Math.min(populationSize, distinctAssignmentsUpTo(tasks, vms, populationSize));
        Set<String> seen = new HashSet<>();
        for (int p = 0; p < populationSize; p++) {
            int[] genes;
            String key;
            do {
                genes = new int[tasks];
                for (int i = 0; i < tasks; i++) genes[i] = rng.nextInt(vms);
                key = Arrays.toString(genes);
            } while (p < uniqueTarget && !seen.add(key));
            population.add(genes);
        }
        List<Double> history = new ArrayList<>();
        Comparator<int[]> byCost = Comparator.comparingDouble(a -> ScheduleEvaluator.evaluate(w, a, wm, ww).objective());
        for (int gen = 0; gen < generations; gen++) {
            double[] scores = population.stream().mapToDouble(a -> ScheduleEvaluator.evaluate(w, a, wm, ww).objective()).toArray();
            int elite = 0; for (int i=1;i<scores.length;i++) if(scores[i]<scores[elite]) elite=i;
            history.add(scores[elite]); List<int[]> offspring = new ArrayList<>();
            while (offspring.size() < populationSize) {
                int[] a = population.get(select(population, scores, rng)).clone();
                int[] b = population.get(select(population, scores, rng)).clone();
                if (tasks > 1 && rng.nextDouble() < crossoverRate) {
                    int point = 1 + rng.nextInt(tasks - 1);
                    for (int i=point;i<tasks;i++) { int x=a[i]; a[i]=b[i]; b[i]=x; }
                }
                mutate(a, rng, mutationRate, vms); mutate(b, rng, mutationRate, vms);
                offspring.add(a); if (offspring.size() < populationSize) offspring.add(b);
            }
            population.addAll(offspring); population.sort(byCost); population = new ArrayList<>(population.subList(0, populationSize));
        }
        int[] best = population.stream().min(byCost).orElseThrow().clone();
        history.add(ScheduleEvaluator.evaluate(w, best, wm, ww).objective());
        return result("ga", w, best, wm, ww, history);
    }

    private static int distinctAssignmentsUpTo(int tasks, int vms, int limit) {
        long count = 1;
        for (int i = 0; i < tasks && count < limit; i++) count = Math.min(limit, count * vms);
        return (int)count;
    }

    private static boolean rate(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    private static void mutate(int[] genes, Random rng, double rate, int vmCount) {
        if (vmCount < 2) return;
        for (int i=0;i<genes.length;i++) if (rng.nextDouble()<rate) { int replacement=rng.nextInt(vmCount-1); genes[i]=replacement+(replacement>=genes[i]?1:0); }
    }
    private static int select(List<int[]> population, double[] costs, Random rng) {
        double worst = Arrays.stream(costs).max().orElseThrow(), total = 0;
        double[] probability = new double[costs.length];
        for(int i=0;i<costs.length;i++) { probability[i]=Math.max(0,worst-costs[i]); total+=probability[i]; }
        if(total<=1e-15) return rng.nextInt(population.size());
        double needle=rng.nextDouble()*total, cumulative=0;
        for(int i=0;i<probability.length;i++) { cumulative+=probability[i]; if(needle<=cumulative) return i; }
        return population.size()-1;
    }
    private static AlgorithmResult result(String name, Workload w, int[] a, double wm, double ww, List<Double> history) {
        return new AlgorithmResult(name, a.clone(), ScheduleEvaluator.evaluate(w,a,wm,ww), List.copyOf(history));
    }
}
