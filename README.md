# Java CloudSim task scheduling reproduction

This project implements the core scheduling problem in Dehury et al., *Joint optimization of resource utilization and makespan in cloud computing using genetic algorithm*, in Java using the **CloudSim Plus toolkit**. It creates actual CloudSim Plus datacenters, hosts, VMs, brokers, and cloudlets. The scheduling algorithms and paper-specific objective are implemented in this project.

The paper itself reports experiments written in Python 3.10 and run in Google Colab. It cites CloudSim in related work but does not say that its own experiments used the CloudSim toolkit. This repository is therefore a Java CloudSim-based implementation of the paper's core model, not the authors' runtime or a numerical reproduction of their results.

## Model

- Every task is an independent, non-preemptive Cloudlet assigned to exactly one VM. Each Cloudlet represents a task of a given length in million instructions (MI); each VM has one processing element with capacity in million instructions per second (MIPS).
- Cloudlets are explicitly bound to the chromosome's VM. CloudSim Plus `CloudletSchedulerSpaceShared` runs a VM's assigned tasks serially. The model omits arrivals, migration, task splitting, network transfer delays, and CPU/memory admission constraints.
- Fast GA fitness evaluation uses the paper's arithmetic: task duration = task length (MI) / VM capacity (MIPS); VM completion time = the sum of assigned task durations; makespan = maximum VM completion time; utilization = sum of task durations / (makespan x VM count); resource wastage = 1 - utilization; objective = `w1 * makespan_seconds + w2 * resource_wastage`.
- After an algorithm picks an assignment, CloudSim Plus executes it. The `analytical_*` metrics are the primary paper-equation results. Reported `cloudsim_*` metrics use the toolkit's Cloudlet CPU times and finish times and are retained as execution-validation evidence. Each JSON run records the largest difference between per-VM CloudSim finish times and the paper's sum-of-durations calculation. This avoids running a complete discrete-event simulation for every GA candidate while still executing each reported schedule in CloudSim. CloudSim Plus uses a 0.1-second minimum event spacing here; short synthetic tasks can accumulate simulator timing overhead.
- The GA uses integer task-to-VM genes, one-point crossover, per-gene mutation, elitist replacement, and roulette selection using the paper's Eq. 12 inverse-cost expression. The paper's prose/table conflict with that equation about whether higher or lower objective values are favored; the implementation follows the equation, consistent with minimizing the objective. The paper does not provide a crossover rate; this implementation uses a configurable local default of 0.8.
- `fcfs` is an earliest-available-VM dispatch baseline, `min-min` is a greedy local baseline, and `random` assigns tasks uniformly. They are not claimed as exact reproductions of the paper's other optimizers.

## Workload limitations

The Cloud-Fog CSV files and source code used by the paper are not included here. The CLI therefore generates a seeded synthetic workload with integer task lengths from 15 to 400 MI. Its VM profile repeats 60/50/40/30 MIPS. These values provide a small, controlled heterogeneous scheduling instance and are explicit local defaults, not the paper's task dataset or nine AWS i8g VM configurations. The paper describes 105 MIPS per vCPU and its table consequently lists total VM capacities from 210 to 20,160 MIPS. All repository output is marked synthetic. No simulated value is presented as a published result or as evidence of statistical significance.

The paper's equations also include CPU and memory capacity constraints, but the synthetic workload has no corresponding per-task demands. One adequately provisioned CloudSim Plus host is used deliberately to isolate task-to-VM scheduling from VM placement and host contention. The host has enough processing elements, RAM, bandwidth, and storage for every configured VM; no unprovided task-demand constraints are inferred. Network delay, migration, energy, and VM placement are outside this reproduction's scope.

The default objective weights are 0.5 and 0.5 as a neutral equal-coefficient local assumption. They are not scale normalization: makespan is measured in seconds while wastage is a fraction from 0 to 1, so makespan can dominate the numerical objective. Both objectives are therefore also reported separately. The local crossover-rate default is 0.8 because the paper does not specify one.

## Requirements and run

- Java 17 or newer
- Maven 3.8 or newer
- Network access the first time Maven resolves CloudSim Plus 8.0.0 and build plugins

Run the complete synthetic evaluation using the paper's reported main GA settings (100 individuals, 100 generations, mutation rate 0.1, 20 runs, 60 VMs) across 100, 200, and 300 tasks:

```powershell
mvn exec:java -Dexec.args="--task-counts 100,200,300 --vms 60 --runs 20 --seed 1 --population 100 --generations 100 --mutation-rate 0.1 --crossover-rate 0.8 --algorithms ga,fcfs,min-min,random --output output-evaluation --report EVALUATION.md"
```

The command writes consolidated `output-evaluation/evaluation.json` and `evaluation.csv`, complete per-run results under `output-evaluation/tasks-N/`, and the print-ready `EVALUATION.md`. The 100-task workload is a prefix of the 200-task workload, which is a prefix of the 300-task workload because every scenario uses the same workload seed. Each scenario is fixed across its 20 runs while stochastic algorithm seeds vary from 1 through 20. FCFS and Min-Min are deterministic, so repeated runs intentionally have zero variance; they are retained for fair comparisons on the identical workload, not treated as independent stochastic samples.

Example quick run:

```powershell
mvn exec:java -Dexec.args="--tasks 10 --vms 4 --runs 1 --population 10 --generations 5 --output output-small"
```

Useful options:

```text
--tasks N --vms N --runs N --seed N
--task-counts N,N,... --report FILE
--algorithms ga,fcfs,min-min,random
--population N --generations N --mutation-rate X --crossover-rate X
--min-mi N --max-mi N
--makespan-weight X --wastage-weight X --output DIR
```

Defaults follow the paper's reported 100-task, 60-VM main GA experiment where available; the crossover rate, objective weights, synthetic data, VM profile, and one-host infrastructure are documented local choices. The reported 20 runs use seeds `base_seed + run_index`, and every algorithm receives the same workload and run seed. The repository does not implement undocumented PSO, ABC, or BWO methods and makes no ANOVA or statistical-significance claim about the local baselines.
