package edu.classproject.cloudsim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runs and reports a reproducible synthetic scalability study. */
final class Evaluation {
    private Evaluation() { }

    static Map<String, Object> run(List<Integer> taskCounts, int vmCount, List<String> algorithms,
                                   int runs, long seed, long minMi, long maxMi, double wm, double ww,
                                   int population, int generations, double mutation, double crossover,
                                   Path outputDir, Path reportPath) throws IOException {
        Files.createDirectories(outputDir);
        List<Map<String, Object>> scenarios = new ArrayList<>();
        List<Map<String, Object>> fullResults = new ArrayList<>();
        for (int taskCount : taskCounts) {
            Workload workload = Workloads.synthetic(taskCount, vmCount, seed, minMi, maxMi);
            Map<String, Object> result = Experiments.run(workload, algorithms, runs, seed, wm, ww,
                population, generations, mutation, crossover);
            Path scenarioDir = outputDir.resolve("tasks-" + taskCount);
            Experiments.write(result, scenarioDir);
            Map<String, Object> scenario = new LinkedHashMap<>();
            scenario.put("task_count", taskCount);
            scenario.put("results_path", outputDir.relativize(scenarioDir.resolve("results.json")).toString().replace('\\', '/'));
            scenario.put("metadata", result.get("metadata"));
            scenario.put("summary", result.get("summary"));
            scenarios.add(scenario);
            fullResults.add(result);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("purpose", "Synthetic scalability evaluation of the paper's core scheduling problem");
        metadata.put("paper_result_reproduction", false);
        metadata.put("task_counts", taskCounts);
        metadata.put("vm_count", vmCount);
        metadata.put("host_count", 1);
        metadata.put("runs_per_algorithm_per_scenario", runs);
        metadata.put("base_seed", seed);
        metadata.put("algorithms", algorithms);
        metadata.put("primary_metrics", "analytical paper-equation metrics");
        metadata.put("cloudsim_role", "execution validation; timing includes CloudSim Plus event spacing");
        metadata.put("workload_origin", "seeded synthetic");
        Map<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("metadata", metadata);
        evaluation.put("scenarios", scenarios);
        Files.writeString(outputDir.resolve("evaluation.json"), Json.encode(evaluation) + System.lineSeparator());
        Files.writeString(outputDir.resolve("evaluation.csv"), csv(taskCounts, algorithms, fullResults));
        String report = markdown(taskCounts, vmCount, algorithms, runs, seed, minMi, maxMi, wm, ww,
            population, generations, mutation, crossover, fullResults);
        Files.writeString(outputDir.resolve("EVALUATION.md"), report);
        Files.writeString(reportPath, report);
        return evaluation;
    }

    private static String csv(List<Integer> taskCounts, List<String> algorithms,
                              List<Map<String, Object>> results) {
        StringBuilder out = new StringBuilder("task_count,algorithm,analytical_makespan_mean,analytical_makespan_stddev,analytical_utilization_mean,analytical_resource_wastage_mean,analytical_objective_mean,cloudsim_makespan_mean,max_vm_completion_difference_mean\r\n");
        for (int i = 0; i < taskCounts.size(); i++) {
            for (String algorithm : algorithms) {
                out.append(taskCounts.get(i)).append(',').append(algorithm).append(',')
                    .append(stat(results.get(i), algorithm, "analytical_makespan_seconds", "mean")).append(',')
                    .append(stat(results.get(i), algorithm, "analytical_makespan_seconds", "stddev")).append(',')
                    .append(stat(results.get(i), algorithm, "analytical_utilization", "mean")).append(',')
                    .append(stat(results.get(i), algorithm, "analytical_resource_wastage", "mean")).append(',')
                    .append(stat(results.get(i), algorithm, "analytical_objective", "mean")).append(',')
                    .append(stat(results.get(i), algorithm, "cloudsim_makespan_seconds", "mean")).append(',')
                    .append(stat(results.get(i), algorithm, "max_vm_completion_difference_seconds", "mean")).append("\r\n");
            }
        }
        return out.toString();
    }

    private static String markdown(List<Integer> taskCounts, int vmCount, List<String> algorithms,
                                   int runs, long seed, long minMi, long maxMi, double wm, double ww,
                                   int population, int generations, double mutation, double crossover,
                                   List<Map<String, Object>> results) {
        StringBuilder out = new StringBuilder();
        out.append("# Evaluation of the CloudSim scheduling reproduction\n\n");
        out.append("## Scope and interpretation\n\n");
        out.append("This is a reproducible **synthetic evaluation** of the core scheduling problem in Dehury et al., not a reproduction of the paper's published numerical results. The unavailable Cloud-Fog task files and authors' code were not reconstructed. The implementation uses the Java CloudSim Plus toolkit; the paper reports a Python 3.10/Google Colab implementation.\n\n");
        out.append("Analytical metrics computed from the paper's equations are the primary results. CloudSim Plus executes every reported assignment as a validation step, but its 0.1-second minimum event spacing adds timing overhead for these short synthetic tasks.\n\n");
        out.append("## Experimental configuration\n\n");
        out.append("- Task counts: ").append(taskCounts).append("; VMs: ").append(vmCount).append("; one sufficiently provisioned host.\n");
        out.append("- Runs per algorithm and task count: ").append(runs).append(" using seeds ").append(seed).append(" through ").append(seed + runs - 1).append(".\n");
        out.append("- Synthetic task lengths: uniformly generated integer values from ").append(minMi).append(" to ").append(maxMi).append(" MI with base seed ").append(seed).append(".\n");
        out.append("- Synthetic heterogeneous VM profile: repeating 60/50/40/30 MIPS. It is not the paper's AWS i8g dataset.\n");
        out.append("- GA: population ").append(population).append(", generations ").append(generations)
            .append(", mutation rate ").append(mutation).append(", crossover rate ").append(crossover).append(". The first three values follow the paper's main experiment; crossover rate is a local assumption because the paper does not report one.\n");
        out.append("- Objective: ").append(wm).append(" x makespan + ").append(ww).append(" x resource wastage. Equal coefficients are a local assumption and are not scale normalization; makespan in seconds can dominate a wastage fraction.\n");
        out.append("- GA is the paper-method reproduction. FCFS, Min-Min, and random allocation are transparent local baselines, not reconstructions of the paper's undocumented PSO, ABC, or BWO implementations.\n");
        out.append("- The single host deliberately removes VM-placement decisions from the experiment so that the decision variable remains task-to-VM scheduling. Host contention, migration, network delay, energy, and unavailable CPU/RAM task-demand constraints are out of scope.\n\n");
        out.append("Each task-count scenario uses a fixed workload across its 20 optimizer runs. The 100-task workload is a prefix of the 200-task workload, which is a prefix of the 300-task workload. FCFS and Min-Min are deterministic, so their repeated rows and standard deviations are expected to be identical or zero.\n\n");
        out.append("## Results\n\n");
        out.append("Primary analytical results (mean +/- sample standard deviation across runs):\n\n");
        out.append("| Tasks | Algorithm | Makespan (s) | Utilization | Wastage | Weighted objective |\n");
        out.append("|---:|---|---:|---:|---:|---:|\n");
        for (int i = 0; i < taskCounts.size(); i++) {
            for (String algorithm : algorithms) {
                out.append("| ").append(taskCounts.get(i)).append(" | ").append(algorithm).append(" | ")
                    .append(f(stat(results.get(i), algorithm, "analytical_makespan_seconds", "mean"))).append(" +/- ")
                    .append(f(stat(results.get(i), algorithm, "analytical_makespan_seconds", "stddev"))).append(" | ")
                    .append(f(stat(results.get(i), algorithm, "analytical_utilization", "mean"))).append(" | ")
                    .append(f(stat(results.get(i), algorithm, "analytical_resource_wastage", "mean"))).append(" | ")
                    .append(f(stat(results.get(i), algorithm, "analytical_objective", "mean"))).append(" |\n");
            }
        }

        out.append("\n## Analysis\n\n");
        if (algorithms.contains("ga") && algorithms.contains("random")) {
            for (int i = 0; i < taskCounts.size(); i++) {
                double gaMakespan = stat(results.get(i), "ga", "analytical_makespan_seconds", "mean");
                double randomMakespan = stat(results.get(i), "random", "analytical_makespan_seconds", "mean");
                double gaWastage = stat(results.get(i), "ga", "analytical_resource_wastage", "mean");
                double randomWastage = stat(results.get(i), "random", "analytical_resource_wastage", "mean");
                out.append("- At ").append(taskCounts.get(i)).append(" tasks, GA reduced mean makespan by ")
                    .append(f(percentReduction(randomMakespan, gaMakespan))).append("% and mean resource wastage by ")
                    .append(f(percentReduction(randomWastage, gaWastage))).append("% relative to the local random baseline.\n");
            }
        }
        for (int i = 0; i < taskCounts.size(); i++) {
            String bestMakespan = bestAlgorithm(results.get(i), algorithms, "analytical_makespan_seconds");
            String bestWastage = bestAlgorithm(results.get(i), algorithms, "analytical_resource_wastage");
            String bestObjective = bestAlgorithm(results.get(i), algorithms, "analytical_objective");
            out.append("- At ").append(taskCounts.get(i)).append(" tasks, the lowest mean makespan was produced by ")
                .append(bestMakespan).append(", the lowest mean wastage by ").append(bestWastage)
                .append(", and the lowest mean weighted objective by ").append(bestObjective).append(".\n");
        }
        if (algorithms.contains("ga") && algorithms.contains("min-min")) {
            out.append("- GA did not outperform every local baseline: Min-Min had a lower mean makespan and weighted objective in all three scenarios, and FCFS also beat GA on those measures at 200 and 300 tasks. This controlled static workload is favorable to greedy earliest-finish scheduling; the result is a limitation of the local GA configuration, not evidence for the paper's published superiority claims.\n");
        }
        if (algorithms.contains("ga")) {
            double first = stat(results.get(0), "ga", "analytical_makespan_seconds", "mean");
            double last = stat(results.get(results.size() - 1), "ga", "analytical_makespan_seconds", "mean");
            out.append("- GA mean makespan increased from ").append(f(first)).append(" seconds at ")
                .append(taskCounts.get(0)).append(" tasks to ").append(f(last)).append(" seconds at ")
                .append(taskCounts.get(taskCounts.size() - 1)).append(" tasks, as expected when total workload increases with a fixed VM count.\n");
            double improvement = convergenceImprovement(results);
            out.append("- Across all GA runs and scenarios, the mean best-objective improvement from the initial population to the final generation was ")
                .append(f(improvement)).append("%. Elitist replacement makes each recorded best-so-far convergence history non-increasing.\n");
        }
        double maxDifference = 0;
        for (Map<String, Object> result : results) for (String algorithm : algorithms)
            maxDifference = Math.max(maxDifference, stat(result, algorithm, "max_vm_completion_difference_seconds", "maximum"));
        out.append("- The largest observed per-VM difference between CloudSim finish time and the analytical completion sum was ")
            .append(f(maxDifference)).append(" seconds. This is retained as validation evidence rather than mixed into the paper-equation comparison.\n");
        out.append("- Results show behavior on this controlled synthetic workload only. They cannot validate the paper's published percentage improvements or establish statistical superiority over PSO, ABC, or BWO.\n\n");
        out.append("## Strengths and limitations\n\n");
        out.append("The implementation preserves independent non-preemptive tasks, exactly one VM assignment per task, serial execution of multiple tasks on a VM, heterogeneous MI/MIPS execution times, the weighted objective, integer chromosomes, Eq. 12 roulette selection, one-point crossover, mutation, and elitist parent-offspring replacement. Seeds and configuration metadata make the experiment reproducible.\n\n");
        out.append("The principal limitations are the synthetic workload and VM profile, absent CPU/memory task demands, unnormalized objective components, fixed one-host infrastructure, omitted communication delays, and unavailable implementations of the paper's comparative optimizers. The 95% intervals in the JSON describe stochastic variation for this fixed synthetic instance; no hypothesis-test or general statistical-significance claim is made.\n\n");
        out.append("Machine-readable details are in `output-evaluation/evaluation.json`, `output-evaluation/evaluation.csv`, and each `output-evaluation/tasks-N/` directory.\n");
        return out.toString();
    }

    private static double convergenceImprovement(List<Map<String, Object>> results) {
        double total = 0; int count = 0;
        for (Map<String, Object> result : results) {
            @SuppressWarnings("unchecked") List<Map<String, Object>> runs = (List<Map<String, Object>>)result.get("runs");
            for (Map<String, Object> run : runs) if ("ga".equals(run.get("algorithm"))) {
                @SuppressWarnings("unchecked") List<Double> convergence = (List<Double>)run.get("convergence");
                if (!convergence.isEmpty() && convergence.get(0) != 0) {
                    total += percentReduction(convergence.get(0), convergence.get(convergence.size() - 1));
                    count++;
                }
            }
        }
        return count == 0 ? 0 : total / count;
    }

    private static double percentReduction(double baseline, double value) {
        return baseline == 0 ? 0 : (baseline - value) * 100 / baseline;
    }

    private static String bestAlgorithm(Map<String, Object> result, List<String> algorithms, String metric) {
        String best = algorithms.get(0);
        double bestValue = stat(result, best, metric, "mean");
        for (int i = 1; i < algorithms.size(); i++) {
            double value = stat(result, algorithms.get(i), metric, "mean");
            if (value < bestValue) { best = algorithms.get(i); bestValue = value; }
        }
        return best;
    }

    private static String f(double value) { return String.format(Locale.ROOT, "%.6f", value); }

    @SuppressWarnings("unchecked")
    private static double stat(Map<String, Object> result, String algorithm, String metric, String statistic) {
        Map<String, Object> summary = (Map<String, Object>)result.get("summary");
        Map<String, Object> algorithmSummary = (Map<String, Object>)summary.get(algorithm);
        Map<String, Number> metricSummary = (Map<String, Number>)algorithmSummary.get(metric);
        return metricSummary.get(statistic).doubleValue();
    }
}
