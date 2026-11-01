package edu.classproject.cloudsim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Experiments {
    private Experiments() { }

    static Map<String, Object> run(Workload workload, List<String> algorithms, int runs, long seed,
                                   double wm, double ww, int population, int generations,
                                   double mutation, double crossover) {
        if (runs < 1) throw new IllegalArgumentException("--runs must be at least 1");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (String name : algorithms) {
            groups.putIfAbsent(name, new ArrayList<>());
            for (int index = 0; index < runs; index++) {
                long runSeed = seed + index;
                AlgorithmResult result = Algorithms.run(name, workload, runSeed, wm, ww, population, generations, mutation, crossover);
                CloudSimOutcome cloudSim = CloudSimExecutor.execute(workload, result.assignment(), wm, ww);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("algorithm", name); row.put("run", index + 1); row.put("seed", runSeed);
                row.put("workload", workload.name()); row.put("task_count", workload.tasks().size()); row.put("vm_count", workload.vms().size());
                row.put("makespan_weight", wm); row.put("wastage_weight", ww);
                row.put("cloudsim_makespan_seconds", cloudSim.metrics().makespanSeconds());
                row.put("cloudsim_total_execution_seconds", cloudSim.metrics().totalExecutionSeconds());
                row.put("cloudsim_utilization", cloudSim.metrics().utilization());
                row.put("cloudsim_resource_wastage", cloudSim.metrics().resourceWastage());
                row.put("cloudsim_objective", cloudSim.metrics().objective());
                row.put("analytical_makespan_seconds", result.analyticalMetrics().makespanSeconds());
                row.put("analytical_total_execution_seconds", result.analyticalMetrics().totalExecutionSeconds());
                row.put("analytical_utilization", result.analyticalMetrics().utilization());
                row.put("analytical_resource_wastage", result.analyticalMetrics().resourceWastage());
                row.put("analytical_objective", result.analyticalMetrics().objective());
                row.put("max_vm_completion_difference_seconds", cloudSim.maxCompletionDifferenceSeconds());
                row.put("assignment", result.assignment().clone());
                row.put("vm_completion_seconds", cloudSim.metrics().vmCompletionSeconds());
                row.put("convergence", result.convergence());
                rows.add(row); groups.get(name).add(row);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        for (var group : groups.entrySet()) {
            Map<String, Object> metrics = new LinkedHashMap<>();
            for (String metric : List.of(
                "analytical_makespan_seconds", "analytical_total_execution_seconds", "analytical_utilization",
                "analytical_resource_wastage", "analytical_objective", "cloudsim_makespan_seconds",
                "cloudsim_utilization", "cloudsim_resource_wastage", "cloudsim_objective",
                "max_vm_completion_difference_seconds")) {
                List<Double> values = group.getValue().stream().map(row -> ((Number)row.get(metric)).doubleValue()).toList();
                metrics.put(metric, statistics(values));
            }
            summary.put(group.getKey(), metrics);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("paper", "Dehury et al., Joint optimization of resource utilization and makespan in cloud computing using genetic algorithm");
        metadata.put("implementation", "Java CloudSim Plus toolkit execution with analytical GA fitness evaluation");
        metadata.put("cloudsim_plus_version", "8.0.0");
        metadata.put("cloudsim_minimum_event_spacing_seconds", 0.1);
        metadata.put("workload_name", workload.name()); metadata.put("workload_origin", "synthetic");
        metadata.put("base_seed", seed); metadata.put("runs_per_algorithm", runs);
        metadata.put("objective", "w1 * makespan_seconds + w2 * resource_wastage");
        metadata.put("weights", Map.of("makespan", wm, "wastage", ww));
        metadata.put("ga", Map.of("population_size", population, "generations", generations, "mutation_rate", mutation, "crossover_rate", crossover));
        metadata.put("vm_mips", workload.vms().stream().map(VmSpec::mips).toList());
        metadata.put("task_lengths_mi", workload.tasks().stream().map(Task::lengthMi).toList());
        metadata.put("task_length_mi", Map.of("minimum", workload.tasks().stream().mapToLong(Task::lengthMi).min().orElseThrow(),
            "maximum", workload.tasks().stream().mapToLong(Task::lengthMi).max().orElseThrow()));
        return Map.of("metadata", metadata, "summary", summary, "runs", rows);
    }

    static void write(Map<String, Object> result, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("results.json"), Json.encode(result) + System.lineSeparator());
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>)result.get("runs");
        List<String> fields = rows.get(0).keySet().stream().filter(k -> !List.of("assignment", "vm_completion_seconds", "convergence").contains(k)).toList();
        StringBuilder csv = new StringBuilder(String.join(",", fields)).append("\r\n");
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < fields.size(); i++) { if (i > 0) csv.append(','); csv.append(row.get(fields.get(i))); }
            csv.append("\r\n");
        }
        Files.writeString(outputDir.resolve("runs.csv"), csv);
    }

    private static double mean(List<Double> values) { return values.stream().mapToDouble(Double::doubleValue).average().orElse(0); }
    private static Map<String, Double> statistics(List<Double> values) {
        double mean = mean(values), stddev = stddev(values);
        double margin = values.size() < 2 ? 0 : 1.96 * stddev / Math.sqrt(values.size());
        return Map.of(
            "mean", mean,
            "stddev", stddev,
            "minimum", values.stream().mapToDouble(Double::doubleValue).min().orElse(0),
            "maximum", values.stream().mapToDouble(Double::doubleValue).max().orElse(0),
            "ci95_low", mean - margin,
            "ci95_high", mean + margin
        );
    }
    private static double stddev(List<Double> values) {
        if (values.size() < 2) return 0;
        double mean = mean(values), sum = 0;
        for (double value : values) sum += (value - mean) * (value - mean);
        return Math.sqrt(sum / (values.size() - 1));
    }
}

final class Json {
    private Json() { }
    static String encode(Object value) { StringBuilder out = new StringBuilder(); append(out, value); return out.toString(); }
    private static void append(StringBuilder out, Object value) {
        if (value == null) out.append("null");
        else if (value instanceof String s) quote(out, s);
        else if (value instanceof Number || value instanceof Boolean) out.append(value);
        else if (value instanceof Map<?, ?> map) {
            out.append('{'); boolean first = true;
            for (var entry : map.entrySet()) { if (!first) out.append(','); first = false; quote(out, String.valueOf(entry.getKey())); out.append(':'); append(out, entry.getValue()); }
            out.append('}');
        } else if (value instanceof Iterable<?> items) {
            out.append('['); boolean first = true; for (Object item : items) { if (!first) out.append(','); first = false; append(out, item); } out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('['); int n = java.lang.reflect.Array.getLength(value);
            for (int i=0;i<n;i++) { if (i>0) out.append(','); append(out, java.lang.reflect.Array.get(value,i)); } out.append(']');
        } else quote(out, value.toString());
    }
    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (char c : text.toCharArray()) switch (c) {
            case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
            default -> { if (c < 0x20) out.append("\\u%04x".formatted((int)c)); else out.append(c); }
        }
        out.append('"');
    }
}
