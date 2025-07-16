### What these two PDFs are documenting

Together, the files form a **“user-preference module” package** for an autonomous Demand-Responsive Transit (DRT) project:

* **`user_preference_data_description.pdf`** – a bilingual data dictionary that specifies two survey-derived CSV tables.
* **`user_preference_explanation.pdf`** – a slide deck that shows how those tables feed a reinforcement-learning (policy-gradient) engine that imitates riders’ acceptance / rejection behaviour.

Below is a structured walkthrough of each file.

---

#### A. Data dictionary – `user_preference_data_description.pdf`

| Dataset                | Records                                      | What each row means                                                        | Core columns                                                                                                                                                                     | Notes                                                                                                                                  |
| ---------------------- | -------------------------------------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| **`user_history.csv`** | 500 riders × 6 choice tasks = **3 000 rows** | The alternative that a respondent actually chose in a stated-choice survey | `id` (1-500), `situation` (0-5), `choice` (0 = alt-0, 1 = alt-1, **2 = “reject service”**)                                                                                       | Rejected-service events later become the negative class for the RL reward                                                              |
| **`features.csv`**     | 3 000 rows × 10 columns                      | Attributes of **each** alternative presented in each task                  | Travel attributes: `access`, `wait`, `ivt`, `egress` • Socio-econ: `linc` (income class), `license` (driving licence) • Flags: `alternative` (0/1/2), `constant` (1 if “reject”) | For rejected alternatives all travel attributes are zero-filled (only the constant is 1) so the RL agent sees no utility signal there  |

Key points:

1. **Origin of data** – one-off survey of 500 residents / workers in the Living-Lab. Each answered six hypothetical dispatch offers.&#x20;
2. **Encoding ready for ML** – choice outcome in `user_history.csv`; matching alternative-level attributes in `features.csv`.
3. **Socio-economic columns** (`linc`, `license`) are available now, but the authors flag that they may be removed in future versions to avoid privacy / applicability issues.&#x20;

---

#### B. Algorithm & workflow deck – `user_preference_explanation.pdf`

1. **Environment set-up**

   * Python 3.11 with `pandas`, `numpy`, plus built-ins `random`, `pickle`.&#x20;

2. **Overall framework**

   * **Database layer** – the two CSVs above are loaded as DataFrames.
   * **`data_call` module** – fetches (`user_history_df`, `features_df`) for a given rider ID and returns:

     * the historical choice list,
     * a 3-D array of alternative features,
     * dimensions `num_situations` and `feature_dim`.&#x20;
   * **Reinforcement-learning core**

     * **Environment**: Markov-decision process where each state = dispatch offer *t*; episode ends after 6 offers.
     * **Policy-Gradient Agent**: soft-max (logit) policy; updates weights θ via the policy-gradient theorem.
     * **Reward**: +1 if the agent matches the real choice, –1 otherwise.&#x20;
   * **Hyper-parameters**: α (learning rate), γ (discount), β (temperature), ε (ε-greedy), `num_episodes`.&#x20;

3. **Training loop (`main` function)**

   * For each rider ID: create `env` + `agent`, run the specified number of episodes, log total reward per episode to check convergence, and store `weights`.&#x20;

4. **Outputs**

   * **`weights.csv`** – one row per rider with the learned utility weight for each travel attribute (`access`, `wait`, `ivt`, `egress`). Intended to be mapped to synthetic rider IDs in real-time simulation.&#x20;

5. **Limitations & planned updates**

   * Positive weights on some disutility variables (artefact of small survey).
   * Training and test sets not yet split; only imitation of stated choices (no accumulated usage history).
   * Future versions will replace survey data with actual dispatch logs and drop sensitive socio-economic fields.&#x20;

---

### How to use the package

1. **Load the data** – read the two CSVs; keep rider IDs consistent.
2. **Instantiate `data_call`** – obtain `user_history`, `features`, etc. for each rider you want to model.
3. **Run `main`** – train or fine-tune the `PolicyGradientAgent` to reproduce that rider’s choices; retrieve their personalised `weights`.
4. **Plug into simulation** – assign a virtual rider ID to every incoming demand request, attach the corresponding weights from `weights.csv`, and let your dispatcher or pricing module query the policy to estimate acceptance probability.

---

**In short:**
*The first PDF is a **schema & code-book** for two survey tables; the second is a **technical recipe** showing how those tables fuel a rider-specific policy-gradient learner that can be embedded in a larger DRT simulation to mimic heterogeneous user preferences.*
