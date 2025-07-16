## 📚  MATSim-DRT — Comprehensive Extension Playbook

*A single document you can reuse for **any** future customisation (cost, optimiser, rebalancing, virtual stops, scoring, etc.).*

---

### 0. 30-second Map of MATSim-DRT

| Layer                    | What happens here                                                                              | Typical “Abstract\*” helper classes                                                                    |
| ------------------------ | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| **Controler / Config**   | Read config, register fleet, fares, stop-time rules, travel-time providers, *static* bindings. | `AbstractDvrpModeModule` (→ `DrtModeModule`, your `MyDrtModule`)                                       |
| **QSim (runtime)**       | Wire vehicle agents, **DrtOptimizer**, scheduler, action creator, passenger engine.            | `AbstractDvrpModeQSimModule` (→ `DrtModeQSimModule`, your `MyDrtQSimModule`)                           |
| **Provider / Algorithm** | Factories and heuristics the optimiser uses (insertion search, cost calc, scheduler).          | `DefaultDrtOptimizerProvider`, `AbstractDrtRequestInsertionSearch`, `AbstractStopDurationEstimator`, … |

> The “Abstract\*” classes don’t add a new layer—they’re just templates that live **inside** the three layers to save boiler-plate.

---

## 1. Workflow Checklist for Any Extension

| Step                                                 | Action                                                                                                                         | Concrete APIs                                                                                                   |
| ---------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------- |
| **1. Pick the layer**                                | Controler ? QSim ? Provider ? (see table above).                                                                               |                                                                                                                 |
| **2. Subclass the right abstract helper (optional)** | e.g. `class MyModeModule extends AbstractDvrpModeModule { … }`                                                                 |                                                                                                                 |
| **3. Implement / override the interface you need**   | Examples:<br>• `DrtInsertionCostCalculator`<br>• `DrtRequestInsertionSearch`<br>• `DrtScheduler`<br>• `RebalancingStrategy`    |                                                                                                                 |
| **4. Bind it with Guice**                            | Use **bindModal(...)** inside your module.                                                                                     | `java bindModal(DrtInsertionCostCalculator.class) .toProvider(MyCostCalcProvider.class) .in(Singleton.class); ` |
| **5. Insert module**                                 | `controler.addOverridingModule(new MyModeModule());`<br>and/or<br>`controler.addOverridingQSimModule(new MyModeQSimModule());` |                                                                                                                 |
| **6. Smoke-test**                                    | Run 0-iteration; confirm your constructor logs appear.                                                                         |                                                                                                                 |
| **7. Short test & KPI check**                        | 10 iterations; compare wait-time / detour.                                                                                     |                                                                                                                 |

---

## 2. Which Interface Do I Touch?

| Goal                           | Interface / Class to implement                                               | Bind in … (layer)                          |
| ------------------------------ | ---------------------------------------------------------------------------- | ------------------------------------------ |
| Custom cost function           | `DrtInsertionCostCalculator` (`InsertionCost` return)                        | **Controler** module                       |
| New insertion search heuristic | `DrtRequestInsertionSearch`                                                  | **Provider** (override optimiser provider) |
| Alternate scheduler            | `DrtScheduler`                                                               | **Provider**                               |
| Dynamic stop duration          | `StopTimeCalculator` / `PassengerStopDurationProvider`                       | **Controler**                              |
| Custom rebalancing             | `RebalancingStrategy`                                                        | **Controler** (`RebalancingModule`)        |
| Different optimiser            | Subclass `DefaultDrtOptimizerProvider` or implement `Provider<DrtOptimizer>` | **QSim** module                            |
| Agent task logic               | `DrtActionCreator`                                                           | **QSim** module                            |
| Scoring / analytics            | `ScoringFunctionFactory` or custom `EventHandler`                            | **Controler**                              |

---

## 3. Binding Patterns (with Abstract helpers)

```java
// ①  Controler-layer module
public final class MyDrtModule extends AbstractDvrpModeModule {
    public MyDrtModule(String mode) { super(mode); }

    @Override
    public void install() {
        // Cost calculator
        bindModal(DrtInsertionCostCalculator.class)
            .toProvider(MyCostCalcProvider.class).in(Singleton.class);

        // Stop-time rule override
        bindModal(StopTimeCalculator.class)
            .to(MyDynamicStopTimeCalc.class).in(Singleton.class);
    }
}

// ②  QSim-layer module
public final class MyDrtQSimModule extends AbstractDvrpModeQSimModule {
    public MyDrtQSimModule(String mode) { super(mode); }

    @Override
    protected void configureQSim() {
        bindModal(DrtOptimizer.class)                      // replace optimiser
            .toProvider(MyOptimizerProvider.class)
            .in(Singleton.class);

        bindModal(DrtActionCreator.class)                  // optional
            .to(MyActionCreator.class).in(Singleton.class);
    }
}
```

---

## 4. Provider Override Recipe

1. **Copy** `DefaultDrtOptimizerProvider` → `MyOptimizerProvider`.
2. Override only the factory you care about (e.g. `createInsertionCostCalculator`).
3. Bind the provider in your QSim module (see above).

---

## 5. Dependency-Injection Do’s & Don’ts

✅ **Constructor injection only** (`@Inject public MyClass(…)`).
✅ Request every MATSim service you need (`Network`, `TravelTime`, `DrtConfigGroup`, `Fleet`, …).
✅ Mark with `.in(Singleton.class)` unless you truly need multiple instances.

❌ Don’t leave fields `null` for “lazy init.”
❌ Don’t bind concrete classes to themselves; always bind interface → impl.

---

## 6. Testing Ladder

1. `lastIteration = 0` → smoke test (verify logs).
2. 10 iterations → basic KPI sanity.
3. 100 + iterations → research runs.

Add `assert !Double.isNaN(cost)` or similar guards inside custom maths.

---

## 7. Version-Upgrade Guard

* Note the exact artefact: `org.matsim:matsim:16.0-2024w15`.
* Before upgrading, **diff these files**:
  – `DrtInsertionCostCalculator.java`
  – `DrtOptimizer.java`
  – `DrtModeModule.java` / `DrtModeQSimModule.java`.

Update your code if signatures moved.

---

## 8. Documentation Hygiene

* **`CHANGELOG.md`** – one-line bullet per new binding or API bump.
* **Inline constructor `System.out.println`** – makes runtime activation obvious.
* **README** – small table: interface → your class → layer.

---

## 9. Quick Decision Tree

```text
Want to change only numbers (weights, α/β)? → Config file, no code.

Need new cost / detour metric?            → 1) Implement DrtInsertionCostCalculator
                                            2) Bind in Controler module.

Need new matching logic?                  → 1) Subclass DefaultDrtOptimizerProvider
                                            2) Replace insertion search / scheduler
                                            3) Bind provider in QSim module.

Need new vehicle behaviour?               → Implement DrtActionCreator + DrtScheduler.

Need global policy (fares, rebalancing)?  → Controler module with RebalancingModule
                                            or FareHandler binding.
```

Keep this playbook handy and you’ll always know:

* **Which interface to implement**,
* **Which “Abstract*” helper to extend*\*,
* **Where to bind it**, and
* **How to test it**

— no matter what DRT innovation you pursue.
