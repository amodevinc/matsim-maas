## TRB Paper Topic Summary

**Title (tentative)**: *Preference-Aware Adaptive Dispatching for Demand-Responsive Transit Under Demand Uncertainty: A Simulation-Based Evaluation Using MATSim*

### 🔍 Core Idea

This research proposes and evaluates a **preference-aware adaptive dispatching system** for Demand-Responsive Transit (DRT) using **MATSim**. The goal is to improve passenger satisfaction and service efficiency by learning and incorporating individual user preferences into ride assignment decisions, especially under **realistic demand uncertainty scenarios**.

---

### 🧠 Motivation

Conventional DRT dispatchers rely on static heuristics and assume passengers will accept any feasible ride. However, in real-world scenarios, passengers often have nuanced preferences (e.g., waiting time, walking distance, detour sensitivity). Ignoring these leads to higher rejection rates and inefficient service.

Our hypothesis: **An adaptive dispatching system that learns and respects passenger preferences can outperform baseline heuristics in service quality and robustness.**

---

### 🛠️ What We’re Doing

* **Simulation Framework**: MATSim, with over 180 experiments across 45 demand uncertainty scenarios (combinations of temporal, spatial, and intensity variations).
* **Dispatching System**:

  * Baseline: Standard insertion-based dispatcher.
  * Proposed: **Preference-aware adaptive dispatcher** using **policy-gradient reinforcement learning** to infer and apply user acceptance weights.
* **Evaluation Metrics**: Service rate, waiting time, in-vehicle travel time, detour factor, fleet utilization, and more.
* **Study Area**: Hwaseong Living-Lab DRT scenario (Korea), with virtual stops, realistic demand patterns, and multimodal simulation.

---

### 📈 Contributions

1. **Methodological**: A reinforcement learning-based dispatcher integrating soft user constraints via preference scores.
2. **Technical**: A reproducible MATSim-based experimentation pipeline with population scaling, demand perturbation, KPI aggregation, and statistical analysis.
3. **Policy-Relevant Insight**: Fleet sizing and robustness trade-offs when adapting dispatching strategies to user behavior under uncertainty.

---

### 📄 Paper Roadmap

1. **Introduction & Motivation**
2. **Related Work** – DRT dispatching, preference modeling, MATSim simulation
3. **Methodology** – Preference-aware model design, RL formulation, and integration
4. **Simulation Setup** – Hwaseong scenario, demand modeling, fleet configuration
5. **Experiments & Evaluation** – KPI comparisons across baseline vs preference-aware runs
6. **Discussion** – Trade-offs, insights on user-centric dispatching
7. **Conclusion & Future Work**

---

### 🧪 Progress So Far

* ✅ 45 MATSim-compatible demand population files generated
* ✅ Preference-aware optimizer implemented and integrated
* ✅ Experimental batch runner and KPI aggregation tools complete
* ✅ Full reproducibility pipeline scripted for Zenodo compliance

Currently finalizing simulation runs and KPI comparisons. The paper draft is being structured in parallel.