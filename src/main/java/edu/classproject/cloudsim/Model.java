package edu.classproject.cloudsim;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

record Task(String id, long lengthMi) {
    Task {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("task ID must not be empty");
        if (lengthMi <= 0) throw new IllegalArgumentException(id + ": task length must be positive MI");
    }
}

record VmSpec(String id, double mips) {
    VmSpec {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("VM ID must not be empty");
        if (!Double.isFinite(mips) || mips <= 0) throw new IllegalArgumentException(id + ": MIPS must be positive and finite");
    }
}

record Workload(List<Task> tasks, List<VmSpec> vms, String name) {
    Workload {
        tasks = List.copyOf(tasks);
        vms = List.copyOf(vms);
        if (tasks.isEmpty()) throw new IllegalArgumentException("workload must contain at least one task");
        if (vms.isEmpty()) throw new IllegalArgumentException("workload must contain at least one VM");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("workload name must not be empty");
        Set<String> taskIds = new HashSet<>();
        for (Task task : tasks) if (!taskIds.add(task.id())) throw new IllegalArgumentException("duplicate task ID: " + task.id());
        Set<String> vmIds = new HashSet<>();
        for (VmSpec vm : vms) if (!vmIds.add(vm.id())) throw new IllegalArgumentException("duplicate VM ID: " + vm.id());
    }
}

final class Workloads {
    private Workloads() { }

    static Workload synthetic(int taskCount, int vmCount, long seed, long minMi, long maxMi) {
        if (taskCount < 1) throw new IllegalArgumentException("--tasks must be at least 1");
        if (vmCount < 1) throw new IllegalArgumentException("--vms must be at least 1");
        if (minMi < 1 || maxMi < minMi) throw new IllegalArgumentException("task lengths must satisfy 1 <= min <= max");
        int[] illustrativeProfile = {60, 50, 40, 30};
        List<VmSpec> vms = java.util.stream.IntStream.range(0, vmCount)
            .mapToObj(i -> new VmSpec("vm-%03d".formatted(i), illustrativeProfile[i % illustrativeProfile.length]))
            .toList();
        Random random = new Random(seed);
        List<Task> tasks = java.util.stream.IntStream.range(0, taskCount)
            .mapToObj(i -> new Task("task-%04d".formatted(i), minMi + (long)(random.nextDouble() * (maxMi - minMi + 1))))
            .toList();
        return new Workload(tasks, vms, "synthetic-%d-tasks-seed-%d".formatted(taskCount, seed));
    }
}
