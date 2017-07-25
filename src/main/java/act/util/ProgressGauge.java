package act.util;

/*-
 * #%L
 * ACT Framework
 * %%
 * Copyright (C) 2014 - 2017 ActFramework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

/**
 * Class implement this interface can track progress
 */
public interface ProgressGauge {

    /**
     * Update max hint. If the number is negative, then
     * it indicate the progress is indefinite
     *
     * @param maxHint the max steps hint
     */
    void updateMaxHint(int maxHint);

    /**
     * Advances the progress by one step
     */
    void step();

    /**
     * Advances the progress by specified steps
     * @param steps the step size
     */
    void stepBy(int steps);

    /**
     * Log progress
     *
     * @param steps the new progress value
     */
    void stepTo(int steps);

    /**
     * mark the progress has been interrupted (by error e.g.)
     */
    void interrupt();

    /**
     * Report the current progress steps
     *
     * @return the current progress
     */
    int currentSteps();

    /**
     * Returns the max hint
     *
     * @return the max hint setting
     */
    int maxHint();

    /**
     * Check if the work is still in progress.
     *
     * Note when `inProgress()` returns `true` it means
     * the work is neither `done()` nor `interrupted()`
     *
     * @return `true` if the work is still in progress or `false` if the work is finished or interrupted
     */
    boolean inProgress();

    /**
     * Check if the progress is finished
     * @return `true` if the progress is finished or `false` otherwise
     */
    boolean done();

    /**
     * Check if the progress has been interrupted due to an error
     * @return `true` if the progress is broken or `false` otherwise
     */
    boolean interrupted();

    /**
     * Reset the progress gauge
     */
    void reset();

    /**
     * Add an listener to this gauge that monitors the progress update
     * @param listener the listener
     */
    void addListener(Listener listener);

    interface Listener {
        void onUpdate(ProgressGauge progressGauge);
    }
}
