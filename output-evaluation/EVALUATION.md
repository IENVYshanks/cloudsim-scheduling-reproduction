# Evaluation of the CloudSim scheduling reproduction

## Scope and interpretation

This is a reproducible **synthetic evaluation** of the core scheduling problem in Dehury et al., not a reproduction of the paper's published numerical results. The unavailable Cloud-Fog task files and authors' code were not reconstructed. The implementation uses the Java CloudSim Plus toolkit; the paper reports a Python 3.10/Google Colab implementation.

Analytical metrics computed from the paper's equations are the primary results. CloudSim Plus executes every reported assignment as a validation step, but its 0.1-second minimum event spacing adds timing overhead for these short synthetic tasks.

## Experimental configuration

- Task counts: [100, 200, 300]; VMs: 60; one sufficiently provisioned host.
- Runs per algorithm and task count: 20 using seeds 1 through 20.
- Synthetic task lengths: uniformly generated integer values from 15 to 400 MI with base seed 1.
- Synthetic heterogeneous VM profile: repeating 60/50/40/30 MIPS. It is not the paper's AWS i8g dataset.
- GA: population 100, generations 100, mutation rate 0.1, crossover rate 0.8. The first three values follow the paper's main experiment; crossover rate is a local assumption because the paper does not report one.
- Objective: 0.5 x makespan + 0.5 x resource wastage. Equal coefficients are a local assumption and are not scale normalization; makespan in seconds can dominate a wastage fraction.
- GA is the paper-method reproduction. FCFS, Min-Min, and random allocation are transparent local baselines, not reconstructions of the paper's undocumented PSO, ABC, or BWO implementations.
- The single host deliberately removes VM-placement decisions from the experiment so that the decision variable remains task-to-VM scheduling. Host contention, migration, network delay, energy, and unavailable CPU/RAM task-demand constraints are out of scope.

Each task-count scenario uses a fixed workload across its 20 optimizer runs. The 100-task workload is a prefix of the 200-task workload, which is a prefix of the 300-task workload. FCFS and Min-Min are deterministic, so their repeated rows and standard deviations are expected to be identical or zero.

## Results

Primary analytical results (mean +/- sample standard deviation across runs):

| Tasks | Algorithm | Makespan (s) | Utilization | Wastage | Weighted objective |
|---:|---|---:|---:|---:|---:|
| 100 | ga | 13.797667 +/- 0.460145 | 0.540295 | 0.459705 | 7.128686 |
| 100 | fcfs | 15.400000 +/- 0.000000 | 0.504957 | 0.495043 | 7.947522 |
| 100 | min-min | 13.333333 +/- 0.000000 | 0.531169 | 0.468831 | 6.901082 |
| 100 | random | 33.591667 +/- 7.873601 | 0.247106 | 0.752894 | 17.172280 |
| 200 | ga | 24.918500 +/- 0.466757 | 0.619623 | 0.380377 | 12.649438 |
| 200 | fcfs | 21.000000 +/- 0.000000 | 0.733975 | 0.266025 | 10.633013 |
| 200 | min-min | 20.000000 +/- 0.000000 | 0.752013 | 0.247987 | 10.123994 |
| 200 | random | 48.673333 +/- 7.773443 | 0.342277 | 0.657723 | 24.665528 |
| 300 | ga | 34.286167 +/- 0.718512 | 0.661297 | 0.338703 | 17.312435 |
| 300 | fcfs | 29.666667 +/- 0.000000 | 0.767120 | 0.232880 | 14.949773 |
| 300 | min-min | 26.560000 +/- 0.000000 | 0.828213 | 0.171787 | 13.365894 |
| 300 | random | 65.178333 +/- 8.099414 | 0.370699 | 0.629301 | 32.903817 |

## Analysis

- At 100 tasks, GA reduced mean makespan by 58.925329% and mean resource wastage by 38.941610% relative to the local random baseline.
- At 200 tasks, GA reduced mean makespan by 48.804616% and mean resource wastage by 42.167652% relative to the local random baseline.
- At 300 tasks, GA reduced mean makespan by 47.396374% and mean resource wastage by 46.177988% relative to the local random baseline.
- At 100 tasks, the lowest mean makespan was produced by min-min, the lowest mean wastage by ga, and the lowest mean weighted objective by min-min.
- At 200 tasks, the lowest mean makespan was produced by min-min, the lowest mean wastage by min-min, and the lowest mean weighted objective by min-min.
- At 300 tasks, the lowest mean makespan was produced by min-min, the lowest mean wastage by min-min, and the lowest mean weighted objective by min-min.
- GA did not outperform every local baseline: Min-Min had a lower mean makespan and weighted objective in all three scenarios, and FCFS also beat GA on those measures at 200 and 300 tasks. This controlled static workload is favorable to greedy earliest-finish scheduling; the result is a limitation of the local GA configuration, not evidence for the paper's published superiority claims.
- GA mean makespan increased from 13.797667 seconds at 100 tasks to 34.286167 seconds at 300 tasks, as expected when total workload increases with a fixed VM count.
- Across all GA runs and scenarios, the mean best-objective improvement from the initial population to the final generation was 29.820819%. Elitist replacement makes each recorded best-so-far convergence history non-increasing.
- The largest observed per-VM difference between CloudSim finish time and the analytical completion sum was 7.010000 seconds. This is retained as validation evidence rather than mixed into the paper-equation comparison.
- Results show behavior on this controlled synthetic workload only. They cannot validate the paper's published percentage improvements or establish statistical superiority over PSO, ABC, or BWO.

## Strengths and limitations

The implementation preserves independent non-preemptive tasks, exactly one VM assignment per task, serial execution of multiple tasks on a VM, heterogeneous MI/MIPS execution times, the weighted objective, integer chromosomes, Eq. 12 roulette selection, one-point crossover, mutation, and elitist parent-offspring replacement. Seeds and configuration metadata make the experiment reproducible.

The principal limitations are the synthetic workload and VM profile, absent CPU/memory task demands, unnormalized objective components, fixed one-host infrastructure, omitted communication delays, and unavailable implementations of the paper's comparative optimizers. The 95% intervals in the JSON describe stochastic variation for this fixed synthetic instance; no hypothesis-test or general statistical-significance claim is made.

Machine-readable details are in `output-evaluation/evaluation.json`, `output-evaluation/evaluation.csv`, and each `output-evaluation/tasks-N/` directory.
