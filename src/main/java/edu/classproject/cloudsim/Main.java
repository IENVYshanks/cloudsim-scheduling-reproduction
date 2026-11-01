package edu.classproject.cloudsim;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.util.Log;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class Main {
    private Main() { }

    public static void main(String[] args) throws Exception {
        Log.setLevel(Level.WARN);
        Map<String, String> options = parse(args);
        if (options.containsKey("help")) { usage(); return; }
        int tasks = integer(options, "tasks", 100), vms = integer(options, "vms", 60), runs = integer(options, "runs", 20);
        int population = integer(options, "population", 100), generations = integer(options, "generations", 100);
        long seed = longValue(options, "seed", 1), minMi = longValue(options, "min-mi", 15), maxMi = longValue(options, "max-mi", 400);
        double mutation = decimal(options, "mutation-rate", 0.1), crossover = decimal(options, "crossover-rate", 0.8);
        double wm = decimal(options, "makespan-weight", 0.5), ww = decimal(options, "wastage-weight", 0.5);
        List<String> algorithms = Arrays.stream(options.getOrDefault("algorithms", "ga,fcfs,min-min,random").split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
        for (String algorithm : algorithms) if (!List.of("ga", "fcfs", "min-min", "random").contains(algorithm)) throw new IllegalArgumentException("unknown algorithm: " + algorithm);
        if (algorithms.isEmpty()) throw new IllegalArgumentException("--algorithms must not be empty");
        Path output = Path.of(options.getOrDefault("output", "output"));
        if (options.containsKey("task-counts")) {
            List<Integer> taskCounts = positiveIntegers(options.get("task-counts"), "--task-counts");
            Path report = Path.of(options.getOrDefault("report", "EVALUATION.md"));
            Evaluation.run(taskCounts, vms, algorithms, runs, seed, minMi, maxMi, wm, ww,
                population, generations, mutation, crossover, output, report);
            System.out.println("Wrote consolidated evaluation to " + output + " and " + report);
            return;
        }
        Workload workload = Workloads.synthetic(tasks, vms, seed, minMi, maxMi);
        Map<String, Object> result = Experiments.run(workload, algorithms, runs, seed, wm, ww, population, generations, mutation, crossover);
        Experiments.write(result, output);
        System.out.println("Wrote " + output.resolve("results.json") + " and " + output.resolve("runs.csv"));
        @SuppressWarnings("unchecked") Map<String, Map<String, Map<String, Number>>> summary = (Map<String, Map<String, Map<String, Number>>>)result.get("summary");
        for (String name : algorithms) {
            var metrics = summary.get(name);
            System.out.printf("%-8s analytical makespan=%.6f +/- %.6fs  wastage=%.6f +/- %.6f%n", name,
                metrics.get("analytical_makespan_seconds").get("mean").doubleValue(), metrics.get("analytical_makespan_seconds").get("stddev").doubleValue(),
                metrics.get("analytical_resource_wastage").get("mean").doubleValue(), metrics.get("analytical_resource_wastage").get("stddev").doubleValue());
        }
    }

    private static Map<String, String> parse(String[] args) {
        var result = new java.util.LinkedHashMap<String, String>();
        for (int i=0;i<args.length;i++) {
            String arg = args[i];
            if (arg.equals("--help") || arg.equals("-h")) { result.put("help", ""); continue; }
            if (!arg.startsWith("--")) throw new IllegalArgumentException("unexpected argument: " + arg);
            String key = arg.substring(2);
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) throw new IllegalArgumentException("missing value for " + arg);
            result.put(key, args[++i]);
        }
        return result;
    }
    private static int integer(Map<String,String> o, String key, int fallback) { try { return Integer.parseInt(o.getOrDefault(key, String.valueOf(fallback))); } catch (NumberFormatException e) { throw new IllegalArgumentException("--"+key+" must be an integer"); } }
    private static long longValue(Map<String,String> o, String key, long fallback) { try { return Long.parseLong(o.getOrDefault(key, String.valueOf(fallback))); } catch (NumberFormatException e) { throw new IllegalArgumentException("--"+key+" must be an integer"); } }
    private static double decimal(Map<String,String> o, String key, double fallback) { try { return Double.parseDouble(o.getOrDefault(key, String.valueOf(fallback))); } catch (NumberFormatException e) { throw new IllegalArgumentException("--"+key+" must be a number"); } }
    private static List<Integer> positiveIntegers(String text, String option) {
        try {
            LinkedHashSet<Integer> values = new LinkedHashSet<>();
            for (String item : text.split(",")) {
                int value = Integer.parseInt(item.trim());
                if (value < 1) throw new IllegalArgumentException(option + " values must be at least 1");
                values.add(value);
            }
            if (values.isEmpty()) throw new IllegalArgumentException(option + " must not be empty");
            return List.copyOf(values);
        } catch (NumberFormatException e) { throw new IllegalArgumentException(option + " must be a comma-separated list of integers"); }
    }
    private static void usage() {
        System.out.println("Usage: mvn exec:java -Dexec.args=\"[options]\"");
        System.out.println("  --tasks N --vms N --runs N --seed N --algorithms ga,fcfs,min-min,random");
        System.out.println("  --task-counts N,N,... --report FILE (runs a consolidated scalability evaluation)");
        System.out.println("  --population N --generations N --mutation-rate X --crossover-rate X");
        System.out.println("  --min-mi N --max-mi N --makespan-weight X --wastage-weight X --output DIR");
    }
}
