package edu.classproject.cloudsim;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

record CloudSimOutcome(Metrics metrics, double maxCompletionDifferenceSeconds) { }

/** Executes one fixed chromosome in the CloudSim Plus event simulator. */
final class CloudSimExecutor {
    private CloudSimExecutor() { }

    static CloudSimOutcome execute(Workload workload, int[] assignment, double makespanWeight, double wastageWeight) {
        if (assignment.length != workload.tasks().size()) throw new IllegalArgumentException("assignment length must equal task count");
        // Each call creates an isolated CloudSim simulation, so independent experiment runs do not share simulator state.
        // CloudSim Plus defaults to 0.1-second event spacing. Keep that stable default:
        // smaller values made routine runs impractically slow in this workload.
        CloudSimPlus simulation = new CloudSimPlus();
        int vmCount = workload.vms().size();
        long maxHostMips = (long)Math.ceil(workload.vms().stream().mapToDouble(VmSpec::mips).max().orElseThrow());
        List<Pe> processingElements = new ArrayList<>(vmCount);
        for (int i = 0; i < vmCount; i++) processingElements.add(new PeSimple(maxHostMips));
        Host host = new HostSimple(Math.max(8192L, vmCount * 1024L), Math.max(100_000L, vmCount * 1_000L),
            Math.max(1_000_000L, vmCount * 10_000L), processingElements);
        new DatacenterSimple(simulation, List.of(host));
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);

        List<Vm> vms = new ArrayList<>(vmCount);
        for (int i = 0; i < vmCount; i++) {
            VmSpec spec = workload.vms().get(i);
            Vm vm = new VmSimple(i, spec.mips(), 1)
                .setRam(512).setBw(1_000).setSize(10_000)
                .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vms.add(vm);
        }
        List<Cloudlet> cloudlets = new ArrayList<>(workload.tasks().size());
        for (int i = 0; i < workload.tasks().size(); i++) {
            Task task = workload.tasks().get(i);
            CloudletSimple cloudlet = new CloudletSimple(task.lengthMi(), 1, new UtilizationModelFull());
            cloudlet.setId(i);
            broker.bindCloudletToVm(cloudlet, vms.get(validVm(assignment[i], vmCount, task.id())));
            cloudlets.add(cloudlet);
        }
        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        simulation.start();

        double[] completion = new double[vmCount];
        double totalCpuSeconds = 0;
        for (Cloudlet cloudlet : broker.getCloudletFinishedList()) {
            int vmIndex = (int)cloudlet.getVm().getId();
            completion[vmIndex] = Math.max(completion[vmIndex], cloudlet.getFinishTime());
            totalCpuSeconds += cloudlet.getActualCpuTime();
        }
        if (broker.getCloudletFinishedList().size() != workload.tasks().size())
            throw new IllegalStateException("CloudSim finished " + broker.getCloudletFinishedList().size() + " of " + workload.tasks().size() + " cloudlets");

        double makespan = Arrays.stream(completion).max().orElse(0);
        double utilization = makespan == 0 ? 0 : totalCpuSeconds / (makespan * vmCount);
        double wastage = 1 - utilization;
        Metrics metrics = new Metrics(makespan, totalCpuSeconds, utilization, wastage,
            makespanWeight * makespan + wastageWeight * wastage,
            Arrays.stream(completion).boxed().toList());
        Metrics analytical = ScheduleEvaluator.evaluate(workload, assignment, makespanWeight, wastageWeight);
        double maxDifference = 0;
        for (int i = 0; i < completion.length; i++) maxDifference = Math.max(maxDifference, Math.abs(completion[i] - analytical.vmCompletionSeconds().get(i)));
        return new CloudSimOutcome(metrics, maxDifference);
    }

    private static int validVm(int vm, int count, String taskId) {
        if (vm < 0 || vm >= count) throw new IllegalArgumentException("invalid VM index " + vm + " for " + taskId);
        return vm;
    }
}
