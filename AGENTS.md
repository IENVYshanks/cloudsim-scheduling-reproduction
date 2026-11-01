# Project Instructions

## Goal

Implement and maintain a reproducible coding-only simulation of the paper *Joint optimization of resource utilization and makespan in cloud computing using genetic algorithm* (Dehury et al.). The project question asks for a CloudSim-based reproduction of the paper's core scheduling problem. Preserve the distinction between a CloudSim-style model and the Java CloudSim toolkit; state clearly which one this repository implements.

## Before making changes on every run

1. Inspect the paper, project files, and current workspace changes relevant to the task.
2. Before editing any file, tell the user what changes are planned, why, which files are affected, the assumptions, and how the result will be checked. This applies to documentation and configuration edits as well as implementation code.
3. Wait for the user's response before making the proposed changes. If the user redirects the work, revise the plan and present the revised plan before proceeding. Do not treat silence or elapsed time as approval.
4. For each substantive implementation run, use independent agents to review the paper/model and proposed code when agent delegation is available. Ask agents to report evidence, assumptions, risks, and commands/results; do not let them edit shared files unless explicitly assigned. The lead agent owns integration and the final decision.
5. Synthesize agent findings: record agreements and disagreements, resolve disagreements against the paper and code evidence, and state the lead's final outcome and any unresolved limitation. Agent consensus does not override the paper or correctness.

## Paper fidelity

- Model independent, non-preemptive tasks, each assigned to exactly one VM; VMs may receive multiple tasks.
- Use integer task-to-VM chromosomes. Derive task execution time as task length (MI) divided by VM processing capacity (MIPS). A VM's completion time is the sum of its assigned task execution times; makespan is the largest VM completion time.
- Compute utilization as total task execution time divided by (makespan x VM count), and resource wastage as 1 - utilization. Minimize the paper's weighted objective `w1 * makespan + w2 * wastage`.
- Preserve the paper's GA choices: roulette-wheel selection, one-point crossover, mutation, and elitism. Use the reported setting when available (100 individuals, 100 iterations, mutation rate 0.1 for the main 60-VM task-count experiment); make all parameters configurable and reproducible by seed. Reported run count is 20 for statistical analysis.
- Include baselines only when implemented transparently and label any locally designed optimizer as a baseline rather than a paper-exact reproduction. Do not claim to reproduce undocumented PSO/ABC/BWO details or unavailable datasets.
- The paper's full datasets/code are not included in this folder. Mark synthetic workloads, reconstructed examples, and all other assumptions as such. Never present simulated numbers as published results or claim statistical significance without appropriate repeated-run analysis.
- Keep the scope to coding deliverables; do not create a printout/report unless requested.

## Coding principles

- Prefer small, cohesive modules and clear domain names; separate simulation, scheduling algorithms, metrics, experiment orchestration, and CLI concerns.
- Validate input and configuration at boundaries. Reject invalid capacities, task lengths, weights, and algorithm parameters with actionable messages.
- Keep random state explicit and local; same inputs, algorithm, and seed must produce the same result.
- Avoid hidden global state, unnecessary dependencies, duplicated metric logic, and premature abstraction.
- Keep algorithms independently callable and compare them on the same workload instances and seeds.
- Keep output machine-readable (CSV/JSON) and include configuration/seed metadata needed to reproduce it.
- Document formulas, units, assumptions, and known limitations near the implementation and in user-facing project documentation.
- Do not add tests or run tests unless the user asks. When asked to verify, report the exact commands and outcomes.

## Run-level decision and completion

The lead agent integrates all agent and local findings and decides the overall outcome for each run. Before editing, report the change plan. At completion, report what changed, the key assumptions/limitations, and any validation performed. If agent findings conflict, resolve using the paper, constraints, and code behavior; explain material unresolved conflicts instead of silently choosing.
