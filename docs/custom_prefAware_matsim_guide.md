Below is a **project-roadmap** that turns the generic “extension playbook” into a concrete, end-to-end plan for a **preference-aware adaptive dispatching system with on-line RL updates** in MATSim.  Think of it as six incremental milestones; each milestone delivers a runnable system and adds one new research ingredient.

---

## Milestone 0 - Baseline Repro

| Goal                                       | Deliverable                                                                                                                                                                              |
| ------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Verify you can run stock DRT and log KPIs. | • Fork `RunDrtExample` → `RunBaselineHwaseong.java`.<br>• Output wait-time, detour, served/ rejected counts every iteration.<br>• Keep `lastIteration = 0` at first, then 20 iterations. |

*No RL, no preferences—just proves your config + fleet are valid.*

---

## Milestone 1 - Static-Weight “Preference” Cost

1. **Extend Controler layer**

   ```java
   class PrefCostModule extends AbstractDvrpModeModule {
       @Override public void install() {
           bindModal(DrtInsertionCostCalculator.class)
               .toProvider(PrefCostProvider.class).in(SINGLETON);
       }
   }
   ```

2. **Cost calculator** (`PrefCostCalc`)
   *Constructor inject:* `@Inject InsertionCostCalculator defaultCalc, DrtConfigGroup, PreferenceStore`.
   *Algorithm:*

   ```
   baseline = defaultCalc.calculate(...).cost
   penalty  = w_access*accessMin + w_wait*wait + w_ivt*ivt + w_egress*egress
   return new InsertionCost(baseline + penalty, detourData)
   ```

   *Weights* read from CSV (`personId, w_access, w_wait, …`) at startup.

3. **Smoke-test** – confirm console prints “PrefCostCalc called”.

*Outcome:* You now have a DRT run whose dispatch choices vary if you perturb the weights file.

---

## Milestone 2 - Event-Driven Metrics & Reward Signals

1. **Event handler** (`PrefRLHandler`) implements

   * `DrtRequestSubmittedEventHandler` – save `t0`
   * `PassengerRequestScheduledEventHandler` – compute **wait**
   * `PassengerDroppedOffEventHandler` – compute **ivt**, **egress**, **total time**

2. **Reward definition**

   ```
   r = - (α_wait * wait  + β_ivt * ivt  + β_detr * detour)
   ```

   Push `(state, action, reward, nextState)` tuples into an in-memory buffer.

3. **Bind handler** in the **Controler module**:

   ```java
   bind(PrefRLHandler.class).asEagerSingleton();
   addEventHandlerBinding().to(PrefRLHandler.class);
   ```

*Outcome:* You have per-passenger reward streams ready for RL.

---

## Milestone 3 - Lightweight RL Loop (Contextual Bandit)

1. **Policy representation**
   *State*: `[timeOfDay, accessEst, waitEst, ivtEst, personSegment]`
   *Action*: discrete set of **weight vectors** or **accept/ reject**.
   Use a **softmax over linear features** (contextual bandit) – easy to update online.

2. **Learner service** (`BanditLearner`)
   *Injected singleton* keeping θ-parameters.
   `update(context, action, reward)` after each request outcome.

3. **Cost calculator ↔ Learner hook**
   At each call, request current weights from learner for that passenger & context.

4. **Exploration** – ε-greedy on cost penalty.

---

## Milestone 4 - Replace Default Optimiser (Optional but Better)

If the default rolling-horizon optimiser places requests before your cost calculator sees a choice set, copy `DefaultDrtOptimizerProvider`:

```java
class PrefOptProvider extends DefaultDrtOptimizerProvider {
    @Override protected InsertionCostCalculator createInsertionCostCalculator(...) {
        return injector.getModal(DrtInsertionCostCalculator.class); // your calc
    }
}
```

Bind in a QSim module:

```java
bindModal(DrtOptimizer.class)
    .toProvider(PrefOptProvider.class).in(SINGLETON);
```

*No algorithmic change yet—just guarantees your calculator is the one actually used.*

---

## Milestone 5 - Full RL (Policy-Gradient or Value-based)

1. **State machine per passenger** (or global network).
2. **Batch update** every N minutes of simulation time (`MobsimBeforeSimStepEvent` in optimiser or a separate RL listener).
3. **Parameter broadcast** – learner pushes new θ to a thread-safe reference the cost calculator reads.

*Tip: keep RL code in pure Java (no MATSim dependency) to unit-test easily.*

---

## Evaluation Plan

| Metric                         | How to capture                                      |
| ------------------------------ | --------------------------------------------------- |
| Mean wait time, pct served     | Built-in `DrtCircuityAnalyzer` + your event handler |
| Passenger utility              | Same reward you optimise                            |
| Operator KPIs (vkm, empty vkm) | `DrtVehicleDistanceStats` contrib                   |
| Learning curve                 | Dump θ or reward-per-iteration to CSV               |

Run **A/B sims**: Baseline vs RL version over identical demand seeds (use `Config.randomSeed`). Plot confidence bands.

---

## Code Hygiene & Repro

* **Modules**

  * `PrefConfigModule` – CSV → preference store
  * `PrefCostModule` – binds insertion cost calc
  * `PrefQSimModule` – binds optimiser provider (if milestone 4)
  * `PrefRLLoggingModule` – binds RL handler & learner

* **Artifacts**

  ```
  prefs/
    weights_init.csv
  rl/
    θ_iter0010.csv
    rewards_iter0010.csv
  out/
    matsim-output/...
  ```

* **Scripts** – one bash/Gradle task per experiment.

---

### Technology Choices Cheat-Sheet

| Component | Light (fast)                             | Heavy (accurate)                   |
| --------- | ---------------------------------------- | ---------------------------------- |
| Learner   | Contextual bandit (LinUCB) in plain Java | Deep RL (PyTorch via JNI gRPC)     |
| Router    | MATSim link-based TT                     | Hybrid with external traffic model |
| Stop-set  | Static CSV of virtual stops              | Dynamic clustering + on-sim demand |

Start **light**, verify end-to-end signal, then upgrade pieces.

---

### Final Advice

1. **Prove each milestone in isolation** (cost calc first, then rewards, then learner).
2. **Keep RL async** (don’t stall QSim thread).
3. **Log everything**; anomalies in dispatch show up first as NaNs or “all requests rejected”.
4. **Automate runs**; you’ll need dozens for ablation.

Follow these milestones and you’ll converge from a stock DRT example to a full preference-adaptive, RL-driven dispatching sandbox you can measure rigorously.
